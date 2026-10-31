package com.earnd.app.domain

import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import java.time.LocalDate

/** Pure schedule and streak rules for habits. */
object HabitSchedule {
    fun isScheduled(h: HabitEntity, day: LocalDate): Boolean {
        if (h.archived) return false
        return when (h.scheduleKind) {
            ScheduleKind.DAILY -> true
            ScheduleKind.WEEKDAYS -> h.weekdayMask and (1 shl (day.dayOfWeek.value - 1)) != 0
            ScheduleKind.EVERY_N_DAYS -> {
                val diff = day.toEpochDay() - h.anchorEpochDay
                diff >= 0 && diff % h.everyNDays.coerceAtLeast(1) == 0L
            }
        }
    }

    /**
     * Consecutive scheduled days completed, counting back from [today]. Today only adds to the
     * streak (an unfinished today does not break it). Unscheduled and [skip] days are ignored.
     */
    fun streak(
        h: HabitEntity,
        today: LocalDate,
        countsByDay: Map<Long, Int>,
        skip: (LocalDate) -> Boolean = { false },
        maxDays: Int = 366,
    ): Int {
        var streak = 0
        var day = today
        repeat(maxDays) {
            if (day.toEpochDay() < h.anchorEpochDay) return streak
            if (isScheduled(h, day) && !skip(day)) {
                val done = (countsByDay[day.toEpochDay()] ?: 0) >= h.timesPerDay
                when {
                    done -> streak++
                    day != today -> return streak
                }
            }
            day = day.minusDays(1)
        }
        return streak
    }
}
