package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.HabitProgress
import com.earnd.app.domain.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** One habit's progress today for the 1x1 habit widget. */
internal object WidgetHabit {
    data class State(
        val id: Long,
        val name: String,
        val colorArgb: Int,
        val done: Int,
        val required: Int,
        val photo: Boolean,
        val quantity: Boolean,
        /** Several focus durations: the tap opens the app's picker. */
        val choice: Boolean = false,
    ) {
        val complete: Boolean get() = done >= required
    }

    /** Emits the current day, re-checking each minute so the widget resets after midnight. */
    private fun days(): Flow<Long> = flow {
        while (true) {
            emit(ServiceLocator.clock.today().toEpochDay())
            delay(60_000)
        }
    }.distinctUntilChanged()

    /** Live state; null when the habit is missing or archived. Room re-emits on every change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(habitId: Long?): Flow<State?> {
        if (habitId == null) return flowOf(null)
        val db = ServiceLocator.db
        return days().flatMapLatest { day ->
            combine(
                db.habitDao().observeActive(),
                db.checkInDao().observeForDay(day),
                ServiceLocator.settings.settings,
            ) { hs, cs, s ->
                val h = hs.firstOrNull { it.id == habitId }
                if (h == null) null else {
                    val mine = cs.filter { it.habitId == habitId }
                    State(
                        id = h.id,
                        name = h.name,
                        colorArgb = h.colorArgb,
                        done = HabitProgress.doneUnits(h, mine),
                        required = HabitProgress.required(h),
                        photo = ServiceLocator.habitService.photoRequired(h, s),
                        quantity = HabitProgress.isQuantity(h),
                        choice = com.earnd.app.domain.TimedHabit.needsChoice(h, s),
                    )
                }
            }
        }
    }

    /**
     * Live states for the multi-habit strip: [ids] in the chosen order (missing/archived ones are
     * skipped), or, when [ids] is empty, every habit due today in today's order.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMany(ids: List<Long>): Flow<List<State>> {
        val db = ServiceLocator.db
        return days().flatMapLatest { day ->
            combine(
                db.habitDao().observeActive(),
                db.checkInDao().observeForDay(day),
                ServiceLocator.settings.settings,
                ServiceLocator.habitService.observeToday(),
            ) { hs, cs, s, due ->
                val byId = hs.associateBy { it.id }
                val chosen = if (ids.isEmpty()) due.mapNotNull { byId[it.habit.id] } else ids.mapNotNull { byId[it] }
                chosen.map { h -> stateOf(h, cs.filter { it.habitId == h.id }, s) }
            }
        }
    }

    private fun stateOf(
        h: com.earnd.app.data.entity.HabitEntity,
        mine: List<com.earnd.app.data.entity.CheckInEntity>,
        s: com.earnd.app.data.Settings,
    ) = State(
        id = h.id,
        name = h.name,
        colorArgb = h.colorArgb,
        done = HabitProgress.doneUnits(h, mine),
        required = HabitProgress.required(h),
        photo = ServiceLocator.habitService.photoRequired(h, s),
        quantity = HabitProgress.isQuantity(h),
        choice = com.earnd.app.domain.TimedHabit.needsChoice(h, s),
    )

    /** What tapping a habit does; shared by the 1x1 widget and the strip. */
    fun tapAction(context: android.content.Context, state: State): androidx.glance.action.Action = when {
        state.complete && (state.quantity || state.choice) ->
            androidx.glance.appwidget.action.actionStartActivity(WidgetLinks.home(context))
        state.photo || state.quantity || state.choice ->
            androidx.glance.appwidget.action.actionStartActivity(WidgetLinks.checkIn(context, state.id))
        else -> androidx.glance.appwidget.action.actionRunCallback<HabitCheckInAction>(
            androidx.glance.action.actionParametersOf(HabitCheckInAction.HABIT_ID to state.id),
        )
    }

    suspend fun load(habitId: Long?): State? = observe(habitId).first()

    /** Short label for a 1x1 cell: the first word, trimmed. */
    fun shortName(name: String): String = name.trim().substringBefore(' ').take(10)
}
