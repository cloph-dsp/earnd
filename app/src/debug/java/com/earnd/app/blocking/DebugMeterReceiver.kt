package com.earnd.app.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.MeterMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only (needs the shell's DUMP permission). Prints the metering state: what counts, rate, since when,
 * balance, remaining real time, carry, grants and whether the overlay is shown.
 * adb shell am broadcast -n com.earnd.app/.blocking.DebugMeterReceiver -a com.earnd.app.blocking.DEBUG_METER_STATE
 * The result is logged (tag EarndDebug) and returned as the broadcast result data.
 */
class DebugMeterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        ServiceLocator.init(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val now = System.currentTimeMillis()
                val e = ServiceLocator.meterEngine
                val a = e.current()
                val bal = ServiceLocator.timeBank.balance(now).minutes
                val msg = buildString {
                    append("counting=").append(a?.let { "${it.pkg} key=${it.key} rate=${it.ratePct}% since=${(now - it.since) / 1000}s ago" } ?: "none")
                    append(" balance=").append(bal).append("min")
                    append(" remaining=").append(e.remainingMs(now)?.let { "${it / 1000}s" } ?: "n/a")
                    append(" carry=").append(MeterMath.carrySeconds(e.carryNow())).append("s")
                    append(" grants=[").append(ServiceLocator.grants.describe(now)).append("]")
                    append(" overlay=").append(MeterOverlayState.visible)
                }
                android.util.Log.i("EarndDebug", msg)
                pending.setResult(0, msg, null)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.earnd.app.blocking.DEBUG_METER_STATE"
    }
}
