package com.earnd.app.blocking

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.alarms.AlarmChannels
import com.earnd.app.alarms.GuardReminder

/**
 * "Blocking is off" safeguard. When blocking is enabled in Earnd but the accessibility service
 * is not (switched off by the user, or reset by an update), posts a persistent notification that
 * opens Accessibility settings, and asks [GuardReminder] to check again in ~30 minutes.
 */
object GuardWatch {
    const val REMIND_AFTER_MS = 30 * 60_000L

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(context, GuardAccessibilityService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), ignoreCase = true) || it.equals(cn.flattenToShortString(), ignoreCase = true)
        }
    }

    /** Pure rule: warn when the user wants blocking but the service is not running. */
    fun shouldWarn(blockingEnabled: Boolean, serviceEnabled: Boolean): Boolean = blockingEnabled && !serviceEnabled

    /**
     * Re-evaluates the guard state. [alert] makes the notification sound again (the 30-minute
     * reminder); routine checks keep an already-posted notification silent.
     */
    suspend fun check(context: Context, alert: Boolean = false) {
        val blocking = runCatching { ServiceLocator.settings.current().blockingEnabled }.getOrDefault(true)
        if (shouldWarn(blocking, isServiceEnabled(context))) {
            post(context, alert)
            GuardReminder.schedule(context, ServiceLocator.clock.now() + REMIND_AFTER_MS, replace = alert)
        } else {
            clear(context)
        }
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(GuardReminder.NOTIFICATION_ID)
        GuardReminder.cancel(context)
    }

    private fun post(context: Context, alert: Boolean) {
        AlarmChannels.ensure(context)
        val open = PendingIntent.getActivity(
            context, 7, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, AlarmChannels.GUARD)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setContentTitle(context.getString(R.string.blocking_guard_off_title))
            .setContentText(context.getString(R.string.blocking_guard_off_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.blocking_guard_off_text)))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(GuardReminder.NOTIFICATION_ID, n)
        }
    }
}
