package com.earnd.app.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.earnd.app.R

/** Notification channels owned by the alarms feature. Safe to call repeatedly. */
object AlarmChannels {
    const val PENDING = "alarms_pending"
    const val PENDING_QUIET = "alarms_pending_quiet"
    const val UPCOMING = "alarms_upcoming"
    /** v2: silent channel. The service plays the sound itself; the old channel looped its own sound. */
    const val RING = "alarms_ring_v2"
    private const val RING_LEGACY = "alarms_ring"
    const val MISSED = "alarms_missed"
    /** Used only when the ringing service cannot start: the system plays the sound itself, once. */
    const val RING_FALLBACK = "alarms_ring_fallback"
    const val GUARD = "alarms_guard_off"

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
            // Sound comes only from AlarmRingService, so Stop always silences everything.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        val fallback = NotificationChannel(RING_FALLBACK, context.getString(R.string.alarms_channel_ring_fallback),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarms_channel_ring_fallback_desc)
            setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setBypassDnd(true)
        }
        val guard = NotificationChannel(GUARD, context.getString(R.string.alarms_channel_guard),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarms_channel_guard_desc)
        }
        val missed = NotificationChannel(MISSED, context.getString(R.string.alarms_channel_missed),
            NotificationManager.IMPORTANCE_DEFAULT)
        nm.deleteNotificationChannel(RING_LEGACY)
        nm.createNotificationChannels(listOf(pending, quiet, upcoming, ring, fallback, guard, missed))
    }
}
