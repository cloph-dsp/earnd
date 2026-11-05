package com.earnd.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.earnd.app.R

/** Channels for the morning nudge and daily report (separate from the alarms' channels). */
object NotifyChannels {
    const val MORNING = "notify_morning"
    const val REPORT = "notify_report"

    // Not in the 4100s: those belong to the alarm notifications (ring, guard-off), which a nudge must never replace.
    const val ID_MORNING = 4301
    const val ID_REPORT = 4302

    fun ensure(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(MORNING, context.getString(R.string.notify_channel_morning),
                    NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(REPORT, context.getString(R.string.notify_channel_report),
                    NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }
}
