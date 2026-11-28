package com.earnd.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.blocking.BlockingIntents

/** Handles a per-item "Done: X" action from the pending or upcoming notification. */
class DoneActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmKeys.ACTION_DONE) return
        val (kind, id) = AlarmKeys.ownerOf(intent) ?: return
        launchAsync {
            val saved = AlarmsModule.actions.done(kind, id)
            val needsApp = if (saved) false else AlarmsModule.actions.needsApp(kind, id)
            if (doneOutcome(kind, saved, needsApp) == DoneOutcome.OPEN_APP) {
                context.startActivity(BlockingIntents.checkIn(context.applicationContext, id))
            }
        }
    }
}
