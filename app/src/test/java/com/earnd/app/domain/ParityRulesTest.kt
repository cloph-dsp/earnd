package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.data.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParityRulesTest {
    private val wed = LocalDate.of(2026, 3, 4)
    private fun habit(
        target: Double? = null, times: Int = 1, reward: Int? = null, kind: ScheduleKind = ScheduleKind.DAILY,
        n: Int = 1, anchor: Long = wed.toEpochDay(), reminderDays: Int = 0, createdAt: Long = 0,
    ) = HabitEntity(id = 1, name = "h", iconKey = "x", colorArgb = 0, timesPerDay = times, scheduleKind = kind,
        everyNDays = n, anchorEpochDay = anchor, targetQuantity = target, rewardMinutes = reward,
        reminderDays = reminderDays, createdAt = createdAt)
    private fun ci(q: Double? = null) =
        CheckInEntity(habitId = 1, epochDay = wed.toEpochDay(), at = 0, quantity = q, minutesEarned = 0)

    @Test fun segments() {
        val s = Settings()
        assertEquals(DaySegment.NONE, Segments.current(4 * 60, s))
        assertEquals(DaySegment.MORNING, Segments.current(5 * 60, s))
        assertEquals(DaySegment.AFTERNOON, Segments.current(12 * 60, s))
        assertEquals(DaySegment.EVENING, Segments.current(23 * 60 + 59, s))
        assertEquals(12 * 60, Segments.endMinute(DaySegment.MORNING, s))
        assertEquals(DaySegment.NONE, Segments.current(13 * 60, s.copy(afternoonStartMin = 3 * 60)))
    }

    @Test fun quantityAddsUpAndEarnsOnce() {
        val h = habit(target = 2.0)
        assertFalse(HabitProgress.isComplete(h, listOf(ci(1.0))))
        assertTrue(HabitProgress.isComplete(h, listOf(ci(1.0), ci(1.5))))
        assertTrue(HabitProgress.earns(h, listOf(ci(1.0)), listOf(ci(1.0), ci(1.0))))
        assertFalse(HabitProgress.earns(h, listOf(ci(2.0)), listOf(ci(2.0), ci(1.0))))
        assertFalse(HabitProgress.earns(h, emptyList(), listOf(ci(0.5))))
    }

    @Test fun countedEarnsUpToTimesPerDay() {
        val h = habit(times = 2)
        assertTrue(HabitProgress.earns(h, listOf(ci()), listOf(ci(), ci())))
        assertFalse(HabitProgress.earns(h, listOf(ci(), ci()), listOf(ci(), ci(), ci())))
    }

    @Test fun rewardOverrideAndDefault() {
        val s = Settings(defaultHabitRewardMin = 7)
        assertEquals(7, HabitProgress.rewardMinutes(habit(), s))
        assertEquals(3, HabitProgress.rewardMinutes(habit(reward = 3), s))
        assertEquals(6, HabitProgress.dayValue(habit(reward = 3, times = 2), s))
        assertEquals(3, HabitProgress.dayValue(habit(reward = 3, times = 2, target = 1.0), s))
    }

    @Test fun photoDefault() {
        assertTrue(HabitProgress.photoRequired(habit(), Settings(photoRequiredDefault = true)))
        assertFalse(HabitProgress.photoRequired(habit().copy(photoRequired = false), Settings(photoRequiredDefault = true)))
    }

    @Test fun everyNDaysZeroIntervalAndUnsetAnchor() {
        assertTrue(HabitSchedule.isScheduled(habit(kind = ScheduleKind.EVERY_N_DAYS, n = 0), wed.plusDays(1)))
        val created = wed.toEpochDay() * 86_400_000L
        val h = habit(kind = ScheduleKind.EVERY_N_DAYS, n = 3, anchor = 0, createdAt = created)
        assertTrue(HabitSchedule.isScheduled(h, wed))
        assertFalse(HabitSchedule.isScheduled(h, wed.plusDays(1)))
        assertTrue(HabitSchedule.isScheduled(h, wed.plusDays(3)))
        assertFalse(HabitSchedule.isScheduled(h, wed.minusDays(3)))
    }

    @Test fun reminderDays() {
        val monOnly = habit(reminderDays = 1)
        assertTrue(HabitSchedule.remindsOn(monOnly, LocalDate.of(2026, 3, 2)))
        assertFalse(HabitSchedule.remindsOn(monOnly, wed))
        assertTrue(HabitSchedule.remindsOn(habit(), wed))
    }

    @Test fun bestStreak() {
        val h = habit(anchor = wed.minusDays(10).toEpochDay())
        // Done on days -10..-7 (4), missed -6, done -5..-1 (5); today not done yet.
        val days = (0..9L).filter { it != 4L }.associate { wed.minusDays(10 - it).toEpochDay() to 1 }
        assertEquals(5, HabitSchedule.bestStreak(h, wed.minusDays(30), wed, days))
        assertEquals(5, HabitSchedule.streak(h, wed, days))
        val longer = days + (wed.minusDays(6).toEpochDay() to 1)
        assertEquals(10, HabitSchedule.bestStreak(h, wed.minusDays(30), wed, longer))
    }

    @Test fun recurrenceIntervalsAndMonthEnd() {
        fun t(kind: RepeatKind, due: LocalDate?, interval: Int) =
            TaskEntity(title = "t", dueEpochDay = due?.toEpochDay(), repeatKind = kind, repeatInterval = interval, createdAt = 0)
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.next(t(RepeatKind.MONTHLY, jan31, 0), jan31))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(t(RepeatKind.MONTHLY, jan31, 1), LocalDate.of(2026, 2, 28)))
        assertEquals(LocalDate.of(2027, 2, 28), Recurrence.next(t(RepeatKind.MONTHLY, LocalDate.of(2024, 2, 29), 12), LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2036, 1, 31), Recurrence.next(t(RepeatKind.MONTHLY, LocalDate.of(1990, 1, 31), 12), LocalDate.of(2035, 6, 1)))
        assertEquals(LocalDate.of(2035, 7, 31), Recurrence.next(t(RepeatKind.MONTHLY, LocalDate.of(1990, 1, 31), 1), LocalDate.of(2035, 6, 30)))
        assertEquals(wed.plusDays(1), Recurrence.next(t(RepeatKind.EVERY_N_DAYS, wed, 0), wed))
        assertEquals(wed, Recurrence.next(t(RepeatKind.EVERY_N_DAYS, wed, 3), wed.minusDays(5)))
        assertEquals(wed.plusDays(6), Recurrence.next(t(RepeatKind.EVERY_N_DAYS, wed, 3), wed.plusDays(4)))
    }

    @Test fun segmentUnlockAllows() {
        val f = BlockFacts(blockingEnabled = true, onVacation = false, essential = false, leisure = true,
            inBedtime = false, bedtimeEndsAt = null, graceActive = false, sessionActive = false,
            allDoneUnlock = false, nothingScheduled = false)
        assertTrue(BlockRules.decide(f) is BlockDecision.Block)
        assertEquals(BlockDecision.Allow, BlockRules.decide(f.copy(segmentUnlock = true)))
        assertTrue(BlockRules.decide(f.copy(segmentUnlock = true, inBedtime = true)) is BlockDecision.Block)
    }
}
