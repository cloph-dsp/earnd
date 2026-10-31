package com.earnd.app.domain

import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import java.time.LocalDate

/** Pure schedule and streak rules for habits. */
object HabitSchedule {
    private const val DAY_MS = 86_400_000L

    /** Start date for EVERY_N_DAYS; an unset (<= 0) anchor falls back to the creation day. */
    fun anchorOf(h: HabitEntity): Long =
        if (h.anchorEpochDay > 0) h.anchorEpochDay else Math.floorDiv(h.createdAt, DAY_MS)

    fun isScheduled(h: HabitEntity, day: LocalDate): Boolean {
        if (h.archived) return false
        return when (h.scheduleKind) {
            ScheduleKind.DAILY -> true
            ScheduleKind.WEEKDAYS -> h.weekdayMask and (1 shl (day.dayOfWeek.value - 1)) != 0
            ScheduleKind.EVERY_N_DAYS -> {
                val diff = day.toEpochDay() - anchorOf(h)
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
            if (day.toEpochDay() < anchorOf(h)) return streak
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

    /** Whether reminders fire on [day]: a scheduled day that is also in `reminderDays` (0 = all). */
    fun remindsOn(h: HabitEntity, day: LocalDate): Boolean =
        isScheduled(h, day) && (h.reminderDays and 0x7F == 0 || h.reminderDays and (1 shl (day.dayOfWeek.value - 1)) != 0)

    /**
     * Longest run of completed scheduled days in [from]..[to]. Unscheduled and [skip] days are
     * ignored; an unfinished [to] (today) does not break the run.
     */
    fun bestStreak(
        h: HabitEntity,
        from: LocalDate,
        to: LocalDate,
        countsByDay: Map<Long, Int>,
        skip: (LocalDate) -> Boolean = { false },
    ): Int {
        var best = 0
        var cur = 0
        var day = maxOf(from, LocalDate.ofEpochDay(anchorOf(h)))
        while (!day.isAfter(to)) {
            if (isScheduled(h, day) && !skip(day)) {
                if ((countsByDay[day.toEpochDay()] ?: 0) >= h.timesPerDay.coerceAtLeast(1)) {
                    cur++
                    best = maxOf(best, cur)
                } else if (day != to) cur = 0
            }
            day = day.plusDays(1)
        }
        return best
    }
}
