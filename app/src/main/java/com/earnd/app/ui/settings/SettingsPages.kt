package com.earnd.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.data.Settings
import com.earnd.app.i18n.Str
import com.earnd.app.data.entity.ListDensity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.data.entity.ThemeMode
import com.earnd.app.domain.DateFormatPref
import com.earnd.app.domain.TimeFormatPref
import com.earnd.app.domain.UnlockCap
import com.earnd.app.domain.today
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField
import com.earnd.app.ui.theme.OutlinedButton
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

@Composable
private fun Helper(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun BlockingPage(cur: Settings, change: Change, onOpenApps: () -> Unit) {
    SectionTitle(stringResource(R.string.settings_blocking_apps_section))
    SwitchRow(stringResource(R.string.settings_blocking_lock_leisure), cur.blockingEnabled, { v -> change { it.copy(blockingEnabled = v) } })
    OutlinedButton(onClick = onOpenApps) { Text(stringResource(R.string.settings_blocking_choose_apps)) }

    SectionTitle(stringResource(R.string.settings_blocking_unlock_section))
    Label(stringResource(R.string.settings_blocking_longest))
    ChoiceChips(UnlockCap.CHOICES, UnlockCap.normalize(cur.unlockCapMin), { it?.let { m -> Str.get(R.string.settings_minutes_short, m) } ?: Str.get(R.string.settings_blocking_no_cap) }) { v ->
        change { it.copy(unlockCapMin = v) }
    }
    Helper(stringResource(R.string.settings_blocking_cap_helper))

    SectionTitle(stringResource(R.string.settings_blocking_puzzle_section))
    Label(stringResource(R.string.settings_blocking_puzzle_label))
    ChoiceChips(PuzzleKind.entries, cur.removalPuzzle, {
        Str.get(when (it) {
            PuzzleKind.NONE -> R.string.settings_puzzle_none
            PuzzleKind.ARITHMETIC -> R.string.settings_puzzle_arithmetic
            PuzzleKind.SENTENCE -> R.string.settings_puzzle_sentence
        })
    }) { v -> change { it.copy(removalPuzzle = v) } }

    SectionTitle(stringResource(R.string.settings_blocking_hard_mode))
    SwitchRow(
        stringResource(R.string.settings_blocking_hard_mode), cur.hardMode, { v -> change { it.copy(hardMode = v) } },
        hint = stringResource(R.string.settings_blocking_hard_mode_hint),
    )
    NumberField(stringResource(R.string.settings_blocking_cooldown), cur.weakenCooldownMin,
        { v -> change { it.copy(weakenCooldownMin = (v ?: 0).coerceIn(1, 24 * 60)) } }, Modifier.fillMaxWidth())
}

@Composable
internal fun TimeBankPage(cur: Settings, change: Change) {
    SectionTitle(stringResource(R.string.settings_bank_reset_section))
    Label(stringResource(R.string.settings_bank_reset_label))
    ChoiceChips(ResetPeriod.entries, cur.resetPeriod, {
        Str.get(when (it) {
            ResetPeriod.DAILY -> R.string.settings_bank_reset_daily
            ResetPeriod.WEEKLY -> R.string.settings_bank_reset_weekly
            ResetPeriod.ROLLING -> R.string.settings_bank_reset_rolling
        })
    }) { v -> change { it.copy(resetPeriod = v) } }
    if (cur.resetPeriod == ResetPeriod.ROLLING) {
        ExpiryHoursSlider(cur.rollingExpiryHours) { v -> change { it.copy(rollingExpiryHours = v) } }
        Helper(stringResource(R.string.settings_bank_rolling_helper))
    }
    if (cur.resetPeriod == ResetPeriod.WEEKLY) {
        Label(stringResource(R.string.settings_bank_week_starts))
        ChoiceChips(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), cur.weekStartsOn,
            { it.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }) { v -> change { it.copy(weekStartsOn = v) } }
    }

    SectionTitle(stringResource(R.string.settings_bank_unlocking_section))
    SwitchRow(stringResource(R.string.settings_bank_all_done_unlock), cur.allDoneUnlock, { v -> change { it.copy(allDoneUnlock = v) } })

    SectionTitle(stringResource(R.string.settings_bank_rewards_section))
    Label(stringResource(R.string.settings_bank_habit_paid))
    ChoiceChips(RewardMode.entries, cur.rewardMode, {
        Str.get(if (it == RewardMode.PER_CHECKIN) R.string.settings_bank_paid_each else R.string.settings_bank_paid_once)
    }) { v -> change { it.copy(rewardMode = v) } }
    NumberField(stringResource(R.string.settings_bank_default_habit_min), cur.defaultHabitRewardMin,
        { v -> change { it.copy(defaultHabitRewardMin = (v ?: 0).coerceIn(0, 600)) } }, Modifier.fillMaxWidth())
    NumberField(stringResource(R.string.settings_bank_default_task_reward), cur.defaultTaskRewardMin,
        { v -> change { it.copy(defaultTaskRewardMin = v) } }, Modifier.fillMaxWidth())
    SwitchRow(
        stringResource(R.string.settings_bank_promise), cur.photoRequiredDefault, { v -> change { it.copy(photoRequiredDefault = v) } },
        hint = stringResource(R.string.settings_bank_promise_hint),
    )

    SectionTitle(stringResource(R.string.settings_bank_comeback_section))
    SwitchRow(
        stringResource(R.string.settings_bank_comeback_bonus), cur.comebackBonus, { v -> change { it.copy(comebackBonus = v) } },
        hint = stringResource(R.string.settings_bank_comeback_hint),
    )
}

