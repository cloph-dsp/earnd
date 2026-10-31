package com.earnd.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.earnd.app.R
import com.earnd.app.ServiceLocator

/** 1x1 widget: one chosen habit's progress today; tap to check in. */
class HabitWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialHabit = getAppWidgetState<Preferences>(context, id)[HABIT_ID]
        val initial = WidgetHabit.load(initialHabit)
        provideContent {
            // The habit id is read from Glance state so a reconfigure recomposes; progress is
            // observed live, since data captured before provideContent would go stale.
            val habitId = currentState<Preferences>()[HABIT_ID]
            val flow = remember(habitId) { WidgetHabit.observe(habitId) }
            val state by flow.collectAsState(if (habitId == initialHabit) initial else null)
            Content(context, appWidgetId, state)
        }
    }

    @Composable
    private fun Content(context: Context, appWidgetId: Int, state: WidgetHabit.State?) {
        val base = GlanceModifier.fillMaxSize().cornerRadius(4.dp)
        if (state == null) {
            Column(
                modifier = base.background(WidgetColors.surface).padding(6.dp)
                    .clickable(actionStartActivity(HabitWidgetConfigActivity.intent(context, appWidgetId))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", style = TextStyle(color = WidgetColors.primary, fontSize = 22.sp, fontWeight = FontWeight.Medium))
                Text(
                    context.getString(R.string.widgets_habit_choose),
                    maxLines = 2,
                    style = TextStyle(color = WidgetColors.muted, fontSize = 10.sp, textAlign = TextAlign.Center),
                )
            }
            return
        }
        val habitColor = ColorProvider(Color(state.colorArgb).copy(alpha = 1f))
        val tap: Action = when {
            state.complete -> actionStartActivity(WidgetLinks.home(context))
            state.photo || state.quantity -> actionStartActivity(WidgetLinks.checkIn(context, state.id))
            else -> actionRunCallback<HabitCheckInAction>(
                actionParametersOf(HabitCheckInAction.HABIT_ID to state.id),
            )
        }
        Column(
            modifier = base.background(if (state.complete) habitColor else WidgetColors.surface)
                .padding(6.dp).clickable(tap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (state.complete) WidgetColors.onPrimary else WidgetColors.onSurface
            Text(
                if (state.complete) "✓" else "${state.done}/${state.required}",
                style = TextStyle(
                    color = if (state.complete) fg else habitColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                WidgetHabit.shortName(state.name),
                maxLines = 1,
                style = TextStyle(color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
            if (!state.complete) {
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = state.done.toFloat() / state.required.coerceAtLeast(1),
                    modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                    color = habitColor,
                    backgroundColor = WidgetColors.muted,
                )
            }
        }
    }

    companion object {
        val HABIT_ID = longPreferencesKey("widgets_habit_id")
    }
}

/** Quick check-in from the widget; photo habits are sent to the app's check-in screen instead. */
class HabitCheckInAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT_ID] ?: return
        val habit = ServiceLocator.db.habitDao().get(id)
        if (habit == null || habit.archived) return
        val s = ServiceLocator.settings.current()
        if (ServiceLocator.habitService.photoRequired(habit, s)) {
            context.startActivity(WidgetLinks.checkIn(context, id))
            return
        }
        ServiceLocator.habitService.checkIn(id)
        WidgetRefresher.updateAll(context)
    }

    companion object {
        val HABIT_ID = ActionParameters.Key<Long>("widgets_habit_checkin_id")
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresher.schedulePeriodic(context)
    }
}
