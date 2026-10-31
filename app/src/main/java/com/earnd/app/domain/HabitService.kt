package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.RewardMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * A habit's progress for one day. [done] and [required] are comparable units; for quantity
 * habits [quantity] holds the amount logged so far against `habit.targetQuantity`.
 */
data class HabitDay(val habit: HabitEntity, val done: Int, val required: Int, val quantity: Double = 0.0) {
    val complete: Boolean get() = done >= required
}

class HabitService(
    private val habits: HabitDao,
    private val checkIns: CheckInDao,
    private val bank: TimeBank,
    private val clock: Clock,
    private val bus: ChangeBus,
    private val settings: SettingsRepository? = null,
) {
    fun isScheduled(h: HabitEntity, day: LocalDate): Boolean = HabitSchedule.isScheduled(h, day)

    private suspend fun settingsNow(): Settings = settings?.current() ?: Settings()

    private fun dayOf(h: HabitEntity, cs: List<CheckInEntity>) =
        HabitDay(h, HabitProgress.doneUnits(h, cs), HabitProgress.required(h), HabitProgress.quantity(cs))

    /** Habits scheduled today with their progress. "Today" is fixed at collection time. */
    fun observeToday(): Flow<List<HabitDay>> {
        val today = clock.today()
        return combine(habits.observeActive(), checkIns.observeForDay(today.toEpochDay())) { hs, cs ->
            val byHabit = cs.groupBy { it.habitId }
            val week = weekDoneBefore(hs, today)
            hs.filter { HabitSchedule.isDueOn(it, today, week[it.id] ?: 0) }.map { dayOf(it, byHabit[it.id].orEmpty()) }
        }
    }

    /** Per TIMES_PER_WEEK habit: complete days in [day]'s week before [day]. */
    private suspend fun weekDoneBefore(hs: List<HabitEntity>, day: LocalDate): Map<Long, Int> {
        val flexible = hs.filter { it.scheduleKind == com.earnd.app.data.entity.ScheduleKind.TIMES_PER_WEEK }
        val mon = HabitSchedule.weekStart(day)
        if (flexible.isEmpty() || mon == day) return emptyMap()
        val byHabitDay = checkIns.between(mon.toEpochDay(), day.toEpochDay() - 1).groupBy { it.habitId }
        return flexible.associate { h ->
            h.id to byHabitDay[h.id].orEmpty().groupBy { it.epochDay }.count { (_, cs) -> HabitProgress.isComplete(h, cs) }
        }
    }

    fun observeArchived(): Flow<List<HabitEntity>> = habits.observeArchived()

    /** Progress of every habit scheduled on [day]. */
    suspend fun daySummary(day: LocalDate): List<HabitDay> {
        val byHabit = checkIns.forDay(day.toEpochDay()).groupBy { it.habitId }
        val hs = habits.active()
        val week = weekDoneBefore(hs, day)
        return hs.filter { HabitSchedule.isDueOn(it, day, week[it.id] ?: 0) }.map { dayOf(it, byHabit[it.id].orEmpty()) }
    }

    /**
     * Records a check-in for today. Counted habits earn up to `timesPerDay` check-ins; quantity
     * habits add up amounts and earn once on reaching the target. In "only when everything is
     * done" reward mode single check-ins earn nothing; the day's lump sum is credited once all
     * scheduled habits are complete.
     */
    suspend fun checkIn(habitId: Long, quantity: Double? = null, photoPath: String? = null, creditBank: Boolean = true): CheckInEntity {
        val h = requireNotNull(habits.get(habitId)) { "No habit $habitId" }
        val s = settingsNow()
        val now = clock.now()
        val day = clock.localDate(now)
        val before = checkIns.forHabitDay(habitId, day.toEpochDay())
        val draft = CheckInEntity(habitId = habitId, epochDay = day.toEpochDay(), at = now, quantity = quantity,
            photoPath = photoPath, minutesEarned = 0)
        val perCheckIn = creditBank && s.rewardMode == RewardMode.PER_CHECKIN &&
            HabitProgress.earns(h, before, before + draft)
        val minutes = if (perCheckIn) HabitProgress.rewardMinutes(h, s) else 0
        val c = draft.copy(minutesEarned = minutes)
        val saved = c.copy(id = checkIns.insert(c))
        if (minutes > 0) bank.earn(minutes, LedgerKind.EARN_HABIT, OwnerKind.HABIT, saved.id)
        if (creditBank && s.rewardMode == RewardMode.ALL_DONE) settleDayBonus(day, s)
        bus.emit(ChangeKind.CHECKINS)
        return saved
    }

    /** Removes a check-in and reverses the minutes it earned. Returns its photo path, if any. */
    suspend fun undoCheckIn(checkInId: Long): String? {
        val c = checkIns.get(checkInId) ?: return null
        checkIns.deleteById(checkInId)
        bank.reverse(OwnerKind.HABIT, checkInId)
        settleDayBonus(LocalDate.ofEpochDay(c.epochDay), settingsNow())
        bus.emit(ChangeKind.CHECKINS)
        return c.photoPath
    }

    /** Credits or takes back the "all done" lump sum so it matches the day's state. */
    private suspend fun settleDayBonus(day: LocalDate, s: Settings) {
        if (day != clock.today()) return
        val source = dayBonusSource(day)
        val credited = bank.netFor(OwnerKind.HABIT, source) > 0
        val done = allDone(day)
        if (done && !credited && s.rewardMode == RewardMode.ALL_DONE) {
            val total = daySummary(day).sumOf { HabitProgress.dayValue(it.habit, s) }
            if (total > 0) bank.earn(total, LedgerKind.EARN_HABIT, OwnerKind.HABIT, source)
        } else if (!done && credited) {
            bank.reverse(OwnerKind.HABIT, source)
        }
    }

    /** True when at least one habit is scheduled on [day] and every scheduled one is complete. */
    suspend fun allDone(day: LocalDate): Boolean {
        val summary = daySummary(day)
        return summary.isNotEmpty() && summary.all { it.complete }
    }

    /** True when [segment] has at least one habit scheduled on [day] and all of them are complete. */
    suspend fun segmentDone(day: LocalDate, segment: DaySegment): Boolean {
        if (segment == DaySegment.NONE) return false
        val inSegment = daySummary(day).filter { it.habit.segment == segment }
        return inSegment.isNotEmpty() && inSegment.all { it.complete }
    }

    suspend fun anythingScheduled(day: LocalDate): Boolean = habits.active().any { isScheduled(it, day) }

    /** Whether [h] is complete on [day]; quantity habits compare the summed amount. */
    suspend fun isComplete(h: HabitEntity, day: LocalDate): Boolean =
        HabitProgress.isComplete(h, checkIns.forHabitDay(h.id, day.toEpochDay()))

    private suspend fun unitsByDay(h: HabitEntity, from: LocalDate, to: LocalDate): Map<Long, Int> =
        checkIns.forHabitBetween(h.id, from.toEpochDay(), to.toEpochDay())
            .groupBy { it.epochDay }.mapValues { (_, cs) -> HabitProgress.doneUnits(h, cs) }

    /** Current streak; vacation days are skipped. */
    suspend fun streak(habitId: Long, today: LocalDate): Int {
        val h = habits.get(habitId) ?: return 0
        val s = settingsNow()
        val counts = unitsByDay(h, today.minusDays(366), today)
        return HabitSchedule.streak(h, today, counts, skip = { s.onVacation(it.toEpochDay()) })
    }

    /** Best streak over the last [days] days; vacation days are skipped. */
    suspend fun bestStreak(habitId: Long, today: LocalDate, days: Long = 3 * 366L): Int {
        val h = habits.get(habitId) ?: return 0
        val s = settingsNow()
        val from = today.minusDays(days)
        return HabitSchedule.bestStreak(h, from, today, unitsByDay(h, from, today), skip = { s.onVacation(it.toEpochDay()) })
    }

    /**
     * Days in [from]..[to] where at least one habit was scheduled and every scheduled habit was
     * done. Vacation days do not count. Uses the current (non-archived) habits.
     */
    suspend fun perfectDays(from: LocalDate, to: LocalDate): List<LocalDate> {
        val s = settingsNow()
        val hs = habits.active()
        val byDay = checkIns.between(from.toEpochDay(), to.toEpochDay()).groupBy { it.epochDay }
        val out = mutableListOf<LocalDate>()
        var day = from
        while (!day.isAfter(to)) {
            val epochDay = day.toEpochDay()
            val scheduled = hs.filter { HabitSchedule.anchorOf(it) <= epochDay && isScheduled(it, day) }
            if (scheduled.isNotEmpty() && !s.onVacation(epochDay)) {
                val byHabit = byDay[epochDay].orEmpty().groupBy { it.habitId }
                if (scheduled.all { HabitProgress.isComplete(it, byHabit[it.id].orEmpty()) }) out += day
            }
            day = day.plusDays(1)
        }
        return out
    }

    suspend fun history(habitId: Long, fromDay: LocalDate, toDay: LocalDate): List<CheckInEntity> =
        checkIns.forHabitBetween(habitId, fromDay.toEpochDay(), toDay.toEpochDay())

    /** Saves a habit; new habits go to the end of the list. */
    suspend fun save(h: HabitEntity): Long {
        val toSave = if (h.id == 0L && h.sortOrder == 0) h.copy(sortOrder = habits.nextSortOrder()) else h
        val id = habits.upsert(toSave)
        bus.emit(ChangeKind.HABITS)
        return if (h.id != 0L) h.id else id
    }

    /** A new habit pre-filled with the global defaults ("I promise to prove it" means photo check-ins). */
    suspend fun newHabitDefaults(name: String = ""): HabitEntity {
        val s = settingsNow()
        val now = clock.now()
        return HabitEntity(
            name = name, iconKey = "check", colorArgb = 0xFFFFB000.toInt(),
            anchorEpochDay = clock.localDate(now).toEpochDay(),
            checkInMode = if (s.photoRequiredDefault) CheckInMode.PHOTO else CheckInMode.TAP,
            createdAt = now,
        )
    }

    /** Whether a check-in of [h] must carry a photo (habit mode, override or global default). */
    fun photoRequired(h: HabitEntity, s: Settings): Boolean =
        h.checkInMode == CheckInMode.PHOTO || HabitProgress.photoRequired(h, s)

    suspend fun archive(habitId: Long) {
        habits.archive(habitId)
        bus.emit(ChangeKind.HABITS)
    }

    suspend fun unarchive(habitId: Long) {
        habits.unarchive(habitId)
        bus.emit(ChangeKind.HABITS)
    }

    /** Persists a manual order: [idsInOrder] gets sortOrder 0, 1, 2, ... */
    suspend fun reorder(idsInOrder: List<Long>) {
        habits.reorder(idsInOrder)
        bus.emit(ChangeKind.HABITS)
    }

    /** Deletes the habit; its check-ins cascade. Earned minutes are kept. */
    suspend fun delete(habitId: Long) {
        val h = habits.get(habitId) ?: return
        habits.delete(h)
        bus.emit(ChangeKind.HABITS)
    }

    private companion object {
        /** Ledger source id for a day's "all done" lump sum; negative so it never matches a check-in. */
        fun dayBonusSource(day: LocalDate): Long = -(day.toEpochDay() + 1)
    }
}
