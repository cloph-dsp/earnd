package com.earnd.app.domain

import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.TaskEntity
import java.time.LocalDate

/** Pure recurrence rules for tasks. The due date (or [afterDay] if none) is the anchor. */
object Recurrence {
    /** First valid occurrence strictly after [afterDay], or null when the task does not repeat. */
    fun next(t: TaskEntity, afterDay: LocalDate): LocalDate? {
        val anchor = t.dueEpochDay?.let(LocalDate::ofEpochDay) ?: afterDay
        val interval = t.repeatInterval.coerceAtLeast(1)
        return when (t.repeatKind) {
            RepeatKind.NONE -> null
            RepeatKind.DAILY -> afterDay.plusDays(1)
            RepeatKind.EVERY_N_DAYS -> {
                val diff = afterDay.toEpochDay() - anchor.toEpochDay()
                if (diff < 0) anchor else anchor.plusDays((diff / interval + 1) * interval)
            }
            RepeatKind.WEEKLY -> {
                // Every [interval] weeks, counted from the Monday of the series' first due week.
                val start = t.repeatAnchorEpochDay?.let(LocalDate::ofEpochDay) ?: anchor
                val mask = if (t.repeatWeekdayMask and 0x7F != 0) t.repeatWeekdayMask
                else 1 shl (start.dayOfWeek.value - 1)
                val week0 = start.minusDays(start.dayOfWeek.value - 1L).toEpochDay()
                val from = if (afterDay.isBefore(start)) start.minusDays(1) else afterDay
                (1L..(7L * interval + 7)).map { from.plusDays(it) }.first {
                    val weeks = Math.floorDiv(it.toEpochDay() - week0, 7L)
                    weeks >= 0 && weeks % interval == 0L && mask and (1 shl (it.dayOfWeek.value - 1)) != 0
                }
            }
            RepeatKind.YEARLY -> {
                // Step from the original anchor so Feb 29 returns in leap years (Feb 28 otherwise).
                val start = t.repeatAnchorEpochDay?.let(LocalDate::ofEpochDay) ?: anchor
                var k = (afterDay.year - start.year - 1L).coerceAtLeast(0)
                var d = start.plusYears(k)
                while (!d.isAfter(afterDay)) d = start.plusYears(++k)
                d
            }
            RepeatKind.MONTHLY -> {
                // Step from the anchor so a 31st clamps per month without drifting.
                // Skip whole intervals up front so an old anchor does not loop month by month.
                val months = java.time.temporal.ChronoUnit.MONTHS.between(anchor, afterDay).coerceAtLeast(0)
                var k = (months / interval - 1).coerceAtLeast(0) * interval
                var d = anchor.plusMonths(k)
                while (!d.isAfter(afterDay)) {
                    k += interval
                    d = anchor.plusMonths(k)
                }
                d
            }
        }
    }
}
