package com.earnd.app

import android.content.Context
import com.earnd.app.alarms.NagEngine
import com.earnd.app.blocking.SystemEssentialApps
import com.earnd.app.data.EarndDatabase
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.BlockPolicy
import com.earnd.app.domain.ChangeBus
import com.earnd.app.domain.Clock
import com.earnd.app.domain.HabitService
import com.earnd.app.domain.SystemTimeClock
import com.earnd.app.domain.TaskService
import com.earnd.app.domain.TimeBank

/**
 * Minimal manual DI. Feature areas add lazily-built singletons here.
 */
object ServiceLocator {
    lateinit var appContext: Context
        private set

    val changeBus: ChangeBus by lazy { ChangeBus() }
    val clock: Clock by lazy { SystemTimeClock }
    val db: EarndDatabase by lazy { EarndDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(db.settingDao(), clock, changeBus) }
    val timeBank: TimeBank by lazy { TimeBank(db.ledgerDao(), db.sessionDao(), settings, clock, changeBus) }
    val habitService: HabitService by lazy {
        HabitService(db.habitDao(), db.checkInDao(), timeBank, clock, changeBus, settings)
    }
    val taskService: TaskService by lazy { TaskService(db.taskDao(), timeBank, settings, clock, changeBus) }
    val essentialApps: SystemEssentialApps by lazy { SystemEssentialApps(appContext) }
    val blockPolicy: BlockPolicy by lazy {
        BlockPolicy(db.appRuleDao(), db.sessionDao(), habitService, settings, essentialApps, clock)
    }
    val nagEngine: NagEngine by lazy {
        NagEngine(db.nagStateDao(), db.alarmConfigDao(), settings, clock) { kind, id ->
            when (kind) {
                OwnerKind.TASK -> db.taskDao().get(id)?.title
                OwnerKind.HABIT -> db.habitDao().get(id)?.name
            } ?: ""
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
