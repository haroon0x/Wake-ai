package com.wake.app.agent

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wake.app.MainActivity
import com.wake.app.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ActionExecutor.EXTRA_TASK_ID, 0L)
        val title = intent.getStringExtra(ActionExecutor.EXTRA_TITLE) ?: "Wake reminder"
        val text = intent.getStringExtra(ActionExecutor.EXTRA_TEXT).orEmpty()
        val open = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ActionExecutor.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify((taskId + REMINDER_ID_OFFSET).toInt(), notification)
        }
    }

    private companion object {
        const val REMINDER_ID_OFFSET = 100_000L
    }
}
