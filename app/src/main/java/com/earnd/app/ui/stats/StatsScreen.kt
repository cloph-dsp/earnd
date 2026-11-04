package com.earnd.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.domain.localDate
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.formatMinutes
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS = 7

/** One week at a glance: check-ins, finished tasks and minutes earned per day. No scores. */
@Composable
fun StatsScreen() {
    val sl = ServiceLocator
    val today = remember { sl.clock.today() }
    val days = remember { (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) } }
    val ledger by remember { sl.db.ledgerDao().observeRecent(1000) }.collectAsState(initial = emptyList())
    val completed by remember { sl.db.taskDao().observeCompleted(500) }.collectAsState(initial = emptyList())
    val checkIns by produceState(emptyMap<Long, Int>(), ledger.size) {
        value = days.associate { d -> d.toEpochDay() to sl.db.checkInDao().forDay(d.toEpochDay()).size }
    }
    val earnedByDay = days.associate { d ->
        d.toEpochDay() to ledger.filter {
            (it.kind == LedgerKind.EARN_HABIT || it.kind == LedgerKind.EARN_TASK || it.kind == LedgerKind.COMEBACK || it.kind == LedgerKind.UNDO) &&
                sl.clock.localDate(it.at) == d
        }.sumOf { it.minutes }.coerceAtLeast(0)
    }
    val spentByDay = days.associate { d ->
        d.toEpochDay() to -ledger.filter {
            (it.kind == LedgerKind.SPEND || it.kind == LedgerKind.ADJUST) && sl.clock.localDate(it.at) == d
        }.sumOf { it.minutes }.coerceAtMost(0)
    }
    val tasksByDay = days.associate { d ->
        d.toEpochDay() to completed.count { t -> t.completedAt?.let { sl.clock.localDate(it) } == d }
    }

    val perfectDays by produceState(0, ledger.size) {
        value = runCatching { sl.habitService.perfectDays(today.withDayOfMonth(1), today).size }.getOrDefault(0)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("This week", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "${checkIns.values.sum()} check-ins, ${tasksByDay.values.sum()} tasks finished, ${formatMinutes(earnedByDay.values.sum())} earned.",
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Perfect days this month: $perfectDays.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
        )
        SectionTitle("Check-ins")
        Bars(days.map { it.toEpochDay() }, checkIns, com.earnd.app.ui.theme.Earnd.palette.amber)
        SectionTitle("Tasks finished")
        Bars(days.map { it.toEpochDay() }, tasksByDay, com.earnd.app.ui.theme.Earnd.palette.secondary)
        SectionTitle("Minutes earned")
        Bars(days.map { it.toEpochDay() }, earnedByDay, com.earnd.app.ui.theme.Earnd.palette.amber)
        SectionTitle("Minutes spent")
        Text(
            "Earned ${formatMinutes(earnedByDay.values.sum())}, spent ${formatMinutes(spentByDay.values.sum())} this week.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Bars(days.map { it.toEpochDay() }, spentByDay, com.earnd.app.ui.theme.Earnd.palette.secondary)
        ScreenTimeSection()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bars(days: List<Long>, values: Map<Long, Int>, color: Color) {
    val max = (values.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            val v = values[d] ?: 0
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                com.earnd.app.ui.theme.Num("$v")
                Box(
                    Modifier.fillMaxWidth().height((96f * v / max).dp.coerceAtLeast(2.dp))
                        .background(if (v == 0) MaterialTheme.colorScheme.surfaceVariant else color, RoundedCornerShape(1.dp)),
                )
                Text(
                    java.time.LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
