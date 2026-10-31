package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.LedgerDao
import com.earnd.app.data.entity.LedgerEntryEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.utc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class TimeBankAtomicityTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0)) // Wednesday

    /** Ledger whose reads yield, so unserialized read-then-write steps would interleave. */
    private class SlowLedger : LedgerDao {
        val inner = FakeLedgerDao()
        private val lock = Any()
        override fun observeSum(periodKey: Long) = inner.observeSum(periodKey)
        override suspend fun sum(periodKey: Long): Int {
            yield()
            val v = synchronized(lock) { inner.rows.value.filter { it.periodKey == periodKey }.sumOf { it.minutes } }
            yield()
            return v
        }
        override suspend fun insert(e: LedgerEntryEntity): Long = synchronized(lock) {
            val id = inner.rows.value.size + 1L
            inner.rows.value = inner.rows.value + e.copy(id = id)
            id
        }
        override suspend fun forSource(kind: OwnerKind, id: Long) = inner.forSource(kind, id)
        override fun observeRecent(limit: Int) = inner.observeRecent(limit)
    }

    @Test fun concurrentSpendsNeverOverdraw() = runBlocking {
        val ledger = SlowLedger()
        val bank = TimeBank(ledger, FakeSessionDao(), SettingsRepository(FakeSettingDao(), clock), clock, ChangeBus())
        bank.earn(10, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        val results = withContext(Dispatchers.Default) {
            (1..20).map { async { bank.spend(4) } }.awaitAll()
        }
        assertTrue(results.any { it is SpendResult.Started })
        assertTrue(bank.balance().minutes >= 0)
    }

    @Test fun concurrentEarnsAllLand() = runBlocking {
        val bank = TimeBank(SlowLedger(), FakeSessionDao(), SettingsRepository(FakeSettingDao(), clock), clock, ChangeBus())
        withContext(Dispatchers.Default) {
            (1..50).map { async { bank.earn(1, LedgerKind.EARN_TASK, OwnerKind.TASK, it.toLong()) } }.awaitAll()
        }
        assertEquals(50, bank.balance().minutes)
    }

    @Test fun periodChangeKeepsBalanceAndMovesReset() = runTest {
        val ledger = FakeLedgerDao()
        val settings = SettingsRepository(FakeSettingDao(), clock)
        val bank = TimeBank(ledger, FakeSessionDao(), settings, clock, ChangeBus())
        settings.update { it.copy(weekStartsOn = DayOfWeek.MONDAY) }
        // A Monday leftover under daily keys would otherwise leak into the weekly sum.
        clock.set(LocalDateTime.of(2026, 3, 2, 9, 0))
        bank.earn(7, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 1)
        clock.set(LocalDateTime.of(2026, 3, 4, 10, 0))
        bank.resetIfPeriodRolled()
        bank.earn(12, LedgerKind.EARN_HABIT, OwnerKind.HABIT, 2)
        assertEquals(12, bank.balance().minutes)
        settings.update { it.copy(resetPeriod = ResetPeriod.WEEKLY) }
        assertEquals(12, bank.balance().minutes)
        assertEquals(utc(LocalDateTime.of(2026, 3, 9, 0, 0)), bank.balance().resetsAt)
        settings.update { it.copy(resetPeriod = ResetPeriod.DAILY) }
        assertEquals(12, bank.balance().minutes)
        bank.resetIfPeriodRolled()
        assertEquals(12, bank.balance().minutes)
    }
}
