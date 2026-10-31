package com.earnd.app.focus

import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLogicTest {
    private val start = 1_000_000L
    private val twenty = FocusSessionEntity(habitId = 7, startedAt = start, durationMs = 20 * 60_000L)

    @Test fun remainingCountsDown() {
        assertEquals(20 * 60_000L, FocusMath.remainingMs(twenty, start))
        assertEquals(8 * 60_000L, FocusMath.remainingMs(twenty, start + 12 * 60_000L))
        assertEquals(0L, FocusMath.remainingMs(twenty, start + 25 * 60_000L))
    }

    @Test fun clockIsBeforeStartCapped() = assertEquals(20 * 60_000L, FocusMath.remainingMs(twenty, start - 5_000))

    @Test fun dueOnlyAtTheEndWhileActive() {
        assertFalse(FocusMath.isDue(twenty, start + 20 * 60_000L - 1))
        assertTrue(FocusMath.isDue(twenty, start + 20 * 60_000L))
        assertFalse(FocusMath.isDue(twenty.copy(state = FocusState.CANCELLED), start + 30 * 60_000L))
        assertFalse(FocusMath.isDue(twenty.copy(state = FocusState.DONE), start + 30 * 60_000L))
    }

    @Test fun runningStopsAtEndOrOnCancel() {
        assertTrue(FocusMath.isRunning(twenty, start + 60_000))
        assertFalse(FocusMath.isRunning(twenty, start + 20 * 60_000L))
        assertFalse(FocusMath.isRunning(twenty.copy(state = FocusState.CANCELLED), start + 60_000))
        assertFalse(FocusMath.isRunning(null, start))
    }

    @Test fun cancelDiscardsProgress() {
        val at = start + 12 * 60_000L + 30_000
        assertEquals(12, FocusMath.minutesLost(twenty, at))
        val cancelled = twenty.copy(state = FocusState.CANCELLED)
        assertEquals(0L, FocusMath.remainingMs(cancelled, at))
        assertFalse(FocusMath.isDue(cancelled, start + 21 * 60_000L)) // never credited later
    }

    @Test fun progressAndClock() {
        assertEquals(0.5f, FocusMath.progress(twenty, start + 10 * 60_000L), 0.001f)
        assertEquals("12:34", FocusMath.clock((12 * 60 + 34) * 1000L))
        assertEquals("00:01", FocusMath.clock(1))
        assertEquals("1:00:00", FocusMath.clock(3_600_000L))
    }

    @Test fun focusPolicyAllowsOnlySafeApps() {
        fun ok(pkg: String, essential: Boolean = false, launcher: Boolean = false, user: Boolean = false) =
            FocusRules.allowed(pkg, "com.earnd.app", essential, launcher, user)
        assertTrue(ok("com.earnd.app"))
        assertTrue(ok("com.google.android.dialer", essential = true))
        assertTrue(ok("com.android.systemui", essential = true))
        assertTrue(ok("org.notes", user = true))
        assertFalse(ok("com.google.android.apps.nexuslauncher", essential = true, launcher = true))
        assertFalse(ok("com.android.settings", essential = true))
        assertFalse(ok("com.example.mail"))
        assertFalse(ok("com.youtube"))
    }
}
