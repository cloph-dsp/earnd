package com.earnd.app.remote

import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.TaskEntity
import java.time.LocalDate

/** Everything the remote dispatcher may touch. Kept narrow so forbidden actions are unreachable. */
interface RemoteBackend {
    fun now(): Long
    fun today(): LocalDate

    suspend fun remoteEnabled(): Boolean

    /** Read-only lock/balance summary (values only, never actions). */
    suspend fun status(): Map<String, Any?>

    suspend fun habits(includeArchived: Boolean): List<HabitEntity>
    suspend fun habit(id: Long): HabitEntity?
    suspend fun checkInsToday(habitId: Long): List<CheckInEntity>
    suspend fun streak(habitId: Long): Int
    suspend fun saveHabit(h: HabitEntity): Long
    suspend fun archiveHabit(id: Long)
    suspend fun checkIn(habitId: Long, quantity: Double?): CheckInEntity
    suspend fun undoCheckIn(checkInId: Long)

    suspend fun openTasks(): List<TaskEntity>
    suspend fun task(id: Long): TaskEntity?
    suspend fun subtasks(parentId: Long): List<TaskEntity>
    suspend fun saveTask(t: TaskEntity): Long
    suspend fun completeTask(id: Long): TaskEntity?
    suspend fun uncompleteTask(id: Long)
    suspend fun deleteTask(id: Long)

    suspend fun appendAudit(entryJson: String)
    suspend fun auditLog(): List<String>
}
