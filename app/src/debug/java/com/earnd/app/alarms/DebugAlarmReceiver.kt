package com.earnd.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.HabitProgress
import com.earnd.app.domain.localDate
import kotlinx.coroutines.launch

/**
 * Debug builds only. Rings a test alarm from adb (needs the shell's DUMP permission):
 * adb shell am broadcast -n com.earnd.app/.alarms.DebugAlarmReceiver -a com.earnd.app.alarms.DEBUG_RING
 * Habit reminder firing now: ... -a com.earnd.app.alarms.DEBUG_HABIT_REMINDER --el id <habitId>
 */
class DebugAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmChannels.ensure(context)
        when (intent.action) {
            ACTION_RING -> {
                val kind = intent.getStringExtra(AlarmKeys.EXTRA_KIND)
                    ?.let { runCatching { OwnerKind.valueOf(it) }.getOrNull() } ?: OwnerKind.TASK
                val id = intent.getLongExtra(AlarmKeys.EXTRA_ID, 0L)
                val label = intent.getStringExtra(AlarmKeys.EXTRA_LABEL) ?: "Test alarm"
                val timeout = intent.getLongExtra(AlarmKeys.EXTRA_TIMEOUT_MS, 0L)
                AlarmRingService.start(context, kind, id, label, timeout)
            }
            ACTION_STOP -> Ringing.stopAll(context)
            ACTION_HABIT -> {
                val id = intent.getLongExtra(AlarmKeys.EXTRA_ID, -1L)
                if (id < 0) return
                val pending = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    try { simulateHabitReminder(context, id) } finally { pending.finish() }
                }
            }
        }
    }

    /** Same path as a real reminder: marks the habit due now (if still incomplete) and applies its style. */
    private suspend fun simulateHabitReminder(context: Context, id: Long) {
        val db = com.earnd.app.ServiceLocator.db
        val h = db.habitDao().get(id) ?: return
        val clock = com.earnd.app.ServiceLocator.clock
        val now = clock.now()
        val today = clock.localDate(now).toEpochDay()
        if (HabitProgress.isComplete(h, db.checkInDao().forHabitDay(id, today))) return
        com.earnd.app.ServiceLocator.nagEngine.onDue(OwnerKind.HABIT, id, now - 1000)
        AlarmsModule.actions.onDue(OwnerKind.HABIT, id)
    }

    private companion object {
        const val ACTION_HABIT = "com.earnd.app.alarms.DEBUG_HABIT_REMINDER"
        const val ACTION_RING = "com.earnd.app.alarms.DEBUG_RING"
        const val ACTION_STOP = "com.earnd.app.alarms.DEBUG_STOP"
    }
}
