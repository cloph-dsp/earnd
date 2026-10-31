package com.earnd.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField
import com.earnd.app.ui.common.formatIsoDay
import com.earnd.app.ui.common.formatMinuteOfDay
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.common.parseIsoDay
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun SettingsScreen(onOpenApps: () -> Unit, onOpenPermissions: () -> Unit) {
    val sl = ServiceLocator
    val s by remember { sl.settings.settings }.collectAsState(initial = null)
    var pendingAt by remember { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s, refresh) { pendingAt = sl.settings.pendingWeakeningAt()?.takeIf { it > 0 } }

    val change: ((Settings) -> Settings) -> Unit = { transform ->
        launchUi {
            val cur = sl.settings.current()
            if (SettingsChanges.isWeakening(cur, transform(cur))) {
                val at = sl.settings.requestWeakening(transform)
                refresh++
                if (at > sl.clock.now()) scope.launch {
                    snackbar.showSnackbar("Hard mode: this change applies at ${formatMinuteOfDay(sl.clock.minuteOfDay(at))}.")
                }
            } else {
                sl.settings.update(transform)
            }
        }
    }

    val cur = s ?: return
    Column(Modifier.fillMaxSize()) {
        SnackbarHost(snackbar)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))

            pendingAt?.let { at ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(12.dp)) {
                        Text("A loosening change is waiting and applies at ${formatMinuteOfDay(sl.clock.minuteOfDay(at))}.", Modifier.weight(1f))
                        TextButton(onClick = { launchUi { sl.settings.cancelPendingWeakening(); refresh++ } }) { Text("Cancel it") }
                    }
                }
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
            NumberField("Default task reward (minutes, blank = none)", cur.defaultTaskRewardMin, { v -> change { it.copy(defaultTaskRewardMin = v) } }, Modifier.fillMaxWidth())

            SectionTitle("Hard mode")
            SwitchRow(
                "Hard mode", cur.hardMode, { v -> change { it.copy(hardMode = v) } },
                hint = "Minutes can't be spent by hand; leisure opens only when everything is done. Loosening any setting waits for the cooldown.",
            )
            NumberField("Cooldown before loosening (minutes)", cur.weakenCooldownMin, { v -> change { it.copy(weakenCooldownMin = (v ?: 0).coerceIn(1, 24 * 60)) } }, Modifier.fillMaxWidth())

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
            DefaultAlarm(OwnerKind.TASK, "Default for tasks")
            DefaultAlarm(OwnerKind.HABIT, "Default for habit reminders")

            SectionTitle("Remote control")
            RemoteSection()

            SectionTitle("System")
            OutlinedButton(onClick = onOpenPermissions) { Text("Permissions") }
            Text(
                "Earnd is free software (GPL-3.0). Everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun VacationEditor(cur: Settings, change: ((Settings) -> Settings) -> Unit) {
    var from by remember(cur.vacationFromDay) { mutableStateOf(cur.vacationFromDay?.let(::formatIsoDay) ?: "") }
    var to by remember(cur.vacationToDay) { mutableStateOf(cur.vacationToDay?.let(::formatIsoDay) ?: "") }
    Text("Pauses blocking and streaks between two dates.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(from, { from = it }, label = { Text("From (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(to, { to = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row {
        val f = parseIsoDay(from)
        val t = parseIsoDay(to)
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
    val time = if (at > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(at)) + ": " else ""
    time + cmd + ok
}.getOrDefault(json)
