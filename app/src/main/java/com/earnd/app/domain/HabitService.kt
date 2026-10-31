package com.earnd.app.domain

import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class HabitDay(val habit: HabitEntity, val done: Int, val required: Int) {
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

    /** Habits scheduled today with their progress. "Today" is fixed at collection time. */
    fun observeToday(): Flow<List<HabitDay>> {
        val today = clock.today()
        return combine(habits.observeActive(), checkIns.observeForDay(today.toEpochDay())) { hs, cs ->
            val counts = cs.groupingBy { it.habitId }.eachCount()
            hs.filter { isScheduled(it, today) }.map { HabitDay(it, counts[it.id] ?: 0, it.timesPerDay) }
        }
    }

    /**
     * Records a check-in for today. Minutes are earned only up to `timesPerDay` check-ins;
     * extra check-ins are kept for history but earn nothing.
     */
    suspend fun checkIn(habitId: Long, quantity: Double? = null, photoPath: String? = null, creditBank: Boolean = true): CheckInEntity {
        val h = requireNotNull(habits.get(habitId)) { "No habit $habitId" }
        val now = clock.now()
        val day = clock.localDate(now).toEpochDay()
        val earns = creditBank && checkIns.countFor(habitId, day) < h.timesPerDay
        val minutes = if (earns) h.minutesPerCheckIn.coerceAtLeast(0) else 0
        val c = CheckInEntity(habitId = habitId, epochDay = day, at = now, quantity = quantity,
            photoPath = photoPath, minutesEarned = minutes)
        val saved = c.copy(id = checkIns.insert(c))
        if (minutes > 0) bank.earn(minutes, LedgerKind.EARN_HABIT, OwnerKind.HABIT, saved.id)
        bus.emit(ChangeKind.CHECKINS)
        return saved
    }

    /** Removes a check-in and reverses the minutes it earned. Returns its photo path, if any. */
    suspend fun undoCheckIn(checkInId: Long): String? {
        val c = checkIns.get(checkInId) ?: return null
        checkIns.deleteById(checkInId)
        bank.reverse(OwnerKind.HABIT, checkInId)
        bus.emit(ChangeKind.CHECKINS)
        return c.photoPath
    }

    /** True when at least one habit is scheduled on [day] and every scheduled one is complete. */
    suspend fun allDone(day: LocalDate): Boolean {
        val scheduled = habits.active().filter { isScheduled(it, day) }
        if (scheduled.isEmpty()) return false
        val counts = checkIns.forDay(day.toEpochDay()).groupingBy { it.habitId }.eachCount()
        return scheduled.all { (counts[it.id] ?: 0) >= it.timesPerDay }
    }

    suspend fun anythingScheduled(day: LocalDate): Boolean = habits.active().any { isScheduled(it, day) }

    /** Current streak; vacation days are skipped. */
    suspend fun streak(habitId: Long, today: LocalDate): Int {
        val h = habits.get(habitId) ?: return 0
        val from = today.minusDays(366).toEpochDay()
        val counts = checkIns.forHabitBetween(habitId, from, today.toEpochDay())
            .groupingBy { it.epochDay }.eachCount()
        val s = settings?.current()
        return HabitSchedule.streak(h, today, counts, skip = { s?.onVacation(it.toEpochDay()) == true })
    }

    suspend fun history(habitId: Long, fromDay: LocalDate, toDay: LocalDate): List<CheckInEntity> =
        checkIns.forHabitBetween(habitId, fromDay.toEpochDay(), toDay.toEpochDay())

    suspend fun save(h: HabitEntity): Long {
        val id = habits.upsert(h)
        bus.emit(ChangeKind.HABITS)
        return if (h.id != 0L) h.id else id
    }

    suspend fun archive(habitId: Long) {
        habits.archive(habitId)
        bus.emit(ChangeKind.HABITS)
    }

    /** Deletes the habit; its check-ins cascade. Earned minutes are kept. */
    suspend fun delete(habitId: Long) {
        val h = habits.get(habitId) ?: return
        habits.delete(h)
        bus.emit(ChangeKind.HABITS)
    }
}
