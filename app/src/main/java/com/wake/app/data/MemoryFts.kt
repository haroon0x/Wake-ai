package com.wake.app.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = MemoryEvent::class)
@Entity(tableName = "memory_fts")
data class MemoryFts(
    val text: String,
    val sender: String?,
    val appLabel: String?
)
