package com.earnd.app.alarms

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.alarms.AlarmKeys.putOwner

/**
 * The single consolidated "pending" notification. Replaced in place (one id), removed when
 * nothing is pending. Each item gets its own "Done: X" action; there is deliberately no snooze.
 */
class PendingNotifier(private val context: Context) {

    /**
     * [alert] true: sound/vibration/heads-up (new items, nag bumps). A silent refresh keeps the
     * alerting channel (so the notification is never demoted to a hidden one) with
     * setOnlyAlertOnce; the quiet channel is only used during quiet hours ([quiet]).
     */
    suspend fun refresh(alert: Boolean, quiet: Boolean = false) {
        val now = ServiceLocator.clock.now()
        val items = ServiceLocator.nagEngine.pending(now)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (items.isEmpty()) {
            nm.cancel(AlarmKeys.NOTIF_PENDING)
            return
        }
        val states = ServiceLocator.db.nagStateDao().all()
        val digest = states.isNotEmpty() && states.filter { it.dueAt <= now }.all { NagSchedule.isDigest(it, now) }
        val needsApp = items.filter { it.ownerKind == com.earnd.app.data.entity.OwnerKind.HABIT && AlarmsModule.actions.needsApp(it.ownerKind, it.ownerId) }
            .map { it.ownerId }.toSet()
        nm.notify(AlarmKeys.NOTIF_PENDING, build(items, alert, digest, quiet, needsApp))
    }

    fun build(items: List<PendingItem>, alert: Boolean, digest: Boolean, quiet: Boolean = false, needsApp: Set<Long> = emptySet()): Notification {
        AlarmChannels.ensure(context)
        val res = context.resources
        val title = if (digest) res.getQuantityString(R.plurals.alarms_digest_title, items.size, items.size)
        else res.getQuantityString(R.plurals.alarms_pending_title, items.size, items.size)
        val header = items.joinToString(" · ") { shortLabel(it) }
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText(header)
        items.take(MAX_LINES).forEach { inbox.addLine(line(it)) }
        if (items.size > MAX_LINES) inbox.addLine(context.getString(R.string.alarms_more, items.size - MAX_LINES))

        val builder = NotificationCompat.Builder(context, if (quiet) AlarmChannels.PENDING_QUIET else AlarmChannels.PENDING)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(title)
            .setContentText(header)
            .setStyle(inbox)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setSilent(quiet)
            .setOngoing(false)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setNumber(items.sumOf { it.nagCount })
        openAppIntent()?.let { builder.setContentIntent(it) }
        // Android shows up to three actions; items are already ordered most-ignored first.
        items.take(MAX_ACTIONS).forEach { item ->
            builder.addAction(0, context.getString(R.string.alarms_done_action, ellipsize(item.label, 22)), doneIntent(item, item.ownerKind == com.earnd.app.data.entity.OwnerKind.HABIT && item.ownerId in needsApp))
        }
        return builder.build()
    }

    private fun line(it: PendingItem): String =
        if (it.nagCount == 0) it.label
        else context.resources.getQuantityString(R.plurals.alarms_line_nagged, it.nagCount, it.label, it.nagCount)

    private fun shortLabel(it: PendingItem): String {
        val short = ellipsize(it.label.substringBefore(' ').ifBlank { it.label }, 12)
        return if (it.nagCount > 0) "$short ×${it.nagCount}" else short
    }

    private fun doneIntent(item: PendingItem, needsApp: Boolean): PendingIntent {
        if (needsApp) {
            return PendingIntent.getActivity(context, (10_000 + item.ownerId % 50_000).toInt(),
                com.earnd.app.blocking.BlockingIntents.checkIn(context, item.ownerId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val intent = Intent(context, DoneActionReceiver::class.java).setAction(AlarmKeys.ACTION_DONE)
            .setData(AlarmKeys.uri(item.ownerKind, item.ownerId, "done"))
            .putOwner(item.ownerKind, item.ownerId)
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 3, it, PendingIntent.FLAG_IMMUTABLE)
        }

    companion object {
        const val MAX_ACTIONS = 3
        const val MAX_LINES = 7

        internal fun ellipsize(s: String, max: Int): String =
            if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
    }
}
