package com.wake.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MemoryEvent::class, MemoryFts::class, AgentTask::class],
    version = 3,
    exportSchema = false
)
abstract class WakeDb : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun agentTaskDao(): AgentTaskDao

    companion object {
        @Volatile private var instance: WakeDb? = null

        fun get(context: Context): WakeDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WakeDb::class.java,
                    "wake.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
