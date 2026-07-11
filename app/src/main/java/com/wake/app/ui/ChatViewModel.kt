package com.wake.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wake.app.WakeApp
import com.wake.app.data.MemoryEvent
import com.wake.app.llm.LlmException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun send(query: String) {
        send(query, emptyList())
    }

    fun askAboutNotification(event: MemoryEvent) {
        val source = event.appLabel ?: event.sender ?: "this notification"
        send("Summarize this notification from $source.", listOf(event))
    }

    private fun send(query: String, selectedEvents: List<MemoryEvent>) {
        val prompt = query.trim()
        if (prompt.isEmpty() || _busy.value) return
        _busy.value = true
        val userId = nextId++
        val assistantId = nextId++
        _messages.update {
            it + ChatMessage(userId, "user", prompt) + ChatMessage(assistantId, "assistant", "", true)
        }
        viewModelScope.launch {
            try {
                WakeApp.instance.answerer().answer(prompt, selectedEvents).collect { chunk ->
                    _messages.update { current ->
                        current.map { message ->
                            if (message.id == assistantId) message.copy(text = message.text + chunk) else message
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
