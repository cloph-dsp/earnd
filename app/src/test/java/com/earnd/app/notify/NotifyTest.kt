package com.earnd.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class NotifyTest {

    private fun ms(t: LocalDateTime, zone: ZoneId = ZoneOffset.UTC) = t.atZone(zone).toInstant().toEpochMilli()

    @Test fun nextAtLaterToday() {
        val now = ms(LocalDateTime.of(2026, 3, 10, 7, 0))
        assertEquals(ms(LocalDateTime.of(2026, 3, 10, 8, 0)), NotifyTimes.nextAt(now, 8 * 60, ZoneOffset.UTC))
    }

    @Test fun nextAtRollsToTomorrowWhenPassedOrEqual() {
        val now = ms(LocalDateTime.of(2026, 3, 10, 8, 0))
        assertEquals(ms(LocalDateTime.of(2026, 3, 11, 8, 0)), NotifyTimes.nextAt(now, 8 * 60, ZoneOffset.UTC))
    }

    @Test fun nextAtAcrossDstGap() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        // 2026-03-29 01:00 -> 02:00 in Lisbon; 01:30 does not exist and resolves to 02:30.
        val now = ms(LocalDateTime.of(2026, 3, 29, 0, 30), lisbon)
        val at = NotifyTimes.nextAt(now, 90, lisbon)
        assertEquals(ms(LocalDateTime.of(2026, 3, 29, 2, 30), lisbon), at)
    }

    @Test fun morningListsOpenHabitsAndRotates() {
        val lines = listOf(HabitLine("Read", 0, 1), HabitLine("Walk", 1, 1), HabitLine("Water", 2, 8))
        val m = NotifyText.morning(100, lines)!!
        assertEquals("2 habits today", m.title)
        assertTrue(m.body.endsWith("Read, Water"))
        assertNotEquals(NotifyText.morningLine(100), NotifyText.morningLine(101))
        assertEquals(NotifyText.morningLine(-1), NotifyText.MORNING_LINES.last())
        assertNull(NotifyText.morning(100, listOf(HabitLine("Walk", 1, 1))))
    }

    @Test fun reportSummarizesDoneMissedAndMinutes() {
        val r = NotifyText.report(listOf(HabitLine("Read", 1, 1), HabitLine("Walk", 0, 1)), 75, 20)!!
        assertEquals("Today: 1/2 habits done", r.title)
        assertTrue(r.body.contains("Done: Read"))
        assertTrue(r.body.contains("Missed: Walk"))
        assertTrue(r.body.contains("1 h 15 min"))
        assertEquals("All done today: 1/1", NotifyText.report(listOf(HabitLine("Read", 1, 1)), 0, 5)!!.title)
        assertNull(NotifyText.report(emptyList(), 0, 0))
    }
}
