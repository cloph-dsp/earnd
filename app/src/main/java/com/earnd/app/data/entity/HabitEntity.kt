package com.earnd.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val timesPerDay: Int = 1,
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    /** bit0 = Monday .. bit6 = Sunday */
    val weekdayMask: Int = 0b1111111,
    val everyNDays: Int = 1,
    /** Start date for EVERY_N_DAYS. */
    val anchorEpochDay: Long,
    val segment: DaySegment = DaySegment.NONE,
    val targetQuantity: Double? = null,
    val unit: String? = null,
    /** Legacy per-habit reward; superseded by [rewardMinutes] (copied there by migration 1->2). */
    val minutesPerCheckIn: Int = 5,
    val checkInMode: CheckInMode = CheckInMode.TAP,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    /** Reminder weekdays, bit0 = Monday .. bit6 = Sunday; 0 = every scheduled day. */
    @ColumnInfo(defaultValue = "0") val reminderDays: Int = 0,
    /** Custom reminder text; null or blank uses the habit name. */
    val reminderMessage: String? = null,
    /** Photo proof override; null follows Settings.photoRequiredDefault. */
    val photoRequired: Boolean? = null,
    /** Minutes per check-in override; null follows Settings.defaultHabitRewardMin. */
    val rewardMinutes: Int? = null,
)
