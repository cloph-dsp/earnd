package com.earnd.app.ui.alarms

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.TimeField

/** Editor for one AlarmConfigEntity. Pure UI: the caller decides where it is saved. */
@Composable
fun AlarmConfigEditor(cfg: AlarmConfigEntity, onChange: (AlarmConfigEntity) -> Unit) {
    val context = LocalContext.current
    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onChange(cfg.copy(soundUri = uri?.toString()))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cfg.ownerKind == OwnerKind.HABIT) {
            TimeField("Daily reminder at", cfg.habitReminderMinuteOfDay, { onChange(cfg.copy(habitReminderMinuteOfDay = it)) })
        } else {
            SwitchRow("Remind at the due time", cfg.atDue, { onChange(cfg.copy(atDue = it)) })
            var offsets by remember { mutableStateOf(cfg.offsetsBeforeMin) }
            OutlinedTextField(
                value = offsets,
                onValueChange = { t ->
                    offsets = t.filter { it.isDigit() || it == ',' }
                    onChange(cfg.copy(offsetsBeforeMin = normalizeCsv(offsets)))
                },
                label = { Text("Also remind before (minutes, e.g. 60,15)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Style", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(AlarmStyle.values().toList(), cfg.style, ::styleLabel) { onChange(cfg.copy(style = it)) }
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
                "Sound: " + (cfg.soundUri?.let { soundTitle(context, it) } ?: "Default"),
                modifier = Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            )
            TextButton(onClick = {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cfg.soundUri?.let(Uri::parse))
                runCatching { ringtone.launch(i) }
            }) { Text("Choose") }
        }
        if (cfg.style == AlarmStyle.FULL_SCREEN) {
            NumberField("Snoozes allowed", cfg.snoozeMaxCount, { onChange(cfg.copy(snoozeMaxCount = (it ?: 0).coerceIn(0, 10))) })
        }
    }
}

private fun normalizeCsv(t: String): String =
    t.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sortedDescending().joinToString(",")

private fun soundTitle(c: android.content.Context, uri: String): String =
    runCatching { RingtoneManager.getRingtone(c, Uri.parse(uri))?.getTitle(c) }.getOrNull() ?: "Custom"

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
