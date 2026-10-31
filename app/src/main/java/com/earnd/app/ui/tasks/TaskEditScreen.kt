package com.earnd.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import com.earnd.app.ui.theme.Button
import com.earnd.app.ui.theme.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.Priority
import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.today
import com.earnd.app.ui.alarms.AlarmConfigEditor
import com.earnd.app.ui.alarms.AlarmConfigs
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.ui.common.EditScaffold
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField
import com.earnd.app.ui.common.WeekdayPicker
import com.earnd.app.ui.common.formatIsoDay
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.common.parseIsoDay
import kotlinx.coroutines.flow.flowOf

@Composable
fun TaskEditScreen(taskId: Long, onDone: () -> Unit, onOpenTask: (Long) -> Unit) {
    val sl = ServiceLocator
    var task by remember { mutableStateOf<TaskEntity?>(null) }
    var alarm by remember { mutableStateOf<AlarmConfigEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val newSubtasks = remember { mutableStateListOf<String>() }
    LaunchedEffect(taskId) {
        task = (if (taskId > 0) sl.taskService.get(taskId) else null) ?: TaskEntity(
            title = "", createdAt = sl.clock.now(),
            rewardMinutes = sl.settings.current().defaultTaskRewardMin,
        )
        alarm = AlarmConfigs.custom(OwnerKind.TASK, taskId)
    }
    val t = task ?: return
    val isNew = t.id == 0L
    val isSub = t.parentId != null
    val set: (TaskEntity) -> Unit = { task = it }
    val subtasks by remember(t.id) { if (t.id > 0) sl.taskService.observeSubtasks(t.id) else flowOf(emptyList()) }
        .collectAsState(initial = emptyList())

    EditScaffold(if (isNew) "New task" else if (isSub) "Subtask" else "Task", onBack = onDone, actions = {
        TextButton(enabled = t.title.isNotBlank(), onClick = {
            launchUi {
                val id = sl.taskService.save(t.copy(title = t.title.trim()))
                newSubtasks.filter { it.isNotBlank() }.forEachIndexed { i, s ->
                    sl.taskService.save(TaskEntity(parentId = id, title = s.trim(), sortOrder = subtasks.size + i, createdAt = sl.clock.now()))
                }
                AlarmConfigs.apply(OwnerKind.TASK, id, alarm)
                onDone()
            }
        }) { Text("Save") }
    }) {
        OutlinedTextField(t.title, { set(t.copy(title = it)) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(t.notes, { set(t.copy(notes = it)) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        if (!isSub) {
            SectionTitle("Due")
            DueEditor(t, set)

            SectionTitle("Priority")
            ChoiceChips(Priority.values().toList(), t.priority, {
                when (it) { Priority.NONE -> "None"; Priority.LOW -> "Low"; Priority.MEDIUM -> "Medium"; Priority.HIGH -> "High" }
            }) { set(t.copy(priority = it)) }

            SectionTitle("Repeat")
            RepeatEditor(t, set)

            SectionTitle("Reward")
            SwitchRow("Earn minutes for this task", t.rewardMinutes != null, { on ->
                set(t.copy(rewardMinutes = if (on) 5 else null))
            }, hint = "Tasks never lock anything on their own.")
            t.rewardMinutes?.let { r -> NumberField("Minutes earned", r, { set(t.copy(rewardMinutes = (it ?: 0).coerceIn(0, 600))) }) }

            SectionTitle("Subtasks")
            subtasks.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.completedAt != null, { c -> launchUi { if (c) sl.taskService.complete(s.id) else sl.taskService.uncomplete(s.id) } })
                    TextButton(onClick = { onOpenTask(s.id) }, modifier = Modifier.weight(1f)) { Text(s.title, modifier = Modifier.fillMaxWidth()) }
                    IconButton(onClick = { launchUi { sl.taskService.delete(s.id) } }) { Icon(Icons.Outlined.Close, "Remove") }
                }
            }
            newSubtasks.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(s, { newSubtasks[i] = it }, singleLine = true, placeholder = { Text("Subtask") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { newSubtasks.removeAt(i) }) { Icon(Icons.Outlined.Close, "Remove") }
                }
            }
            TextButton(onClick = { newSubtasks.add("") }) { Icon(Icons.Outlined.Add, null); Text("Add subtask") }

            SectionTitle("Alarm")
            SwitchRow("Custom alarm for this task", alarm != null, { on ->
                if (!on) alarm = null else launchUi { alarm = AlarmConfigs.defaults(OwnerKind.TASK).copy(ownerId = t.id) }
            }, hint = if (alarm == null) "Uses your default alarm settings." else null)
            alarm?.let { a -> AlarmConfigEditor(a) { alarm = it } }
            if (t.dueEpochDay == null && alarm != null) {
                Text("Alarms need a due date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        if (!isNew) {
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            text = { Text(if (isSub) "This removes the subtask." else "Subtasks are removed too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    launchUi { AlarmConfigs.clear(OwnerKind.TASK, t.id); sl.taskService.delete(t.id); onDone() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun DueEditor(t: TaskEntity, set: (TaskEntity) -> Unit) {
    val today = remember { ServiceLocator.clock.today().toEpochDay() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(t.dueEpochDay == null, { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = null, dueMinuteOfDay = null)) }, { Text("No date") })
        FilterChip(t.dueEpochDay == today, { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = today)) }, { Text("Today") })
        FilterChip(t.dueEpochDay == today + 1, { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = today + 1)) }, { Text("Tomorrow") })
    }
    if (t.dueEpochDay != null) {
        var text by remember(t.dueEpochDay) { mutableStateOf(formatIsoDay(t.dueEpochDay)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { s -> text = s; parseIsoDay(s)?.let { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = it)) } },
                label = { Text("Date (YYYY-MM-DD)") }, singleLine = true,
                isError = parseIsoDay(text) == null, modifier = Modifier.weight(1f),
            )
            TimeField("Time (optional)", t.dueMinuteOfDay, { set(t.copy(dueMinuteOfDay = it)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RepeatEditor(t: TaskEntity, set: (TaskEntity) -> Unit) {
    ChoiceChips(RepeatKind.values().toList(), t.repeatKind, {
        when (it) {
            RepeatKind.NONE -> "Once"; RepeatKind.DAILY -> "Daily"; RepeatKind.WEEKLY -> "Weekly"
            RepeatKind.MONTHLY -> "Monthly"; RepeatKind.EVERY_N_DAYS -> "Every few days"; RepeatKind.YEARLY -> "Yearly"
        }
    }) { k ->
        val today = ServiceLocator.clock.today()
        set(t.copy(
            repeatKind = k,
            repeatAnchorEpochDay = null,
            repeatWeekdayMask = if (k == RepeatKind.WEEKLY && t.repeatWeekdayMask == 0) 1 shl (today.dayOfWeek.value - 1) else t.repeatWeekdayMask,
            dueEpochDay = if (k != RepeatKind.NONE && t.dueEpochDay == null) today.toEpochDay() else t.dueEpochDay,
        ))
    }
    when (t.repeatKind) {
        RepeatKind.WEEKLY -> {
            WeekdayPicker(t.repeatWeekdayMask) { set(t.copy(repeatWeekdayMask = it)) }
            NumberField("Every how many weeks", t.repeatInterval, { set(t.copy(repeatInterval = (it ?: 1).coerceAtLeast(1), repeatAnchorEpochDay = null)) })
        }
        RepeatKind.MONTHLY -> NumberField("Every how many months", t.repeatInterval, { set(t.copy(repeatInterval = (it ?: 1).coerceAtLeast(1))) })
        RepeatKind.EVERY_N_DAYS -> NumberField("Every how many days", t.repeatInterval, { set(t.copy(repeatInterval = (it ?: 1).coerceAtLeast(1))) })
        else -> Unit
    }
    if (t.repeatKind != RepeatKind.NONE) {
        Text("If a day is missed, it moves on to the next one by itself.", style = MaterialTheme.typography.bodySmall)
    }
}
