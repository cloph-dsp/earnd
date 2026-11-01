package com.earnd.app.alarms

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.Clock
import java.time.LocalDate

/** Small pure helpers for turning items into instants. */
internal object AlarmTimes {
    private const val MINUTE = 60_000L

    /** All-day tasks are treated as due when quiet hours end. */
    fun taskDueAt(t: TaskEntity, settings: Settings, clock: Clock): Long? {
        val day = t.dueEpochDay ?: return null
        val date = LocalDate.ofEpochDay(day)
        val min = t.dueMinuteOfDay ?: DayStart.quietEndMin(settings, date)
        return at(date, min, clock)
    }

    /**
     * Local wall-clock time on [day]. Built from the local date-time (not start of day + offset)
     * so DST days keep e.g. 09:00 at 09:00; a time inside a spring-forward gap moves later.
     */
    fun at(day: LocalDate, minuteOfDay: Int, clock: Clock): Long =
        day.atStartOfDay().plusMinutes(minuteOfDay.toLong()).atZone(clock.zone()).toInstant().toEpochMilli()

    fun offsets(cfg: AlarmConfigEntity): List<Int> =
        cfg.offsetsBeforeMin.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct()

    fun minutes(n: Int) = n * MINUTE
}
