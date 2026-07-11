package com.wake.app.capture

import com.wake.app.data.MemoryEvent
import java.security.MessageDigest

data class RawCapture(
    val timestamp: Long,
    val source: String,
    val pkg: String?,
    val appLabel: String?,
    val sender: String?,
    val text: String,
    val structured: String? = null
) {
    fun contentHash(): String {
        val normalized = normalizedText()
        val raw = if (normalized.length >= 24) normalized else "$pkg|$sender|$normalized"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun normalizedText(): String = text
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

    fun toEvent(sessionId: Long, hash: String) = MemoryEvent(
        timestamp = timestamp,
        source = source,
        pkg = pkg,
        appLabel = appLabel,
        sender = sender,
        text = text,
        structured = structured,
        sessionId = sessionId,
        contentHash = hash
    )
}
