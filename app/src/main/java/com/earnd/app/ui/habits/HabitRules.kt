package com.earnd.app.ui.habits

import com.earnd.app.R
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.HabitProgress
import com.earnd.app.i18n.Str

/** UI-side reading of a habit's overrides against the global settings. */
object HabitRules {
    /** Same rule as HabitService.photoRequired, usable without a service instance. */
    fun needsPhoto(h: HabitEntity, s: Settings): Boolean =
        h.checkInMode == CheckInMode.PHOTO || HabitProgress.photoRequired(h, s)

    fun rewardMinutes(h: HabitEntity, s: Settings): Int = HabitProgress.rewardMinutes(h, s)

    fun quantityDone(checkIns: List<CheckInEntity>): Double = HabitProgress.quantity(checkIns)

    fun dayComplete(h: HabitEntity, dayCheckIns: List<CheckInEntity>): Boolean = HabitProgress.isComplete(h, dayCheckIns)

    /** Short schedule text for lists, e.g. "Daily", "Mon Wed Fri", "Every 3 days", "3x / week". */
    fun scheduleSummary(h: HabitEntity): String {
        val base = when (h.scheduleKind) {
            com.earnd.app.data.entity.ScheduleKind.DAILY -> Str.get(R.string.rrule_daily)
            com.earnd.app.data.entity.ScheduleKind.WEEKDAYS -> {
                val mask = h.weekdayMask and 0b1111111
                when (mask) {
                    0b1111111 -> Str.get(R.string.rrule_daily)
                    0b0011111 -> Str.get(R.string.rrule_weekdays)
                    0b1100000 -> Str.get(R.string.habits_sched_weekends)
                    else -> java.time.DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }
                        .joinToString(" ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
                }
            }
            com.earnd.app.data.entity.ScheduleKind.EVERY_N_DAYS ->
                if (h.everyNDays <= 1) Str.get(R.string.rrule_daily) else Str.get(R.string.rrule_every_days, h.everyNDays)
            com.earnd.app.data.entity.ScheduleKind.TIMES_PER_WEEK -> Str.get(R.string.habits_sched_x_week, h.timesPerWeek)
            com.earnd.app.data.entity.ScheduleKind.RULE ->
                com.earnd.app.domain.RRule.parseOrNull(h.scheduleRule)?.summary(start = java.time.LocalDate.ofEpochDay(h.anchorEpochDay))
                    ?: Str.get(R.string.habits_sched_custom)
        }
        return if (h.timesPerDay > 1) Str.get(R.string.habits_sched_times_a_day, base, h.timesPerDay) else base
    }

    /** Parses a user-typed amount ("2", "1,5", "0.25"); null when not a positive number. */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    fun formatAmount(q: Double): String =
        if (q % 1.0 == 0.0) q.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, q).trimEnd('0').trimEnd('.')

    /** Moves the item at [from] to [to], clamping [to] to the list bounds. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices) return list
        val target = to.coerceIn(0, list.lastIndex)
        if (from == target) return list
        return list.toMutableList().apply { add(target, removeAt(from)) }
    }
}
