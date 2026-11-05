package com.earnd.app.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.BlockCause
import com.earnd.app.domain.HabitDay
import com.earnd.app.domain.HabitProgress
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.domain.SpendResult
import com.earnd.app.domain.TaskBucket
import com.earnd.app.domain.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One way to earn time, shown on the lock screen. */
sealed interface EarnItem {
    val minutes: Int
    /** [minutes] is what one check-in earns now (0 in "only when everything is done" mode). */
    data class Habit(val day: HabitDay, override val minutes: Int, val needsPhoto: Boolean, val needsApp: Boolean = needsPhoto) : EarnItem
    data class Task(val id: Long, val title: String, override val minutes: Int, val overdue: Boolean) : EarnItem
}

data class LockedUi(
    val balanceMinutes: Int = 0,
    val hardMode: Boolean = false,
    val unlockCapMin: Int? = null,
    val items: List<EarnItem> = emptyList(),
)

class LockedViewModel(val cause: BlockCause) : ViewModel() {
    private val bank = ServiceLocator.timeBank
    private val habits = ServiceLocator.habitService
    private val tasks = ServiceLocator.taskService

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Emits true once leisure apps are open again (session started or unlock reward). */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val taskItems = combine(
        tasks.observeBucket(TaskBucket.OVERDUE), tasks.observeBucket(TaskBucket.TODAY),
    ) { overdue, today ->
        (overdue.map { it to true } + today.map { it to false })
            .filter { (t, _) -> t.parentId == null && (t.rewardMinutes ?: 0) > 0 }
            .map { (t, late) -> EarnItem.Task(t.id, t.title, t.rewardMinutes ?: 0, late) }
    }

    private val habitItems = combine(habits.observeToday(), ServiceLocator.settings.settings) { days, s ->
        days.filter { !it.complete }.map { d ->
            val minutes = if (s.rewardMode == RewardMode.ALL_DONE) 0 else HabitProgress.rewardMinutes(d.habit, s)
            EarnItem.Habit(d, minutes, habits.photoRequired(d.habit, s), habits.checkInKind(d.habit, s) != com.earnd.app.domain.HabitService.CheckInKind.TAP)
        }.filter { it.minutes > 0 || s.rewardMode == RewardMode.ALL_DONE }
    }

    val ui: StateFlow<LockedUi> = combine(
        bank.observeBalance(), ServiceLocator.settings.settings, habitItems, taskItems,
    ) { bal, s, h, t -> LockedUi(bal.minutes, s.hardMode, s.unlockCapMin, h + t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockedUi())

    fun checkIn(habitId: Long) = viewModelScope.launch {
        habits.checkIn(habitId)
        tryAllDone()
    }

    fun completeTask(taskId: Long) = viewModelScope.launch { tasks.complete(taskId) }

    fun startSession(minutes: Int) = viewModelScope.launch { handle(bank.spend(minutes)) }

    fun startGrace() = viewModelScope.launch { handle(bank.startBedtimeGrace()) }

    fun clearMessage() { _message.value = null }

    /** After a check-in, everything for today may be done: honour the all-done reward. */
    private suspend fun tryAllDone() {
        val s = ServiceLocator.settings.current()
        if (cause != BlockCause.NO_BALANCE || !s.allDoneUnlock) return
        if (habits.allDone(ServiceLocator.clock.today())) handle(bank.startAllDoneUnlock())
    }

    private fun handle(r: SpendResult) {
        when (r) {
            is SpendResult.Started -> _unlocked.value = true
            is SpendResult.Refused -> _message.value = r.reason
        }
    }

    companion object {
        /** Sessions the lock screen offers, filtered to what the balance allows. */
        val SESSION_CHOICES = listOf(5, 10, 15, 30, 60)

    }
}
