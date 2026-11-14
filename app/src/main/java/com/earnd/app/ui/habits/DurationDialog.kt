package com.earnd.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.FocusOption
import com.earnd.app.domain.TimedHabit
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num

/**
 * Ticking a timed habit records what was actually done instead of starting the timer.
 * Several durations become one row each; a stopwatch habit asks for minutes.
 */
@Composable
internal fun DurationDialog(
    h: HabitEntity,
    options: List<FocusOption>,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val p = Earnd.palette
    val opts = options
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text(h.name) },
        text = {
            Column {
                Text(stringResource(R.string.home_duration_prompt), style = MaterialTheme.typography.bodyMedium, color = p.secondary)
                Spacer(Modifier.height(8.dp))
                if (opts.isEmpty()) {
                    OutlinedTextField(
                        text, { text = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.habits_minutes)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    opts.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onConfirm(o.minutes, o.reward) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Num(stringResource(R.string.focus_minutes, o.minutes), Modifier.weight(1f), color = p.text)
                            Num("+${o.reward}", color = p.amber)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (opts.isEmpty()) {
                TextButton(
                    enabled = minutes > 0,
                    onClick = { onConfirm(minutes, TimedHabit.stopwatchReward(h, minutes * 60_000L) ?: 0) },
                ) { Text(stringResource(R.string.common_ok)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
