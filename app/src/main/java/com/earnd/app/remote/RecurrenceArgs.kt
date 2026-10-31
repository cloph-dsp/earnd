package com.earnd.app.remote

import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.RRule
import com.earnd.app.domain.RRule.Freq
import com.earnd.app.domain.RRule.WeekdayNum
import java.time.DayOfWeek
import java.time.LocalDate

/** Remote argument parsing for recurrence (task `repeat=` and habit schedules). */
internal object RecurrenceArgs {
    /**
     * Builds a task rule from `repeat` (simple name or RRULE string), `days`, `interval`,
     * `monthday`, `nth`, `count` and `until`. Keys left out keep the [base] task's rule.
     * Returns null for "does not repeat".
     */
    fun rule(a: RemoteArgs, base: TaskEntity, start: LocalDate): RRule? = guard {
        val v = a.str("repeat")?.trim()
        var r: RRule? = when {
            v == null -> RRule.parseOrNull(base.repeatRule)
                ?: if (listOf("days", "interval", "monthday", "nth", "count", "until").any(a::has)) bad("repeat= is required") else null
            v.isEmpty() || v.equals("none", true) || v.equals("once", true) -> return@guard null
            v.contains("FREQ=", ignoreCase = true) -> RRule.parse(v)
            else -> simple(v.uppercase(), a, start)
        }
        if (r != null && v != null && !v.contains("FREQ=", true)) return@guard ends(a, r)
        if (r != null && v == null) {
            a.int("interval")?.let { r = r!!.copy(interval = it) }
            a.weekdayMask("days")?.let { m -> r = r!!.copy(byDay = RRule.maskToDays(m).map { WeekdayNum(0, it) }) }
            monthDays(a.str("monthday"))?.let { r = r!!.copy(byMonthDay = it, byDay = emptyList()) }
            nth(a.str("nth"))?.let { r = r!!.copy(byDay = listOf(it), byMonthDay = emptyList()) }
        }
        r?.let { ends(a, it) }
    }

    private fun simple(kind: String, a: RemoteArgs, start: LocalDate): RRule {
        val n = a.int("interval") ?: 1
        return when (kind) {
            "DAILY" -> RRule(Freq.DAILY, n)
            "EVERY_N_DAYS" -> RRule(Freq.DAILY, a.int("interval") ?: bad("EVERY_N_DAYS needs interval"))
            "WEEKDAYS" -> RRule.weekdays()
            "WEEKLY" -> RRule.weekly(a.weekdayMask("days")?.let(RRule::maskToDays)?.ifEmpty { null } ?: listOf(start.dayOfWeek), n)
            "MONTHLY" -> RRule(Freq.MONTHLY, n, byDay = listOfNotNull(nth(a.str("nth"))),
                byMonthDay = monthDays(a.str("monthday")).orEmpty())
            "YEARLY" -> nth(a.str("nth"))?.let { RRule(Freq.YEARLY, n, byDay = listOf(it), byMonth = listOf(start.monthValue)) }
                ?: RRule(Freq.YEARLY, n)
            else -> bad("repeat must be NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS or an RRULE (FREQ=...)")
        }
    }

    private fun ends(a: RemoteArgs, r: RRule): RRule {
        var out = r
        a.str("count")?.let { c -> out = out.copy(count = if (c.equals("none", true)) null else c.toIntOrNull() ?: bad("count must be a number"), until = null) }
        a.str("until")?.let { u ->
            out = out.copy(until = if (u.equals("none", true)) null else runCatching { LocalDate.parse(u) }.getOrNull() ?: bad("until must be YYYY-MM-DD"),
                count = if (u.equals("none", true)) out.count else null)
        }
        return out.copy()
    }

    /** "15", "1,15,-1", "last". */
    fun monthDays(s: String?): List<Int>? = s?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.map {
        if (it == "last") -1 else it.toIntOrNull() ?: bad("monthday must be numbers or last")
    }

    private val DAY_PREFIX = mapOf("mo" to DayOfWeek.MONDAY, "tu" to DayOfWeek.TUESDAY, "we" to DayOfWeek.WEDNESDAY,
        "th" to DayOfWeek.THURSDAY, "fr" to DayOfWeek.FRIDAY, "sa" to DayOfWeek.SATURDAY, "su" to DayOfWeek.SUNDAY)

    /** "2TU", "-1FR", "2tue", "last fri", "1st mon". */
    fun nth(s: String?): WeekdayNum? {
        val v = s?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val m = Regex("""^(last|[+-]?\d)(?:st|nd|rd|th)?\s*([a-z]{2})[a-z]*$""").find(v) ?: bad("nth must look like 2TU, -1FR or 'last fri'")
        val ord = if (m.groupValues[1] == "last") -1 else m.groupValues[1].toInt()
        val day = DAY_PREFIX[m.groupValues[2]] ?: bad("nth: unknown weekday")
        if (ord == 0 || ord !in -5..5) bad("nth ordinal must be 1..5 or -1..-5")
        return WeekdayNum(ord, day)
    }

    /** Habit schedule rule from `rule=`, `weeks=` (+ `days=`) or `monthdays=`; null if none given. */
    fun habitRule(a: RemoteArgs, start: LocalDate): RRule? = guard {
        when {
            a.has("rule") -> RRule.parse(a.str("rule").orEmpty())
            a.has("weeks") -> RRule.weekly(
                a.weekdayMask("days")?.let(RRule::maskToDays)?.ifEmpty { null } ?: listOf(start.dayOfWeek),
                a.int("weeks") ?: 1,
            )
            a.has("monthdays") -> RRule(Freq.MONTHLY, byMonthDay = monthDays(a.str("monthdays")).orEmpty().ifEmpty { bad("monthdays is empty") })
            else -> null
        }
    }

    private inline fun <T> guard(block: () -> T): T = try { block() } catch (e: IllegalArgumentException) {
        if (e is BadArgs) throw e
        bad(e.message ?: "bad repeat rule")
    }
}
