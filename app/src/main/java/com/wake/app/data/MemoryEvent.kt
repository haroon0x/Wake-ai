package com.wake.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val SOURCE_NOTIFICATION = "notification"
const val SOURCE_SCREEN_TEXT = "screen_text"
const val SOURCE_MANUAL = "manual"

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
    val text: String,
    val structured: String?,
    val sessionId: Long,
    val contentHash: String
)
