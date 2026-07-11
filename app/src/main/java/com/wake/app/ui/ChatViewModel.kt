package com.wake.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wake.app.WakeApp
import com.wake.app.data.MemoryEvent
import com.wake.app.data.withoutPackageIdentifiers
import com.wake.app.llm.LlmException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

data class ChatMessage(
    val id: Long,
    val role: String,
    val text: String,
    val streaming: Boolean = false
)

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var nextId = 0L

    fun send(query: String, deepResearch: Boolean = false) {
        send(query, emptyList(), deepResearch)
    }

    fun askAboutNotification(event: MemoryEvent) {
        val source = event.appLabel ?: event.sender ?: "this notification"
        send("Summarize this notification from $source.", listOf(event), false)
    }

    private fun send(query: String, selectedEvents: List<MemoryEvent>, deepResearch: Boolean) {
        val prompt = query.trim()
        if (prompt.isEmpty() || _busy.value) return
        _busy.value = true
        val userId = nextId++
        val assistantId = nextId++
        WakeApp.instance.diagnostics.record(
            "chat_query",
            "query" to prompt,
            "selectedEventIds" to JSONArray(selectedEvents.map { it.id }),
            "deepResearch" to deepResearch
        )
        _messages.update {
            it + ChatMessage(userId, "user", prompt) + ChatMessage(assistantId, "assistant", "", true)
        }
        viewModelScope.launch {
            try {
                if (deepResearch) {
                    val events = if (selectedEvents.isNotEmpty()) {
                        selectedEvents
                    } else {
                        WakeApp.instance.retriever.hybrid(prompt, 12)
                    }
                    val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    val agentPrompt = buildString {
                        append("Captured mobile context:\n")
                        events.forEachIndexed { index, event ->
                            append("${index + 1}. [${event.appLabel ?: event.pkg ?: event.source}, ")
                            append(timeFmt.format(java.util.Date(event.timestamp)))
                            append("] ")
                            append(event.text.take(1000))
                            append("\n")
                        }
                        append("\nUser query: ")
                        append(prompt)
                        append("\n\nInstructions: Analyze the captured mobile context. You have access to Google Search and a Python code execution sandbox. Autonomously research and verify the solution, compile scripts if needed, and write a thorough, detailed answer.")
                    }
                    WakeApp.instance.antigravityEngine.generate(agentPrompt).collect { chunk ->
                        _messages.update { current ->
                            current.map { message ->
                                if (message.id == assistantId) {
                                    message.copy(text = (message.text + chunk).withoutPackageIdentifiers())
                                } else {
                                    message
                                }
                            }
                        }
                    }
                } else {
                    WakeApp.instance.answerer().answer(prompt, selectedEvents).collect { chunk ->
                        _messages.update { current ->
                            current.map { message ->
                                if (message.id == assistantId) {
                                    message.copy(text = (message.text + chunk).withoutPackageIdentifiers())
                                } else {
                                    message
                                }
                            }
                        }
                    }
                }
            } catch (e: LlmException) {
                setAssistantMessage(assistantId, e.message ?: "The model request failed.")
            } catch (e: Exception) {
                setAssistantMessage(assistantId, "Something went wrong. Please try again.")
            } finally {
                _messages.update { current ->
                    current.map { message ->
                        if (message.id == assistantId) message.copy(streaming = false) else message
                    }
                }
                val answer = _messages.value.firstOrNull { it.id == assistantId }?.text.orEmpty()
                WakeApp.instance.diagnostics.record("chat_answer", "query" to prompt, "answer" to answer)
                _busy.value = false
            }
        }
    }

    private fun setAssistantMessage(id: Long, text: String) {
        _messages.update { current ->
            current.map { message ->
                if (message.id == id) message.copy(text = text) else message
            }
        }
    }
}
