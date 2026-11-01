package com.earnd.app.ui.settings

import com.earnd.app.ui.theme.Earnd
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import com.earnd.app.alarms.AlarmsModule
import com.earnd.app.ui.common.EditScaffold
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

internal typealias Change = ((Settings) -> Settings) -> Unit

private enum class Page(val title: String) {
    BLOCKING("Blocking"), TIME_BANK("Time bank"), SCHEDULE("Schedule"), REMINDERS("Reminders & alarms"),
    APPEARANCE("Appearance"), REMOTE("Remote control"), BACKUP("Backup & data"), PERMISSIONS("Permissions"), ABOUT("About"),
}

@Composable
fun SettingsScreen(onOpenApps: () -> Unit, onOpenPermissions: () -> Unit) {
    val sl = ServiceLocator
    val s by remember { sl.settings.settings }.collectAsState(initial = null)
    var pendingAt by remember { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    LaunchedEffect(s, refresh) { pendingAt = sl.settings.pendingWeakeningAt()?.takeIf { it > 0 } }
    val context = LocalContext.current
    // Reminder times depend on these, so re-arm when any of them change.
    LaunchedEffect(
        s?.morningNudgeEnabled, s?.morningNudgeMin, s?.dailyReportEnabled, s?.dailyReportMin,
        s?.dayStartWeekdayMin, s?.dayStartWeekendMin, s?.laterWeekendStart, s?.quietStartMin, s?.quietEndMin,
    ) {
        if (s != null) runCatching {
            com.earnd.app.notify.Notify.reschedule(context)
            AlarmsModule.planner.rearmAll()
        }
    }

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
    val change: Change = { transform ->
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
    BackHandler(enabled = page != null) { page = null }
    val banner: @Composable () -> Unit = {
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
    }

    Column(Modifier.fillMaxSize()) {
        SnackbarHost(snackbar)
        val p = page
        if (p == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                banner()
                var remoteOn by remember { mutableStateOf(false) }
                LaunchedEffect(page) { remoteOn = RemoteSettings.isEnabled(sl.settings) }
                Page.entries.forEach { pg ->
                    CategoryRow(pg.title, summary(pg, cur, remoteOn)) { page = pg }
                    RowDivider()
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            EditScaffold(p.title, onBack = { page = null }) {
                banner()
                when (p) {
                    Page.BLOCKING -> BlockingPage(cur, change, onOpenApps)
                    Page.TIME_BANK -> TimeBankPage(cur, change)
                    Page.SCHEDULE -> SchedulePage(cur, change)
                    Page.REMINDERS -> RemindersPage(cur, change)
                    Page.APPEARANCE -> AppearancePage(cur, change)
                    Page.REMOTE -> RemoteSection()
                    Page.BACKUP -> BackupPage(snackbar)
                    Page.PERMISSIONS -> {
                        Text(
                            "Usage access, overlay, notifications and exact alarms.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.padding(top = 8.dp)) { Text("Review permissions") }
                    }
                    Page.ABOUT -> AboutPage()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Earnd.palette.muted)
    }
}

private fun summary(p: Page, s: Settings, remoteOn: Boolean): String = when (p) {
    Page.BLOCKING -> listOfNotNull(
        if (s.blockingEnabled) "Locked" else "Off",
        s.unlockCapMin?.let { "max $it min" } ?: "no unlock cap",
        if (s.hardMode) "hard mode" else null,
    ).joinToString(" · ")
    Page.TIME_BANK -> (if (s.resetPeriod == ResetPeriod.DAILY) "Resets daily" else "Resets weekly") +
        " · ${s.defaultHabitRewardMin} min per habit"
    Page.SCHEDULE -> "Day starts ${formatMinuteOfDay(s.dayStartWeekdayMin)}" +
        (if (s.laterWeekendStart) ", weekends ${formatMinuteOfDay(s.dayStartWeekendMin)}" else "") +
        " · Bedtime " + (if (s.bedtimeEnabled) "${formatMinuteOfDay(s.bedtimeStartMin)}-${formatMinuteOfDay(s.bedtimeEndMin)}" else "off")
    Page.REMINDERS -> "Quiet ${formatMinuteOfDay(s.quietStartMin)}-${formatMinuteOfDay(s.quietEndMin)}" +
        " · Morning nudge " + (if (s.morningNudgeEnabled) formatMinuteOfDay(s.morningNudgeMin) else "off") +
        " · Report " + (if (s.dailyReportEnabled) formatMinuteOfDay(s.dailyReportMin) else "off")
    Page.APPEARANCE -> listOf(
        when (s.themeMode) { ThemeMode.SYSTEM -> "System theme"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" },
        if (s.listDensity == ListDensity.COMPACT) "compact" else "comfortable",
    ).joinToString(" · ")
    Page.REMOTE -> if (remoteOn) "Local agent allowed" else "Off"
    Page.BACKUP -> "Export, import or clear your data"
    Page.PERMISSIONS -> "What Earnd is allowed to do"
    Page.ABOUT -> "Free software, GPL-3.0"
}

/** Shows whether exact alarms are allowed, re-checked whenever the screen resumes. */
@Composable
internal fun ExactAlarmRow() {
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
internal fun VacationEditor(cur: Settings, change: Change) {
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
internal fun DefaultAlarm(kind: OwnerKind, title: String) {
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
internal fun RemoteSection() {
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
