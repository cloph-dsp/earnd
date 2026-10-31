package com.earnd.app

import android.app.Application
import com.earnd.app.alarms.AlarmsModule
import com.earnd.app.widgets.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EarndApp : Application() {
    /** Lives as long as the process; feature modules hang their ChangeBus listeners here. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        AlarmsModule.start(this, appScope)     // re-plans alarms on task/habit/settings changes
        WidgetRefresher.start(this, appScope)  // refreshes widgets on balance/session/task changes
    }
}
