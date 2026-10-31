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
     * Counted habits earn up to `timesPerDay` times; quantity habits earn once, on reaching the target.
     */
    fun earns(h: HabitEntity, before: List<CheckInEntity>, after: List<CheckInEntity>): Boolean =
        if (isQuantity(h)) !isComplete(h, before) && isComplete(h, after)
        else before.size < required(h)

    fun photoRequired(h: HabitEntity, s: Settings): Boolean = h.photoRequired ?: s.photoRequiredDefault
}
