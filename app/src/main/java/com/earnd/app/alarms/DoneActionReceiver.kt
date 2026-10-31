package com.earnd.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles a per-item "Done: X" action from the pending or upcoming notification. */
class DoneActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmKeys.ACTION_DONE) return
        val (kind, id) = AlarmKeys.ownerOf(intent) ?: return
        launchAsync { AlarmsModule.actions.done(kind, id) }
    }
}
