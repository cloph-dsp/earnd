package com.earnd.app.alarms

import com.earnd.app.data.entity.AlarmStyle
import com.earnd.app.data.entity.OwnerKind

/** One exact alarm to arm. [slot] is "due", "pre<min>" or "nag". */
data class PlannedAlarm(
    val ownerKind: OwnerKind,
    val ownerId: Long,
    val at: Long,
    val style: AlarmStyle,
    val slot: String = SLOT_DUE,
) {
    companion object {
        const val SLOT_DUE = "due"
        const val SLOT_NAG = "nag"
        /** A habit reminder occurrence, keyed by its minute of day; handled exactly like a due time. */
        const val HABIT_PREFIX = "hr"
        fun habitReminder(minuteOfDay: Int) = "$HABIT_PREFIX$minuteOfDay"
        fun pre(min: Int) = "pre$min"
    }
}
