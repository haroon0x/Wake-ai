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

    @Query("SELECT * FROM memory_event WHERE timestamp >= :after ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentCaptures(after: Long, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_event ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): MemoryEvent?

    @Query("SELECT * FROM memory_event WHERE source = :source ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestBySource(source: String): MemoryEvent?

    @Query("SELECT * FROM memory_event WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_event ORDER BY timestamp DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<MemoryEvent>>

    @Query("SELECT * FROM memory_event WHERE source = :source AND timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    fun sourceFlow(source: String, since: Long, limit: Int): Flow<List<MemoryEvent>>

    @Query("SELECT * FROM memory_event WHERE conversationId = :conversationId AND timestamp >= :since ORDER BY timestamp ASC LIMIT :limit")
    suspend fun byConversation(conversationId: String, since: Long, limit: Int): List<MemoryEvent>

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

    @Query(
        """
        SELECT me.* FROM memory_event me
        JOIN memory_fts fts ON me.id = fts.rowid
        WHERE memory_fts MATCH :match
        AND (:since IS NULL OR me.timestamp >= :since)
        AND (:until IS NULL OR me.timestamp < :until)
        AND (:source IS NULL OR me.source = :source)
        AND (:app IS NULL OR LOWER(me.appLabel) LIKE '%' || LOWER(:app) || '%' OR LOWER(me.pkg) LIKE '%' || LOWER(:app) || '%')
        AND (:sender IS NULL OR LOWER(me.sender) LIKE '%' || LOWER(:sender) || '%')
        ORDER BY me.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchConstrained(
        match: String,
        since: Long?,
        until: Long?,
        source: String?,
        app: String?,
        sender: String?,
        limit: Int
    ): List<MemoryEvent>

    @Query(
        """
        SELECT * FROM memory_event
        WHERE (:since IS NULL OR timestamp >= :since)
        AND (:until IS NULL OR timestamp < :until)
        AND (:source IS NULL OR source = :source)
        AND (:app IS NULL OR LOWER(appLabel) LIKE '%' || LOWER(:app) || '%' OR LOWER(pkg) LIKE '%' || LOWER(:app) || '%')
        AND (:sender IS NULL OR LOWER(sender) LIKE '%' || LOWER(:sender) || '%')
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun recentConstrained(
        since: Long?,
        until: Long?,
        source: String?,
        app: String?,
        sender: String?,
        limit: Int
    ): List<MemoryEvent>

    @Query("SELECT * FROM memory_event WHERE embedding IS NULL ORDER BY id DESC LIMIT :limit")
    suspend fun unembedded(limit: Int): List<MemoryEvent>

    @Query("UPDATE memory_event SET embedding = :embedding WHERE id = :id")
    suspend fun setEmbedding(id: Long, embedding: ByteArray)

    @Query("SELECT * FROM memory_event WHERE embedding IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    suspend fun embedded(limit: Int): List<MemoryEvent>

    @Query("SELECT * FROM memory_event WHERE embedding IS NOT NULL AND timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun embeddedSince(since: Long, limit: Int): List<MemoryEvent>

    @Query("SELECT appLabel FROM memory_event WHERE appLabel IS NOT NULL GROUP BY appLabel ORDER BY MAX(timestamp) DESC LIMIT :limit")
    suspend fun knownApps(limit: Int): List<String?>

    @Query("SELECT sender FROM memory_event WHERE sender IS NOT NULL GROUP BY sender ORDER BY MAX(timestamp) DESC LIMIT :limit")
    suspend fun knownSenders(limit: Int): List<String?>

    @Query("SELECT COUNT(*) FROM memory_event")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM memory_event WHERE source = :source")
    suspend fun countBySource(source: String): Long

    @Query("SELECT MIN(timestamp) FROM memory_event")
    suspend fun earliestTimestamp(): Long?

    @Query("SELECT * FROM memory_event WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun bySession(sessionId: Long): List<MemoryEvent>

    @Query("DELETE FROM memory_event WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM memory_event")
    suspend fun clear()
}
