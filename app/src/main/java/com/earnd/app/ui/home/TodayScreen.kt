package com.earnd.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

private val SEGMENT_ORDER = listOf(DaySegment.MORNING, DaySegment.AFTERNOON, DaySegment.EVENING, DaySegment.NONE)

@Composable
fun TodayScreen(onOpenHabit: (Long) -> Unit, onAddHabit: () -> Unit, onPhotoCheckIn: (Long) -> Unit, onManageHabits: () -> Unit = {}) {
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
    val bank = BankState(balance?.minutes ?: 0, session, settings.hardMode, allDone, settings.allDoneUnlock)
    val inBedtime = TimeBank.bedtimeWindow(settings)?.contains(System.currentTimeMillis(), sl.clock.zone()) == true

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddHabit, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Habit") })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onManageHabits) { Icon(Icons.Outlined.SwapVert, "Reorder and archived habits") }
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
                    }) { Text("It's bedtime. Need ${settings.bedtimeGraceMin} minutes to wrap up?") }
                }
            }
            if (habits.isEmpty()) {
                item {
                    Text(
                        "Nothing scheduled today, so nothing is locked. Add a habit when you're ready.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            if (habits.isNotEmpty()) {
                item(key = "progress") {
                    val done = habits.count(doneOf)
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("$done of ${habits.size} habits done", style = MaterialTheme.typography.labelLarge)
                        LinearProgressIndicator(
                            progress = { done.toFloat() / habits.size },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
            val bySegment = habits.groupBy { it.habit.segment }
            SEGMENT_ORDER.forEach { seg ->
                val list = bySegment[seg].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "seg-$seg") {
                        val done = list.count(doneOf)
                        val label = HabitLook.segmentLabel(seg) + if (settings.segmentMode) " · $done of ${list.size}" else ""
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(label, Modifier.weight(1f))
                            if (seg == currentSeg) {
                                Text(
                                    if (done == list.size) "Done, apps open until the next part" else "Now",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                    items(list, key = { it.habit.id }) { hd ->
                        val last = checkIns.filter { it.habitId == hd.habit.id }.maxByOrNull { it.at }
                        HabitRow(
                            hd, last, doneOf(hd), byHabit[hd.habit.id].orEmpty(), settings,
                            onOpen = { onOpenHabit(hd.habit.id) },
                            onCheck = {
                                when {
                                    HabitRules.needsPhoto(hd.habit, settings) -> onPhotoCheckIn(hd.habit.id)
                                    hd.habit.targetQuantity != null -> askAmount = hd.habit
                                    else -> launchUi { sl.habitService.checkIn(hd.habit.id) }
                                }
                            },
                            onUndo = { c -> launchUi { HabitActions.undo(c.id) } },
                        )
                    }
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
private fun HabitRow(hd: HabitDay, last: CheckInEntity?, complete: Boolean, todays: List<CheckInEntity>, settings: Settings, onOpen: () -> Unit, onCheck: () -> Unit, onUndo: (CheckInEntity) -> Unit) {
    val h = hd.habit
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(Color(h.colorArgb), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(HabitLook.icon(h.iconKey), null, tint = Color.White) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                h.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (complete) TextDecoration.LineThrough else null,
            )
            val parts = buildList {
                if (hd.required > 1) add("${hd.done} of ${hd.required}")
                val reward = HabitRules.rewardMinutes(h, settings)
                if (reward > 0) add("+$reward min")
                if (h.targetQuantity != null) {
                    add("${fmtQty(HabitRules.quantityDone(todays))} of ${fmtQty(h.targetQuantity)} ${h.unit.orEmpty()}".trim())
                }
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (last != null) IconButton(onClick = { onUndo(last) }) { Icon(Icons.Outlined.Undo, "Undo last check-in") }
        if (!complete) {
            FilledIconButton(onClick = onCheck) {
                Icon(
                    when {
                        HabitRules.needsPhoto(h, settings) -> Icons.Outlined.CameraAlt
                        h.targetQuantity != null -> Icons.Outlined.Add
                        else -> Icons.Outlined.Check
                    },
                    "Check in",
                )
            }
        } else {
            Icon(Icons.Outlined.Check, "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
        }
    }
}

private fun fmtQty(q: Double): String = HabitRules.formatAmount(q)
