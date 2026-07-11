package com.wake.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wake.app.answer.DeterministicComposer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composer = DeterministicComposer(WakeApp.instance.retriever)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AskScreen(composer, ::openNotificationAccess, ::openAccessibility)
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
    var query by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wake", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNotif) { Text("Notifications") }
            OutlinedButton(onClick = onA11y) { Text("Screen memory") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Ask your phone") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch { answer = composer.answer(query.ifBlank { "what was i just doing" }) }
            }) { Text("Ask") }
            OutlinedButton(onClick = {
                scope.launch {
                    val events = WakeApp.instance.retriever.recent(30)
                    recent = events.map { "${it.appLabel ?: it.pkg}: ${it.text.take(70)}" }
                }
            }) { Text("Recent") }
        }

        if (answer.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(answer, modifier = Modifier.padding(12.dp))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(recent) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
