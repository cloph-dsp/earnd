package com.earnd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun offDayRoundTripsAndExpiresByDate() {
        val s = Settings(offDayEpochDay = 20_000L)
        assertEquals(20_000L, SettingsCodec.decode(SettingsCodec.encode(s)).offDayEpochDay)
        assertTrue(s.onOffDay(20_000L))
        assertFalse(s.onOffDay(20_001L))
        assertNull(SettingsCodec.decode(SettingsCodec.encode(Settings())).offDayEpochDay)
    }

    @Test
    fun offDayWeekdaysRoundTripAndParse() {
        val days = setOf(1, 3, 7)
        val encoded = SettingsCodec.encode(Settings(offDayWeekdays = days))
        assertEquals("1,3,7", encoded["offDayWeekdays"])
        assertEquals(days, SettingsCodec.decode(encoded).offDayWeekdays)
        assertEquals(emptySet<Int>(), SettingsCodec.decode(SettingsCodec.encode(Settings())).offDayWeekdays)
        assertEquals(setOf(3), SettingsCodec.decode(mapOf("offDayWeekdays" to "3,x")).offDayWeekdays)
    }

    @Test
    fun onOffDayHonorsWeekdays() {
        val day = 20_000L
        val weekday = java.time.LocalDate.ofEpochDay(day).dayOfWeek.value
        val s = Settings(offDayWeekdays = setOf(weekday))
        assertTrue(s.onOffDay(day))
        assertFalse(s.onOffDay(day + 1))
    }

    @Test
    fun onOffDayCombinesSingleDayAndWeekdays() {
        val weekdayDay = 20_000L
        val weekday = java.time.LocalDate.ofEpochDay(weekdayDay).dayOfWeek.value
        val s = Settings(offDayEpochDay = 20_001L, offDayWeekdays = setOf(weekday))
        assertTrue(s.onOffDay(weekdayDay))
        assertTrue(s.onOffDay(20_001L))
        assertFalse(s.onOffDay(20_002L))
    }
}
