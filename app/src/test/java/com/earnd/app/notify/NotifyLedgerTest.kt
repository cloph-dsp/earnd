package com.earnd.app.notify

import com.earnd.app.data.entity.LedgerEntryEntity
import com.earnd.app.data.entity.LedgerKind
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifyLedgerTest {

    private val since = 1_000L

    private fun entry(at: Long, kind: LedgerKind, minutes: Int) =
        LedgerEntryEntity(at = at, kind = kind, minutes = minutes, periodKey = 0)

    @Test
    fun meteredAppUseCountsAsSpent() {
        val rows = listOf(
            entry(since + 1, LedgerKind.SPEND, -10),
            entry(since + 2, LedgerKind.METER, -7),
            entry(since - 1, LedgerKind.SPEND, -99), // yesterday
            entry(since + 3, LedgerKind.RESET, -50), // a reset is not leisure
        )
        assertEquals(17, NotifyReceiver.usedMinutes(rows, since))
    }

    @Test
    fun earningsNetOutUndosAndIgnoreResets() {
        val rows = listOf(
            entry(since + 1, LedgerKind.EARN_HABIT, 10),
            entry(since + 2, LedgerKind.EARN_TASK, 20),
            entry(since + 3, LedgerKind.COMEBACK, 5),
            entry(since + 4, LedgerKind.UNDO, -8),
            entry(since + 5, LedgerKind.RESET, 40),
            entry(since - 1, LedgerKind.EARN_HABIT, 100), // yesterday
        )
        assertEquals(27, NotifyReceiver.earnedMinutes(rows, since))
    }
}
