package com.wake.app.agent

import android.util.Log
import com.wake.app.data.ACTION_DROP
import com.wake.app.data.AgentTask
import com.wake.app.data.AgentTaskDao
import com.wake.app.data.MemoryDao
import com.wake.app.data.MemoryEvent
import com.wake.app.data.SOURCE_NOTIFICATION
import com.wake.app.data.SOURCE_SCREEN_TEXT
import com.wake.app.data.STATUS_DISMISSED
import com.wake.app.data.STATUS_DONE
import com.wake.app.data.STATUS_EXPIRED
import com.wake.app.data.STATUS_PROPOSED
import com.wake.app.data.STATUS_THINKING
import com.wake.app.data.STATUS_WATCHING
import com.wake.app.data.TASK_COMMITMENT
import com.wake.app.data.TASK_PENDING_REPLY
import com.wake.app.data.withTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentEngine(
    private val taskDao: AgentTaskDao,
    private val memoryDao: MemoryDao,
    private val reasoner: AgentReasoner,
    private val executor: ActionExecutor,
    private val scope: CoroutineScope
) {
    private val lock = Mutex()

    fun start() {
        scope.launch {
            while (true) {
                runCatching { tick() }.onFailure { Log.w("WAKE", "agent tick failed", it) }
                delay(TICK_MILLIS)
            }
        }
    }

    fun onEvent(event: MemoryEvent) {
        scope.launch {
            lock.withLock {
                when (event.source) {
                    SOURCE_NOTIFICATION -> onNotification(event)
                    SOURCE_SCREEN_TEXT -> onScreenText(event)
                }
            }
        }
    }

    private suspend fun onNotification(event: MemoryEvent) {
        val sender = event.sender?.trim().orEmpty()
        if (sender.isEmpty() || event.pkg.isNullOrBlank()) return
        maybeCommitment(event, sender)
        val existing = taskDao.activeFor(TASK_PENDING_REPLY, event.pkg, sender)
        val now = System.currentTimeMillis()
        if (existing != null) {
            if (existing.status == STATUS_WATCHING) {
                taskDao.update(
                    existing.copy(sourceText = event.text, sourceEventId = event.id, dueAt = now + REPLY_WINDOW_MILLIS)
                        .withTrace("Another message from $sender — still unanswered", now)
                )
            }
            return
        }
        val recentDismissals = taskDao.dismissalsFor(event.pkg, sender, now - DAY_MILLIS)
        if (recentDismissals >= MAX_DISMISSALS_PER_SENDER) return
        taskDao.insert(
            AgentTask(
                type = TASK_PENDING_REPLY,
                status = STATUS_WATCHING,
                createdAt = now,
                updatedAt = now,
                dueAt = now + REPLY_WINDOW_MILLIS,
                pkg = event.pkg,
                appLabel = event.appLabel,
                sender = sender,
                sourceEventId = event.id,
                sourceText = event.text,
                title = "Message from $sender"
            ).withTrace("Noticed a message from $sender in ${event.appLabel ?: "an app"} — watching for your reply", now)
        )
    }

    private suspend fun maybeCommitment(event: MemoryEvent, sender: String) {
        if (!CommitmentDetector.matches(event.text)) return
        if (taskDao.activeFor(TASK_COMMITMENT, event.pkg, sender) != null) return
        val now = System.currentTimeMillis()
        if (taskDao.dismissalsFor(event.pkg, sender, now - DAY_MILLIS) >= MAX_DISMISSALS_PER_SENDER) return
        taskDao.insert(
            AgentTask(
                type = TASK_COMMITMENT,
                status = STATUS_WATCHING,
                createdAt = now,
                updatedAt = now,
                dueAt = now + COMMITMENT_DELAY_MILLIS,
                pkg = event.pkg,
                appLabel = event.appLabel,
                sender = sender,
                sourceEventId = event.id,
                sourceText = event.text,
                title = "Possible commitment with $sender"
            ).withTrace("Spotted a time or deadline in $sender's message — checking if it needs a reminder", now)
        )
    }

    private suspend fun onScreenText(event: MemoryEvent) {
        if (event.pkg.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        taskDao.active()
            .filter { it.type == TASK_PENDING_REPLY && it.pkg == event.pkg }
            .forEach { task ->
                val sender = task.sender.orEmpty()
                if (sender.length >= 3 && event.text.contains(sender, ignoreCase = true)) {
                    taskDao.update(
                        task.copy(status = STATUS_DONE)
                            .withTrace("Saw $sender on your screen in ${task.appLabel ?: "the app"} — looks handled, closing", now)
                    )
                }
            }
    }

    suspend fun tick() {
        val now = System.currentTimeMillis()
        val overdue = lock.withLock {
            val tasks = taskDao.overdue(now)
            expireStale(now)
            val budget = MAX_PROPOSALS_PER_DAY - taskDao.proposalsSince(now - DAY_MILLIS)
            tasks.take(budget.coerceAtLeast(0)).map { task ->
                val thinking = task.copy(status = STATUS_THINKING, attempts = task.attempts + 1)
                    .withTrace("No reply after ${REPLY_WINDOW_MILLIS / 60_000} min — asking the model what to do", now)
                taskDao.update(thinking)
                thinking
            }
        }
        overdue.forEach { reason(it) }
    }

    private suspend fun reason(task: AgentTask) {
        val context = relatedEvents(task)
        val proposal = reasoner.propose(task, context)
        val now = System.currentTimeMillis()
        val current = taskDao.byId(task.id) ?: return
        if (current.status != STATUS_THINKING) return
        if (proposal.action == ACTION_DROP || proposal.confidence < MIN_CONFIDENCE) {
            taskDao.update(
                current.copy(status = STATUS_DONE, action = null, confidence = proposal.confidence)
                    .withTrace("Model judged no action needed (${proposal.title})", now)
            )
            return
        }
        val note = if (proposal.fallback) {
            "Model output unusable — falling back to a simple nudge"
        } else {
            "Model proposed: ${proposal.title} (confidence ${"%.0f".format(proposal.confidence * 100)}%)"
        }
        val proposed = current.copy(
            status = STATUS_PROPOSED,
            action = proposal.action,
            title = proposal.title,
            draft = proposal.draft,
            reminderMinutes = proposal.reminderMinutes,
            confidence = proposal.confidence
        ).withTrace(note, now).withTrace("Waiting for your approval", now)
        taskDao.update(proposed)
        executor.notifyProposal(proposed)
    }

    private suspend fun relatedEvents(task: AgentTask): List<MemoryEvent> {
        val sender = task.sender
        val bySender = if (sender.isNullOrBlank()) emptyList() else {
            memoryDao.recentConstrained(
                since = task.createdAt - CONTEXT_WINDOW_MILLIS,
                until = null,
                source = null,
                app = null,
                sender = sender,
                limit = CONTEXT_EVENTS
            )
        }
        return bySender.ifEmpty {
            memoryDao.recentCaptures(task.createdAt - CONTEXT_WINDOW_MILLIS, CONTEXT_EVENTS)
        }.sortedBy { it.timestamp }
    }

    suspend fun approve(taskId: Long) {
        val task = taskDao.byId(taskId) ?: return
        if (task.status != STATUS_PROPOSED) return
        val outcome = executor.execute(task)
        taskDao.update(task.copy(status = STATUS_DONE).withTrace("Approved — $outcome"))
    }

    suspend fun dismiss(taskId: Long) {
        val task = taskDao.byId(taskId) ?: return
        if (task.status !in setOf(STATUS_PROPOSED, STATUS_WATCHING, STATUS_THINKING)) return
        taskDao.update(task.copy(status = STATUS_DISMISSED).withTrace("Dismissed by you — I'll stay quiet about this one"))
    }

    private suspend fun expireStale(now: Long) {
        taskDao.active()
            .filter { now - it.updatedAt > STALE_MILLIS }
            .forEach { task ->
                taskDao.update(task.copy(status = STATUS_EXPIRED).withTrace("Too old — letting this one go", now))
            }
    }

    companion object {
        const val REPLY_WINDOW_MILLIS = 3 * 60_000L
        const val COMMITMENT_DELAY_MILLIS = 60_000L
        const val TICK_MILLIS = 30_000L
        const val DAY_MILLIS = 86_400_000L
        const val STALE_MILLIS = 12 * 3_600_000L
        const val CONTEXT_WINDOW_MILLIS = 6 * 3_600_000L
        const val CONTEXT_EVENTS = 10
        const val MAX_PROPOSALS_PER_DAY = 15
        const val MAX_DISMISSALS_PER_SENDER = 2
        const val MIN_CONFIDENCE = 0.3f
    }
}
