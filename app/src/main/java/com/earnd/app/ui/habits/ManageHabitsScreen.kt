package com.earnd.app.ui.habits

import com.earnd.app.ui.theme.RowDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.ui.common.EditScaffold
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.launchUi

/** All habits in their display order, with up/down reordering and the archive. */
@Composable
fun ManageHabitsScreen(onBack: () -> Unit, onOpenHabit: (Long) -> Unit) {
    val sl = ServiceLocator
    val active by remember { sl.db.habitDao().observeActive() }.collectAsState(initial = emptyList())
    val archived by remember { sl.habitService.observeArchived() }.collectAsState(initial = emptyList())
    // Local order so taps feel instant; resynced whenever the database list changes.
    var order by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    LaunchedEffect(active) { order = active }

    val move: (Int, Int) -> Unit = { from, to ->
        val next = HabitRules.move(order, from, to)
        if (next !== order) {
            order = next
            launchUi { sl.habitService.reorder(next.map { it.id }) }
        }
    }

    EditScaffold("Habits", onBack = onBack) {
        SectionTitle("Order")
        if (order.isEmpty()) Text("No active habits.", style = MaterialTheme.typography.bodyMedium)
        order.forEachIndexed { i, h ->
            HabitLine(h, onOpen = { onOpenHabit(h.id) }) {
                IconButton(enabled = i > 0, onClick = { move(i, i - 1) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                IconButton(enabled = i < order.lastIndex, onClick = { move(i, i + 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
            }
            RowDivider()
        }

        SectionTitle("Archived")
        if (archived.isEmpty()) {
            Text("Archived habits keep their history and show up here.", style = MaterialTheme.typography.bodyMedium)
        }
        archived.forEach { h ->
            HabitLine(h, onOpen = { onOpenHabit(h.id) }) {
                TextButton(onClick = { launchUi { sl.habitService.unarchive(h.id) } }) { Text("Restore") }
            }
            RowDivider()
        }
    }
}

@Composable
private fun HabitLine(h: HabitEntity, onOpen: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Icon(HabitLook.icon(h.iconKey), null, tint = Color(h.colorArgb), modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(h.name, style = MaterialTheme.typography.bodyLarge) }
        trailing()
    }
}
