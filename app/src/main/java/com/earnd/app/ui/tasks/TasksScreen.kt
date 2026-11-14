package com.earnd.app.ui.tasks

import com.earnd.app.i18n.Str
import com.earnd.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.RowDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import com.earnd.app.ui.theme.AddFab
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Repeat
import com.earnd.app.ui.theme.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.Priority
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.TaskBucket
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.formatDay
import com.earnd.app.ui.common.formatMinuteOfDay
import com.earnd.app.ui.common.launchUi

private val OPEN_BUCKETS = listOf(TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.UPCOMING, TaskBucket.NO_DATE)

private fun bucketLabelRes(b: TaskBucket) = when (b) {
    TaskBucket.OVERDUE -> R.string.tasks_bucket_overdue
    TaskBucket.TODAY -> R.string.tasks_bucket_today
    TaskBucket.UPCOMING -> R.string.tasks_bucket_upcoming
    TaskBucket.NO_DATE -> R.string.tasks_bucket_no_date
    TaskBucket.COMPLETED -> R.string.tasks_bucket_completed
}

fun bucketLabel(b: TaskBucket): String = Str.get(bucketLabelRes(b))

@Composable
fun TasksScreen(onOpenTask: (Long) -> Unit, onNewTask: () -> Unit) {
    val ts = ServiceLocator.taskService
    val lists = OPEN_BUCKETS.associateWith { b -> remember(b) { ts.observeBucket(b) }.collectAsState(initial = emptyList()).value }
    val completed by remember { ts.observeBucket(TaskBucket.COMPLETED) }.collectAsState(initial = emptyList())
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    // Same frame as the Habits tab: 22sp title row, quiet add button bottom right, flat rows.
    Scaffold(floatingActionButton = { AddFab(onClick = onNewTask, description = stringResource(R.string.tasks_new_with_details)) }) { pad ->
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_title), style = com.earnd.app.ui.theme.ScreenTitleStyle, modifier = Modifier.weight(1f))
            }
        }
        if (lists.values.all { it.isEmpty() }) {
            item { Text(stringResource(R.string.tasks_nothing_open), color = Earnd.palette.secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 20.dp)) }
        }
        OPEN_BUCKETS.forEach { b -> taskSection(b, lists[b].orEmpty(), onOpenTask) }
        if (completed.isNotEmpty()) {
            item {
                TextButton(onClick = { showCompleted = !showCompleted }) {
                    Text(stringResource(if (showCompleted) R.string.tasks_hide_completed else R.string.tasks_show_completed, completed.size))
                }
            }
            if (showCompleted) items(completed, key = { "c${it.id}" }) { t -> TaskRow(t, onOpenTask) }
        }
        item { Box(Modifier.size(88.dp)) }
    }
    }
}

private fun LazyListScope.taskSection(b: TaskBucket, tasks: List<TaskEntity>, onOpen: (Long) -> Unit) {
    if (tasks.isEmpty()) return
    item(key = "h$b") { SectionTitle(stringResource(bucketLabelRes(b))) }
    items(tasks, key = { it.id }) { t ->
        TaskRow(t, onOpen)
        com.earnd.app.ui.theme.RowDivider()
    }
}

@Composable
fun TaskRow(t: TaskEntity, onOpen: (Long) -> Unit) {
    val done = t.completedAt != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(t.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { c ->
                launchUi { if (c) ServiceLocator.taskService.complete(t.id) else ServiceLocator.taskService.uncomplete(t.id) }
            },
            reward = false,
            modifier = Modifier.offset(x = (-17).dp),
        )
        Column(Modifier.weight(1f).offset(x = (-17).dp).padding(vertical = 6.dp)) {
            Text(
                t.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) Earnd.palette.muted else MaterialTheme.colorScheme.onBackground,
            )
            val todayDay = remember { ServiceLocator.clock.today().toEpochDay() }
            val meta = buildList {
                // A task due today sits under the "Today" heading, so repeating the date is noise.
                t.dueEpochDay?.takeIf { it != todayDay }?.let { add(formatDay(it) + (t.dueMinuteOfDay?.let { m -> " " + formatMinuteOfDay(m) } ?: "")) }
            }
            if (meta.isNotEmpty() || t.repeats || (t.priority == Priority.HIGH && !done)) Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotEmpty()) Num(meta.joinToString("  "), color = if (done) Earnd.palette.done else Earnd.palette.secondary)
                if (t.repeats) Icon(Icons.Outlined.Repeat, stringResource(R.string.tasks_repeats), tint = Earnd.palette.muted, modifier = Modifier.padding(start = 8.dp).size(14.dp))
                // High priority is a property, not a lock: red stays reserved for the locked state and the primary action.
                if (t.priority == Priority.HIGH && !done) Num(stringResource(R.string.tasks_priority_high), Modifier.padding(start = 8.dp), color = Earnd.palette.secondary)
            }
        }
        t.rewardMinutes?.takeIf { it > 0 }?.let {
            Box(Modifier.padding(start = 8.dp).widthIn(min = 40.dp), contentAlignment = Alignment.CenterEnd) {
                Num("+$it", color = if (done) Earnd.palette.done else Earnd.palette.amber, style = com.earnd.app.ui.theme.NumberStyle.copy(fontSize = 14.sp))
            }
        }
    }
}
