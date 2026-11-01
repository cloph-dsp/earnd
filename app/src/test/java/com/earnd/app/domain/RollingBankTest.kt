package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.ui.settings.SettingsChanges
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RollingBankTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
    private val ledger = FakeLedgerDao()
    private val sessions = FakeSessionDao()
    private val settings = SettingsRepository(FakeSettingDao(), clock)
    private val bank = TimeBank(ledger, sessions, settings, clock, ChangeBus())

    private suspend fun earn(m: Int, src: Long) = bank.earn(m, LedgerKind.EARN_HABIT, OwnerKind.HABIT, src)

    @Test fun rollingIsTheDefault() {
        assertEquals(ResetPeriod.ROLLING, Settings().resetPeriod)
        assertEquals(24, Settings().rollingExpiryHours)
    }

    @Test fun minutesExpireAfterConfiguredHours() = runTest {
        earn(10, 1)
        clock.advanceMin(24 * 60 - 1)
        assertEquals(10, bank.balance().minutes)
        clock.advanceMin(1)
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun hoursSettingApplies() = runTest {
        settings.update { it.copy(rollingExpiryHours = 12) }
        earn(10, 1)
        clock.advanceMin(11 * 60)
        assertEquals(10, bank.balance().minutes)
        clock.advanceMin(60)
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun balanceReportsNextExpiry() = runTest {
        earn(10, 1)
        val first = clock.now() + 24 * TimeBank.HOUR
        clock.advanceMin(60)
        earn(5, 2)
        val b = bank.balance()
        assertEquals(15, b.minutes)
        assertEquals(first, b.nextExpiryAt)
        assertEquals(10, b.minutesExpiringNext)
        assertNull(b.resetsAt)
    }

    @Test fun spendConsumesOldestFirst() = runTest {
        earn(10, 1)
        clock.advanceMin(60)
        earn(10, 2)
        assertTrue(bank.spend(12) is SpendResult.Started)
        // 10 from the first entry, 2 from the second: only 8 of the second remain
        val b = bank.balance()
        assertEquals(8, b.minutes)
        assertEquals(8, b.minutesExpiringNext)
        clock.advanceMin(23 * 60 + 1)
        assertEquals(8, bank.balance().minutes)
    }

    @Test fun partialSpendLeavesRemainderOfEntry() = runTest {
        earn(10, 1)
        bank.spend(4)
        assertEquals(6, bank.balance().minutes)
        assertEquals(6, bank.balance().minutesExpiringNext)
    }

    @Test fun spendRefusedWithOnlyExpiredMinutes() = runTest {
        earn(10, 1)
        clock.advanceMin(25 * 60)
        assertEquals(SpendResult.Refused(RefuseReason.NOT_ENOUGH), bank.spend(5))
    }

    @Test fun sessionCapAppliesAndRestStaysBanked() = runTest {
        settings.update { it.copy(unlockCapMin = 20) }
        earn(30, 1)
        earn(30, 2)
        val r = bank.spend(50) as SpendResult.Started
        assertEquals(20 * TimeBank.MINUTE, r.session.plannedEndAt - r.session.startedAt)
        assertEquals(40, bank.balance().minutes)
    }

    @Test fun sessionCrossingAnExpiryKeepsItsMinutes() = runTest {
        earn(30, 1)
        clock.advanceMin(23 * 60 + 50)
        bank.spend(30) // runs 30 min, past the 24 h mark of the entry it drew from
        clock.advanceMin(20)
        assertEquals(0, bank.balance().minutes)
        assertNull(bank.balance().nextExpiryAt)
        assertTrue(bank.activeSession() != null)
    }

    @Test fun earlyEndRefundsIntoSameExpiry() = runTest {
        earn(30, 1)
        val exp = clock.now() + 24 * TimeBank.HOUR
        bank.spend(30)
        clock.advanceMin(10)
        bank.endSession()
        val b = bank.balance()
        assertEquals(20, b.minutes)
        assertEquals(exp, b.nextExpiryAt) // the refund does not buy a fresh 24 h
    }

    @Test fun refundLostWhenItsEntryAlreadyExpired() = runTest {
        earn(30, 1)
        clock.advanceMin(23 * 60 + 50)
        bank.spend(30)
        clock.advanceMin(15)
        bank.endSession()
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun undoRemovesUnspentMinutes() = runTest {
        earn(10, 1)
        earn(5, 2)
        bank.reverse(OwnerKind.HABIT, 1)
        assertEquals(5, bank.balance().minutes)
        bank.reverse(OwnerKind.HABIT, 1)
        assertEquals(5, bank.balance().minutes)
        assertEquals(0, bank.netFor(OwnerKind.HABIT, 1))
    }

    @Test fun undoAfterPartialSpendTakesDeficitFromBalance() = runTest {
        earn(10, 1)
        clock.advanceMin(5)
        earn(8, 2)
        bank.spend(6) // 6 from entry 1 -> 4 left there
        bank.reverse(OwnerKind.HABIT, 1) // 4 unspent removed, 6 deficit from entry 2
        assertEquals(2, bank.balance().minutes)
    }

    @Test fun undoNeverGoesBelowZero() = runTest {
        earn(10, 1)
        bank.spend(10)
        bank.reverse(OwnerKind.HABIT, 1)
        assertEquals(0, bank.balance().minutes)
        earn(3, 2)
        assertEquals(3, bank.balance().minutes)
    }

    @Test fun undoOfExpiredEntryDoesNotTouchOthers() = runTest {
        earn(10, 1)
        clock.advanceMin(25 * 60)
        earn(5, 2)
        bank.reverse(OwnerKind.HABIT, 1)
        assertEquals(5, bank.balance().minutes)
    }

    @Test fun comebackBonusExpiresToo() = runTest {
        bank.earn(5, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        bank.earn(5, LedgerKind.COMEBACK, OwnerKind.HABIT, 1)
        assertEquals(10, bank.balance().minutes)
        clock.advanceMin(24 * 60)
        assertEquals(0, bank.balance().minutes)
        bank.reverse(OwnerKind.HABIT, 1)
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun switchingDailyToRollingKeepsBalanceAndStartsExpiry() = runTest {
        settings.update { it.copy(resetPeriod = ResetPeriod.DAILY) }
        earn(12, 1)
        settings.update { it.copy(resetPeriod = ResetPeriod.ROLLING) }
        assertEquals(12, bank.balance().minutes)
        clock.advanceMin(24 * 60)
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun switchingRollingToDailyCarriesLiveMinutesOnly() = runTest {
        earn(10, 1)
        clock.advanceMin(20 * 60)
        earn(7, 2)
        clock.advanceMin(5 * 60) // entry 1 expired
        settings.update { it.copy(resetPeriod = ResetPeriod.DAILY) }
        assertEquals(7, bank.balance().minutes)
        bank.spend(2)
        assertEquals(5, bank.balance().minutes)
    }

    @Test fun switchingRollingToWeeklyAndBackKeepsBalance() = runTest {
        earn(9, 1)
        settings.update { it.copy(resetPeriod = ResetPeriod.WEEKLY) }
        assertEquals(9, bank.balance().minutes)
        settings.update { it.copy(resetPeriod = ResetPeriod.ROLLING) }
        assertEquals(9, bank.balance().minutes)
        assertEquals(9, bank.balance().minutesExpiringNext)
    }

    @Test fun periodRollDoesNothingInRollingMode() = runTest {
        earn(10, 1)
        clock.set(LocalDateTime.of(2026, 3, 5, 0, 5))
        bank.resetIfPeriodRolled()
        assertEquals(10, bank.balance().minutes)
    }

    @Test fun switchingToRollingIsWeakening() {
        val daily = Settings(resetPeriod = ResetPeriod.DAILY)
        assertTrue(SettingsChanges.isWeakening(daily, daily.copy(resetPeriod = ResetPeriod.ROLLING)))
        assertTrue(SettingsChanges.isWeakening(Settings(resetPeriod = ResetPeriod.WEEKLY), Settings(resetPeriod = ResetPeriod.ROLLING)))
        assertFalse(SettingsChanges.isWeakening(Settings(resetPeriod = ResetPeriod.ROLLING), Settings(resetPeriod = ResetPeriod.DAILY)))
        assertFalse(SettingsChanges.isWeakening(Settings(), Settings(rollingExpiryHours = 12)))
    }
}
