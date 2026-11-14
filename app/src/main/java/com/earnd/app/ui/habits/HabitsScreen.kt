package com.earnd.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.produceState
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.ui.theme.Checkbox
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
import androidx.compose.ui.res.stringResource
import com.earnd.app.R
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
fun HabitsScreen(onOpenHabit: (Long) -> Unit, onAddHabit: () -> Unit, onPhotoCheckIn: (Long) -> Unit = {}) {
    val sl = ServiceLocator
    // Same check-in and undo as the Today list, so a habit can be ticked (or un-ticked) from here too.
    val todayDay by produceState(sl.clock.today().toEpochDay()) { while (true) { value = sl.clock.today().toEpochDay(); kotlinx.coroutines.delay(30_000) } }
    val todays by remember(todayDay) { sl.db.checkInDao().observeForDay(todayDay) }.collectAsState(initial = emptyList())
    val settings by remember { sl.settings.settings }.collectAsState(initial = Settings())
    val snackbar = remember { SnackbarHostState() }
    var askAmount by remember { mutableStateOf<HabitEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val doCheck: (HabitDay) -> Unit = { hd ->
        when {
            com.earnd.app.domain.TimedHabit.isTimed(hd.habit) -> launchUi { com.earnd.app.focus.startFocus(context, hd.habit.id) }
            HabitRules.needsPhoto(hd.habit, settings) -> onPhotoCheckIn(hd.habit.id)
            hd.habit.targetQuantity != null -> askAmount = hd.habit
            else -> launchUi {
                val r = sl.habitService.checkInWithBonus(hd.habit.id)
                com.earnd.app.focus.bonusText(r)?.let { snackbar.showSnackbar(it) }
            }
        }
    }
    val doUndo: (HabitDay) -> Unit = { hd ->
        todays.filter { it.habitId == hd.habit.id }.maxByOrNull { it.at }?.let { c -> launchUi { HabitActions.undo(c.id) } }
    }
    val rows by remember { sl.habitService.observeAllActive() }.collectAsState(initial = null)
    val archived by remember { sl.habitService.observeArchived() }.collectAsState(initial = emptyList())
    var streaks by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    // Active days: a run of days with any check-in, shown when the strict streak (full daily target) is zero.
    var actives by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(rows) {
        val today = sl.clock.today()
        streaks = rows.orEmpty().associate { (d, _) -> d.habit.id to runCatching { sl.habitService.streak(d.habit.id, today) }.getOrDefault(0) }
        actives = rows.orEmpty().associate { (d, _) -> d.habit.id to runCatching { sl.habitService.activeStreak(d.habit.id, today) }.getOrDefault(0) }
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
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!reorder) com.earnd.app.ui.theme.AddFab(onClick = onAddHabit, description = stringResource(R.string.habits_add))
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.habits_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (reorder) TextButton(onClick = { reorder = false }) { Text(stringResource(R.string.habits_done)) }
                    else IconButton(onClick = { reorder = true }, enabled = order.size > 1) {
                        Icon(Icons.Outlined.SwapVert, stringResource(R.string.habits_reorder), tint = p.secondary)
                    }
                }
            }
            val list = rows
            if (list != null && list.isEmpty()) item {
                Text(stringResource(R.string.habits_empty), style = MaterialTheme.typography.bodyMedium,
                    color = p.muted, modifier = Modifier.padding(vertical = 16.dp))
            }
            if (reorder) {
                itemsIndexed(order, key = { _, h -> "o${h.id}" }) { i, h ->
                    Line(h, onOpen = {}) {
                        IconButton(enabled = i > 0, onClick = { move(i, i - 1) }) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.habits_move_up)) }
                        IconButton(enabled = i < order.lastIndex, onClick = { move(i, i + 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.habits_move_down)) }
                    }
                }
            } else {
                items(list.orEmpty(), key = { "h${it.first.habit.id}" }) { (d, due) ->
                    HabitOverviewRow(d, due, streaks[d.habit.id] ?: 0, actives[d.habit.id] ?: 0, onOpen = { onOpenHabit(d.habit.id) },
                        onCheck = { doCheck(d) }, onUndo = { doUndo(d) })
                    RowDivider()
                }
            }
            if (archived.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.habits_archived)) }
                items(archived, key = { "a${it.id}" }) { h ->
                    Line(h, onOpen = { onOpenHabit(h.id) }, muted = true) {
                        TextButton(onClick = { launchUi { sl.habitService.unarchive(h.id) } }) { Text(stringResource(R.string.habits_restore)) }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 88.dp)) }
        }
    }
    askAmount?.let { h ->
        AmountDialog(h, onDismiss = { askAmount = null }) { amount ->
            askAmount = null
            launchUi { sl.habitService.checkIn(h.id, quantity = amount) }
        }
    }
}

