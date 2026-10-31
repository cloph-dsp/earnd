package com.earnd.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.earnd.app.domain.DateTimeFormats

/**
 * Compose-observable mirror of [DateTimeFormats.current]: reading it during composition
 * recomposes when the user changes the date/time format in Settings.
 */
object UiFormats {
    var value: DateTimeFormats by mutableStateOf(DateTimeFormats.current)
        private set

    fun apply(f: DateTimeFormats) {
        DateTimeFormats.current = f
        if (value != f) value = f
    }
}

fun formatMinutes(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

/** Minute of day in the user's time format (24h "07:30" or 12h "7:30 AM"). */
fun formatMinuteOfDay(min: Int): String = UiFormats.value.formatTime(min)

/** Parses a typed time ("HH:MM", or "h:mm am/pm") into minute of day, or null. */
fun parseMinuteOfDay(text: String): Int? = UiFormats.value.parseTime(text)

/** Weekday plus date in the user's date format, e.g. "Sat 31/10/2026". */
fun formatDay(epochDay: Long): String = UiFormats.value.formatDayWithWeekday(epochDay)

/** Date in the user's date format (used for typed date fields). */
fun formatDate(epochDay: Long): String = UiFormats.value.formatDate(epochDay)

/** Parses a typed date in the user's format (ISO also accepted), or null. */
fun parseDate(text: String): Long? = UiFormats.value.parseDate(text)?.toEpochDay()

fun formatDateTime(epochMs: Long): String =
    UiFormats.value.formatDateTime(epochMs, com.earnd.app.ServiceLocator.clock.zone())

fun dateHint(): String = UiFormats.value.dateHint
fun timeHint(): String = UiFormats.value.timeHint

fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
