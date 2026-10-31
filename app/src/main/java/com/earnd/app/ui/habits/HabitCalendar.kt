package com.earnd.app.ui.habits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Month grid and day counting for the habit detail screen. Pure, so it is unit-tested. */
object HabitCalendar {
    /** Cells for a month view, weeks starting on [weekStart]; null pads the first and last week. */
    fun monthCells(month: YearMonth, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells += null }
        for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** Scheduled days on which the habit was complete. */
    fun perfectDays(days: List<LocalDate>, scheduled: (LocalDate) -> Boolean, complete: (LocalDate) -> Boolean): Int =
        days.count { scheduled(it) && complete(it) }
}
