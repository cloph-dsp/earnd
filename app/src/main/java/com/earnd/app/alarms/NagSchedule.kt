package com.earnd.app.alarms

import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.NagMode
import com.earnd.app.data.entity.NagStateEntity
import com.earnd.app.domain.DailyWindow
import java.time.Instant
import java.time.ZoneId

/** Pure nag cadence: progressive/fixed gaps, max count, weekly-overdue digest, quiet hours. */
object NagSchedule {
    /** Gap in minutes before nag #n (n = nagCount so far); last value repeats. */
    val PROGRESSIVE_GAPS_MIN = listOf(10, 15, 25, 40, 60, 90, 120, 180, 240)
    const val DIGEST_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    const val SNOOZE_MIN = 10
    private const val MINUTE = 60_000L

    fun gapMin(nagCount: Int, cfg: AlarmConfigEntity?): Int? = when (cfg?.nagMode ?: NagMode.PROGRESSIVE) {
        NagMode.OFF -> null
        NagMode.FIXED -> cfg!!.nagEveryMin.coerceAtLeast(1)
        NagMode.PROGRESSIVE -> PROGRESSIVE_GAPS_MIN[nagCount.coerceAtMost(PROGRESSIVE_GAPS_MIN.lastIndex)]
    }

    fun isDigest(state: NagStateEntity, now: Long): Boolean = now - state.dueAt > DIGEST_AFTER_MS

    /**
     * Next time to nag, or null when nagging is off or the max count is reached. Items more than
     * a week overdue move to one shared daily digest slot (quiet end), so they fire together.
     */
    fun next(state: NagStateEntity, cfg: AlarmConfigEntity?, now: Long, zone: ZoneId, quiet: DailyWindow): Long? {
        val gap = gapMin(state.nagCount, cfg) ?: return null
        cfg?.nagMaxCount?.let { if (state.nagCount >= it) return null }
        if (isDigest(state, now)) {
            val after = maxOf(now, (state.lastNagAt ?: 0L) + 1)
            return nextDigestSlot(after, zone, quiet, onlyAfterLast = state.lastNagAt)
        }
        val base = state.lastNagAt ?: state.dueAt
        val candidate = maxOf(base + gap * MINUTE, now)
        return shiftOutOfQuiet(candidate, zone, quiet)
    }

    fun shiftOutOfQuiet(at: Long, zone: ZoneId, quiet: DailyWindow): Long = quiet.endOf(at, zone) ?: at

    /** Earliest daily digest time (quiet end) at or after [after], at most one per calendar day. */
    fun nextDigestSlot(after: Long, zone: ZoneId, quiet: DailyWindow, onlyAfterLast: Long? = null): Long {
        val slotMin = if (quiet.startMin == quiet.endMin) 9 * 60 else quiet.endMin
        var day = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val lastDay = onlyAfterLast?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        while (true) {
            val slot = day.atStartOfDay(zone).plusMinutes(slotMin.toLong()).toInstant().toEpochMilli()
            if (slot >= after && (lastDay == null || day.isAfter(lastDay))) return slot
            day = day.plusDays(1)
        }
    }

    /** Snooze exists only on the full-screen alarm and is capped by `snoozeMaxCount`. */
    fun canSnooze(state: NagStateEntity, cfg: AlarmConfigEntity?): Boolean =
        state.snoozeCount < (cfg?.snoozeMaxCount ?: 2)

    /** Most-nagged first, then the longest overdue. */
    fun order(states: List<NagStateEntity>): List<NagStateEntity> =
        states.sortedWith(compareByDescending<NagStateEntity> { it.nagCount }.thenBy { it.dueAt })
}
