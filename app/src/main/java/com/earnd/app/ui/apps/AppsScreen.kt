package com.earnd.app.ui.apps

import com.earnd.app.R
import androidx.compose.ui.res.stringResource
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
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.common.rememberPuzzleGate
import com.earnd.app.domain.minuteOfDay
import com.earnd.app.domain.today
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import com.earnd.app.domain.startOfDay
import com.earnd.app.ui.onboarding.AppPermissions
import com.earnd.app.ui.stats.UsageReport
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.RowDivider
import kotlinx.coroutines.launch

private val TABS = listOf(R.string.apps_tab_leisure, R.string.apps_tab_websites, R.string.apps_tab_always_open)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sl = ServiceLocator
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var pending by remember { mutableStateOf<List<PendingRuleChanges.Pending>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var usage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val rules by remember { sl.db.appRuleDao().observeAll() }.collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gate = rememberPuzzleGate()
    LaunchedEffect(Unit) { sl.essentialApps.refresh(); apps = InstalledApps.load(context) }
    LaunchedEffect(Unit) {
        if (AppPermissions.usageAccessOn(context)) {
            val now = sl.clock.now()
            usage = UsageReport.today(context, sl.clock.startOfDay(sl.clock.today()), now)?.perApp?.toMap().orEmpty()
        }
    }
    LaunchedEffect(reload, rules) { PendingRuleChanges.applyDue(sl.clock.now()); pending = PendingRuleChanges.pending() }

    val announce: (Long) -> Unit = { at ->
        reload++
        if (at > sl.clock.now()) scope.launch {
            snackbar.showSnackbar(context.getString(R.string.apps_hard_mode_effect, formatMinuteOfDay(sl.clock.minuteOfDay(at))))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.apps_title)) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.apps_back)) }
            })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, divider = { RowDivider() }) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, selectedContentColor = Earnd.palette.amber, unselectedContentColor = Earnd.palette.secondary, text = { Text(stringResource(t), maxLines = 1) }) }
            }
            if (pending.isNotEmpty()) PendingList(pending) { key -> launchUi { PendingRuleChanges.cancel(key); reload++ } }
            when (tab) {
                0 -> AppList(apps, rules, usage, header = stringResource(R.string.apps_header_leisure)) { app, isLeisure, isEssential ->
                    if (isEssential) {
                        Text(stringResource(R.string.apps_tab_always_open), style = MaterialTheme.typography.labelSmall, color = Earnd.palette.muted)
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
                else -> AppList(apps, rules, usage, header = stringResource(R.string.apps_header_always_open)) { app, isLeisure, isEssential ->
                    val userAllowed = rules.any { it.key == app.pkg && it.rule == AppRule.ALLOWED }
                    val builtIn = isEssential && !userAllowed
                    Checkbox(isEssential, enabled = !builtIn, onCheckedChange = { on ->
                        if (on) gate { launchUi { announce(PendingRuleChanges.allow(app.pkg, app.label)) } }
                        else launchUi { PendingRuleChanges.disallow(app.pkg); reload++ }
                    })
                    if (isLeisure && !isEssential) Text(stringResource(R.string.apps_leisure_tag), style = MaterialTheme.typography.labelSmall, color = Earnd.palette.amber)
                }
            }
        }
    }
}

/** "Charging" selector of a leisure app: a compact share ("50%") that opens the 0/25/50/75/100% menu. */
@Composable
private fun ChargingPicker(current: Int?, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val rate = Charging.effective(current)
    Box {
        Box(
            Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp).clickable { open = true },
            contentAlignment = Alignment.CenterEnd,
        ) { Num("$rate%", color = Earnd.palette.amber) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Charging.RATES.forEach { r ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (r == Charging.DEFAULT_RATE) R.string.apps_counts_pct_default else R.string.apps_counts_pct, r)) },
                    onClick = { open = false; onPick(r) },
                )
            }
        }
    }
}

@Composable
private fun PendingList(pending: List<PendingRuleChanges.Pending>, onCancel: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.apps_pending_title), style = MaterialTheme.typography.titleSmall, color = com.earnd.app.ui.theme.Earnd.palette.secondary)
        pending.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.apps_pending_item, p.label, formatMinuteOfDay(ServiceLocator.clock.minuteOfDay(p.appliesAt))),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onCancel(p.key) }) { Text(stringResource(R.string.apps_cancel)) }
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<InstalledApp>?,
    rules: List<AppRuleEntity>,
    usage: Map<String, Long>,
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
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(apps, leisure, query) {
        val q = query.trim()
        apps.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<InstalledApp> { it.pkg !in leisure }.thenBy { it.label.lowercase() })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.apps_clear_search)) }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text(header, style = MaterialTheme.typography.bodySmall, color = Earnd.palette.secondary, modifier = Modifier.padding(vertical = 8.dp)) }
            if (shown.isEmpty()) item {
                Text(stringResource(R.string.apps_no_match), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.muted, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(shown, key = { it.pkg }) { app ->
                val isEssential = essentials.isEssential(app.pkg) || app.pkg in allowed
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    val bmp = remember(app.pkg) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                    if (bmp != null) Image(bmp, null, Modifier.size(36.dp)) else Box(Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        val ms = usage[app.pkg] ?: 0L
                        if (ms >= 60_000L) Num(stringResource(R.string.apps_usage_today, formatMinutes((ms / 60_000).toInt())), color = Earnd.palette.muted)
                    }
                    trailing(app, app.pkg in leisure, isEssential)
                }
                RowDivider()
            }
        }
    }
}

@Composable
private fun WebsiteList(sites: List<AppRuleEntity>, onAdd: (String) -> Unit, onRemove: (AppRuleEntity) -> Unit) {
    var text by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text(stringResource(R.string.apps_sites_explain), style = MaterialTheme.typography.bodySmall, color = Earnd.palette.secondary, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("example.com") },
                trailingIcon = {
                    IconButton(onClick = {
                        normalizeHost(text)?.let(onAdd)
                        text = ""
                    }) { Icon(Icons.Outlined.Add, stringResource(R.string.apps_add_site)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (sites.isEmpty()) item {
            Text(stringResource(R.string.apps_no_sites), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.muted, modifier = Modifier.padding(vertical = 16.dp))
        }
        items(sites, key = { it.key }) { r ->
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onRemove(r) }) { Icon(Icons.Outlined.Close, stringResource(R.string.apps_remove_site, r.label)) }
            }
            RowDivider()
        }
    }
}
