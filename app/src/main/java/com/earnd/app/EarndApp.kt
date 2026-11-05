package com.earnd.app

import android.app.Application
import com.earnd.app.alarms.AlarmsModule
import com.earnd.app.widgets.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.earnd.app.domain.DateTimeFormats

class EarndApp : Application() {
    /** Lives as long as the process; feature modules hang their ChangeBus listeners here. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        com.earnd.app.i18n.Str.init(this)
        ServiceLocator.init(this)
        ServiceLocator.timeBank // registers the period-change hook on settings before anything edits them
        appScope.launch { // keeps DateTimeFormats.current in sync for non-UI text (lock screen, alarms, widgets)
            ServiceLocator.settings.settings.collect { s ->
                DateTimeFormats.current = DateTimeFormats(s.dateFormat, s.timeFormat, android.text.format.DateFormat.is24HourFormat(this@EarndApp))
            }
        }
        appScope.launch { runCatching { com.earnd.app.notify.Notify.init(this@EarndApp) } } // nudge and report survive a force-stop
        AlarmsModule.start(this, appScope)     // re-plans alarms on task/habit/settings changes
        WidgetRefresher.start(this, appScope)  // refreshes widgets on balance/session/task changes
        appScope.launch { runCatching { ServiceLocator.focus.restore() } } // finish or re-arm a focus session after restart
    }
}
