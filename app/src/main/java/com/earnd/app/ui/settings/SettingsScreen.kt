package com.earnd.app.ui.settings

import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.RowDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.earnd.app.ui.theme.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.domain.minuteOfDay
import com.earnd.app.remote.RemoteSettings
import com.earnd.app.ui.alarms.AlarmConfigEditor
import com.earnd.app.ui.alarms.AlarmConfigs
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.domain.DateFormatPref
import com.earnd.app.domain.TimeFormatPref
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField
import com.earnd.app.ui.common.formatDate
import com.earnd.app.ui.common.dateHint
import com.earnd.app.ui.common.formatMinuteOfDay
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.common.parseDate
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.earnd.app.alarms.AlarmPermissions
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.data.entity.ThemeMode
import com.earnd.app.data.entity.ListDensity
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.domain.UnlockCap
import com.earnd.app.ui.common.rememberPuzzleGate

@Composable
fun SettingsScreen(onOpenApps: () -> Unit, onOpenPermissions: () -> Unit) {
    val sl = ServiceLocator
    val s by remember { sl.settings.settings }.collectAsState(initial = null)
    var pendingAt by remember { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s, refresh) { pendingAt = sl.settings.pendingWeakeningAt()?.takeIf { it > 0 } }

    val gate = rememberPuzzleGate()
    val weaken: ((Settings) -> Settings) -> Unit = { transform ->
        launchUi {
            val at = sl.settings.requestWeakening(transform)
            refresh++
            if (at > sl.clock.now()) scope.launch {
                snackbar.showSnackbar("Hard mode: this change applies at ${formatMinuteOfDay(sl.clock.minuteOfDay(at))}.")
            }
        }
    }
    val change: ((Settings) -> Settings) -> Unit = { transform ->
        val shown = s
        // Loosening first passes the removal puzzle (if set), then the hard-mode cooldown.
        if (shown != null && SettingsChanges.isWeakening(shown, transform(shown))) {
            gate { weaken(transform) }
        } else {
            launchUi {
                val cur = sl.settings.current()
                if (SettingsChanges.isWeakening(cur, transform(cur))) weaken(transform) else sl.settings.update(transform)
            }
        }
    }

    val cur = s ?: return
    Column(Modifier.fillMaxSize()) {
        SnackbarHost(snackbar)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))

            pendingAt?.let { at ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Looser settings apply at ${formatMinuteOfDay(sl.clock.minuteOfDay(at))}.",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.red,
                    )
                    TextButton(onClick = { launchUi { sl.settings.cancelPendingWeakening(); refresh++ } }) { Text("Cancel") }
                }
                RowDivider()
            }

            SectionTitle("Blocking")
            SwitchRow("Lock leisure apps", cur.blockingEnabled, { v -> change { it.copy(blockingEnabled = v) } })
            OutlinedButton(onClick = onOpenApps) { Text("Choose apps, sites and always-open apps") }

            SectionTitle("Time bank")
            Text("Saved minutes reset", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(ResetPeriod.values().toList(), cur.resetPeriod, { if (it == ResetPeriod.DAILY) "Every day" else "Every week" }) { v ->
                change { it.copy(resetPeriod = v) }
            }
            if (cur.resetPeriod == ResetPeriod.WEEKLY) {
                Text("Week starts on", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), cur.weekStartsOn,
                    { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { v -> change { it.copy(weekStartsOn = v) } }
            }
            SwitchRow("Open everything when today's habits are done", cur.allDoneUnlock, { v -> change { it.copy(allDoneUnlock = v) } })
            Text("Longest single unlock", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(UnlockCap.CHOICES, UnlockCap.normalize(cur.unlockCapMin), { it?.let { m -> "$m min" } ?: "No cap" }) { v ->
                change { it.copy(unlockCapMin = v) }
            }
            Text(
                "Unlocks stop after this long. Minutes you don't use stay banked for the next one.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberField("Default task reward (minutes, blank = none)", cur.defaultTaskRewardMin, { v -> change { it.copy(defaultTaskRewardMin = v) } }, Modifier.fillMaxWidth())

            SectionTitle("Habits")
            Text("Habit minutes are paid", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(RewardMode.entries, cur.rewardMode, {
                if (it == RewardMode.PER_CHECKIN) "With each check-in" else "Once everything is done"
            }) { v -> change { it.copy(rewardMode = v) } }
            NumberField("Default minutes per check-in", cur.defaultHabitRewardMin,
                { v -> change { it.copy(defaultHabitRewardMin = (v ?: 0).coerceIn(0, 600)) } }, Modifier.fillMaxWidth())
            SwitchRow(
                "I promise to prove it", cur.photoRequiredDefault, { v -> change { it.copy(photoRequiredDefault = v) } },
                hint = "Habits check in with a photo unless you set otherwise on the habit.",
            )
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

            SectionTitle("Hard mode")
            SwitchRow(
                "Hard mode", cur.hardMode, { v -> change { it.copy(hardMode = v) } },
                hint = "Minutes can't be spent by hand; leisure opens only when everything is done. Loosening any setting waits for the cooldown.",
            )
            NumberField("Cooldown before loosening (minutes)", cur.weakenCooldownMin, { v -> change { it.copy(weakenCooldownMin = (v ?: 0).coerceIn(1, 24 * 60)) } }, Modifier.fillMaxWidth())
            Text("Puzzle before removing a block or loosening a rule", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(PuzzleKind.entries, cur.removalPuzzle, {
                when (it) { PuzzleKind.NONE -> "None"; PuzzleKind.ARITHMETIC -> "Arithmetic"; PuzzleKind.SENTENCE -> "Type a sentence" }
            }) { v -> change { it.copy(removalPuzzle = v) } }

            SectionTitle("Bedtime")
            SwitchRow("Lock leisure apps at night", cur.bedtimeEnabled, { v -> change { it.copy(bedtimeEnabled = v) } })
            if (cur.bedtimeEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("From", cur.bedtimeStartMin, { v -> v?.let { m -> change { it.copy(bedtimeStartMin = m) } } }, Modifier.weight(1f))
                    TimeField("Until", cur.bedtimeEndMin, { v -> v?.let { m -> change { it.copy(bedtimeEndMin = m) } } }, Modifier.weight(1f))
                }
                NumberField("One-off grace (minutes)", cur.bedtimeGraceMin, { v -> change { it.copy(bedtimeGraceMin = (v ?: 0).coerceIn(0, 15)) } })
            }

            SectionTitle("Vacation")
            VacationEditor(cur, change)

            SectionTitle("Reminders")
            Text("Quiet hours (no nagging)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("From", cur.quietStartMin, { v -> v?.let { m -> change { it.copy(quietStartMin = m) } } }, Modifier.weight(1f))
                TimeField("Until", cur.quietEndMin, { v -> v?.let { m -> change { it.copy(quietEndMin = m) } } }, Modifier.weight(1f))
            }
            SwitchRow("Morning nudge", cur.morningNudgeEnabled, { v -> change { it.copy(morningNudgeEnabled = v) } },
                hint = "A short summary of today's habits.")
            if (cur.morningNudgeEnabled) {
                TimeField("At", cur.morningNudgeMin, { v -> v?.let { m -> change { it.copy(morningNudgeMin = m) } } })
            }
            SwitchRow("Daily report", cur.dailyReportEnabled, { v -> change { it.copy(dailyReportEnabled = v) } },
                hint = "In the evening: what got done, what didn't, and leisure minutes used.")
            if (cur.dailyReportEnabled) {
                TimeField("At", cur.dailyReportMin, { v -> v?.let { m -> change { it.copy(dailyReportMin = m) } } })
            }
            ExactAlarmRow()
            DefaultAlarm(OwnerKind.TASK, "Default for tasks")
            DefaultAlarm(OwnerKind.HABIT, "Default for habit reminders")

            SectionTitle("Appearance")
            ChoiceChips(ThemeMode.entries, cur.themeMode, {
                when (it) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
            }) { v -> change { it.copy(themeMode = v) } }
            Text("Habit list", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(ListDensity.entries, cur.listDensity, {
                if (it == ListDensity.COMPACT) "Compact" else "Comfortable"
            }) { v -> change { it.copy(listDensity = v) } }
            Text("Date format", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(DateFormatPref.entries, cur.dateFormat, {
                when (it) {
                    DateFormatPref.DMY -> "DD/MM/YYYY"; DateFormatPref.YMD -> "YYYY-MM-DD"
                    DateFormatPref.MDY -> "MM/DD/YYYY"; DateFormatPref.SYSTEM -> "Follow system"
                }
            }) { v -> change { it.copy(dateFormat = v) } }
            Text("Time format", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(TimeFormatPref.entries, cur.timeFormat, {
                when (it) { TimeFormatPref.H24 -> "24-hour"; TimeFormatPref.H12 -> "12-hour"; TimeFormatPref.SYSTEM -> "Follow system" }
            }) { v -> change { it.copy(timeFormat = v) } }

            SectionTitle("Remote control")
            RemoteSection()

            SectionTitle("System")
            OutlinedButton(onClick = onOpenPermissions) { Text("Permissions") }

            SectionTitle("Backup")
            BackupSection(snackbar)
            Text(
                "Earnd is free software (GPL-3.0). Everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

/** Shows whether exact alarms are allowed, re-checked whenever the screen resumes. */
@Composable
private fun ExactAlarmRow() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var ok by remember { mutableStateOf(AlarmPermissions.canScheduleExact(context)) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { ok = AlarmPermissions.canScheduleExact(context) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Exact alarms", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ok) "Allowed. Reminders ring on time." else "Not allowed. Reminders may arrive late.",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        val intent = AlarmPermissions.exactAlarmSettingsIntent(context)
        if (!ok && intent != null) TextButton(onClick = { runCatching { context.startActivity(intent) } }) { Text("Allow") }
    }
}

@Composable
private fun VacationEditor(cur: Settings, change: ((Settings) -> Settings) -> Unit) {
    var from by remember(cur.vacationFromDay) { mutableStateOf(cur.vacationFromDay?.let(::formatDate) ?: "") }
    var to by remember(cur.vacationToDay) { mutableStateOf(cur.vacationToDay?.let(::formatDate) ?: "") }
    Text("Pauses blocking and streaks between two dates.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(from, { from = it }, label = { Text("From (${dateHint()})") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(to, { to = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row {
        val f = parseDate(from)
        val t = parseDate(to)
        TextButton(enabled = f != null && t != null && t >= f, onClick = {
            change { it.copy(vacationFromDay = f, vacationToDay = t) }
        }) { Text("Set vacation") }
        if (cur.vacationFromDay != null) {
            TextButton(onClick = { change { it.copy(vacationFromDay = null, vacationToDay = null) } }) { Text("Clear") }
        }
    }
}

@Composable
private fun DefaultAlarm(kind: OwnerKind, title: String) {
    var cfg by remember { mutableStateOf<AlarmConfigEntity?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(kind) { cfg = AlarmConfigs.defaults(kind) }
    TextButton(onClick = { open = !open }) { Text((if (open) "Hide: " else "Edit: ") + title) }
    val c = cfg
    if (open && c != null) {
        AlarmConfigEditor(c) { updated -> cfg = updated; launchUi { AlarmConfigs.save(updated) } }
    }
}

@Composable
private fun RemoteSection() {
    val sl = ServiceLocator
    var enabled by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        enabled = RemoteSettings.isEnabled(sl.settings)
        last = RemoteSettings.auditLog(sl.settings).lastOrNull()
    }
    SwitchRow(
        "Allow a local agent over adb", enabled,
        { v -> enabled = v; launchUi { RemoteSettings.setEnabled(sl.settings, v) } },
        hint = "It can read and manage habits and tasks. It can never unlock apps or change blocking.",
    )
    Text("Last remote action: " + (last?.let(::describeAudit) ?: "none yet"), style = MaterialTheme.typography.bodySmall)
}

/** Turns one audit JSON line into a short readable summary; falls back to the raw line. */
private fun describeAudit(json: String): String = runCatching {
    val o = org.json.JSONObject(json)
    val cmd = o.optString("cmd", "?")
    val ok = if (o.optBoolean("ok", true)) "" else " (refused)"
    val at = o.optLong("at", 0L)
    val time = if (at > 0) com.earnd.app.ui.common.formatDateTime(at) + ": " else ""
    time + cmd + ok
}.getOrDefault(json)
