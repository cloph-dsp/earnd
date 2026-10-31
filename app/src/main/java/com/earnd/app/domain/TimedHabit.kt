package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.HabitEntity
import kotlin.math.floor
import kotlin.math.roundToInt

/** One focus duration a timed habit offers, with what completing it earns. */
data class FocusOption(val minutes: Int, val reward: Int)

/** Pure rules for timed habits: duration options and open-ended (stopwatch) sessions. */
object TimedHabit {
    val MINUTES_RANGE = 1..240
    val RATE_RANGE = 0.0..10.0
    const val DEFAULT_RATE = 0.5
    const val DEFAULT_MIN_SESSION = 5
    const val MAX_OPTIONS = 6

    /** "10:5,20,30:15" -> (10,5), (20,null), (30,15). Invalid entries throw. */
    fun parse(spec: String?): List<Pair<Int, Int?>> {
        if (spec.isNullOrBlank()) return emptyList()
        val out = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
            val m = part.substringBefore(':').trim().toIntOrNull()?.takeIf { it in MINUTES_RANGE }
                ?: throw IllegalArgumentException("bad duration: $part")
            val r = if (':' in part) part.substringAfter(':').trim().toIntOrNull()?.takeIf { it in 0..600 }
                ?: throw IllegalArgumentException("bad reward: $part") else null
            m to r
        }.distinctBy { it.first }.sortedBy { it.first }
        require(out.size <= MAX_OPTIONS) { "at most $MAX_OPTIONS durations" }
        return out
    }

    fun parseOrEmpty(spec: String?): List<Pair<Int, Int?>> = runCatching { parse(spec) }.getOrDefault(emptyList())

    fun format(list: List<Pair<Int, Int?>>): String? =
        list.sortedBy { it.first }.joinToString(",") { (m, r) -> if (r == null) "$m" else "$m:$r" }.ifEmpty { null }

    /** Earned minutes per focused minute: the habit reward spread over its main focus length (20 if unset). */
    fun perMinute(h: HabitEntity, s: Settings): Double =
        HabitProgress.rewardMinutes(h, s).toDouble() / (h.focusMinutes?.takeIf { it > 0 } ?: 20)

    fun linearReward(h: HabitEntity, s: Settings, minutes: Int): Int = (minutes * perMinute(h, s)).roundToInt()

    /** Durations offered when starting, by minutes; an explicit option overrides the main length. Empty = not a countdown habit. */
    fun options(h: HabitEntity, s: Settings): List<FocusOption> {
        val extra = parseOrEmpty(h.focusOptions).map { (m, r) -> FocusOption(m, r ?: linearReward(h, s, m)) }
        val main = h.focusMinutes?.takeIf { it > 0 }?.let { FocusOption(it, HabitProgress.rewardMinutes(h, s)) }
        return (extra + listOfNotNull(main)).distinctBy { it.minutes }.sortedBy { it.minutes }
    }

    fun isTimed(h: HabitEntity): Boolean = h.stopwatch || (h.focusMinutes ?: 0) > 0 || parseOrEmpty(h.focusOptions).isNotEmpty()

    /** Whether starting needs a choice of duration. */
    fun needsChoice(h: HabitEntity, s: Settings): Boolean = !h.stopwatch && options(h, s).size > 1

    fun rate(h: HabitEntity): Double = (h.stopwatchRate ?: DEFAULT_RATE).coerceIn(RATE_RANGE)
    fun minSession(h: HabitEntity): Int = (h.stopwatchMinMin ?: DEFAULT_MIN_SESSION).coerceAtLeast(0)

    /** Reward for a stopwatch session of [elapsedMs]; null when shorter than the minimum (no check-in). */
    fun stopwatchReward(h: HabitEntity, elapsedMs: Long): Int? {
        val minutes = (elapsedMs.coerceAtLeast(0) / 60_000L).toInt()
        if (minutes < minSession(h) || minutes == 0) return null
        val counted = h.stopwatchCapMin?.let { minOf(minutes, it) } ?: minutes
        return floor(counted * rate(h)).toInt()
    }

    /** Highest earned-per-focused-minute any session of [h] can pay; null when not timed. */
    fun maxRate(h: HabitEntity, s: Settings): Double? = when {
        h.stopwatch -> rate(h)
        else -> options(h, s).maxOfOrNull { it.reward.toDouble() / it.minutes }
    }
}
