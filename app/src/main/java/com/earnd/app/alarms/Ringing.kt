package com.earnd.app.alarms

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.earnd.app.R
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind

/**
 * The ringing notification: full-screen intent to [AlarmActivity] (heads-up when the system
 * downgrades it), tap opens the alarm screen, and Stop / Snooze actions that work from the shade.
 */
internal object Ringing {
    /** Starts ringing through [AlarmRingService]. */
    suspend fun show(context: Context, kind: OwnerKind, id: Long) {
        AlarmChannels.ensure(context)
        val label = AlarmsModule.actions.labelOf(kind, id).ifBlank { context.getString(R.string.alarms_ring_fallback) }
        if (AlarmRingService.start(context, kind, id, label)) return
        // Service refused: a plain heads-up with the same actions (no looping sound to get stuck).
        // It uses a channel that makes its own sound and vibration: the ring channel is silent by design.
        context.getSystemService(NotificationManager::class.java)
            .notify(AlarmKeys.NOTIF_RING, build(context, kind, id, label, snoozesLeft = 0, channel = AlarmChannels.RING_FALLBACK))
    }

    fun openIntent(context: Context, kind: OwnerKind, id: Long): PendingIntent = PendingIntent.getActivity(
        context, 0, AlarmActivity.intent(context, kind, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(context: Context, action: String, kind: OwnerKind, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, RingActionReceiver::class.java).setAction(action)
                .setData(AlarmKeys.uri(kind, id, action.substringAfterLast('.'))).putOwner(kind, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun build(context: Context, kind: OwnerKind, id: Long, label: String, snoozesLeft: Int, channel: String = AlarmChannels.RING): Notification {
        val open = openIntent(context, kind, id)
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(label)
            .setContentText(context.getString(R.string.alarms_ring_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(open, true) // downgraded to heads-up when not allowed or screen in use
            .addAction(0, context.getString(R.string.alarms_ring_stop), actionIntent(context, AlarmKeys.ACTION_RING_STOP, kind, id))
        if (RingPolicy.canSnooze(snoozesLeft)) {
            b.addAction(0, context.getString(R.string.alarms_ring_snooze_short, NagSchedule.SNOOZE_MIN),
                actionIntent(context, AlarmKeys.ACTION_RING_SNOOZE, kind, id))
        }
        return b.build()
    }

    fun missed(context: Context, label: String) {
        val n = NotificationCompat.Builder(context, AlarmChannels.MISSED)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(context.getString(R.string.alarms_missed_title))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(AlarmKeys.NOTIF_MISSED, n) }
    }

    /** Minimal notification for a service start that turns out to have nothing to ring. */
    fun placeholder(context: Context): Notification =
        NotificationCompat.Builder(context, AlarmChannels.RING)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setContentText(context.getString(R.string.alarms_ring_text))
            .setSilent(true)
            .build()

    /** Silences and removes everything about the current ring. Safe to call any number of times. */
    @Suppress("UNUSED_PARAMETER")
    fun dismiss(context: Context, kind: OwnerKind, id: Long) = stopAll(context)

    fun stopAll(context: Context) {
        AlarmRingService.stop(context)
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(AlarmKeys.NOTIF_RING) }
        AlarmActivity.finishCurrent()
    }
}
