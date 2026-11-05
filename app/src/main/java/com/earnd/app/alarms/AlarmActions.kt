package com.earnd.app.alarms

import android.content.Context
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AlarmStyle
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.DailyWindow
import com.earnd.app.domain.localDate
import com.earnd.app.domain.today
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What happens when alarms fire or the user acts on them. Shared by receivers, worker and activity. */
class AlarmActions(private val context: Context) {
    private val db get() = ServiceLocator.db
    private val nag get() = ServiceLocator.nagEngine
    private val clock get() = ServiceLocator.clock

    /** An item's due time arrived. */
    suspend fun onDue(kind: OwnerKind, id: Long) {
        // Reconcile marks it pending if it is still open. If it fails, ring anyway: a late alarm beats a silent one.
        val reconciled = runCatching { AlarmsModule.planner.rearmAll() }
            .onFailure { android.util.Log.e("Earnd", "Re-arm before ringing failed", it) }.isSuccess
        if (reconciled && db.nagStateDao().get(kind, id) == null) return
        val style = styleOf(kind, id)
        if (style == AlarmStyle.FULL_SCREEN) Ringing.show(context, kind, id)
        AlarmsModule.notifier.refresh(alert = style != AlarmStyle.SILENT && !inQuiet(), quiet = inQuiet())
    }

    /** A reminder ahead of the due time. */
    suspend fun onUpcoming(kind: OwnerKind, id: Long, dueAt: Long) {
        val label = labelOf(kind, id)
        if (label.isNotBlank()) {
            UpcomingNotifier.show(context, kind, id, label, dueAt, silent = styleOf(kind, id) == AlarmStyle.SILENT, needsApp = needsApp(kind, id))
        }
        AlarmsModule.planner.rearmAll()
    }

    /** Nag alarm or heartbeat: bump due items, ring full-screen ones, refresh the notification. */
    suspend fun nagPass() = passLock.withLock {
        housekeeping()
        val now = clock.now()
        val before = db.nagStateDao().all().associate { (it.ownerKind to it.ownerId) to it.nagCount }
        nag.onFired(now)
        val bumped = db.nagStateDao().all().filter { it.nagCount > (before[it.ownerKind to it.ownerId] ?: 0) }
        AlarmsModule.planner.rearmAll()
        val styles = bumped.map { styleOf(it.ownerKind, it.ownerId) }
        bumped.zip(styles).firstOrNull { it.second == AlarmStyle.FULL_SCREEN }?.let { (s, _) ->
            Ringing.show(context, s.ownerKind, s.ownerId)
        }
        val alert = styles.any { it != AlarmStyle.SILENT } && !inQuiet()
        AlarmsModule.notifier.refresh(alert = alert, quiet = inQuiet())
    }

    /** UI-independent upkeep: period reset and recurring roll-forward, before nags and widgets refresh. */
    suspend fun housekeeping() {
        runCatching { ServiceLocator.timeBank.resetIfPeriodRolled() }
        runCatching { ServiceLocator.taskService.rollForward(clock.today()) }
        runCatching { com.earnd.app.blocking.GuardWatch.check(context) }
    }

    /** True when "Done" for this item must go through the app (photo, focus timer or amount) instead of a plain tap. */
    suspend fun needsApp(kind: OwnerKind, id: Long): Boolean {
        if (kind != OwnerKind.HABIT) return false
        val h = runCatching { db.habitDao().get(id) }.getOrNull() ?: return false
        val s = runCatching { ServiceLocator.settings.current() }.getOrNull() ?: return false
        return ServiceLocator.habitService.checkInKind(h, s) != com.earnd.app.domain.HabitService.CheckInKind.TAP
    }

    /**
     * "Done" from the notification or the ringing screen. Completes only what a plain tap can complete: a habit that
     * needs a photo, a focus session or an amount returns false (the caller opens the app) and keeps nagging, and a
     * failed save keeps nagging too instead of looking done.
     */
    suspend fun done(kind: OwnerKind, id: Long): Boolean {
        // An item that no longer exists (or is already done) is resolved without a save, as before.
        val gone = runCatching {
            when (kind) {
                OwnerKind.HABIT -> db.habitDao().get(id).let { it == null || it.archived }
                OwnerKind.TASK -> ServiceLocator.taskService.get(id).let { it == null || it.completedAt != null }
            }
        }.getOrDefault(false)
        if (!gone && needsApp(kind, id)) return refreshed(false)
        val saved = gone || runCatching {
            when (kind) {
                OwnerKind.TASK -> ServiceLocator.taskService.complete(id)
                OwnerKind.HABIT -> ServiceLocator.habitService.checkIn(id)
            }
        }.isSuccess
        if (!saved) return refreshed(false)
        nag.resolve(kind, id)
        Ringing.dismiss(context, kind, id)
        UpcomingNotifier.cancel(context, kind, id)
        AlarmsModule.planner.rearmAll()
        AlarmsModule.notifier.refresh(alert = false)
        return true
    }

    /** Rebuilds the pending notification (an action may now need the app) and rearms, then returns [result]. */
    private suspend fun refreshed(result: Boolean): Boolean {
        runCatching { AlarmsModule.planner.rearmAll() }
        runCatching { AlarmsModule.notifier.refresh(alert = false) }
        return result
    }

    /** Stop: silence this occurrence without completing it; the next reminder time rings again. */
    suspend fun stop(kind: OwnerKind, id: Long) {
        nag.silence(kind, id)
        Ringing.dismiss(context, kind, id)
        AlarmsModule.planner.rearmAll()
        AlarmsModule.notifier.refresh(alert = false, quiet = inQuiet())
    }

    /** Snooze from the ringing screen only. Returns false when no snoozes are left. */
    suspend fun snooze(kind: OwnerKind, id: Long, minutes: Int = NagSchedule.SNOOZE_MIN): Boolean {
        val at = nag.snooze(kind, id, clock.now(), minutes) ?: return false
        Ringing.dismiss(context, kind, id)
        AlarmsModule.planner.rearmAll()
        return at > 0
    }

    suspend fun snoozesLeft(kind: OwnerKind, id: Long): Int {
        val s = db.nagStateDao().get(kind, id) ?: return 0
        return RingPolicy.snoozesLeft(db.alarmConfigDao().get(kind, id)?.snoozeMaxCount, s.snoozeCount)
    }

    suspend fun labelOf(kind: OwnerKind, id: Long): String = when (kind) {
        OwnerKind.TASK -> db.taskDao().get(id)?.title
        OwnerKind.HABIT -> db.habitDao().get(id)?.let { h -> h.reminderMessage?.takeIf { it.isNotBlank() } ?: h.name }
    }.orEmpty()

    suspend fun styleOf(kind: OwnerKind, id: Long): AlarmStyle =
        db.alarmConfigDao().get(kind, id)?.style ?: AlarmStyle.NOTIFICATION

    private suspend fun inQuiet(): Boolean {
        val s = ServiceLocator.settings.current()
        val now = clock.now()
        return DayStart.quietWindow(s, clock.localDate(now)).contains(now, clock.zone())
    }

    private companion object {
        /** Process-wide: receivers, ticker and worker may all run a pass at once. */
        val passLock = Mutex()
    }
}
