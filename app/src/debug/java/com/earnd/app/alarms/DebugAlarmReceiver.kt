package com.earnd.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.data.entity.OwnerKind

/**
 * Debug builds only. Rings a test alarm from adb (needs the shell's DUMP permission):
 * adb shell am broadcast -n com.earnd.app/.alarms.DebugAlarmReceiver -a com.earnd.app.alarms.DEBUG_RING
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
        }
    }

    private companion object {
        const val ACTION_RING = "com.earnd.app.alarms.DEBUG_RING"
        const val ACTION_STOP = "com.earnd.app.alarms.DEBUG_STOP"
    }
}
