package com.wake.app.retrieval

import com.wake.app.data.MemoryDao
import com.wake.app.data.MemoryEvent
import com.wake.app.data.Diagnostics
import com.wake.app.data.toFloatArray
import kotlin.math.exp
import org.json.JSONArray
import org.json.JSONObject

class Retriever(
    private val dao: MemoryDao,
    private val embedder: Embedder? = null,
    private val diagnostics: Diagnostics? = null
) {
    private val interpreter = QueryInterpreter(dao)

    suspend fun recent(minutes: Int, limit: Int = 200): List<MemoryEvent> {
        val since = System.currentTimeMillis() - minutes * 60_000L
        return dao.since(since, limit)
    }

    suspend fun search(query: String, limit: Int = 40): List<MemoryEvent> {
        val constraints = interpreter.interpret(query)
        return lexical(constraints, limit)
    }

    private suspend fun lexical(constraints: QueryConstraints, limit: Int): List<MemoryEvent> {
        val terms = terms(constraints.semanticQuery)
        if (terms.isEmpty()) return constrainedRecent(constraints, limit)
        val andMatch = terms.joinToString(" ") { "$it*" }
        val results = constrainedSearch(andMatch, constraints, limit)
        if (results.isNotEmpty()) return results
        val orMatch = terms.joinToString(" OR ") { "$it*" }
        return constrainedSearch(orMatch, constraints, limit)
    }

    suspend fun semantic(query: String, limit: Int = 8): List<MemoryEvent> {
        val constraints = interpreter.interpret(query)
        return semanticRanked(constraints, limit).map { it.first }
    }

    private suspend fun semanticRanked(constraints: QueryConstraints, limit: Int): List<Pair<MemoryEvent, Float>> {
        if (terms(constraints.semanticQuery).isEmpty()) return emptyList()
        val queryEmbedding = embedder?.embed(constraints.semanticQuery) ?: return emptyList()
        val since = constraints.since ?: 0L
        return dao.embeddedSince(since, MAX_SEMANTIC_CANDIDATES)
            .asSequence()
            .filter(constraints::matches)
            .mapNotNull { event ->
                event.embedding?.toFloatArray()?.let { embedding ->
                    event to Embedder.cosine(queryEmbedding, embedding)
                }
            }
            .filter { (_, score) -> score > 0.3f }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .toList()
    }

    suspend fun hybrid(query: String, limit: Int = 8): List<MemoryEvent> {
        val constraints = interpreter.interpret(query)
        val lexical = lexical(constraints, limit * CANDIDATE_MULTIPLIER)
        val semanticRanked = semanticRanked(constraints, limit * CANDIDATE_MULTIPLIER)
        val semantic = semanticRanked.map { it.first }
        val ranked = (lexical + semantic)
            .distinctBy { it.id }
            .map { event ->
                val lexicalRank = lexical.indexOfFirst { it.id == event.id }
                val semanticRank = semantic.indexOfFirst { it.id == event.id }
                val score = (if (lexicalRank >= 0) 1f / (60 + lexicalRank) else 0f) +
                    (if (semanticRank >= 0) 1f / (60 + semanticRank) else 0f) +
                    recencyScore(event, constraints)
                event to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
        val selected = diversify(ranked, limit)
        diagnostics?.record(
            "retrieval",
            "query" to query,
            "constraints" to constraintsJson(constraints),
            "lexicalEventIds" to JSONArray(lexical.map { it.id }),
            "semantic" to JSONArray(semanticRanked.map { (event, score) ->
                JSONObject().put("eventId", event.id).put("score", score.toDouble())
            }),
            "rankedEventIds" to JSONArray(ranked.map { it.id }),
            "selectedEventIds" to JSONArray(selected.map { it.id })
        )
        return selected
    }

    private fun constraintsJson(constraints: QueryConstraints): JSONObject = JSONObject()
        .put("since", constraints.since)
        .put("until", constraints.until)
        .put("source", constraints.source)
        .put("app", constraints.app)
        .put("sender", constraints.sender)
        .put("semanticQuery", constraints.semanticQuery)
        .put("temporal", constraints.temporal)

    private suspend fun constrainedSearch(
        match: String,
        constraints: QueryConstraints,
        limit: Int
    ): List<MemoryEvent> = runCatching {
        dao.searchConstrained(
            match,
            constraints.since,
            constraints.until,
            constraints.source,
            constraints.app,
            constraints.sender,
            limit
        )
    }.getOrDefault(emptyList())

    private suspend fun constrainedRecent(constraints: QueryConstraints, limit: Int): List<MemoryEvent> =
        dao.recentConstrained(
            constraints.since,
            constraints.until,
            constraints.source,
            constraints.app,
            constraints.sender,
            limit
        )

    private fun recencyScore(event: MemoryEvent, constraints: QueryConstraints): Float {
        val ageMillis = (System.currentTimeMillis() - event.timestamp).coerceAtLeast(0L)
        val halfLife = if (constraints.temporal) TEMPORAL_HALF_LIFE_MILLIS else DEFAULT_HALF_LIFE_MILLIS
        return (RECENCY_WEIGHT * exp(-ageMillis.toDouble() / halfLife)).toFloat()
    }

    private fun diversify(events: List<MemoryEvent>, limit: Int): List<MemoryEvent> {
        val selected = mutableListOf<MemoryEvent>()
        val captureCounts = mutableMapOf<String, Int>()
        val sessionCounts = mutableMapOf<Long, Int>()
        val deferred = mutableListOf<MemoryEvent>()
        events.forEach { event ->
            val capture = "${event.source}|${event.pkg}|${event.timestamp}"
            if (captureCounts.getOrDefault(capture, 0) >= MAX_PER_CAPTURE ||
                sessionCounts.getOrDefault(event.sessionId, 0) >= MAX_PER_SESSION
            ) {
                deferred += event
            } else if (selected.size < limit) {
                selected += event
                captureCounts[capture] = captureCounts.getOrDefault(capture, 0) + 1
                sessionCounts[event.sessionId] = sessionCounts.getOrDefault(event.sessionId, 0) + 1
            }
        }
        if (selected.size < limit) {
            selected += deferred.take(limit - selected.size)
        }
        return selected
    }

    private fun terms(query: String): List<String> =
        query.trim()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.length > 1 && it.lowercase() !in stopwords }

    private companion object {
        val stopwords = setOf(
            "what", "was", "is", "the", "a", "an", "i", "me", "my", "did", "do", "does",
            "about", "say", "said", "to", "of", "in", "on", "at", "for", "and", "or", "just",
            "doing", "who", "when", "where", "which", "how", "tell", "send", "sent", "show",
            "find", "message", "messages", "notification", "notifications"
        )

        const val MAX_SEMANTIC_CANDIDATES = 5000
        const val CANDIDATE_MULTIPLIER = 5
        const val MAX_PER_CAPTURE = 2
        const val MAX_PER_SESSION = 4
        const val RECENCY_WEIGHT = 0.004
        const val TEMPORAL_HALF_LIFE_MILLIS = 12.0 * 60 * 60 * 1000
        const val DEFAULT_HALF_LIFE_MILLIS = 7.0 * 24 * 60 * 60 * 1000
    }
}
