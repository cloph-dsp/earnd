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
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.i18n.Str
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
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

private enum class Page(@StringRes val titleRes: Int) {
    BLOCKING(R.string.settings_page_blocking), TIME_BANK(R.string.settings_page_time_bank),
    SCHEDULE(R.string.settings_page_schedule), REMINDERS(R.string.settings_page_reminders),
    APPEARANCE(R.string.settings_page_appearance), REMOTE(R.string.settings_page_remote),
    BACKUP(R.string.settings_page_backup), PERMISSIONS(R.string.settings_page_permissions), ABOUT(R.string.settings_page_about),
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
                snackbar.showSnackbar(context.getString(R.string.settings_hard_mode_applies_at, formatMinuteOfDay(sl.clock.minuteOfDay(at))))
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
                    stringResource(R.string.settings_looser_applies_at, formatMinuteOfDay(sl.clock.minuteOfDay(at))),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.red,
                )
                TextButton(onClick = { launchUi { sl.settings.cancelPendingWeakening(); refresh++ } }) { Text(stringResource(R.string.settings_cancel)) }
            }
            RowDivider()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SnackbarHost(snackbar)
        val p = page
        if (p == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                banner()
                var remoteOn by remember { mutableStateOf(false) }
                LaunchedEffect(page) { remoteOn = RemoteSettings.isEnabled(sl.settings) }
                Page.entries.forEachIndexed { i, pg ->
                    CategoryRow(stringResource(pg.titleRes), summary(pg, cur, remoteOn)) { page = pg }
                    if (i < Page.entries.lastIndex) RowDivider()
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            EditScaffold(stringResource(p.titleRes), onBack = { page = null }) {
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
                            stringResource(R.string.settings_permissions_blurb),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.settings_permissions_review)) }
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
    com.earnd.app.ui.theme.ListRow(onClick, Modifier.padding(vertical = 8.dp), minHeight = 56.dp) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Earnd.palette.muted)
    }
}

private fun summary(p: Page, s: Settings, remoteOn: Boolean): String = when (p) {
    Page.BLOCKING -> listOfNotNull(
        if (s.blockingEnabled) Str.get(R.string.settings_sum_locked) else Str.get(R.string.settings_off),
        s.unlockCapMin?.let { Str.get(R.string.settings_sum_max_min, it) } ?: Str.get(R.string.settings_sum_no_cap),
        if (s.hardMode) Str.get(R.string.settings_sum_hard_mode) else null,
    ).joinToString(" · ")
    Page.TIME_BANK -> when (s.resetPeriod) {
        ResetPeriod.DAILY -> Str.get(R.string.settings_sum_resets_daily)
        ResetPeriod.WEEKLY -> Str.get(R.string.settings_sum_resets_weekly)
        ResetPeriod.ROLLING -> Str.get(R.string.settings_sum_expires_after, s.rollingExpiryHours)
    } +
        " · " + Str.get(R.string.settings_sum_min_per_habit, s.defaultHabitRewardMin)
    Page.SCHEDULE -> (
        if (s.laterWeekendStart) {
            Str.get(R.string.settings_sum_day_starts_weekend, formatMinuteOfDay(s.dayStartWeekdayMin), formatMinuteOfDay(s.dayStartWeekendMin))
        } else {
            Str.get(R.string.settings_sum_day_starts, formatMinuteOfDay(s.dayStartWeekdayMin))
        }
        ) + " · " + (
        if (s.bedtimeEnabled) {
            Str.get(R.string.settings_sum_bedtime, formatMinuteOfDay(s.bedtimeStartMin), formatMinuteOfDay(s.bedtimeEndMin))
        } else {
            Str.get(R.string.settings_sum_bedtime_off)
        }
        )
    Page.REMINDERS -> Str.get(R.string.settings_sum_quiet, formatMinuteOfDay(s.quietStartMin), formatMinuteOfDay(s.quietEndMin)) +
        " · " + (
        if (s.morningNudgeEnabled) Str.get(R.string.settings_sum_nudge, formatMinuteOfDay(s.morningNudgeMin))
        else Str.get(R.string.settings_sum_nudge_off)
        ) +
        " · " + (
        if (s.dailyReportEnabled) Str.get(R.string.settings_sum_report, formatMinuteOfDay(s.dailyReportMin))
        else Str.get(R.string.settings_sum_report_off)
        )
    Page.APPEARANCE -> listOf(
        Str.get(when (s.themeMode) {
            ThemeMode.SYSTEM -> R.string.settings_sum_theme_system
            ThemeMode.LIGHT -> R.string.settings_app_theme_light
            ThemeMode.DARK -> R.string.settings_app_theme_dark
        }),
        Str.get(if (s.listDensity == ListDensity.COMPACT) R.string.settings_sum_compact else R.string.settings_sum_comfortable),
    ).joinToString(" · ")
    Page.REMOTE -> if (remoteOn) Str.get(R.string.settings_sum_remote_on) else Str.get(R.string.settings_off)
    Page.BACKUP -> Str.get(R.string.settings_sum_backup)
    Page.PERMISSIONS -> Str.get(R.string.settings_sum_permissions)
    Page.ABOUT -> Str.get(R.string.settings_sum_about)
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
            Text(stringResource(R.string.settings_rem_exact_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (ok) R.string.settings_rem_exact_ok else R.string.settings_rem_exact_no),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        val intent = AlarmPermissions.exactAlarmSettingsIntent(context)
        if (!ok && intent != null) TextButton(onClick = { runCatching { context.startActivity(intent) } }) { Text(stringResource(R.string.settings_allow)) }
    }
}

