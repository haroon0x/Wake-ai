package com.wake.app.capture

import android.util.Log
import com.wake.app.data.MemoryDao
import com.wake.app.data.toByteArray
import com.wake.app.retrieval.Embedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Ingest(
    private val dao: MemoryDao,
    private val scope: CoroutineScope,
    private val embedder: Embedder? = null,
    private val retentionDaysProvider: () -> Int = { 30 },
    private val dedupWindowMillis: Long = 15 * 1000
) {
    private val grouper = SessionGrouper()
    private var lastCleanupAt = 0L

    fun submit(raw: RawCapture) {
        val text = raw.text.trim()
        if (text.isEmpty()) return
        if (Exclusions.isExcluded(raw.pkg)) return

        scope.launch {
            val hash = raw.contentHash()
            if (dao.existsRecent(hash, raw.timestamp - dedupWindowMillis)) return@launch
            val sessionId = grouper.assign(raw.timestamp)
            val id = dao.insert(raw.toEvent(sessionId, hash))
            runCatching {
                embedder?.embed(text)?.let { dao.setEmbedding(id, it.toByteArray()) }
            }
            if (raw.timestamp - lastCleanupAt >= CLEANUP_INTERVAL_MILLIS) {
                val retentionDays = retentionDaysProvider()
                if (retentionDays > 0) {
                    dao.deleteOlderThan(raw.timestamp - retentionDays * DAY_MILLIS)
                }
                lastCleanupAt = raw.timestamp
            }
            Log.d("WAKE", "ingest id=$id source=${raw.source} pkg=${raw.pkg} sender=${raw.sender} text=${text.take(60)}")
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val CLEANUP_INTERVAL_MILLIS = 21_600_000L
    }
}
