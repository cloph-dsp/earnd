package com.earnd.app.ui.tasks

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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Repeat
import com.earnd.app.ui.theme.Checkbox
import androidx.compose.material3.FilterChip
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

fun bucketLabel(b: TaskBucket) = when (b) {
    TaskBucket.OVERDUE -> "Overdue"
    TaskBucket.TODAY -> "Today"
    TaskBucket.UPCOMING -> "Upcoming"
    TaskBucket.NO_DATE -> "No date"
    TaskBucket.COMPLETED -> "Completed"
}

@Composable
fun TasksScreen(onOpenTask: (Long) -> Unit, onNewTask: () -> Unit) {
    val ts = ServiceLocator.taskService
    val lists = OPEN_BUCKETS.associateWith { b -> remember(b) { ts.observeBucket(b) }.collectAsState(initial = emptyList()).value }
    val completed by remember { ts.observeBucket(TaskBucket.COMPLETED) }.collectAsState(initial = emptyList())
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Text("Tasks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onNewTask) { Icon(Icons.Outlined.EditNote, "New task with details", tint = Earnd.palette.secondary) }
            }
            QuickAdd()
        }
        if (lists.values.all { it.isEmpty() }) {
            item { Text("Nothing open.", color = Earnd.palette.secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 20.dp)) }
        }
        OPEN_BUCKETS.forEach { b -> taskSection(b, lists[b].orEmpty(), onOpenTask) }
        if (completed.isNotEmpty()) {
            item {
                TextButton(onClick = { showCompleted = !showCompleted }) {
                    Text((if (showCompleted) "Hide" else "Show") + " completed (${completed.size})")
                }
            }
            if (showCompleted) items(completed, key = { "c${it.id}" }) { t -> TaskRow(t, onOpenTask) }
        }
    }
}

private fun LazyListScope.taskSection(b: TaskBucket, tasks: List<TaskEntity>, onOpen: (Long) -> Unit) {
    if (tasks.isEmpty()) return
    item(key = "h$b") { SectionTitle(bucketLabel(b)) }
    items(tasks, key = { it.id }) { t -> TaskRow(t, onOpen) }
}

@Composable
private fun QuickAdd() {
    var text by remember { mutableStateOf("") }
    var forToday by rememberSaveable { mutableStateOf(true) }
    val submit = {
        val title = text.trim()
        if (title.isNotEmpty()) {
            text = ""
            val sl = ServiceLocator
            launchUi {
                sl.taskService.save(TaskEntity(
                    title = title,
                    dueEpochDay = if (forToday) sl.clock.today().toEpochDay() else null,
                    createdAt = sl.clock.now(),
                ))
            }
        }
    }
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Add a task") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = { IconButton(onClick = { submit() }) { Icon(Icons.Outlined.Add, "Add") } },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = forToday, onClick = { forToday = !forToday }, label = { Text("Today") })
    }
}

@Composable
fun TaskRow(t: TaskEntity, onOpen: (Long) -> Unit) {
    val done = t.completedAt != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onOpen(t.id) },
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
                color = when {
                    done -> Earnd.palette.done
                    t.priority == Priority.HIGH -> Earnd.palette.red
                    else -> MaterialTheme.colorScheme.onBackground
                },
            )
            val meta = buildList {
                t.dueEpochDay?.let { add(formatDay(it) + (t.dueMinuteOfDay?.let { m -> " " + formatMinuteOfDay(m) } ?: "")) }
            }
            if (meta.isNotEmpty()) Num(meta.joinToString("  "), color = if (done) Earnd.palette.done else Earnd.palette.secondary)
        }
        t.rewardMinutes?.takeIf { it > 0 }?.let { Num("+$it", Modifier.padding(horizontal = 8.dp), color = if (done) Earnd.palette.done else Earnd.palette.amber) }
        if (t.repeats) Icon(Icons.Outlined.Repeat, "Repeats", tint = Earnd.palette.muted, modifier = Modifier.size(16.dp))
    }
}
