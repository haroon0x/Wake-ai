package com.wake.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.wake.app.WakeApp
import com.wake.app.data.DIRECTION_INCOMING
import com.wake.app.data.DIRECTION_OUTGOING
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
            val sender = last.person?.name?.toString()
            val conversationTitle = style.conversationTitle?.toString()
            submit(
                sbn.postTime,
                pkg,
                appLabel,
                sender,
                conversationId(pkg, n.shortcutId, conversationTitle, sender, sbn.groupKey),
                messageDirection(style, last),
                last.text?.toString().orEmpty(),
                notificationContext(sbn, n, conversationTitle)
            )
            return
        }

        val title = n.extras.getCharSequence("android.title")?.toString()
        val text = n.extras.getCharSequence("android.text")?.toString().orEmpty()
        submit(
            sbn.postTime,
            pkg,
            appLabel,
            title,
            conversationId(pkg, n.shortcutId, null, title, sbn.groupKey),
            DIRECTION_INCOMING,
            text,
            notificationContext(sbn, n, null)
        )
    }

    private fun submit(
        ts: Long,
        pkg: String,
        appLabel: String?,
        sender: String?,
        conversationId: String,
        direction: String,
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
                conversationId = conversationId,
                direction = direction,
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
        .put("shortcutId", notification.shortcutId)
        .put("conversationTitle", conversationTitle)
        .toString()

    private fun conversationId(
        pkg: String,
        shortcutId: String?,
        conversationTitle: String?,
        sender: String?,
        groupKey: String?
    ): String {
        val identity = when {
            !shortcutId.isNullOrBlank() -> "shortcut:${shortcutId.trim()}"
            !conversationTitle.isNullOrBlank() -> "title:${conversationTitle.trim().lowercase()}"
            !sender.isNullOrBlank() -> "sender:${sender.trim().lowercase()}"
            !groupKey.isNullOrBlank() -> "group:${groupKey.trim()}"
            else -> "general"
        }
        return "$pkg|$identity"
    }

    private fun messageDirection(
        style: NotificationCompat.MessagingStyle,
        message: NotificationCompat.MessagingStyle.Message
    ): String {
        val author = message.person ?: return DIRECTION_INCOMING
        val user = style.user
        val sameKey = !author.key.isNullOrBlank() && author.key == user.key
        val sameName = author.name?.toString()?.trim()?.equals(
            user.name?.toString()?.trim(),
            ignoreCase = true
        ) == true
        return if (sameKey || sameName) DIRECTION_OUTGOING else DIRECTION_INCOMING
    }

    private fun labelFor(pkg: String): String? = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }
}
