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
import com.earnd.app.data.entity.OwnerKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** StateFlow-backed fakes: observeDay must emit on check-in, undo and habit edits. */
private class FlowCheckInDao : CheckInDao {
    val rows = MutableStateFlow<List<CheckInEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForDay(epochDay: Long) = rows.map { list ->
        list.filter { it.epochDay == epochDay }.sortedBy { it.at }
    }
    override suspend fun forDay(epochDay: Long) = rows.value.filter { it.epochDay == epochDay }
    override suspend fun forHabitBetween(habitId: Long, fromDay: Long, toDay: Long) =
        rows.value.filter { it.habitId == habitId && it.epochDay in fromDay..toDay }
    override suspend fun countFor(habitId: Long, epochDay: Long) =
        rows.value.count { it.habitId == habitId && it.epochDay == epochDay }
    override suspend fun between(fromDay: Long, toDay: Long) = rows.value.filter { it.epochDay in fromDay..toDay }
    override suspend fun forHabitDay(habitId: Long, epochDay: Long) =
        rows.value.filter { it.habitId == habitId && it.epochDay == epochDay }
    override suspend fun insert(c: CheckInEntity): Long {
        val n = c.copy(id = nextId++)
        rows.value = rows.value + n
        return n.id
    }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun deleteById(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
}

private class FlowHabitDao : HabitDao {
    val rows = MutableStateFlow<List<HabitEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActive() = rows.map { list -> list.filterNot { it.archived } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun all() = rows.value.toList()
    override suspend fun active() = rows.value.filterNot { it.archived }
    override suspend fun upsert(h: HabitEntity): Long {
        if (h.id != 0L) {
            rows.value = rows.value.map { if (it.id == h.id) h else it }
            return h.id
        }
        val n = h.copy(id = nextId++)
        rows.value = rows.value + n
        return n.id
    }
    override suspend fun archive(id: Long) { rows.value = rows.value.map { if (it.id == id) it.copy(archived = true) else it } }
    override fun observeArchived() = rows.map { list -> list.filter { it.archived } }
    override suspend fun unarchive(id: Long) { rows.value = rows.value.map { if (it.id == id) it.copy(archived = false) else it } }
    override suspend fun setSortOrder(id: Long, sortOrder: Int) {
        rows.value = rows.value.map { if (it.id == id) it.copy(sortOrder = sortOrder) else it }
    }
    override suspend fun nextSortOrder() = rows.value.size
    override suspend fun delete(h: HabitEntity) { rows.value = rows.value.filterNot { it.id == h.id } }
}

private class Env {
    val today = LocalDate.of(2026, 3, 4)
    val yesterday = today.minusDays(1)
    val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
    val habits = FlowHabitDao()
    val checkIns = FlowCheckInDao()
    val ledger = FakeLedgerDao()
    val settings = SettingsRepository(FakeSettingDao(), clock, ChangeBus())
    val tx = MutexTransactor()
    val bank = TimeBank(ledger, FakeSessionDao(), settings, clock, ChangeBus(), tx)
    val service = HabitService(habits, checkIns, bank, clock, ChangeBus(), settings, tx)

    suspend fun habit(reward: Int = 5, anchor: LocalDate = yesterday): Long =
        habits.upsert(HabitEntity(name = "Meds", iconKey = "check", colorArgb = 0,
            anchorEpochDay = anchor.toEpochDay(), rewardMinutes = reward, createdAt = clock.now()))
}

class DayObservationTest {
    @Test fun observeDayFollowsCheckInThenUndoOnYesterday() = runTest {
        val e = Env()
        val id = e.habit()
        val seen = mutableListOf<Boolean>()
        val job = launch { e.service.observeDay(e.yesterday).collect { seen += it.single { d -> d.habit.id == id }.complete } }
        runCurrent()
        assertEquals(listOf(false), seen)

        e.service.checkIn(id, onDay = e.yesterday)
        runCurrent()
        assertEquals(listOf(false, true), seen)

        e.service.undoCheckIn(e.checkIns.rows.value.single().id)
        runCurrent()
        assertEquals(listOf(false, true, false), seen)
        job.cancel()
    }

    @Test fun undoYesterdayRowAtNowRemovesItAndRestoresNetZero() = runTest {
        val e = Env()
        val id = e.habit()
        val saved = e.service.checkIn(id, onDay = e.yesterday)
        assertEquals(e.yesterday.toEpochDay(), saved.epochDay)
        assertEquals(e.clock.now(), saved.at)
        assertEquals(5, e.bank.balance().minutes)
        assertEquals(1, e.checkIns.rows.value.size)

        e.service.undoCheckIn(saved.id)

        assertFalse(e.checkIns.rows.value.any { it.id == saved.id })
        assertTrue(e.checkIns.rows.value.isEmpty())
        assertEquals(0, e.bank.balance().minutes)
        assertEquals(0, e.bank.netFor(OwnerKind.HABIT, saved.id))
    }

    @Test fun observeDayReemitsWhenAnActiveHabitIsEdited() = runTest {
        val e = Env()
        e.habit()
        val seen = mutableListOf<String>()
        val job = launch { e.service.observeDay(e.yesterday).collect { seen += it.single().habit.name } }
        runCurrent()
        assertEquals(listOf("Meds"), seen)

        e.habits.rows.value = listOf(e.habits.rows.value.single().copy(name = "Meds PM"))
        runCurrent()
        assertEquals(listOf("Meds", "Meds PM"), seen)
        job.cancel()
    }
}
