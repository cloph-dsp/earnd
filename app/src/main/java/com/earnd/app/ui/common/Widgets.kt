package com.earnd.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Single-choice chip row over any list of options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Weekday mask picker, bit0 = Monday .. bit6 = Sunday. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayPicker(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_LABELS.forEachIndexed { i, l ->
            val on = mask and (1 shl i) != 0
            FilterChip(selected = on, onClick = { onChange(mask xor (1 shl i)) }, label = { Text(l) })
        }
    }
}

@Composable
fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { t -> onChange(t.filter(Char::isDigit).take(5).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Minute-of-day text field ("HH:MM"); reports a value only once valid, null when cleared. */
@Composable
fun TimeField(label: String, minuteOfDay: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val state = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(minuteOfDay?.let(::formatMinuteOfDay) ?: "")
    }
    OutlinedTextField(
        value = state.value,
        onValueChange = { t ->
            state.value = t.take(5)
            val parsed = parseMinuteOfDay(t)
            if (parsed != null || t.isBlank()) onChange(parsed)
        },
        label = { Text(label) },
        placeholder = { Text("HH:MM") },
        singleLine = true,
        isError = state.value.isNotBlank() && parseMinuteOfDay(state.value) == null,
        modifier = modifier,
    )
}
