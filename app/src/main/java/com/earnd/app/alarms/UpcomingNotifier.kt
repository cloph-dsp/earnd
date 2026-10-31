package com.earnd.app.alarms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.earnd.app.R
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind
import java.util.Date

/** Heads-up reminder shown at an "offset before" time. Replaced per item, cleared when done. */
internal object UpcomingNotifier {
    fun show(context: Context, kind: OwnerKind, id: Long, label: String, dueAt: Long, silent: Boolean) {
        AlarmChannels.ensure(context)
        val time = DateFormat.getTimeFormat(context).format(Date(dueAt))
        val done = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, DoneActionReceiver::class.java).setAction(AlarmKeys.ACTION_DONE)
                .setData(AlarmKeys.uri(kind, id, "done-upcoming")).putOwner(kind, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, AlarmChannels.UPCOMING)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(label)
            .setContentText(context.getString(R.string.alarms_upcoming_text, time))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(silent)
            .setAutoCancel(true)
            .setTimeoutAfter((dueAt - System.currentTimeMillis()).coerceAtLeast(60_000L))
            .addAction(0, context.getString(R.string.alarms_done_action, PendingNotifier.ellipsize(label, 22)), done)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notifId(kind, id), n)
    }

    fun cancel(context: Context, kind: OwnerKind, id: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(notifId(kind, id))

    private fun notifId(kind: OwnerKind, id: Long) =
        AlarmKeys.NOTIF_UPCOMING_BASE + ((kind.ordinal * 100_003 + id) % 100_000).toInt()
}
