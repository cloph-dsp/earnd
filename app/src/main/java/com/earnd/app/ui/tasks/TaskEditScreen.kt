package com.earnd.app.ui.tasks

import com.earnd.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import com.earnd.app.ui.theme.Button
import com.earnd.app.ui.theme.Checkbox
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
import com.earnd.app.domain.RRule
import com.earnd.app.ui.common.RecurrencePicker
import java.time.LocalDate
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.today
import com.earnd.app.ui.alarms.AlarmConfigEditor
import com.earnd.app.ui.alarms.AlarmConfigs
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.ui.common.EarndChip
import com.earnd.app.ui.common.DateField
import com.earnd.app.ui.common.EditScaffold
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField
import com.earnd.app.ui.common.WeekdayPicker
import com.earnd.app.ui.common.launchUi
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
    val taskDefaultReward = remember { sl.settings.settings }.collectAsState(initial = com.earnd.app.data.Settings()).value.defaultTaskRewardMin
    val t = task ?: return
    val isNew = t.id == 0L
    val isSub = t.parentId != null
    val set: (TaskEntity) -> Unit = { task = it }
    val subtasks by remember(t.id) { if (t.id > 0) sl.taskService.observeSubtasks(t.id) else flowOf(emptyList()) }
        .collectAsState(initial = emptyList())

    val save: () -> Unit = {
        launchUi {
            val id = sl.taskService.save(t.copy(title = t.title.trim()))
            newSubtasks.filter { it.isNotBlank() }.forEachIndexed { i, s ->
                sl.taskService.save(TaskEntity(parentId = id, title = s.trim(), sortOrder = subtasks.size + i, createdAt = sl.clock.now()))
            }
            AlarmConfigs.apply(OwnerKind.TASK, id, alarm)
            onDone()
        }
    }

    EditScaffold(stringResource(if (isNew) R.string.tasks_edit_new else if (isSub) R.string.tasks_edit_subtask else R.string.tasks_edit_task), onBack = onDone, bottomBar = {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()) {
            Button(onClick = save, enabled = t.title.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.tasks_save))
            }
        }
    }) {
        OutlinedTextField(t.title, { set(t.copy(title = it)) }, label = { Text(stringResource(R.string.tasks_field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(t.notes, { set(t.copy(notes = it)) }, label = { Text(stringResource(R.string.tasks_field_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        if (!isSub) {
            SectionTitle(stringResource(R.string.tasks_section_due))
            DueEditor(t, set)

            SectionTitle(stringResource(R.string.tasks_section_priority))
            val priorityLabels = mapOf(
                Priority.NONE to stringResource(R.string.tasks_priority_none),
                Priority.LOW to stringResource(R.string.tasks_priority_low),
                Priority.MEDIUM to stringResource(R.string.tasks_priority_medium),
                Priority.HIGH to stringResource(R.string.tasks_priority_high),
            )
            ChoiceChips(Priority.values().toList(), t.priority, { priorityLabels.getValue(it) }) { set(t.copy(priority = it)) }

            SectionTitle(stringResource(R.string.tasks_section_repeat))
            RepeatEditor(t, set)

            SectionTitle(stringResource(R.string.tasks_section_reward))
            SwitchRow(stringResource(R.string.tasks_reward_switch), t.rewardMinutes != null, { on ->
                set(t.copy(rewardMinutes = if (on) (taskDefaultReward ?: 5) else null))
            }, hint = stringResource(R.string.tasks_reward_hint))
            t.rewardMinutes?.let { r -> NumberField(stringResource(R.string.tasks_reward_minutes), r, { set(t.copy(rewardMinutes = (it ?: 0).coerceIn(0, 600))) }) }

            SectionTitle(stringResource(R.string.tasks_section_subtasks))
            subtasks.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.completedAt != null, { c -> launchUi { if (c) sl.taskService.complete(s.id) else sl.taskService.uncomplete(s.id) } })
                    TextButton(onClick = { onOpenTask(s.id) }, modifier = Modifier.weight(1f)) { Text(s.title, modifier = Modifier.fillMaxWidth()) }
                    IconButton(onClick = { launchUi { sl.taskService.delete(s.id) } }) { Icon(Icons.Outlined.Close, stringResource(R.string.tasks_remove)) }
                }
            }
            newSubtasks.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(s, { newSubtasks[i] = it }, singleLine = true, placeholder = { Text(stringResource(R.string.tasks_edit_subtask)) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { newSubtasks.removeAt(i) }) { Icon(Icons.Outlined.Close, stringResource(R.string.tasks_remove)) }
                }
            }
            TextButton(onClick = { newSubtasks.add("") }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.tasks_add_subtask)) }

            SectionTitle(stringResource(R.string.tasks_section_alarm))
            SwitchRow(stringResource(R.string.tasks_alarm_switch), alarm != null, { on ->
                if (!on) alarm = null else launchUi { alarm = AlarmConfigs.defaults(OwnerKind.TASK).copy(ownerId = t.id) }
            }, hint = if (alarm == null) stringResource(R.string.tasks_alarm_default_hint) else null)
            alarm?.let { a -> AlarmConfigEditor(a) { alarm = it } }
            if (t.dueEpochDay == null && alarm != null) {
                Text(stringResource(R.string.tasks_alarm_needs_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        if (!isNew) {
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmDelete = true }) { Text(stringResource(R.string.tasks_delete)) }
                if (t.repeats && !isSub && t.completedAt == null) {
                    Button(onClick = { launchUi { sl.taskService.skip(t.id); onDone() } }) { Text(stringResource(R.string.tasks_skip_this_one)) }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tasks_delete_title)) },
            text = { Text(stringResource(if (isSub) R.string.tasks_delete_body_sub else R.string.tasks_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    launchUi { AlarmConfigs.clear(OwnerKind.TASK, t.id); sl.taskService.delete(t.id); onDone() }
                }) { Text(stringResource(R.string.tasks_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.tasks_keep)) } },
        )
    }
}

@Composable
private fun DueEditor(t: TaskEntity, set: (TaskEntity) -> Unit) {
    val today = remember { ServiceLocator.clock.today().toEpochDay() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarndChip(t.dueEpochDay == null, stringResource(R.string.tasks_due_no_date)) { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = null, dueMinuteOfDay = null)) }
        EarndChip(t.dueEpochDay == today, stringResource(R.string.tasks_due_today)) { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = today)) }
        EarndChip(t.dueEpochDay == today + 1, stringResource(R.string.tasks_due_tomorrow)) { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = today + 1)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(
            stringResource(R.string.tasks_due_date_label),
            t.dueEpochDay,
            { set(t.copy(repeatAnchorEpochDay = null, dueEpochDay = it)) },
            Modifier.weight(1f),
            placeholder = stringResource(R.string.tasks_due_pick),
        )
        if (t.dueEpochDay != null) {
            TimeField(stringResource(R.string.tasks_due_time_label), t.dueMinuteOfDay, { set(t.copy(dueMinuteOfDay = it)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RepeatEditor(t: TaskEntity, set: (TaskEntity) -> Unit) {
    val today = ServiceLocator.clock.today()
    val start = t.dueEpochDay?.let(LocalDate::ofEpochDay) ?: today
    RecurrencePicker(RRule.parseOrNull(t.repeatRule), t.repeatFromCompletion, start) { r, fromCompletion ->
        set(t.copy(
            repeatRule = r?.toString(),
            repeatFromCompletion = r != null && fromCompletion,
            repeatAnchorEpochDay = null,
            repeatOccurrence = 1,
            dueEpochDay = if (r != null && t.dueEpochDay == null) today.toEpochDay() else t.dueEpochDay,
        ))
    }
    if (t.repeats) {
        Text(stringResource(R.string.tasks_repeat_missed_hint), style = MaterialTheme.typography.bodySmall)
    }
}
