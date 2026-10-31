package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.DaySegment

/** Pure day-segment rules for segment mode. Before the morning start there is no segment. */
object Segments {
    private const val DAY_MIN = 24 * 60

    /** True when the configured starts are strictly increasing within the day. */
    fun valid(s: Settings): Boolean =
        s.morningStartMin in 0 until DAY_MIN && s.morningStartMin < s.afternoonStartMin &&
            s.afternoonStartMin < s.eveningStartMin && s.eveningStartMin < DAY_MIN

    fun current(minuteOfDay: Int, s: Settings): DaySegment = when {
        !valid(s) || minuteOfDay < s.morningStartMin -> DaySegment.NONE
        minuteOfDay < s.afternoonStartMin -> DaySegment.MORNING
        minuteOfDay < s.eveningStartMin -> DaySegment.AFTERNOON
        else -> DaySegment.EVENING
    }

    fun startMinute(seg: DaySegment, s: Settings): Int? = when (seg) {
        DaySegment.NONE -> null
        DaySegment.MORNING -> s.morningStartMin
        DaySegment.AFTERNOON -> s.afternoonStartMin
        DaySegment.EVENING -> s.eveningStartMin
    }

    /** Minute of day the segment ends (the next segment's start, or midnight = 1440). */
    fun endMinute(seg: DaySegment, s: Settings): Int? = when (seg) {
        DaySegment.NONE -> null
        DaySegment.MORNING -> s.afternoonStartMin
        DaySegment.AFTERNOON -> s.eveningStartMin
        DaySegment.EVENING -> DAY_MIN
    }
}
