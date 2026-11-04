package com.earnd.app.data.entity

enum class DaySegment { NONE, MORNING, AFTERNOON, EVENING }
/** RULE uses HabitEntity.scheduleRule; TIMES_PER_WEEK is flexible (any X days per week). */
enum class ScheduleKind { DAILY, WEEKDAYS, EVERY_N_DAYS, RULE, TIMES_PER_WEEK }
enum class CheckInMode { TAP, PHOTO }
enum class Priority { NONE, LOW, MEDIUM, HIGH }
enum class AlarmStyle { SILENT, NOTIFICATION, FULL_SCREEN }
enum class NagMode { OFF, PROGRESSIVE, FIXED }
enum class OwnerKind { TASK, HABIT }
enum class LedgerKind { EARN_HABIT, EARN_TASK, UNDO, SPEND, RESET, ADJUST, COMEBACK, METER }
/** ROLLING: every earned entry expires [Settings.rollingExpiryHours] after it was earned. */
enum class ResetPeriod { DAILY, WEEKLY, ROLLING }

/** User-chosen app/website rule. Essential apps are hardcoded and never stored. */
enum class AppRule { LEISURE, ALLOWED }

/** How habit check-ins pay out: per check-in, or one lump sum when everything scheduled today is done. */
enum class RewardMode { PER_CHECKIN, ALL_DONE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Challenge shown before removing a blocked app/site or loosening blocking. */
enum class PuzzleKind { NONE, ARITHMETIC, SENTENCE }

/** Row height of habit lists. */
enum class ListDensity { COMFORTABLE, COMPACT }
