package com.earnd.app.alarms

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.Clock
import com.earnd.app.domain.startOfDay
import java.time.LocalDate

/** Small pure helpers for turning items into instants. */
internal object AlarmTimes {
    private const val MINUTE = 60_000L

    /** All-day tasks are treated as due when quiet hours end. */
    fun taskDueAt(t: TaskEntity, settings: Settings, clock: Clock): Long? {
        val day = t.dueEpochDay ?: return null
        val min = t.dueMinuteOfDay ?: settings.quietEndMin
        return clock.startOfDay(LocalDate.ofEpochDay(day)) + min * MINUTE
    }

    fun at(day: LocalDate, minuteOfDay: Int, clock: Clock): Long = clock.startOfDay(day) + minuteOfDay * MINUTE

    fun offsets(cfg: AlarmConfigEntity): List<Int> =
        cfg.offsetsBeforeMin.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct()

    fun minutes(n: Int) = n * MINUTE
}
