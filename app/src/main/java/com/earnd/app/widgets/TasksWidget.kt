package com.earnd.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.earnd.app.R
import com.earnd.app.ServiceLocator

/** 4x2 widget: pending tasks (overdue and today first); check one to complete it. */
class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = WidgetTasks.load()
        provideContent { Content(context, rows) }
    }

    @Composable
    private fun Content(context: Context, rows: List<WidgetTasks.Row>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(4.dp)
                .background(WidgetColors.surface).padding(12.dp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widgets_tasks_title),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    "+",
                    modifier = GlanceModifier.padding(horizontal = 10.dp)
                        .clickable(actionStartActivity(WidgetLinks.addTask(context))),
                    style = TextStyle(color = WidgetColors.primary, fontSize = 20.sp, fontWeight = FontWeight.Medium),
                )
            }
            if (rows.isEmpty()) {
                Text(
                    context.getString(R.string.widgets_tasks_empty),
                    modifier = GlanceModifier.padding(top = 8.dp),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
                )
            } else {
                LazyColumn {
                    items(rows, itemId = { it.id }) { row -> TaskRow(context, row) }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(context: Context, row: WidgetTasks.Row) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<CompleteTaskAction>(
                    actionParametersOf(CompleteTaskAction.TASK_ID to row.id),
                ),
                colors = CheckboxDefaults.colors(
                    checkedColor = WidgetColors.muted,
                    uncheckedColor = WidgetColors.onSurface,
                ),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                row.title,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
                    .clickable(actionStartActivity(WidgetLinks.task(context, row.id))),
                style = TextStyle(
                    color = if (row.overdue) WidgetColors.accent else WidgetColors.onSurface,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        ServiceLocator.taskService.complete(id)
        WidgetRefresher.updateAll(context)
    }

    companion object {
        val TASK_ID = ActionParameters.Key<Long>("widgets_task_id")
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresher.schedulePeriodic(context)
    }
}
