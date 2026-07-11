package com.wake.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wake.app.data.MemoryEvent
import com.wake.app.data.SOURCE_NOTIFICATION
import com.wake.app.data.displaySource
import com.wake.app.ui.ChatMessage
import com.wake.app.ui.ChatViewModel
import com.wake.app.ui.WakeTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WakeTheme {
                ChatScreen(::openNotificationAccess, ::openAccessibility)
            }
        }
    }

    private fun openNotificationAccess() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    onNotifications: () -> Unit,
    onAccessibility: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messages by chatViewModel.messages.collectAsState()
    val busy by chatViewModel.busy.collectAsState()
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var deepResearchEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(
                onNotifications = { showNotifications = true },
                onSettings = { showSettings = true }
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyState(onSuggestion = chatViewModel::send)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = 14.dp,
                            bottom = 14.dp
                        )
                    ) {
                        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                            val previousRole = messages.getOrNull(index - 1)?.role
                            MessageBubble(
                                message = message,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(top = if (index == 0) 0.dp else if (previousRole == message.role) 6.dp else 14.dp)
                            )
                        }
                    }
                }
            }
            InputBar(
                query = query,
                onQueryChange = { query = it },
                enabled = !busy && query.isNotBlank(),
                deepResearchEnabled = deepResearchEnabled,
                onDeepResearchChange = { deepResearchEnabled = it },
                onSend = {
                    if (!busy && query.isNotBlank()) {
                        chatViewModel.send(query, deepResearchEnabled)
                        query = ""
                    }
                }
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            onNotifications = onNotifications,
            onAccessibility = onAccessibility
        )
    }
    if (showNotifications) {
        NotificationChatSheet(
            onDismiss = { showNotifications = false },
            onAsk = { event ->
                showNotifications = false
                chatViewModel.askAboutNotification(event)
            }
        )
    }
}

