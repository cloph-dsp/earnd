package com.earnd.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.earnd.app.data.dao.*
import com.earnd.app.data.entity.*

/** Enums are persisted by name using Room's built-in enum support. */
@Database(
    entities = [
        HabitEntity::class, CheckInEntity::class, TaskEntity::class, AlarmConfigEntity::class,
        NagStateEntity::class, LedgerEntryEntity::class, UnlockSessionEntity::class,
        AppRuleEntity::class, SettingEntity::class, FocusSessionEntity::class, MeterUseEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class EarndDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
    abstract fun taskDao(): TaskDao
    abstract fun alarmConfigDao(): AlarmConfigDao
    abstract fun nagStateDao(): NagStateDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun sessionDao(): SessionDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun settingDao(): SettingDao
    abstract fun focusDao(): FocusDao
    abstract fun meterUseDao(): MeterUseDao

    companion object {
        fun build(context: Context): EarndDatabase =
            Room.databaseBuilder(context, EarndDatabase::class.java, "earnd.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()

        /** Per-app metered minutes, and the per-habit "bonus" flag (not needed to close the day). */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS meter_use (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, at INTEGER NOT NULL, appKey TEXT NOT NULL, minutes INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE habit ADD COLUMN bonus INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Metering is the default model: former FULL rows (null rate) become METERED at 100 percent. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_rule SET meterPct = 100 WHERE meterPct IS NULL")
            }
        }

        /** Metered leisure apps (per-app charging rate) and the per-habit "reward beyond target" flag. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_rule ADD COLUMN meterPct INTEGER")
                db.execSQL("ALTER TABLE habit ADD COLUMN overTargetReward INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Per-item ring duration and volume overrides (null = global default). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_config ADD COLUMN ringSec INTEGER")
                db.execSQL("ALTER TABLE alarm_config ADD COLUMN volumePct INTEGER")
            }
        }

        /**
         * Rolling expiry: ledger rows gain expiresAt/remaining. The existing setting moves to ROLLING (24 h) and the
         * current period's balance is carried into one entry that expires in 24 h, so nothing is lost on update.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger ADD COLUMN expiresAt INTEGER")
                db.execSQL("ALTER TABLE ledger ADD COLUMN remaining INTEGER NOT NULL DEFAULT 0")
                migrateToRolling(db, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
            }
        }

        internal fun migrateToRolling(db: SupportSQLiteDatabase, now: Long, zone: java.time.ZoneId) {
            fun setting(key: String): String? =
                db.query("SELECT value FROM setting WHERE `key` = ?", arrayOf<Any>(key)).use { if (it.moveToFirst()) it.getString(0) else null }
            val period = setting("s.resetPeriod")?.let { v -> com.earnd.app.data.entity.ResetPeriod.entries.find { it.name == v } }
                ?: com.earnd.app.data.entity.ResetPeriod.DAILY
            if (period == com.earnd.app.data.entity.ResetPeriod.ROLLING) return
            val weekStart = setting("s.weekStartsOn")?.let { v -> java.time.DayOfWeek.entries.find { it.name == v } }
                ?: java.time.DayOfWeek.MONDAY
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val oldKey = com.earnd.app.domain.Periods.start(today, period, weekStart).toEpochDay()
            val newKey = today.toEpochDay()
            val carry = db.query("SELECT COALESCE(SUM(minutes), 0) FROM ledger WHERE periodKey = ?", arrayOf<Any>(oldKey)).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            if (carry > 0) {
                db.execSQL(
                    "INSERT INTO ledger (at, kind, minutes, sourceKind, sourceId, periodKey, expiresAt, remaining) " +
                        "VALUES (?, 'ADJUST', ?, NULL, NULL, ?, ?, ?)",
                    arrayOf<Any>(now, carry, newKey, now + 24 * 3_600_000L, carry),
                )
            }
            db.execSQL("INSERT OR REPLACE INTO setting (`key`, value) VALUES ('s.resetPeriod', 'ROLLING')")
            db.execSQL("INSERT OR REPLACE INTO setting (`key`, value) VALUES ('s.rollingExpiryHours', '24')")
        }

        /** Focus duration options, stopwatch habits and stopwatch sessions. Existing rows keep their behaviour. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit ADD COLUMN focusOptions TEXT")
                db.execSQL("ALTER TABLE habit ADD COLUMN stopwatch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habit ADD COLUMN stopwatchRate REAL")
                db.execSQL("ALTER TABLE habit ADD COLUMN stopwatchMinMin INTEGER")
                db.execSQL("ALTER TABLE habit ADD COLUMN stopwatchCapMin INTEGER")
                db.execSQL("ALTER TABLE focus_session ADD COLUMN stopwatch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE focus_session ADD COLUMN rewardMinutes INTEGER")
            }
        }

        /** Timed focus habits: optional focus length per habit and the (single) focus session row. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit ADD COLUMN focusMinutes INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_session` (`id` INTEGER NOT NULL, `habitId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * General recurrence: tasks move from repeatKind/repeatWeekdayMask/repeatInterval to an
         * RRULE string (converted row by row, nothing lost); habits gain RULE and TIMES_PER_WEEK.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit ADD COLUMN scheduleRule TEXT")
                db.execSQL("ALTER TABLE habit ADD COLUMN timesPerWeek INTEGER NOT NULL DEFAULT 0")
                db.execSQL(TASK_V4_CREATE.replace("`task`", "`task_new`"))
                val keep = TASK_V4_KEPT.joinToString(", ") { "`$it`" }
                db.execSQL("INSERT INTO task_new ($keep, repeatRule, repeatFromCompletion, repeatOccurrence) SELECT $keep, NULL, 0, 1 FROM task")
                db.query("SELECT id, repeatKind, repeatWeekdayMask, repeatInterval FROM task WHERE repeatKind != 'NONE'").use { c ->
                    while (c.moveToNext()) {
                        val rule = com.earnd.app.domain.RRule.fromLegacy(c.getString(1), c.getInt(2), c.getInt(3)) ?: continue
                        db.execSQL("UPDATE task_new SET repeatRule = ? WHERE id = ?", arrayOf<Any>(rule, c.getLong(0)))
                    }
                }
                db.execSQL("DROP TABLE task")
                db.execSQL("ALTER TABLE task_new RENAME TO task")
                TASK_V4_INDICES.forEach(db::execSQL)
            }
        }

        private val TASK_V4_KEPT = listOf(
            "id", "parentId", "title", "notes", "dueEpochDay", "dueMinuteOfDay", "priority",
            "repeatAnchorEpochDay", "rewardMinutes", "completedAt", "seriesId", "sortOrder", "createdAt",
        )
        private const val TASK_V4_CREATE = "CREATE TABLE IF NOT EXISTS `task` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`parentId` INTEGER, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, `dueEpochDay` INTEGER, `dueMinuteOfDay` INTEGER, " +
            "`priority` TEXT NOT NULL, `repeatRule` TEXT, `repeatFromCompletion` INTEGER NOT NULL DEFAULT 0, " +
            "`repeatAnchorEpochDay` INTEGER, `repeatOccurrence` INTEGER NOT NULL DEFAULT 1, `rewardMinutes` INTEGER, " +
            "`completedAt` INTEGER, `seriesId` INTEGER, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        private val TASK_V4_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_task_dueEpochDay` ON `task` (`dueEpochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_task_parentId` ON `task` (`parentId`)",
            "CREATE INDEX IF NOT EXISTS `index_task_seriesId` ON `task` (`seriesId`)",
        )

        /** Recurrence anchor for interval-weekly and yearly series; existing rows anchor on their due date. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN repeatAnchorEpochDay INTEGER")
            }
        }

        /** Parity pass: habit reminders, photo and reward overrides. Existing rewards move to rewardMinutes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit ADD COLUMN reminderDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habit ADD COLUMN reminderMessage TEXT")
                db.execSQL("ALTER TABLE habit ADD COLUMN photoRequired INTEGER")
                db.execSQL("ALTER TABLE habit ADD COLUMN rewardMinutes INTEGER")
                db.execSQL("UPDATE habit SET rewardMinutes = minutesPerCheckIn")
            }
        }
    }
}
