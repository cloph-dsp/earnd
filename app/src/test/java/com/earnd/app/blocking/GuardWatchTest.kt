package com.earnd.app.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardWatchTest {
    @Test fun warnsOnlyWhenWantedButOff() {
        assertTrue(GuardWatch.shouldWarn(blockingEnabled = true, serviceEnabled = false))
        assertFalse(GuardWatch.shouldWarn(blockingEnabled = true, serviceEnabled = true))
        assertFalse(GuardWatch.shouldWarn(blockingEnabled = false, serviceEnabled = false))
    }
}
