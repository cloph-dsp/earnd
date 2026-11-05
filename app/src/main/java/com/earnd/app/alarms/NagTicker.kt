package com.earnd.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Low-cost safety net. Nags are driven by one exact alarm for the next nag time, armed by
 * [AlarmPlanner]; there is no periodic wake-up alarm. An hourly WorkManager job (batched by
 * the system) re-plans in case an alarm was lost, e.g. after the app was force-stopped.
 */
object NagTicker {
    private const val WORK_NAME = "alarms_nag_tick"

    fun ensure(context: Context) {
        cancelLegacyTick(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TickWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    /** Earlier versions armed a 5-minute heartbeat alarm; make sure it is gone. */
    private fun cancelLegacyTick(context: Context) {
        val pi = PendingIntent.getBroadcast(
            context, 2, Intent(context, AlarmReceiver::class.java).setAction(AlarmKeys.ACTION_TICK),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    class TickWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            AlarmsModule.actions.nagPass()
            runCatching { com.earnd.app.notify.Notify.init(applicationContext) } // re-arms the nudge and report if something cleared them
            return Result.success()
        }
    }
}
