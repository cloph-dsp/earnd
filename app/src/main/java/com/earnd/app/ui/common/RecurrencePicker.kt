package com.earnd.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earnd.app.domain.RRule
import com.earnd.app.domain.RRule.Freq
import com.earnd.app.domain.RRule.WeekdayNum
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class Preset { ONCE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** Recurrence presets plus a Custom dialog exposing every option, with a readable summary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrencePicker(rule: RRule?, fromCompletion: Boolean, start: LocalDate, onChange: (RRule?, Boolean) -> Unit) {
    val presets = linkedMapOf(
        Preset.ONCE to null,
        Preset.DAILY to RRule(Freq.DAILY),
        Preset.WEEKDAYS to RRule.weekdays(),
        Preset.WEEKLY to RRule.weekly(listOf(start.dayOfWeek)),
        Preset.MONTHLY to RRule(Freq.MONTHLY, byMonthDay = listOf(start.dayOfMonth)),
        Preset.YEARLY to RRule(Freq.YEARLY),
    )
    val selected = if (fromCompletion) Preset.CUSTOM else presets.entries.firstOrNull { it.value == rule }?.key ?: Preset.CUSTOM
    var custom by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Preset.values().forEach { p ->
            val label = when (p) {
                Preset.ONCE -> "Once"
                Preset.DAILY -> "Daily"
                Preset.WEEKDAYS -> "Weekdays"
                Preset.WEEKLY -> "Weekly on ${RRule.dayName(start.dayOfWeek)}"
                Preset.MONTHLY -> "Monthly on the ${RRule.ordinalName(start.dayOfMonth)}"
                Preset.YEARLY -> "Yearly on ${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${start.dayOfMonth}"
                Preset.CUSTOM -> "Custom…"
            }
            FilterChip(selected = p == selected, onClick = {
                if (p == Preset.CUSTOM) custom = true else onChange(presets[p], false)
            }, label = { Text(label) })
        }
    }
    if (rule != null) {
        Text(rule.summary(fromCompletion, start), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
    if (custom) {
        CustomRecurrenceDialog(rule ?: RRule.weekly(listOf(start.dayOfWeek)), fromCompletion, start,
            onDismiss = { custom = false }, onApply = { r, c -> custom = false; onChange(r, c) })
    }
}

private enum class MonthMode { DAY, LAST_DAY, NTH_WEEKDAY }
private enum class EndMode { NEVER, UNTIL, COUNT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRecurrenceDialog(
    initial: RRule,
    initialFromCompletion: Boolean,
    start: LocalDate,
    onDismiss: () -> Unit,
    onApply: (RRule, Boolean) -> Unit,
) {
    var freq by remember { mutableStateOf(initial.freq) }
    var interval by remember { mutableStateOf<Int?>(initial.interval) }
    var fromCompletion by remember { mutableStateOf(initialFromCompletion) }
    var mask by remember {
        mutableStateOf(RRule.daysToMask(initial.byDay.map { it.day }).takeIf { it != 0 } ?: RRule.daysToMask(listOf(start.dayOfWeek)))
    }
    var monthMode by remember {
        mutableStateOf(when {
            initial.byMonthDay == listOf(-1) -> MonthMode.LAST_DAY
            initial.byDay.any { it.ordinal != 0 } -> MonthMode.NTH_WEEKDAY
            else -> MonthMode.DAY
        })
    }
    var monthDay by remember { mutableStateOf<Int?>(initial.byMonthDay.firstOrNull()?.takeIf { it > 0 } ?: start.dayOfMonth) }
    val defaultOrd = ((start.dayOfMonth - 1) / 7 + 1).let { if (it > 4) -1 else it }
    var ordinal by remember { mutableStateOf(initial.byDay.firstOrNull { it.ordinal != 0 }?.ordinal ?: defaultOrd) }
    var nthDay by remember { mutableStateOf(initial.byDay.firstOrNull { it.ordinal != 0 }?.day ?: start.dayOfWeek) }
    var endMode by remember { mutableStateOf(when { initial.count != null -> EndMode.COUNT; initial.until != null -> EndMode.UNTIL; else -> EndMode.NEVER }) }
    var count by remember { mutableStateOf<Int?>(initial.count ?: 10) }
    var untilText by remember { mutableStateOf((initial.until ?: start.plusMonths(3)).toString()) }

    val draft: RRule? = runCatching {
        val n = (interval ?: 1).coerceIn(1, 999)
        val nth = listOf(WeekdayNum(ordinal, nthDay))
        val base = when {
            fromCompletion -> RRule(freq, n)
            freq == Freq.WEEKLY -> RRule.weekly(RRule.maskToDays(mask).ifEmpty { listOf(start.dayOfWeek) }, n)
            freq == Freq.MONTHLY -> when (monthMode) {
                MonthMode.DAY -> RRule(freq, n, byMonthDay = listOf((monthDay ?: start.dayOfMonth).coerceIn(1, 31)))
                MonthMode.LAST_DAY -> RRule(freq, n, byMonthDay = listOf(-1))
                MonthMode.NTH_WEEKDAY -> RRule(freq, n, byDay = nth)
            }
            freq == Freq.YEARLY && monthMode == MonthMode.NTH_WEEKDAY -> RRule(freq, n, byDay = nth, byMonth = listOf(start.monthValue))
            else -> RRule(freq, n)
        }
        when (endMode) {
            EndMode.NEVER -> base
            EndMode.COUNT -> base.copy(count = (count ?: 1).coerceAtLeast(1))
            EndMode.UNTIL -> base.copy(until = LocalDate.parse(untilText.trim()))
        }
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom repeat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle("Next date counts from")
                ChoiceChips(listOf(false, true), fromCompletion, { if (it) "Completion" else "Due date" }) { fromCompletion = it }
                SectionTitle("Every")
                NumberField("How many", interval, { interval = it }, Modifier.fillMaxWidth())
                ChoiceChips(Freq.values().toList(), freq, {
                    val plural = (interval ?: 1) != 1
                    when (it) {
                        Freq.DAILY -> if (plural) "Days" else "Day"; Freq.WEEKLY -> if (plural) "Weeks" else "Week"
                        Freq.MONTHLY -> if (plural) "Months" else "Month"; Freq.YEARLY -> if (plural) "Years" else "Year"
                    }
                }) { freq = it }
                if (!fromCompletion) when (freq) {
                    Freq.WEEKLY -> { SectionTitle("On"); WeekdayPicker(mask) { mask = it } }
                    Freq.MONTHLY -> {
                        SectionTitle("On")
                        ChoiceChips(MonthMode.values().toList(), monthMode, {
                            when (it) { MonthMode.DAY -> "Day of month"; MonthMode.LAST_DAY -> "Last day"; MonthMode.NTH_WEEKDAY -> "Nth weekday" }
                        }) { monthMode = it }
                        when (monthMode) {
                            MonthMode.DAY -> NumberField("Day (1-31, clamps to month end)", monthDay, { monthDay = it })
                            MonthMode.NTH_WEEKDAY -> NthWeekday(ordinal, nthDay, { ordinal = it }, { nthDay = it })
                            MonthMode.LAST_DAY -> Unit
                        }
                    }
                    Freq.YEARLY -> {
                        SectionTitle("On")
                        val month = start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        ChoiceChips(listOf(MonthMode.DAY, MonthMode.NTH_WEEKDAY), if (monthMode == MonthMode.NTH_WEEKDAY) monthMode else MonthMode.DAY, {
                            if (it == MonthMode.DAY) "$month ${start.dayOfMonth}" else "A weekday of $month"
                        }) { monthMode = it }
                        if (monthMode == MonthMode.NTH_WEEKDAY) NthWeekday(ordinal, nthDay, { ordinal = it }, { nthDay = it })
                    }
                    Freq.DAILY -> Unit
                }
                SectionTitle("Ends")
                ChoiceChips(EndMode.values().toList(), endMode, {
                    when (it) { EndMode.NEVER -> "Never"; EndMode.UNTIL -> "On a date"; EndMode.COUNT -> "After N times" }
                }) { endMode = it }
                when (endMode) {
                    EndMode.UNTIL -> OutlinedTextField(untilText, { untilText = it }, label = { Text("Last date (YYYY-MM-DD)") }, singleLine = true)
                    EndMode.COUNT -> NumberField("Times", count, { count = it })
                    EndMode.NEVER -> Unit
                }
                Text(draft?.summary(fromCompletion, start) ?: "Check the values above.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(enabled = draft != null, onClick = { draft?.let { onApply(it, fromCompletion) } }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NthWeekday(ordinal: Int, day: DayOfWeek, onOrdinal: (Int) -> Unit, onDay: (DayOfWeek) -> Unit) {
    ChoiceChips(listOf(1, 2, 3, 4, -1), ordinal, { RRule.ordinalName(it).replaceFirstChar(Char::uppercase) }, onOrdinal)
    ChoiceChips(DayOfWeek.values().toList(), day, { RRule.dayName(it) }, onDay)
}
