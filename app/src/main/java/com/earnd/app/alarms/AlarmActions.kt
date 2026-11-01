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
        AlarmsModule.planner.rearmAll() // reconcile marks it pending if it is still open
        if (db.nagStateDao().get(kind, id) == null) return
        val style = styleOf(kind, id)
        if (style == AlarmStyle.FULL_SCREEN) Ringing.show(context, kind, id)
        AlarmsModule.notifier.refresh(alert = style != AlarmStyle.SILENT && !inQuiet())
    }

    /** A reminder ahead of the due time. */
    suspend fun onUpcoming(kind: OwnerKind, id: Long, dueAt: Long) {
        val label = labelOf(kind, id)
        if (label.isNotBlank()) {
            UpcomingNotifier.show(context, kind, id, label, dueAt, silent = styleOf(kind, id) == AlarmStyle.SILENT)
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
        AlarmsModule.notifier.refresh(alert = alert)
    }

    /** UI-independent upkeep: period reset and recurring roll-forward, before nags and widgets refresh. */
    suspend fun housekeeping() {
        runCatching { ServiceLocator.timeBank.resetIfPeriodRolled() }
        runCatching { ServiceLocator.taskService.rollForward(clock.today()) }
        runCatching { com.earnd.app.blocking.GuardWatch.check(context) }
    }

    /** "Done" from the notification or the ringing screen: completes via the domain services. */
    suspend fun done(kind: OwnerKind, id: Long) {
        runCatching {
            when (kind) {
                OwnerKind.TASK -> ServiceLocator.taskService.complete(id)
                OwnerKind.HABIT -> ServiceLocator.habitService.checkIn(id)
            }
        }
        nag.resolve(kind, id)
        Ringing.dismiss(context, kind, id)
        UpcomingNotifier.cancel(context, kind, id)
        AlarmsModule.planner.rearmAll()
        AlarmsModule.notifier.refresh(alert = false)
    }

    /** Snooze from the ringing screen only. Returns false when no snoozes are left. */
    suspend fun snooze(kind: OwnerKind, id: Long): Boolean {
        val at = nag.snooze(kind, id, clock.now()) ?: return false
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
