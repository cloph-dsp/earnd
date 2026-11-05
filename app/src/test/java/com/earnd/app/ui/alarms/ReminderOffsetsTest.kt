package com.earnd.app.ui.alarms

import com.earnd.app.i18n.TestStr
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReminderOffsetsTest {
    @Before fun english() = TestStr.install()

    @Test fun labels() {
        assertEquals("At due time", ReminderOffsets.label(0))
        assertEquals("15 min before", ReminderOffsets.label(15))
        assertEquals("1 h before", ReminderOffsets.label(60))
        assertEquals("1 h 30 min before", ReminderOffsets.label(90))
        assertEquals("1 day before", ReminderOffsets.label(1440))
        assertEquals("2 days before", ReminderOffsets.label(2880))
        assertEquals("2 days 1 h before", ReminderOffsets.label(2940))
        assertEquals("1 week before", ReminderOffsets.label(10080))
    }

    @Test fun csvOps() {
        assertEquals(listOf(60, 15), ReminderOffsets.parse(" 15,60,x,0,-5,15"))
        assertEquals("1440,60,15", ReminderOffsets.add("60,15", 1440))
        assertEquals("60,15", ReminderOffsets.add("60,15", 60))
        assertEquals("15", ReminderOffsets.remove("60,15", 60))
        assertEquals("", ReminderOffsets.remove("60", 60))
    }

    @Test fun split() {
        assertEquals(2 to ReminderOffsets.Unit.DAYS, ReminderOffsets.split(2880))
        assertEquals(3 to ReminderOffsets.Unit.HOURS, ReminderOffsets.split(180))
        assertEquals(90 to ReminderOffsets.Unit.MINUTES, ReminderOffsets.split(90))
    }
}
