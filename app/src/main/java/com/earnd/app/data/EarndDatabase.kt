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
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

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
