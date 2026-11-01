package com.earnd.app.alarms

import com.earnd.app.data.Settings
import com.earnd.app.domain.DailyWindow
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure weekend shift. On Saturday and Sunday, habit reminder times, nag schedules and the quiet-hours
 * end move later by (weekend day start - weekday day start) when that is positive, so spacing between
 * reminders is preserved. Tasks with explicit due times are never shifted.
 */
object DayStart {
    fun isWeekend(day: LocalDate) = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    /** Minutes to add on [day]; 0 on weekdays, when disabled, or when the weekend start is not later. */
    fun shiftMin(s: Settings, day: LocalDate): Int =
        if (s.laterWeekendStart && isWeekend(day)) (s.dayStartWeekendMin - s.dayStartWeekdayMin).coerceAtLeast(0) else 0

    /** A habit reminder minute-of-day on [day], shifted on weekends and clamped to 23:59. */
    fun habitMinute(s: Settings, day: LocalDate, minuteOfDay: Int): Int =
        (minuteOfDay + shiftMin(s, day)).coerceAtMost(24 * 60 - 1)

    /** Quiet end on [day]: follows the day start on weekends. */
    fun quietEndMin(s: Settings, day: LocalDate): Int = (s.quietEndMin + shiftMin(s, day)).coerceAtMost(24 * 60 - 1)

    fun quietWindow(s: Settings, day: LocalDate) = DailyWindow(s.quietStartMin, quietEndMin(s, day))

    /** Morning nudge minute on [day]: the weekend start on weekends when later starts are on. */
    fun morningNudgeMin(s: Settings, day: LocalDate, configured: Int = s.morningNudgeMin): Int =
        (configured + shiftMin(s, day)).coerceAtMost(24 * 60 - 1)
}
