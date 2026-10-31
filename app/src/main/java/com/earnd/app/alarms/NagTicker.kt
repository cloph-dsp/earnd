package com.earnd.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Safety-net heartbeat. Exact nag alarms do the real work; this ~5 min inexact alarm (plus a
 * 15 min WorkManager job that re-arms it) catches anything missed after doze or a killed process.
 */
object NagTicker {
    const val INTERVAL_MS = 5 * 60_000L
    private const val WORK_NAME = "alarms_nag_tick"

    fun ensure(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL_MS, tickIntent(context),
        )
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 2, Intent(context, AlarmReceiver::class.java).setAction(AlarmKeys.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    class TickWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            AlarmsModule.actions.nagPass()
            ensure(applicationContext)
            return Result.success()
        }
    }
}
