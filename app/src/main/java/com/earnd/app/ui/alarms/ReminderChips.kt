package com.earnd.app.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.data.entity.AlarmConfigEntity

/**
 * Task reminders as removable chips ("At due time", "1 h before", ...) plus an "Add reminder" picker.
 * Offsets are minutes before the due instant. All-day tasks use the default due time from settings,
 * so "N days before at HH:MM" is not expressible yet (follow-up: needs an absolute-time field).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderChips(cfg: AlarmConfigEntity, onChange: (AlarmConfigEntity) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val offsets = ReminderOffsets.parse(cfg.offsetsBeforeMin)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (cfg.atDue) RemovableChip(ReminderOffsets.label(0)) { onChange(cfg.copy(atDue = false)) }
        offsets.forEach { m ->
            RemovableChip(ReminderOffsets.label(m)) { onChange(cfg.copy(offsetsBeforeMin = ReminderOffsets.remove(cfg.offsetsBeforeMin, m))) }
        }
        AssistChip(onClick = { picking = true }, label = { Text(stringResource(R.string.alarmsui_add_reminder)) })
    }
    if (cfg.atDue.not() && offsets.isEmpty()) {
        Text(stringResource(R.string.alarmsui_no_reminders), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (picking) {
        ReminderPickerDialog(
            onDismiss = { picking = false },
            onPick = { m ->
                picking = false
                onChange(if (m == 0) cfg.copy(atDue = true) else cfg.copy(offsetsBeforeMin = ReminderOffsets.add(cfg.offsetsBeforeMin, m)))
            },
        )
    }
}

@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit) {
    InputChip(selected = false, onClick = onRemove, label = { Text(text) }, trailingIcon = { Text("×") })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPickerDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var amount by remember { mutableStateOf("30") }
    var unit by remember { mutableStateOf(ReminderOffsets.Unit.MINUTES) }
    var menu by remember { mutableStateOf(false) }
    val total = amount.toIntOrNull()?.let { it * unit.minutes }
    val valid = total != null && total in 1..ReminderOffsets.MAX_MIN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarmsui_remind_me)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = { onPick(0) }, label = { Text(stringResource(R.string.alarmsui_at_due_time)) })
                    ReminderOffsets.PRESETS.forEach { m ->
                        AssistChip(onClick = { onPick(m) }, label = { Text(ReminderOffsets.duration(m)) })
                    }
                }
                Text(stringResource(R.string.alarmsui_custom), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                    )
                    Column {
                        OutlinedButton(onClick = { menu = true }) { Text(unit.label + " ▾") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            ReminderOffsets.Unit.values().forEach { u ->
                                DropdownMenuItem(text = { Text(u.label) }, onClick = { unit = u; menu = false })
                            }
                        }
                    }
                    Text(stringResource(R.string.alarmsui_before_word))
                }
                if (valid) Text(ReminderOffsets.label(total!!), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onPick(total!!) }) { Text(stringResource(R.string.alarmsui_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.alarmsui_cancel)) } },
    )
}
