package com.wake.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentTaskDao {

    @Insert
    suspend fun insert(task: AgentTask): Long

    @Update
    suspend fun update(task: AgentTask)

    @Query("SELECT * FROM agent_task WHERE id = :id")
    suspend fun byId(id: Long): AgentTask?

    @Query("SELECT * FROM agent_task ORDER BY updatedAt DESC LIMIT :limit")
    fun feed(limit: Int): Flow<List<AgentTask>>

    @Query("SELECT COUNT(*) FROM agent_task WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT * FROM agent_task WHERE status IN ('watching', 'thinking', 'proposed')")
    suspend fun active(): List<AgentTask>

    @Query("SELECT * FROM agent_task WHERE status IN ('watching', 'thinking', 'proposed') AND pkg = :pkg AND sender = :sender LIMIT 1")
    suspend fun activeFor(pkg: String?, sender: String?): AgentTask?

    @Query("SELECT * FROM agent_task WHERE status = 'watching' AND dueAt <= :now")
    suspend fun overdue(now: Long): List<AgentTask>

    @Query("SELECT COUNT(*) FROM agent_task WHERE status = 'proposed' OR (status IN ('done', 'dismissed') AND updatedAt >= :since AND action IS NOT NULL)")
    suspend fun proposalsSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM agent_task WHERE status = 'dismissed' AND pkg = :pkg AND sender = :sender AND updatedAt >= :since")
    suspend fun dismissalsFor(pkg: String?, sender: String?, since: Long): Int

    @Query("DELETE FROM agent_task")
    suspend fun clear()
}
