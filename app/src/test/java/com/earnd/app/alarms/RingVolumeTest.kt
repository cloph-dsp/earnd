package com.earnd.app.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingVolumeTest {
    @Test fun durationResolution() {
        assertEquals(60_000L, RingPolicy.effectiveRingMs(null, 60))
        assertEquals(2_000L, RingPolicy.effectiveRingMs(2, 60))
        assertEquals(300_000L, RingPolicy.effectiveRingMs(null, 300))
        assertEquals(1_000L, RingPolicy.effectiveRingMs(0, 60))
        assertEquals(600_000L, RingPolicy.effectiveRingMs(9999, 60))
    }

    @Test fun volumeResolution() {
        assertEquals(0.6f, RingPolicy.effectiveVolume(null, 60), 1e-6f)
        assertEquals(1f, RingPolicy.effectiveVolume(100, 60), 1e-6f)
        assertEquals(0.1f, RingPolicy.effectiveVolume(1, 60), 1e-6f)
        assertEquals(1f, RingPolicy.effectiveVolume(null, 500), 1e-6f)
    }

    @Test fun shortRingsSkipRamp() {
        assertTrue(RingPolicy.isShort(10_000))
        assertFalse(RingPolicy.isShort(10_001))
        assertFalse(RingPolicy.usesRamp(true, 10_000))
        assertTrue(RingPolicy.usesRamp(true, 30_000))
        assertFalse(RingPolicy.usesRamp(false, 30_000))
    }

    @Test fun rampCurve() {
        assertEquals(0.2f, RingPolicy.rampVolume(1f, 0, true), 1e-6f)
        assertEquals(0.6f, RingPolicy.rampVolume(1f, 10_000, true), 1e-6f)
        assertEquals(1f, RingPolicy.rampVolume(1f, 20_000, true), 1e-6f)
        assertEquals(1f, RingPolicy.rampVolume(1f, 99_000, true), 1e-6f)
        assertEquals(0.1f, RingPolicy.rampVolume(0.5f, 0, true), 1e-6f)
        assertEquals(0.5f, RingPolicy.rampVolume(0.5f, 0, false), 1e-6f)
    }
}
