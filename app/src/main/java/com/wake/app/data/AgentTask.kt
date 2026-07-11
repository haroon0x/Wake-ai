package com.wake.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

const val TASK_PENDING_REPLY = "pending_reply"
const val TASK_COMMITMENT = "commitment"

const val STATUS_WATCHING = "watching"
const val STATUS_THINKING = "thinking"
const val STATUS_PROPOSED = "proposed"
const val STATUS_DONE = "done"
const val STATUS_DISMISSED = "dismissed"
const val STATUS_EXPIRED = "expired"

const val ACTION_REPLY = "reply"
const val ACTION_REMIND = "remind"
const val ACTION_OPEN_APP = "open_app"
const val ACTION_DROP = "drop"

@Entity(
    tableName = "agent_task",
    indices = [Index("status"), Index("updatedAt")]
)
data class AgentTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val dueAt: Long,
    val pkg: String?,
    val appLabel: String?,
    val sender: String?,
    val sourceEventId: Long,
    val sourceText: String,
    val title: String,
    val action: String? = null,
    val draft: String? = null,
    val reminderMinutes: Int? = null,
    val confidence: Float? = null,
    val attempts: Int = 0,
    val trace: String = "[]"
)

data class TraceStep(val timestamp: Long, val note: String)

fun AgentTask.traceSteps(): List<TraceStep> = runCatching {
    val array = JSONArray(trace)
    (0 until array.length()).map { index ->
        val step = array.getJSONObject(index)
        TraceStep(step.optLong("t"), step.optString("n"))
    }
}.getOrDefault(emptyList())

fun AgentTask.withTrace(note: String, now: Long = System.currentTimeMillis()): AgentTask {
    val array = runCatching { JSONArray(trace) }.getOrDefault(JSONArray())
    array.put(JSONObject().put("t", now).put("n", note))
    return copy(trace = array.toString(), updatedAt = now)
}
