package com.earnd.app.domain

import com.earnd.app.data.entity.OwnerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryLedgerTest {
    private val h = 3_600_000L
    private fun lot(id: Long, atH: Long, rem: Int, ttlH: Long = 24, src: Long? = null) =
        Lot(id, atH * h, (atH + ttlH) * h, rem, if (src != null) OwnerKind.HABIT else null, src)

    @Test fun balanceSumsUnexpiredOnly() {
        val lots = listOf(lot(1, 0, 10), lot(2, 10, 5))
        assertEquals(15, ExpiryLedger.balance(lots, 23 * h))
        assertEquals(5, ExpiryLedger.balance(lots, 25 * h))
        assertEquals(0, ExpiryLedger.balance(lots, 40 * h))
    }

    @Test fun expiryBoundaryIsExclusive() {
        val lots = listOf(lot(1, 0, 10))
        assertEquals(10, ExpiryLedger.balance(lots, 24 * h - 1))
        assertEquals(0, ExpiryLedger.balance(lots, 24 * h))
    }

    @Test fun nextExpiryGroupsSameMinute() {
        val lots = listOf(
            Lot(1, 0, 24 * h + 5_000, 4), Lot(2, 0, 24 * h + 50_000, 3), Lot(3, 0, 30 * h, 9),
        )
        val n = ExpiryLedger.nextExpiry(lots, 1 * h)!!
        assertEquals(24 * h + 5_000, n.at)
        assertEquals(7, n.minutes)
        assertNull(ExpiryLedger.nextExpiry(lots, 31 * h))
        assertNull(ExpiryLedger.nextExpiry(emptyList(), 0))
    }

    @Test fun spendIsFifoAndPartial() {
        val lots = listOf(lot(2, 5, 10), lot(1, 0, 6))
        val c = ExpiryLedger.spend(lots, 6 * h, 8)!!
        assertEquals(mapOf(1L to 0, 2L to 8), c.remaining)
        assertEquals(29 * h, c.lastExpiresAt)
    }

    @Test fun spendSkipsExpiredLots() {
        val lots = listOf(lot(1, 0, 10), lot(2, 5, 10))
        val c = ExpiryLedger.spend(lots, 25 * h, 4)!!
        assertEquals(mapOf(2L to 6), c.remaining)
    }

    @Test fun spendRefusedWhenNotEnoughUnexpired() {
        val lots = listOf(lot(1, 0, 10), lot(2, 5, 3))
        assertNull(ExpiryLedger.spend(lots, 25 * h, 4))
        assertNull(ExpiryLedger.spend(lots, 1 * h, 14))
        assertNull(ExpiryLedger.spend(lots, 1 * h, 0))
        assertEquals(mapOf(1L to 0, 2L to 0), ExpiryLedger.spend(lots, 1 * h, 13)!!.remaining)
    }

    @Test fun undoRemovesUnspentOfSourceOnly() {
        val lots = listOf(lot(1, 0, 10, src = 7), lot(2, 1, 5, src = 8))
        val c = ExpiryLedger.undo(lots, 2 * h, OwnerKind.HABIT, 7, 10)
        assertEquals(mapOf(1L to 0), c.remaining)
    }

    @Test fun undoAfterPartialSpendTakesDeficitFromOthers() {
        // source 7 earned 10 and 4 are left (6 spent); another entry holds 8
        val lots = listOf(lot(1, 0, 4, src = 7), lot(2, 1, 8, src = 8))
        val c = ExpiryLedger.undo(lots, 2 * h, OwnerKind.HABIT, 7, 10)
        assertEquals(mapOf(1L to 0, 2L to 2), c.remaining)
    }

    @Test fun undoDeficitCappedAtBalance() {
        val lots = listOf(lot(1, 0, 0, src = 7), lot(2, 1, 2, src = 8))
        val c = ExpiryLedger.undo(lots, 2 * h, OwnerKind.HABIT, 7, 10)
        assertEquals(mapOf(2L to 0), c.remaining)
    }

    @Test fun undoOfExpiredUnspentCostsNothing() {
        val lots = listOf(lot(1, 0, 10, src = 7), lot(2, 30, 5, src = 8))
        val c = ExpiryLedger.undo(lots, 40 * h, OwnerKind.HABIT, 7, 10)
        assertEquals(mapOf(1L to 0), c.remaining)
    }

    @Test fun undoNonPositiveNetDoesNothing() {
        assertEquals(emptyMap<Long, Int>(), ExpiryLedger.undo(listOf(lot(1, 0, 5, src = 7)), h, OwnerKind.HABIT, 7, 0).remaining)
    }

    @Test fun refundExpiry() {
        assertEquals(10L, ExpiryLedger.refundExpiry(10, 5))
        assertNull(ExpiryLedger.refundExpiry(10, 10))
        assertNull(ExpiryLedger.refundExpiry(null, 5))
    }
}