@Composable
internal fun SchedulePage(cur: Settings, change: Change) {
    SectionTitle(stringResource(R.string.settings_sched_day_start_section))
    TimeField(stringResource(R.string.settings_sched_day_starts_weekdays), cur.dayStartWeekdayMin,
        { v -> v?.let { m -> change { it.copy(dayStartWeekdayMin = m) } } }, Modifier.fillMaxWidth())
    SwitchRow(
        stringResource(R.string.settings_sched_later_weekend), cur.laterWeekendStart, { v -> change { it.copy(laterWeekendStart = v) } },
        hint = stringResource(R.string.settings_sched_later_weekend_hint),
    )
    if (cur.laterWeekendStart) {
        TimeField(stringResource(R.string.settings_sched_day_starts_weekends), cur.dayStartWeekendMin,
            { v -> v?.let { m -> change { it.copy(dayStartWeekendMin = m) } } }, Modifier.fillMaxWidth())
    }

    SectionTitle(stringResource(R.string.settings_sched_quiet_section))
    Helper(stringResource(R.string.settings_sched_quiet_helper))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField(stringResource(R.string.settings_from), cur.quietStartMin, { v -> v?.let { m -> change { it.copy(quietStartMin = m) } } }, Modifier.weight(1f))
        TimeField(stringResource(R.string.settings_until), cur.quietEndMin, { v -> v?.let { m -> change { it.copy(quietEndMin = m) } } }, Modifier.weight(1f))
    }

    SectionTitle(stringResource(R.string.settings_sched_bedtime_section))
    SwitchRow(stringResource(R.string.settings_sched_bedtime_lock), cur.bedtimeEnabled, { v -> change { it.copy(bedtimeEnabled = v) } })
    if (cur.bedtimeEnabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(stringResource(R.string.settings_from), cur.bedtimeStartMin, { v -> v?.let { m -> change { it.copy(bedtimeStartMin = m) } } }, Modifier.weight(1f))
            TimeField(stringResource(R.string.settings_until), cur.bedtimeEndMin, { v -> v?.let { m -> change { it.copy(bedtimeEndMin = m) } } }, Modifier.weight(1f))
        }
        NumberField(stringResource(R.string.settings_sched_bedtime_grace), cur.bedtimeGraceMin, { v -> change { it.copy(bedtimeGraceMin = (v ?: 0).coerceIn(0, 15)) } })
    }

    SectionTitle(stringResource(R.string.settings_sched_parts_section))
    SwitchRow(
        stringResource(R.string.settings_sched_parts_switch), cur.segmentMode, { v -> change { it.copy(segmentMode = v) } },
        hint = stringResource(R.string.settings_sched_parts_hint),
    )
    if (cur.segmentMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(stringResource(R.string.settings_sched_morning), cur.morningStartMin, { v -> v?.let { m -> change { it.copy(morningStartMin = m) } } }, Modifier.weight(1f))
            TimeField(stringResource(R.string.settings_sched_afternoon), cur.afternoonStartMin, { v -> v?.let { m -> change { it.copy(afternoonStartMin = m) } } }, Modifier.weight(1f))
            TimeField(stringResource(R.string.settings_sched_evening), cur.eveningStartMin, { v -> v?.let { m -> change { it.copy(eveningStartMin = m) } } }, Modifier.weight(1f))
        }
    }

    SectionTitle(stringResource(R.string.settings_sched_vacation_section))
    VacationEditor(cur, change)
}

