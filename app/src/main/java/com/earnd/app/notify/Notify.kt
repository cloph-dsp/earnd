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

    suspend fun prefs(): NotifyPrefs = ServiceLocator.settings.current().let {
        NotifyPrefs(it.morningNudgeEnabled, it.morningNudgeMin, it.dailyReportEnabled, it.dailyReportMin)
    }

    suspend fun reschedule(context: Context) {
        NotifyScheduler(context.applicationContext, ServiceLocator.clock).reschedule(prefs(), ServiceLocator.settings.current())
    }
}
