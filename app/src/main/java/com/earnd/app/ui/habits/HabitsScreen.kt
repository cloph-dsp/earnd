package com.earnd.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.HabitDay
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.RowDivider

/**
 * Habits tab: every active habit (not just today's) with schedule, today's progress and streak.
 * Reorder mode and the archive live here too.
 */
@Composable
fun HabitsScreen(onOpenHabit: (Long) -> Unit, onAddHabit: () -> Unit) {
    val sl = ServiceLocator
    val rows by remember { sl.habitService.observeAllActive() }.collectAsState(initial = null)
    val archived by remember { sl.habitService.observeArchived() }.collectAsState(initial = emptyList())
    var streaks by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(rows) {
        val today = sl.clock.today()
        streaks = rows.orEmpty().associate { (d, _) -> d.habit.id to runCatching { sl.habitService.streak(d.habit.id, today) }.getOrDefault(0) }
    }
    var reorder by remember { mutableStateOf(false) }
    // Local order so reorder taps feel instant; resynced whenever the database list changes.
    var order by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    LaunchedEffect(rows) { order = rows.orEmpty().map { it.first.habit } }
    val move: (Int, Int) -> Unit = { from, to ->
        val next = HabitRules.move(order, from, to)
        if (next !== order) {
            order = next
            launchUi { sl.habitService.reorder(next.map { it.id }) }
        }
    }
    val p = Earnd.palette

    Scaffold(
        floatingActionButton = {
            if (!reorder) ExtendedFloatingActionButton(
                onClick = onAddHabit, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Habit") },
                containerColor = p.raised, contentColor = MaterialTheme.colorScheme.onBackground,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Habits", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (reorder) TextButton(onClick = { reorder = false }) { Text("Done") }
                    else IconButton(onClick = { reorder = true }, enabled = order.size > 1) {
                        Icon(Icons.Outlined.SwapVert, "Reorder habits", tint = p.secondary)
                    }
                }
                RowDivider()
            }
            val list = rows
            if (list != null && list.isEmpty()) item {
                Text("No habits yet. Add one to start earning screen time.", style = MaterialTheme.typography.bodyMedium,
                    color = p.muted, modifier = Modifier.padding(vertical = 16.dp))
            }
            if (reorder) {
                itemsIndexed(order, key = { _, h -> "o${h.id}" }) { i, h ->
                    Line(h, onOpen = {}) {
                        IconButton(enabled = i > 0, onClick = { move(i, i - 1) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                        IconButton(enabled = i < order.lastIndex, onClick = { move(i, i + 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                    }
                    RowDivider()
                }
            } else {
                items(list.orEmpty(), key = { "h${it.first.habit.id}" }) { (d, due) ->
                    HabitOverviewRow(d, due, streaks[d.habit.id] ?: 0, onOpen = { onOpenHabit(d.habit.id) })
                    RowDivider()
                }
            }
            if (archived.isNotEmpty()) {
                item { SectionTitle("Archived") }
                items(archived, key = { "a${it.id}" }) { h ->
                    Line(h, onOpen = { onOpenHabit(h.id) }, muted = true) {
                        TextButton(onClick = { launchUi { sl.habitService.unarchive(h.id) } }) { Text("Restore") }
                    }
                    RowDivider()
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 88.dp)) }
        }
    }
}

@Composable
private fun HabitOverviewRow(d: HabitDay, due: Boolean, streak: Int, onOpen: () -> Unit) {
    val settings by androidx.compose.runtime.remember { com.earnd.app.ServiceLocator.settings.settings }
        .collectAsState(initial = com.earnd.app.data.Settings())
    val h = d.habit
    val p = Earnd.palette
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Icon(HabitLook.icon(h.iconKey), null, tint = Color(h.colorArgb), modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(h.name, style = MaterialTheme.typography.bodyLarge, color = if (d.complete) p.done else p.text)
            val focusing = com.earnd.app.focus.focusLabel(h.id)
            if (focusing != null) com.earnd.app.ui.theme.Num(focusing, color = p.amber)
            else Row {
                Text(
                    HabitRules.scheduleSummary(h) + when {
                        h.stopwatch -> " · stopwatch"
                        else -> com.earnd.app.domain.TimedHabit.options(h, settings).takeIf { it.isNotEmpty() }
                            ?.let { o -> " · " + o.joinToString("/") { "${it.minutes}" } + " min focus" } ?: ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = p.muted,
                )
                if (!d.complete && d.comebackMissed > 0) com.earnd.app.ui.theme.Num(
                    "  " + com.earnd.app.domain.Comeback.label(d.comebackMissed) + " comeback", color = p.amber,
                )
            }
        }
        if (due && !d.complete && com.earnd.app.domain.TimedHabit.isTimed(h) && !h.archived) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.material3.TextButton(onClick = { com.earnd.app.ui.common.launchUi { com.earnd.app.focus.startFocus(context, h.id) } }) {
                Text(com.earnd.app.focus.startLabel(h, settings), color = p.amber)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val today = when {
                d.complete -> if (d.done > d.required) "${d.done}/${d.required} ✓" else "done"
                !due -> "rest"
                else -> "${d.done}/${d.required}"
            }
            Num(today, color = if (d.complete) p.amber else if (due) p.secondary else p.muted)
            Num(if (streak > 0) "${streak}d" else "-", color = if (streak > 0) p.secondary else p.muted)
        }
    }
}

@Composable
private fun Line(h: HabitEntity, onOpen: () -> Unit, muted: Boolean = false, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Icon(HabitLook.icon(h.iconKey), null, tint = Color(h.colorArgb), modifier = Modifier.size(16.dp))
        Text(h.name, style = MaterialTheme.typography.bodyLarge, color = if (muted) Earnd.palette.muted else Earnd.palette.text,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        trailing()
    }
}
