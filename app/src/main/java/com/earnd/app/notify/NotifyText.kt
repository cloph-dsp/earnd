package com.earnd.app.notify

import com.earnd.app.R
import com.earnd.app.i18n.Str

/** One scheduled habit for today as seen by the nudge and the report. */
data class HabitLine(val name: String, val done: Int, val target: Int) {
    val complete get() = done >= target
}

data class NotifyMessage(val title: String, val body: String)

/** Pure text builders, kept free of Android so they can be unit-tested. */
object NotifyText {
    private val MORNING_LINE_IDS = intArrayOf(
        R.string.notify_morning_line_1,
        R.string.notify_morning_line_2,
        R.string.notify_morning_line_3,
        R.string.notify_morning_line_4,
        R.string.notify_morning_line_5,
        R.string.notify_morning_line_6,
        R.string.notify_morning_line_7,
    )

    /** Small rotating set of short original lines; picked by day so it changes daily. */
    val MORNING_LINES: List<String> get() = MORNING_LINE_IDS.map { Str.get(it) }

    fun morningLine(epochDay: Long): String =
        Str.get(MORNING_LINE_IDS[Math.floorMod(epochDay, MORNING_LINE_IDS.size.toLong()).toInt()])

    /** Null when there is nothing to nudge about. */
    fun morning(epochDay: Long, habits: List<HabitLine>): NotifyMessage? {
        val open = habits.filterNot { it.complete }
        if (open.isEmpty()) return null
        val title = Str.plural(R.plurals.notify_habits_today, open.size)
        return NotifyMessage(title, morningLine(epochDay) + "\n" + listNames(open))
    }

    /** Null when nothing was scheduled and no time was earned or used. */
    fun report(habits: List<HabitLine>, leisureMinutesUsed: Int, earnedMinutes: Int): NotifyMessage? {
        if (habits.isEmpty() && leisureMinutesUsed <= 0 && earnedMinutes <= 0) return null
        val done = habits.filter { it.complete }
        val missed = habits.filterNot { it.complete }
        val title = when {
            habits.isEmpty() -> Str.get(R.string.notify_report_title_empty)
            missed.isEmpty() -> Str.get(R.string.notify_report_title_all, done.size, habits.size)
            else -> Str.plural(R.plurals.notify_report_title_partial, habits.size, done.size)
        }
        val lines = buildList {
            if (done.isNotEmpty()) add(Str.get(R.string.notify_report_done, listNames(done)))
            if (missed.isNotEmpty()) add(Str.get(R.string.notify_report_missed, listNames(missed)))
            add(Str.get(R.string.notify_report_leisure, minutes(leisureMinutesUsed), minutes(earnedMinutes)))
        }
        return NotifyMessage(title, lines.joinToString("\n"))
    }

    fun minutes(m: Int): String {
        val v = m.coerceAtLeast(0)
        return if (v >= 60) Str.get(R.string.notify_hours_minutes, v / 60, v % 60) else Str.get(R.string.notify_minutes, v)
    }

    private fun listNames(l: List<HabitLine>, max: Int = 5): String {
        val shown = l.take(max).joinToString(", ") { it.name }
        return if (l.size > max) "$shown +${l.size - max}" else shown
    }
}
