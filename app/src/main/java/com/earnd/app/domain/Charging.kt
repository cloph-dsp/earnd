package com.earnd.app.domain

import com.earnd.app.R
import com.earnd.app.i18n.Str
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default charging model: every leisure app/site is metered at 0/25/50/100 percent of its foreground time
 * (default 100). A null stored rate (old FULL rows) means [DEFAULT_RATE].
 */
object Charging {
    val RATES = listOf(0, 25, 50, 100)
    const val DEFAULT_RATE = 100

    /** Rate actually applied for a stored value. */
    fun effective(pct: Int?): Int = pct ?: DEFAULT_RATE

    /** Higher = stricter. */
    fun strictness(pct: Int?): Int = effective(pct)

    /** True when going from [old] to [new] makes the app cheaper to use (a lower rate). */
    fun loosens(old: Int?, new: Int?): Boolean = strictness(new) < strictness(old)

    fun label(pct: Int?): String = Str.get(R.string.habits_charging_metered, effective(pct))

    /** "TIDAL · 50%" */
    fun note(name: String, pct: Int): String = "$name · $pct%"

    /** Wrapper so that a null result of [parse] means invalid input. */
    data class Rate(val pct: Int?)

    /** One of [RATES] (with an optional `%`); `full` is the legacy name of 100. Null when invalid. */
    fun parse(v: String): Rate? {
        val t = v.trim().lowercase().removeSuffix("%")
        if (t == "full") return Rate(DEFAULT_RATE)
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
    const val MAX_STEP_MS = 120_000L

    data class Step(val units: Int, val carry: Long)

    /** Adds [elapsedMs] at [ratePct] to [carry]; returns whole minutes to debit and the new carry. */
    fun accrue(carry: Long, elapsedMs: Long, ratePct: Int): Step {
        val total = carry + elapsedMs.coerceIn(0, MAX_STEP_MS) * ratePct.coerceIn(0, 100)
        return Step((total / UNIT).toInt(), total % UNIT)
    }

    /** Seconds of charged time already accumulated toward the next minute. */
    fun carrySeconds(carry: Long): Int = (carry / 100 / 1000).toInt()

    /**
     * Real milliseconds until [balanceMin] whole minutes (minus the already accrued [carry]) are used up at
     * [ratePct]; null when nothing is charged (rate 0).
     */
    fun msUntilZero(balanceMin: Int, carry: Long, ratePct: Int): Long? {
        val r = ratePct.coerceIn(0, 100)
        if (r == 0) return null
        val left = balanceMin.coerceAtLeast(0).toLong() * UNIT - carry
        if (left <= 0) return 0
        return (left + r - 1) / r
    }
}

/** What the meter is charging: rule [key] (package or site host), the [pkg] in front, its [ratePct], and when counting began. */
data class MeterActive(val key: String, val pkg: String, val ratePct: Int, val since: Long)

/**
 * Debits the bank for foreground time of the leisure app being counted. The guard calls [advance] on every event
 * (window change, screen on/off, user present) and tick, charging the time since the last call at the previous
 * rate, then [switchTo] with what now counts (null: nothing).
 */
class MeterEngine(
    /** Debits whole minutes from the bank; returns how many it could take. */
    private val debit: suspend (minutes: Int) -> Int,
    private val loadCarry: suspend () -> Long,
    private val saveCarry: suspend (Long) -> Unit,
) {
    private val mutex = Mutex()
    private var lastAt: Long? = null
    private var active: MeterActive? = null
    private var carry: Long? = null
    private var balance = 0

    /** What is being counted now, or null. */
    fun current(): MeterActive? = active

    /** Latest known balance in minutes (kept in step with debits between [syncBalance] calls). */
    fun knownBalance(): Int = balance

    fun syncBalance(minutes: Int) { balance = minutes }

    fun carryNow(): Long = carry ?: 0L

    /** Real ms left at the current rate as of [now] (not yet committed); null when nothing counts or the rate is 0. */
    fun remainingMs(now: Long): Long? {
        val a = active ?: return null
        val from = lastAt ?: now
        val virtual = (carry ?: 0L) + (now - from).coerceIn(0, MeterMath.MAX_STEP_MS) * a.ratePct.coerceIn(0, 100)
        return MeterMath.msUntilZero(balance, virtual, a.ratePct)
    }

    /** Whole minutes debited. [interactive] false (screen off, call, locked) charges nothing. */
    suspend fun advance(now: Long, interactive: Boolean = true): Int = mutex.withLock { advanceLocked(now, interactive) }

    private suspend fun advanceLocked(now: Long, interactive: Boolean): Int {
        val from = lastAt
        lastAt = now
        val r = active?.ratePct
        if (from == null || r == null || r == 0 || !interactive || now <= from) return 0
        val c = carry ?: loadCarry().also { carry = it }
        val step = MeterMath.accrue(c, now - from, r)
        carry = step.carry
        if (step.carry != c) saveCarry(step.carry)
        if (step.units <= 0) return 0
        val taken = debit(step.units)
        balance = (balance - taken).coerceAtLeast(0)
        return taken
    }

    /** Starts/keeps counting [target] (null = nothing counts). Keeps the original start when the target is unchanged. */
    suspend fun switchTo(target: MeterActive?, now: Long) = mutex.withLock {
        val cur = active
        active = if (target != null && cur != null && cur.key == target.key && cur.ratePct == target.ratePct) cur else target
        lastAt = now
        if (target != null && carry == null) carry = loadCarry()
    }

    /** Charge up to [now] and stop metering (service stopped, screen off, lock shown). */
    suspend fun stop(now: Long): Int = mutex.withLock { advanceLocked(now, true).also { active = null; lastAt = now } }
}
