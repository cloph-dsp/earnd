package com.earnd.app.remote

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.OwnerKind
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
    /** Skips a recurring occurrence; returns its new due day, null if the series ended. */
    suspend fun skipTask(id: Long): java.time.LocalDate?

    /** Per-item alarm/reminder config, or null when the item follows the defaults. */
    suspend fun alarmConfig(kind: OwnerKind, id: Long): AlarmConfigEntity?
    suspend fun saveAlarmConfig(cfg: AlarmConfigEntity)
    /** Selectable alarm sounds (system + custom files). */
    suspend fun sounds(): List<com.earnd.app.alarms.SoundOption> = emptyList()

    suspend fun settings(): Settings
    /** Replaces settings with [new] only if they still equal [expected]; false on a race. */
    suspend fun saveSettings(expected: Settings, new: Settings): Boolean

    suspend fun appRules(): List<AppRuleEntity>
    /** Marks an app or website as blocked (leisure). Only ever strengthens blocking. */
    suspend fun blockRule(key: String, label: String, isWebsite: Boolean)
    /** Launchable installed apps as (label, package), sorted by label. */
    suspend fun launchableApps(): List<Pair<String, String>>

    suspend fun appendAudit(entryJson: String)
    suspend fun auditLog(): List<String>
}
