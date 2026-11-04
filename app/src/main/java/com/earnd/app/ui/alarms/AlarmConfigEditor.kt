package com.earnd.app.ui.alarms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.AlarmStyle
import com.earnd.app.data.entity.NagMode
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.ui.common.ChoiceChips
import androidx.compose.runtime.collectAsState
import com.earnd.app.domain.today
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField

/** Editor for one AlarmConfigEntity. Pure UI: the caller decides where it is saved. */
@Composable
fun AlarmConfigEditor(cfg: AlarmConfigEntity, onChange: (AlarmConfigEntity) -> Unit) {
    val context = LocalContext.current
    var pickSound by remember { mutableStateOf(false) }
    var soundOptions by remember { mutableStateOf<List<com.earnd.app.alarms.SoundOption>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        soundOptions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.earnd.app.alarms.AlarmSounds.list(context) }
    }
    if (pickSound) SoundPickerDialog(cfg.soundUri, { onChange(cfg.copy(soundUri = it)); pickSound = false }, { pickSound = false })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cfg.ownerKind == OwnerKind.HABIT) {
            TimeField("Daily reminder at", cfg.habitReminderMinuteOfDay, { onChange(cfg.copy(habitReminderMinuteOfDay = it)) })
            WeekendShiftHint(cfg.habitReminderMinuteOfDay)
        } else {
            Text("Reminders", style = MaterialTheme.typography.labelLarge)
            ReminderChips(cfg, onChange)
        }
        Text("Style", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth()) {
            AlarmStyle.values().forEach { st ->
                val on = cfg.style == st
                val label = when (st) { AlarmStyle.SILENT -> "Silent"; AlarmStyle.NOTIFICATION -> "Notification"; AlarmStyle.FULL_SCREEN -> "Alarm" }
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .background(if (on) com.earnd.app.ui.theme.Earnd.palette.amber else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.dp, if (on) com.earnd.app.ui.theme.Earnd.palette.amber else com.earnd.app.ui.theme.Earnd.palette.line)
                        .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton) { onChange(cfg.copy(style = st)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (on) com.earnd.app.ui.theme.Earnd.palette.onAmber else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        var advanced by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { advanced = !advanced }, verticalAlignment = Alignment.CenterVertically) {
            Text("Advanced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (advanced) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = com.earnd.app.ui.theme.Earnd.palette.amber)
        }
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("If I don't respond", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(NagMode.values().toList(), cfg.nagMode, ::nagLabel) { onChange(cfg.copy(nagMode = it)) }
        if (cfg.nagMode == NagMode.FIXED) {
            NumberField("Every (minutes)", cfg.nagEveryMin, { onChange(cfg.copy(nagEveryMin = (it ?: 1).coerceAtLeast(1))) })
        }
        if (cfg.nagMode != NagMode.OFF) {
            NumberField("Stop after this many (blank = keep going)", cfg.nagMaxCount, { onChange(cfg.copy(nagMaxCount = it)) })
        }
        SwitchRow("Vibrate", cfg.vibrate, { onChange(cfg.copy(vibrate = it)) })
        Row {
            Text(
                "Sound: " + com.earnd.app.alarms.AlarmSounds.nameOf(cfg.soundUri, soundOptions),
                modifier = Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            )
            TextButton(onClick = { pickSound = true }) { Text("Choose") }
        }
        Text("Ring for", style = MaterialTheme.typography.labelLarge)
        RingForPicker(cfg.ringSec, withDefault = true) { onChange(cfg.copy(ringSec = it)) }
        Text("Volume", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf<Int?>(null, 25, 50, 75, 100), cfg.volumePct, { if (it == null) "Default" else "$it%" }) {
            onChange(cfg.copy(volumePct = it))
        }
        if (cfg.style == AlarmStyle.FULL_SCREEN) {
            NumberField("Snoozes allowed", cfg.snoozeMaxCount, { onChange(cfg.copy(snoozeMaxCount = (it ?: 0).coerceIn(0, 10))) })
        }
            }
        }
    }
}


fun styleLabel(s: AlarmStyle) = when (s) {
    AlarmStyle.SILENT -> "Silent"
    AlarmStyle.NOTIFICATION -> "Notification"
    AlarmStyle.FULL_SCREEN -> "Full-screen alarm"
}

fun nagLabel(m: NagMode) = when (m) {
    NagMode.OFF -> "Leave it"
    NagMode.PROGRESSIVE -> "Remind, spacing out"
    NagMode.FIXED -> "Remind every…"
}

/** On Saturday/Sunday with a later weekend start, shows the time the reminder actually rings today. */
@Composable
private fun WeekendShiftHint(minute: Int?) {
    val s by androidx.compose.runtime.remember { com.earnd.app.ServiceLocator.settings.settings }
        .collectAsState(initial = null)
    val cur = s ?: return
    val m = minute ?: return
    val today = com.earnd.app.ServiceLocator.clock.today()
    val shifted = com.earnd.app.alarms.DayStart.habitMinute(cur, today, m)
    if (shifted == m) return
    Text(
        "Today (weekend) it rings at ${com.earnd.app.ui.common.formatMinuteOfDay(shifted)}.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val RING_PRESETS = listOf(2, 5, 10, 30, 60, 120, 300)

fun ringLabel(sec: Int?): String = when {
    sec == null -> "Default"
    sec >= 60 && sec % 60 == 0 -> "${sec / 60} min"
    else -> "$sec s"
}

/** Preset chips plus a custom seconds field (1-600). [withDefault] adds a "Default" chip (null). */
@Composable
fun RingForPicker(sec: Int?, withDefault: Boolean, onChange: (Int?) -> Unit) {
    val options = (if (withDefault) listOf<Int?>(null) else emptyList()) + RING_PRESETS
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceChips(options, sec, ::ringLabel, onChange)
        NumberField("Custom (seconds, 1-600)", sec, { onChange(it?.coerceIn(1, 600)) })
    }
}
