package com.earnd.app.domain

import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.data.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun task(kind: RepeatKind, due: LocalDate?, mask: Int = 0, interval: Int = 1) = TaskEntity(
        title = "t", dueEpochDay = due?.toEpochDay(), repeatKind = kind, repeatWeekdayMask = mask,
        repeatInterval = interval, createdAt = 0,
    )
    private val wed = LocalDate.of(2026, 3, 4)

    @Test fun none() = assertNull(Recurrence.next(task(RepeatKind.NONE, wed), wed))

    @Test fun daily() = assertEquals(wed.plusDays(1), Recurrence.next(task(RepeatKind.DAILY, wed), wed))

    @Test fun weeklyOnMask() {
        val monFri = 0b0010001
        assertEquals(LocalDate.of(2026, 3, 6), Recurrence.next(task(RepeatKind.WEEKLY, wed, monFri), wed))
        assertEquals(LocalDate.of(2026, 3, 9), Recurrence.next(task(RepeatKind.WEEKLY, wed, monFri), LocalDate.of(2026, 3, 6)))
        assertEquals(wed.plusWeeks(1), Recurrence.next(task(RepeatKind.WEEKLY, wed), wed))
    }

    @Test fun everyNDaysStaysAligned() {
        val t = task(RepeatKind.EVERY_N_DAYS, wed, interval = 3)
        assertEquals(wed.plusDays(3), Recurrence.next(t, wed))
        assertEquals(wed.plusDays(12), Recurrence.next(t, wed.plusDays(10)))
    }

    @Test fun monthlyClampsWithoutDrift() {
        val t = task(RepeatKind.MONTHLY, LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.next(t, LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(t, LocalDate.of(2026, 2, 28)))
    }

    @Test fun rollForwardTargetIsTodayOrLater() {
        val t = task(RepeatKind.EVERY_N_DAYS, wed.minusDays(10), interval = 2)
        val next = Recurrence.next(t, wed.minusDays(1))!!
        assertTrue(!next.isBefore(wed))
    }

    private fun anchored(kind: RepeatKind, due: LocalDate, anchor: LocalDate, mask: Int = 0, interval: Int = 1) =
        task(kind, due, mask, interval).copy(repeatAnchorEpochDay = anchor.toEpochDay())

    @Test fun yearlySameMonthDay() {
        val t = task(RepeatKind.YEARLY, LocalDate.of(2026, 7, 15))
        assertEquals(LocalDate.of(2027, 7, 15), Recurrence.next(t, LocalDate.of(2026, 7, 15)))
        assertEquals(LocalDate.of(2026, 7, 15), Recurrence.next(t, LocalDate.of(2026, 1, 1)))
    }

    @Test fun yearlyLeapDay() {
        val leap = LocalDate.of(2028, 2, 29)
        val t = task(RepeatKind.YEARLY, leap)
        assertEquals(LocalDate.of(2029, 2, 28), Recurrence.next(t, leap))
        // Later occurrence (due Feb 28) still anchored on Feb 29 returns to it in the next leap year.
        val later = anchored(RepeatKind.YEARLY, LocalDate.of(2031, 2, 28), leap)
        assertEquals(LocalDate.of(2032, 2, 29), Recurrence.next(later, LocalDate.of(2031, 2, 28)))
    }

    @Test fun yearlyRollForwardOverdue() {
        val t = task(RepeatKind.YEARLY, LocalDate.of(2020, 3, 1))
        assertEquals(LocalDate.of(2027, 3, 1), Recurrence.next(t, wed.minusDays(1)))
    }

    @Test fun biweeklyAnchoredOnFirstWeek() {
        val sun = 1 shl 6
        val monSun = sun or 1
        val firstSun = LocalDate.of(2026, 3, 8) // week of Mon 2 Mar
        val t = task(RepeatKind.WEEKLY, firstSun, sun, interval = 2)
        assertEquals(LocalDate.of(2026, 3, 22), Recurrence.next(t, firstSun))
        // Mon+Sun every 2 weeks, anchored on week of 2 Mar: after Sun 8 Mar comes Mon 16 Mar.
        val t2 = anchored(RepeatKind.WEEKLY, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), monSun, 2)
        assertEquals(LocalDate.of(2026, 3, 8), Recurrence.next(t2, LocalDate.of(2026, 3, 2)))
        assertEquals(LocalDate.of(2026, 3, 16), Recurrence.next(t2, LocalDate.of(2026, 3, 8)))
        // A later occurrence keeps the original week parity.
        val t3 = anchored(RepeatKind.WEEKLY, LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 2), monSun, 2)
        assertEquals(LocalDate.of(2026, 3, 22), Recurrence.next(t3, LocalDate.of(2026, 3, 16)))
        assertEquals(LocalDate.of(2026, 3, 30), Recurrence.next(t3, LocalDate.of(2026, 3, 22)))
    }

    @Test fun biweeklyRollForwardOverdue() {
        val sun = 1 shl 6
        val t = anchored(RepeatKind.WEEKLY, LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), sun, 2)
        // today Wed 4 Mar: Sundays on-week are 1 Mar, 15 Mar (Jan 4 + 10 weeks = 15 Mar).
        assertEquals(LocalDate.of(2026, 3, 15), Recurrence.next(t, wed.minusDays(1)))
    }

    @Test fun bucketOf() {
        val today = wed.toEpochDay()
        assertEquals(TaskBucket.OVERDUE, TaskService.bucketOf(task(RepeatKind.NONE, wed.minusDays(1)), today))
        assertEquals(TaskBucket.TODAY, TaskService.bucketOf(task(RepeatKind.NONE, wed), today))
        assertEquals(TaskBucket.UPCOMING, TaskService.bucketOf(task(RepeatKind.NONE, wed.plusDays(1)), today))
        assertEquals(TaskBucket.NO_DATE, TaskService.bucketOf(task(RepeatKind.NONE, null), today))
    }

    @Test fun habitScheduleAndStreak() {
        val h = HabitEntity(name = "h", iconKey = "x", colorArgb = 0, anchorEpochDay = wed.minusDays(30).toEpochDay(),
            scheduleKind = ScheduleKind.WEEKDAYS, weekdayMask = 0b0000101, createdAt = 0) // Mon, Wed
        assertTrue(HabitSchedule.isScheduled(h, wed))
        assertFalse(HabitSchedule.isScheduled(h, wed.plusDays(1)))
        val counts = mapOf(wed.minusDays(2).toEpochDay() to 1, wed.minusDays(7).toEpochDay() to 1)
        assertEquals(2, HabitSchedule.streak(h, wed, counts)) // today not done yet doesn't break
        assertEquals(1, HabitSchedule.streak(h, wed, counts, skip = { false }, maxDays = 3))
    }
}
