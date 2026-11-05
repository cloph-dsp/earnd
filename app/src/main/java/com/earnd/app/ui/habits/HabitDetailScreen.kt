package com.earnd.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.NumberStyle
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
import androidx.compose.ui.res.stringResource
import com.earnd.app.R
import com.earnd.app.i18n.Str
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.outlined.Undo
import com.earnd.app.ui.theme.ListRow
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.RowDivider
import java.time.DayOfWeek
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
    var confirmArchive by remember { mutableStateOf(false) }
    var hardBlocked by remember { mutableStateOf(false) }
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
                TextButton(onClick = { launchUi { sl.habitService.unarchive(h.id); reload++ } }) { Text(stringResource(R.string.habits_restore)) }
            } else {
                IconButton(onClick = { launchUi { if (sl.habitService.hardModeBlocks(h)) hardBlocked = true else confirmArchive = true } }) { Icon(Icons.Outlined.Archive, stringResource(R.string.habits_archive)) }
            }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.habits_edit)) }
    }) {
        if (h == null) return@EditScaffold
        if (hardBlocked) AlertDialog(
            onDismissRequest = { hardBlocked = false },
            text = { Text(stringResource(R.string.habits_hard_mode_blocked)) },
            confirmButton = { TextButton(onClick = { hardBlocked = false }) { Text(stringResource(R.string.habits_hard_mode_ok)) } },
        )
        if (confirmArchive) AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.habits_archive_title, h.name)) },
            text = { Text(stringResource(R.string.habits_archive_body)) },
            confirmButton = { TextButton(onClick = { confirmArchive = false; launchUi { sl.habitService.archive(h.id); onBack() } }) { Text(stringResource(R.string.habits_archive)) } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.habits_cancel)) } },
        )
        Text(
            if (streak == 0) stringResource(R.string.habits_streak_zero) else Str.plural(R.plurals.habits_streak_days, streak),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.habits_stat_current), streak, Modifier.weight(1f))
            Stat(stringResource(R.string.habits_stat_best), best, Modifier.weight(1f))
            Stat(stringResource(R.string.habits_stat_days_done), perfect, Modifier.weight(1f))
        }
        if (h.archived) Text(stringResource(R.string.habits_archived_note), style = MaterialTheme.typography.bodySmall)
        if (!h.archived && com.earnd.app.domain.TimedHabit.isTimed(h)) {
            val st by androidx.compose.runtime.remember { sl.settings.settings }.collectAsState(initial = com.earnd.app.data.Settings())
            val context = androidx.compose.ui.platform.LocalContext.current
            val focusing = com.earnd.app.focus.focusLabel(h.id)
            com.earnd.app.ui.theme.Button(
                onClick = { launchUi { com.earnd.app.focus.startFocus(context, h.id) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
            ) { Text(focusing ?: com.earnd.app.focus.startLabel(h, st)) }
        }

        val byDay = year.groupBy { it.epochDay }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.habits_prev_month)) }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + month.year,
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            IconButton(enabled = month < YearMonth.from(today), onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.habits_next_month))
            }
        }
        MonthCalendar(month, today, h, byDay, Color(h.colorArgb))
        if (month < YearMonth.from(today.minusDays(365))) {
            Text(stringResource(R.string.habits_last_year_only), style = MaterialTheme.typography.bodySmall)
        }

        SectionTitle(stringResource(R.string.habits_recent_checkins))
        if (history.isEmpty()) Text(stringResource(R.string.habits_no_checkins), style = MaterialTheme.typography.bodyMedium)
        history.sortedByDescending { it.at }.take(30).forEach { c ->
            val p = Earnd.palette
            ListRow(onClick = {}, minHeight = 60.dp) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(formatDay(c.epochDay), style = MaterialTheme.typography.bodyMedium)
                    Num(
                        formatMinuteOfDay(sl.clock.minuteOfDay(c.at)) + (c.quantity?.let { " · $it ${h.unit.orEmpty()}" } ?: ""),
                        color = p.secondary,
                    )
                }
                if (c.minutesEarned > 0) Num("+${c.minutesEarned}", Modifier.padding(start = 10.dp), color = p.amber, style = NumberStyle.copy(fontSize = 14.sp))
                if (c.photoPath != null) Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.habits_photo), Modifier.padding(start = 8.dp).size(16.dp), tint = p.muted)
                IconButton(onClick = { launchUi { HabitActions.undo(c.id); reload++ } }) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.habits_undo_checkin), tint = p.secondary)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = if (value > 0) Earnd.palette.amber else Earnd.palette.secondary, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 36.sp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Earnd.palette.secondary)
    }
}

/** Month grid, Monday first: filled when complete, tinted when partly done, faint when not scheduled. */
@Composable
private fun MonthCalendar(month: YearMonth, today: LocalDate, h: HabitEntity, byDay: Map<Long, List<CheckInEntity>>, color: Color) {
    val sl = ServiceLocator
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach {
            Text(
                it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = Earnd.palette.secondary, textAlign = TextAlign.Center,
            )
        }
    }
    HabitCalendar.monthCells(month).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            week.forEach { d ->
                Box(Modifier.weight(1f).height(44.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        val list = byDay[d.toEpochDay()].orEmpty()
                        val done = HabitRules.dayComplete(h, list)
                        val faint = d.isAfter(today) || !sl.habitService.isScheduled(h, d)
                        val p = Earnd.palette
                        // Habit color only for earned days: filled when done, outlined when partly done.
                        // Today is a neutral outline, so color keeps meaning "earned".
                        val bg = if (done) color else Color.Transparent
                        val outline = when {
                            done -> null
                            list.isNotEmpty() -> color
                            d == today -> p.text
                            else -> null
                        }
                        Box(
                            Modifier.fillMaxSize().background(bg, RectangleShape)
                                .then(if (outline != null) Modifier.border(1.5.dp, outline, RectangleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Num(
                                "${d.dayOfMonth}",
                                style = NumberStyle.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                color = when {
                                    done -> if (color.luminance() > 0.5f) Color.Black else Color.White
                                    list.isNotEmpty() -> color
                                    faint -> p.done
                                    else -> p.text
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
