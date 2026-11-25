package com.earnd.app.alarms

import com.earnd.app.R
import com.earnd.app.i18n.Str

/** Short, shame-free lines; the day and count pick a variant, nothing is stored. */
object NagCopy {
    private val PENDING = intArrayOf(
        R.string.alarms_nag_pending_1,
        R.string.alarms_nag_pending_2,
        R.string.alarms_nag_pending_3,
    )
    private val UPCOMING = intArrayOf(
        R.string.alarms_nag_upcoming_1,
        R.string.alarms_nag_upcoming_2,
    )

    fun pendingLine(epochDay: Long, nagCount: Int): String = Str.get(PENDING[index(epochDay, nagCount, PENDING.size)])

    fun upcomingLine(epochDay: Long, nagCount: Int): String = Str.get(UPCOMING[index(epochDay, nagCount, UPCOMING.size)])

    /** Status line only: no notification of its own. */
    val capReachedLine: String get() = Str.get(R.string.alarms_nag_cap)

    private fun index(epochDay: Long, nagCount: Int, size: Int): Int =
        Math.floorMod(epochDay + nagCount, size.toLong()).toInt()
}
