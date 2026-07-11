package com.wake.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wake.app.data.AgentTask
import com.wake.app.data.STATUS_DISMISSED
import com.wake.app.data.STATUS_DONE
import com.wake.app.data.STATUS_EXPIRED
import com.wake.app.data.STATUS_PROPOSED
import com.wake.app.data.STATUS_THINKING
import com.wake.app.data.STATUS_WATCHING
import com.wake.app.data.traceSteps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSheet(
    onDismiss: () -> Unit,
    viewModel: AgentViewModel
) {
    val tasks by viewModel.tasks.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Agent activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Wake watches for unanswered messages and suggests actions. Nothing runs without your approval.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
            if (tasks.isEmpty()) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Nothing yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When a message goes unanswered, the agent will show up here — first watching, then thinking, then suggesting.",
                            modifier = Modifier.padding(top = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        AgentTaskCard(
                            task = task,
                            onApprove = { viewModel.approve(task) },
                            onDismissTask = { viewModel.dismiss(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentTaskCard(task: AgentTask, onApprove: () -> Unit, onDismissTask: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (task.status == STATUS_PROPOSED) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(task.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    timeFormat.format(Date(task.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                task.title,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val subtitle = listOfNotNull(task.sender, task.appLabel).distinct().joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.draft != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        "“${task.draft}”",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                task.traceSteps().takeLast(4).forEach { step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            timeFormat.format(Date(step.timestamp)),
                            modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            step.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (task.status == STATUS_PROPOSED) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                        Text(approveLabel(task))
                    }
                    TextButton(onClick = onDismissTask) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status) {
        STATUS_WATCHING -> "Watching" to MaterialTheme.colorScheme.secondary
        STATUS_THINKING -> "Thinking" to MaterialTheme.colorScheme.secondary
        STATUS_PROPOSED -> "Suggested" to MaterialTheme.colorScheme.primary
        STATUS_DONE -> "Done" to MaterialTheme.colorScheme.onSurfaceVariant
        STATUS_DISMISSED -> "Dismissed" to MaterialTheme.colorScheme.onSurfaceVariant
        STATUS_EXPIRED -> "Expired" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == STATUS_WATCHING || status == STATUS_THINKING) {
            val transition = rememberInfiniteTransition(label = "agentPulse")
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "agentPulseAlpha"
            )
            Box(modifier = Modifier.size(8.dp).alpha(alpha)) {
                Surface(shape = CircleShape, color = color, modifier = Modifier.fillMaxSize()) {}
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun approveLabel(task: AgentTask): String = when (task.action) {
    "reply" -> "Copy draft & open"
    "remind" -> "Set reminder"
    "open_app" -> "Open ${task.appLabel ?: "app"}"
    else -> "Do it"
}
