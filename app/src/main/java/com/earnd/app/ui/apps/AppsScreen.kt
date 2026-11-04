package com.earnd.app.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import com.earnd.app.ui.theme.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.earnd.app.domain.Charging
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.ui.common.formatMinuteOfDay
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.common.rememberPuzzleGate
import com.earnd.app.domain.minuteOfDay
import kotlinx.coroutines.launch

private val TABS = listOf("Leisure apps", "Websites", "Always open")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sl = ServiceLocator
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var pending by remember { mutableStateOf<List<PendingRuleChanges.Pending>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    val rules by remember { sl.db.appRuleDao().observeAll() }.collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gate = rememberPuzzleGate()
    LaunchedEffect(Unit) { sl.essentialApps.refresh(); apps = InstalledApps.load(context) }
    LaunchedEffect(reload, rules) { PendingRuleChanges.applyDue(sl.clock.now()); pending = PendingRuleChanges.pending() }

    val announce: (Long) -> Unit = { at ->
        reload++
        if (at > sl.clock.now()) scope.launch {
            snackbar.showSnackbar("Hard mode: this takes effect at ${formatMinuteOfDay(sl.clock.minuteOfDay(at))}.")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("Apps and sites") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            if (pending.isNotEmpty()) PendingList(pending) { key -> launchUi { PendingRuleChanges.cancel(key); reload++ } }
            when (tab) {
                0 -> AppList(apps, rules, header = "Leisure apps are metered: they open while you have minutes and cost a share of the time they are in front. A manual pass makes them free for a fixed time.") { app, isLeisure, isEssential ->
                    if (isEssential) {
                        Text("Always open", style = MaterialTheme.typography.labelSmall)
                    } else {
                        if (isLeisure) {
                            val cur = rules.firstOrNull { it.key == app.pkg && !it.isWebsite }?.meterPct
                            ChargingPicker(cur) { pct ->
                                val apply = { launchUi { announce(PendingRuleChanges.setCharging(app.pkg, app.label, pct)) } }
                                if (Charging.loosens(cur, pct)) gate { apply() } else apply()
                            }
                        }
                        Checkbox(isLeisure, { on ->
                            launchUi {
                                if (on) { PendingRuleChanges.addLeisure(app.pkg, app.label, false); reload++ }
                                else gate { launchUi { announce(PendingRuleChanges.removeLeisure(app.pkg, app.label, false)) } }
                            }
                        })
                    }
                }
                1 -> WebsiteList(rules.filter { it.isWebsite }, onAdd = { host ->
                    launchUi { PendingRuleChanges.addLeisure(host, host, true); reload++ }
                }, onRemove = { r -> gate { launchUi { announce(PendingRuleChanges.removeLeisure(r.key, r.label, true)) } } })
                else -> AppList(apps, rules, header = "Calls, messages, maps, settings and this app are always open. Add others here.") { app, isLeisure, isEssential ->
                    val userAllowed = rules.any { it.key == app.pkg && it.rule == AppRule.ALLOWED }
                    val builtIn = isEssential && !userAllowed
                    Checkbox(isEssential, enabled = !builtIn, onCheckedChange = { on ->
                        if (on) gate { launchUi { announce(PendingRuleChanges.allow(app.pkg, app.label)) } }
                        else launchUi { PendingRuleChanges.disallow(app.pkg); reload++ }
                    })
                    if (isLeisure && !isEssential) Text("Leisure", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** "Charging" selector of a leisure app: 0/25/50/100% of foreground time. */
@Composable
private fun ChargingPicker(current: Int?, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Charging: " + Charging.label(current), style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Charging.RATES.forEach { r ->
                DropdownMenuItem(
                    text = { Text("Metered $r%" + if (r == Charging.DEFAULT_RATE) " (default)" else "") },
                    onClick = { open = false; onPick(r) },
                )
            }
        }
    }
}

@Composable
private fun PendingList(pending: List<PendingRuleChanges.Pending>, onCancel: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Waiting for the hard-mode cooldown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
        pending.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${p.label} at ${formatMinuteOfDay(ServiceLocator.clock.minuteOfDay(p.appliesAt))}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onCancel(p.key) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<InstalledApp>?,
    rules: List<AppRuleEntity>,
    header: String,
    trailing: @Composable (InstalledApp, Boolean, Boolean) -> Unit,
) {
    if (apps == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val essentials = ServiceLocator.essentialApps
    val leisure = rules.filter { it.rule == AppRule.LEISURE && !it.isWebsite }.map { it.key }.toSet()
    val allowed = rules.filter { it.rule == AppRule.ALLOWED }.map { it.key }.toSet()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Text(header, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp)) }
        items(apps, key = { it.pkg }) { app ->
            val isEssential = essentials.isEssential(app.pkg) || app.pkg in allowed
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(app.pkg) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                if (bmp != null) Image(bmp, null, Modifier.size(36.dp)) else Box(Modifier.size(36.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing(app, app.pkg in leisure, isEssential)
            }
        }
    }
}

@Composable
private fun WebsiteList(sites: List<AppRuleEntity>, onAdd: (String) -> Unit, onRemove: (AppRuleEntity) -> Unit) {
    var text by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Sites listed here lock in browsers, including their subdomains.", modifier = Modifier.padding(vertical = 12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("example.com") },
                trailingIcon = {
                    IconButton(onClick = {
                        normalizeHost(text)?.let(onAdd)
                        text = ""
                    }) { Icon(Icons.Outlined.Add, "Add site") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(sites, key = { it.key }) { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(r) }) { Icon(Icons.Outlined.Close, "Remove") }
            }
        }
    }
}
