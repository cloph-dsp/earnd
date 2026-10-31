package com.earnd.app.notify

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object NotifyTimes {
    /** Next instant strictly after [nowMs] at [minuteOfDay] local time (DST-safe via ZonedDateTime). */
    fun nextAt(nowMs: Long, minuteOfDay: Int, zone: ZoneId): Long {
        require(minuteOfDay in 0 until 1440)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var at = now.toLocalDate().atTime(time).atZone(zone)
        if (!at.toInstant().isAfter(now.toInstant())) at = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return at.toInstant().toEpochMilli()
    }
}
