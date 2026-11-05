package com.earnd.app.domain

import com.earnd.app.R
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.i18n.Str
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
            ScheduleKind.RULE -> RRule.parseOrNull(h.scheduleRule)?.occursOn(LocalDate.ofEpochDay(anchorOf(h)), day) ?: true
            // Flexible: any day counts; [isDueToday] hides it once the week's quota is met.
            ScheduleKind.TIMES_PER_WEEK -> true
        }
    }

    /** Human-readable schedule, e.g. "Every 2 weeks on Mon, Thu" or "3 times a week". */
    fun summary(h: HabitEntity): String = when (h.scheduleKind) {
        ScheduleKind.DAILY -> Str.get(R.string.rrule_every_day)
        ScheduleKind.WEEKDAYS -> RRule.weekly(RRule.maskToDays(h.weekdayMask)).summary()
        ScheduleKind.EVERY_N_DAYS ->
            if (h.everyNDays <= 1) Str.get(R.string.rrule_every_day) else Str.get(R.string.rrule_every_days, h.everyNDays)
        ScheduleKind.RULE -> RRule.parseOrNull(h.scheduleRule)?.summary(start = LocalDate.ofEpochDay(anchorOf(h))) ?: Str.get(R.string.rrule_every_day)
        ScheduleKind.TIMES_PER_WEEK -> Str.plural(R.plurals.rrule_times_a_week, h.timesPerWeek.coerceAtLeast(1))
    }

    /** Monday of [day]'s week. */
    fun weekStart(day: LocalDate): LocalDate = day.with(java.time.DayOfWeek.MONDAY)

    /**
     * Whether the habit still needs doing on [day], given how many days of that week (before
     * [day]) are already complete. Only TIMES_PER_WEEK depends on the week.
     */
    fun isDueOn(h: HabitEntity, day: LocalDate, completeDaysThisWeekBefore: Int): Boolean =
        isScheduled(h, day) && (h.scheduleKind != ScheduleKind.TIMES_PER_WEEK ||
            completeDaysThisWeekBefore < h.timesPerWeek.coerceAtLeast(1))

    private fun weekMet(h: HabitEntity, day: LocalDate, countsByDay: Map<Long, Int>): Boolean {
        val mon = weekStart(day)
        val done = (0L..6L).count { (countsByDay[mon.plusDays(it).toEpochDay()] ?: 0) >= h.timesPerDay.coerceAtLeast(1) }
        return done >= h.timesPerWeek.coerceAtLeast(1)
    }

    /** A missed scheduled day breaks a streak, except flexible weeks that are open or met. */
    private fun missBreaks(h: HabitEntity, day: LocalDate, current: LocalDate, countsByDay: Map<Long, Int>): Boolean =
        h.scheduleKind != ScheduleKind.TIMES_PER_WEEK ||
            (weekStart(day) != weekStart(current) && !weekMet(h, day, countsByDay))

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
                    day != today && missBreaks(h, day, today, countsByDay) -> return streak
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
                } else if (day != to && missBreaks(h, day, to, countsByDay)) cur = 0
            }
            day = day.plusDays(1)
        }
        return best
    }
}
