package com.earnd.app.alarms

import com.earnd.app.FakeClock
import com.earnd.app.data.Settings
import com.earnd.app.domain.DailyWindow
import com.earnd.app.notify.NotifyTimes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayStartTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val clock = FakeClock(0, lisbon)
    private val s = Settings() // weekdays 08:00, weekends 10:00, quiet 00:00-08:00
    private val fri = LocalDate.of(2026, 3, 27)
    private val sat = LocalDate.of(2026, 3, 28)
    private val sun = LocalDate.of(2026, 3, 29) // spring forward in Lisbon
    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(lisbon).toLocalDateTime().toString()

    @Test fun weekdaysAreNotShifted() {
        assertEquals(0, DayStart.shiftMin(s, fri))
        assertEquals(9 * 60, DayStart.habitMinute(s, fri, 9 * 60))
    }

    @Test fun weekendShiftsByDifference() {
        assertEquals(120, DayStart.shiftMin(s, sat))
        assertEquals(11 * 60, DayStart.habitMinute(s, sat, 9 * 60))
        assertEquals(10 * 60, DayStart.quietEndMin(s, sun))
        assertEquals(DailyWindow(0, 10 * 60), DayStart.quietWindow(s, sat))
        assertEquals(DailyWindow(0, 8 * 60), DayStart.quietWindow(s, fri))
    }

    @Test fun spacingIsPreserved() {
        val a = DayStart.habitMinute(s, sat, 8 * 60)
        val b = DayStart.habitMinute(s, sat, 8 * 60 + 45)
        assertEquals(45, b - a)
    }

    @Test fun noShiftWhenDisabledOrNotLater() {
        assertEquals(0, DayStart.shiftMin(s.copy(laterWeekendStart = false), sat))
        assertEquals(0, DayStart.shiftMin(s.copy(dayStartWeekendMin = 7 * 60), sat))
        assertEquals(0, DayStart.shiftMin(s.copy(dayStartWeekendMin = 8 * 60), sun))
    }

    @Test fun clampsAtEndOfDay() {
        assertEquals(1439, DayStart.habitMinute(s, sat, 23 * 60 + 30))
    }

    @Test fun dstWeekendKeepsLocalWallClock() {
        // Sunday 29 March 2026 is 23 hours long; a 09:00 reminder shifts to 11:00 local, not 12:00.
        val at = AlarmTimes.at(sun, DayStart.habitMinute(s, sun, 9 * 60), clock)
        assertEquals("2026-03-29T11:00", local(at))
        val satAt = AlarmTimes.at(sat, DayStart.habitMinute(s, sat, 9 * 60), clock)
        assertEquals(23 * 3_600_000L, at - satAt)
    }

    @Test fun nagLeavesWeekendQuietLater() {
        val quietFor: (LocalDate) -> DailyWindow = { DayStart.quietWindow(s, it) }
        val satAt = AlarmTimes.at(sat, 9 * 60, clock) // inside the weekend quiet window, outside the weekday one
        assertEquals("2026-03-28T10:00", local(NagSchedule.shiftOutOfQuiet(satAt, lisbon, quietFor)))
        val friAt = AlarmTimes.at(fri, 7 * 60, clock)
        assertEquals("2026-03-27T08:00", local(NagSchedule.shiftOutOfQuiet(friAt, lisbon, quietFor)))
        // Friday night into Saturday morning ends at the weekend end.
        val overnight: (LocalDate) -> DailyWindow = { DailyWindow(23 * 60, DayStart.quietEndMin(s, it)) }
        val lateFri = AlarmTimes.at(fri, 23 * 60 + 30, clock)
        assertEquals("2026-03-28T10:00", local(NagSchedule.shiftOutOfQuiet(lateFri, lisbon, overnight)))
    }

    @Test fun morningNudgeUsesWeekendStart() {
        val now = AlarmTimes.at(fri, 12 * 60, clock)
        val next = NotifyTimes.nextAt(now, lisbon) { DayStart.morningNudgeMin(s, it) }
        assertEquals("2026-03-28T10:00", local(next))
        val monday = NotifyTimes.nextAt(AlarmTimes.at(sun, 12 * 60, clock), lisbon) { DayStart.morningNudgeMin(s, it) }
        assertEquals("2026-03-30T08:00", local(monday))
    }
}
