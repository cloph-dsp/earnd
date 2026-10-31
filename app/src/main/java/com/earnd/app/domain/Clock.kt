package com.earnd.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun now(): Long
    fun zone(): ZoneId
}

object SystemTimeClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun Clock.today(): LocalDate = localDate(now())
fun Clock.localDate(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone()).toLocalDate()
fun Clock.startOfDay(day: LocalDate): Long = day.atStartOfDay(zone()).toInstant().toEpochMilli()
fun Clock.minuteOfDay(at: Long): Int =
    Instant.ofEpochMilli(at).atZone(zone()).let { it.hour * 60 + it.minute }
