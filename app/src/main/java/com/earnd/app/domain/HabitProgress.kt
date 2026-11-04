package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity

/**
 * Pure completion and reward rules. A quantity habit (target > 0) is complete when its
 * check-in amounts add up to the target; any other habit when it has `timesPerDay` check-ins.
 */
object HabitProgress {
    fun required(h: HabitEntity): Int = h.timesPerDay.coerceAtLeast(1)

    fun isQuantity(h: HabitEntity): Boolean = (h.targetQuantity ?: 0.0) > 0.0

    fun quantity(dayCheckIns: List<CheckInEntity>): Double = dayCheckIns.sumOf { it.quantity ?: 0.0 }

    /** Completed units for the day, comparable with [required]. */
    fun doneUnits(h: HabitEntity, dayCheckIns: List<CheckInEntity>): Int =
        if (isQuantity(h)) { if (quantity(dayCheckIns) >= h.targetQuantity!!) required(h) else 0 }
        else dayCheckIns.size

    fun isComplete(h: HabitEntity, dayCheckIns: List<CheckInEntity>): Boolean =
        doneUnits(h, dayCheckIns) >= required(h)

    /** Minutes for one check-in of [h]: the habit's override or the global default. */
    fun rewardMinutes(h: HabitEntity, s: Settings): Int =
        (h.rewardMinutes ?: s.defaultHabitRewardMin).coerceAtLeast(0)

    /** Minutes the habit is worth for a whole day (used for the "all done" lump sum). */
    fun dayValue(h: HabitEntity, s: Settings): Int =
        rewardMinutes(h, s) * if (isQuantity(h)) 1 else required(h)

    /**
     * Whether a new check-in earns, given the day's check-ins before it and after it.
     * Counted habits always earn (`timesPerDay` is a target, not a cap; see [checkInReward] for the
     * overflow cap); quantity habits earn once, on reaching the target.
     */
    fun earns(h: HabitEntity, before: List<CheckInEntity>, after: List<CheckInEntity>): Boolean =
        if (isQuantity(h)) !isComplete(h, before) && isComplete(h, after)
        else true

    /**
     * Minutes for the next counted check-in given the day's [before] check-ins and the per-check-in
     * [perCheckIn] reward. The first `timesPerDay` check-ins earn in full. Check-ins beyond the
     * target also earn, but the day's total overflow is capped at +50% of the target reward
     * (`timesPerDay * perCheckIn / 2`), so spamming the button cannot farm minutes.
     */
    fun checkInReward(h: HabitEntity, before: List<CheckInEntity>, perCheckIn: Int): Int {
        val n = required(h)
        if (before.size < n) return perCheckIn
        if (!h.overTargetReward) return 0
        val cap = n * perCheckIn / 2
        val extraSoFar = before.sortedBy { it.at }.drop(n).sumOf { it.minutesEarned }
        return minOf(perCheckIn, (cap - extraSoFar).coerceAtLeast(0))
    }

    fun photoRequired(h: HabitEntity, s: Settings): Boolean = h.photoRequired ?: s.photoRequiredDefault
}
