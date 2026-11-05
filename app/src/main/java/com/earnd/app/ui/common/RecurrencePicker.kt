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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.domain.RRule
import com.earnd.app.domain.RRule.Freq
import com.earnd.app.domain.RRule.WeekdayNum
import com.earnd.app.i18n.Str
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
                Preset.ONCE -> stringResource(R.string.rrule_once)
                Preset.DAILY -> stringResource(R.string.rrule_daily)
                Preset.WEEKDAYS -> stringResource(R.string.rrule_weekdays)
                Preset.WEEKLY -> stringResource(R.string.rrule_weekly_on, RRule.dayName(start.dayOfWeek))
                Preset.MONTHLY -> stringResource(R.string.rrule_monthly_on, stringResource(R.string.rrule_the, RRule.dayOfMonthName(start.dayOfMonth)))
                Preset.YEARLY -> stringResource(
                    R.string.rrule_yearly_on,
                    stringResource(R.string.rrule_month_days, RRule.monthName(start.monthValue), start.dayOfMonth.toString()),
                )
                Preset.CUSTOM -> stringResource(R.string.rrule_custom_ellipsis)
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
        title = { Text(stringResource(R.string.rrule_custom_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.rrule_counts_from))
                ChoiceChips(listOf(false, true), fromCompletion, { Str.get(if (it) R.string.rrule_completion else R.string.rrule_due_date) }) { fromCompletion = it }
                SectionTitle(stringResource(R.string.rrule_every))
                NumberField(stringResource(R.string.rrule_how_many), interval, { interval = it }, Modifier.fillMaxWidth())
                ChoiceChips(Freq.values().toList(), freq, {
                    val plural = (interval ?: 1) != 1
                    Str.get(
                        when (it) {
                            Freq.DAILY -> if (plural) R.string.rrule_unit_days else R.string.rrule_unit_day
                            Freq.WEEKLY -> if (plural) R.string.rrule_unit_weeks else R.string.rrule_unit_week
                            Freq.MONTHLY -> if (plural) R.string.rrule_unit_months else R.string.rrule_unit_month
                            Freq.YEARLY -> if (plural) R.string.rrule_unit_years else R.string.rrule_unit_year
                        },
                    )
                }) { freq = it }
                if (!fromCompletion) when (freq) {
                    Freq.WEEKLY -> { SectionTitle(stringResource(R.string.rrule_on)); WeekdayPicker(mask) { mask = it } }
                    Freq.MONTHLY -> {
                        SectionTitle(stringResource(R.string.rrule_on))
                        ChoiceChips(MonthMode.values().toList(), monthMode, {
                            Str.get(
                                when (it) {
                                    MonthMode.DAY -> R.string.rrule_mode_day
                                    MonthMode.LAST_DAY -> R.string.rrule_mode_last_day
                                    MonthMode.NTH_WEEKDAY -> R.string.rrule_mode_nth_weekday
                                },
                            )
                        }) { monthMode = it }
                        when (monthMode) {
                            MonthMode.DAY -> NumberField(stringResource(R.string.rrule_day_clamps), monthDay, { monthDay = it })
                            MonthMode.NTH_WEEKDAY -> NthWeekday(ordinal, nthDay, { ordinal = it }, { nthDay = it })
                            MonthMode.LAST_DAY -> Unit
                        }
                    }
                    Freq.YEARLY -> {
                        SectionTitle(stringResource(R.string.rrule_on))
                        val month = start.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        ChoiceChips(listOf(MonthMode.DAY, MonthMode.NTH_WEEKDAY), if (monthMode == MonthMode.NTH_WEEKDAY) monthMode else MonthMode.DAY, {
                            if (it == MonthMode.DAY) Str.get(R.string.rrule_month_days, month, start.dayOfMonth.toString()) else Str.get(R.string.rrule_a_weekday_of, month)
                        }) { monthMode = it }
                        if (monthMode == MonthMode.NTH_WEEKDAY) NthWeekday(ordinal, nthDay, { ordinal = it }, { nthDay = it })
                    }
                    Freq.DAILY -> Unit
                }
                SectionTitle(stringResource(R.string.rrule_ends))
                ChoiceChips(EndMode.values().toList(), endMode, {
                    Str.get(
                        when (it) {
                            EndMode.NEVER -> R.string.rrule_end_never
                            EndMode.UNTIL -> R.string.rrule_end_on_date
                            EndMode.COUNT -> R.string.rrule_end_after_n
                        },
                    )
                }) { endMode = it }
                when (endMode) {
                    EndMode.UNTIL -> OutlinedTextField(untilText, { untilText = it }, label = { Text(stringResource(R.string.rrule_last_date)) }, singleLine = true)
                    EndMode.COUNT -> NumberField(stringResource(R.string.rrule_times), count, { count = it })
                    EndMode.NEVER -> Unit
                }
                Text(draft?.summary(fromCompletion, start) ?: stringResource(R.string.rrule_check_values),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(enabled = draft != null, onClick = { draft?.let { onApply(it, fromCompletion) } }) { Text(stringResource(R.string.habits_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) } },
    )
}

@Composable
private fun NthWeekday(ordinal: Int, day: DayOfWeek, onOrdinal: (Int) -> Unit, onDay: (DayOfWeek) -> Unit) {
    ChoiceChips(listOf(1, 2, 3, 4, -1), ordinal, { RRule.ordinalName(it, day).replaceFirstChar(Char::uppercase) }, onOrdinal)
    ChoiceChips(DayOfWeek.values().toList(), day, { RRule.dayName(it).replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }, onDay)
}
