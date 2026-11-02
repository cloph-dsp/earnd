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

    const val DEFAULT_RING_SEC = 60
    const val DEFAULT_VOLUME_PCT = 60
    const val MIN_RING_SEC = 1
    const val MAX_RING_SEC = 600
    const val MIN_VOLUME_PCT = 10
    const val MAX_VOLUME_PCT = 100
    /** Rings this short or shorter play at full target volume at once and are not reported as missed. */
    const val SHORT_RING_SEC = 10
    const val RAMP_MS = 20_000L
    const val RAMP_START_FRACTION = 0.2f

    /** Per-item override (null = default) over the global value, clamped to 1..600 s, in ms. */
    fun effectiveRingMs(itemSec: Int?, globalSec: Int): Long =
        (itemSec ?: globalSec).coerceIn(MIN_RING_SEC, MAX_RING_SEC) * 1000L

    /** Per-item override over the global value, as a 0.1..1.0 fraction of the alarm stream. */
    fun effectiveVolume(itemPct: Int?, globalPct: Int): Float =
        (itemPct ?: globalPct).coerceIn(MIN_VOLUME_PCT, MAX_VOLUME_PCT) / 100f

    fun isShort(ringMs: Long): Boolean = ringMs <= SHORT_RING_SEC * 1000L

    /** Ramp is used only when gentle start is on and the ring is longer than a short one. */
    fun usesRamp(gentle: Boolean, ringMs: Long): Boolean = gentle && !isShort(ringMs)

    /** Volume [elapsedMs] after the ring began: linear from 20% of [target] to [target] over [RAMP_MS]. */
    fun rampVolume(target: Float, elapsedMs: Long, gentle: Boolean): Float {
        if (!gentle) return target
        val f = (elapsedMs.toFloat() / RAMP_MS).coerceIn(0f, 1f)
        return target * (RAMP_START_FRACTION + (1f - RAMP_START_FRACTION) * f)
    }

    fun timedOut(startedAt: Long, now: Long, timeoutMs: Long): Boolean = now - startedAt >= timeoutMs
}
