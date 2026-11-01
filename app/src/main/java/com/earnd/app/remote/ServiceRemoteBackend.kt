package com.earnd.app.remote

import android.content.Context
import android.content.Intent
import com.earnd.app.data.EarndDatabase
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.ui.alarms.AlarmConfigs
import com.earnd.app.ui.apps.PendingRuleChanges
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.Clock
import com.earnd.app.domain.HabitSchedule
import com.earnd.app.domain.HabitService
import com.earnd.app.domain.TaskBucket
import com.earnd.app.domain.TaskService
import com.earnd.app.domain.TimeBank
import com.earnd.app.domain.today
import java.io.File
import java.time.LocalDate

/** [RemoteBackend] backed by the real domain services. */
class ServiceRemoteBackend(
    private val db: EarndDatabase,
    private val settings: SettingsRepository,
    private val bank: TimeBank,
    private val habitsSvc: HabitService,
    private val tasksSvc: TaskService,
    private val clock: Clock,
    private val photoDir: File?,
    private val context: Context?,
) : RemoteBackend {

    override fun now() = clock.now()
    override fun today(): LocalDate = clock.today()

    override suspend fun remoteEnabled() = RemoteSettings.isEnabled(settings)

    override suspend fun status(): Map<String, Any?> {
        val now = now()
        val today = today()
        val s = settings.current()
        val bal = bank.balance(now)
        val session = bank.activeSession(now)
        val habits = db.habitDao().active().filter { HabitSchedule.isScheduled(it, today) }
        val counts = db.checkInDao().forDay(today.toEpochDay()).groupingBy { it.habitId }.eachCount()
        val buckets = db.taskDao().open().filter { it.parentId == null }
            .groupingBy { TaskService.bucketOf(it, today.toEpochDay()) }.eachCount()
        return Json.obj(
            "now" to now,
            "balanceMinutes" to bal.minutes,
            "resetsAt" to bal.resetsAt,
            "resetMode" to s.resetPeriod.name,
            "nextExpiryAt" to bal.nextExpiryAt,
            "minutesExpiringNext" to bal.minutesExpiringNext,
            "unlocked" to (session != null),
            "unlockedUntil" to session?.plannedEndAt,
            "unlockReason" to session?.reason,
            "blockingEnabled" to s.blockingEnabled,
            "hardMode" to s.hardMode,
            "bedtimeEnabled" to s.bedtimeEnabled,
            "onVacation" to s.onVacation(today.toEpochDay()),
            "habitsScheduled" to habits.size,
            "habitsComplete" to habits.count { (counts[it.id] ?: 0) >= it.timesPerDay },
            "tasksOverdue" to (buckets[TaskBucket.OVERDUE] ?: 0),
            "tasksToday" to (buckets[TaskBucket.TODAY] ?: 0),
        )
    }

    override suspend fun habits(includeArchived: Boolean): List<HabitEntity> =
        if (includeArchived) db.habitDao().all() else db.habitDao().active()

    override suspend fun habit(id: Long) = db.habitDao().get(id)

    override suspend fun checkInsToday(habitId: Long): List<CheckInEntity> =
        db.checkInDao().forDay(today().toEpochDay()).filter { it.habitId == habitId }

    override suspend fun streak(habitId: Long) = habitsSvc.streak(habitId, today())
    override suspend fun saveHabit(h: HabitEntity) = habitsSvc.save(h)
    override suspend fun archiveHabit(id: Long) = habitsSvc.archive(id)
    override suspend fun checkIn(habitId: Long, quantity: Double?) = habitsSvc.checkIn(habitId, quantity, creditBank = false)

    override suspend fun undoCheckIn(checkInId: Long) {
        val photo = habitsSvc.undoCheckIn(checkInId) ?: return
        photoDir?.let { File(it, photo).delete() }
    }

    override suspend fun openTasks() = db.taskDao().open()
    override suspend fun task(id: Long) = tasksSvc.get(id)
    override suspend fun subtasks(parentId: Long) = db.taskDao().subtasks(parentId)
    override suspend fun saveTask(t: TaskEntity) = tasksSvc.save(t)
    override suspend fun completeTask(id: Long) = tasksSvc.complete(id, creditBank = false)
    override suspend fun uncompleteTask(id: Long) = tasksSvc.uncomplete(id)
    override suspend fun deleteTask(id: Long) = tasksSvc.delete(id)
    override suspend fun skipTask(id: Long) = tasksSvc.skip(id)

    override suspend fun alarmConfig(kind: OwnerKind, id: Long) = db.alarmConfigDao().get(kind, id)
    override suspend fun saveAlarmConfig(cfg: AlarmConfigEntity) = AlarmConfigs.save(cfg)

    override suspend fun settings(): Settings = settings.current()
    override suspend fun saveSettings(expected: Settings, new: Settings): Boolean {
        var applied = false
        settings.update { cur -> if (cur == expected) new.also { applied = true } else cur }
        return applied
    }

    override suspend fun appRules() = db.appRuleDao().all()
    override suspend fun blockRule(key: String, label: String, isWebsite: Boolean) =
        PendingRuleChanges.addLeisure(key, label, isWebsite)

    override suspend fun launchableApps(): List<Pair<String, String>> {
        val ctx = context ?: return emptyList()
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ctx.packageName }
            .map { it.loadLabel(pm).toString() to it.packageName }
            .sortedBy { it.first.lowercase() }
    }

    override suspend fun appendAudit(entryJson: String) {
        settings.rawPut(RemoteAudit.KEY, RemoteAudit.append(settings.rawGet(RemoteAudit.KEY), entryJson))
    }

    override suspend fun auditLog() = RemoteAudit.parse(settings.rawGet(RemoteAudit.KEY))
}
