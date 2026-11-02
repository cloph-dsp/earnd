package com.earnd.app.alarms

import com.earnd.app.FakeClock
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.RRule
import com.earnd.app.domain.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Recurring occurrences keep their local time across DST changes in Europe/Lisbon. */
class DstTimesTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val clock = FakeClock(0, lisbon)
    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(lisbon).toLocalDateTime().toString()

    @Test fun dailyAcrossSpringForwardAndFallBack() {
        val t = TaskEntity(title = "pill", dueEpochDay = LocalDate.of(2026, 3, 28).toEpochDay(), dueMinuteOfDay = 9 * 60,
            repeatRule = RRule(RRule.Freq.DAILY).toString(), createdAt = 0)
        val next = Recurrence.following(t, LocalDate.of(2026, 3, 28))!!
        assertEquals(LocalDate.of(2026, 3, 29), next)
        val before = AlarmTimes.at(LocalDate.of(2026, 3, 28), 9 * 60, clock)
        val after = AlarmTimes.at(next, 9 * 60, clock)
        assertEquals("2026-03-29T09:00", local(after))
        assertEquals(23 * 3_600_000L, after - before)
        val oct = AlarmTimes.at(LocalDate.of(2026, 10, 25), 9 * 60, clock)
        assertEquals("2026-10-25T09:00", local(oct))
        assertEquals(25 * 3_600_000L, oct - AlarmTimes.at(LocalDate.of(2026, 10, 24), 9 * 60, clock))
    }

    @Test fun lastSundayOfMarchRuleAndGapTime() {
        assertEquals(LocalDate.of(2027, 3, 28),
            RRule.parse("FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU").next(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29)))
        // 01:30 does not exist on 2026-03-29 in Lisbon; it lands at 02:30.
        assertEquals("2026-03-29T02:30", local(AlarmTimes.at(LocalDate.of(2026, 3, 29), 90, clock)))
    }
}

/** Habit reminder times each become their own due occurrence per day. */
class HabitReminderTimesTest {
    private val clock = FakeClock(0, ZoneId.of("Europe/Lisbon"))
    // 08:00,14:00,21:00 as stored by the remote mapping: latest + minutes-before offsets.
    private val cfg = com.earnd.app.data.entity.AlarmConfigEntity(
        com.earnd.app.data.entity.OwnerKind.HABIT, 1, habitReminderMinuteOfDay = 21 * 60, offsetsBeforeMin = "780,420")

    @Test fun expandsEveryReminderTime() {
        assertEquals(listOf(8 * 60, 14 * 60, 21 * 60), AlarmTimes.habitMinutes(cfg))
    }

    @Test fun instantsAreAscendingLocalTimes() {
        val day = LocalDate.of(2026, 5, 4)
        val at = AlarmTimes.habitInstants(cfg, day, clock)
        assertEquals(listOf(AlarmTimes.at(day, 480, clock), AlarmTimes.at(day, 840, clock), AlarmTimes.at(day, 1260, clock)), at)
    }

    @Test fun noReminderMeansNoOccurrences() {
        assertEquals(emptyList<Int>(), AlarmTimes.habitMinutes(cfg.copy(habitReminderMinuteOfDay = null)))
    }

    @Test fun offsetsBeforeMidnightAreDropped() {
        assertEquals(listOf(60), AlarmTimes.habitMinutes(cfg.copy(habitReminderMinuteOfDay = 60, offsetsBeforeMin = "120")))
    }
}
