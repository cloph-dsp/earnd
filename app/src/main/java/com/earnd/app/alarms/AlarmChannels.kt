package com.earnd.app.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.earnd.app.R

/** Notification channels owned by the alarms feature. Safe to call repeatedly. */
object AlarmChannels {
    const val PENDING = "alarms_pending"
    const val PENDING_QUIET = "alarms_pending_quiet"
    const val UPCOMING = "alarms_upcoming"
    const val RING = "alarms_ring"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pending = NotificationChannel(PENDING, context.getString(R.string.alarms_channel_pending),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarms_channel_pending_desc)
            enableVibration(true)
        }
        val quiet = NotificationChannel(PENDING_QUIET, context.getString(R.string.alarms_channel_quiet),
            NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) }
        val upcoming = NotificationChannel(UPCOMING, context.getString(R.string.alarms_channel_upcoming),
            NotificationManager.IMPORTANCE_DEFAULT)
        val ring = NotificationChannel(RING, context.getString(R.string.alarms_channel_ring),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarms_channel_ring_desc)
            // The ringing activity plays sound itself; the channel sound is a fallback if it cannot open.
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setBypassDnd(true)
        }
        nm.createNotificationChannels(listOf(pending, quiet, upcoming, ring))
    }
}
