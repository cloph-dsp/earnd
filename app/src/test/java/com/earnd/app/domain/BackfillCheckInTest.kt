package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.RewardMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Backfilling marks the missed day done but credits minutes to the balance we have now. */
class BackfillCheckInTest {
    private class SlowCheckInDao : CheckInDao {
        val rows = mutableListOf<CheckInEntity>()
        private var nextId = 1L
        private val lock = Any()

        override fun observeForDay(epochDay: Long) = flowOf(rows.filter { it.epochDay == epochDay })
        override suspend fun forDay(epochDay: Long) = query { it.epochDay == epochDay }
        override suspend fun forHabitBetween(habitId: Long, fromDay: Long, toDay: Long) =
            query { it.habitId == habitId && it.epochDay in fromDay..toDay }
        override suspend fun countFor(habitId: Long, epochDay: Long) =
            query { it.habitId == habitId && it.epochDay == epochDay }.size
        override suspend fun between(fromDay: Long, toDay: Long) = query { it.epochDay in fromDay..toDay }
        override suspend fun forHabitDay(habitId: Long, epochDay: Long) =
            query { it.habitId == habitId && it.epochDay == epochDay }
        override suspend fun insert(c: CheckInEntity): Long {
            yield()
            return synchronized(lock) {
                val n = c.copy(id = nextId++)
                rows += n
                n.id
            }
        }
        override suspend fun get(id: Long): CheckInEntity? = synchronized(lock) { rows.firstOrNull { it.id == id } }
        override suspend fun deleteById(id: Long) { synchronized(lock) { rows.removeAll { it.id == id } } }

        private suspend fun query(p: (CheckInEntity) -> Boolean): List<CheckInEntity> {
            yield()
            return synchronized(lock) { rows.filter(p) }
        }
    }

    private class SlowHabitDao : HabitDao {
        val rows = mutableListOf<HabitEntity>()
        private var nextId = 1L
        private val lock = Any()

        override fun observeActive() = flowOf(rows.filterNot { it.archived })
        override suspend fun get(id: Long): HabitEntity? = synchronized(lock) { rows.firstOrNull { it.id == id } }
        override suspend fun all() = synchronized(lock) { rows.toList() }
        override suspend fun active() = synchronized(lock) { rows.filterNot { it.archived } }
        override suspend fun upsert(h: HabitEntity): Long = synchronized(lock) {
            val n = h.copy(id = nextId++)
            rows += n
            n.id
        }
        override suspend fun archive(id: Long) {
            synchronized(lock) { rows.replaceAll { if (it.id == id) it.copy(archived = true) else it } }
        }
        override fun observeArchived() = flowOf(emptyList<HabitEntity>())
        override suspend fun unarchive(id: Long) {
            synchronized(lock) { rows.replaceAll { if (it.id == id) it.copy(archived = false) else it } }
        }
        override suspend fun setSortOrder(id: Long, sortOrder: Int) {
            synchronized(lock) { rows.replaceAll { if (it.id == id) it.copy(sortOrder = sortOrder) else it } }
        }
        override suspend fun nextSortOrder() = synchronized(lock) { rows.size }
        override suspend fun delete(h: HabitEntity) { synchronized(lock) { rows.removeAll { it.id == h.id } } }
    }

    private class Env {
        val today = LocalDate.of(2026, 3, 4)
        val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
        val habits = SlowHabitDao()
        val checkIns = SlowCheckInDao()
        val ledger = FakeLedgerDao()
        val settings = SettingsRepository(FakeSettingDao(), clock)
        val tx = MutexTransactor()
        val bank = TimeBank(ledger, FakeSessionDao(), settings, clock, ChangeBus(), tx)
        val service = HabitService(habits, checkIns, bank, clock, ChangeBus(), settings, tx)

        suspend fun habit(reward: Int = 5, anchor: LocalDate = LocalDate.of(2026, 3, 1)): Long =
            habits.upsert(HabitEntity(name = "Meds", iconKey = "check", colorArgb = 0,
                anchorEpochDay = anchor.toEpochDay(), rewardMinutes = reward, createdAt = clock.now()))
    }

    @Test fun backfillStoresYesterdaysDayButPaysIntoToday() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        val id = e.habit(anchor = yesterday)

        val saved = e.service.checkIn(id, onDay = yesterday)

