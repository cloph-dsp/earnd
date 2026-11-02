package com.earnd.app.ui.home

import com.earnd.app.data.entity.ListDensity
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.earnd.app.ui.habits.HabitRules
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.minuteOfDay
import com.earnd.app.domain.Segments
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

private val SEGMENT_ORDER = listOf(DaySegment.MORNING, DaySegment.AFTERNOON, DaySegment.EVENING, DaySegment.NONE)

@Composable
fun TodayScreen(onOpenHabit: (Long) -> Unit, onAddHabit: () -> Unit, onPhotoCheckIn: (Long) -> Unit, onOpenTask: (Long) -> Unit = {}) {
    val sl = ServiceLocator
    val todayDay = remember { sl.clock.today().toEpochDay() }
    val habits by remember { sl.habitService.observeToday() }.collectAsState(initial = emptyList())
    val checkIns by remember { sl.db.checkInDao().observeForDay(todayDay) }.collectAsState(initial = emptyList())
    val balance by remember { sl.timeBank.observeBalance() }.collectAsState(initial = null)
    val session by remember { sl.timeBank.observeActiveSession() }.collectAsState(initial = null)
    val settings by remember { sl.settings.settings }.collectAsState(initial = Settings())
    val snackbar = remember { SnackbarHostState() }
    var askAmount by remember { mutableStateOf<HabitEntity?>(null) }
    val byHabit = checkIns.groupBy { it.habitId }
    val doneOf = { hd: HabitDay -> hd.complete }
    val minuteNow = sl.clock.minuteOfDay(sl.clock.now())
    val currentSeg = if (settings.segmentMode) Segments.current(minuteNow, settings) else null

    val allDone = habits.isNotEmpty() && habits.all(doneOf)
    val bank = BankState(balance?.minutes ?: 0, session, settings.hardMode, allDone = allDone, allDoneUnlock = settings.allDoneUnlock, unlockCapMin = settings.unlockCapMin,
        nextExpiryAt = balance?.nextExpiryAt, minutesExpiringNext = balance?.minutesExpiringNext ?: 0)
    val inBedtime = TimeBank.bedtimeWindow(settings)?.contains(System.currentTimeMillis(), sl.clock.zone()) == true

    val nags by remember { sl.db.nagStateDao().observeAll() }.collectAsState(initial = emptyList())
    val ignored = nags.filter { it.ownerKind == OwnerKind.HABIT }.associate { it.ownerId to it.nagCount }
    val overdue by remember { sl.taskService.observeBucket(TaskBucket.OVERDUE) }.collectAsState(initial = emptyList())
    val dueToday by remember { sl.taskService.observeBucket(TaskBucket.TODAY) }.collectAsState(initial = emptyList())
    val tasksToday = overdue + dueToday
    var clock by remember { mutableStateOf(formatMinuteOfDay(minuteNow)) }
    LaunchedEffect(Unit) { while (true) { clock = formatMinuteOfDay(sl.clock.minuteOfDay(sl.clock.now())); delay(15_000) } }

    val context = androidx.compose.ui.platform.LocalContext.current
    val habitRow: @Composable (HabitDay) -> Unit = { hd ->
        val last = checkIns.filter { it.habitId == hd.habit.id }.maxByOrNull { it.at }
        HabitRow(
            hd, last, doneOf(hd), byHabit[hd.habit.id].orEmpty(), settings, ignored[hd.habit.id] ?: 0,
            onOpen = { onOpenHabit(hd.habit.id) },
            onCheck = {
                when {
                    com.earnd.app.domain.TimedHabit.isTimed(hd.habit) -> launchUi { com.earnd.app.focus.startFocus(context, hd.habit.id) }
                    HabitRules.needsPhoto(hd.habit, settings) -> onPhotoCheckIn(hd.habit.id)
                    hd.habit.targetQuantity != null -> askAmount = hd.habit
                    else -> launchUi {
                        val r = sl.habitService.checkInWithBonus(hd.habit.id)
                        com.earnd.app.focus.bonusText(r)?.let { snackbar.showSnackbar(it) }
                    }
                }
            },
            onUndo = { c -> launchUi { HabitActions.undo(c.id) } },
        )
        RowDivider()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddHabit, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Habit") },
                containerColor = Earnd.palette.raised, contentColor = MaterialTheme.colorScheme.onBackground,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Wordmark()
                    Spacer(Modifier.width(10.dp))
                    Num(clock, Modifier.weight(1f))
                }
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
                    }) { Text("Bedtime. Take ${settings.bedtimeGraceMin} minutes to wrap up") }
                }
            }
            if (habits.isNotEmpty()) {
                item(key = "progress") {
                    val done = habits.count(doneOf)
                    Column(Modifier.padding(top = 10.dp)) {
                        ProgressBar(done.toFloat() / habits.size)
                        Num("$done/${habits.size} habits", Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                item {
                    Text(
                        "Nothing scheduled today, so nothing is locked. Add a habit when you're ready.",
                        style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
            val byIgnored = compareBy<HabitDay> { doneOf(it) }.thenByDescending { ignored[it.habit.id] ?: 0 }
            if (settings.segmentMode) {
                val bySegment = habits.groupBy { it.habit.segment }
                SEGMENT_ORDER.forEach { seg ->
                    val list = bySegment[seg].orEmpty().sortedWith(byIgnored)
                    if (list.isNotEmpty()) {
                        item(key = "seg-$seg") {
                            val done = list.count(doneOf)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle(HabitLook.segmentLabel(seg), Modifier.weight(1f))
                                if (seg == currentSeg) {
                                    Text(
                                        if (done == list.size) "Done, open until the next part" else "Now",
                                        style = MaterialTheme.typography.labelMedium, color = Earnd.palette.amber,
                                        modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                                    )
                                }
                                Num("$done/${list.size}", Modifier.padding(top = 16.dp))
                            }
                        }
                        items(list, key = { it.habit.id }) { habitRow(it) }
                    }
                }
            } else if (habits.isNotEmpty()) {
                item(key = "do-now") { SectionTitle("Do now") }
                items(habits.sortedWith(byIgnored), key = { it.habit.id }) { habitRow(it) }
            }
            if (tasksToday.isNotEmpty()) {
                item(key = "tasks-today") { SectionTitle("Tasks today") }
                items(tasksToday, key = { "t${it.id}" }) { t -> TaskRow(t, onOpenTask); RowDivider() }
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

/** Asks how much was done; the amount is added to today's total. */
@Composable
private fun AmountDialog(h: HabitEntity, onDismiss: () -> Unit, onAdd: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = HabitRules.parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(h.name) },
        text = {
            OutlinedTextField(
                text, { text = it.take(10) },
                label = { Text("How much? (${h.unit ?: "amount"})") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { amount?.let(onAdd) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun refuseText(reason: String): String = when (reason) {
    com.earnd.app.domain.RefuseReason.HARD_MODE -> "Hard mode is on, so minutes can't be spent by hand."
    com.earnd.app.domain.RefuseReason.NOT_ENOUGH -> "Not quite enough minutes saved yet."
    com.earnd.app.domain.RefuseReason.GRACE_USED -> "Tonight's grace has already been used."
    com.earnd.app.domain.RefuseReason.NOT_BEDTIME -> "It isn't bedtime right now."
    else -> "That didn't work. Try a different amount."
}

@Composable
private fun HabitRow(hd: HabitDay, last: CheckInEntity?, complete: Boolean, todays: List<CheckInEntity>, settings: Settings, ignored: Int, onOpen: () -> Unit, onCheck: () -> Unit, onUndo: (CheckInEntity) -> Unit) {
    val h = hd.habit
    val p = Earnd.palette
    val compact = settings.listDensity == ListDensity.COMPACT
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 60.dp).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = complete,
            onCheckedChange = { if (!complete) onCheck() else last?.let(onUndo) },
            reward = true,
            modifier = Modifier.offset(x = (-17).dp),
        )
        Column(Modifier.weight(1f).offset(x = (-17).dp).padding(vertical = if (compact) 2.dp else 10.dp)) {
            Text(
                h.name,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (complete) p.done else p.text,
                textDecoration = if (complete) TextDecoration.LineThrough else null,
            )
            val parts = buildList {
                if (hd.required > 1 || hd.done > hd.required) add("${hd.done}/${hd.required}" + if (complete) " ✓" else "")
                val reward = HabitRules.rewardMinutes(h, settings)
                if (reward > 0) add("+$reward min")
                if (!complete && hd.comebackMissed > 0) add(com.earnd.app.domain.Comeback.label(hd.comebackMissed) + " comeback")
                if (h.targetQuantity != null) {
                    add("${fmtQty(HabitRules.quantityDone(todays))}/${fmtQty(h.targetQuantity)} ${h.unit.orEmpty()}".trim())
                }
            }
            val focusing = com.earnd.app.focus.focusLabel(h.id)
            if (focusing != null) Num(focusing, color = p.amber)
            else Row {
                if (parts.isNotEmpty()) Num(parts.joinToString("  "), color = if (complete) p.done else p.secondary)
                if (!complete && ignored > 0) Num((if (parts.isNotEmpty()) "  " else "") + "ignored ${ignored}×", color = p.red)
            }
        }
        if (!complete && last != null) {
            IconButton(onClick = { onUndo(last) }) { Icon(Icons.Outlined.Undo, "Undo last check-in", tint = p.muted) }
        }
        when {
            complete && !com.earnd.app.domain.HabitProgress.isQuantity(h) ->
                IconButton(onClick = onCheck) { Icon(Icons.Outlined.Add, "Check in again", tint = p.amber) }
            complete -> Unit
            com.earnd.app.domain.TimedHabit.isTimed(h) -> TextButton(onClick = onCheck) { Text(com.earnd.app.focus.startLabel(h, settings), color = p.amber) }
            HabitRules.needsPhoto(h, settings) -> Icon(Icons.Outlined.CameraAlt, "Photo needed", tint = p.muted, modifier = Modifier.size(16.dp))
            h.targetQuantity != null -> Icon(Icons.Outlined.Add, "Add amount", tint = p.muted, modifier = Modifier.size(16.dp))
            else -> Icon(HabitLook.icon(h.iconKey), null, tint = p.muted, modifier = Modifier.size(16.dp))
        }
    }
}

private fun fmtQty(q: Double): String = HabitRules.formatAmount(q)
