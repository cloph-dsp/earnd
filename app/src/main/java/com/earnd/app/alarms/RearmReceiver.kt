package com.earnd.app.alarms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms every alarm after boot, app update, clock or timezone change, or exact-alarm grant. */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        launchAsync {
            AlarmChannels.ensure(context)
            AlarmPermissions.refresh(context)
            AlarmsModule.resync()
            com.earnd.app.blocking.GuardWatch.check(context)
            // A new day (or a clock change): daily progress on the widgets starts from zero.
            runCatching { com.earnd.app.widgets.WidgetRefresher.updateAll(context) }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
