package com.earnd.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.domain.today
import com.earnd.app.ui.alarms.AlarmConfigEditor
import com.earnd.app.ui.alarms.AlarmConfigs
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.ui.common.EditScaffold
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.WeekdayPicker
import com.earnd.app.ui.common.launchUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(habitId: Long, onDone: () -> Unit) {
    val sl = ServiceLocator
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var alarm by remember { mutableStateOf<AlarmConfigEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(habitId) {
        habit = sl.db.habitDao().get(habitId) ?: HabitEntity(
            name = "", iconKey = "star", colorArgb = HabitLook.colors.first(),
            anchorEpochDay = sl.clock.today().toEpochDay(), createdAt = sl.clock.now(),
        )
        alarm = AlarmConfigs.custom(OwnerKind.HABIT, habitId)
    }
    val h = habit ?: return
    val isNew = h.id == 0L
    val set: (HabitEntity) -> Unit = { habit = it }

    EditScaffold(if (isNew) "New habit" else "Edit habit", onBack = onDone, actions = {
        TextButton(enabled = h.name.isNotBlank(), onClick = {
            launchUi {
                val id = sl.habitService.save(h.copy(name = h.name.trim()))
                AlarmConfigs.apply(OwnerKind.HABIT, id, alarm)
                onDone()
            }
        }) { Text("Save") }
    }) {
        OutlinedTextField(h.name, { set(h.copy(name = it)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionTitle("Look")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitLook.icons.forEach { (key, icon) ->
                val on = key == h.iconKey
                Box(
                    Modifier.size(40.dp)
                        .background(if (on) Color(h.colorArgb) else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable { set(h.copy(iconKey = key)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, key, tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitLook.colors.forEach { c ->
                Box(
                    Modifier.size(32.dp).background(Color(c), CircleShape)
                        .then(if (c == h.colorArgb) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                        .clickable { set(h.copy(colorArgb = c)) },
                )
            }
        }

        SectionTitle("When")
        ChoiceChips(ScheduleKind.values().toList(), h.scheduleKind, {
            when (it) { ScheduleKind.DAILY -> "Every day"; ScheduleKind.WEEKDAYS -> "Some days"; ScheduleKind.EVERY_N_DAYS -> "Every few days" }
        }) { set(h.copy(scheduleKind = it)) }
        when (h.scheduleKind) {
            ScheduleKind.WEEKDAYS -> WeekdayPicker(h.weekdayMask) { set(h.copy(weekdayMask = it)) }
            ScheduleKind.EVERY_N_DAYS -> NumberField("Every how many days", h.everyNDays, { set(h.copy(everyNDays = (it ?: 1).coerceAtLeast(1))) })
            ScheduleKind.DAILY -> Unit
        }
        Text("Part of the day", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(DaySegment.values().toList(), h.segment, HabitLook::segmentLabel) { set(h.copy(segment = it)) }
        NumberField("Times per day", h.timesPerDay, { set(h.copy(timesPerDay = (it ?: 1).coerceIn(1, 50))) }, Modifier.padding(top = 8.dp))

        SectionTitle("Amount (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = h.targetQuantity?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "",
                onValueChange = { t -> set(h.copy(targetQuantity = t.toDoubleOrNull())) },
                label = { Text("Target") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(h.unit.orEmpty(), { set(h.copy(unit = it.ifBlank { null })) }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
        }

        SectionTitle("Reward and check-in")
        NumberField("Minutes earned per check-in", h.minutesPerCheckIn, { set(h.copy(minutesPerCheckIn = (it ?: 0).coerceIn(0, 600))) })
        SwitchRow(
            "Check in with a photo", h.checkInMode == CheckInMode.PHOTO,
            { set(h.copy(checkInMode = if (it) CheckInMode.PHOTO else CheckInMode.TAP)) },
            hint = "Taking the picture is the commitment. Photos stay on this phone.",
        )

        SectionTitle("Reminder")
        SwitchRow("Custom reminder for this habit", alarm != null, { on ->
            if (!on) alarm = null else launchUi { alarm = AlarmConfigs.defaults(OwnerKind.HABIT).copy(ownerId = h.id) }
        }, hint = if (alarm == null) "Uses your default reminder settings." else null)
        alarm?.let { a -> AlarmConfigEditor(a) { alarm = it } }

        if (!isNew) {
            SectionTitle("Manage")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launchUi { sl.habitService.archive(h.id); onDone() } }) { Text("Archive") }
                Button(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${h.name}?") },
            text = { Text("Its history goes too. Archiving keeps the history instead.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    launchUi { AlarmConfigs.clear(OwnerKind.HABIT, h.id); sl.habitService.delete(h.id); onDone() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
