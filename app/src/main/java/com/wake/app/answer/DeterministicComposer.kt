package com.wake.app.answer

import com.wake.app.data.MemoryEvent
import com.wake.app.data.SOURCE_NOTIFICATION
import com.wake.app.data.displaySource
import com.wake.app.data.withoutPackageIdentifiers
import com.wake.app.retrieval.Retriever
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeterministicComposer(private val retriever: Retriever) {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    suspend fun answer(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("just doing") || q.contains("what was i") || q.contains("what did i") ->
                recentActivity()
            q.contains("reply") || q.contains("respond") || q.contains("follow up") ->
                pendingReplies()
            else -> searchAnswer(query)
        }
    }

    private suspend fun recentActivity(): String {
        val events = retriever.recent(30)
        if (events.isEmpty()) return "No memory captured in the last 30 minutes yet."
        val apps = events.map { it.displaySource() }
            .distinct()
            .take(6)
        val first = events.minByOrNull { it.timestamp }!!
        return buildString {
            append("In the last 30 minutes you used: ")
            append(apps.joinToString(", "))
            append(".\nStarting around ")
            append(timeFmt.format(Date(first.timestamp)))
            append(". ")
            val notable = events.firstOrNull { it.text.length in 12..200 }
            if (notable != null) {
                append("Most recent notable item: \"")
                append(notable.text.withoutPackageIdentifiers().take(120))
                append("\" (")
                append(notable.displaySource())
                append(").")
            }
        }
    }

    private suspend fun pendingReplies(): String {
        val notifs = retriever.recent(180).filter { it.source == SOURCE_NOTIFICATION && it.sender != null }
        if (notifs.isEmpty()) return "Nothing looks like it needs a reply right now."
        return buildString {
            append("These look like possible follow-ups:\n")
            notifs.take(5).forEach {
                append("• ")
                append(it.sender)
                append(": \"")
                append(it.text.withoutPackageIdentifiers().take(80))
                append("\" (")
                append(timeFmt.format(Date(it.timestamp)))
                append(")\n")
            }
        }
    }

    private suspend fun searchAnswer(query: String): String {
        val hits = retriever.search(query)
        if (hits.isEmpty()) return "Not found in memory."
        val top = hits.first()
        return buildString {
            append("Found: \"")
            append(top.text.withoutPackageIdentifiers().take(160))
            append("\"\nSource: ")
            append(top.displaySource())
            append(", ")
            append(timeFmt.format(Date(top.timestamp)))
            append(".")
            if (hits.size > 1) append("\n(+${hits.size - 1} more matches)")
        }
    }

    fun format(events: List<MemoryEvent>): String =
        events.joinToString("\n") {
            "${timeFmt.format(Date(it.timestamp))}  ${it.displaySource()}: ${it.text.withoutPackageIdentifiers().take(80)}"
        }
}
