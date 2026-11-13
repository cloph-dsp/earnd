package com.earnd.app.domain

import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.TaskDao
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class TaskBucket { OVERDUE, TODAY, UPCOMING, NO_DATE, COMPLETED }

class TaskService(
    private val tasks: TaskDao,
    private val bank: TimeBank,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val bus: ChangeBus,
    /** Makes the task rows and its ledger entry one unit; share the bank's transactor in production. */
    private val tx: Transactor = MutexTransactor(),
) {
    fun observeBucket(bucket: TaskBucket): Flow<List<TaskEntity>> {
        if (bucket == TaskBucket.COMPLETED) return tasks.observeCompleted(COMPLETED_LIMIT)
        return tasks.observeOpen().map { list ->
            val today = clock.today().toEpochDay()
            list.filter { bucketOf(it, today) == bucket }
        }
    }

    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>> = tasks.observeSubtasks(parentId)

    suspend fun get(id: Long): TaskEntity? = tasks.get(id)

    /** Inserts or updates. New top-level tasks without a reward get the default reward. */
    suspend fun save(t: TaskEntity): Long = tx.run {
        var toSave = t
        if (t.repeats && t.repeatAnchorEpochDay == null && t.dueEpochDay != null) {
            toSave = toSave.copy(repeatAnchorEpochDay = t.dueEpochDay)
        }
        if (t.id == 0L && t.rewardMinutes == null && t.parentId == null) {
            toSave = toSave.copy(rewardMinutes = settings.current().defaultTaskRewardMin)
        }
        val id = tasks.upsert(toSave).let { if (t.id != 0L) t.id else it }
        if (toSave.repeats && toSave.seriesId == null) {
            tasks.upsert(toSave.copy(id = id, seriesId = id))
        }
        bus.emit(ChangeKind.TASKS)
        id
    }

    /**
     * Marks done, earns the reward (if any) and, for a recurring task, creates the next
     * occurrence (with fresh copies of its subtasks), which is returned.
     */
    suspend fun complete(taskId: Long, creditBank: Boolean = true): TaskEntity? = tx.run {
        val t = tasks.get(taskId) ?: return@run null
        if (t.completedAt != null) return@run null
        val now = clock.now()
        tasks.upsert(t.copy(completedAt = now))
        t.rewardMinutes?.takeIf { creditBank && it > 0 }?.let { bank.earn(it, LedgerKind.EARN_TASK, OwnerKind.TASK, t.id) }
        val next = if (t.parentId == null) spawnNext(t, clock.localDate(now)) else null
        bus.emit(ChangeKind.TASKS)
        next
    }

    /** Reopens a task, reverses its reward and removes the untouched occurrence it spawned. */
    suspend fun uncomplete(taskId: Long) = tx.run {
        val t = tasks.get(taskId) ?: return@run
        if (t.completedAt == null) return@run
        tasks.upsert(t.copy(completedAt = null))
        bank.reverse(OwnerKind.TASK, t.id)
        val series = t.seriesId
        val due = t.dueEpochDay
        if (series != null && due != null) {
            tasks.nextOpenInSeries(series, due)?.let { deleteTree(it.id) }
        }
        bus.emit(ChangeKind.TASKS)
    }

    /** Moves unfinished past occurrences of recurring tasks to their next valid date (>= today). */
    suspend fun rollForward(today: LocalDate): Int = tx.run {
        val stale = tasks.openRecurringDueBefore(today.toEpochDay())
        stale.forEach { t ->
            val next = Recurrence.rolledForward(t, today) ?: return@forEach
            tasks.upsert(t.copy(dueEpochDay = next.toEpochDay()))
        }
        if (stale.isNotEmpty()) bus.emit(ChangeKind.TASKS)
        stale.size
    }

    /**
     * Skips this occurrence of a recurring task without completing it (no reward): it moves to
     * the next occurrence, or is removed when the series has ended. Returns the new due day.
     */
    suspend fun skip(taskId: Long): LocalDate? = tx.run {
        val t = tasks.get(taskId) ?: return@run null
        if (t.completedAt != null || t.parentId != null || !t.repeats) return@run null
        val next = Recurrence.following(t, clock.today())
        if (next == null) {
            deleteTree(t.id)
        } else {
            tasks.upsert(t.copy(dueEpochDay = next.toEpochDay(), repeatOccurrence = t.repeatOccurrence + 1,
                repeatAnchorEpochDay = t.repeatAnchorEpochDay ?: t.dueEpochDay, seriesId = t.seriesId ?: t.id))
            tasks.subtasks(t.id).filter { it.completedAt != null }.forEach { tasks.upsert(it.copy(completedAt = null)) }
        }
        bus.emit(ChangeKind.TASKS)
        next
    }

    /** Deletes the task and its subtasks. */
    suspend fun delete(taskId: Long) = tx.run {
        deleteTree(taskId)
        bus.emit(ChangeKind.TASKS)
    }

    private suspend fun deleteTree(taskId: Long) {
        tasks.subtasks(taskId).forEach { tasks.deleteById(it.id) }
        tasks.deleteById(taskId)
    }

    private suspend fun spawnNext(t: TaskEntity, today: LocalDate): TaskEntity? {
        val nextDay = Recurrence.following(t, today) ?: return null
        val seriesId = t.seriesId ?: t.id
        val copy = t.copy(id = 0, dueEpochDay = nextDay.toEpochDay(), completedAt = null,
            seriesId = seriesId, repeatOccurrence = t.repeatOccurrence + 1,
            repeatAnchorEpochDay = t.repeatAnchorEpochDay ?: t.dueEpochDay, createdAt = clock.now())
        val saved = copy.copy(id = tasks.upsert(copy))
        tasks.subtasks(t.id).forEach { sub ->
            tasks.upsert(sub.copy(id = 0, parentId = saved.id, completedAt = null, createdAt = clock.now()))
        }
        return saved
    }

    companion object {
        private const val COMPLETED_LIMIT = 200

        fun bucketOf(t: TaskEntity, todayEpochDay: Long): TaskBucket {
            if (t.completedAt != null) return TaskBucket.COMPLETED
            val due = t.dueEpochDay ?: return TaskBucket.NO_DATE
            return when {
                due < todayEpochDay -> TaskBucket.OVERDUE
                due == todayEpochDay -> TaskBucket.TODAY
                else -> TaskBucket.UPCOMING
            }
        }
    }
}
