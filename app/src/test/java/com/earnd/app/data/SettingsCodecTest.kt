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
}
