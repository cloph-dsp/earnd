package com.earnd.app.ui

import com.earnd.app.ui.stats.UsageEvent
import com.earnd.app.ui.stats.UsageMath
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageMathTest {
    private val h = 3_600_000L
    private val m = 60_000L

    @Test fun sumsPerAppAndSplitsAcrossHours() {
        val ev = listOf(
            UsageEvent("a", 9 * h + 50 * m, true),
            UsageEvent("a", 10 * h + 20 * m, false),
            UsageEvent("b", 11 * h, true),
            UsageEvent("b", 11 * h + 5 * m, false),
        )
        val s = UsageMath.summarize(ev, 0, 12 * h)
        assertEquals(listOf("a" to 30 * m, "b" to 5 * m), s.perApp)
        assertEquals(10 * m, s.perHour[9])
        assertEquals(20 * m, s.perHour[10])
        assertEquals(5 * m, s.perHour[11])
    }

    @Test fun switchingAppsClosesPreviousAndOpenCountsToNow() {
        val ev = listOf(UsageEvent("a", 0, true), UsageEvent("b", 10 * m, true))
        val s = UsageMath.summarize(ev, 0, 30 * m)
        assertEquals(10 * m, s.perApp.toMap()["a"])
        assertEquals(20 * m, s.perApp.toMap()["b"])
    }

    @Test fun excludedAndBeforeDayStartAreClipped() {
        val ev = listOf(UsageEvent("x", -10 * m, true), UsageEvent("x", 5 * m, false), UsageEvent("off", 6 * m, true))
        val s = UsageMath.summarize(ev, 0, h, exclude = setOf("off"))
        assertEquals(listOf("x" to 5 * m), s.perApp)
        assertEquals(5 * m, s.totalMs)
    }
}
