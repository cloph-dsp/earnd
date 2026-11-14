package com.earnd.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.today
import com.earnd.app.ui.theme.Earnd
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 28.dp, bottom = 6.dp),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EarndSwitch(checked, onChange, enabled)
    }
}

/** Square switch: the Material pill would be the only rounded control in an app built on right angles. */
@Composable
private fun EarndSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    val p = Earnd.palette
    // 48dp touch target around the 44x24 visual: the drawn box stays small, the finger does not.
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp, 24.dp)
                .alpha(if (enabled) 1f else 0.4f)
                // On is a neutral fill: amber means earned time, never "this switch is on".
                .background(if (checked) p.text else Color.Transparent, RectangleShape)
                .border(1.dp, if (checked) p.text else p.edge, RectangleShape)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(16.dp).background(if (checked) p.bg else p.edge, RectangleShape))
        }
    }
}

/** Single-choice chip row over any list of options. Square, like every other control. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            EarndChip(selected = o == selected, text = label(o)) { onSelect(o) }
        }
    }
}

/**
 * One chip rule for the whole app: a raised fill and a bright hairline when chosen, a dim
 * hairline when not. Selection is neutral on purpose — amber stays reserved for earned time.
 */
@Composable
internal fun EarndChip(selected: Boolean, text: String, onClick: () -> Unit) {
    val p = Earnd.palette
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = RectangleShape,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = p.secondary,
            selectedContainerColor = p.raised,
            selectedLabelColor = p.text,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) p.text else p.edge),
    )
}

/** Short weekday names for Monday..Sunday in the current locale ("Mon" in English, "seg." in Portuguese). */
private fun weekdayLabels(locale: java.util.Locale): List<String> =
    java.time.DayOfWeek.values().map { d ->
        d.getDisplayName(java.time.format.TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }
    }

/** Weekday mask picker, bit0 = Monday .. bit6 = Sunday. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayPicker(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekdayLabels(java.util.Locale.getDefault()).forEachIndexed { i, l ->
            val on = mask and (1 shl i) != 0
            EarndChip(selected = on, text = l) { onChange(mask xor (1 shl i)) }
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

/**
 * Minute-of-day field. Tapping it opens the clock picker: typing "18:30" into a text box is the
 * slowest way to answer "when", and it is the one place a typo silently means a different time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minuteOfDay: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = minuteOfDay?.let(::formatMinuteOfDay) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(timeHint()) },
            trailingIcon = { Icon(Icons.Outlined.Schedule, null, tint = Earnd.palette.muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A readOnly field still takes taps for the cursor, so an overlay is what actually opens the picker.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val start = minuteOfDay ?: DEFAULT_TIME_MIN
        val state = rememberTimePickerState(
            initialHour = start / 60,
            initialMinute = start % 60,
            is24Hour = UiFormats.value.is24,
        )
        // A plain Dialog, not AlertDialog: the alert's text slot squeezes the dial into ~250dp and the
        // numbers collide. The picker needs its own 328dp.
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RectangleShape,
                modifier = Modifier.width(328.dp).border(1.dp, Earnd.palette.edge, RectangleShape),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) }
                        TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) {
                            Text(stringResource(R.string.common_ok))
                        }
                    }
                }
            }
        }
    }
}

/** Epoch-day field. Tapping it opens the calendar, so a date is chosen rather than spelled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochDay: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = epochDay?.let(::formatDay) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Outlined.DateRange, null, tint = Earnd.palette.muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val day = epochDay ?: ServiceLocator.clock.today().toEpochDay()
        val state = rememberDatePickerState(initialSelectedDateMillis = day * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker works in UTC midnights; read it back the same way or the day shifts.
                    state.selectedDateMillis?.let { ms ->
                        onChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    open = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
            shape = RectangleShape,
        ) { DatePicker(state) }
    }
}

private const val DEFAULT_TIME_MIN = 8 * 60
private const val MILLIS_PER_DAY = 86_400_000L
