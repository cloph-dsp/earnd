package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.dao.TaskDao
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime

/** Two callers (UI, alarm, widget, remote) can complete the same task or habit at once; it must pay once. */
class DomainConcurrencyTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))

    private class SlowTaskDao : TaskDao {
        val rows = mutableListOf<TaskEntity>()
        private var nextId = 1L
        private val lock = Any()

        override fun observeOpen() = kotlinx.coroutines.flow.flowOf(emptyList<TaskEntity>())
        override fun observeCompleted(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<TaskEntity>())
        override fun observeSubtasks(parentId: Long) = kotlinx.coroutines.flow.flowOf(emptyList<TaskEntity>())
        override suspend fun subtasks(parentId: Long): List<TaskEntity> {
            yield()
            return synchronized(lock) { rows.filter { it.parentId == parentId } }
        }
        override suspend fun get(id: Long): TaskEntity? {
            yield()
            return synchronized(lock) { rows.firstOrNull { it.id == id } }
        }
        override suspend fun open() = synchronized(lock) { rows.filter { it.parentId == null && it.completedAt == null } }
        override suspend fun openRecurringDueBefore(epochDay: Long) = emptyList<TaskEntity>()
        override suspend fun nextOpenInSeries(seriesId: Long, afterDay: Long): TaskEntity? = null
        override suspend fun upsert(t: TaskEntity): Long {
            yield()
            return synchronized(lock) {
                if (t.id == 0L) {
                    val n = t.copy(id = nextId++)
                    rows += n
                    n.id
                } else {
                    rows.replaceAll { if (it.id == t.id) t else it }
                    t.id
                }
            }
        }
        override suspend fun deleteById(id: Long) {
            synchronized(lock) { rows.removeAll { it.id == id } }
        }
    }

    private class SlowHabitDao : HabitDao {
        val rows = mutableListOf<HabitEntity>()
        private var nextId = 1L
        private val lock = Any()

        override fun observeActive() = kotlinx.coroutines.flow.flowOf(emptyList<HabitEntity>())
        override suspend fun get(id: Long): HabitEntity? = synchronized(lock) { rows.firstOrNull { it.id == id } }
        override suspend fun all() = synchronized(lock) { rows.toList() }
        override suspend fun active() = synchronized(lock) { rows.filterNot { it.archived } }
        override suspend fun upsert(h: HabitEntity): Long = synchronized(lock) {
            if (h.id == 0L) {
                val n = h.copy(id = nextId++)
                rows += n
                n.id
            } else {
                rows.replaceAll { if (it.id == h.id) h else it }
                h.id
            }
        }
        override suspend fun archive(id: Long) {
            synchronized(lock) { rows.replaceAll { if (it.id == id) it.copy(archived = true) else it } }
        }
        override fun observeArchived() = kotlinx.coroutines.flow.flowOf(emptyList<HabitEntity>())
        override suspend fun unarchive(id: Long) {
            synchronized(lock) { rows.replaceAll { if (it.id == id) it.copy(archived = false) else it } }
        }
        override suspend fun setSortOrder(id: Long, sortOrder: Int) {
            synchronized(lock) { rows.replaceAll { if (it.id == id) it.copy(sortOrder = sortOrder) else it } }
        }
        override suspend fun nextSortOrder() = synchronized(lock) { rows.size }
        override suspend fun delete(h: HabitEntity) {
            synchronized(lock) { rows.removeAll { it.id == h.id } }
        }
    }

    private class SlowCheckInDao : CheckInDao {
        val rows = mutableListOf<CheckInEntity>()
        private var nextId = 1L
        private val lock = Any()

        override fun observeForDay(epochDay: Long) = kotlinx.coroutines.flow.flowOf(emptyList<CheckInEntity>())
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
        override suspend fun deleteById(id: Long) {
            synchronized(lock) { rows.removeAll { it.id == id } }
        }

        private suspend fun query(p: (CheckInEntity) -> Boolean): List<CheckInEntity> {
            yield()
            return synchronized(lock) { rows.filter(p) }
        }
    }

    private fun settings() = SettingsRepository(FakeSettingDao(), clock)

    @Test fun concurrentTaskCompletionPaysOnce() = runBlocking {
        val tasks = SlowTaskDao()
        val ledger = FakeLedgerDao()
        val tx = MutexTransactor()
        val s = settings()
        val bank = TimeBank(ledger, FakeSessionDao(), s, clock, ChangeBus(), tx)
        val id = tasks.upsert(TaskEntity(title = "Passport", rewardMinutes = 20, createdAt = clock.now()))
        val svc = TaskService(tasks, bank, s, clock, ChangeBus(), tx)

        withContext(Dispatchers.Default) {
            (1..10).map { async { svc.complete(id) } }.awaitAll()
        }

        assertNotNull(tasks.rows.single().completedAt)
        assertEquals(20, ledger.rows.value.filter { it.kind == LedgerKind.EARN_TASK }.sumOf { it.minutes })
    }

    @Test fun concurrentCheckInsShareTheDailyReward() = runBlocking {
        val habits = SlowHabitDao()
        val checkIns = SlowCheckInDao()
        val ledger = FakeLedgerDao()
        val tx = MutexTransactor()
        val s = settings()
        s.update { it.copy(comebackBonus = false) }
        val bank = TimeBank(ledger, FakeSessionDao(), s, clock, ChangeBus(), tx)
        val id = habits.upsert(HabitEntity(name = "Gym", iconKey = "check", colorArgb = 0,
            anchorEpochDay = clock.today().toEpochDay(), rewardMinutes = 10, createdAt = clock.now()))
        val svc = HabitService(habits, checkIns, bank, clock, ChangeBus(), s, tx)

        withContext(Dispatchers.Default) {
            (1..5).map { async { svc.checkIn(id) } }.awaitAll()
        }

        // 10 for the target, then the +50% overflow cap; a race would pay 10 twice.
        assertEquals(15, ledger.rows.value.filter { it.kind == LedgerKind.EARN_HABIT }.sumOf { it.minutes })
    }
}
