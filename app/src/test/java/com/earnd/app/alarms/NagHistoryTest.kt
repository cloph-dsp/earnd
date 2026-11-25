package com.earnd.app.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NagHistoryTest {
    @Test fun roundTripCountsAndPercentage() {
        val day = 20_000L
        var history = NagHistory.recordPrompt(null, day)
        history = NagHistory.recordPrompt(history, day)
        history = NagHistory.recordResponse(history, day)
        assertEquals("20000:2:1", history)
        assertEquals(50, NagHistory.rateFor(history, listOf(day))[day])
    }

    @Test fun prunesToSevenDaysAndMissingDaysReadZero() {
        var history = ""
        for (day in 0L..7L) history = NagHistory.recordPrompt(history, day)
        val lines = history.lines()
        assertEquals(7, lines.size)
        assertEquals("1", lines.first().substringBefore(':'))
        assertEquals("7", lines.last().substringBefore(':'))
        val rates = NagHistory.rateFor(history, listOf(0L, 3L, 8L))
        assertEquals(0, rates[0L])
        assertEquals(0, rates[3L])
        assertEquals(0, rates[8L])
    }

    @Test fun corruptHistoryNeverThrows() {
        val corrupt = "garbage\n1:x:2\n2:3\n3:4:5\n\n4:2:1"
        assertEquals("4:2:2", NagHistory.recordResponse(corrupt, 4L))
        val rates = NagHistory.rateFor(corrupt, listOf(1L, 3L, 4L))
        assertEquals(0, rates[1L])
        assertEquals(0, rates[3L])
        assertEquals(50, rates[4L])
    }

    @Test fun responsesNeverExceedPrompts() {
        val day = 20_000L
        var history = NagHistory.recordPrompt(null, day)
        history = NagHistory.recordResponse(history, day)
        history = NagHistory.recordResponse(history, day)
        assertEquals("20000:1:1", history)
        assertEquals(100, NagHistory.rateFor(history, listOf(day))[day])
        for (rate in NagHistory.rateFor(history, listOf(day, day + 1)).values) {
            assertTrue(rate in 0..100)
        }
    }
}
