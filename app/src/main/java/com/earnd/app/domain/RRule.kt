package com.earnd.app.domain

import com.earnd.app.R
import com.earnd.app.i18n.Str
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A recurrence rule: the subset of RFC 5545 RRULE that Earnd supports, as a value type with a
 * lossless canonical string form (`FREQ=MONTHLY;INTERVAL=2;BYDAY=-1FR;COUNT=10`).
 *
 * Dates only (no times): the series start ("dtstart") is the first due day; times of day are kept
 * separately as local minute-of-day, so DST never shifts an occurrence.
 *
 * Deviation from RFC 5545: a positive BYMONTHDAY past the month's end (e.g. 31 in February)
 * clamps to the last day instead of being skipped, which is what people expect from "monthly on
 * the 31st". The same applies to Feb 29 in yearly rules.
 */
data class RRule(
    val freq: Freq,
    val interval: Int = 1,
    /** Weekdays, optionally with an ordinal (1..5 = Nth, -1..-5 = Nth from the end, 0 = every). */
    val byDay: List<WeekdayNum> = emptyList(),
    /** Days of month, 1..31 or -1..-31 (-1 = last day). */
    val byMonthDay: List<Int> = emptyList(),
    /** Months 1..12. */
    val byMonth: List<Int> = emptyList(),
    /** Total number of occurrences in the series (including the first). */
    val count: Int? = null,
    /** Last allowed day (inclusive). */
    val until: LocalDate? = null,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    data class WeekdayNum(val ordinal: Int, val day: DayOfWeek) {
        override fun toString() = (if (ordinal != 0) "$ordinal" else "") + CODES[day.value - 1]
    }

    init {
        require(interval in 1..999) { "INTERVAL must be 1..999" }
        require(byDay.all { it.ordinal in -5..5 }) { "BYDAY ordinal must be -5..5" }
        require(byMonthDay.all { it in -31..31 && it != 0 }) { "BYMONTHDAY must be 1..31 or -31..-1" }
        require(byMonth.all { it in 1..12 }) { "BYMONTH must be 1..12" }
        require(count == null || count >= 1) { "COUNT must be >= 1" }
    }

    override fun toString(): String = buildList {
        add("FREQ=$freq")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(","))
        if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + byMonthDay.joinToString(","))
        if (byMonth.isNotEmpty()) add("BYMONTH=" + byMonth.joinToString(","))
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=" + it.toString().replace("-", "")) }
    }.joinToString(";")

    /** Weekly rule without explicit days repeats on the start's weekday. */
    private fun weekDays(start: LocalDate): Set<DayOfWeek> =
        byDay.map { it.day }.toSet().ifEmpty { setOf(start.dayOfWeek) }

    /** Days matched inside one month by BYMONTHDAY / BYDAY, falling back to the start's day. */
    private fun daysInMonth(ym: YearMonth, start: LocalDate): List<LocalDate> {
        val len = ym.lengthOfMonth()
        return when {
            byMonthDay.isNotEmpty() -> byMonthDay.mapNotNull { d ->
                when {
                    d > 0 -> ym.atDay(minOf(d, len))
                    -d <= len -> ym.atDay(len + d + 1)
                    else -> null
                }
            }
            byDay.isNotEmpty() -> byDay.flatMap { wd -> nthWeekdays(ym, wd) }
            else -> listOf(ym.atDay(minOf(start.dayOfMonth, len)))
        }
    }

    private fun nthWeekdays(ym: YearMonth, wd: WeekdayNum): List<LocalDate> {
        val all = generateSequence(ym.atDay(1).with(TemporalAdjusters.nextOrSame(wd.day))) { it.plusWeeks(1) }
            .takeWhile { YearMonth.from(it) == ym }.toList()
        return when {
            wd.ordinal == 0 -> all
            wd.ordinal > 0 -> listOfNotNull(all.getOrNull(wd.ordinal - 1))
            else -> listOfNotNull(all.getOrNull(all.size + wd.ordinal))
        }
    }

    private fun filters(d: LocalDate): Boolean =
        (byMonth.isEmpty() || d.monthValue in byMonth) &&
            (freq != Freq.DAILY || byDay.isEmpty() || byDay.any { it.day == d.dayOfWeek }) &&
            (freq != Freq.DAILY || byMonthDay.isEmpty() || d.dayOfMonth in byMonthDay ||
                byMonthDay.any { it < 0 && d.dayOfMonth == d.lengthOfMonth() + it + 1 })

    /** Candidate days of period [k] (k = number of intervals since the start's period), sorted. */
    private fun period(start: LocalDate, k: Long): List<LocalDate> {
        val step = k * interval
        val raw = when (freq) {
            Freq.DAILY -> listOf(start.plusDays(step))
            Freq.WEEKLY -> {
                val monday = start.with(DayOfWeek.MONDAY).plusWeeks(step)
                weekDays(start).map { monday.plusDays(it.value - 1L) }
            }
            Freq.MONTHLY -> daysInMonth(YearMonth.from(start).plusMonths(step), start)
            Freq.YEARLY -> {
                val year = start.year + step
                val months = byMonth.ifEmpty { listOf(start.monthValue) }
                months.flatMap { m ->
                    val ym = YearMonth.of(year.toInt(), m)
                    if (byMonthDay.isEmpty() && byDay.isEmpty()) listOf(ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth())))
                    else daysInMonth(ym, start)
                }
            }
        }
        return raw.filter(::filters).distinct().sorted()
    }

    private fun periodsBetween(start: LocalDate, day: LocalDate): Long = when (freq) {
        Freq.DAILY -> ChronoUnit.DAYS.between(start, day)
        Freq.WEEKLY -> ChronoUnit.WEEKS.between(start.with(DayOfWeek.MONDAY), day.with(DayOfWeek.MONDAY))
        Freq.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(day))
        Freq.YEARLY -> (day.year - start.year).toLong()
    } / interval

    /**
     * First occurrence strictly after [after] (and not before [start]), ignoring COUNT but
     * honouring UNTIL. Null when the series has ended or nothing matches within a long horizon.
     */
    fun next(start: LocalDate, after: LocalDate): LocalDate? {
        var k = (periodsBetween(start, maxOf(start, after)) - 1).coerceAtLeast(0)
        val limit = k + MAX_EMPTY_PERIODS
        while (k <= limit) {
            for (d in period(start, k)) {
                if (until != null && d.isAfter(until)) return null
                if (d.isAfter(after) && !d.isBefore(start)) return d
            }
            k++
        }
        return null
    }

    /** Whether [day] is an occurrence of the series starting at [start] (COUNT ignored). */
    fun occursOn(start: LocalDate, day: LocalDate): Boolean =
        !day.isBefore(start) && next(start, day.minusDays(1)) == day

    /** All occurrences from [start], honouring COUNT and UNTIL. The first is the start itself. */
    fun occurrences(start: LocalDate): Sequence<LocalDate> =
        generateSequence(start) { next(start, it) }.let { s -> count?.let { s.take(it) } ?: s }

    /** Due date after completing on [completedOn], for "repeat after completion" series. */
    fun afterCompletion(completedOn: LocalDate): LocalDate? = when (freq) {
        Freq.DAILY -> completedOn.plusDays(interval.toLong())
        Freq.WEEKLY -> completedOn.plusWeeks(interval.toLong())
        Freq.MONTHLY -> completedOn.plusMonths(interval.toLong())
        Freq.YEARLY -> completedOn.plusYears(interval.toLong())
    }.takeIf { until == null || !it.isAfter(until) }

    /** Human-readable summary, e.g. "Every 2 weeks on Sun" or "3 days after completion, 10 times". */
    fun summary(fromCompletion: Boolean = false, start: LocalDate? = null): String {
        val base = if (fromCompletion) {
            Str.plural(
                when (freq) {
                    Freq.DAILY -> R.plurals.rrule_after_completion_days
                    Freq.WEEKLY -> R.plurals.rrule_after_completion_weeks
                    Freq.MONTHLY -> R.plurals.rrule_after_completion_months
                    Freq.YEARLY -> R.plurals.rrule_after_completion_years
                },
                interval,
            )
        } else when (freq) {
            Freq.DAILY -> {
                val head = if (interval == 1) Str.get(R.string.rrule_daily) else Str.get(R.string.rrule_every_days, interval)
                if (byDay.isNotEmpty()) Str.get(R.string.rrule_with_days, head, dayList()) else head
            }
            Freq.WEEKLY -> when {
                interval == 1 && byDay.map { it.day }.toSet() == WORKDAYS -> Str.get(R.string.rrule_weekdays)
                else -> {
                    val days = if (byDay.isEmpty()) start?.dayOfWeek?.let(::dayName) ?: Str.get(R.string.rrule_same_day) else dayList()
                    if (interval == 1) Str.get(R.string.rrule_weekly_on, days) else Str.get(R.string.rrule_every_weeks_on, interval, days)
                }
            }
            Freq.MONTHLY ->
                if (interval == 1) Str.get(R.string.rrule_monthly_on, monthPart(start))
                else Str.get(R.string.rrule_every_months_on, interval, monthPart(start))
            Freq.YEARLY ->
                if (interval == 1) Str.get(R.string.rrule_yearly_on, yearPart(start))
                else Str.get(R.string.rrule_every_years_on, interval, yearPart(start))
        }
        return when {
            count == 1 -> Str.get(R.string.rrule_with_once, base)
            count != null -> Str.get(R.string.rrule_with_times, base, count)
            until != null -> Str.get(R.string.rrule_with_until, base, until.toString())
            else -> base
        }
    }

    private fun dayList() = byDay.sortedBy { it.day.value }.joinToString(", ") { dayName(it.day) }

    private fun monthPart(start: LocalDate?): String = when {
        byMonthDay.isNotEmpty() -> Str.get(R.string.rrule_the, byMonthDay.joinToString(", ") { dayOfMonthName(it) })
        byDay.isNotEmpty() -> Str.get(R.string.rrule_the, byDay.joinToString(", ") { wd ->
            if (wd.ordinal == 0) Str.get(R.string.rrule_every_weekday, fullDayName(wd.day))
            else Str.get(R.string.rrule_nth_weekday, ordinalName(wd.ordinal, wd.day), fullDayName(wd.day))
        })
        start != null -> Str.get(R.string.rrule_the, dayOfMonthName(start.dayOfMonth))
        else -> Str.get(R.string.rrule_same_day)
    }

    private fun yearPart(start: LocalDate?): String {
        val months = byMonth.ifEmpty { listOfNotNull(start?.monthValue) }
        val monthNames = months.joinToString(", ") { monthName(it) }
        return when {
            byDay.isNotEmpty() -> Str.get(R.string.rrule_of_months, monthPart(start), monthNames)
            byMonthDay.isNotEmpty() ->
                Str.get(R.string.rrule_month_days, monthNames, byMonthDay.joinToString(", ") { if (it > 0) "$it" else dayOfMonthName(it) })
            start != null -> Str.get(R.string.rrule_month_days, monthNames, "${start.dayOfMonth}")
            else -> Str.get(R.string.rrule_same_date)
        }
    }

    companion object {
        private const val MAX_EMPTY_PERIODS = 2000L
        private val CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        fun weekly(days: Collection<DayOfWeek>, interval: Int = 1) =
            RRule(Freq.WEEKLY, interval, days.sortedBy { it.value }.map { WeekdayNum(0, it) })

        fun weekdays() = weekly(WORKDAYS)

        /** Weekday mask (bit0 = Monday) to days and back. */
        fun maskToDays(mask: Int): List<DayOfWeek> = DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }
        fun daysToMask(days: Collection<DayOfWeek>): Int = days.fold(0) { m, d -> m or (1 shl (d.value - 1)) }

        fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        fun fullDayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.getDefault())

        /** Short month name (1..12) in the current language. */
        fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())

        /** Ordinal in the masculine form ("1st", "last", "second to last"). */
        fun ordinalName(n: Int): String = ordinal(n, feminine = false)

        /** Ordinal agreeing with the weekday's grammatical gender (relevant in languages that inflect it). */
        fun ordinalName(n: Int, day: DayOfWeek): String = ordinal(n, feminine = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY)

        private fun ordinal(n: Int, feminine: Boolean): String = when (n) {
            -1 -> Str.get(if (feminine) R.string.rrule_ord_f_last else R.string.rrule_ord_m_last)
            -2 -> Str.get(if (feminine) R.string.rrule_ord_f_last2 else R.string.rrule_ord_m_last2)
            1 -> Str.get(if (feminine) R.string.rrule_ord_f_1 else R.string.rrule_ord_m_1)
            2 -> Str.get(if (feminine) R.string.rrule_ord_f_2 else R.string.rrule_ord_m_2)
            3 -> Str.get(if (feminine) R.string.rrule_ord_f_3 else R.string.rrule_ord_m_3)
            else ->
                if (n > 0) Str.get(if (feminine) R.string.rrule_ord_f_n else R.string.rrule_ord_m_n, n)
                else Str.get(if (feminine) R.string.rrule_ord_f_last_n else R.string.rrule_ord_m_last_n, -n)
        }

        fun dayOfMonthName(d: Int): String = when {
            d == -1 -> Str.get(R.string.rrule_dom_last)
            d == -2 -> Str.get(R.string.rrule_dom_last2)
            d < 0 -> Str.get(R.string.rrule_dom_last_n, -d)
            d == 1 -> Str.get(R.string.rrule_dom_st, d)
            d == 2 -> Str.get(R.string.rrule_dom_nd, d)
            d == 3 -> Str.get(R.string.rrule_dom_rd, d)
            else -> Str.get(R.string.rrule_dom_th, d)
        }

        /** Parses a rule (optionally prefixed with "RRULE:"); throws [IllegalArgumentException]. */
        fun parse(text: String): RRule {
            val body = text.trim().removePrefix("RRULE:").removePrefix("rrule:")
            val parts = body.split(';').filter { it.isNotBlank() }.associate { p ->
                val i = p.indexOf('=')
                require(i > 0) { "bad rule part '$p'" }
                p.substring(0, i).trim().uppercase() to p.substring(i + 1).trim()
            }
            val freq = parts["FREQ"]?.uppercase()?.let { f -> Freq.values().firstOrNull { it.name == f } }
                ?: throw IllegalArgumentException("FREQ must be DAILY, WEEKLY, MONTHLY or YEARLY")
            fun ints(key: String) = parts[key]?.split(',')?.filter { it.isNotBlank() }?.map {
                it.trim().toIntOrNull() ?: throw IllegalArgumentException("$key must be numbers")
            }.orEmpty()
            var byDay = parts["BYDAY"]?.split(',')?.filter { it.isNotBlank() }?.map { parseDay(it.trim()) }.orEmpty()
            val setPos = ints("BYSETPOS")
            if (setPos.isNotEmpty()) {
                // "BYDAY=TU;BYSETPOS=2" is the same as "BYDAY=2TU" when one weekday is given.
                require(setPos.size == 1 && byDay.size == 1 && byDay[0].ordinal == 0) { "BYSETPOS is only supported with a single BYDAY" }
                byDay = listOf(byDay[0].copy(ordinal = setPos[0]))
            }
            val until = parts["UNTIL"]?.let { u ->
                val digits = u.take(8)
                runCatching { LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt()) }
                    .getOrNull() ?: runCatching { LocalDate.parse(u.take(10)) }.getOrNull()
                    ?: throw IllegalArgumentException("UNTIL must be YYYYMMDD")
            }
            return RRule(
                freq = freq,
                interval = ints("INTERVAL").firstOrNull() ?: 1,
                byDay = byDay,
                byMonthDay = ints("BYMONTHDAY"),
                byMonth = ints("BYMONTH"),
                count = ints("COUNT").firstOrNull(),
                until = until,
            )
        }

        fun parseOrNull(text: String?): RRule? = text?.takeIf { it.isNotBlank() }?.let { runCatching { parse(it) }.getOrNull() }

        private fun parseDay(s: String): WeekdayNum {
            val code = s.takeLast(2).uppercase()
            val idx = CODES.indexOf(code)
            require(idx >= 0) { "bad BYDAY '$s'" }
            val num = s.dropLast(2).removePrefix("+")
            val ord = if (num.isEmpty()) 0 else num.toIntOrNull() ?: throw IllegalArgumentException("bad BYDAY '$s'")
            return WeekdayNum(ord, DayOfWeek.of(idx + 1))
        }

        /** Converts the pre-RRULE columns (schema v3) to a rule string; null when not repeating. */
        fun fromLegacy(kind: String?, weekdayMask: Int, interval: Int): String? {
            val n = interval.coerceIn(1, 999)
            return when (kind) {
                "DAILY" -> RRule(Freq.DAILY)
                "EVERY_N_DAYS" -> RRule(Freq.DAILY, n)
                "WEEKLY" -> RRule(Freq.WEEKLY, n, maskToDays(weekdayMask).map { WeekdayNum(0, it) })
                "MONTHLY" -> RRule(Freq.MONTHLY, n)
                "YEARLY" -> RRule(Freq.YEARLY)
                else -> null
            }?.toString()
        }
    }
}
