package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.data.entity.SessionReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TimeBankTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0)) // Wednesday
    private val ledger = FakeLedgerDao()
    private val sessions = FakeSessionDao()
    private val settings = SettingsRepository(FakeSettingDao(), clock)
    private val bank = TimeBank(ledger, sessions, settings, clock, ChangeBus())

    @Test fun earnAndReverse() = runTest {
        bank.earn(10, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 7)
        bank.earn(5, LedgerKind.EARN_TASK, OwnerKind.TASK, 1)
        assertEquals(15, bank.balance().minutes)
        bank.reverse(OwnerKind.HABIT, 7)
        assertEquals(5, bank.balance().minutes)
        bank.reverse(OwnerKind.HABIT, 7) // idempotent
        assertEquals(5, bank.balance().minutes)
    }

    @Test fun spendNeedsBalanceAndRefundsWhenEndedEarly() = runTest {
        bank.earn(20, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        assertTrue(bank.spend(30) is SpendResult.Refused)
        val r = bank.spend(15)
        assertTrue(r is SpendResult.Started)
        assertEquals(5, bank.balance().minutes)
        clock.advanceMin(5)
        bank.endSession()
        assertEquals(15, bank.balance().minutes)
        assertNull(sessions.active())
    }

    @Test fun hardModeRefusesManualSpend() = runTest {
        settings.update { it.copy(hardMode = true) }
        bank.earn(20, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        val r = bank.spend(5)
        assertEquals(SpendResult.Refused(RefuseReason.HARD_MODE), r)
    }

    @Test fun dailyResetZeroesBalance() = runTest {
        bank.resetIfPeriodRolled()
        bank.earn(12, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        clock.set(LocalDateTime.of(2026, 3, 5, 0, 1))
        assertEquals(0, bank.balance().minutes)
        bank.resetIfPeriodRolled()
        assertEquals(LedgerKind.RESET, ledger.rows.value.last().kind)
        assertEquals(0, ledger.rows.value.sumOf { it.minutes })
    }

    @Test fun weeklyPeriodKeepsBalanceWithinWeek() = runTest {
        settings.update { it.copy(resetPeriod = ResetPeriod.WEEKLY) }
        bank.earn(12, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        clock.set(LocalDateTime.of(2026, 3, 8, 23, 0)) // Sunday
        assertEquals(12, bank.balance().minutes)
        clock.set(LocalDateTime.of(2026, 3, 9, 0, 5)) // Monday
        assertEquals(0, bank.balance().minutes)
    }

    @Test fun bedtimeGraceOncePerNight() = runTest {
        settings.update { it.copy(bedtimeEnabled = true, bedtimeStartMin = 22 * 60, bedtimeEndMin = 6 * 60) }
        assertEquals(SpendResult.Refused(RefuseReason.NOT_BEDTIME), bank.startBedtimeGrace())
        clock.set(LocalDateTime.of(2026, 3, 4, 23, 0))
        val first = bank.startBedtimeGrace() as SpendResult.Started
        assertEquals(SessionReason.BEDTIME_GRACE, first.session.reason)
        clock.set(LocalDateTime.of(2026, 3, 5, 1, 0))
        assertEquals(SpendResult.Refused(RefuseReason.GRACE_USED), bank.startBedtimeGrace())
        clock.set(LocalDateTime.of(2026, 3, 5, 23, 0))
        assertTrue(bank.startBedtimeGrace() is SpendResult.Started)
    }

    @Test fun allDoneUnlockRunsToMidnight() = runTest {
        val s = (bank.startAllDoneUnlock() as SpendResult.Started).session
        assertEquals(com.earnd.app.utc(LocalDateTime.of(2026, 3, 5, 0, 0)), s.plannedEndAt)
    }

    @Test fun expiredSessionIsClosed() = runTest {
        bank.startSession(5, SessionReason.ALL_DONE)
        clock.advanceMin(6)
        assertNull(bank.activeSession())
    }
}
