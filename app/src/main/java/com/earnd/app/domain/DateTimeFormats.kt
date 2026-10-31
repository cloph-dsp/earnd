package com.earnd.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.ResolverStyle
import java.util.Locale

enum class DateFormatPref { DMY, YMD, MDY, SYSTEM }
enum class TimeFormatPref { H24, H12, SYSTEM }

/**
 * The single place that turns dates and times into user-facing text.
 * [current] mirrors Settings.dateFormat/timeFormat (set by the app on every settings change);
 * [systemIs24] mirrors the phone's 24-hour setting for [TimeFormatPref.SYSTEM].
 * Storage and remote JSON stay ISO and never go through here.
 */
data class DateTimeFormats(
    val date: DateFormatPref = DateFormatPref.DMY,
    val time: TimeFormatPref = TimeFormatPref.H24,
    val systemIs24: Boolean = true,
    val locale: Locale = Locale.getDefault(),
) {
    val is24: Boolean get() = when (time) { TimeFormatPref.H24 -> true; TimeFormatPref.H12 -> false; TimeFormatPref.SYSTEM -> systemIs24 }

    /** Numeric date pattern used for display and for typed date input. */
    val datePattern: String get() = when (date) {
        DateFormatPref.DMY -> "dd/MM/uuuu"
        DateFormatPref.YMD -> "uuuu-MM-dd"
        DateFormatPref.MDY -> "MM/dd/uuuu"
        DateFormatPref.SYSTEM -> null
    } ?: systemPattern()

    /** Hint shown in date fields, e.g. "DD/MM/YYYY". */
    val dateHint: String get() = datePattern.uppercase(Locale.ROOT).replace("UUUU", "YYYY")

    val timeHint: String get() = if (is24) "HH:MM" else "h:mm AM"

    private fun systemPattern(): String = runCatching {
        java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, locale,
        ).replace("yyyy", "uuuu").replace("yy", "uuuu").replace(Regex("(?<!M)M(?!M)"), "MM").replace(Regex("(?<!d)d(?!d)"), "dd")
    }.getOrDefault("dd/MM/uuuu")

    private fun dateFormatter() = DateTimeFormatter.ofPattern(datePattern, locale).withResolverStyle(ResolverStyle.STRICT)

    fun formatDate(day: LocalDate): String = day.format(dateFormatter())
    fun formatDate(epochDay: Long): String = formatDate(LocalDate.ofEpochDay(epochDay))

    /** Weekday plus date, e.g. "Sat 31/10/2026". */
    fun formatDayWithWeekday(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return d.format(DateTimeFormatter.ofPattern("EEE", locale)) + " " + formatDate(d)
    }

    fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim(), dateFormatter()) }.getOrNull()
            ?: runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    fun formatTime(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        if (is24) return "%02d:%02d".format(h, m)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }

    fun formatTime(epochMs: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        return formatTime(t.hour * 60 + t.minute)
    }

    fun formatDateTime(epochMs: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        return formatDate(t.toLocalDate()) + " " + formatTime(t.hour * 60 + t.minute)
    }

    /** Accepts "HH:MM" always, plus "h:mm am/pm" / "h am" (12-hour) forms. Null if invalid. */
    fun parseTime(text: String): Int? {
        val t = text.trim().lowercase(Locale.ROOT)
        val m = Regex("""^(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m?\.?$""").matchEntire(t)
        if (m != null) {
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h !in 1..12 || min !in 0..59) return null
            val h24 = (h % 12) + if (m.groupValues[3] == "p") 12 else 0
            return h24 * 60 + min
        }
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val min = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    companion object {
        /** Process-wide current formats; alarms/widgets/notifications can read this directly. */
        @Volatile var current: DateTimeFormats = DateTimeFormats()
    }
}
