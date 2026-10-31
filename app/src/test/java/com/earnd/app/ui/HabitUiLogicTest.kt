package com.earnd.app.ui

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.ui.habits.HabitCalendar
import com.earnd.app.ui.habits.HabitRules
import com.earnd.app.ui.settings.SettingsChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HabitUiLogicTest {
    private val h = HabitEntity(name = "x", iconKey = "star", colorArgb = 0, anchorEpochDay = 0, createdAt = 0)

    @Test fun monthCellsPadToWholeWeeks() {
        // 1 Oct 2026 is a Thursday.
        val cells = HabitCalendar.monthCells(YearMonth.of(2026, 10))
        assertEquals(0, cells.size % 7)
        assertEquals(3, cells.indexOfFirst { it != null })
        assertEquals(LocalDate.of(2026, 10, 31), cells.filterNotNull().last())
        val sunday = HabitCalendar.monthCells(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(4, sunday.indexOfFirst { it != null })
    }

    @Test fun daysDoneCountsOnlyScheduledCompleteDays() {
        val start = LocalDate.of(2026, 1, 1)
        val days = (0L until 10).map { start.plusDays(it) }
        val done = setOf(0, 1, 2, 3, 8).map { start.plusDays(it.toLong()) }.toSet()
        val off = start.plusDays(2)
        assertEquals(4, HabitCalendar.perfectDays(days, { it != off }, { it in done }))
    }

    @Test fun overridesFallBackToSettings() {
        val s = Settings(photoRequiredDefault = true, defaultHabitRewardMin = 7)
        assertTrue(HabitRules.needsPhoto(h, s))
        assertFalse(HabitRules.needsPhoto(h.copy(photoRequired = false), s))
        assertTrue(HabitRules.needsPhoto(h.copy(checkInMode = CheckInMode.PHOTO), Settings()))
        assertEquals(7, HabitRules.rewardMinutes(h, s))
        assertEquals(2, HabitRules.rewardMinutes(h.copy(rewardMinutes = 2), s))
    }

    @Test fun amountsParseAndFormat() {
        assertEquals(1.5, HabitRules.parseAmount("1,5")!!, 0.0)
        assertNull(HabitRules.parseAmount("-2"))
        assertNull(HabitRules.parseAmount("abc"))
        assertEquals("2", HabitRules.formatAmount(2.0))
        assertEquals("0.25", HabitRules.formatAmount(0.25))
    }

    @Test fun moveReorders() {
        assertEquals(listOf("b", "a", "c"), HabitRules.move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("c", "a", "b"), HabitRules.move(listOf("a", "b", "c"), 2, -5))
        assertEquals(listOf("a"), HabitRules.move(listOf("a"), 3, 0))
    }

    @Test fun newSettingsWeakeningRules() {
        val base = Settings()
        assertTrue(SettingsChanges.isWeakening(base, base.copy(segmentMode = true)))
        assertTrue(SettingsChanges.isWeakening(base.copy(rewardMode = RewardMode.ALL_DONE), base))
        assertTrue(SettingsChanges.isWeakening(base.copy(photoRequiredDefault = true), base))
        assertTrue(SettingsChanges.isWeakening(base, base.copy(defaultHabitRewardMin = 10)))
        assertFalse(SettingsChanges.isWeakening(base, base.copy(rewardMode = RewardMode.ALL_DONE)))
        assertFalse(SettingsChanges.isWeakening(base, base.copy(morningNudgeEnabled = true)))
    }
}
