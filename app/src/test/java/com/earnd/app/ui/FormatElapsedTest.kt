package com.earnd.app.ui

import com.earnd.app.ui.common.formatElapsed
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTest {
    @Test fun zeroAndNegative() {
        assertEquals("0m", formatElapsed(0))
        assertEquals("0m", formatElapsed(-1))
        assertEquals("0m", formatElapsed(-60_000))
    }

    @Test fun subMinuteRoundsUp() {
        assertEquals("1m", formatElapsed(1))
        assertEquals("1m", formatElapsed(59_999))
        assertEquals("1m", formatElapsed(60_000))
    }

    @Test fun twelveMinutes() = assertEquals("12m", formatElapsed(12 * 60_000L))

    @Test fun hoursWithMinutes() {
        assertEquals("1h 0m", formatElapsed(60 * 60_000L))
        assertEquals("3h 12m", formatElapsed((3 * 60 + 12) * 60_000L))
    }
}
