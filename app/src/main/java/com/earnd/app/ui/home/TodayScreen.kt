package com.earnd.app.ui.home

import com.earnd.app.data.entity.ListDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.TaskBucket
import com.earnd.app.ui.common.formatMinuteOfDay
import com.earnd.app.ui.tasks.TaskRow
import com.earnd.app.ui.theme.Checkbox
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.ProgressBar
import com.earnd.app.ui.theme.RowDivider
import com.earnd.app.ui.theme.Wordmark
import kotlinx.coroutines.delay
import com.earnd.app.ServiceLocator
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.domain.HabitDay
import com.earnd.app.domain.SpendResult
import com.earnd.app.domain.TimeBank
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.habits.HabitLook
import com.earnd.app.ui.habits.HabitActions
import com.earnd.app.ui.habits.AmountDialog
import com.earnd.app.ui.habits.HabitRules
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.minuteOfDay
import com.earnd.app.domain.Segments
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.earnd.app.R
import com.earnd.app.i18n.Str

private val SEGMENT_ORDER = listOf(DaySegment.MORNING, DaySegment.AFTERNOON, DaySegment.EVENING, DaySegment.NONE)

@Composable
fun TodayScreen(onOpenHabit: (Long) -> Unit, onAddHabit: () -> Unit, onPhotoCheckIn: (Long) -> Unit, onOpenTask: (Long) -> Unit = {}, onOpenPermissions: () -> Unit = {}) {
    val sl = ServiceLocator
    val todayDay by produceState(sl.clock.today().toEpochDay()) { while (true) { value = sl.clock.today().toEpochDay(); delay(30_000) } }
    val loadedHabits by remember { sl.habitService.observeToday() }.collectAsState(initial = null)
    val habits = loadedHabits.orEmpty()
    val checkIns by remember(todayDay) { sl.db.checkInDao().observeForDay(todayDay) }.collectAsState(initial = emptyList())
    val balance by remember { sl.timeBank.observeBalance() }.collectAsState(initial = null)
    val session by remember { sl.timeBank.observeActiveSession() }.collectAsState(initial = null)
    val settings by remember { sl.settings.settings }.collectAsState(initial = Settings())
    val snackbar = remember { SnackbarHostState() }
    var doneOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var askAmount by remember { mutableStateOf<HabitEntity?>(null) }
    val byHabit = checkIns.groupBy { it.habitId }
    val doneOf = { hd: HabitDay -> hd.complete }
    val minuteNow = sl.clock.minuteOfDay(sl.clock.now())
    val currentSeg = if (settings.segmentMode) Segments.current(minuteNow, settings) else null

    // Bonus habits earn like the rest but are not needed to close the day (the all-done reward mode counts every habit).
    val core = if (settings.rewardMode == com.earnd.app.data.entity.RewardMode.ALL_DONE) habits else habits.filterNot { it.habit.bonus }
    val allDone = core.isNotEmpty() && core.all(doneOf)
    val bank = BankState(balance?.minutes ?: 0, session, settings.hardMode, allDone = allDone, allDoneUnlock = settings.allDoneUnlock, unlockCapMin = settings.unlockCapMin,
        loading = balance == null, nextExpiryAt = balance?.nextExpiryAt, debtMin = balance?.debt ?: 0, minutesExpiringNext = balance?.minutesExpiringNext ?: 0)
    val inBedtime = TimeBank.bedtimeWindow(settings)?.contains(System.currentTimeMillis(), sl.clock.zone()) == true

    val nags by remember { sl.db.nagStateDao().observeAll() }.collectAsState(initial = emptyList())
    val ignored = nags.filter { it.ownerKind == OwnerKind.HABIT }.associate { it.ownerId to it.nagCount }
    val overdue by remember { sl.taskService.observeBucket(TaskBucket.OVERDUE) }.collectAsState(initial = emptyList())
    val dueToday by remember { sl.taskService.observeBucket(TaskBucket.TODAY) }.collectAsState(initial = emptyList())
    val tasksToday = overdue + dueToday
    var clock by remember { mutableStateOf(formatMinuteOfDay(minuteNow)) }
    LaunchedEffect(Unit) { while (true) { clock = formatMinuteOfDay(sl.clock.minuteOfDay(sl.clock.now())); delay(15_000) } }

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
    val habitRow: @Composable (HabitDay) -> Unit = { hd ->
        val last = checkIns.filter { it.habitId == hd.habit.id }.maxByOrNull { it.at }
        HabitRow(
            hd, last, doneOf(hd), byHabit[hd.habit.id].orEmpty(), settings, ignored[hd.habit.id] ?: 0,
            onOpen = { onOpenHabit(hd.habit.id) },
            onCheck = { doCheck(hd) },
            onUndo = { c -> launchUi { HabitActions.undo(c.id) } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Wordmark()
                    Spacer(Modifier.width(10.dp))
                    Num(clock, Modifier.weight(1f))
                    OffDayToggle(settings.onOffDay(todayDay)) { on ->
                        launchUi { sl.settings.update { it.copy(offDayEpochDay = if (on) todayDay else null) } }
                    }
                    Spacer(Modifier.width(8.dp))
                    AddButton(onClick = onAddHabit, description = stringResource(R.string.home_add_habit))
                }
                HealthBanner(onOpenPermissions)
                TimeBankCard(
                    state = bank,
                    onSpend = { m ->
                        launchUi {
                            val r = sl.timeBank.spend(m)
                            if (r is SpendResult.Refused) snackbar.showSnackbar(refuseText(r.reason))
                        }
                    },
                    onStop = { launchUi { sl.timeBank.endSession() } },
                    onAllDoneUnlock = { launchUi { sl.timeBank.startAllDoneUnlock() } },
                )
                if (inBedtime && session == null) {
                    TextButton(onClick = {
                        launchUi {
                            val r = sl.timeBank.startBedtimeGrace()
                            if (r is SpendResult.Refused) snackbar.showSnackbar(refuseText(r.reason))
                        }
                    }) { Text(pluralStringResource(R.plurals.home_bedtime_grace, settings.bedtimeGraceMin, settings.bedtimeGraceMin)) }
                }
            }
            if (habits.isNotEmpty()) {
                item(key = "progress") {
                    val counted = core.ifEmpty { habits }
                    val done = counted.count(doneOf)
                    Column(Modifier.padding(top = 28.dp)) {
                        // Caption above the bar: below it, the bar read as a second balance bar
                        // belonging to the card above.
                        Num(pluralStringResource(R.plurals.home_progress, counted.size, counted.size, done), Modifier.padding(bottom = 6.dp))
                        com.earnd.app.ui.theme.SegmentBar(done, counted.size)
                    }
                }
            } else if (loadedHabits != null) {
                item {
                    Text(
                        stringResource(R.string.home_nothing_scheduled),
                        style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
            val byIgnored = compareBy<HabitDay> { it.habit.bonus }.thenByDescending { ignored[it.habit.id] ?: 0 }
            val pendingHabits = habits.filterNot(doneOf).sortedWith(byIgnored)
            val doneHabits = habits.filter(doneOf)
            val pendingTasks = tasksToday.filter { it.completedAt == null }
            val doneTasks = tasksToday.filter { it.completedAt != null }
            val nextHabit = pendingHabits.firstOrNull()
            val nextTask = if (nextHabit == null) pendingTasks.firstOrNull() else null
            val restHabits = if (nextHabit != null) pendingHabits.drop(1) else pendingHabits
            val restTasks = if (nextTask != null) pendingTasks.drop(1) else pendingTasks
            val doneCount = doneHabits.size + doneTasks.size

            if (nextHabit != null || nextTask != null) {
                item(key = "next") {
                    val p = Earnd.palette
                    Column(Modifier.padding(top = 8.dp)) {
                        SectionTitle(stringResource(R.string.home_next))
                        if (nextHabit != null) {
                            val h = nextHabit.habit
                            val reward = HabitRules.rewardMinutes(h, settings)
                            val nagCount = ignored[h.id] ?: 0
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { onOpenHabit(h.id) }.padding(vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(h.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall, color = p.text)
                                    }
                                    Row {
                                        if (reward > 0) Num(stringResource(R.string.home_reward_min, reward), color = p.amber)
                                        if (nagCount > 0) Num((if (reward > 0) " · " else "") + stringResource(R.string.home_ignored, nagCount), color = p.red)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                val label = when {
                                    com.earnd.app.domain.TimedHabit.isTimed(h) -> com.earnd.app.focus.startLabel(h, settings)
                                    HabitRules.needsPhoto(h, settings) -> stringResource(R.string.home_action_photo)
                                    h.targetQuantity != null -> stringResource(R.string.home_action_add)
                                    else -> stringResource(R.string.home_action_check_in)
                                }
                                com.earnd.app.ui.theme.QuietButton(onClick = { doCheck(nextHabit) }, Modifier.heightIn(min = 52.dp)) { Text(label) }
                            }
                        } else if (nextTask != null) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { onOpenTask(nextTask.id) }.padding(vertical = 8.dp)) {
                                    Text(nextTask.title, style = MaterialTheme.typography.headlineSmall, color = p.text)
                                    Num(stringResource(R.string.home_task), color = p.secondary)
                                }
                                Spacer(Modifier.width(12.dp))
                                com.earnd.app.ui.theme.QuietButton(onClick = { launchUi { sl.taskService.complete(nextTask.id) } }, Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.common_done)) }
                            }
                        }
                    }
                }
            }
            if (settings.segmentMode) {
                val bySegment = restHabits.groupBy { it.habit.segment }
                SEGMENT_ORDER.forEach { seg ->
                    val list = bySegment[seg].orEmpty()
                    if (habits.any { it.habit.segment == seg }) {
                        item(key = "seg-$seg") {
                            val all = habits.filter { it.habit.segment == seg }.let { l -> if (core.size == habits.size) l else l.filter { it in core }.ifEmpty { l } }
                            val done = all.count(doneOf)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle(HabitLook.segmentLabel(seg), Modifier.weight(1f))
                                if (seg == currentSeg) {
                                    Text(stringResource(R.string.home_now), style = MaterialTheme.typography.labelMedium, color = Earnd.palette.secondary,
                                        modifier = Modifier.padding(top = 28.dp, end = 8.dp))
                                }
                                Num("$done/${all.size}", Modifier.padding(top = 28.dp))
                            }
                        }
                        items(list, key = { it.habit.id }) { habitRow(it) }
                    }
                }
            } else if (restHabits.isNotEmpty()) {
                item(key = "do-now") { SectionTitle(stringResource(R.string.home_later_today)) }
                items(restHabits, key = { it.habit.id }) { habitRow(it) }
            }
            if (restTasks.isNotEmpty()) {
                item(key = "tasks-today") { SectionTitle(stringResource(R.string.home_tasks_today)) }
                items(restTasks, key = { "t${it.id}" }) { t -> TaskRow(t, onOpenTask) }
            }
            if (doneCount > 0) {
                item(key = "done-toggle") {
                    Column {
                        com.earnd.app.ui.theme.ListRow(onClick = { doneOpen = !doneOpen }, Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.home_done_count, doneCount), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Earnd.palette.secondary)
                            Icon(if (doneOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                stringResource(if (doneOpen) R.string.home_collapse_done else R.string.home_expand_done), tint = Earnd.palette.muted)
                        }
                        RowDivider()
                    }
                }
                if (doneOpen) {
                    items(doneHabits, key = { "d${it.habit.id}" }) { habitRow(it) }
                    items(doneTasks, key = { "dt${it.id}" }) { t -> TaskRow(t, onOpenTask) }
                }
            }
            item { Box(Modifier.size(88.dp)) }
        }
    }
    askAmount?.let { h ->
        AmountDialog(h, onDismiss = { askAmount = null }) { amount ->
            askAmount = null
            launchUi { sl.habitService.checkIn(h.id, quantity = amount) }
        }
    }
}


