package com.wake.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wake.app.WakeApp
import com.wake.app.data.MemoryEvent
import com.wake.app.data.SOURCE_NOTIFICATION
import com.wake.app.data.displaySource
import com.wake.app.data.withoutPackageIdentifiers
import com.wake.app.llm.LlmException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ThreadMessage(
    val id: Long,
    val role: String,
    val text: String,
    val time: Long,
    val streaming: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationChatSheet(onDismiss: () -> Unit) {
    var notifications by remember { mutableStateOf<List<MemoryEvent>>(emptyList()) }
    var selectedSender by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        notifications = WakeApp.instance.dao.since(
            System.currentTimeMillis() - 7 * 86_400_000L,
            300
        ).filter { it.source == SOURCE_NOTIFICATION && !it.sender.isNullOrBlank() }
    }

    val conversations = remember(notifications) {
        notifications.groupBy { it.sender!! }
            .toList()
            .sortedByDescending { (_, events) -> events.maxOf { it.timestamp } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            val sender = selectedSender
            if (sender == null) {
                ConversationList(
                    conversations = conversations,
                    onOpen = { selectedSender = it },
                    onDismiss = onDismiss
                )
            } else {
                ConversationThread(
                    sender = sender,
                    events = conversations.firstOrNull { it.first == sender }?.second.orEmpty(),
                    onBack = { selectedSender = null }
                )
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<Pair<String, List<MemoryEvent>>>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Conversations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Captured this week — open one to chat with Wake about it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Close")
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (conversations.isEmpty()) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text("No conversations yet", fontWeight = FontWeight.SemiBold)
                Text(
                    "Enable notification access in Settings. Messages will appear here as conversations.",
                    modifier = Modifier.padding(top = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(500.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations, key = { it.first }) { (sender, events) ->
                val latest = events.maxBy { it.timestamp }
                Surface(
                    onClick = { onOpen(sender) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(sender)
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sender, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(
                                    timeFormat.format(Date(latest.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                latest.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                "${latest.displaySource()} · ${events.size} message${if (events.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationThread(
    sender: String,
    events: List<MemoryEvent>,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var query by remember(sender) { mutableStateOf("") }
    var busy by remember(sender) { mutableStateOf(false) }
    var thread by remember(sender) {
        mutableStateOf(
            events.sortedBy { it.timestamp }.map {
                ThreadMessage(id = it.id, role = "them", text = it.text, time = it.timestamp)
            }
        )
    }
    var nextId by remember(sender) { mutableStateOf(-1L) }

    LaunchedEffect(thread.size, thread.lastOrNull()?.text?.length) {
        if (thread.isNotEmpty()) listState.scrollToItem(thread.lastIndex)
    }

    fun ask() {
        val question = query.trim()
        if (question.isEmpty() || busy) return
        busy = true
        query = ""
        val askId = nextId--
        val answerId = nextId--
        val now = System.currentTimeMillis()
        thread = thread +
            ThreadMessage(askId, "user", question, now) +
            ThreadMessage(answerId, "wake", "", now, streaming = true)
        scope.launch {
            try {
                WakeApp.instance.answerer().answer(question, events).collect { chunk ->
                    thread = thread.map {
                        if (it.id == answerId) it.copy(text = (it.text + chunk).withoutPackageIdentifiers()) else it
                    }
                }
            } catch (e: LlmException) {
                thread = thread.map {
                    if (it.id == answerId) it.copy(text = e.message ?: "The model request failed.") else it
                }
            } catch (e: Exception) {
                thread = thread.map {
                    if (it.id == answerId) it.copy(text = "Something went wrong. Please try again.") else it
                }
            } finally {
                thread = thread.map { if (it.id == answerId) it.copy(streaming = false) else it }
                busy = false
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to conversations")
        }
        Avatar(sender)
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(sender, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${events.size} captured message${if (events.size == 1) "" else "s"} · ask Wake about them",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(430.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(thread, key = { it.id }) { message ->
            when (message.role) {
                "them" -> Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.widthIn(max = 300.dp),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                timeFormat.format(Date(message.time)),
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                "user" -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        modifier = Modifier.widthIn(max = 300.dp),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            message.text,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                else -> Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(
                                "Wake",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (message.streaming && message.text.isEmpty()) "Thinking…" else message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about this conversation") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { ask() })
        )
        FilledIconButton(onClick = { ask() }, enabled = !busy && query.isNotBlank(), modifier = Modifier.size(46.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
        Text(
            name.take(1).uppercase(Locale.getDefault()),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}
