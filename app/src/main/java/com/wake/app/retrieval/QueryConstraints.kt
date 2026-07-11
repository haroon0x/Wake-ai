package com.wake.app.retrieval

import com.wake.app.data.MemoryDao
import com.wake.app.data.MemoryEvent
import com.wake.app.data.SOURCE_NOTIFICATION
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class QueryConstraints(
    val since: Long? = null,
    val until: Long? = null,
    val source: String? = null,
    val app: String? = null,
    val sender: String? = null,
    val semanticQuery: String,
    val temporal: Boolean = false
) {
    fun matches(event: MemoryEvent): Boolean {
        if (since != null && event.timestamp < since) return false
        if (until != null && event.timestamp >= until) return false
        if (source != null && event.source != source) return false
        if (app != null && listOfNotNull(event.appLabel, event.pkg).none { it.contains(app, ignoreCase = true) }) return false
        if (sender != null && event.sender?.contains(sender, ignoreCase = true) != true) return false
        return true
    }
}

class QueryInterpreter(private val dao: MemoryDao) {
    suspend fun interpret(query: String): QueryConstraints {
        val normalized = query.trim().lowercase()
        val now = ZonedDateTime.now()
        val time = timeRange(normalized, now)
        val app = bestMention(query, dao.knownApps(100).filterNotNull())
        val sender = bestMention(query, dao.knownSenders(200).filterNotNull())
        val source = if ("notification" in normalized || "notifications" in normalized) {
            SOURCE_NOTIFICATION
        } else {
            null
        }
        val semanticQuery = cleanedQuery(query, app, sender)
        return QueryConstraints(
            since = time?.first,
            until = time?.second,
            source = source,
            app = app,
            sender = sender,
            semanticQuery = semanticQuery,
            temporal = time != null
        )
    }

    private fun timeRange(query: String, now: ZonedDateTime): Pair<Long, Long?>? {
        val today = now.truncatedTo(ChronoUnit.DAYS)
        return when {
            "yesterday" in query -> today.minusDays(1).millis() to today.millis()
            "this morning" in query -> today.millis() to today.plusHours(12).millis()
            "this afternoon" in query -> today.plusHours(12).millis() to today.plusHours(18).millis()
            "tonight" in query -> today.plusHours(18).millis() to today.plusDays(1).millis()
            "today" in query -> today.millis() to today.plusDays(1).millis()
            "last two hours" in query -> now.minusHours(2).millis() to null
            "last hour" in query -> now.minusHours(1).millis() to null
            "recently" in query -> now.minusHours(2).millis() to null
            "just doing" in query || "what was i" in query -> now.minusMinutes(30).millis() to null
            else -> relativeRange(query, now)
        }
    }

    private fun relativeRange(query: String, now: ZonedDateTime): Pair<Long, Long?>? {
        val match = Regex("last\\s+(\\d+)\\s+(minute|minutes|hour|hours|day|days)").find(query) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val start = when (match.groupValues[2]) {
            "minute", "minutes" -> now.minusMinutes(amount)
            "hour", "hours" -> now.minusHours(amount)
            else -> now.minusDays(amount)
        }
        return start.millis() to null
    }

    private fun bestMention(query: String, values: List<String>): String? = values
        .filter(String::isNotBlank)
        .filter { query.contains(it, ignoreCase = true) }
        .maxByOrNull(String::length)

    private fun cleanedQuery(query: String, app: String?, sender: String?): String {
        var value = query
        timePhrases.forEach { value = value.replace(it, "", ignoreCase = true) }
        listOfNotNull(app, sender).forEach { value = value.replace(it, "", ignoreCase = true) }
        value = value.replace(Regex("last\\s+\\d+\\s+(minute|minutes|hour|hours|day|days)", RegexOption.IGNORE_CASE), "")
        val cleaned = value.replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { query }
    }

    private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()

    private companion object {
        val timePhrases = listOf(
            "yesterday", "this morning", "this afternoon", "tonight", "today",
            "last two hours", "last hour", "recently", "just doing"
        )
    }
}
