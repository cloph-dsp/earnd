package com.earnd.app.notify

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

object NotifyTimes {
    /** Next instant strictly after [nowMs] at [minuteOfDay] local time (DST-safe via ZonedDateTime). */
    fun nextAt(nowMs: Long, minuteOfDay: Int, zone: ZoneId): Long {
        require(minuteOfDay in 0 until 1440)
        return nextAt(nowMs, zone) { minuteOfDay }
    }

    /** Like [nextAt] with a minute that can differ per day (weekend shift). */
    fun nextAt(nowMs: Long, zone: ZoneId, minuteFor: (LocalDate) -> Int): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        fun on(d: LocalDate): ZonedDateTime {
            val m = minuteFor(d)
            require(m in 0 until 1440)
            return d.atTime(LocalTime.of(m / 60, m % 60)).atZone(zone)
        }
        var at = on(now.toLocalDate())
        if (!at.toInstant().isAfter(now.toInstant())) at = on(now.toLocalDate().plusDays(1))
        return at.toInstant().toEpochMilli()
    }
}
