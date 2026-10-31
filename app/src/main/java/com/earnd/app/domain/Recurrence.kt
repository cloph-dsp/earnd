package com.earnd.app.domain

import com.earnd.app.data.entity.TaskEntity
import java.time.LocalDate

/** Recurrence of tasks on top of [RRule]. The series start is the first due day. */
object Recurrence {
    fun rule(t: TaskEntity): RRule? = RRule.parseOrNull(t.repeatRule)

    fun start(t: TaskEntity, fallback: LocalDate): LocalDate =
        (t.repeatAnchorEpochDay ?: t.dueEpochDay)?.let(LocalDate::ofEpochDay) ?: fallback

    /** First scheduled occurrence strictly after [afterDay] (COUNT ignored), or null. */
    fun next(t: TaskEntity, afterDay: LocalDate): LocalDate? =
        rule(t)?.next(start(t, afterDay), afterDay)

    /**
     * Due day of the occurrence after [t] once it is completed or skipped on [on], or null when
     * the series ends (no rule, COUNT reached, past UNTIL).
     */
    fun following(t: TaskEntity, on: LocalDate): LocalDate? {
        val r = rule(t) ?: return null
        if (r.count != null && t.repeatOccurrence >= r.count) return null
        if (t.repeatFromCompletion) return r.afterCompletion(on)
        val due = t.dueEpochDay?.let(LocalDate::ofEpochDay) ?: on
        return r.next(start(t, due), maxOf(due, on))
    }

    /** Where an overdue open occurrence moves on [today]; null leaves it in place. */
    fun rolledForward(t: TaskEntity, today: LocalDate): LocalDate? {
        val r = rule(t) ?: return null
        if (t.repeatFromCompletion) return today
        return r.next(start(t, today), today.minusDays(1))
    }

    fun summary(t: TaskEntity): String? = rule(t)?.summary(
        t.repeatFromCompletion, (t.repeatAnchorEpochDay ?: t.dueEpochDay)?.let(LocalDate::ofEpochDay),
    )
}
