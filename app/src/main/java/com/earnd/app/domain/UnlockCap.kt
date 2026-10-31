package com.earnd.app.domain

/**
 * Optional cap on a single paid unlock session. A request longer than the cap starts a session
 * of exactly the cap; only the minutes actually used leave the balance, the rest stay banked.
 */
object UnlockCap {
    const val MIN = 15
    const val MAX = 45
    const val STEP = 5

    /** Selectable values: null (off) then 15..45 in 5-minute steps. */
    val CHOICES: List<Int?> = listOf<Int?>(null) + (MIN..MAX step STEP).toList()

    /** Snaps a stored value to a valid choice; anything outside the range turns the cap off. */
    fun normalize(cap: Int?): Int? {
        if (cap == null || cap < MIN || cap > MAX) return null
        return MIN + ((cap - MIN + STEP / 2) / STEP) * STEP
    }

    /** Minutes a session may actually last when [requested] minutes are asked for. */
    fun clamp(requested: Int, cap: Int?): Int {
        val c = normalize(cap) ?: return requested
        return minOf(requested, c)
    }

    /** Spend choices to offer for a balance: base choices within reach, plus the largest allowed. */
    fun choices(base: List<Int>, balance: Int, cap: Int?): List<Int> {
        if (balance <= 0) return emptyList()
        val top = clamp(balance, cap)
        return (base.filter { it < top } + top).distinct().sorted()
    }
}
