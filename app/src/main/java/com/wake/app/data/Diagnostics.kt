package com.wake.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
import org.json.JSONObject

class Diagnostics(context: Context) {
    private val enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val file = File(context.filesDir, FILE_NAME)
    private val previousFile = File(context.filesDir, PREVIOUS_FILE_NAME)

    @Synchronized
    fun record(type: String, vararg values: Pair<String, Any?>) {
        if (!enabled) return
        runCatching {
            rotateIfNeeded()
            val data = JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("type", type)
            values.forEach { (key, value) -> data.put(key, value ?: JSONObject.NULL) }
            file.appendText(data.toString() + "\n")
        }
    }

    fun path(): String = file.absolutePath

    private fun rotateIfNeeded() {
        if (!file.exists() || file.length() < MAX_BYTES) return
        previousFile.delete()
        file.renameTo(previousFile)
    }

    private companion object {
        const val FILE_NAME = "wake_debug.jsonl"
        const val PREVIOUS_FILE_NAME = "wake_debug.previous.jsonl"
        const val MAX_BYTES = 5L * 1024 * 1024
    }
}
