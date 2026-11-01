package com.earnd.app.domain

import com.earnd.app.data.entity.OwnerKind

/** An earned ledger entry in rolling mode: [remaining] unspent minutes that expire at [expiresAt]. */
data class Lot(
    val id: Long,
    val at: Long,
    val expiresAt: Long,
    val remaining: Int,
    val sourceKind: OwnerKind? = null,
    val sourceId: Long? = null,
)

/** Minutes that lapse together at [at] (grouped to the minute). */
data class NextExpiry(val at: Long, val minutes: Int)

/** New unspent amounts for lots, by id, plus the expiry of the last lot touched. */
data class LotChanges(val remaining: Map<Long, Int>, val lastExpiresAt: Long? = null)

/**
 * Pure rolling-expiry arithmetic. A lot counts while `expiresAt > now`; spending consumes the oldest
 * (earliest [Lot.at], then id) unexpired lots first.
 */
object ExpiryLedger {
    private const val MINUTE = 60_000L

    private fun live(lots: List<Lot>, now: Long) =
        lots.filter { it.remaining > 0 && it.expiresAt > now }.sortedWith(compareBy({ it.at }, { it.id }))

    fun balance(lots: List<Lot>, now: Long): Int = live(lots, now).sumOf { it.remaining }

    /** The soonest expiry and how many minutes lapse within that same minute, or null if nothing is banked. */
    fun nextExpiry(lots: List<Lot>, now: Long): NextExpiry? {
        val l = live(lots, now)
        val first = l.minOfOrNull { it.expiresAt } ?: return null
        val bucket = first / MINUTE
        return NextExpiry(first, l.filter { it.expiresAt / MINUTE == bucket }.sumOf { it.remaining })
    }

    /** FIFO spend of [minutes]; null when the unexpired balance is too small (or [minutes] <= 0). */
    fun spend(lots: List<Lot>, now: Long, minutes: Int): LotChanges? {
        if (minutes <= 0) return null
        val l = live(lots, now)
        if (l.sumOf { it.remaining } < minutes) return null
        var left = minutes
        val changes = LinkedHashMap<Long, Int>()
        var last: Long? = null
        for (lot in l) {
            if (left == 0) break
            val take = minOf(left, lot.remaining)
            changes[lot.id] = lot.remaining - take
            last = lot.expiresAt
            left -= take
        }
        return LotChanges(changes, last)
    }

    /**
     * Undo of a source that netted [net] minutes. The source's own unspent minutes are removed; whatever part
     * of [net] was already spent comes off the other lots (oldest first), never taking the balance below zero.
     * Expired unspent minutes of the source are simply gone and cost nothing.
     */
    fun undo(lots: List<Lot>, now: Long, sourceKind: OwnerKind, sourceId: Long, net: Int): LotChanges {
        if (net <= 0) return LotChanges(emptyMap())
        val mine = lots.filter { it.sourceKind == sourceKind && it.sourceId == sourceId }
        val unspentAll = mine.sumOf { it.remaining }
        val changes = LinkedHashMap<Long, Int>()
        mine.filter { it.remaining > 0 }.forEach { changes[it.id] = 0 }
        var deficit = (net - unspentAll).coerceAtLeast(0)
        for (lot in live(lots, now)) {
            if (deficit == 0) break
            if (lot.id in changes) continue
            val take = minOf(deficit, lot.remaining)
            changes[lot.id] = lot.remaining - take
            deficit -= take
        }
        return LotChanges(changes)
    }

    /** Where unused minutes of an ended session go back: nowhere if their lot has already expired. */
    fun refundExpiry(spendExpiresAt: Long?, now: Long): Long? = spendExpiresAt?.takeIf { it > now }
}
