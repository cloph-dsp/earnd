package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.ui.settings.SettingsChanges
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class UnlockCapTest {
    @Test fun choicesAreOffThen15To45() {
        assertEquals(listOf(null, 15, 20, 25, 30, 35, 40, 45), UnlockCap.CHOICES)
    }

    @Test fun normalizeSnapsAndRejectsOutOfRange() {
        assertNull(UnlockCap.normalize(null))
        assertNull(UnlockCap.normalize(10))
        assertNull(UnlockCap.normalize(50))
        assertEquals(20, UnlockCap.normalize(22))
        assertEquals(25, UnlockCap.normalize(23))
        assertEquals(45, UnlockCap.normalize(45))
    }

    @Test fun clampOnlyShortens() {
        assertEquals(60, UnlockCap.clamp(60, null))
        assertEquals(30, UnlockCap.clamp(60, 30))
        assertEquals(10, UnlockCap.clamp(10, 30))
    }

    @Test fun choicesEndAtCappedBalance() {
        val base = listOf(5, 10, 15, 30, 60)
        assertEquals(listOf(5, 10, 15, 30, 60, 90), UnlockCap.choices(base, 90, null))
        assertEquals(listOf(5, 10, 15, 25), UnlockCap.choices(base, 90, 25))
        assertEquals(listOf(5, 8), UnlockCap.choices(base, 8, 25))
        assertEquals(emptyList<Int>(), UnlockCap.choices(base, 0, 25))
    }

    @Test fun spendIsCappedAndRestStaysBanked() = runTest {
        val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
        val settings = SettingsRepository(FakeSettingDao(), clock)
        val sessions = FakeSessionDao()
        val bank = TimeBank(FakeLedgerDao(), sessions, settings, clock, ChangeBus())
        settings.update { it.copy(unlockCapMin = 20, resetPeriod = com.earnd.app.data.entity.ResetPeriod.DAILY) }
        bank.earn(50, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        val r = bank.spend(50)
        assertTrue(r is SpendResult.Started)
        val s = (r as SpendResult.Started).session
        assertEquals(20 * TimeBank.MINUTE, s.plannedEndAt - s.startedAt)
        assertEquals(30, bank.balance().minutes)
    }

    @Test fun loosenCapOrPuzzleIsWeakening() {
        val base = Settings(unlockCapMin = 20, removalPuzzle = PuzzleKind.SENTENCE)
        assertTrue(SettingsChanges.isWeakening(base, base.copy(unlockCapMin = 30)))
        assertTrue(SettingsChanges.isWeakening(base, base.copy(unlockCapMin = null)))
        assertFalse(SettingsChanges.isWeakening(base, base.copy(unlockCapMin = 15)))
        assertFalse(SettingsChanges.isWeakening(Settings(), Settings(unlockCapMin = 30)))
        assertTrue(SettingsChanges.isWeakening(base, base.copy(removalPuzzle = PuzzleKind.NONE)))
        assertFalse(SettingsChanges.isWeakening(Settings(), Settings(removalPuzzle = PuzzleKind.ARITHMETIC)))
    }
}
