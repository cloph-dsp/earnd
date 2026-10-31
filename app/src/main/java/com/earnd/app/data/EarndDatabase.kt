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
        AppRuleEntity::class, SettingEntity::class,
    ],
    version = 4,
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

    companion object {
        fun build(context: Context): EarndDatabase =
            Room.databaseBuilder(context, EarndDatabase::class.java, "earnd.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

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
