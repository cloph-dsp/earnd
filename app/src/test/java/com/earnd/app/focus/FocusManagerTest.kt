package com.earnd.app.focus

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.FocusDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.dao.TaskDao
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.ChangeBus
import com.earnd.app.domain.CheckInResult
import com.earnd.app.domain.HabitService
import com.earnd.app.domain.TaskService
import com.earnd.app.domain.today
import com.earnd.app.domain.TimeBank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FakeFocusDao : FocusDao {
    var row: FocusSessionEntity? = null
    override fun observe(): Flow<FocusSessionEntity?> = flowOf(row)
    override suspend fun get(): FocusSessionEntity? = row
    override suspend fun put(s: FocusSessionEntity) { row = s }
    override suspend fun finish(startedAt: Long, to: FocusState): Int {
        val r = row
        return if (r != null && r.state == FocusState.ACTIVE && r.startedAt == startedAt) {
            row = r.copy(state = to); 1
        } else 0
    }
    override suspend fun finishStopwatch(startedAt: Long, elapsedMs: Long): Int {
        val r = row
        return if (r != null && r.state == FocusState.ACTIVE && r.startedAt == startedAt) {
            row = r.copy(state = FocusState.DONE, durationMs = elapsedMs); 1
        } else 0
    }
}

class FakeHabitDao : HabitDao {
    val rows = mutableMapOf<Long, HabitEntity>()
    private var next = 1L
    override fun observeActive(): Flow<List<HabitEntity>> = flowOf(rows.values.toList())
    override suspend fun get(id: Long): HabitEntity? = rows[id]
    override suspend fun all(): List<HabitEntity> = rows.values.toList()
    override suspend fun active(): List<HabitEntity> = rows.values.filter { !it.archived }.toList()
    override suspend fun upsert(h: HabitEntity): Long {
        val id = if (h.id == 0L) next++ else h.id
        rows[id] = h.copy(id = id)
        return id
    }
    override suspend fun archive(id: Long) { rows[id] = rows[id]!!.copy(archived = true) }
    override fun observeArchived(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun unarchive(id: Long) {}
    override suspend fun setSortOrder(id: Long, sortOrder: Int) {}
    override suspend fun nextSortOrder(): Int = rows.size
    override suspend fun delete(h: HabitEntity) { rows.remove(h.id) }
}

class FakeTaskDao : TaskDao {
    val rows = mutableMapOf<Long, TaskEntity>()
    private var next = 1L
    override fun observeOpen(): Flow<List<TaskEntity>> = flowOf(rows.values.filter { it.completedAt == null && it.parentId == null }.toList())
    override fun observeCompleted(limit: Int): Flow<List<TaskEntity>> = flowOf(emptyList())
    override fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>> = flowOf(emptyList())
    override suspend fun subtasks(parentId: Long): List<TaskEntity> = emptyList()
    override suspend fun get(id: Long): TaskEntity? = rows[id]
    override suspend fun open(): List<TaskEntity> = rows.values.filter { it.completedAt == null && it.parentId == null }.toList()
    override suspend fun openRecurringDueBefore(epochDay: Long): List<TaskEntity> = emptyList()
    override suspend fun nextOpenInSeries(seriesId: Long, afterDay: Long): TaskEntity? = null
    override suspend fun upsert(t: TaskEntity): Long {
        val id = if (t.id == 0L) next++ else t.id
        rows[id] = t.copy(id = id)
        return id
    }
    override suspend fun deleteById(id: Long) { rows.remove(id) }
}

class FakeCheckInDao : CheckInDao {
    override fun observeForDay(epochDay: Long): Flow<List<CheckInEntity>> = flowOf(emptyList())
    override suspend fun forDay(epochDay: Long): List<CheckInEntity> = emptyList()
    override suspend fun forHabitBetween(habitId: Long, fromDay: Long, toDay: Long): List<CheckInEntity> = emptyList()
    override suspend fun countFor(habitId: Long, epochDay: Long): Int = 0
    override suspend fun between(fromDay: Long, toDay: Long): List<CheckInEntity> = emptyList()
    override suspend fun forHabitDay(habitId: Long, epochDay: Long): List<CheckInEntity> = emptyList()
    override suspend fun insert(c: CheckInEntity): Long = 1
    override suspend fun get(id: Long): CheckInEntity? = null
    override suspend fun deleteById(id: Long) {}
}

class FocusManagerTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
    private val settings = SettingsRepository(FakeSettingDao(), clock)
    private val ledger = FakeLedgerDao()
    private val sessions = FakeSessionDao()
    private val bus = ChangeBus()
    private val bank = TimeBank(ledger, sessions, settings, clock, bus)
    private val focusDao = FakeFocusDao()
    private val habitDao = FakeHabitDao()
    private val taskDao = FakeTaskDao()

