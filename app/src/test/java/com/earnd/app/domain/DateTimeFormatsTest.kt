package com.earnd.app.domain

import com.earnd.app.data.SettingsCodec
import com.earnd.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DateTimeFormatsTest {
    private val day = LocalDate.of(2026, 3, 7)

    @Test fun defaultsAreEuropean() {
        val f = DateTimeFormats(locale = Locale.ENGLISH)
        assertEquals("07/03/2026", f.formatDate(day))
        assertEquals("19:05", f.formatTime(19 * 60 + 5))
        assertEquals(DateFormatPref.DMY, Settings().dateFormat)
        assertEquals(TimeFormatPref.H24, Settings().timeFormat)
    }

    @Test fun otherDateFormats() {
        assertEquals("2026-03-07", DateTimeFormats(DateFormatPref.YMD).formatDate(day))
        assertEquals("03/07/2026", DateTimeFormats(DateFormatPref.MDY).formatDate(day))
        assertEquals(day, DateTimeFormats(DateFormatPref.MDY).parseDate("03/07/2026"))
        assertEquals(day, DateTimeFormats().parseDate("07/03/2026"))
        assertEquals(day, DateTimeFormats().parseDate("2026-03-07"))
        assertNull(DateTimeFormats().parseDate("31/02/2026"))
    }

    @Test fun twelveHour() {
        val f = DateTimeFormats(time = TimeFormatPref.H12)
        assertEquals("12:00 AM", f.formatTime(0))
        assertEquals("7:05 PM", f.formatTime(19 * 60 + 5))
        assertEquals(19 * 60 + 5, f.parseTime("7:05 pm"))
        assertEquals(12 * 60, f.parseTime("12 PM"))
        assertEquals(19 * 60 + 5, f.parseTime("19:05"))
        assertNull(f.parseTime("13:00 pm"))
        assertEquals("19:05", DateTimeFormats(time = TimeFormatPref.SYSTEM, systemIs24 = true).formatTime(19 * 60 + 5))
    }

    @Test fun settingsRoundTrip() {
        val s = Settings(dateFormat = DateFormatPref.YMD, timeFormat = TimeFormatPref.SYSTEM)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }
}
