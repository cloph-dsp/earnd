package com.earnd.app.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.earnd.app.alarms.AlarmPermissions
import com.earnd.app.data.dao.FocusDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import com.earnd.app.domain.ChangeBus
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.HabitService
import kotlinx.coroutines.flow.Flow

sealed interface FocusStart {
    data class Started(val session: FocusSessionEntity) : FocusStart
    data class Busy(val habitId: Long) : FocusStart
    data object NotTimed : FocusStart
}

/**
 * Runs timed focus habits. The session lives in Room, so the timer survives process death:
 * an exact alarm fires at the end ([FocusReceiver]) and [restore] re-arms after boot or restart.
 * Completing credits one ordinary check-in through [HabitService]; cancelling credits nothing.
 */
class FocusManager(
    private val context: Context,
    private val dao: FocusDao,
    private val habitDao: HabitDao,
    private val habits: HabitService,
    private val clock: Clock,
    private val bus: ChangeBus,
) {
    fun observe(): Flow<FocusSessionEntity?> = dao.observe()

    suspend fun running(): FocusSessionEntity? = dao.get()?.takeIf { it.state == FocusState.ACTIVE }

    suspend fun start(habitId: Long): FocusStart {
        completeIfDue()
        running()?.let { return FocusStart.Busy(it.habitId) }
        val h = habitDao.get(habitId)
        val min = h?.focusMinutes?.takeIf { it > 0 } ?: return FocusStart.NotTimed
        val s = FocusSessionEntity(habitId = habitId, startedAt = clock.now(), durationMs = min * 60_000L)
        dao.put(s)
        armAlarm(FocusMath.endAt(s))
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
        if (h != null && !h.archived) runCatching { habits.checkIn(h.id) }
        FocusChime.play(context)
        FocusNotifier.showDone(context, h?.name ?: "Focus", (s.durationMs / 60_000L).toInt())
        bus.emit(ChangeKind.FOCUS)
        return true
    }

    /** After boot, app update or process start: finish an overdue session or re-arm its alarm and notification. */
    suspend fun restore() {
        if (completeIfDue()) return
        val s = running() ?: return
        armAlarm(FocusMath.endAt(s))
        val name = habitDao.get(s.habitId)?.name ?: "Focus"
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
