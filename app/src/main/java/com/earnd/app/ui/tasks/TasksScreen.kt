package com.earnd.app.ui.tasks

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
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
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
import com.earnd.app.data.entity.RepeatKind
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
                TextButton(onClick = onNewTask) { Icon(Icons.Outlined.Add, null); Text("Details") }
            }
            QuickAdd()
        }
        if (lists.values.all { it.isEmpty() }) {
            item { Text("Nothing open. Nice and quiet.", modifier = Modifier.padding(vertical = 24.dp)) }
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
    Column {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Add a task") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = { IconButton(onClick = { submit() }) { Icon(Icons.Outlined.Add, "Add") } },
            modifier = Modifier.fillMaxWidth(),
        )
        FilterChip(selected = forToday, onClick = { forToday = !forToday }, label = { Text("Due today") })
    }
}

@Composable
fun TaskRow(t: TaskEntity, onOpen: (Long) -> Unit) {
    val done = t.completedAt != null
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { c ->
            launchUi { if (c) ServiceLocator.taskService.complete(t.id) else ServiceLocator.taskService.uncomplete(t.id) }
        })
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (t.priority == Priority.HIGH && !done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground,
            )
            val meta = buildList {
                t.dueEpochDay?.let { add(formatDay(it) + (t.dueMinuteOfDay?.let { m -> " " + formatMinuteOfDay(m) } ?: "")) }
                t.rewardMinutes?.takeIf { it > 0 }?.let { add("+$it min") }
            }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (t.repeatKind != RepeatKind.NONE) Icon(Icons.Outlined.Repeat, "Repeats", modifier = Modifier.padding(8.dp))
    }
}
