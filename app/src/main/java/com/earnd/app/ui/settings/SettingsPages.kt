package com.earnd.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.ListDensity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.data.entity.ThemeMode
import com.earnd.app.domain.DateFormatPref
import com.earnd.app.domain.TimeFormatPref
import com.earnd.app.domain.UnlockCap
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField
import com.earnd.app.ui.theme.OutlinedButton
import java.time.DayOfWeek

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

@Composable
private fun Helper(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun BlockingPage(cur: Settings, change: Change, onOpenApps: () -> Unit) {
    SectionTitle("Apps and sites")
    SwitchRow("Lock leisure apps", cur.blockingEnabled, { v -> change { it.copy(blockingEnabled = v) } })
    OutlinedButton(onClick = onOpenApps) { Text("Choose apps, sites and always-open apps") }

    SectionTitle("Unlock window")
    Label("Longest single unlock")
    ChoiceChips(UnlockCap.CHOICES, UnlockCap.normalize(cur.unlockCapMin), { it?.let { m -> "$m min" } ?: "No cap" }) { v ->
        change { it.copy(unlockCapMin = v) }
    }
    Helper("Unlocks stop after this long. Minutes you don't use stay banked for the next one.")

    SectionTitle("Puzzle")
    Label("Puzzle before removing a block or loosening a rule")
    ChoiceChips(PuzzleKind.entries, cur.removalPuzzle, {
        when (it) { PuzzleKind.NONE -> "None"; PuzzleKind.ARITHMETIC -> "Arithmetic"; PuzzleKind.SENTENCE -> "Type a sentence" }
    }) { v -> change { it.copy(removalPuzzle = v) } }

    SectionTitle("Hard mode")
    SwitchRow(
        "Hard mode", cur.hardMode, { v -> change { it.copy(hardMode = v) } },
        hint = "Minutes can't be spent by hand; leisure opens only when everything is done. Loosening any setting waits for the cooldown.",
    )
    NumberField("Cooldown before loosening (minutes)", cur.weakenCooldownMin,
        { v -> change { it.copy(weakenCooldownMin = (v ?: 0).coerceIn(1, 24 * 60)) } }, Modifier.fillMaxWidth())
}

@Composable
internal fun TimeBankPage(cur: Settings, change: Change) {
    SectionTitle("Reset")
    Label("Saved minutes reset")
    ChoiceChips(ResetPeriod.values().toList(), cur.resetPeriod, { if (it == ResetPeriod.DAILY) "Every day" else "Every week" }) { v ->
        change { it.copy(resetPeriod = v) }
    }
    if (cur.resetPeriod == ResetPeriod.WEEKLY) {
        Label("Week starts on")
        ChoiceChips(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), cur.weekStartsOn,
            { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { v -> change { it.copy(weekStartsOn = v) } }
    }

    SectionTitle("Unlocking")
    SwitchRow("Open everything when today's habits are done", cur.allDoneUnlock, { v -> change { it.copy(allDoneUnlock = v) } })

    SectionTitle("Reward defaults")
    Label("Habit minutes are paid")
    ChoiceChips(RewardMode.entries, cur.rewardMode, {
        if (it == RewardMode.PER_CHECKIN) "With each check-in" else "Once everything is done"
    }) { v -> change { it.copy(rewardMode = v) } }
    NumberField("Default minutes per check-in", cur.defaultHabitRewardMin,
        { v -> change { it.copy(defaultHabitRewardMin = (v ?: 0).coerceIn(0, 600)) } }, Modifier.fillMaxWidth())
    NumberField("Default task reward (minutes, blank = none)", cur.defaultTaskRewardMin,
        { v -> change { it.copy(defaultTaskRewardMin = v) } }, Modifier.fillMaxWidth())
    SwitchRow(
        "I promise to prove it", cur.photoRequiredDefault, { v -> change { it.copy(photoRequiredDefault = v) } },
        hint = "Habits check in with a photo unless you set otherwise on the habit.",
    )

    SectionTitle("Comeback")
    SwitchRow(
        "Comeback bonus", cur.comebackBonus, { v -> change { it.copy(comebackBonus = v) } },
        hint = "After missed days, the first check-in earns up to double. Skipping never pays more than doing.",
    )
}

@Composable
internal fun SchedulePage(cur: Settings, change: Change) {
    SectionTitle("Day start")
    TimeField("Day starts (weekdays)", cur.dayStartWeekdayMin,
        { v -> v?.let { m -> change { it.copy(dayStartWeekdayMin = m) } } }, Modifier.fillMaxWidth())
    SwitchRow(
        "Later start on weekends", cur.laterWeekendStart, { v -> change { it.copy(laterWeekendStart = v) } },
        hint = "On Saturday and Sunday, habit reminders and nags move later by the difference. Tasks with a set time don't move.",
    )
    if (cur.laterWeekendStart) {
        TimeField("Day starts (weekends)", cur.dayStartWeekendMin,
            { v -> v?.let { m -> change { it.copy(dayStartWeekendMin = m) } } }, Modifier.fillMaxWidth())
    }

    SectionTitle("Quiet hours")
    Helper("No nagging in this window. On weekends it ends later by the same shift.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField("From", cur.quietStartMin, { v -> v?.let { m -> change { it.copy(quietStartMin = m) } } }, Modifier.weight(1f))
        TimeField("Until", cur.quietEndMin, { v -> v?.let { m -> change { it.copy(quietEndMin = m) } } }, Modifier.weight(1f))
    }

    SectionTitle("Bedtime")
    SwitchRow("Lock leisure apps at night", cur.bedtimeEnabled, { v -> change { it.copy(bedtimeEnabled = v) } })
    if (cur.bedtimeEnabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("From", cur.bedtimeStartMin, { v -> v?.let { m -> change { it.copy(bedtimeStartMin = m) } } }, Modifier.weight(1f))
            TimeField("Until", cur.bedtimeEndMin, { v -> v?.let { m -> change { it.copy(bedtimeEndMin = m) } } }, Modifier.weight(1f))
        }
        NumberField("One-off grace (minutes)", cur.bedtimeGraceMin, { v -> change { it.copy(bedtimeGraceMin = (v ?: 0).coerceIn(0, 15)) } })
    }

    SectionTitle("Parts of the day")
    SwitchRow(
        "Unlock by part of the day", cur.segmentMode, { v -> change { it.copy(segmentMode = v) } },
        hint = "Finish the morning's habits and leisure apps open until the afternoon starts, and so on.",
    )
    if (cur.segmentMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("Morning", cur.morningStartMin, { v -> v?.let { m -> change { it.copy(morningStartMin = m) } } }, Modifier.weight(1f))
            TimeField("Afternoon", cur.afternoonStartMin, { v -> v?.let { m -> change { it.copy(afternoonStartMin = m) } } }, Modifier.weight(1f))
            TimeField("Evening", cur.eveningStartMin, { v -> v?.let { m -> change { it.copy(eveningStartMin = m) } } }, Modifier.weight(1f))
        }
    }

    SectionTitle("Vacation")
    VacationEditor(cur, change)
}

