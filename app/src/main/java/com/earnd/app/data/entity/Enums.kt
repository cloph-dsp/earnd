package com.earnd.app.data.entity

enum class DaySegment { NONE, MORNING, AFTERNOON, EVENING }
enum class ScheduleKind { DAILY, WEEKDAYS, EVERY_N_DAYS }
enum class CheckInMode { TAP, PHOTO }
enum class Priority { NONE, LOW, MEDIUM, HIGH }
enum class RepeatKind { NONE, DAILY, WEEKLY, MONTHLY, EVERY_N_DAYS }
enum class AlarmStyle { SILENT, NOTIFICATION, FULL_SCREEN }
enum class NagMode { OFF, PROGRESSIVE, FIXED }
enum class OwnerKind { TASK, HABIT }
enum class LedgerKind { EARN_HABIT, EARN_TASK, UNDO, SPEND, RESET, ADJUST }
enum class ResetPeriod { DAILY, WEEKLY }

/** User-chosen app/website rule. Essential apps are hardcoded and never stored. */
enum class AppRule { LEISURE, ALLOWED }
