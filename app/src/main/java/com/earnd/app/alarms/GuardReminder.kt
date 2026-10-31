package com.earnd.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.earnd.app.blocking.GuardWatch

/** Alarms-side scheduler for the "blocking is off" re-reminder. One inexact alarm at a time. */
object GuardReminder {
    const val NOTIFICATION_ID = AlarmKeys.NOTIF_GUARD_OFF

    private fun intent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, 3, Intent(context, AlarmReceiver::class.java).setAction(AlarmKeys.ACTION_GUARD),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Arms the re-check at [at]. Unless [replace], an already armed re-check is kept. */
    fun schedule(context: Context, at: Long, replace: Boolean = false) {
        if (!replace && intent(context, PendingIntent.FLAG_NO_CREATE) != null) return
        val pi = intent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context) {
        val pi = intent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    suspend fun onFired(context: Context) {
        // The fired PendingIntent stays registered; drop it so schedule() re-arms.
        cancel(context)
        GuardWatch.check(context, alert = true)
    }
}
