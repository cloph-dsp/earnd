package com.earnd.app.alarms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.earnd.app.R
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind

/** Posts the full-screen-intent notification that opens [AlarmActivity] over the lock screen. */
internal object Ringing {
    /** Posts the ringing notification through [AlarmRingService]; falls back to a plain notification. */
    suspend fun show(context: Context, kind: OwnerKind, id: Long) {
        AlarmChannels.ensure(context)
        val label = AlarmsModule.actions.labelOf(kind, id).ifBlank { context.getString(R.string.alarms_ring_fallback) }
        if (AlarmRingService.start(context, kind, id, label)) return
        val n = build(context, kind, id, label).apply { flags = flags or android.app.Notification.FLAG_INSISTENT }
        context.getSystemService(NotificationManager::class.java).notify(AlarmKeys.NOTIF_RING, n)
    }

    fun build(context: Context, kind: OwnerKind, id: Long, label: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0, AlarmActivity.intent(context, kind, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, DoneActionReceiver::class.java).setAction(AlarmKeys.ACTION_DONE)
                .setData(AlarmKeys.uri(kind, id, "done-ring")).putOwner(kind, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, AlarmChannels.RING)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(label)
            .setContentText(context.getString(R.string.alarms_ring_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.alarms_done_action, PendingNotifier.ellipsize(label, 22)), done)
            .build()
    }

    /** Minimal notification for a service start that turns out to have nothing to ring. */
    fun placeholder(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, AlarmChannels.RING)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setContentText(context.getString(R.string.alarms_ring_text))
            .build()

    @Suppress("UNUSED_PARAMETER")
    fun dismiss(context: Context, kind: OwnerKind, id: Long) {
        AlarmRingService.stop(context)
        context.getSystemService(NotificationManager::class.java).cancel(AlarmKeys.NOTIF_RING)
        AlarmActivity.finishCurrent()
    }
}