fun refuseText(reason: String): String = when (reason) {
    com.earnd.app.domain.RefuseReason.HARD_MODE -> Str.get(R.string.home_refuse_hard_mode)
    com.earnd.app.domain.RefuseReason.NOT_ENOUGH -> Str.get(R.string.home_refuse_not_enough)
    com.earnd.app.domain.RefuseReason.GRACE_USED -> Str.get(R.string.home_refuse_grace_used)
    com.earnd.app.domain.RefuseReason.NOT_BEDTIME -> Str.get(R.string.home_refuse_not_bedtime)
    else -> Str.get(R.string.home_refuse_other)
}

@Composable
private fun HabitRow(hd: HabitDay, last: CheckInEntity?, complete: Boolean, todays: List<CheckInEntity>, settings: Settings, ignored: Int, onOpen: () -> Unit, onCheck: () -> Unit, onUndo: (CheckInEntity) -> Unit) {
    val h = hd.habit
    val p = Earnd.palette
    val compact = settings.listDensity == ListDensity.COMPACT
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 56.dp else 64.dp).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = complete,
            onCheckedChange = { if (!complete) onCheck() else last?.let(onUndo) },
            reward = true,
            modifier = Modifier.offset(x = (-17).dp),
        )
        Column(Modifier.weight(1f).offset(x = (-17).dp).padding(vertical = if (compact) 2.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.name, Modifier.weight(1f, fill = false),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = if (complete) p.done else p.text,
                    textDecoration = if (complete) TextDecoration.LineThrough else null,
                )
            }
            val comebackText = if (!complete && hd.comebackMissed > 0) {
                stringResource(R.string.home_comeback_label, com.earnd.app.domain.Comeback.label(hd.comebackMissed))
            } else null
            val parts = buildList {
                if (hd.required > 1 || hd.done > hd.required) add("${hd.done}/${hd.required}" + if (complete) " ✓" else "")
                if (h.bonus) add(stringResource(R.string.habits_bonus_tag))
                if (comebackText != null) add(comebackText)
                if (h.targetQuantity != null) {
                    add("${fmtQty(HabitRules.quantityDone(todays))}/${fmtQty(h.targetQuantity)} ${h.unit.orEmpty()}".trim())
                }
            }
            val focusing = com.earnd.app.focus.focusLabel(h.id)
            if (focusing != null) Num(focusing, color = p.amber)
            else Row {
                if (parts.isNotEmpty()) Num(parts.joinToString("  "), color = if (complete) p.done else p.secondary)
                if (!complete && ignored > 0) Num((if (parts.isNotEmpty()) " · " else "") + stringResource(R.string.home_ignored, ignored), color = p.red)
            }
        }
        val rewardMin = HabitRules.rewardMinutes(h, settings)
        if (rewardMin > 0) Num("+$rewardMin", Modifier.padding(horizontal = 8.dp), color = if (complete) p.done else p.amber)
        when {
            complete && !com.earnd.app.domain.HabitProgress.isQuantity(h) ->
                IconButton(onClick = onCheck) { Icon(Icons.Outlined.Add, stringResource(R.string.home_check_in_again), tint = p.secondary) }
            complete -> Unit
            com.earnd.app.domain.TimedHabit.isTimed(h) -> TextButton(onClick = onCheck) { Text(com.earnd.app.focus.startLabel(h, settings), color = p.secondary) }
            HabitRules.needsPhoto(h, settings) -> Icon(Icons.Outlined.CameraAlt, stringResource(R.string.home_photo_needed), tint = p.muted, modifier = Modifier.size(16.dp))
            h.targetQuantity != null -> Icon(Icons.Outlined.Add, stringResource(R.string.home_add_amount), tint = p.muted, modifier = Modifier.size(16.dp))
            else -> Unit
        }
    }
}

private fun fmtQty(q: Double): String = HabitRules.formatAmount(q)

/** Header add button: a quiet 32dp square that matches the off-day chip, so it never competes with the one red action. */
@Composable
private fun AddButton(onClick: () -> Unit, description: String) {
    val p = Earnd.palette
    Surface(
        onClick,
        Modifier.size(32.dp),
        shape = RectangleShape,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, p.edge),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, description, tint = p.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Header switch: today's alarms vibrate instead of playing a sound. */
@Composable
private fun OffDayToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    val p = Earnd.palette
    // 48dp touch target around the 32dp chip.
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .background(if (on) p.text else Color.Transparent, RectangleShape)
                .border(1.dp, if (on) p.text else p.edge, RectangleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Vibration, null, tint = if (on) p.bg else p.muted, modifier = Modifier.size(14.dp))
            Text(
                stringResource(R.string.home_off_day),
                color = if (on) p.bg else p.muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
