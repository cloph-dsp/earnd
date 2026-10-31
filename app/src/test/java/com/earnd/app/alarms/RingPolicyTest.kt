package com.earnd.app.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingPolicyTest {
    @Test fun snoozeLimit() {
        assertEquals(2, RingPolicy.snoozesLeft(null, 0))
        assertEquals(1, RingPolicy.snoozesLeft(3, 2))
        assertEquals(0, RingPolicy.snoozesLeft(2, 5))
        assertTrue(RingPolicy.canSnooze(1))
        assertFalse(RingPolicy.canSnooze(0))
    }

    @Test fun timeoutDefaultsAndClamps() {
        assertEquals(5 * 60_000L, RingPolicy.timeoutMs(null))
        assertEquals(5 * 60_000L, RingPolicy.timeoutMs(0))
        assertEquals(10_000L, RingPolicy.timeoutMs(1))
        assertEquals(60 * 60_000L, RingPolicy.timeoutMs(Long.MAX_VALUE))
        assertEquals(30_000L, RingPolicy.timeoutMs(30_000L))
    }

    @Test fun timedOut() {
        assertFalse(RingPolicy.timedOut(1_000, 1_000 + 299_999, RingPolicy.DEFAULT_TIMEOUT_MS))
        assertTrue(RingPolicy.timedOut(1_000, 1_000 + 300_000, RingPolicy.DEFAULT_TIMEOUT_MS))
    }
}
