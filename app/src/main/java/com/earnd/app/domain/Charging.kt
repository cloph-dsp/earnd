package com.earnd.app.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-app charging. FULL (null) needs an unlock session. METERED (0/25/50/100) keeps the app open while the
 * balance is positive and the app is in the foreground, charging that percent of the foreground time.
 */
object Charging {
    val RATES = listOf(0, 25, 50, 100)
    const val DEFAULT_RATE = 50

    /** Higher = stricter. FULL is stricter than any metered rate. */
    fun strictness(pct: Int?): Int = pct ?: Int.MAX_VALUE

    /** True when going from [old] to [new] makes the app cheaper to use (FULL -> METERED, or a lower rate). */
    fun loosens(old: Int?, new: Int?): Boolean = strictness(new) < strictness(old)

    fun label(pct: Int?): String = if (pct == null) "Full" else "Metered $pct%"

    /** "TIDAL · 50%" */
    fun note(name: String, pct: Int): String = "$name · $pct%"

    /** Wrapper so that FULL (null) is distinguishable from invalid input. */
    data class Rate(val pct: Int?)

    /** `full`, or one of [RATES] (with an optional `%`); null when invalid. */
    fun parse(v: String): Rate? {
        val t = v.trim().lowercase().removeSuffix("%")
        if (t == "full") return Rate(null)
        val n = t.toIntOrNull() ?: return null
        return if (n in RATES) Rate(n) else null
    }
}

/** Pure foreground-time arithmetic. Charge is accumulated as elapsed ms x rate (ms-percent) so nothing is lost to rounding. */
object MeterMath {
    /** A debit is made per whole minute of charged time. */
    const val UNIT_MS = 60_000L
    private const val UNIT = UNIT_MS * 100
    /** One interval is never charged for more than this; guards against a stalled service or a long sleep. */
    const val MAX_STEP_MS = 60_000L

    data class Step(val units: Int, val carry: Long)

    /** Adds [elapsedMs] at [ratePct] to [carry]; returns whole minutes to debit and the new carry. */
    fun accrue(carry: Long, elapsedMs: Long, ratePct: Int): Step {
        val total = carry + elapsedMs.coerceIn(0, MAX_STEP_MS) * ratePct.coerceIn(0, 100)
        return Step((total / UNIT).toInt(), total % UNIT)
    }

    /** Seconds of charged time already accumulated toward the next minute. */
    fun carrySeconds(carry: Long): Int = (carry / 100 / 1000).toInt()
}

/**
 * Tracks which metered app is in front and debits the bank. The guard calls [advance] on every foreground
 * change and tick (charging the time since the last call at the previous rate), then [switchTo] with the rate
 * that now applies (null: nothing metered in front, e.g. not metered, covered by a session, or locked).
 */
class MeterEngine(
    /** Debits whole minutes from the bank; returns how many it could take. */
    private val debit: suspend (minutes: Int) -> Int,
    private val loadCarry: suspend () -> Long,
    private val saveCarry: suspend (Long) -> Unit,
) {
    private val mutex = Mutex()
    private var lastAt: Long? = null
    private var rate: Int? = null
    private var carry: Long? = null

    /** Whole minutes debited. [interactive] false (screen off) charges nothing. */
    suspend fun advance(now: Long, interactive: Boolean = true): Int = mutex.withLock { advanceLocked(now, interactive) }

    private suspend fun advanceLocked(now: Long, interactive: Boolean): Int {
        val from = lastAt
        lastAt = now
        val r = rate
        if (from == null || r == null || r == 0 || !interactive || now <= from) return 0
        val c = carry ?: loadCarry().also { carry = it }
        val step = MeterMath.accrue(c, now - from, r)
        carry = step.carry
        if (step.carry != c) saveCarry(step.carry)
        return if (step.units > 0) debit(step.units) else 0
    }

    suspend fun switchTo(ratePct: Int?, now: Long) = mutex.withLock {
        rate = ratePct
        lastAt = now
    }

    /** Charge up to [now] and stop metering (service stopped, screen off, lock shown). */
    suspend fun stop(now: Long): Int = mutex.withLock { advanceLocked(now, true).also { rate = null; lastAt = now } }
}