    private val habits = object : HabitService(habitDao, FakeCheckInDao(), bank, clock, ChangeBus(), settings) {
        val calls = mutableListOf<Pair<Long, Int?>>()
        override suspend fun checkInWithBonus(habitId: Long, quantity: Double?, photoPath: String?, creditBank: Boolean, rewardOverride: Int?, onDay: java.time.LocalDate?): CheckInResult {
            calls += habitId to rewardOverride
            return CheckInResult(
                CheckInEntity(habitId = habitId, epochDay = 0, at = clock.now(), minutesEarned = rewardOverride ?: 0),
                0,
            )
        }
    }

    private val tasks = object : TaskService(taskDao, bank, settings, clock, ChangeBus()) {
        val focusEarnCalls = mutableListOf<Pair<Long, Int>>()
        val completeCalls = mutableListOf<Long>()
        override suspend fun focusEarn(taskId: Long, reward: Int): Long {
            focusEarnCalls += taskId to reward
            return 0L
        }
        override suspend fun complete(taskId: Long, creditBank: Boolean): TaskEntity? {
            completeCalls += taskId
            return null
        }
    }

    private val fm = FocusManager(
        context = null,
        dao = focusDao,
        habitDao = habitDao,
        habits = habits,
        clock = clock,
        bus = bus,
        settings = settings,
        taskDao = taskDao,
        tasks = tasks,
    )

    @Test
    fun startTaskArmsRow() = runTest {
        val t = TaskEntity(title = "Write", rewardMinutes = 7, focusMinutes = 20, createdAt = clock.now())
        val id = taskDao.upsert(t)
        val r = fm.startTask(id, 20)
        assertTrue(r is FocusStart.Started)
        val s = focusDao.get()!!
        assertEquals(0L, s.habitId)
        assertEquals(id, s.taskId)
        assertEquals(20 * 60_000L, s.durationMs)
        assertEquals(7, s.rewardMinutes)
    }

    @Test
    fun completeIfDueTaskPaysFocusEarnWithoutCompleting() = runTest {
        val t = TaskEntity(title = "Write", rewardMinutes = 7, focusMinutes = 20, createdAt = clock.now())
        val id = taskDao.upsert(t)
        fm.startTask(id, 20)
        // make due
        clock.nowMs += 20 * 60_000L
        val finished = fm.completeIfDue()
        assertTrue(finished)
        assertEquals(listOf(id to 7), tasks.focusEarnCalls)
        assertTrue(tasks.completeCalls.isEmpty())
        // task not completed
        assertNull(taskDao.get(id)!!.completedAt)
    }

    @Test
    fun habitFlowStillPaysCheckInWithBonus() = runTest {
        val h = HabitEntity(name = "Read", iconKey = "book", colorArgb = 0, anchorEpochDay = clock.today().toEpochDay(), rewardMinutes = 5, createdAt = clock.now(), focusMinutes = 10)
        val hid = habitDao.upsert(h)
        fm.start(hid, 10)
        clock.nowMs += 10 * 60_000L
        val finished = fm.completeIfDue()
        assertTrue(finished)
        assertEquals(listOf(hid to (focusDao.get()?.rewardMinutes)), habits.calls.map { it.first to it.second })
    }
}