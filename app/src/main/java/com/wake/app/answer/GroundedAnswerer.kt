package com.wake.app.answer

import com.wake.app.data.MemoryEvent
import com.wake.app.retrieval.Retriever
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GroundedAnswerer(
    private val retriever: Retriever,
    private val llm: com.wake.app.llm.LlmEngine,
    private val topK: Int = 8
) {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    suspend fun answer(query: String): Flow<String> {
        val events = retriever.hybrid(query, topK).ifEmpty { retriever.recent(120, topK) }
        if (events.isEmpty()) return flowOf("Not found in memory. Enable Notifications and Screen memory access, then use your phone for a bit.")
        return llm.generate(prompt(query, events))
    }

    private fun prompt(query: String, events: List<MemoryEvent>): String = buildString {
        append("""
            You are Wake, an on-device memory assistant.
            Answer ONLY from the memory context below. Do not use outside knowledge or make up details.
            Cite every factual claim with [source, time]. If the context does not answer the question, say exactly: Not found in memory.

            Memory context:
            """.trimIndent())
        append('\n')
        events.forEachIndexed { index, event ->
            append(index + 1)
            append(". [")
            append(event.appLabel ?: event.pkg ?: event.source)
            append(", ")
            append(timeFmt.format(Date(event.timestamp)))
            append("] ")
            append(event.text)
            append('\n')
        }
        append("\nQuestion: ")
        append(query)
        append("\nAnswer:")
    }
}
