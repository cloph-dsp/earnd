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
import com.earnd.app.data.dao.TaskDao
import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import com.earnd.app.domain.ChangeBus
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.FocusOption
import com.earnd.app.domain.HabitService
import com.earnd.app.domain.TaskService
import com.earnd.app.domain.TimedHabit
import kotlinx.coroutines.flow.Flow

sealed interface FocusStart {
    data class Started(val session: FocusSessionEntity) : FocusStart
    data class Busy(val habitId: Long, val taskId: Long? = null) : FocusStart
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
    private val context: Context?,
    private val dao: FocusDao,
    private val habitDao: HabitDao,
    private val habits: HabitService,
    private val clock: Clock,
    private val bus: ChangeBus,
    private val settings: SettingsRepository,
    private val taskDao: TaskDao,
    private val tasks: TaskService,
) {
    fun observe(): Flow<FocusSessionEntity?> = dao.observe()

    suspend fun running(): FocusSessionEntity? = dao.get()?.takeIf { it.state == FocusState.ACTIVE }

    /** Starts [habitId]'s session; [minutes] picks a duration option (required when several are offered). */
    suspend fun start(habitId: Long, minutes: Int? = null): FocusStart {
        completeIfDue()
        running()?.let { return FocusStart.Busy(it.habitId, it.taskId) }
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
        context?.let { FocusNotifier.showOngoing(it, s, h.name) }
        bus.emit(ChangeKind.FOCUS)
        return FocusStart.Started(s)
    }

    /** Starts a fixed countdown on [taskId]; finishes pay the task's reward without completing it. */
    suspend fun startTask(taskId: Long, minutes: Int): FocusStart {
        completeIfDue()
        running()?.let { return FocusStart.Busy(it.habitId, it.taskId) }
        val t = taskDao.get(taskId) ?: return FocusStart.NotTimed
        // Task-owned session row: habitId=0 sentinel (habit ids start at 1).
        val s = FocusSessionEntity(
            habitId = 0,
            taskId = taskId,
            startedAt = clock.now(),
            durationMs = minutes * 60_000L,
            rewardMinutes = t.rewardMinutes?.coerceAtLeast(0),
        )
        dao.put(s)
        armAlarm(FocusMath.endAt(s))
        context?.let { FocusNotifier.showOngoing(it, s, t.title) }
        bus.emit(ChangeKind.FOCUS)
        return FocusStart.Started(s)
    }
    /** Discards the running session: no check-in, no minutes. */
    suspend fun cancel(): Boolean {
        val s = running() ?: return false
        if (dao.finish(s.startedAt, FocusState.CANCELLED) == 0) return false
        cancelAlarm()
        context?.let { FocusNotifier.clearOngoing(it) }
        bus.emit(ChangeKind.FOCUS)
        return true
    }

    /** Credits the session if its time is up. Safe to call from several places; only one wins. */
    suspend fun completeIfDue(): Boolean {
        val s = running() ?: return false
        if (!FocusMath.isDue(s, clock.now())) return false
        if (dao.finish(s.startedAt, FocusState.DONE) == 0) return false
        cancelAlarm()
        context?.let { FocusNotifier.clearOngoing(it) }
        val minutes = (s.durationMs / 60_000L).toInt()
        val ctx = context
        // DONE is already claimed; a failed save must not be reported as a check-in.
        val ok = if (s.taskId != null) {
            val t = taskDao.get(s.taskId)
            val reward = s.rewardMinutes ?: t?.rewardMinutes?.coerceAtLeast(0) ?: 0
            val name = t?.title ?: ctx?.getString(R.string.focus_fallback_name) ?: ""
            val done = runCatching { tasks.focusEarn(s.taskId, reward) }.isSuccess
            if (done) ctx?.let { FocusNotifier.showDone(it, name, minutes, reward.takeIf { r -> r > 0 }, 0) }
            else ctx?.let { FocusNotifier.showFailed(it, name, minutes) }
            done
        } else {
            val h = habitDao.get(s.habitId)
            val name = h?.name ?: ctx?.getString(R.string.focus_fallback_name) ?: ""
            val r = if (h != null && !h.archived) runCatching { habits.checkInWithBonus(h.id, rewardOverride = s.rewardMinutes) }.getOrNull() else null
            if (r != null) ctx?.let { FocusNotifier.showDone(it, name, minutes, r.checkIn.minutesEarned, r.bonus) }
            else ctx?.let { FocusNotifier.showFailed(it, name, minutes) }
            r != null
        }
        if (ok) ctx?.let { FocusChime.play(it) }
        bus.emit(ChangeKind.FOCUS)
        return ok
    }

    /** Ends the running stopwatch session (no penalty) and credits it if it reached the minimum. */
    suspend fun finishStopwatch(): StopwatchResult? {
        val s = running()?.takeIf { it.stopwatch } ?: return null
        val elapsed = (clock.now() - s.startedAt).coerceAtLeast(0L)
        if (dao.finishStopwatch(s.startedAt, elapsed) == 0) return null
        context?.let { FocusNotifier.clearOngoing(it) }
        val h = habitDao.get(s.habitId)?.takeIf { !it.archived }
        val reward = h?.let { TimedHabit.stopwatchReward(it, elapsed) }
        val r = if (h != null && reward != null) runCatching { habits.checkInWithBonus(h.id, rewardOverride = reward) }.getOrNull() else null
        val minutes = (elapsed / 60_000L).toInt()
        val ctx = context
        if (r != null) ctx?.let { FocusNotifier.showDone(it, h?.name ?: it.getString(R.string.focus_fallback_name), minutes, r.checkIn.minutesEarned, r.bonus) }
        else ctx?.let { FocusNotifier.showFailed(it, h?.name ?: it.getString(R.string.focus_fallback_name), minutes) }
        bus.emit(ChangeKind.FOCUS)
        return StopwatchResult(minutes, r?.checkIn?.minutesEarned, r?.bonus ?: 0)
    }

    /** After boot, app update or process start: finish an overdue session or re-arm its alarm and notification. */
    suspend fun restore() {
        if (completeIfDue()) return
        val s = running() ?: return
        if (!s.stopwatch) armAlarm(FocusMath.endAt(s))
        val fallback = context?.getString(R.string.focus_fallback_name) ?: ""
        val name = if (s.taskId != null) taskDao.get(s.taskId)?.title ?: fallback
        else habitDao.get(s.habitId)?.name ?: fallback
        context?.let { FocusNotifier.showOngoing(it, s, name) }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_END, Intent(context, FocusReceiver::class.java).setAction(FocusReceiver.ACTION_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun armAlarm(at: Long) {
        val ctx = context ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        runCatching {
            if (AlarmPermissions.canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
        }
    }

    private fun cancelAlarm() {
        val ctx = context ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    private companion object { const val REQ_END = 4200 }
}
