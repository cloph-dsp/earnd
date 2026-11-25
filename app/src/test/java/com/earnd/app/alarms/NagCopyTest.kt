package com.earnd.app.alarms

import com.earnd.app.i18n.TestStr
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class NagCopyTest {
    private val day = 20_000L

    @Before fun strings() = TestStr.install()

    @Test fun variantsRotateAcrossDaysAndCounts() {
        assertNotEquals(NagCopy.pendingLine(day, 0), NagCopy.pendingLine(day + 1, 0))
        assertNotEquals(NagCopy.pendingLine(day, 0), NagCopy.pendingLine(day, 1))
        assertNotEquals(NagCopy.upcomingLine(day, 0), NagCopy.upcomingLine(day + 1, 0))
    }

    @Test fun linesCarryNoCountOrBlame() {
        val lines = (0 until 6).flatMap { i ->
            listOf(NagCopy.pendingLine(day + i, i), NagCopy.upcomingLine(day + i, i))
        } + NagCopy.capReachedLine
        for (line in lines) {
            assertFalse(line.contains("ignored", ignoreCase = true))
            assertFalse(line.contains("missed", ignoreCase = true))
            assertFalse(line.any(Char::isDigit))
        }
    }
}
