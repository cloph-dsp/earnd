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
