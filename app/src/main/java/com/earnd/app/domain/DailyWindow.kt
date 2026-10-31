package com.earnd.app.domain

import java.time.Instant
import java.time.ZoneId

/**
 * A nightly-style window in local minutes-of-day, e.g. 23:00-07:00. When [startMin] > [endMin]
 * it wraps midnight. Equal bounds mean an empty window. Used for bedtime and quiet hours.
 */
data class DailyWindow(val startMin: Int, val endMin: Int) {
    fun contains(minuteOfDay: Int): Boolean = when {
        startMin == endMin -> false
        startMin < endMin -> minuteOfDay in startMin until endMin
        else -> minuteOfDay >= startMin || minuteOfDay < endMin
    }

    fun contains(at: Long, zone: ZoneId): Boolean = contains(minuteOfDay(at, zone))

    /** Start of the window occurrence containing [at] (epoch ms), or null if outside. */
    fun startOf(at: Long, zone: ZoneId): Long? {
        if (!contains(at, zone)) return null
        val z = Instant.ofEpochMilli(at).atZone(zone)
        val day = if (startMin > endMin && minuteOfDay(at, zone) < endMin) z.toLocalDate().minusDays(1) else z.toLocalDate()
        return day.atStartOfDay(zone).plusMinutes(startMin.toLong()).toInstant().toEpochMilli()
    }

    /** End of the window occurrence containing [at] (epoch ms), or null if outside. */
    fun endOf(at: Long, zone: ZoneId): Long? {
        if (!contains(at, zone)) return null
        val z = Instant.ofEpochMilli(at).atZone(zone)
        val day = if (startMin > endMin && minuteOfDay(at, zone) >= startMin) z.toLocalDate().plusDays(1) else z.toLocalDate()
        return day.atStartOfDay(zone).plusMinutes(endMin.toLong()).toInstant().toEpochMilli()
    }

    companion object {
        fun minuteOfDay(at: Long, zone: ZoneId): Int =
            Instant.ofEpochMilli(at).atZone(zone).let { it.hour * 60 + it.minute }
    }
}
