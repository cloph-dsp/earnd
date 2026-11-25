package com.earnd.app.alarms

import com.earnd.app.data.entity.NagMode
import com.earnd.app.domain.DailyWindow
import com.earnd.app.domain.ForegroundRules
import com.earnd.app.utc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class NagDecisionTest {
    private val z = ZoneOffset.UTC
    private val quiet = DailyWindow(0, 8 * 60)
    private val now = utc(LocalDateTime.of(2026, 3, 10, 12, 0))
    private val weekOld = now - 9L * 24 * 60 * 60_000

    private fun signals(due: Long = now - 30 * 60_000L, trigger: NagTrigger = NagTrigger.TIMER) = NagSignals(
        now = now, dueAt = due, zone = z, nagCount = 0, lastNagAt = null, nextNagAt = now,
        mode = NagMode.PROGRESSIVE, maxCount = null, quietActive = false, quietEndsAt = null,
        trigger = trigger, foregroundKind = ForegroundRules.Kind.APP, interactive = true, inCall = false,
        focusRunning = false, selfForeground = false, balanceZero = false, nagsToday = 0, capPerDay = null,
        onVacation = false, progressedSinceLastNag = false, quietWindow = quiet,
    )

    @Test fun timerAtDueTimePromptsFirstVariant() {
        assertEquals(NagDecision.Prompt(0), decide(signals(due = now)))
        assertEquals(NagDecision.Prompt(0), decide(signals(due = now + 5 * 60_000L)))
    }

    @Test fun quietDefersPastQuietEnd() {
        val d = decide(signals().copy(quietActive = true, quietEndsAt = now + 60 * 60_000L))
        assertTrue(d is NagDecision.Defer)
        assertTrue((d as NagDecision.Defer).until > now)
        assertEquals(now + 60 * 60_000L, d.until)
    }

    @Test fun offVacationAndMaxAreTerminal() {
        assertEquals(NagDecision.None, decide(signals().copy(mode = NagMode.OFF)))
        assertEquals(NagDecision.None, decide(signals().copy(nagCount = 3, maxCount = 3)))
        val away = decide(signals().copy(onVacation = true))
        assertTrue(away is NagDecision.Defer)
        assertTrue((away as NagDecision.Defer).until > now)
    }

    @Test fun unreceptiveStatesDeferWithoutBumping() {
        val cases = listOf(
            signals().copy(foregroundKind = ForegroundRules.Kind.COVERING),
            signals().copy(interactive = false),
            signals().copy(inCall = true),
        )
        for (s in cases) {
            val d = decide(s)
            assertTrue(d is NagDecision.Defer)
            assertTrue((d as NagDecision.Defer).until > s.now)
        }
    }

    @Test fun dailyCapReschedulesToNextDaySlot() {
        assertEquals(NagDecision.CapReached, decide(signals().copy(capPerDay = 3, nagsToday = 3)))
        val slot = NagSchedule.nextDigestSlot(now + 1, z, { quiet }, onlyAfterLast = now)
        assertTrue(slot > now)
        assertEquals(LocalDate.of(2026, 3, 11), Instant.ofEpochMilli(slot).atZone(z).toLocalDate())
    }

    @Test fun selfFocusAndProgressStaySilent() {
        assertEquals(NagDecision.None, decide(signals().copy(selfForeground = true)))
        assertEquals(NagDecision.None, decide(signals().copy(focusRunning = true)))
        assertEquals(NagDecision.None, decide(signals().copy(progressedSinceLastNag = true)))
    }

    @Test fun digestWithZeroBalanceNeverPromptsOnOrdinaryTriggers() {
        val d = decide(signals(due = weekOld).copy(balanceZero = true))
        assertTrue(d is NagDecision.Defer)
        // Balance and block-end events are the two triggers allowed through a zero balance.
        assertTrue(decide(signals(due = weekOld, trigger = NagTrigger.BALANCE_ZERO).copy(balanceZero = true)) is NagDecision.Prompt)
        assertTrue(decide(signals(due = weekOld, trigger = NagTrigger.BLOCK_END).copy(balanceZero = true)) is NagDecision.Prompt)
    }
}
