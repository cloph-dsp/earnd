package com.earnd.app.data

import com.earnd.app.data.entity.ResetPeriod
import java.time.DayOfWeek

/** Typed user settings. Minute-of-day values are 0..1439 local time. */
data class Settings(
    val resetPeriod: ResetPeriod = ResetPeriod.DAILY,
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val allDoneUnlock: Boolean = true,
    val hardMode: Boolean = false,
    val weakenCooldownMin: Int = 10,
    val bedtimeEnabled: Boolean = false,
    val bedtimeStartMin: Int = 23 * 60,
    val bedtimeEndMin: Int = 7 * 60,
    val bedtimeGraceMin: Int = 2,
    val vacationFromDay: Long? = null,
    val vacationToDay: Long? = null,
    val defaultTaskRewardMin: Int? = 5,
    val quietStartMin: Int = 0,
    val quietEndMin: Int = 8 * 60,
    val blockingEnabled: Boolean = true,
) {
    fun onVacation(epochDay: Long): Boolean {
        val from = vacationFromDay ?: return false
        val to = vacationToDay ?: return false
        return epochDay in from..to
    }
}
