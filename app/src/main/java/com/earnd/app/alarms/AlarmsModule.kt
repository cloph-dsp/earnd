package com.earnd.app.alarms

import android.content.Context
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.ChangeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Entry point for the alarms feature. The app calls [start] once from `EarndApp.onCreate`
 * with an application-wide scope; everything else is driven by alarms and receivers.
 */
object AlarmsModule {
    val planner: AlarmPlanner by lazy {
        val db = ServiceLocator.db
        AlarmPlanner(
            ServiceLocator.appContext, db.taskDao(), db.habitDao(), db.checkInDao(), db.alarmConfigDao(),
            db.nagStateDao(), ServiceLocator.nagEngine, ServiceLocator.settings, ServiceLocator.clock,
        )
    }

    val notifier: PendingNotifier by lazy { PendingNotifier(ServiceLocator.appContext) }

    val actions: AlarmActions by lazy { AlarmActions(ServiceLocator.appContext) }

    private val relevant = setOf(
        ChangeKind.TASKS, ChangeKind.HABITS, ChangeKind.CHECKINS, ChangeKind.SETTINGS, ChangeKind.ALARMS,
    )

    /** Creates channels, arms everything, and re-plans (debounced) whenever relevant data changes. */
    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope) {
        AlarmChannels.ensure(context)
        NagTicker.ensure(context)
        scope.launch { resync() }
        scope.launch {
            ServiceLocator.changeBus.events.filter { it in relevant }.debounce(600).collect { resync() }
        }
    }

    /** Re-plan alarms and refresh the pending notification without alerting. */
    suspend fun resync() {
        runCatching {
            actions.housekeeping()
            planner.rearmAll()
            notifier.refresh(alert = false)
        }
    }
}
