package com.wake.app.capture

import android.util.Log
import com.wake.app.data.MemoryDao
import com.wake.app.data.MemoryEvent
import com.wake.app.data.SOURCE_SCREEN_TEXT
import com.wake.app.data.Diagnostics
import com.wake.app.data.toByteArray
import com.wake.app.retrieval.Embedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class Ingest(
    private val dao: MemoryDao,
    private val scope: CoroutineScope,
    private val embedder: Embedder? = null,
    private val retentionDaysProvider: () -> Int = { 30 },
    private val dedupWindowMillis: Long = 15 * 1000,
    private val diagnostics: Diagnostics? = null
) {
    private val grouper = SessionGrouper()
    private var grouperInitialized = false
    private var lastCleanupAt = 0L

    fun submit(raw: RawCapture) {
        val text = raw.text.trim()
        if (text.isEmpty()) {
            diagnostics?.record("ingest_drop", "reason" to "empty", "source" to raw.source, "package" to raw.pkg)
            return
        }
        if (Exclusions.isExcluded(raw.pkg)) {
            diagnostics?.record("ingest_drop", "reason" to "excluded", "source" to raw.source, "package" to raw.pkg)
            return
        }

        scope.launch {
            diagnostics?.record(
                "ingest_received",
                "source" to raw.source,
                "package" to raw.pkg,
                "sender" to raw.sender,
                "text" to text,
                "structured" to raw.structured
            )
            initializeGrouper()
            val captures = chunk(raw.copy(text = text))
            val sessionId = grouper.assign(raw.timestamp, raw.source, raw.pkg)
            diagnostics?.record(
                "ingest_prepared",
                "source" to raw.source,
                "package" to raw.pkg,
                "sessionId" to sessionId,
                "chunkCount" to captures.size
            )
            var inserted = 0
            captures.forEach { capture ->
                val hash = capture.contentHash()
                val after = capture.timestamp - dedupWindowMillis
                if (dao.existsRecent(hash, after)) {
                    diagnostics?.record(
                        "ingest_drop",
                        "reason" to "exact_duplicate",
                        "source" to capture.source,
                        "package" to capture.pkg,
                        "text" to capture.text
                    )
                    return@forEach
                }
                val recent = dao.recentCaptures(after, DEDUP_CANDIDATE_LIMIT)
                val duplicate = recent.firstOrNull { isNearDuplicate(capture, it) }
                if (duplicate != null) {
                    diagnostics?.record(
                        "ingest_drop",
                        "reason" to "near_duplicate",
                        "duplicateEventId" to duplicate.id,
                        "source" to capture.source,
                        "package" to capture.pkg,
                        "text" to capture.text
                    )
                    return@forEach
                }
                val id = dao.insert(capture.toEvent(sessionId, hash))
                inserted++
                var embedded = false
                var embeddingError: String? = null
                runCatching {
                    embedder?.embed(capture.text)?.let {
                        dao.setEmbedding(id, it.toByteArray())
                        embedded = true
                    }
                }.onFailure { embeddingError = it.message ?: it.javaClass.simpleName }
                diagnostics?.record(
                    "ingest_stored",
                    "eventId" to id,
                    "sessionId" to sessionId,
                    "source" to capture.source,
                    "package" to capture.pkg,
                    "sender" to capture.sender,
                    "text" to capture.text,
                    "structured" to capture.structured,
                    "embedded" to embedded,
                    "embeddingError" to embeddingError
                )
            }
            if (raw.timestamp - lastCleanupAt >= CLEANUP_INTERVAL_MILLIS) {
                val retentionDays = retentionDaysProvider()
                if (retentionDays > 0) {
                    dao.deleteOlderThan(raw.timestamp - retentionDays * DAY_MILLIS)
                }
                lastCleanupAt = raw.timestamp
            }
            Log.d("WAKE", "ingest count=$inserted source=${raw.source} pkg=${raw.pkg} sender=${raw.sender} text=${text.take(60)}")
        }
    }

    private suspend fun initializeGrouper() {
        if (grouperInitialized) return
        val latest = dao.latest()
        val latestScreen = dao.latestBySource(SOURCE_SCREEN_TEXT)
        if (latest != null) {
            grouper.restore(latest.sessionId, latest.timestamp, latestScreen?.pkg)
        }
        grouperInitialized = true
    }

    private fun chunk(raw: RawCapture): List<RawCapture> {
        if (raw.source != SOURCE_SCREEN_TEXT || raw.text.length <= MAX_CHUNK_CHARS) return listOf(raw)
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        raw.text.lines().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            if (current.isNotEmpty() && current.length + line.length + 1 > MAX_CHUNK_CHARS) {
                chunks += current.toString()
                current = StringBuilder()
            }
            if (line.length > MAX_CHUNK_CHARS) {
                if (current.isNotEmpty()) {
                    chunks += current.toString()
                    current = StringBuilder()
                }
                line.chunked(MAX_CHUNK_CHARS).forEach(chunks::add)
            } else {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks.mapIndexed { index, value ->
            raw.copy(text = value, structured = structuredChunk(raw.structured, index, chunks.size))
        }
    }

    private fun structuredChunk(structured: String?, index: Int, count: Int): String =
        runCatching { if (structured.isNullOrBlank()) JSONObject() else JSONObject(structured) }
            .getOrDefault(JSONObject())
            .put("chunkIndex", index)
            .put("chunkCount", count)
            .toString()

    private fun isNearDuplicate(raw: RawCapture, event: MemoryEvent): Boolean {
        if (raw.pkg != event.pkg || raw.source != event.source) return false
        val left = raw.normalizedText()
        val right = event.text.trim().replace(Regex("\\s+"), " ").lowercase()
        if (left == right) return true
        if (minOf(left.length, right.length) < MIN_SIMILAR_TEXT_CHARS) return false
        val lengthRatio = minOf(left.length, right.length).toFloat() / maxOf(left.length, right.length)
        if (lengthRatio >= 0.9f && (left.contains(right) || right.contains(left))) return true
        val leftTokens = left.split(' ').toSet()
        val rightTokens = right.split(' ').toSet()
        val union = leftTokens union rightTokens
        if (union.isEmpty()) return false
        val similarity = (leftTokens intersect rightTokens).size.toFloat() / union.size
        return lengthRatio >= 0.85f && similarity >= 0.9f
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val CLEANUP_INTERVAL_MILLIS = 21_600_000L
        const val MAX_CHUNK_CHARS = 700
        const val MIN_SIMILAR_TEXT_CHARS = 48
        const val DEDUP_CANDIDATE_LIMIT = 50
    }
}
