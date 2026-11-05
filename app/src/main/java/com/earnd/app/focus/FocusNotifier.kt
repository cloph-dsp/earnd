package com.earnd.app.focus

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.earnd.app.R
import com.earnd.app.data.entity.FocusSessionEntity

/** Ongoing countdown notification while focusing, and the "done" notice afterwards. */
object FocusNotifier {
    private const val CH_ONGOING = "focus_ongoing"
    private const val CH_DONE = "focus_done"
    private const val ID_ONGOING = 4201
    private const val ID_DONE = 4202

    private fun ensure(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(CH_ONGOING, context.getString(R.string.focus_channel_timer), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            NotificationChannel(CH_DONE, context.getString(R.string.focus_channel_done), NotificationManager.IMPORTANCE_HIGH),
        ))
    }

    private fun open(context: Context, askCancel: Boolean): PendingIntent = PendingIntent.getActivity(
        context, if (askCancel) 1 else 0, FocusActivity.intent(context, askCancel),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun showOngoing(context: Context, s: FocusSessionEntity, habitName: String) {
        ensure(context)
        val n = Notification.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(context.getString(if (s.stopwatch) R.string.focus_notif_practising else R.string.focus_notif_focusing, habitName))
            .setContentText(if (s.stopwatch) context.getString(R.string.focus_notif_stopwatch_text)
                else context.getString(R.string.focus_notif_countdown_text, s.durationMs / 60_000L))
            .setWhen(if (s.stopwatch) s.startedAt else FocusMath.endAt(s))
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(!s.stopwatch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(open(context, askCancel = false))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.focus_cancel), open(context, askCancel = true)).build())
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_ONGOING, n) }
    }

    fun clearOngoing(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_ONGOING)
    }

    @SuppressLint("MissingPermission")
    fun showDone(context: Context, habitName: String, minutes: Int, earned: Int? = null, bonus: Int = 0) {
        ensure(context)
        val n = Notification.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(context.getString(R.string.focus_done_title, habitName, minutes))
            .setContentText(when {
                earned == null -> context.getString(R.string.focus_done_checked)
                bonus > 0 -> context.getString(R.string.focus_done_checked_bonus, earned, bonus)
                else -> context.getString(R.string.focus_done_checked_min, earned)
            })
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 2,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_DONE, n) }
    }
}
