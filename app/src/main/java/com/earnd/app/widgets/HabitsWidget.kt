package com.earnd.app.widgets

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.earnd.app.R
import kotlinx.coroutines.flow.first

/** Resizable "Habits" strip (default 4x1, min 2x1): a row or grid of habit cells; tap acts like the 1x1 widget. */
class HabitsWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialRaw = getAppWidgetState<Preferences>(context, id)[HABIT_IDS]
        val initial = runCatching { WidgetHabit.observeMany(parseIds(initialRaw)).first() }.getOrNull()
        provideContent {
            // Ids come from Glance state so a reconfigure recomposes; progress is observed live.
            val raw = currentState<Preferences>()[HABIT_IDS]
            val flow = remember(raw) { WidgetHabit.observeMany(parseIds(raw)) }
            val states by flow.collectAsState(if (raw == initialRaw) initial.orEmpty() else emptyList())
            Content(context, appWidgetId, states)
        }
    }

    @Composable
    private fun Content(context: Context, appWidgetId: Int, states: List<WidgetHabit.State>) {
        val size = LocalSize.current
        val radius = if (Build.VERSION.SDK_INT >= 31) {
            GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            GlanceModifier.cornerRadius(16.dp)
        }
        val root = GlanceModifier.fillMaxSize().then(radius).background(WidgetColors.surface).padding(PAD)
        if (states.isEmpty()) {
            Box(
                modifier = root.clickable(actionStartActivity(HabitsWidgetConfigActivity.intent(context, appWidgetId))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    context.getString(R.string.widgets_habits_empty),
                    maxLines = 2,
                    style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp, textAlign = TextAlign.Center),
                )
            }
            return
        }
        val innerW = size.width - PAD * 2
        val innerH = size.height - PAD * 2
        val maxCols = (innerW.value / MIN_CELL_W).toInt().coerceAtLeast(1)
        val maxRows = (innerH.value / MIN_CELL_H).toInt().coerceAtLeast(1)
        // Fewer habits than slots: shrink the grid so the cells share all the space.
        val n = states.size
        val rows = if (n >= maxCols * maxRows) maxRows else ((n + maxCols - 1) / maxCols).coerceIn(1, maxRows)
        val cols = if (n >= maxCols * maxRows) maxCols else ((n + rows - 1) / rows).coerceIn(1, maxCols)
        val capacity = cols * rows
        val overflow = states.size > capacity && capacity > 1
        val shown = if (overflow) states.take(capacity - 1) else states.take(capacity)
        val hidden = states.size - shown.size
        val compact = innerH.value / rows < TWO_LINE_H

        // Row-major slots; the "+N" cell, when needed, is last; trailing slots stay empty.
        val slots: List<Any?> = (shown + listOfNotNull(if (overflow) hidden else null))
            .let { it + List((capacity - it.size).coerceAtLeast(0)) { null } }
        Column(modifier = root) {
            for (r in 0 until rows) {
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (c in 0 until cols) {
                        if (c > 0) {
                            Box(GlanceModifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp)) {
                                Box(GlanceModifier.fillMaxSize().background(WidgetColors.muted)) {}
                            }
                        }
                        val cell = GlanceModifier.defaultWeight().fillMaxHeight()
                        when (val slot = slots.getOrNull(r * cols + c)) {
                            is WidgetHabit.State -> HabitCell(context, cell, slot, compact)
                            is Int -> MoreCell(context, cell, slot)
                            else -> Spacer(cell)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HabitCell(context: Context, modifier: GlanceModifier, s: WidgetHabit.State, compact: Boolean) {
        val countColor = if (s.complete) WidgetColors.muted else WidgetColors.primary
        val nameColor = if (s.complete) WidgetColors.muted else WidgetColors.onSurface
        val count = if (s.complete && s.done <= s.required) "✓" else "${s.done}/${s.required}"
        val desc = context.getString(
            if (s.complete) R.string.widgets_habits_cell_done else R.string.widgets_habits_cell_pending,
            s.name, s.done, s.required,
        )
        val countStyle = TextStyle(
            color = countColor,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
        val nameStyle = TextStyle(color = nameColor, fontSize = if (compact) 12.sp else 11.sp, fontWeight = FontWeight.Medium)
        val tap = WidgetHabit.tapAction(context, s)
        if (compact) {
            // Short cells: "1/3 Lavar" on one line, no progress bar.
            Row(
                modifier = modifier.padding(horizontal = 4.dp).clickable(tap).semantics { contentDescription = desc },
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(count, maxLines = 1, style = countStyle)
                Spacer(GlanceModifier.width(4.dp))
                Text(s.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = nameStyle)
            }
        } else {
            Column(
                modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp).clickable(tap)
                    .semantics { contentDescription = desc },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(count, maxLines = 1, style = countStyle)
                Text(s.name, maxLines = 1, modifier = GlanceModifier.fillMaxWidth(), style = nameStyle.copy(textAlign = TextAlign.Center))
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = s.done.toFloat() / s.required.coerceAtLeast(1),
                    modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                    color = if (s.complete) WidgetColors.muted else ColorProvider(Color(s.colorArgb).copy(alpha = 1f)),
                    backgroundColor = WidgetColors.tile,
                )
            }
        }
    }

    @Composable
    private fun MoreCell(context: Context, modifier: GlanceModifier, hidden: Int) {
        Box(
            modifier = modifier.clickable(actionStartActivity(WidgetLinks.home(context)))
                .semantics { contentDescription = context.getString(R.string.widgets_habits_more_desc, hidden) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+$hidden",
                maxLines = 1,
                style = TextStyle(color = WidgetColors.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
            )
        }
    }

    companion object {
        /** Chosen habit ids in order, comma-separated; absent or empty means "all due today". */
        val HABIT_IDS = stringPreferencesKey("widgets_habits_ids")
        private val PAD = 8.dp
        private const val MIN_CELL_W = 64f
        private const val MIN_CELL_H = 40f
        private const val TWO_LINE_H = 52f

        fun parseIds(raw: String?): List<Long> =
            raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

        fun encodeIds(ids: List<Long>): String = ids.joinToString(",")
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresher.schedulePeriodic(context)
    }
}