@Composable
private fun TopBar(onNotifications: () -> Unit, onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Wake",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
            Box(
                modifier = Modifier.padding(start = 7.dp, top = 2.dp).size(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {}
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onNotifications) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Captured notifications")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Engine settings")
            }
        }
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        "What was I just doing?",
        "Any messages I haven't replied to?",
        "What did I read about today?"
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = "W",
                modifier = Modifier.padding(horizontal = 19.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = "Ask anything about what you've seen.",
            modifier = Modifier.padding(top = 20.dp, bottom = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        suggestions.forEach { suggestion ->
            FilterChip(
                selected = false,
                onClick = { onSuggestion(suggestion) },
                label = { Text(suggestion) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val user = message.role == "user"
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .then(if (user) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth())
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            shape = if (user) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
            } else {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
            },
            color = if (user) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        ) {
            if (message.streaming && message.text.isEmpty()) {
                TypingIndicator(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp))
            } else {
                SelectionContainer {
                    Text(
                        text = formattedChatText(message.text),
                        modifier = Modifier.padding(horizontal = if (user) 16.dp else 4.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        color = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formattedChatText(value: String) = buildAnnotatedString {
    value.lines().forEachIndexed { index, rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(line.drop(4)) }
            line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append(line.drop(3)) }
            line.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) { append(line.drop(2)) }
            line.startsWith("- ") || line.startsWith("* ") -> {
                append("•  ")
                appendInlineFormatting(line.drop(2))
            }
            else -> appendInlineFormatting(line)
        }
        if (index < value.lines().lastIndex) append('\n')
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineFormatting(value: String) {
    var cursor = 0
    while (cursor < value.length) {
        val bold = value.indexOf("**", cursor)
        val code = value.indexOf('`', cursor)
        val next = listOf(bold, code).filter { it >= 0 }.minOrNull()
        if (next == null) {
            append(value.substring(cursor))
            return
        }
        append(value.substring(cursor, next))
        if (next == bold) {
            val end = value.indexOf("**", next + 2)
            if (end < 0) {
                append(value.substring(next))
                return
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.substring(next + 2, end)) }
            cursor = end + 2
        } else {
            val end = value.indexOf('`', next + 1)
            if (end < 0) {
                append(value.substring(next))
                return
            }
            withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color(0xFF65D6C5))) {
                append(value.substring(next + 1, end))
            }
            cursor = end + 1
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationChatSheet(onDismiss: () -> Unit, onAsk: (MemoryEvent) -> Unit) {
    var notifications by remember { mutableStateOf<List<MemoryEvent>>(emptyList()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        notifications = WakeApp.instance.dao.since(
            System.currentTimeMillis() - 7 * 86_400_000L,
            100
        ).filter { it.source == SOURCE_NOTIFICATION }.take(30)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification memory", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Recent conversations captured by Wake",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
            if (notifications.isEmpty()) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("No notifications captured yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Enable notification access in Settings. New messages will appear here.",
                            modifier = Modifier.padding(top = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(notifications, key = { _, event -> event.id }) { _, event ->
                        val source = event.displaySource()
                        Surface(
                            onClick = {
                                onAsk(event)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            source.take(1).uppercase(Locale.getDefault()),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                                        Text(event.sender ?: source, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "$source · ${timeFormat.format(Date(event.timestamp))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    event.text,
                                    modifier = Modifier.padding(top = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3
                                )
                                Text(
                                    "Ask Wake about this",
                                    modifier = Modifier.padding(top = 9.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1050
                        0.28f at index * 175
                        1f at index * 175 + 350 using FastOutSlowInEasing
                        0.28f at index * 175 + 700
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "typingDot$index"
            )
            Box(
                modifier = Modifier.size(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                ) {}
            }
        }
    }
}

@Composable
private fun InputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    deepResearchEnabled: Boolean,
    onDeepResearchChange: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "sendScale"
    )
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deep Research",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (deepResearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (deepResearchEnabled) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Switch(
                    checked = deepResearchEnabled,
                    onCheckedChange = onDeepResearchChange,
                    modifier = Modifier.scale(0.8f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (deepResearchEnabled) {
                    Text(
                        text = "iAPI Cloud Sandbox enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (deepResearchEnabled) "Ask cloud sandbox (with tools)" else "Ask your phone") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() })
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    modifier = Modifier.size(48.dp).scale(scale)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    onDismiss: () -> Unit,
    onNotifications: () -> Unit,
    onAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var engine by remember { mutableStateOf(Prefs.engineChoice(context)) }
    var apiKey by remember { mutableStateOf(Prefs.savedGeminiApiKey(context)) }
    var recent by remember { mutableStateOf<List<MemoryEvent>>(emptyList()) }
    var retentionDays by remember { mutableStateOf(Prefs.retentionDays(context)) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    val localModelState by WakeApp.instance.gemmaEngine.stateFlow.collectAsState()
    val activity = context as ComponentActivity
    val refreshPermissions = {
        notificationsEnabled = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        accessibilityEnabled = isAccessibilityEnabled(context)
    }

    DisposableEffect(activity) {
        refreshPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        recent = WakeApp.instance.dao.since(System.currentTimeMillis() - 24 * 60 * 60_000L, Int.MAX_VALUE)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineChoice("Gemma on-device", "gemma", engine) {
                    engine = it
                    WakeApp.instance.selectEngine(it)
                }
                EngineChoice("Gemma cloud", "gemma_cloud", engine) {
                    engine = it
                    WakeApp.instance.selectEngine(it)
                }
                EngineChoice("Gemini cloud", "gemini", engine) {
                    engine = it
                    WakeApp.instance.selectEngine(it)
                }
            }
            Text(
                text = engineStatus(engine, apiKey, localModelState),
                style = MaterialTheme.typography.bodySmall,
                color = if (engine == "gemma" && !WakeApp.instance.gemmaEngine.isReady()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    Prefs.setGeminiApiKey(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text("Required for cloud engines") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Text("Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CaptureRow(
                if (notificationsEnabled) "Notifications enabled" else "Notifications disabled",
                Icons.Outlined.Notifications,
                onNotifications
            )
            CaptureRow(
                if (accessibilityEnabled) "Screen memory enabled" else "Screen memory disabled",
                Icons.Outlined.Settings,
                onAccessibility
            )
            Text("Memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${recent.size} events captured in the last 24 hours", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7 days", 30 to "30 days", 0 to "Forever").forEach { (days, label) ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = {
                            retentionDays = days
                            Prefs.setRetentionDays(context, days)
                            scope.launch {
                                WakeApp.instance.applyRetention()
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            TextButton(onClick = { showClearConfirmation = true }) { Text("Clear all memory") }
            if (recent.isNotEmpty()) {
                Text("Recent captures", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                recent.take(5).forEach { event ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = event.displaySource(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(event.text.take(100), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear all memory?") },
            text = { Text("This permanently removes every captured notification, screen memory, and embedding from Wake.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    scope.launch {
                        WakeApp.instance.dao.clear()
                        recent = emptyList()
                    }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EngineChoice(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CaptureRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClick) { Text("Open") }
    }
}

private fun engineStatus(engine: String, savedKey: String, localState: com.wake.app.gemma.GemmaEngine.State): String = when (engine) {
    "gemma" -> when (localState) {
        com.wake.app.gemma.GemmaEngine.State.Missing -> "Model file missing (${com.wake.app.gemma.GemmaEngine.MODEL_FILE})"
        com.wake.app.gemma.GemmaEngine.State.Loading -> "Loading model"
        is com.wake.app.gemma.GemmaEngine.State.Ready -> "Ready on ${localState.backend}"
        is com.wake.app.gemma.GemmaEngine.State.Failed -> "Model failed: ${localState.message}"
    }
    else -> if (savedKey.isNotBlank() || Prefs.geminiApiKey(WakeApp.instance).isNotBlank()) {
        "Cloud API key ready"
    } else {
        "Cloud API key required"
    }
}

private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val service = "${context.packageName}/${com.wake.app.capture.ScreenTextService::class.java.name}"
    return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
}
