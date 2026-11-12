package com.earnd.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.domain.HabitSchedule
import com.earnd.app.domain.startOfDay
import com.earnd.app.domain.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Posts the morning nudge and the daily report, then arms the next one. Also re-arms after boot,
 * app update and clock/timezone changes. Reads habits and the ledger; never writes them.
 */
class NotifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                // The chain re-arms itself, so a failure while posting must not stop it.
                runCatching {
                    when (intent.action) {
                        ACTION_MORNING -> morning(app)
                        ACTION_REPORT -> report(app)
                    }
                }.onFailure { android.util.Log.e("Earnd", "Nudge or report failed", it) }
                runCatching { Notify.init(app) }.onFailure { android.util.Log.e("Earnd", "Re-arming nudge and report failed", it) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun todayLines(): List<HabitLine>? {
        val today = ServiceLocator.clock.today()
        if (ServiceLocator.settings.current().onVacation(today.toEpochDay())) return null
        val lump = ServiceLocator.settings.current().rewardMode == com.earnd.app.data.entity.RewardMode.ALL_DONE
        // Bonus habits are extras: not listed as missed (the all-done lump-sum mode still counts every habit).
        return ServiceLocator.habitService.daySummary(today).filter { lump || !it.habit.bonus }
            .map { HabitLine(it.habit.name, it.done, it.required) }
    }

    private suspend fun morning(context: Context) {
        val lines = todayLines() ?: return
        val msg = NotifyText.morning(ServiceLocator.clock.today().toEpochDay(), lines) ?: return
        post(context, NotifyChannels.MORNING, NotifyChannels.ID_MORNING, msg)
    }

    private suspend fun report(context: Context) {
        val lines = todayLines() ?: return
        val clock = ServiceLocator.clock
        val since = clock.startOfDay(clock.today())
        val today = ServiceLocator.db.ledgerDao().observeRecent(LEDGER_SCAN).first().filter { it.at >= since }
        val used = -today.filter { it.kind == LedgerKind.SPEND }.sumOf { it.minutes }
        val earned = today.filter { it.kind == LedgerKind.EARN_HABIT || it.kind == LedgerKind.EARN_TASK || it.kind == LedgerKind.COMEBACK || it.kind == LedgerKind.UNDO }
            .sumOf { it.minutes }
        val msg = NotifyText.report(lines, used, earned) ?: return
        post(context, NotifyChannels.REPORT, NotifyChannels.ID_REPORT, msg)
    }

    private fun post(context: Context, channel: String, id: Int, msg: NotifyMessage) {
        NotifyChannels.ensure(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.alarms_ic_notification)
            .setColor(context.getColor(R.color.alarms_accent))
            .setContentTitle(msg.title)
            .setContentText(msg.body.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(msg.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    companion object {
        const val ACTION_MORNING = "com.earnd.app.notify.MORNING"
        const val ACTION_REPORT = "com.earnd.app.notify.REPORT"
        private const val LEDGER_SCAN = 1_000
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
