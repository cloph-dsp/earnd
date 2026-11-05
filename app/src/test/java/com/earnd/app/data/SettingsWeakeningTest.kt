package com.earnd.app.data

import com.earnd.app.FakeClock
import com.earnd.app.FakeSettingDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SettingsWeakeningTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 3, 4, 10, 0))
    private val repo = SettingsRepository(FakeSettingDao(), clock)

    private suspend fun hard() = repo.update { it.copy(hardMode = true, weakenCooldownMin = 30, unlockCapMin = 15) }

    @Test fun aQueuedChangeDoesNotUndoTighteningMadeMeanwhile() = runTest {
        hard()
        val at = repo.requestWeakening { it.copy(unlockCapMin = null) }
        repo.update { it.copy(defaultHabitRewardMin = 1) } // tightened while waiting
        clock.nowMs = at + 1
        val s = repo.current()
        assertNull(s.unlockCapMin)              // the queued change applied
        assertEquals(1, s.defaultHabitRewardMin) // and the later change survived
    }

    @Test fun twoQueuedChangesBothApplyTogether() = runTest {
        hard()
        repo.requestWeakening { it.copy(unlockCapMin = null) }
        clock.nowMs += 60_000
        val at = repo.requestWeakening { it.copy(defaultHabitRewardMin = 9) }
        clock.nowMs = at + 1
        val s = repo.current()
        assertNull(s.unlockCapMin)
        assertEquals(9, s.defaultHabitRewardMin)
    }

    @Test fun cancellingForgetsTheQueuedChange() = runTest {
        hard()
        val at = repo.requestWeakening { it.copy(unlockCapMin = null) }
        repo.cancelPendingWeakening()
        clock.nowMs = at + 1
        assertEquals(15, repo.current().unlockCapMin)
    }

    @Test fun aQueueFromTheOldFullSnapshotFormatIsDroppedNotApplied() = runTest {
        val dao = FakeSettingDao()
        val legacy = SettingsRepository(dao, clock)
        legacy.update { it.copy(hardMode = true, unlockCapMin = 15) }
        dao.put(com.earnd.app.data.entity.SettingEntity("pending.unlockCapMin", ""))
        dao.put(com.earnd.app.data.entity.SettingEntity("pendingAt", (clock.nowMs - 1).toString()))
        assertNull(legacy.pendingWeakeningAt())
        assertEquals(15, legacy.current().unlockCapMin)
    }
}
