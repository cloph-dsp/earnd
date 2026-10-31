package com.earnd.app.domain

import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class BlockRulesTest {
    private val locked = BlockFacts(
        blockingEnabled = true, onVacation = false, essential = false, leisure = true,
        inBedtime = false, bedtimeEndsAt = null, graceActive = false, sessionActive = false,
        allDoneUnlock = false, nothingScheduled = false,
    )
    private val noBalance = BlockDecision.Block(BlockCause.NO_BALANCE, null)

    @Test fun defaultLeisureIsBlocked() = assertEquals(noBalance, BlockRules.decide(locked))

    @Test fun earlyAllows() {
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(blockingEnabled = false)))
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(onVacation = true, inBedtime = true)))
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(essential = true, inBedtime = true)))
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(leisure = false, inBedtime = true)))
    }

    @Test fun bedtimeBeatsSessionButNotGrace() {
        val bed = locked.copy(inBedtime = true, bedtimeEndsAt = 99L, sessionActive = true, allDoneUnlock = true)
        assertEquals(BlockDecision.Block(BlockCause.BEDTIME, 99L), BlockRules.decide(bed))
        assertEquals(BlockDecision.Allow, BlockRules.decide(bed.copy(graceActive = true)))
    }

    @Test fun unlockPaths() {
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(sessionActive = true)))
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(allDoneUnlock = true)))
        assertEquals(BlockDecision.Allow, BlockRules.decide(locked.copy(nothingScheduled = true)))
    }

    @Test fun leisureMatching() {
        val rules = listOf(
            AppRuleEntity("com.video", AppRule.LEISURE, label = "Video"),
            AppRuleEntity("news.example", AppRule.LEISURE, isWebsite = true, label = "News"),
            AppRuleEntity("com.chat", AppRule.ALLOWED, label = "Chat"),
        )
        assertTrue(BlockRules.isLeisure(rules, "com.video", null))
        assertTrue(BlockRules.isLeisure(rules, "com.browser", "www.news.example"))
        assertTrue(BlockRules.isLeisure(rules, "com.browser", "m.news.example"))
        assertFalse(BlockRules.isLeisure(rules, "com.browser", "notnews.example"))
        assertFalse(BlockRules.isLeisure(rules, "com.chat", null))
        assertTrue(BlockRules.isUserAllowed(rules, "com.chat"))
    }

    @Test fun windowWrapsMidnight() {
        val w = DailyWindow(23 * 60, 7 * 60)
        val z = ZoneOffset.UTC
        val at = LocalDateTime.of(2026, 1, 1, 23, 30).toInstant(z).toEpochMilli()
        val early = LocalDateTime.of(2026, 1, 2, 3, 0).toInstant(z).toEpochMilli()
        assertTrue(w.contains(at, z))
        assertTrue(w.contains(early, z))
        assertFalse(w.contains(LocalDateTime.of(2026, 1, 2, 12, 0).toInstant(z).toEpochMilli(), z))
        val end = LocalDateTime.of(2026, 1, 2, 7, 0).toInstant(z).toEpochMilli()
        assertEquals(end, w.endOf(at, z))
        assertEquals(end, w.endOf(early, z))
        assertEquals(LocalDateTime.of(2026, 1, 1, 23, 0).toInstant(z).toEpochMilli(), w.startOf(early, z))
    }
}