@Composable
internal fun RemindersPage(cur: Settings, change: Change) {
    SectionTitle("Nudges")
    SwitchRow("Morning nudge", cur.morningNudgeEnabled, { v -> change { it.copy(morningNudgeEnabled = v) } },
        hint = "A short summary of today's habits. Later on weekends when the weekend start is later.")
    if (cur.morningNudgeEnabled) {
        TimeField("At", cur.morningNudgeMin, { v -> v?.let { m -> change { it.copy(morningNudgeMin = m) } } })
    }
    SwitchRow("Daily report", cur.dailyReportEnabled, { v -> change { it.copy(dailyReportEnabled = v) } },
        hint = "In the evening: what got done, what didn't, and leisure minutes used.")
    if (cur.dailyReportEnabled) {
        TimeField("At", cur.dailyReportMin, { v -> v?.let { m -> change { it.copy(dailyReportMin = m) } } })
    }

    SectionTitle("Defaults for new items")
    DefaultAlarm(OwnerKind.TASK, "Default for tasks")
    DefaultAlarm(OwnerKind.HABIT, "Default for habit reminders")

    SectionTitle("Status")
    ExactAlarmRow()
}

@Composable
internal fun AppearancePage(cur: Settings, change: Change) {
    Label("Theme")
    ChoiceChips(ThemeMode.entries, cur.themeMode, {
        when (it) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
    }) { v -> change { it.copy(themeMode = v) } }
    Label("Habit list")
    ChoiceChips(ListDensity.entries, cur.listDensity, {
        if (it == ListDensity.COMPACT) "Compact" else "Comfortable"
    }) { v -> change { it.copy(listDensity = v) } }
    Label("Date format")
    ChoiceChips(DateFormatPref.entries, cur.dateFormat, {
        when (it) {
            DateFormatPref.DMY -> "DD/MM/YYYY"; DateFormatPref.YMD -> "YYYY-MM-DD"
            DateFormatPref.MDY -> "MM/DD/YYYY"; DateFormatPref.SYSTEM -> "Follow system"
        }
    }) { v -> change { it.copy(dateFormat = v) } }
    Label("Time format")
    ChoiceChips(TimeFormatPref.entries, cur.timeFormat, {
        when (it) { TimeFormatPref.H24 -> "24-hour"; TimeFormatPref.H12 -> "12-hour"; TimeFormatPref.SYSTEM -> "Follow system" }
    }) { v -> change { it.copy(timeFormat = v) } }
}

@Composable
internal fun BackupPage(snackbar: SnackbarHostState) {
    SectionTitle("Backup")
    BackupSection(snackbar)
    SectionTitle("Danger zone")
    ClearDataSection(snackbar)
}

@Composable
internal fun AboutPage() {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    Text("Earnd" + (version?.let { " $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
    Helper("Earnd is free software (GPL-3.0). Everything stays on this phone.")
}
