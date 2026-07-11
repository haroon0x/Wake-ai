package com.wake.app.retrieval

import com.wake.app.data.MemoryDao
import com.wake.app.data.MemoryEvent

class Retriever(private val dao: MemoryDao) {

    suspend fun recent(minutes: Int, limit: Int = 200): List<MemoryEvent> {
        val since = System.currentTimeMillis() - minutes * 60_000L
        return dao.since(since, limit)
    }

    suspend fun search(query: String, limit: Int = 40): List<MemoryEvent> {
        val match = toMatch(query)
        if (match.isBlank()) return emptyList()
        return runCatching { dao.search(match, limit) }.getOrDefault(emptyList())
    }

    private fun toMatch(query: String): String =
        query.trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .joinToString(" ") { "${it.replace("\"", "")}*" }
}
