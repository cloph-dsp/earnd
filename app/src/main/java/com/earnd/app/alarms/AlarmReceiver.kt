package com.earnd.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the exact alarms armed by [AlarmPlanner] and the [NagTicker] heartbeat. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actions = AlarmsModule.actions
        when (intent.action) {
            AlarmKeys.ACTION_TICK -> launchAsync {
                NagTicker.ensure(context)
                actions.nagPass()
            }
            AlarmKeys.ACTION_FIRE -> launchAsync {
                val slot = intent.getStringExtra(AlarmKeys.EXTRA_SLOT) ?: PlannedAlarm.SLOT_DUE
                val owner = AlarmKeys.ownerOf(intent)
                when {
                    slot == PlannedAlarm.SLOT_NAG || owner == null -> actions.nagPass()
                    slot.startsWith("pre") -> {
                        val offset = slot.removePrefix("pre").toLongOrNull() ?: 0
                        val at = intent.getLongExtra(AlarmKeys.EXTRA_AT, System.currentTimeMillis())
                        actions.onUpcoming(owner.first, owner.second, at + offset * 60_000L)
                    }
                    else -> actions.onDue(owner.first, owner.second)
                }
            }
        }
    }
}
