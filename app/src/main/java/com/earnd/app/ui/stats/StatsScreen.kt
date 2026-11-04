package com.earnd.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.domain.localDate
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.NumberStyle
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS = 7

/** One week at a glance: earned against spent, then check-ins and tasks per day. No scores. */
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
    val p = Earnd.palette
    val earned = earnedByDay.values.sum()
    val spent = spentByDay.values.sum()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("This week", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure(formatMinutes(earned), "earned", p.amber, Modifier.weight(1f))
            Figure(formatMinutes(spent), "spent", p.text, Modifier.weight(1f))
            Figure("${checkIns.values.sum()}", "check-ins", p.text, Modifier.weight(0.8f))
            Figure("${tasksByDay.values.sum()}", "tasks", p.text, Modifier.weight(0.6f))
        }

        SectionTitle("Earned and spent")
        Legend()
        PairedBars(days, earnedByDay, spentByDay, today)

        SectionTitle("Done per day")
        DayGrid(days, today, listOf("Check-ins" to checkIns, "Tasks" to tasksByDay))

        HorizontalDivider(Modifier.padding(top = 20.dp), color = p.divider)
        ScreenTimeSection()
        Spacer(Modifier.height(24.dp))
    }
}

/** Three-letter weekday so long names like "domingo" never wrap. */
private fun dayLabel(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.').take(3)
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun Figure(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = NumberStyle.copy(fontSize = 20.sp, lineHeight = 24.sp), color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Earnd.palette.secondary, maxLines = 1)
    }
}

@Composable
private fun Legend() {
    val p = Earnd.palette
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).background(p.amber))
        Text(" earned", style = MaterialTheme.typography.labelSmall, color = p.secondary)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(10.dp).height(10.dp).background(p.muted))
        Text(" spent", style = MaterialTheme.typography.labelSmall, color = p.secondary)
    }
}

@Composable
private fun PairedBars(days: List<LocalDate>, earned: Map<Long, Int>, spent: Map<Long, Int>, today: LocalDate) {
    val p = Earnd.palette
    val max = (earned.values + spent.values).maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            val e = earned[d.toEpochDay()] ?: 0
            val s = spent[d.toEpochDay()] ?: 0
            val isToday = d == today
            Column(
                Modifier.weight(1f).semantics { contentDescription = "${dayLabel(d)}: earned $e min, spent $s min" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Num("$e", color = if (e > 0) p.amber else p.muted, style = NumberStyle.copy(fontSize = 10.sp))
                Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f).height((100f * e / max).dp.coerceAtLeast(2.dp)).background(if (e == 0) p.line else p.amber))
                    Box(Modifier.weight(1f).height((100f * s / max).dp.coerceAtLeast(2.dp)).background(if (s == 0) p.line else p.muted))
                }
                Text(
                    dayLabel(d), maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (isToday) p.text else p.secondary,
                )
            }
        }
    }
}

/** A compact table: one row per metric, one column per day, today highlighted. */
@Composable
private fun DayGrid(days: List<LocalDate>, today: LocalDate, rows: List<Pair<String, Map<Long, Int>>>) {
    val p = Earnd.palette
    val labelWidth = 72.dp
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(labelWidth))
        days.forEach { d ->
            Text(
                dayLabel(d), Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                style = MaterialTheme.typography.labelSmall, color = if (d == today) p.text else p.secondary,
            )
        }
    }
    rows.forEach { (label, values) ->
        HorizontalDivider(color = p.divider)
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(labelWidth), style = MaterialTheme.typography.bodySmall, color = p.secondary, maxLines = 1)
            days.forEach { d ->
                val v = values[d.toEpochDay()] ?: 0
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Num(if (v == 0) "·" else "$v", color = if (v == 0) p.muted else p.text)
                }
            }
        }
    }
}
