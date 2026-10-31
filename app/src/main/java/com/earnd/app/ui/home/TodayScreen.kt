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
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.domain.HabitDay
import com.earnd.app.domain.SpendResult
import com.earnd.app.domain.TimeBank
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.habits.HabitLook
import com.earnd.app.ui.habits.HabitActions

private val SEGMENT_ORDER = listOf(DaySegment.MORNING, DaySegment.AFTERNOON, DaySegment.EVENING, DaySegment.NONE)

@Composable
fun TodayScreen(onOpenHabit: (Long) -> Unit, onAddHabit: () -> Unit, onPhotoCheckIn: (Long) -> Unit) {
    val sl = ServiceLocator
    val todayDay = remember { sl.clock.today().toEpochDay() }
    val habits by remember { sl.habitService.observeToday() }.collectAsState(initial = emptyList())
    val checkIns by remember { sl.db.checkInDao().observeForDay(todayDay) }.collectAsState(initial = emptyList())
    val balance by remember { sl.timeBank.observeBalance() }.collectAsState(initial = null)
    val session by remember { sl.timeBank.observeActiveSession() }.collectAsState(initial = null)
    val settings by remember { sl.settings.settings }.collectAsState(initial = Settings())
    val snackbar = remember { SnackbarHostState() }

    val allDone = habits.isNotEmpty() && habits.all { it.complete }
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
                Text("Today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
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
            val bySegment = habits.groupBy { it.habit.segment }
            SEGMENT_ORDER.forEach { seg ->
                val list = bySegment[seg].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "seg-$seg") { SectionTitle(HabitLook.segmentLabel(seg)) }
                    items(list, key = { it.habit.id }) { hd ->
                        val last = checkIns.filter { it.habitId == hd.habit.id }.maxByOrNull { it.at }
                        HabitRow(
                            hd, last,
                            onOpen = { onOpenHabit(hd.habit.id) },
                            onCheck = {
                                if (hd.habit.checkInMode == CheckInMode.PHOTO) onPhotoCheckIn(hd.habit.id)
                                else launchUi { sl.habitService.checkIn(hd.habit.id) }
                            },
                            onUndo = { c -> launchUi { HabitActions.undo(c.id) } },
                        )
                    }
                }
            }
            item { Box(Modifier.size(88.dp)) }
        }
    }
}

fun refuseText(reason: String): String = when (reason) {
    com.earnd.app.domain.RefuseReason.HARD_MODE -> "Hard mode is on, so minutes can't be spent by hand."
    com.earnd.app.domain.RefuseReason.NOT_ENOUGH -> "Not quite enough minutes saved yet."
    com.earnd.app.domain.RefuseReason.GRACE_USED -> "Tonight's grace has already been used."
    com.earnd.app.domain.RefuseReason.NOT_BEDTIME -> "It isn't bedtime right now."
    else -> "That didn't work. Try a different amount."
}

@Composable
private fun HabitRow(hd: HabitDay, last: CheckInEntity?, onOpen: () -> Unit, onCheck: () -> Unit, onUndo: (CheckInEntity) -> Unit) {
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
                textDecoration = if (hd.complete) TextDecoration.LineThrough else null,
            )
            val parts = buildList {
                if (hd.required > 1) add("${hd.done} of ${hd.required}")
                if (h.minutesPerCheckIn > 0) add("+${h.minutesPerCheckIn} min")
                if (h.targetQuantity != null) add("${fmtQty(h.targetQuantity)} ${h.unit.orEmpty()}".trim())
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (last != null) IconButton(onClick = { onUndo(last) }) { Icon(Icons.Outlined.Undo, "Undo last check-in") }
        if (!hd.complete) {
            FilledIconButton(onClick = onCheck) {
                Icon(if (h.checkInMode == CheckInMode.PHOTO) Icons.Outlined.CameraAlt else Icons.Outlined.Check, "Check in")
            }
        } else {
            Icon(Icons.Outlined.Check, "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
        }
    }
}

private fun fmtQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
