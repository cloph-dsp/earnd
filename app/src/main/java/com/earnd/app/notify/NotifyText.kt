package com.earnd.app.notify

/** One scheduled habit for today as seen by the nudge and the report. */
data class HabitLine(val name: String, val done: Int, val target: Int) {
    val complete get() = done >= target
}

data class NotifyMessage(val title: String, val body: String)

/** Pure text builders, kept free of Android so they can be unit-tested. */
object NotifyText {
    /** Small rotating set of short original lines; picked by day so it changes daily. */
    val MORNING_LINES = listOf(
        "Small steps first, screen time after.",
        "Start with the easiest one.",
        "Today's minutes are waiting to be earned.",
        "One check-in and you're on your way.",
        "Do it now, enjoy it later.",
        "A fresh day, a fresh bank.",
        "Future you says thanks in advance.",
    )

    fun morningLine(epochDay: Long): String =
        MORNING_LINES[Math.floorMod(epochDay, MORNING_LINES.size.toLong()).toInt()]

    /** Null when there is nothing to nudge about. */
    fun morning(epochDay: Long, habits: List<HabitLine>): NotifyMessage? {
        val open = habits.filterNot { it.complete }
        if (open.isEmpty()) return null
        val title = if (open.size == 1) "1 habit today" else "${open.size} habits today"
        return NotifyMessage(title, morningLine(epochDay) + "\n" + listNames(open))
    }

    /** Null when nothing was scheduled and no time was earned or used. */
    fun report(habits: List<HabitLine>, leisureMinutesUsed: Int, earnedMinutes: Int): NotifyMessage? {
        if (habits.isEmpty() && leisureMinutesUsed <= 0 && earnedMinutes <= 0) return null
        val done = habits.filter { it.complete }
        val missed = habits.filterNot { it.complete }
        val title = when {
            habits.isEmpty() -> "Today's report"
            missed.isEmpty() -> "All done today: ${done.size}/${habits.size}"
            else -> "Today: ${done.size}/${habits.size} habits done"
        }
        val lines = buildList {
            if (done.isNotEmpty()) add("Done: " + listNames(done))
            if (missed.isNotEmpty()) add("Missed: " + listNames(missed))
            add("Leisure used: ${minutes(leisureMinutesUsed)} · earned: ${minutes(earnedMinutes)}")
        }
        return NotifyMessage(title, lines.joinToString("\n"))
    }

    fun minutes(m: Int): String {
        val v = m.coerceAtLeast(0)
        return if (v >= 60) "${v / 60} h ${v % 60} min" else "$v min"
    }

    private fun listNames(l: List<HabitLine>, max: Int = 5): String {
        val shown = l.take(max).joinToString(", ") { it.name }
        return if (l.size > max) "$shown +${l.size - max}" else shown
    }
}
