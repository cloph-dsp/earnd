package com.earnd.app.notify

import android.content.Context
import com.earnd.app.ServiceLocator

/** Entry points for app start and the settings screen. */
object Notify {
    /** Creates channels and arms the next nudge/report. Call on app start; idempotent. */
    suspend fun init(context: Context) {
        NotifyChannels.ensure(context)
        reschedule(context)
    }

    suspend fun prefs(): NotifyPrefs = NotifySettings.get(ServiceLocator.settings)

    /** Saves new choices and re-arms immediately. */
    suspend fun update(context: Context, p: NotifyPrefs) {
        NotifySettings.put(ServiceLocator.settings, p)
        reschedule(context)
    }

    suspend fun reschedule(context: Context) {
        NotifyScheduler(context.applicationContext, ServiceLocator.clock).reschedule(prefs())
    }
}
