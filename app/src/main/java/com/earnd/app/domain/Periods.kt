package com.earnd.app.domain

import com.earnd.app.data.entity.ResetPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Pure helpers for balance reset periods. */
object Periods {
    fun start(day: LocalDate, period: ResetPeriod, weekStartsOn: DayOfWeek): LocalDate = when (period) {
        ResetPeriod.DAILY -> day
        ResetPeriod.WEEKLY -> day.with(TemporalAdjusters.previousOrSame(weekStartsOn))
    }

    fun nextStart(day: LocalDate, period: ResetPeriod, weekStartsOn: DayOfWeek): LocalDate = when (period) {
        ResetPeriod.DAILY -> day.plusDays(1)
        ResetPeriod.WEEKLY -> start(day, period, weekStartsOn).plusWeeks(1)
    }
}
