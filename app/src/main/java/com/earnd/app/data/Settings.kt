package com.earnd.app.data

import com.earnd.app.data.entity.ListDensity
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.data.entity.ThemeMode
import com.earnd.app.domain.DateFormatPref
import com.earnd.app.domain.TimeFormatPref
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
    /** Finishing the current segment's habits unlocks leisure apps until the next segment starts. */
    val segmentMode: Boolean = false,
    val morningStartMin: Int = 5 * 60,
    val afternoonStartMin: Int = 12 * 60,
    val eveningStartMin: Int = 18 * 60,
    val rewardMode: RewardMode = RewardMode.PER_CHECKIN,
    /** Minutes per check-in for habits without their own override. */
    val defaultHabitRewardMin: Int = 5,
    /** "I promise to prove it": new habits default to photo check-ins. */
    val photoRequiredDefault: Boolean = false,
    val morningNudgeEnabled: Boolean = false,
    val morningNudgeMin: Int = 8 * 60,
    val dailyReportEnabled: Boolean = false,
    val dailyReportMin: Int = 21 * 60,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Longest single unlock session in minutes, or null for no cap. Unused minutes stay banked. */
    val unlockCapMin: Int? = null,
    val removalPuzzle: PuzzleKind = PuzzleKind.NONE,
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
    val dateFormat: DateFormatPref = DateFormatPref.DMY,
    val timeFormat: TimeFormatPref = TimeFormatPref.H24,
    /** First check-in after missed occasions earns a capped bonus (see Comeback). */
    val comebackBonus: Boolean = true,
) {
    fun onVacation(epochDay: Long): Boolean {
        val from = vacationFromDay ?: return false
        val to = vacationToDay ?: return false
        return epochDay in from..to
    }
}
