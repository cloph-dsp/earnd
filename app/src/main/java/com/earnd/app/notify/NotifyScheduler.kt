package com.earnd.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.earnd.app.domain.Clock

/** Arms one alarm each for the next morning nudge and daily report. Independent of AlarmPlanner. */
class NotifyScheduler(private val context: Context, private val clock: Clock) {

    fun reschedule(p: NotifyPrefs) {
        set(NotifyReceiver.ACTION_MORNING, REQ_MORNING, p.morningMin.takeIf { p.morningEnabled })
        set(NotifyReceiver.ACTION_REPORT, REQ_REPORT, p.reportMin.takeIf { p.reportEnabled })
    }

    private fun set(action: String, requestCode: Int, minuteOfDay: Int?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, NotifyReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (minuteOfDay == null) {
            am.cancel(pi)
            return
        }
        val at = NotifyTimes.nextAt(clock.now(), minuteOfDay, clock.zone())
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private companion object {
        const val REQ_MORNING = 41
        const val REQ_REPORT = 42
    }
}
