package com.earnd.app.domain

import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Comeback bonus: the first earning check-in after N missed scheduled occasions earns an extra
 * base x min(1, 0.25 x N). Anti-gaming by construction: the bonus never exceeds the base, and
 * skipping k >= 1 occasions forfeits at least k x base, so skipping then returning always earns
 * strictly less than never skipping.
 *
 * An "active" day is one where the habit was complete or a check-in earned minutes. A lapse is
 * the run of scheduled, non-skipped days (TIMES_PER_WEEK: whole weeks) before today with no
 * active day; any active day ends it, so each lapse pays once.
 */
object Comeback {
    const val LOOKBACK_DAYS = 120L
    private const val LOOKBACK_WEEKS = 16

    fun fraction(missed: Int): Double = if (missed <= 0) 0.0 else minOf(1.0, 0.25 * missed)

    fun bonus(base: Int, missed: Int): Int = if (base <= 0) 0 else (base * fraction(missed)).roundToInt()

    /** "×1.5" for 2 misses. */
    fun label(missed: Int): String {
        val m = 1.0 + fraction(missed)
        return "×" + if (m % 1.0 == 0.0) m.toInt().toString() else m.toString().trimEnd('0')
    }

    /** Missed occasions since the last active day; 0 when today (this week, for flexible habits) is already active. */
    fun missedOccasions(h: HabitEntity, today: LocalDate, activeDays: Set<Long>, skip: (LocalDate) -> Boolean = { false }): Int {
        val anchor = HabitSchedule.anchorOf(h)
        if (h.scheduleKind == ScheduleKind.TIMES_PER_WEEK) {
            val mon = HabitSchedule.weekStart(today)
            if ((mon.toEpochDay()..today.toEpochDay()).any { it in activeDays }) return 0
            var n = 0
            var w = mon.minusWeeks(1)
            repeat(LOOKBACK_WEEKS) {
                // Reached the day the habit began without ever having been active: nothing to come back from.
                if (h.archived) return n
                if (w.plusDays(6).toEpochDay() < anchor) return 0
                if ((0L..6L).any { w.plusDays(it).toEpochDay() in activeDays }) return n
                if ((0L..6L).none { skip(w.plusDays(it)) }) n++
                w = w.minusWeeks(1)
            }
            return n
        }
        if (today.toEpochDay() in activeDays) return 0
        var n = 0
        var d = today.minusDays(1)
        repeat(LOOKBACK_DAYS.toInt()) {
            if (d.toEpochDay() < anchor) return 0 // never active since it began: a first check-in is not a comeback
            if (d.toEpochDay() in activeDays) return n
            if (HabitSchedule.isScheduled(h, d) && !skip(d)) n++
            d = d.minusDays(1)
        }
        return n
    }
}
