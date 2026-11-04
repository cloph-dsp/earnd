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
import com.earnd.app.domain.MeterEngine
import com.earnd.app.domain.SystemTimeClock
import com.earnd.app.domain.TaskService
import com.earnd.app.domain.TimeBank
import com.earnd.app.domain.MutexTransactor
import com.earnd.app.domain.Transactor
import androidx.room.withTransaction

/**
 * Minimal manual DI. Feature areas add lazily-built singletons here.
 */
object ServiceLocator {
    lateinit var appContext: Context
        private set

    val changeBus: ChangeBus by lazy { ChangeBus() }
    val clock: Clock by lazy { SystemTimeClock }
    val db: EarndDatabase by lazy { EarndDatabase.build(appContext) }
    val backup: com.earnd.app.backup.BackupStore by lazy { com.earnd.app.backup.BackupStore(db) }
    val settings: SettingsRepository by lazy { SettingsRepository(db.settingDao(), clock, changeBus) }
    /** Serializes TimeBank read-then-write steps in-process and runs them in one Room transaction. */
    val transactor: Transactor by lazy {
        MutexTransactor(object : Transactor {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
        })
    }
    val timeBank: TimeBank by lazy { TimeBank(db.ledgerDao(), db.sessionDao(), settings, clock, changeBus, transactor) }
    val habitService: HabitService by lazy {
        HabitService(db.habitDao(), db.checkInDao(), timeBank, clock, changeBus, settings)
    }
    val taskService: TaskService by lazy { TaskService(db.taskDao(), timeBank, settings, clock, changeBus) }
    val photoStore: com.earnd.app.photo.PhotoStore by lazy { com.earnd.app.photo.PhotoStore(appContext) }
    val essentialApps: SystemEssentialApps by lazy { SystemEssentialApps(appContext) }
    val blockPolicy: BlockPolicy by lazy {
        BlockPolicy(db.appRuleDao(), db.sessionDao(), habitService, settings, essentialApps, clock,
            focusActive = { now -> com.earnd.app.focus.FocusMath.isRunning(db.focusDao().get(), now) },
            ownPackage = appContext.packageName,
            balance = { now -> timeBank.balance(now).minutes })
    }
    /** Charges metered leisure apps for foreground time (see [MeterEngine]). */
    val meterEngine: MeterEngine by lazy {
        MeterEngine(
            debit = { timeBank.debitMetered(it) },
            loadCarry = { settings.rawGet("meter.carry")?.toLongOrNull() ?: 0L },
            saveCarry = { settings.rawPut("meter.carry", it.toString()) },
        )
    }
    /** Counting grants: leisure targets the user confirmed at the gate (see [com.earnd.app.domain.Grants]). */
    val grants: com.earnd.app.domain.Grants by lazy { com.earnd.app.domain.Grants() }
    val nagEngine: NagEngine by lazy {
        NagEngine(db.nagStateDao(), db.alarmConfigDao(), settings, clock) { kind, id ->
            when (kind) {
                OwnerKind.TASK -> db.taskDao().get(id)?.title
                OwnerKind.HABIT -> db.habitDao().get(id)?.name
            } ?: ""
        }
    }

    val focus: com.earnd.app.focus.FocusManager by lazy { com.earnd.app.focus.FocusManager(appContext, db.focusDao(), db.habitDao(), habitService, clock, changeBus, settings) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
