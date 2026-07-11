package com.wake.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(event: MemoryEvent): Long

    @Query("SELECT EXISTS(SELECT 1 FROM memory_event WHERE contentHash = :hash AND timestamp >= :after)")
    suspend fun existsRecent(hash: String, after: Long): Boolean

    @Query("SELECT * FROM memory_event WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_event ORDER BY timestamp DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<MemoryEvent>>

    @Query(
        """
        SELECT me.* FROM memory_event me
        JOIN memory_fts fts ON me.id = fts.rowid
        WHERE memory_fts MATCH :match
        ORDER BY me.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun search(match: String, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_event WHERE embedding IS NULL ORDER BY id DESC LIMIT :limit")
    suspend fun unembedded(limit: Int): List<MemoryEvent>

    @Query("UPDATE memory_event SET embedding = :embedding WHERE id = :id")
    suspend fun setEmbedding(id: Long, embedding: ByteArray)

    @Query("SELECT * FROM memory_event WHERE embedding IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    suspend fun embedded(limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_event WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun bySession(sessionId: Long): List<MemoryEvent>

    @Query("DELETE FROM memory_event WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM memory_event")
    suspend fun clear()
}
