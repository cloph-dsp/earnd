package com.earnd.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.today
import com.earnd.app.ui.common.EditScaffold
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.formatDay
import com.earnd.app.ui.common.formatMinuteOfDay
import com.earnd.app.ui.common.launchUi
import com.earnd.app.domain.minuteOfDay
import com.earnd.app.domain.localDate
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val WEEKS = 12

@Composable
fun HabitDetailScreen(habitId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val sl = ServiceLocator
    val today = remember { sl.clock.today() }
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var history by remember { mutableStateOf<List<CheckInEntity>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var perfect by remember { mutableIntStateOf(0) }
    var year by remember { mutableStateOf<List<CheckInEntity>>(emptyList()) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var reload by remember { mutableIntStateOf(0) }
    // Reload whenever today's check-ins change (undo here, or check-in elsewhere).
    val todays by remember { sl.db.checkInDao().observeForDay(today.toEpochDay()) }.collectAsState(initial = emptyList())
    LaunchedEffect(habitId, reload, todays.size) {
        habit = sl.db.habitDao().get(habitId)
        val start = today.minusWeeks(WEEKS.toLong()).minusDays(6)
        history = sl.habitService.history(habitId, start, today)
        streak = sl.habitService.streak(habitId, today)
        val yearStart = today.minusDays(365)
        year = sl.habitService.history(habitId, yearStart, today)
        habit?.let { hh ->
            val s = sl.settings.current()
            val byDay = year.groupBy { it.epochDay }
            val created = sl.clock.localDate(hh.createdAt)
            val days = generateSequence(maxOf(yearStart, created)) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
            val complete = { d: LocalDate -> HabitRules.dayComplete(hh, byDay[d.toEpochDay()].orEmpty()) }
            val scheduled = { d: LocalDate -> sl.habitService.isScheduled(hh, d) }
            best = maxOf(streak, sl.habitService.bestStreak(hh.id, today))
            perfect = HabitCalendar.perfectDays(days.filterNot { s.onVacation(it.toEpochDay()) }, scheduled, complete)
        }
    }
    val h = habit
    EditScaffold(h?.name ?: "", onBack = onBack, actions = {
        if (h != null) {
            if (h.archived) {
                TextButton(onClick = { launchUi { sl.habitService.unarchive(h.id); reload++ } }) { Text("Restore") }
            } else {
                IconButton(onClick = { launchUi { sl.habitService.archive(h.id); onBack() } }) { Icon(Icons.Outlined.Archive, "Archive") }
            }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
    }) {
        if (h == null) return@EditScaffold
        Text(
            when (streak) { 0 -> "A fresh start today."; 1 -> "Done 1 day in a row."; else -> "Done $streak days in a row." },
            style = MaterialTheme.typography.titleMedium,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Current streak", streak, Modifier.weight(1f))
            Stat("Best streak", best, Modifier.weight(1f))
            Stat("Days done", perfect, Modifier.weight(1f))
        }
        if (h.archived) Text("Archived. Restore it to see it on Today again.", style = MaterialTheme.typography.bodySmall)
        if (!h.archived && com.earnd.app.domain.TimedHabit.isTimed(h)) {
            val st by androidx.compose.runtime.remember { sl.settings.settings }.collectAsState(initial = com.earnd.app.data.Settings())
            val context = androidx.compose.ui.platform.LocalContext.current
            val focusing = com.earnd.app.focus.focusLabel(h.id)
            com.earnd.app.ui.theme.AmberButton(
                onClick = { launchUi { com.earnd.app.focus.startFocus(context, h.id) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(focusing ?: com.earnd.app.focus.startLabel(h, st)) }
        }

        val byDay = year.groupBy { it.epochDay }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            IconButton(enabled = month < YearMonth.from(today), onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month")
            }
        }
        MonthCalendar(month, today, h, byDay, Color(h.colorArgb))
        if (month < YearMonth.from(today.minusDays(365))) {
            Text("Only the last year is shown here.", style = MaterialTheme.typography.bodySmall)
        }

        SectionTitle("Last $WEEKS weeks")
        val counts = history.groupingBy { it.epochDay }.eachCount()
        HistoryGrid(today, counts, h.timesPerDay, Color(h.colorArgb))

        SectionTitle("Recent check-ins")
        if (history.isEmpty()) Text("No check-ins yet.", style = MaterialTheme.typography.bodyMedium)
        history.sortedByDescending { it.at }.take(30).forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDay(c.epochDay) + " · " + formatMinuteOfDay(sl.clock.minuteOfDay(c.at)) +
                        (if (c.minutesEarned > 0) " · +${c.minutesEarned} min" else "") +
                        (c.quantity?.let { " · $it ${h.unit.orEmpty()}" } ?: ""),
                    modifier = Modifier.weight(1f),
                )
                if (c.photoPath != null) Icon(Icons.Outlined.PhotoCamera, "Photo", modifier = Modifier.padding(end = 4.dp))
                TextButton(onClick = { launchUi { HabitActions.undo(c.id); reload++ } }) { Text("Undo") }
            }
        }
    }
}

/** Columns are weeks (oldest left), rows Monday..Sunday; shade by how much of the day's target was done. */
@Composable
private fun HistoryGrid(today: java.time.LocalDate, counts: Map<Long, Int>, perDay: Int, color: Color) {
    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (w in WEEKS - 1 downTo 0) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0 until 7) {
                    val day = lastMonday.minusWeeks(w.toLong()).plusDays(d.toLong())
                    val n = counts[day.toEpochDay()] ?: 0
                    val fill = when {
                        day.isAfter(today) -> Color.Transparent
                        n == 0 -> MaterialTheme.colorScheme.surfaceVariant
                        else -> color.copy(alpha = (0.35f + 0.65f * (n.toFloat() / perDay).coerceAtMost(1f)))
                    }
                    Box(Modifier.size(16.dp).background(fill, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Month grid, Monday first: filled when complete, tinted when partly done, faint when not scheduled. */
@Composable
private fun MonthCalendar(month: YearMonth, today: LocalDate, h: HabitEntity, byDay: Map<Long, List<CheckInEntity>>, color: Color) {
    val sl = ServiceLocator
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
    HabitCalendar.monthCells(month).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            week.forEach { d ->
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        val list = byDay[d.toEpochDay()].orEmpty()
                        val done = HabitRules.dayComplete(h, list)
                        val faint = d.isAfter(today) || !sl.habitService.isScheduled(h, d)
                        val bg = when {
                            done -> color
                            list.isNotEmpty() -> color.copy(alpha = 0.35f)
                            else -> Color.Transparent
                        }
                        Box(
                            Modifier.fillMaxSize().background(bg, CircleShape)
                                .then(if (d == today) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    done -> Color.White
                                    faint -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