@Composable
internal fun RemindersPage(cur: Settings, change: Change) {
    SectionTitle(stringResource(R.string.settings_rem_nudges_section))
    SwitchRow(stringResource(R.string.settings_rem_morning_nudge), cur.morningNudgeEnabled, { v -> change { it.copy(morningNudgeEnabled = v) } },
        hint = stringResource(R.string.settings_rem_morning_nudge_hint))
    if (cur.morningNudgeEnabled) {
        TimeField(stringResource(R.string.settings_at), cur.morningNudgeMin, { v -> v?.let { m -> change { it.copy(morningNudgeMin = m) } } })
    }
    SwitchRow(stringResource(R.string.settings_rem_daily_report), cur.dailyReportEnabled, { v -> change { it.copy(dailyReportEnabled = v) } },
        hint = stringResource(R.string.settings_rem_daily_report_hint))
    if (cur.dailyReportEnabled) {
        TimeField(stringResource(R.string.settings_at), cur.dailyReportMin, { v -> v?.let { m -> change { it.copy(dailyReportMin = m) } } })
    }

    SectionTitle(stringResource(R.string.settings_rem_ringing_section))
    val today = com.earnd.app.ServiceLocator.clock.today().toEpochDay()
    SwitchRow(stringResource(R.string.settings_rem_off_day), cur.onOffDay(today),
        { on -> change { it.copy(offDayEpochDay = if (on) today else null) } },
        hint = stringResource(R.string.settings_rem_off_day_hint))
    Label(stringResource(R.string.settings_rem_ring_for))
    com.earnd.app.ui.alarms.RingForPicker(cur.ringSec, withDefault = false) { v -> v?.let { s -> change { it.copy(ringSec = s) } } }
    Label(stringResource(R.string.settings_rem_volume, cur.alarmVolumePct))
    var vol by remember(cur.alarmVolumePct) { androidx.compose.runtime.mutableFloatStateOf(cur.alarmVolumePct.toFloat()) }
    androidx.compose.material3.Slider(
        value = vol, onValueChange = { vol = it },
        onValueChangeFinished = { val p = vol.toInt(); change { it.copy(alarmVolumePct = p) } },
        valueRange = 10f..100f,
    )
    Helper(stringResource(R.string.settings_rem_volume_helper))
    SwitchRow(stringResource(R.string.settings_rem_gentle), cur.gentleStart, { v -> change { it.copy(gentleStart = v) } },
        hint = stringResource(R.string.settings_rem_gentle_hint))

    SectionTitle(stringResource(R.string.settings_rem_defaults_section))
    DefaultAlarm(OwnerKind.TASK, stringResource(R.string.settings_rem_default_tasks))
    DefaultAlarm(OwnerKind.HABIT, stringResource(R.string.settings_rem_default_habits))

    SectionTitle(stringResource(R.string.settings_rem_status_section))
    ExactAlarmRow()
}

