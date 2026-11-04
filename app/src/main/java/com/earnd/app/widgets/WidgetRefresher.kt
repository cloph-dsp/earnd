package com.earnd.app.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.ChangeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps all widgets current: ChangeBus events, session end, and a periodic fallback. */
object WidgetRefresher {
    private const val PERIODIC = "widgets_periodic"
    private const val MIDNIGHT = "widgets_midnight"
    private val relevant = setOf(
        ChangeKind.TASKS, ChangeKind.BALANCE, ChangeKind.SESSION, ChangeKind.SETTINGS,
        ChangeKind.CHECKINS, ChangeKind.HABITS,
    )
    private val started = AtomicBoolean(false)

    suspend fun updateAll(context: Context) {
        TasksWidget().updateAll(context)
        HabitWidget().updateAll(context)
    }

    /** Call once from Application.onCreate; later calls are ignored. */
    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        schedulePeriodic(app)
        scheduleMidnight(app, ExistingWorkPolicy.KEEP)
        scope.launch {
            ServiceLocator.changeBus.events
                .filter { it in relevant }
                .debounce(300)
                .collect { runCatching { updateAll(app) } }
        }
    }

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One refresh just after the next local midnight, so daily progress resets. */
    fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy) {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        val delay = java.time.Duration.between(now, next).toMillis() + 5_000
        val req = OneTimeWorkRequestBuilder<MidnightWidgetWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT, policy, req)
    }

}

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Closes an expired session so the lock state flips.
        ServiceLocator.timeBank.activeSession(ServiceLocator.clock.now())
        WidgetRefresher.updateAll(applicationContext)
        return Result.success()
    }
}

class MidnightWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { WidgetRefresher.updateAll(applicationContext) }
        // Chains after this run (REPLACE would cancel the running worker).
        WidgetRefresher.scheduleMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
