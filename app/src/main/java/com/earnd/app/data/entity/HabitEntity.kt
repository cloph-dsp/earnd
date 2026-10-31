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
    /** RRULE for ScheduleKind.RULE (e.g. every 2 weeks on Mon, monthly on the 1st and last day). */
    val scheduleRule: String? = null,
    /** Target days per week for ScheduleKind.TIMES_PER_WEEK (any days). */
    @ColumnInfo(defaultValue = "0") val timesPerWeek: Int = 0,
    /** Timed focus habit: check-in is a focus session of this many minutes; null = plain check-in. */
    val focusMinutes: Int? = null,
    /** Extra focus durations "min:reward,min:reward" (reward optional = linear from the per-minute rate). */
    val focusOptions: String? = null,
    /** Open-ended (count-up) focus: reward = floor(minutes x [stopwatchRate]). Takes precedence over focus. */
    @ColumnInfo(defaultValue = "0") val stopwatch: Boolean = false,
    /** Earned minutes per focused minute; null = 0.5. */
    val stopwatchRate: Double? = null,
    /** Shortest session that counts; null = 5 min, 0 = none. */
    val stopwatchMinMin: Int? = null,
    /** Focused minutes counted at most per session; null = no cap. */
    val stopwatchCapMin: Int? = null,
)