@Composable
private fun HabitOverviewRow(d: HabitDay, due: Boolean, streak: Int, activeDays: Int, onOpen: () -> Unit, onCheck: () -> Unit, onUndo: () -> Unit) {
    val settings by androidx.compose.runtime.remember { com.earnd.app.ServiceLocator.settings.settings }
        .collectAsState(initial = com.earnd.app.data.Settings())
    val h = d.habit
    val p = Earnd.palette
    com.earnd.app.ui.theme.ListRow(onOpen, minHeight = 64.dp) {
        // Tick to check in, tick again to take the last one back. A rest day (not due, nothing logged) has nothing to tick.
        if (due || d.done > 0) Checkbox(
            checked = d.complete,
            onCheckedChange = { if (!d.complete) onCheck() else onUndo() },
            reward = true,
            modifier = Modifier.offset(x = (-17).dp),
        ) else Spacer(Modifier.size(48.dp).offset(x = (-17).dp))
        Column(Modifier.weight(1f).offset(x = (-17).dp).padding(end = 12.dp, top = 6.dp, bottom = 6.dp)) {
            // A done name stays one step above its own subtitle: muted here made the habit dimmer than "Daily".
            Text(h.name, style = MaterialTheme.typography.bodyLarge, color = if (d.complete) p.secondary else p.text,
                textDecoration = if (d.complete) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            val focusing = com.earnd.app.focus.focusLabel(h.id)
            if (focusing != null) com.earnd.app.ui.theme.Num(focusing, color = p.amber)
            else Column {
                val schedule = HabitRules.scheduleSummary(h)
                val focusOptions = com.earnd.app.domain.TimedHabit.options(h, settings)
                Text(
                    when {
                        h.stopwatch -> stringResource(R.string.habits_row_stopwatch, schedule)
                        focusOptions.isNotEmpty() ->
                            stringResource(R.string.habits_row_focus, schedule, focusOptions.joinToString("/") { "${it.minutes}" })
                        else -> schedule
                    },
                    style = MaterialTheme.typography.bodySmall, color = p.muted,
                )
                if (!d.complete && d.comebackMissed > 0) com.earnd.app.ui.theme.Num(
                    stringResource(R.string.habits_row_comeback, com.earnd.app.domain.Comeback.label(d.comebackMissed)), color = p.amber,
                )
            }
        }
        if (due && !d.complete && com.earnd.app.domain.TimedHabit.isTimed(h) && !h.archived) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.material3.TextButton(onClick = { com.earnd.app.ui.common.launchUi { com.earnd.app.focus.startFocus(context, h.id) } }) {
                Text(com.earnd.app.focus.startLabel(h, settings), color = p.amber)
            }
        }
        // Partway through a multi-check-in habit (6 of 10): the tick adds one, this takes the last one back.
        if (d.done > 0 && !d.complete) IconButton(onClick = onUndo) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.habits_undo_checkin), tint = p.secondary)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 64.dp)) {
            val today = when {
                d.complete -> if (d.done > d.required) "${d.done}/${d.required} ✓" else stringResource(R.string.habits_today_done)
                !due -> stringResource(R.string.habits_today_rest)
                else -> "${d.done}/${d.required}"
            }
            // The tick already says "done"; repeating it in amber made the status louder than the habit name.
            Num(today, color = if (due && !d.complete) p.secondary else p.muted)
            val small = com.earnd.app.ui.theme.NumberStyle.copy(fontSize = 12.sp)
            if (streak > 0) Num(stringResource(R.string.habits_streak_short, streak), color = p.muted, style = small)
            else if (activeDays > 0) Num(stringResource(R.string.habits_active_short, activeDays), color = p.muted, style = small)
        }
    }
}

@Composable
private fun Line(h: HabitEntity, onOpen: () -> Unit, muted: Boolean = false, trailing: @Composable () -> Unit) {
    com.earnd.app.ui.theme.ListRow(onOpen, minHeight = 56.dp) {
        Text(h.name, style = MaterialTheme.typography.bodyLarge, color = if (muted) Earnd.palette.muted else Earnd.palette.text,
            modifier = Modifier.weight(1f).padding(end = 12.dp))
        trailing()
    }
}
