package com.earnd.app.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Charging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only (needs the shell's DUMP permission). Sets how a blocked app is charged, bypassing the
 * weakening gates; the app is added to the leisure list when it is not there yet.
 * adb shell am broadcast -n com.earnd.app/.blocking.DebugChargingReceiver \
 *   -a com.earnd.app.blocking.DEBUG_SET_CHARGING --es pkg com.aspiro.tidal --es rate 50
 * rate = 0 | 25 | 50 | 100 (full = 100). The result is logged (tag EarndDebug) and returned as the broadcast result data.
 */
class DebugChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pkg = intent.getStringExtra("pkg")?.trim().orEmpty()
        val rate = Charging.parse(intent.getStringExtra("rate").orEmpty())
        if (pkg.isEmpty() || rate == null) {
            setResult(1, "usage: --es pkg <package> --es rate 0|25|50|100", null)
            return
        }
        ServiceLocator.init(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val dao = ServiceLocator.db.appRuleDao()
                val cur = dao.get(pkg)
                val label = cur?.label ?: pkg
                dao.upsert(AppRuleEntity(pkg, AppRule.LEISURE, false, label, meterPct = rate.pct))
                ServiceLocator.changeBus.emit(ChangeKind.BLOCKLIST)
                val msg = "$pkg charging=${Charging.label(rate.pct)}"
                android.util.Log.i("EarndDebug", msg)
                pending.setResult(0, msg, null)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.earnd.app.blocking.DEBUG_SET_CHARGING"
    }
}
