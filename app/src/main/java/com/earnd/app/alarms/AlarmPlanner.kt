package com.earnd.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.AlarmConfigDao
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.dao.NagStateDao
import com.earnd.app.data.dao.TaskDao
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.AlarmStyle
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.HabitProgress
import com.earnd.app.domain.HabitSchedule
import com.earnd.app.domain.localDate
import com.earnd.app.domain.today
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Computes every upcoming alarm (due, offsets before, next nag) and arms them as exact alarms.
 * Also reconciles nag state with the data: finished items stop nagging, missed due times start.
 */
class AlarmPlanner(
    private val context: Context,
    private val tasks: TaskDao,
    private val habits: HabitDao,
    private val checkIns: CheckInDao,
    private val configs: AlarmConfigDao,
    private val nagStates: NagStateDao,
    private val nag: NagEngine,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val lock = Mutex()

    suspend fun plan(now: Long): List<PlannedAlarm> {
        val s = settings.current()
        val cfgs = configs.all().associateBy { it.ownerKind to it.ownerId }
        val out = mutableListOf<PlannedAlarm>()

        for (t in tasks.open()) {
            val due = AlarmTimes.taskDueAt(t, s, clock) ?: continue
            val cfg = cfgs[OwnerKind.TASK to t.id] ?: AlarmConfigEntity(OwnerKind.TASK, t.id)
            addItem(out, cfg, due, now)
        }
        for (h in habits.active()) {
            val cfg = cfgs[OwnerKind.HABIT to h.id] ?: continue
            val (at, minute) = nextHabitReminder(h, cfg, now) ?: continue
            out += PlannedAlarm(OwnerKind.HABIT, h.id, at, cfg.style, PlannedAlarm.habitReminder(minute))
        }
        nag.earliestNagAt()?.let { at ->
            val state = nagStates.all().filter { it.nextNagAt == at }.minByOrNull { it.dueAt }
            if (state != null) {
                val style = cfgs[state.ownerKind to state.ownerId]?.style ?: AlarmStyle.NOTIFICATION
                out += PlannedAlarm(state.ownerKind, state.ownerId, maxOf(at, now + 1000), style, PlannedAlarm.SLOT_NAG)
            }
        }
        return out
    }

    private fun addItem(out: MutableList<PlannedAlarm>, cfg: AlarmConfigEntity, due: Long, now: Long) {
        if (cfg.atDue && due > now) out += PlannedAlarm(cfg.ownerKind, cfg.ownerId, due, cfg.style)
        for (off in AlarmTimes.offsets(cfg)) {
            val at = due - AlarmTimes.minutes(off)
            if (at > now) out += PlannedAlarm(cfg.ownerKind, cfg.ownerId, at, cfg.style, PlannedAlarm.pre(off))
        }
    }

    /**
     * Next reminder (instant, stored minute) on a scheduled, non-vacation day. Every reminder time
     * is its own due occurrence; today's are skipped once the habit is complete.
     */
    private suspend fun nextHabitReminder(h: HabitEntity, cfg: AlarmConfigEntity, now: Long): Pair<Long, Int>? {
        val minutes = AlarmTimes.habitMinutes(cfg)
        if (minutes.isEmpty()) return null
        val s = settings.current()
        val today = clock.today()
        for (i in 0..14L) {
            val day = today.plusDays(i)
            if (!HabitSchedule.remindsOn(h, day) || s.onVacation(day.toEpochDay())) continue
            if (i == 0L && habitDone(h, day)) continue
            if (!quotaOpen(h, day)) continue
            val todayCount = if (i == 0L && !HabitProgress.isQuantity(h)) checkIns.forHabitDay(h.id, day.toEpochDay()).size else 0
            val slots = minutes.map { m -> AlarmTimes.at(day, DayStart.habitMinute(s, day, m), clock) to m }.sortedBy { it.first }
            for ((k, slot) in slots.withIndex()) {
                // Reminder k+1 of the day is already answered once that many check-ins exist (2 brushings = 2nd reminder done).
                if (todayCount >= k + 1) continue
                if (slot.first > now) return slot
            }
        }
        return null
    }

    /** Brings nag state in line with tasks and habits, then cancels and re-arms everything. */
    suspend fun rearmAll() = lock.withLock {
        val now = clock.now()
        reconcile(now)
        val planned = plan(now)
        cancelArmed()
        val uris = planned.map { arm(it) }
        settings.rawPut(KEY_ARMED, uris.joinToString("\n"))
        // Done elsewhere (app, widget, notification) while it is ringing: stop the ring and close the alarm screen.
        AlarmRingService.ringingOwner()?.let { (k, id) ->
            if (nagStates.get(k, id) == null) Ringing.stopAll(context)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun rearm(ownerKind: OwnerKind, ownerId: Long) = rearmAll()

    private suspend fun reconcile(now: Long) {
        val s = settings.current()
        val today = clock.today()
        for (st in nagStates.all()) {
            val stillPending = when (st.ownerKind) {
                OwnerKind.TASK -> tasks.get(st.ownerId)?.let { t ->
                    val due = AlarmTimes.taskDueAt(t, s, clock)
                    t.completedAt == null && due != null && due <= now && due == st.dueAt
                } ?: false
                OwnerKind.HABIT -> {
                    val h = habits.get(st.ownerId)
                    h != null && !h.archived && clock.localDate(st.dueAt) == today &&
                        !habitDone(h, today) && quotaOpen(h, today) && !answeredByCount(h, st.dueAt, today, s)
                }
            }
            if (!stillPending) { nag.resolve(st.ownerKind, st.ownerId); continue }
            if (st.ownerKind == OwnerKind.HABIT) {
                val latest = checkIns.forHabitDay(st.ownerId, today.toEpochDay()).maxOfOrNull { it.at }
                if (NagSchedule.progressedSinceLastNag(st, latest)) nag.restart(st.ownerKind, st.ownerId, latest!!)
            }
        }
        val tracked = nagStates.all().associateBy { it.ownerKind to it.ownerId }
        val cfgs = configs.all().associateBy { it.ownerKind to it.ownerId }
        for (t in tasks.open()) {
            val due = AlarmTimes.taskDueAt(t, s, clock) ?: continue
            val cfg = cfgs[OwnerKind.TASK to t.id] ?: AlarmConfigEntity(OwnerKind.TASK, t.id)
            if (!cfg.atDue && AlarmTimes.offsets(cfg).isEmpty()) continue
            val known = tracked[OwnerKind.TASK to t.id]
            if (due <= now && (known == null || known.dueAt != due)) nag.onDue(OwnerKind.TASK, t.id, due)
        }
        if (s.onVacation(today.toEpochDay())) return
        for (h in habits.active()) {
            val cfg = cfgs[OwnerKind.HABIT to h.id] ?: continue
            if (!HabitSchedule.remindsOn(h, today)) continue
            val due = AlarmTimes.habitInstants(cfg, today, clock) { DayStart.habitMinute(s, today, it) }
                .lastOrNull { it <= now } ?: continue
            // A newer reminder time replaces an older pending one (fresh counts, rings again).
            if (tracked[OwnerKind.HABIT to h.id]?.dueAt == due) continue
            if (habitDone(h, today) || !quotaOpen(h, today) || answeredByCount(h, due, today, s)) continue
            // A check-in after this reminder answered it; the next reminder time re-triggers.
            if (checkIns.forHabitDay(h.id, today.toEpochDay()).any { it.at >= due }) continue
            nag.onDue(OwnerKind.HABIT, h.id, due)
        }
    }

    /**
     * False for an "N times a week" habit whose weekly quota was already met on the days before [day]: it is not due
     * (the Today list hides it), so it must not remind or nag either.
     */
    private suspend fun quotaOpen(h: HabitEntity, day: java.time.LocalDate): Boolean {
        if (h.scheduleKind != ScheduleKind.TIMES_PER_WEEK) return true
        val mon = HabitSchedule.weekStart(day)
        val before = if (mon == day) 0 else checkIns.forHabitBetween(h.id, mon.toEpochDay(), day.toEpochDay() - 1)
            .groupBy { it.epochDay }.count { (_, cs) -> HabitProgress.isComplete(h, cs) }
        return HabitSchedule.isDueOn(h, day, before)
    }

    /**
     * A counted habit with several reminders a day: reminder number k is answered once k check-ins exist today, even if
     * they were made before it rang. Quantity habits are answered only by reaching their target.
     */
    private suspend fun answeredByCount(h: HabitEntity, dueAt: Long, day: java.time.LocalDate, s: com.earnd.app.data.Settings): Boolean {
        if (HabitProgress.isQuantity(h)) return false
        val cfg = configs.get(OwnerKind.HABIT, h.id) ?: return false
        val k = AlarmTimes.habitInstants(cfg, day, clock) { DayStart.habitMinute(s, day, it) }.count { it <= dueAt }
        return k > 0 && checkIns.forHabitDay(h.id, day.toEpochDay()).size >= k
    }

    private suspend fun habitDone(h: HabitEntity, day: java.time.LocalDate): Boolean =
        HabitProgress.isComplete(h, checkIns.forHabitDay(h.id, day.toEpochDay()))

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun fireIntent(uri: Uri) =
        Intent(context, AlarmReceiver::class.java).setAction(AlarmKeys.ACTION_FIRE).setData(uri)

    private suspend fun cancelArmed() {
        val previous = settings.rawGet(KEY_ARMED).orEmpty().lines().filter { it.isNotBlank() }
        for (uri in previous) {
            val pi = PendingIntent.getBroadcast(context, 0, fireIntent(Uri.parse(uri)),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: continue
            alarmManager().cancel(pi)
            pi.cancel()
        }
    }

    private fun arm(p: PlannedAlarm): String {
        val uri = AlarmKeys.uri(p.ownerKind, p.ownerId, p.slot)
        val intent = fireIntent(uri).putOwner(p.ownerKind, p.ownerId)
            .putExtra(AlarmKeys.EXTRA_SLOT, p.slot).putExtra(AlarmKeys.EXTRA_AT, p.at)
        val pi = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = alarmManager()
        when {
            !AlarmPermissions.canScheduleExact(context) ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pi)
            p.style == AlarmStyle.FULL_SCREEN ->
                am.setAlarmClock(AlarmManager.AlarmClockInfo(p.at, openAppIntent()), pi)
            else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pi)
        }
        return uri.toString()
    }

    private fun openAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private companion object {
        const val KEY_ARMED = "alarms.armed"
    }
}
