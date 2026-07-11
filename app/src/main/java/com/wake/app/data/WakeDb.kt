package com.wake.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryEvent::class, MemoryFts::class, AgentTask::class],
    version = 4,
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
                ).addMigrations(MIGRATION_3_4).fallbackToDestructiveMigration().build().also { instance = it }
            }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_event ADD COLUMN conversationId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE memory_event ADD COLUMN direction TEXT NOT NULL DEFAULT 'unknown'")
                db.execSQL("ALTER TABLE agent_task ADD COLUMN conversationId TEXT DEFAULT NULL")
            }
        }
    }
}
