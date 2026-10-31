package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.ui.settings.SettingsChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComebackAndTimedTest {
    private val today = LocalDate.of(2026, 3, 18) // Wednesday
    private fun habit(kind: ScheduleKind = ScheduleKind.DAILY, mask: Int = 0b1111111, perWeek: Int = 0, anchor: LocalDate = today.minusDays(200)) =
        HabitEntity(id = 1, name = "h", iconKey = "x", colorArgb = 0, scheduleKind = kind, weekdayMask = mask,
            timesPerWeek = perWeek, anchorEpochDay = anchor.toEpochDay(), createdAt = 0)
    private fun days(vararg back: Long) = back.map { today.minusDays(it).toEpochDay() }.toSet()

    @Test fun multiplierSteps() {
        assertEquals(0, Comeback.bonus(20, 0))
        assertEquals(5, Comeback.bonus(20, 1))
        assertEquals(10, Comeback.bonus(20, 2))
        assertEquals(20, Comeback.bonus(20, 4))
        assertEquals(20, Comeback.bonus(20, 9))
        assertEquals("×1.25", Comeback.label(1))
        assertEquals("×1.5", Comeback.label(2))
        assertEquals("×2", Comeback.label(7))
    }

    /** Skipping k occasions then doing it once always earns strictly less than doing it every time. */
    @Test fun skippingNeverPays() {
        for (base in 1..240) for (k in 1..30) {
            val skipThenReturn = base + Comeback.bonus(base, k)
            val everyTime = base * (k + 1)
            assertTrue("base=$base k=$k", skipThenReturn < everyTime)
            assertTrue(Comeback.bonus(base, k) <= base)
        }
    }

    @Test fun dailyLapseCountsScheduledMissesUntilLastActiveDay() {
        val h = habit()
        assertEquals(3, Comeback.missedOccasions(h, today, days(4)))
        assertEquals(0, Comeback.missedOccasions(h, today, days(1)))
        assertEquals(0, Comeback.missedOccasions(h, today, days(0, 5)))
        // vacation days are neither missed nor the end of the lapse
        assertEquals(1, Comeback.missedOccasions(h, today, days(4), skip = { it.isAfter(today.minusDays(3)) }))
    }

    @Test fun onlyScheduledDaysCountAndAnchorBounds() {
        val mwf = habit(kind = ScheduleKind.WEEKDAYS, mask = 0b0010101) // Mon, Wed, Fri
        // last active: previous Wednesday (7 days back); missed Fri + Mon
        assertEquals(2, Comeback.missedOccasions(mwf, today, days(7)))
        assertEquals(2, Comeback.missedOccasions(habit(anchor = today.minusDays(2)), today, emptySet()))
    }

    @Test fun flexibleHabitsCountMissedWeeks() {
        val h = habit(kind = ScheduleKind.TIMES_PER_WEEK, perWeek = 3)
        // active 3 weeks ago (Wed 2026-02-25); weeks of Mar 2 and Mar 9 missed
        assertEquals(2, Comeback.missedOccasions(h, today, days(21)))
        assertEquals(0, Comeback.missedOccasions(h, today, days(1))) // Tuesday this week
        assertEquals(0, Comeback.missedOccasions(h, today, days(9)))
    }

    @Test fun durationOptionsParseAndScaleLinearly() {
        assertEquals(listOf(10 to 5, 20 to null, 30 to 15), TimedHabit.parse("30:15, 10:5,20"))
        val s = Settings(defaultHabitRewardMin = 10)
        val h = habit().copy(focusMinutes = 20, focusOptions = "10,30:12")
        assertEquals(listOf(FocusOption(10, 5), FocusOption(20, 10), FocusOption(30, 12)), TimedHabit.options(h, s))
        assertTrue(TimedHabit.needsChoice(h, s))
        assertFalse(TimedHabit.needsChoice(h.copy(focusOptions = null), s))
        assertEquals("10:5,20:10", TimedHabit.format(listOf(20 to 10, 10 to 5)))
        assertTrue(runCatching { TimedHabit.parse("0:5") }.isFailure)
    }

    @Test fun stopwatchReward() {
        val h = habit().copy(stopwatch = true, stopwatchRate = 0.5, stopwatchMinMin = 10)
        assertNull(TimedHabit.stopwatchReward(h, 9 * 60_000L + 59_000))
        assertEquals(5, TimedHabit.stopwatchReward(h, 10 * 60_000L))
        assertEquals(11, TimedHabit.stopwatchReward(h, 23 * 60_000L + 10_000))
        assertEquals(15, TimedHabit.stopwatchReward(h.copy(stopwatchCapMin = 30), 90 * 60_000L))
        assertEquals(2, TimedHabit.stopwatchReward(habit().copy(stopwatch = true), 5 * 60_000L)) // defaults 0.5, min 5
    }

    @Test fun turningComebackOnIsWeakening() {
        assertTrue(SettingsChanges.isWeakening(Settings(comebackBonus = false), Settings(comebackBonus = true)))
        assertFalse(SettingsChanges.isWeakening(Settings(comebackBonus = true), Settings(comebackBonus = false)))
    }
}
