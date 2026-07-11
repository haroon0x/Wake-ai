package com.wake.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.wake.app.WakeApp
import org.json.JSONObject

class WakeNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val pkg = sbn.packageName
        val appLabel = labelFor(pkg)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)

        if (style != null && style.messages.isNotEmpty()) {
            val last = style.messages.last()
            submit(
                sbn.postTime,
                pkg,
                appLabel,
                last.person?.name?.toString(),
                last.text?.toString().orEmpty(),
                notificationContext(sbn, n, style.conversationTitle?.toString())
            )
            return
        }

        val title = n.extras.getCharSequence("android.title")?.toString()
        val text = n.extras.getCharSequence("android.text")?.toString().orEmpty()
        submit(sbn.postTime, pkg, appLabel, title, text, notificationContext(sbn, n, null))
    }

    private fun submit(
        ts: Long,
        pkg: String,
        appLabel: String?,
        sender: String?,
        text: String,
        structured: String
    ) {
        if (text.isBlank()) return
        WakeApp.instance.ingest.submit(
            RawCapture(
                timestamp = ts,
                source = com.wake.app.data.SOURCE_NOTIFICATION,
                pkg = pkg,
                appLabel = appLabel,
                sender = sender,
                text = text,
                structured = structured
            )
        )
    }

    private fun notificationContext(
        sbn: StatusBarNotification,
        notification: Notification,
        conversationTitle: String?
    ): String = JSONObject()
        .put("captureKind", "notification")
        .put("notificationKey", sbn.key)
        .put("groupKey", sbn.groupKey)
        .put("category", notification.category)
        .put("conversationTitle", conversationTitle)
        .toString()

    private fun labelFor(pkg: String): String? = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }
}
