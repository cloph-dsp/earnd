package com.earnd.app.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.earnd.app.R
import com.earnd.app.alarms.AlarmPermissions
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.FocusDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import com.earnd.app.domain.ChangeBus
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.FocusOption
import com.earnd.app.domain.HabitService
import com.earnd.app.domain.TimedHabit
import kotlinx.coroutines.flow.Flow

sealed interface FocusStart {
    data class Started(val session: FocusSessionEntity) : FocusStart
    data class Busy(val habitId: Long) : FocusStart
    /** Several durations are offered: ask which one, then call start again with its minutes. */
    data class Choose(val habitId: Long, val options: List<FocusOption>) : FocusStart
    data object NotTimed : FocusStart
}

/** Outcome of finishing a stopwatch session: [earned] is null when it was too short to count. */
data class StopwatchResult(val minutes: Int, val earned: Int?, val bonus: Int)

/**
 * Runs timed focus habits. The session lives in Room, so the timer survives process death:
 * an exact alarm fires at the end ([FocusReceiver]) and [restore] re-arms after boot or restart.
 * Completing credits one ordinary check-in through [HabitService]; cancelling credits nothing.
 * Stopwatch sessions count up with no alarm and are credited by [finishStopwatch].
 */
class FocusManager(
    private val context: Context,
    private val dao: FocusDao,
    private val habitDao: HabitDao,
    private val habits: HabitService,
    private val clock: Clock,
    private val bus: ChangeBus,
    private val settings: SettingsRepository,
) {
    fun observe(): Flow<FocusSessionEntity?> = dao.observe()

    suspend fun running(): FocusSessionEntity? = dao.get()?.takeIf { it.state == FocusState.ACTIVE }

    /** Starts [habitId]'s session; [minutes] picks a duration option (required when several are offered). */
    suspend fun start(habitId: Long, minutes: Int? = null): FocusStart {
        completeIfDue()
        running()?.let { return FocusStart.Busy(it.habitId) }
        val h = habitDao.get(habitId)?.takeIf { !it.archived } ?: return FocusStart.NotTimed
        val s = if (h.stopwatch) {
            FocusSessionEntity(habitId = habitId, startedAt = clock.now(), durationMs = 0, stopwatch = true)
        } else {
            val opts = TimedHabit.options(h, settings.current())
            val opt = when {
                opts.isEmpty() -> return FocusStart.NotTimed
                minutes != null -> opts.firstOrNull { it.minutes == minutes } ?: return FocusStart.NotTimed
                opts.size > 1 -> return FocusStart.Choose(habitId, opts)
                else -> opts.single()
            }
            FocusSessionEntity(habitId = habitId, startedAt = clock.now(), durationMs = opt.minutes * 60_000L, rewardMinutes = opt.reward)
        }
        dao.put(s)
        if (!s.stopwatch) armAlarm(FocusMath.endAt(s))
        FocusNotifier.showOngoing(context, s, h.name)
        bus.emit(ChangeKind.FOCUS)
        return FocusStart.Started(s)
    }

    /** Discards the running session: no check-in, no minutes. */
    suspend fun cancel(): Boolean {
        val s = running() ?: return false
        if (dao.finish(s.startedAt, FocusState.CANCELLED) == 0) return false
        cancelAlarm()
        FocusNotifier.clearOngoing(context)
        bus.emit(ChangeKind.FOCUS)
        return true
    }

    /** Credits the session if its time is up. Safe to call from several places; only one wins. */
    suspend fun completeIfDue(): Boolean {
        val s = running() ?: return false
        if (!FocusMath.isDue(s, clock.now())) return false
        if (dao.finish(s.startedAt, FocusState.DONE) == 0) return false
        cancelAlarm()
        FocusNotifier.clearOngoing(context)
        val h = habitDao.get(s.habitId)
        val r = if (h != null && !h.archived) runCatching { habits.checkInWithBonus(h.id, rewardOverride = s.rewardMinutes) }.getOrNull() else null
        FocusChime.play(context)
        FocusNotifier.showDone(context, h?.name ?: context.getString(R.string.focus_fallback_name), (s.durationMs / 60_000L).toInt(), r?.checkIn?.minutesEarned, r?.bonus ?: 0)
        bus.emit(ChangeKind.FOCUS)
        return true
    }

    /** Ends the running stopwatch session (no penalty) and credits it if it reached the minimum. */
    suspend fun finishStopwatch(): StopwatchResult? {
        val s = running()?.takeIf { it.stopwatch } ?: return null
        val elapsed = (clock.now() - s.startedAt).coerceAtLeast(0L)
        if (dao.finishStopwatch(s.startedAt, elapsed) == 0) return null
        FocusNotifier.clearOngoing(context)
        val h = habitDao.get(s.habitId)?.takeIf { !it.archived }
        val reward = h?.let { TimedHabit.stopwatchReward(it, elapsed) }
        val r = if (h != null && reward != null) runCatching { habits.checkInWithBonus(h.id, rewardOverride = reward) }.getOrNull() else null
        val minutes = (elapsed / 60_000L).toInt()
        if (h != null && r != null) FocusNotifier.showDone(context, h.name, minutes, r.checkIn.minutesEarned, r.bonus)
        bus.emit(ChangeKind.FOCUS)
        return StopwatchResult(minutes, r?.checkIn?.minutesEarned, r?.bonus ?: 0)
    }

    /** After boot, app update or process start: finish an overdue session or re-arm its alarm and notification. */
    suspend fun restore() {
        if (completeIfDue()) return
        val s = running() ?: return
        if (!s.stopwatch) armAlarm(FocusMath.endAt(s))
        val name = habitDao.get(s.habitId)?.name ?: context.getString(R.string.focus_fallback_name)
        FocusNotifier.showOngoing(context, s, name)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_END, Intent(context, FocusReceiver::class.java).setAction(FocusReceiver.ACTION_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun armAlarm(at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching {
            if (AlarmPermissions.canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
        }
    }

    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    private companion object { const val REQ_END = 4200 }
}
