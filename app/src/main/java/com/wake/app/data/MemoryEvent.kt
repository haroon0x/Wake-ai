package com.wake.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

const val SOURCE_NOTIFICATION = "notification"
const val SOURCE_SCREEN_TEXT = "screen_text"
const val SOURCE_MANUAL = "manual"
const val DIRECTION_INCOMING = "incoming"
const val DIRECTION_OUTGOING = "outgoing"
const val DIRECTION_UNKNOWN = "unknown"

@Entity(
    tableName = "memory_event",
    indices = [Index("timestamp"), Index("contentHash"), Index("sessionId")]
)
data class MemoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val source: String,
    val pkg: String?,
    val appLabel: String?,
    val sender: String?,
    @ColumnInfo(defaultValue = "NULL") val conversationId: String? = null,
    @ColumnInfo(defaultValue = "'unknown'") val direction: String = DIRECTION_UNKNOWN,
    val text: String,
    val structured: String?,
    val sessionId: Long,
    val contentHash: String,
    val embedding: ByteArray? = null
)

fun MemoryEvent.displaySource(): String {
    val label = appLabel?.trim()?.takeIf { value ->
        value.isNotEmpty() && !(value.count { it == '.' } >= 2 && value.none(Char::isWhitespace))
    }
    return label ?: when (source) {
        SOURCE_NOTIFICATION -> "Notification"
        SOURCE_SCREEN_TEXT -> "Screen memory"
        SOURCE_MANUAL -> "Saved memory"
        else -> "Memory"
    }
}

fun String.withoutPackageIdentifiers(): String = replace(
    Regex("\\b(?:com|org|net|io|in|android)\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+\\b"),
    "the app"
)
