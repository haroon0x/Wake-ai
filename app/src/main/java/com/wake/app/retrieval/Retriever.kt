package com.wake.app.retrieval

import com.wake.app.data.MemoryDao
import com.wake.app.data.MemoryEvent
import com.wake.app.data.toFloatArray

class Retriever(
    private val dao: MemoryDao,
    private val embedder: Embedder? = null
) {

    suspend fun recent(minutes: Int, limit: Int = 200): List<MemoryEvent> {
        val since = System.currentTimeMillis() - minutes * 60_000L
        return dao.since(since, limit)
    }

    suspend fun search(query: String, limit: Int = 40): List<MemoryEvent> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        val andMatch = terms.joinToString(" ") { "$it*" }
        val results = runCatching { dao.search(andMatch, limit) }.getOrDefault(emptyList())
        if (results.isNotEmpty()) return results
        val orMatch = terms.joinToString(" OR ") { "$it*" }
        return runCatching { dao.search(orMatch, limit) }.getOrDefault(emptyList())
    }

    suspend fun semantic(query: String, limit: Int = 8): List<MemoryEvent> {
        val queryEmbedding = embedder?.embed(query) ?: return emptyList()
        return dao.embedded(2000)
            .mapNotNull { event ->
                event.embedding?.toFloatArray()?.let { embedding ->
                    event to Embedder.cosine(queryEmbedding, embedding)
                }
            }
            .filter { (_, score) -> score > 0.3f }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (event, _) -> event }
    }

    suspend fun hybrid(query: String, limit: Int = 8): List<MemoryEvent> {
        val lexical = search(query, limit)
        if (lexical.size >= limit) return lexical
        return (lexical + semantic(query, limit))
            .distinctBy { it.id }
            .take(limit)
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
            "doing", "who", "when", "where", "which", "how", "tell"
        )
    }
}
