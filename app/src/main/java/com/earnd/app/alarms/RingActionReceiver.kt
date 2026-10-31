package com.earnd.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stop / Snooze from the ringing notification. Silences first, synchronously, then updates state. */
class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmKeys.ACTION_RING_STOP -> Ringing.stopAll(context)
            AlarmKeys.ACTION_RING_SNOOZE -> {
                Ringing.stopAll(context)
                val (kind, id) = AlarmKeys.ownerOf(intent) ?: return
                launchAsync { runCatching { AlarmsModule.actions.snooze(kind, id) } }
            }
        }
    }
}
