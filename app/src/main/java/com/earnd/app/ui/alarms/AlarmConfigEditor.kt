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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.AlarmStyle
import com.earnd.app.data.entity.NagMode
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.ui.common.ChoiceChips
import androidx.compose.runtime.collectAsState
import com.earnd.app.domain.today
import com.earnd.app.i18n.Str
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
            TimeField(stringResource(R.string.alarmsui_daily_reminder_at), cfg.habitReminderMinuteOfDay, { onChange(cfg.copy(habitReminderMinuteOfDay = it)) })
            WeekendShiftHint(cfg.habitReminderMinuteOfDay)
        } else {
            Text(stringResource(R.string.alarmsui_reminders), style = MaterialTheme.typography.labelLarge)
            ReminderChips(cfg, onChange)
        }
        Text(stringResource(R.string.alarmsui_style), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth()) {
            AlarmStyle.values().forEach { st ->
                val on = cfg.style == st
                val label = stringResource(when (st) {
                    AlarmStyle.SILENT -> R.string.alarmsui_style_silent
                    AlarmStyle.NOTIFICATION -> R.string.alarmsui_style_notification
                    AlarmStyle.FULL_SCREEN -> R.string.alarmsui_style_alarm
                })
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
            Text(stringResource(R.string.alarmsui_advanced), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringResource(if (advanced) R.string.alarmsui_hide else R.string.alarmsui_show), style = MaterialTheme.typography.labelLarge, color = com.earnd.app.ui.theme.Earnd.palette.amber)
        }
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.alarmsui_if_no_response), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(NagMode.values().toList(), cfg.nagMode, ::nagLabel) { onChange(cfg.copy(nagMode = it)) }
        if (cfg.nagMode == NagMode.FIXED) {
            NumberField(stringResource(R.string.alarmsui_nag_every), cfg.nagEveryMin, { onChange(cfg.copy(nagEveryMin = (it ?: 1).coerceAtLeast(1))) })
        }
        if (cfg.nagMode != NagMode.OFF) {
            NumberField(stringResource(R.string.alarmsui_nag_max), cfg.nagMaxCount, { onChange(cfg.copy(nagMaxCount = it)) })
        }
        SwitchRow(stringResource(R.string.alarmsui_vibrate), cfg.vibrate, { onChange(cfg.copy(vibrate = it)) })
        Row {
            Text(
                stringResource(R.string.alarmsui_sound, com.earnd.app.alarms.AlarmSounds.nameOf(cfg.soundUri, soundOptions)),
                modifier = Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            )
            TextButton(onClick = { pickSound = true }) { Text(stringResource(R.string.alarmsui_choose)) }
        }
        Text(stringResource(R.string.alarmsui_ring_for), style = MaterialTheme.typography.labelLarge)
        RingForPicker(cfg.ringSec, withDefault = true) { onChange(cfg.copy(ringSec = it)) }
        Text(stringResource(R.string.alarmsui_volume), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(listOf<Int?>(null, 25, 50, 75, 100), cfg.volumePct, { if (it == null) Str.get(R.string.alarmsui_default) else Str.get(R.string.alarmsui_percent, it) }) {
            onChange(cfg.copy(volumePct = it))
        }
        if (cfg.style == AlarmStyle.FULL_SCREEN) {
            NumberField(stringResource(R.string.alarmsui_snoozes_allowed), cfg.snoozeMaxCount, { onChange(cfg.copy(snoozeMaxCount = (it ?: 0).coerceIn(0, 10))) })
        }
            }
        }
    }
}


fun styleLabel(s: AlarmStyle) = when (s) {
    AlarmStyle.SILENT -> Str.get(R.string.alarmsui_style_silent)
    AlarmStyle.NOTIFICATION -> Str.get(R.string.alarmsui_style_notification)
    AlarmStyle.FULL_SCREEN -> Str.get(R.string.alarmsui_style_full_screen)
}

fun nagLabel(m: NagMode) = when (m) {
    NagMode.OFF -> Str.get(R.string.alarmsui_nag_off)
    NagMode.PROGRESSIVE -> Str.get(R.string.alarmsui_nag_progressive)
    NagMode.FIXED -> Str.get(R.string.alarmsui_nag_fixed)
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
        stringResource(R.string.alarmsui_weekend_shift, com.earnd.app.ui.common.formatMinuteOfDay(shifted)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val RING_PRESETS = listOf(2, 5, 10, 30, 60, 120, 300)

fun ringLabel(sec: Int?): String = when {
    sec == null -> Str.get(R.string.alarmsui_default)
    sec >= 60 && sec % 60 == 0 -> Str.get(R.string.alarmsui_ring_minutes, sec / 60)
    else -> Str.get(R.string.alarmsui_ring_seconds, sec)
}

/** Preset chips plus a custom seconds field (1-600). [withDefault] adds a "Default" chip (null). */
@Composable
fun RingForPicker(sec: Int?, withDefault: Boolean, onChange: (Int?) -> Unit) {
    val options = (if (withDefault) listOf<Int?>(null) else emptyList()) + RING_PRESETS
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceChips(options, sec, ::ringLabel, onChange)
        NumberField(stringResource(R.string.alarmsui_ring_custom), sec, { onChange(it?.coerceIn(1, 600)) })
    }
}
