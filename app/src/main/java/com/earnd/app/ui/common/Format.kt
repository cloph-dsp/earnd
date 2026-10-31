package com.earnd.app.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

fun formatMinutes(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

fun formatMinuteOfDay(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** Parses "H:MM" / "HH:MM" into minute of day, or null. */
fun parseMinuteOfDay(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormat)

fun formatIsoDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

fun parseIsoDay(text: String): Long? = runCatching { LocalDate.parse(text.trim()).toEpochDay() }.getOrNull()

fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
