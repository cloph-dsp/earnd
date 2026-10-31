package com.earnd.app.ui.habits

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.HabitProgress

/** UI-side reading of a habit's overrides against the global settings. */
object HabitRules {
    /** Same rule as HabitService.photoRequired, usable without a service instance. */
    fun needsPhoto(h: HabitEntity, s: Settings): Boolean =
        h.checkInMode == CheckInMode.PHOTO || HabitProgress.photoRequired(h, s)

    fun rewardMinutes(h: HabitEntity, s: Settings): Int = HabitProgress.rewardMinutes(h, s)

    fun quantityDone(checkIns: List<CheckInEntity>): Double = HabitProgress.quantity(checkIns)

    fun dayComplete(h: HabitEntity, dayCheckIns: List<CheckInEntity>): Boolean = HabitProgress.isComplete(h, dayCheckIns)

    /** Parses a user-typed amount ("2", "1,5", "0.25"); null when not a positive number. */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    fun formatAmount(q: Double): String =
        if (q % 1.0 == 0.0) q.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, q).trimEnd('0').trimEnd('.')

    /** Moves the item at [from] to [to], clamping [to] to the list bounds. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices) return list
        val target = to.coerceIn(0, list.lastIndex)
        if (from == target) return list
        return list.toMutableList().apply { add(target, removeAt(from)) }
    }
}
