package com.earnd.app.ui.alarms

import androidx.annotation.StringRes
import com.earnd.app.R
import com.earnd.app.i18n.Str

/** Pure helpers for "remind N before due" offsets (stored as minutes, CSV in AlarmConfigEntity). */
object ReminderOffsets {
    enum class Unit(val minutes: Int, @StringRes private val labelRes: Int) {
        MINUTES(1, R.string.alarmsui_unit_min), HOURS(60, R.string.alarmsui_unit_h), DAYS(1440, R.string.alarmsui_unit_days);

        /** Short unit name in the current language ("min", "h", "days"). */
        val label: String get() = Str.get(labelRes)
    }

    val PRESETS = listOf(5, 10, 15, 30, 60, 120, 1440, 2880, 10080)
    const val MAX_MIN = 60 * 24 * 60 // 60 days

    fun parse(csv: String): List<Int> =
        csv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..MAX_MIN }.distinct().sortedDescending()

    fun toCsv(list: List<Int>): String = list.filter { it in 1..MAX_MIN }.distinct().sortedDescending().joinToString(",")

    fun add(csv: String, min: Int): String = toCsv(parse(csv) + min)
    fun remove(csv: String, min: Int): String = toCsv(parse(csv) - min)

    /** Short duration: 90 -> "1 h 30 min", 1440 -> "1 day", 10080 -> "1 week", 2940 -> "2 days 1 h". */
    fun duration(min: Int): String {
        if (min <= 0) return Str.get(R.string.alarmsui_dur_zero)
        if (min % 10080 == 0) return Str.plural(R.plurals.alarmsui_dur_weeks, min / 10080)
        val d = min / 1440; val h = (min % 1440) / 60; val m = min % 60
        return buildList {
            if (d > 0) add(Str.plural(R.plurals.alarmsui_dur_days, d))
            if (h > 0) add(Str.get(R.string.alarmsui_dur_h, h))
            if (m > 0) add(Str.get(R.string.alarmsui_dur_min, m))
        }.joinToString(" ")
    }

    fun label(min: Int): String =
        if (min <= 0) Str.get(R.string.alarmsui_at_due_time) else Str.get(R.string.alarmsui_offset_before, duration(min))

    /** Largest unit that divides [min] exactly, for prefilling the picker. */
    fun split(min: Int): Pair<Int, Unit> = when {
        min > 0 && min % 1440 == 0 -> min / 1440 to Unit.DAYS
        min > 0 && min % 60 == 0 -> min / 60 to Unit.HOURS
        else -> min to Unit.MINUTES
    }

}
