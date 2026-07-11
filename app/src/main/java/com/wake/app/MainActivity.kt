package com.wake.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wake.app.answer.DeterministicComposer
import com.wake.app.gemma.GemmaEngine
import com.wake.app.ui.WakeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composer = DeterministicComposer(WakeApp.instance.retriever)
        setContent {
            WakeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AskScreen(composer, ::openNotificationAccess, ::openAccessibility)
                }
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

@Composable
private fun AskScreen(
    composer: DeterministicComposer,
    onNotif: () -> Unit,
    onA11y: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(listOf<String>()) }
    var engine by remember { mutableStateOf(Prefs.engineChoice(context)) }
    var apiKey by remember { mutableStateOf(Prefs.geminiApiKey(context).orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ask: () -> Unit = {
        if (!busy) {
            busy = true
            answer = ""
            scope.launch {
                try {
                    WakeApp.instance.answerer().answer(query).collect { answer += it }
                } finally {
                    busy = false
                }
            }
        }
    }
    val status = if (engine == "gemma") {
        if (WakeApp.instance.gemmaEngine.isReady()) "Gemma model: ready"
        else "Gemma model: file missing (${GemmaEngine.MODEL_FILE})"
    } else {
        if (Prefs.geminiApiKey(context).isNullOrBlank()) "Gemini key: missing" else "Gemini key: set"
    }
    val ready = (engine == "gemma" && WakeApp.instance.gemmaEngine.isReady()) ||
        (engine == "gemini" && !Prefs.geminiApiKey(context).isNullOrBlank())

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Wake",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = "Your phone remembers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onNotif) { Text("Notifications") }
            TextButton(onClick = onA11y) { Text("Screen memory") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = engine == "gemma",
                onClick = {
                    engine = "gemma"
                    Prefs.setEngineChoice(context, engine)
                },
                label = { Text("Gemma (on-device)") }
            )
            FilterChip(
                selected = engine == "gemini",
                onClick = {
                    engine = "gemini"
                    Prefs.setEngineChoice(context, engine)
                },
                label = { Text("Gemini (cloud)") }
            )
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
        )

        if (engine == "gemini") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    Prefs.setGeminiApiKey(context, it)
                },
                label = { Text("Gemini API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Ask your phone") },
            trailingIcon = {
                TextButton(onClick = ask, enabled = !busy) { Text("Ask") }
            },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { ask() }),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ask, enabled = !busy) { Text(if (busy) "Asking…" else "Ask") }
            TextButton(onClick = {
                scope.launch {
                    val events = WakeApp.instance.retriever.recent(30)
                    recent = events.map { "${it.appLabel ?: it.pkg}: ${it.text.take(70)}" }
                }
            }) { Text("Recent") }
            TextButton(onClick = {
                scope.launch { answer = composer.answer(query.ifBlank { "what was i just doing" }) }
            }) { Text("Quick") }
        }

        if (answer.isNotBlank() || busy) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (busy && answer.isBlank()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(12.dp))
                } else {
                    Text(answer, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (recent.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(recent) { line ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        val separator = line.indexOf(": ")
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (separator >= 0) line.substring(0, separator) else line,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (separator >= 0) {
                                Text(line.substring(separator + 2), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
