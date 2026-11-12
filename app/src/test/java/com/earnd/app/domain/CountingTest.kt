package com.earnd.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingTest {
    // ---- zero prediction ----

    @Test fun zeroPredictionScalesWithRate() {
        assertEquals(10 * 60_000L, MeterMath.msUntilZero(10, 0, 100))
        assertEquals(20 * 60_000L, MeterMath.msUntilZero(10, 0, 50))
        assertEquals(40 * 60_000L, MeterMath.msUntilZero(10, 0, 25))
        assertNull(MeterMath.msUntilZero(10, 0, 0))
    }

    @Test fun zeroPredictionUsesCarry() {
        val carry = MeterMath.accrue(0, 30_000, 50).carry // 15 s charged
        assertEquals(2 * 60_000L - 30_000L, MeterMath.msUntilZero(1, carry, 50))
        assertEquals(0L, MeterMath.msUntilZero(0, 0, 100))
    }

    // ---- grants ----

    @Test fun grantKeepsTwoMinutesAfterLeaving() {
        val g = Grants()
        assertFalse(g.active("tidal", 0))
        g.grant("tidal", 0)
        g.enter("tidal")
        assertTrue(g.active("tidal", 10 * 60_000)) // in front: no run-down
        g.leave("tidal", 1_000_000)
        assertTrue(g.active("tidal", 1_000_000 + 119_000))
        assertTrue(g.active("tidal", 1_000_000 + 120_000))
        assertFalse(g.active("tidal", 1_000_000 + 120_001))
        assertFalse(g.active("tidal", 1_000_000 + 1)) // an expired grant is gone for good
    }

    @Test fun returningInsideTheWindowClearsTheClock() {
        val g = Grants()
        g.grant("a", 0)
        g.enter("a")
        g.leave("a", 500_000)
        assertTrue(g.active("a", 500_000 + 100_000))
        g.enter("a")
        g.leave("a", 900_000)
        assertTrue(g.active("a", 900_000 + 60_000))
        assertFalse(g.active("a", 900_000 + 130_000))
    }

    @Test fun grantsAreIndependentAndUnknownKeysAreInactive() {
        val g = Grants()
        g.grant("a", 0)
        assertFalse(g.active("b", 1))
        g.leave("b", 5) // unknown key: ignored
        assertFalse(g.active("b", 6))
        g.clear()
        assertFalse(g.active("a", 1))
    }

    // ---- call / screen ----

    @Test fun callStateAndAudioMode() {
        assertFalse(CountRules.inCall(0, 0))
        assertFalse(CountRules.inCall(null, null))
        assertTrue(CountRules.inCall(1, 0)) // ringing
        assertTrue(CountRules.inCall(2, null)) // offhook
        assertTrue(CountRules.inCall(null, CountRules.MODE_IN_CALL))
        assertTrue(CountRules.inCall(0, CountRules.MODE_IN_COMMUNICATION))
        assertFalse(CountRules.inCall(0, 1)) // ringtone mode alone is not a call
    }

    @Test fun countsOnlyWhenInteractiveUnlockedAndNotInCall() {
        assertTrue(CountRules.active(true, false, false))
        assertFalse(CountRules.active(false, false, false))
        assertFalse(CountRules.active(true, true, false))
        assertFalse(CountRules.active(true, false, true))
    }

    // ---- PiP / split-screen ----

    private val rules = mapOf("com.yt" to ("com.yt" to 50), "com.tidal" to ("com.tidal" to 100))
    private val rateOf: (String) -> Pair<String, Int>? = { rules[it] }

    @Test fun pipOfLeisureAppCountsAtItsRate() {
        val c = CountRules.visibleLeisure(listOf(WinInfo("com.launcher", false), WinInfo("com.yt", true)), rateOf)
        assertEquals(listOf(Counted("com.yt", "com.yt", 50, pip = true)), c)
        assertEquals("com.yt", CountRules.pick(null, c)?.pkg)
    }

    @Test fun systemWindowsNeverCount() {
        val c = CountRules.visibleLeisure(listOf(WinInfo("com.yt", false, application = false)), rateOf)
        assertTrue(c.isEmpty())
    }

    @Test fun splitScreenCountsAVisibleLeisureWindowAndPicksOneRate() {
        val primary = Counted("com.tidal", "com.tidal", 100)
        val extras = CountRules.visibleLeisure(
            listOf(WinInfo("com.tidal", false), WinInfo("com.yt", false), WinInfo("com.notes", false)), rateOf)
        assertEquals(2, extras.size)
        // one charge at a time: the stricter (higher) rate, not the sum
        assertEquals(100, CountRules.pick(primary, extras)?.ratePct)
        // a leisure window beside a non-leisure foreground app still counts
        assertEquals("com.yt", CountRules.pick(null, extras.filter { it.pkg == "com.yt" })?.pkg)
    }

    @Test fun tieGoesToThePrimaryAndNothingMeansNull() {
        val p = Counted("a", "a", 50)
        val e = Counted("b", "b", 50, pip = true)
        assertEquals("a", CountRules.pick(p, listOf(e))?.key)
        assertNull(CountRules.pick(null, emptyList()))
    }

    // ---- overlay maths ----

    @Test fun overlayFormatting() {
        assertEquals("23:41", OverlayMath.format(23 * 60_000L + 41_000))
        assertEquals("00:01", OverlayMath.format(1)) // rounds up to the second
        assertEquals("00:00", OverlayMath.format(-5))
        assertEquals("1:05:00", OverlayMath.format(65 * 60_000L))
        assertEquals("23:41 ½", OverlayMath.text(23 * 60_000L + 41_000, 50))
        assertEquals("23:41 ¾", OverlayMath.text(23 * 60_000L + 41_000, 75))
        // Continuous-use nudge: once per 20 minutes crossed, never on the first reading or after a switch.
        assertNull(OverlayMath.crossedStreak(null, 25 * 60_000L))
        assertNull(OverlayMath.crossedStreak(5 * 60_000L, 19 * 60_000L))
        assertEquals(20, OverlayMath.crossedStreak(19 * 60_000L, 20 * 60_000L + 1))
        assertNull(OverlayMath.crossedStreak(20 * 60_000L + 1, 21 * 60_000L))
        assertEquals(40, OverlayMath.crossedStreak(39 * 60_000L, 40 * 60_000L))
        assertEquals("10:00", OverlayMath.text(600_000, 100))
    }

    @Test fun overlayWarnings() {
        assertTrue(OverlayMath.isLow(120_000))
        assertFalse(OverlayMath.isLow(120_001))
        assertTrue(OverlayMath.crossedVibrate(61_000, 60_000))
        assertFalse(OverlayMath.crossedVibrate(60_000, 59_000)) // already past
        assertFalse(OverlayMath.crossedVibrate(null, 30_000))
    }

    @Test fun overlaySnapAndClamp() {
        assertTrue(OverlayMath.snapLeft(100, 1080))
        assertFalse(OverlayMath.snapLeft(700, 1080))
        assertEquals(0, OverlayMath.clampY(-30, 70, 2000))
        assertEquals(1930, OverlayMath.clampY(5000, 70, 2000))
    }

    // ---- engine ----

    @Test fun engineRemainingAndCurrent() = runTest {
        var carry = 0L
        var debited = 0
        val e = MeterEngine({ debited += it; it }, { carry }, { carry = it })
        e.syncBalance(10)
        assertNull(e.remainingMs(0))
        e.switchTo(MeterActive("k", "p", 50, 1_000), 1_000)
        assertEquals("p", e.current()?.pkg)
        assertEquals(20 * 60_000L, e.remainingMs(1_000))
        assertEquals(20 * 60_000L - 30_000L, e.remainingMs(31_000))
        assertEquals(0, e.advance(61_000)) // 30 s charged
        assertEquals(1, e.advance(121_000)) // 60 s charged in total
        assertEquals(1, debited)
        assertEquals(9, e.knownBalance())
        // switching to the same target keeps the original start
        e.switchTo(MeterActive("k", "p", 50, 200_000), 200_000)
        assertEquals(1_000L, e.current()?.since)
        e.switchTo(null, 200_000)
        assertNull(e.current())
    }
}
