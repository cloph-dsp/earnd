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

private const val WEEKS = 12

@Composable
fun HabitDetailScreen(habitId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val sl = ServiceLocator
    val today = remember { sl.clock.today() }
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var history by remember { mutableStateOf<List<CheckInEntity>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    // Reload whenever today's check-ins change (undo here, or check-in elsewhere).
    val todays by remember { sl.db.checkInDao().observeForDay(today.toEpochDay()) }.collectAsState(initial = emptyList())
    LaunchedEffect(habitId, reload, todays.size) {
        habit = sl.db.habitDao().get(habitId)
        val start = today.minusWeeks(WEEKS.toLong()).minusDays(6)
        history = sl.habitService.history(habitId, start, today)
        streak = sl.habitService.streak(habitId, today)
    }
    val h = habit
    EditScaffold(h?.name ?: "", onBack = onBack, actions = {
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
    }) {
        if (h == null) return@EditScaffold
        Text(
            when (streak) { 0 -> "A fresh start today."; 1 -> "Done 1 day in a row."; else -> "Done $streak days in a row." },
            style = MaterialTheme.typography.titleMedium,
        )
        SectionTitle("Last $WEEKS weeks")
        val counts = history.groupingBy { it.epochDay }.eachCount()
        HistoryGrid(today, counts, h.timesPerDay, Color(h.colorArgb))

        SectionTitle("Recent check-ins")
        if (history.isEmpty()) Text("No check-ins yet.", style = MaterialTheme.typography.bodyMedium)
        history.sortedByDescending { it.at }.take(30).forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                    Box(Modifier.size(16.dp).background(fill, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