@Composable
internal fun AppearancePage(cur: Settings, change: Change) {
    if (com.earnd.app.i18n.AppLanguage.supported) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var lang by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.earnd.app.i18n.AppLanguage.current(context)) }
        Label(stringResource(R.string.settings_app_language))
        ChoiceChips(com.earnd.app.i18n.AppLanguage.choices, lang, {
            when (it) {
                com.earnd.app.i18n.AppLanguage.SYSTEM -> Str.get(R.string.settings_follow_system)
                "pt" -> "Português"
                else -> "English"
            }
        }) { v -> lang = v; com.earnd.app.i18n.AppLanguage.set(context, v) }
    }
    Label(stringResource(R.string.settings_app_theme))
    ChoiceChips(ThemeMode.entries, cur.themeMode, {
        Str.get(when (it) {
            ThemeMode.SYSTEM -> R.string.settings_follow_system
            ThemeMode.LIGHT -> R.string.settings_app_theme_light
            ThemeMode.DARK -> R.string.settings_app_theme_dark
        })
    }) { v -> change { it.copy(themeMode = v) } }
    Label(stringResource(R.string.settings_app_density))
    ChoiceChips(ListDensity.entries, cur.listDensity, {
        Str.get(if (it == ListDensity.COMPACT) R.string.settings_app_density_compact else R.string.settings_app_density_comfortable)
    }) { v -> change { it.copy(listDensity = v) } }
    Label(stringResource(R.string.settings_app_date_format))
    ChoiceChips(DateFormatPref.entries, cur.dateFormat, {
        Str.get(when (it) {
            DateFormatPref.DMY -> R.string.settings_app_date_dmy
            DateFormatPref.YMD -> R.string.settings_app_date_ymd
            DateFormatPref.MDY -> R.string.settings_app_date_mdy
            DateFormatPref.SYSTEM -> R.string.settings_follow_system
        })
    }) { v -> change { it.copy(dateFormat = v) } }
    Label(stringResource(R.string.settings_app_time_format))
    ChoiceChips(TimeFormatPref.entries, cur.timeFormat, {
        Str.get(when (it) {
            TimeFormatPref.H24 -> R.string.settings_app_time_24
            TimeFormatPref.H12 -> R.string.settings_app_time_12
            TimeFormatPref.SYSTEM -> R.string.settings_follow_system
        })
    }) { v -> change { it.copy(timeFormat = v) } }
}

@Composable
internal fun BackupPage(snackbar: SnackbarHostState) {
    SectionTitle(stringResource(R.string.settings_backup_section))
    BackupSection(snackbar)
    SectionTitle(stringResource(R.string.settings_danger_section))
    ClearDataSection(snackbar)
}

@Composable
internal fun AboutPage() {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    Text("Earnd" + (version?.let { " $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
    Helper(stringResource(R.string.settings_about_blurb))
}


/** Hour slider; the value is committed on release so hard-mode/puzzle gates run once, not per step. */
@Composable
private fun ExpiryHoursSlider(current: Int, onCommit: (Int) -> Unit) {
    var v by androidx.compose.runtime.remember(current) {
        androidx.compose.runtime.mutableFloatStateOf(current.coerceIn(MIN_EXPIRY_H, MAX_EXPIRY_H).toFloat())
    }
    Label(stringResource(R.string.settings_bank_expire_after, v.toInt()))
    androidx.compose.material3.Slider(
        value = v,
        onValueChange = { v = it },
        valueRange = MIN_EXPIRY_H.toFloat()..MAX_EXPIRY_H.toFloat(),
        steps = MAX_EXPIRY_H - MIN_EXPIRY_H - 1,
        onValueChangeFinished = { if (v.toInt() != current) onCommit(v.toInt()) },
    )
}

private const val MIN_EXPIRY_H = 6
private const val MAX_EXPIRY_H = 72