@Composable
internal fun VacationEditor(cur: Settings, change: Change) {
    var from by remember(cur.vacationFromDay) { mutableStateOf(cur.vacationFromDay?.let(::formatDate) ?: "") }
    var to by remember(cur.vacationToDay) { mutableStateOf(cur.vacationToDay?.let(::formatDate) ?: "") }
    Text(stringResource(R.string.settings_vacation_blurb), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.settings_vacation_from, dateHint())) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.settings_vacation_to)) }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row {
        val f = parseDate(from)
        val t = parseDate(to)
        TextButton(enabled = f != null && t != null && t >= f, onClick = {
            change { it.copy(vacationFromDay = f, vacationToDay = t) }
        }) { Text(stringResource(R.string.settings_vacation_set)) }
        if (cur.vacationFromDay != null) {
            TextButton(onClick = { change { it.copy(vacationFromDay = null, vacationToDay = null) } }) { Text(stringResource(R.string.settings_vacation_clear)) }
        }
    }
}

@Composable
internal fun DefaultAlarm(kind: OwnerKind, title: String) {
    var cfg by remember { mutableStateOf<AlarmConfigEntity?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(kind) { cfg = AlarmConfigs.defaults(kind) }
    TextButton(onClick = { open = !open }) { Text(stringResource(if (open) R.string.settings_rem_default_hide else R.string.settings_rem_default_edit, title)) }
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
        stringResource(R.string.settings_remote_allow), enabled,
        { v -> enabled = v; launchUi { RemoteSettings.setEnabled(sl.settings, v) } },
        hint = stringResource(R.string.settings_remote_hint),
    )
    Text(stringResource(R.string.settings_remote_last, last?.let(::describeAudit) ?: stringResource(R.string.settings_remote_none)), style = MaterialTheme.typography.bodySmall)
}

/** Turns one audit JSON line into a short readable summary; falls back to the raw line. */
private fun describeAudit(json: String): String = runCatching {
    val o = org.json.JSONObject(json)
    val cmd = o.optString("cmd", "?")
    val refused = !o.optBoolean("ok", true)
    val at = o.optLong("at", 0L)
    val time = if (at > 0) com.earnd.app.ui.common.formatDateTime(at) + ": " else ""
    time + if (refused) Str.get(R.string.settings_remote_refused, cmd) else cmd
}.getOrDefault(json)