        assertEquals(yesterday.toEpochDay(), saved.epochDay)
        assertEquals(e.clock.now(), saved.at)
        assertEquals(5, e.bank.balance().minutes)
        assertEquals(1, e.checkIns.rows.size)
    }

    @Test fun backfillTwicePaysOnce() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        val id = e.habit(anchor = yesterday)

        val first = e.service.checkInWithBonus(id, onDay = yesterday)
        val second = e.service.checkInWithBonus(id, onDay = yesterday)

        assertEquals(5, first.checkIn.minutesEarned)
        assertEquals(0, second.checkIn.minutesEarned)
        assertEquals(5, e.bank.balance().minutes)
        assertEquals(2, e.checkIns.rows.size)
    }

    @Test fun futureDayRejected() = runTest {
        val e = Env()
        val id = e.habit()

        val error = runCatching { e.service.checkIn(id, onDay = e.today.plusDays(1)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Cannot check in for a future day", error?.message)
        assertTrue(e.checkIns.rows.isEmpty())
        assertEquals(0, e.bank.balance().minutes)
    }

    @Test fun backfillSkipsComebackButNextNormalCheckInKeepsIt() = runTest {
        val e = Env()
        val id = e.habit()
        val yesterday = e.today.minusDays(1)
        // An active day further back makes the backfill a comeback candidate, and keeps the next day's lapse alive.
        e.checkIns.insert(CheckInEntity(habitId = id, epochDay = e.today.minusDays(3).toEpochDay(),
            at = e.clock.now() - 3 * 86_400_000L, minutesEarned = 5))

        val backfill = e.service.checkInWithBonus(id, onDay = yesterday)
        assertEquals(0, backfill.bonus)

        e.clock.nowMs += 86_400_000L
        val normal = e.service.checkInWithBonus(id)
        assertTrue(normal.bonus > 0)
    }

    @Test fun daySummaryShowsBackfillOnlyOnItsDay() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        val id = e.habit(anchor = yesterday)

        e.service.checkIn(id, onDay = yesterday)

        assertTrue(e.service.daySummary(yesterday).single { it.habit.id == id }.complete)
        assertEquals(0, e.service.daySummary(e.today).single { it.habit.id == id }.done)
    }

    @Test fun allDoneBackfillPaysLumpSumToday() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        e.settings.update { it.copy(rewardMode = RewardMode.ALL_DONE) }
        val first = e.habit(reward = 5, anchor = yesterday)
        val second = e.habit(reward = 7, anchor = yesterday)

        e.service.checkInWithBonus(first, onDay = yesterday)
        e.service.checkInWithBonus(second, onDay = yesterday)

        val earns = e.ledger.rows.value.filter { it.kind == LedgerKind.EARN_HABIT }
        assertEquals(1, earns.size)
        val earn = earns.single()
        assertEquals(12, earn.minutes)
        assertEquals(-(yesterday.toEpochDay() + 1), earn.sourceId)
        assertEquals(e.today.toEpochDay(), earn.periodKey)
        assertEquals(e.clock.now() + 24 * TimeBank.HOUR, earn.expiresAt)
        assertTrue(e.checkIns.rows.all { it.minutesEarned == 0 })
    }

    @Test fun allDoneBackfillPaysOnlyOnce() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        e.settings.update { it.copy(rewardMode = RewardMode.ALL_DONE) }
        val first = e.habit(reward = 5, anchor = yesterday)
        val second = e.habit(reward = 7, anchor = yesterday)

        e.service.checkInWithBonus(first, onDay = yesterday)
        e.service.checkInWithBonus(second, onDay = yesterday)
        e.clock.advanceMin(25 * 60)
        // An extra row in an already closed day must not re-pay, even from a new day.
        e.service.checkInWithBonus(first, onDay = yesterday)

        assertEquals(12, e.bank.netFor(OwnerKind.HABIT, -(yesterday.toEpochDay() + 1)))
        assertEquals(1, e.ledger.rows.value.count { it.kind == LedgerKind.EARN_HABIT })
    }

    @Test fun undoCompletingBackfillReversesTodaysLumpSum() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        e.settings.update { it.copy(rewardMode = RewardMode.ALL_DONE) }
        val first = e.habit(reward = 5, anchor = yesterday)
        val second = e.habit(reward = 7, anchor = yesterday)

        e.service.checkInWithBonus(first, onDay = yesterday)
        val completing = e.service.checkInWithBonus(second, onDay = yesterday)
        e.service.undoCheckIn(completing.checkIn.id)

        assertEquals(0, e.bank.netFor(OwnerKind.HABIT, -(yesterday.toEpochDay() + 1)))
        val undo = e.ledger.rows.value.single { it.kind == LedgerKind.UNDO }
        assertEquals(-12, undo.minutes)
        assertEquals(e.today.toEpochDay(), undo.periodKey)
    }

    @Test fun undoExtraBackfillKeepsLumpSum() = runTest {
        val e = Env()
        val yesterday = e.today.minusDays(1)
        e.settings.update { it.copy(rewardMode = RewardMode.ALL_DONE) }
        val first = e.habit(reward = 5, anchor = yesterday)
        val second = e.habit(reward = 7, anchor = yesterday)

        e.service.checkInWithBonus(first, onDay = yesterday)
        e.service.checkInWithBonus(second, onDay = yesterday)
        val overflow = e.service.checkInWithBonus(first, onDay = yesterday)
        e.service.undoCheckIn(overflow.checkIn.id)

        assertEquals(12, e.bank.netFor(OwnerKind.HABIT, -(yesterday.toEpochDay() + 1)))
        assertEquals(1, e.ledger.rows.value.count { it.kind == LedgerKind.EARN_HABIT })
        assertEquals(0, e.ledger.rows.value.count { it.kind == LedgerKind.UNDO })
    }
}
