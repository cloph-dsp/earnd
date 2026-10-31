package com.earnd.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.earnd.app.data.dao.*
import com.earnd.app.data.entity.*

/** Enums are persisted by name using Room's built-in enum support. */
@Database(
    entities = [
        HabitEntity::class, CheckInEntity::class, TaskEntity::class, AlarmConfigEntity::class,
        NagStateEntity::class, LedgerEntryEntity::class, UnlockSessionEntity::class,
        AppRuleEntity::class, SettingEntity::class,
    ],
    version = 1,
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
            Room.databaseBuilder(context, EarndDatabase::class.java, "earnd.db").build()
    }
}
