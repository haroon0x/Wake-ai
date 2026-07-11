package com.wake.app.agent

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wake.app.MainActivity
import com.wake.app.R
import com.wake.app.data.ACTION_OPEN_APP
import com.wake.app.data.ACTION_REMIND
import com.wake.app.data.ACTION_REPLY
import com.wake.app.data.AgentTask

class ActionExecutor(private val context: Context) {

    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wake agent", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Suggestions and reminders from the Wake agent"
            }
        )
    }

    fun execute(task: AgentTask): String = when (task.action) {
        ACTION_REPLY -> {
            task.draft?.let { copyDraft(it) }
            val opened = openApp(task.pkg)
            when {
                task.draft != null && opened -> "Draft copied to clipboard, ${task.appLabel ?: "app"} opened — paste and send"
                task.draft != null -> "Draft copied to clipboard"
                opened -> "${task.appLabel ?: "App"} opened"
                else -> "Could not open ${task.appLabel ?: "the app"}"
            }
        }
        ACTION_REMIND -> {
            val minutes = task.reminderMinutes ?: DEFAULT_REMINDER_MINUTES
            scheduleReminder(task, minutes)
            "Reminder set for $minutes minutes from now"
        }
        ACTION_OPEN_APP -> {
            if (openApp(task.pkg)) "${task.appLabel ?: "App"} opened" else "Could not open ${task.appLabel ?: "the app"}"
        }
        else -> "Nothing to do"
    }

    fun notifyProposal(task: AgentTask) {
        val intent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TASK_ID, task.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Wake suggests")
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.title))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(task.id.toInt(), notification)
        }
    }

    private fun copyDraft(draft: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Wake draft", draft))
    }

    private fun openApp(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch) }.isSuccess
    }

    private fun scheduleReminder(task: AgentTask, minutes: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_TASK_ID, task.id)
                putExtra(EXTRA_TITLE, task.title)
                putExtra(EXTRA_TEXT, task.sourceText.take(200))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, intent)
    }

    companion object {
        const val CHANNEL_ID = "wake_agent"
        const val EXTRA_TASK_ID = "wake_task_id"
        const val EXTRA_TITLE = "wake_title"
        const val EXTRA_TEXT = "wake_text"
        const val DEFAULT_REMINDER_MINUTES = 30
    }
}
