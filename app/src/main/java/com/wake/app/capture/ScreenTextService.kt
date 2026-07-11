package com.wake.app.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wake.app.WakeApp
import com.wake.app.data.SOURCE_SCREEN_TEXT

class ScreenTextService : AccessibilityService() {

    private var lastCaptureAt = 0L
    private var lastPkg: String? = null
    private val throttleMillis = 1500L
    private val maxChars = 4000

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        if (Exclusions.isExcluded(pkg)) return

        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastCaptureAt < throttleMillis) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb)
        val text = sb.toString().trim()
        if (text.length < 8) return

        lastCaptureAt = now
        lastPkg = pkg

        WakeApp.instance.ingest.submit(
            RawCapture(
                timestamp = now,
                source = SOURCE_SCREEN_TEXT,
                pkg = pkg,
                appLabel = labelFor(pkg),
                sender = null,
                text = text.take(maxChars)
            )
        )
    }

    private fun collect(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        node ?: return
        if (sb.length >= maxChars) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), sb)
        }
    }

    private fun labelFor(pkg: String): String? = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    override fun onInterrupt() {}
}
