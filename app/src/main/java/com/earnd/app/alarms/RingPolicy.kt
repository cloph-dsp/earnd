package com.earnd.app.alarms

/** Pure rules for a ringing alarm: which buttons to offer and when to give up ringing. */
object RingPolicy {
    /** Default cap on one ring before it stops by itself and leaves a "missed" notification. */
    const val DEFAULT_TIMEOUT_MS = 5 * 60_000L
    private const val MIN_TIMEOUT_MS = 10_000L
    private const val MAX_TIMEOUT_MS = 60 * 60_000L

    fun snoozesLeft(maxCount: Int?, used: Int): Int = ((maxCount ?: 2) - used).coerceAtLeast(0)

    fun canSnooze(snoozesLeft: Int): Boolean = snoozesLeft > 0

    /** Requested timeout (null or <= 0 = default), clamped to a sane range. */
    fun timeoutMs(requested: Long?): Long =
        if (requested == null || requested <= 0) DEFAULT_TIMEOUT_MS
        else requested.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

    fun timedOut(startedAt: Long, now: Long, timeoutMs: Long): Boolean = now - startedAt >= timeoutMs
}
