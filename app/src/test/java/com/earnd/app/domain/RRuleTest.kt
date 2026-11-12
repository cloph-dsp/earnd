package com.earnd.app.domain

import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.i18n.TestStr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class RRuleTest {
    @Before fun english() = TestStr.install()

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun r(s: String) = RRule.parse(s)
    private fun first(rule: String, start: LocalDate, n: Int) = r(rule).occurrences(start).take(n).toList()

    @Test fun roundTripIsLossless() {
        listOf(
            "FREQ=DAILY", "FREQ=DAILY;INTERVAL=3", "FREQ=WEEKLY;INTERVAL=2;BYDAY=SU",
            "FREQ=MONTHLY;BYDAY=-1FR", "FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=1,15,-1;COUNT=10",
            "FREQ=YEARLY;BYDAY=2TU;BYMONTH=5", "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20271231",
        ).forEach { assertEquals(it, r(it).toString()); assertEquals(r(it), r(r(it).toString())) }
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", r("RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=2;WKST=MO").toString())
        assertEquals(d(2027, 1, 5), r("FREQ=DAILY;UNTIL=20270105T235959Z").until)
        assertEquals(1, r("freq=weekly;byday=+1mo").byDay.single().ordinal)
    }

    @Test fun rejectsUnsupported() {
        listOf("FREQ=HOURLY", "BYDAY=MO", "FREQ=DAILY;INTERVAL=0", "FREQ=MONTHLY;BYMONTHDAY=32", "FREQ=WEEKLY;BYDAY=XX", "FREQ=DAILY;COUNT=0")
            .forEach { assertNull(it, RRule.parseOrNull(it)) }
    }

    @Test fun dailyAndWeekdays() {
        assertEquals(listOf(d(2026, 3, 1), d(2026, 3, 4), d(2026, 3, 7)), first("FREQ=DAILY;INTERVAL=3", d(2026, 3, 1), 3))
        // Fri 6 Mar -> Mon 9 Mar.
        assertEquals(d(2026, 3, 9), RRule.weekdays().next(d(2026, 3, 2), d(2026, 3, 6)))
        assertEquals(d(2026, 3, 9), r("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR").next(d(2026, 3, 2), d(2026, 3, 6)))
    }

    @Test fun everyTwoWeeksAnchoredOnStartWeek() {
        val rule = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,SU"
        assertEquals(listOf(d(2026, 3, 2), d(2026, 3, 8), d(2026, 3, 16), d(2026, 3, 22)), first(rule, d(2026, 3, 2), 4))
        // Far ahead the parity holds: 2026-03-02 + 52 weeks = 2027-03-01, an on-week.
        assertEquals(d(2027, 3, 1), r(rule).next(d(2026, 3, 2), d(2027, 2, 27)))
        assertFalse(r(rule).occursOn(d(2026, 3, 2), d(2026, 3, 9)))
    }

    @Test fun monthEndClampsAndLastDay() {
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)), first("FREQ=MONTHLY", d(2026, 1, 31), 4))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31)), first("FREQ=MONTHLY;BYMONTHDAY=31", d(2026, 1, 31), 3))
        assertEquals(listOf(d(2028, 1, 31), d(2028, 2, 29), d(2028, 3, 31)), first("FREQ=MONTHLY;BYMONTHDAY=-1", d(2028, 1, 31), 3))
        assertEquals(listOf(d(2026, 3, 1), d(2026, 3, 15), d(2026, 3, 31), d(2026, 4, 1)), first("FREQ=MONTHLY;BYMONTHDAY=1,15,-1", d(2026, 3, 1), 4))
    }

    @Test fun ordinalWeekdays() {
        assertEquals(listOf(d(2026, 3, 10), d(2026, 4, 14), d(2026, 5, 12)), first("FREQ=MONTHLY;BYDAY=2TU", d(2026, 3, 10), 3))
        assertEquals(listOf(d(2026, 3, 27), d(2026, 4, 24), d(2026, 5, 29)), first("FREQ=MONTHLY;BYDAY=-1FR", d(2026, 3, 27), 3))
        // A 5th Monday only exists in some months (Mar 30, Jun 29 2026).
        assertEquals(d(2026, 6, 29), r("FREQ=MONTHLY;BYDAY=5MO").next(d(2026, 3, 30), d(2026, 3, 30)))
        assertEquals(listOf(d(2026, 1, 5), d(2026, 4, 6), d(2026, 7, 6)), first("FREQ=MONTHLY;INTERVAL=3;BYDAY=1MO", d(2026, 1, 5), 3))
    }

    @Test fun yearlyAndLeapYears() {
        assertEquals(listOf(d(2028, 2, 29), d(2029, 2, 28), d(2030, 2, 28), d(2031, 2, 28), d(2032, 2, 29)),
            first("FREQ=YEARLY", d(2028, 2, 29), 5))
        assertEquals(listOf(d(2026, 7, 15), d(2028, 7, 15)), first("FREQ=YEARLY;INTERVAL=2", d(2026, 7, 15), 2))
        assertEquals(listOf(d(2026, 11, 26), d(2027, 11, 25)), first("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH", d(2026, 11, 26), 2))
        assertEquals(d(2027, 2, 28), r("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29").next(d(2024, 2, 29), d(2026, 3, 1)))
    }

    @Test fun countAndUntil() {
        assertEquals(3, r("FREQ=DAILY;COUNT=3").occurrences(d(2026, 3, 1)).count())
        assertEquals(listOf(d(2026, 3, 1), d(2026, 3, 8)), r("FREQ=WEEKLY;UNTIL=20260314").occurrences(d(2026, 3, 1)).toList())
        assertNull(r("FREQ=WEEKLY;UNTIL=20260314").next(d(2026, 3, 1), d(2026, 3, 8)))
        assertNull(r("FREQ=DAILY;INTERVAL=3;UNTIL=20260302").afterCompletion(d(2026, 3, 1)))
    }

    @Test fun afterCompletion() {
        assertEquals(d(2026, 3, 13), r("FREQ=DAILY;INTERVAL=3").afterCompletion(d(2026, 3, 10)))
        assertEquals(d(2026, 2, 28), r("FREQ=MONTHLY").afterCompletion(d(2026, 1, 31)))
        assertEquals(d(2026, 3, 24), r("FREQ=WEEKLY;INTERVAL=2").afterCompletion(d(2026, 3, 10)))
    }

    @Test fun summaries() {
        assertEquals("Every 2 weeks on Sun", r("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU").summary())
        assertEquals("Monthly on the last Friday", r("FREQ=MONTHLY;BYDAY=-1FR").summary())
        assertEquals("Monthly on the 2nd Tuesday", r("FREQ=MONTHLY;BYDAY=2TU").summary())
        assertEquals("Monthly on the last day", r("FREQ=MONTHLY;BYMONTHDAY=-1").summary())
        assertEquals("3 days after completion, 10 times", r("FREQ=DAILY;INTERVAL=3;COUNT=10").summary(fromCompletion = true))
        assertEquals("Weekdays", RRule.weekdays().summary())
        assertEquals("Yearly on Feb 29", r("FREQ=YEARLY").summary(start = d(2028, 2, 29)))
        assertEquals("Daily, until 2026-12-31", r("FREQ=DAILY;UNTIL=20261231").summary())
        assertEquals("Every 3 months on the 15th", r("FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=15").summary())
    }

    @Test fun legacyConversion() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,SU", RRule.fromLegacy("WEEKLY", 0b1000001, 2))
        assertEquals("FREQ=DAILY;INTERVAL=4", RRule.fromLegacy("EVERY_N_DAYS", 0, 4))
        assertEquals("FREQ=MONTHLY;INTERVAL=3", RRule.fromLegacy("MONTHLY", 0, 3))
        assertEquals("FREQ=DAILY", RRule.fromLegacy("DAILY", 0, 7))
        assertEquals("FREQ=YEARLY", RRule.fromLegacy("YEARLY", 0, 1))
        assertNull(RRule.fromLegacy("NONE", 0, 1))
    }

    // ---- tasks on top of rules ----

    private fun task(rule: String, due: LocalDate, fromCompletion: Boolean = false, occurrence: Int = 1, anchor: LocalDate? = null) =
        TaskEntity(title = "t", dueEpochDay = due.toEpochDay(), repeatRule = rule, repeatFromCompletion = fromCompletion,
            repeatOccurrence = occurrence, repeatAnchorEpochDay = anchor?.toEpochDay(), createdAt = 0)

    @Test fun followingHonoursCountAndBasis() {
        val t = task("FREQ=DAILY;COUNT=3", d(2026, 3, 1))
        assertEquals(d(2026, 3, 2), Recurrence.following(t, d(2026, 3, 1)))
        assertNull(Recurrence.following(t.copy(repeatOccurrence = 3), d(2026, 3, 3)))
        // Completed 2 days late: a fixed schedule continues after today; completion-based counts from today.
        assertEquals(d(2026, 3, 10), Recurrence.following(task("FREQ=WEEKLY", d(2026, 3, 3)), d(2026, 3, 5)))
        assertEquals(d(2026, 3, 8), Recurrence.following(task("FREQ=DAILY;INTERVAL=3", d(2026, 3, 3), fromCompletion = true), d(2026, 3, 5)))
    }

    @Test fun rollForwardOverdue() {
        val today = d(2026, 3, 18)
        assertEquals(d(2026, 3, 27), Recurrence.rolledForward(task("FREQ=MONTHLY;BYDAY=-1FR", d(2026, 1, 30)), today))
        assertEquals(today, Recurrence.rolledForward(task("FREQ=DAILY", d(2026, 3, 10)), today))
        // Every-2-weeks Sunday keeps its anchor parity (anchor Mar 1 -> Mar 29, not Mar 22).
        assertEquals(d(2026, 3, 29), Recurrence.rolledForward(task("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU", d(2026, 3, 15), anchor = d(2026, 3, 1)), today))
        assertEquals(today, Recurrence.rolledForward(task("FREQ=DAILY;INTERVAL=3", d(2026, 3, 1), fromCompletion = true), today))
        assertNull(Recurrence.rolledForward(task("FREQ=WEEKLY;UNTIL=20260310", d(2026, 3, 3)), today))
    }

    @Test fun taskSummary() {
        assertEquals("Monthly on the 4th", Recurrence.summary(task("FREQ=MONTHLY", d(2026, 3, 4))))
        assertNull(Recurrence.summary(TaskEntity(title = "x", createdAt = 0)))
    }

    // ---- habits ----

    private fun habit(kind: ScheduleKind, rule: String? = null, perWeek: Int = 0, anchor: LocalDate = d(2026, 3, 2)) =
        HabitEntity(name = "h", iconKey = "x", colorArgb = 0, anchorEpochDay = anchor.toEpochDay(), scheduleKind = kind,
            scheduleRule = rule, timesPerWeek = perWeek, createdAt = 0)

    @Test fun habitRuleSchedules() {
        val biweekly = habit(ScheduleKind.RULE, "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH")
        assertTrue(HabitSchedule.isScheduled(biweekly, d(2026, 3, 5)))
        assertFalse(HabitSchedule.isScheduled(biweekly, d(2026, 3, 9)))
        assertTrue(HabitSchedule.isScheduled(biweekly, d(2026, 3, 16)))
        val monthly = habit(ScheduleKind.RULE, "FREQ=MONTHLY;BYMONTHDAY=1,-1")
        assertTrue(HabitSchedule.isScheduled(monthly, d(2026, 3, 31)))
        assertTrue(HabitSchedule.isScheduled(monthly, d(2026, 4, 1)))
        assertFalse(HabitSchedule.isScheduled(monthly, d(2026, 4, 2)))
        assertEquals("Every 2 weeks on Mon, Thu", HabitSchedule.summary(biweekly))
        assertEquals("3 times a week", HabitSchedule.summary(habit(ScheduleKind.TIMES_PER_WEEK, perWeek = 3)))
    }

    @Test fun timesPerWeekIsFlexible() {
        val h = habit(ScheduleKind.TIMES_PER_WEEK, perWeek = 2)
        assertTrue(HabitSchedule.isDueOn(h, d(2026, 3, 4), 1))
        assertFalse(HabitSchedule.isDueOn(h, d(2026, 3, 4), 2))
        // Week of Mar 2: Mon + Thu done (quota met). Week of Mar 9: Tue done so far.
        val counts = listOf(d(2026, 3, 2), d(2026, 3, 5), d(2026, 3, 10)).associate { it.toEpochDay() to 1 }
        assertEquals(3, HabitSchedule.streak(h, d(2026, 3, 12), counts))
        assertEquals(3, HabitSchedule.bestStreak(h, d(2026, 3, 2), d(2026, 3, 12), counts))
        // A past week below quota breaks the streak.
        val short = listOf(d(2026, 3, 2), d(2026, 3, 10)).associate { it.toEpochDay() to 1 }
        assertEquals(1, HabitSchedule.streak(h, d(2026, 3, 12), short))
    }

    @Test fun activeStreakCountsDaysWithAnyCheckIn() {
        // Ten a day: 3 glasses a day never reach the target, so the strict streak is 0 but the run of active days is not.
        val water = habit(ScheduleKind.DAILY).copy(timesPerDay = 10)
        val counts = (2L..5L).associate { d(2026, 3, it.toInt()).toEpochDay() to 3 }
        assertEquals(0, HabitSchedule.streak(water, d(2026, 3, 5), counts))
        assertEquals(4, HabitSchedule.activeStreak(water, d(2026, 3, 5), counts))
        // Nothing yet today does not break the run; a gap yesterday does.
        assertEquals(4, HabitSchedule.activeStreak(water, d(2026, 3, 6), counts))
        assertEquals(0, HabitSchedule.activeStreak(water, d(2026, 3, 7), counts))
        // Vacation days are skipped, and days before the habit existed end the run.
        assertEquals(4, HabitSchedule.activeStreak(water, d(2026, 3, 7), counts, skip = { it == d(2026, 3, 6) || it == d(2026, 3, 7) }))
        assertEquals(4, HabitSchedule.activeStreak(water, d(2026, 3, 5), counts + (d(2026, 3, 1).toEpochDay() to 3)))
    }

    @Test fun activeStreakOfAFlexibleWeeklyHabitSurvivesMetWeeks() {
        val h = habit(ScheduleKind.TIMES_PER_WEEK, perWeek = 2)
        val counts = listOf(d(2026, 3, 2), d(2026, 3, 5), d(2026, 3, 10)).associate { it.toEpochDay() to 1 }
        assertEquals(3, HabitSchedule.activeStreak(h, d(2026, 3, 12), counts))
    }
}
