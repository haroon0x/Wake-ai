package com.wake.app.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wake.app.WakeApp
import com.wake.app.data.SOURCE_SCREEN_TEXT
import org.json.JSONObject

class ScreenTextService : AccessibilityService() {

    private var lastCaptureAt = 0L
    private var lastPkg: String? = null
    private val previousLines = mutableMapOf<String, Set<String>>()
    private val throttleMillis = 1500L
    private val maxChars = 4000
    private val maxLines = 500

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
        val lines = linkedSetOf<String>()
        collect(root, lines)
        val previous = previousLines[pkg].orEmpty()
        if (previous.isNotEmpty() && lines == previous) {
            lastCaptureAt = now
            lastPkg = pkg
            WakeApp.instance.diagnostics.record(
                "screen_drop",
                "reason" to "unchanged",
                "package" to pkg,
                "lineCount" to lines.size
            )
            return
        }
        val retainedRatio = if (lines.isEmpty()) 0f else lines.count(previous::contains).toFloat() / lines.size
        val added = lines.filterNot(previous::contains)
        val useDelta = previous.isNotEmpty() && retainedRatio >= 0.5f && added.isNotEmpty()
        val selected = if (useDelta) added else lines.toList()
        val text = selected.joinToString("\n").take(maxChars).trim()
        if (text.length < 8) {
            WakeApp.instance.diagnostics.record(
                "screen_drop",
                "reason" to "too_short",
                "package" to pkg,
                "lineCount" to lines.size,
                "text" to text
            )
            return
        }

        lastCaptureAt = now
        lastPkg = pkg
        previousLines[pkg] = lines

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val structured = JSONObject()
            .put("captureKind", if (useDelta) "screen_delta" else "screen_snapshot")
            .put("eventType", type)
            .put("lineCount", lines.size)
            .put("previousLineCount", previous.size)
            .put("addedLineCount", added.size)
            .put("retainedRatio", retainedRatio.toDouble())
            .put("windowTitle", root.window?.title?.toString())
            .put("url", findUrl(root))
            .put("focusedViewId", focused?.viewIdResourceName)
            .put("rootClass", root.className?.toString())
            .toString()
        WakeApp.instance.diagnostics.record(
            "screen_prepared",
            "package" to pkg,
            "captureKind" to if (useDelta) "screen_delta" else "screen_snapshot",
            "lineCount" to lines.size,
            "previousLineCount" to previous.size,
            "addedLineCount" to added.size,
            "retainedRatio" to retainedRatio.toDouble(),
            "text" to text,
            "structured" to structured
        )

        WakeApp.instance.ingest.submit(
            RawCapture(
                timestamp = now,
                source = SOURCE_SCREEN_TEXT,
                pkg = pkg,
                appLabel = labelFor(pkg),
                sender = null,
                text = text,
                structured = structured
            )
        )
    }

    private fun collect(node: AccessibilityNodeInfo?, lines: MutableSet<String>) {
        node ?: return
        if (lines.size >= maxLines) return
        node.text?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(lines::add)
        node.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(lines::add)
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), lines)
        }
    }

    private fun findUrl(node: AccessibilityNodeInfo?): String? {
        node ?: return null
        val id = node.viewIdResourceName.orEmpty().lowercase()
        val value = node.text?.toString()?.trim().orEmpty()
        if (("url" in id || "address" in id || "location" in id) && value.isNotEmpty()) return value
        for (i in 0 until node.childCount) {
            findUrl(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun labelFor(pkg: String): String? = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    override fun onInterrupt() {}
}
