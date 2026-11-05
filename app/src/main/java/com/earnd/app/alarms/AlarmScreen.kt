package com.earnd.app.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earnd.app.R
import com.earnd.app.ui.theme.Button
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.NumberStyle
import com.earnd.app.ui.theme.QuietButton
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen alarm. Done is the one primary (red) action; Silence and Snooze are quiet.
 * [onStop] is the existing silence action (also bound to Back by the activity).
 */
@Composable
internal fun AlarmScreen(
    label: String,
    snoozesLeft: Int,
    onStop: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDone: () -> Unit,
    ignoredCount: Int = 0,
    rewardMinutes: Int = 0,
) {
    val time = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Num(time, color = MaterialTheme.colorScheme.onBackground, style = NumberStyle.copy(fontSize = 48.sp, lineHeight = 56.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                label.ifBlank { stringResource(R.string.alarms_ring_fallback) },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (ignoredCount > 0 || rewardMinutes > 0) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ignoredCount > 0) {
                        Num(stringResource(R.string.alarms_ring_ignored, ignoredCount), color = Earnd.palette.red, style = NumberStyle.copy(fontSize = 14.sp, lineHeight = 18.sp))
                    }
                    if (rewardMinutes > 0) {
                        Num(stringResource(R.string.alarms_ring_reward, rewardMinutes), color = Earnd.palette.amber, style = NumberStyle.copy(fontSize = 14.sp, lineHeight = 18.sp))
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.alarms_ring_done), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(16.dp))
            QuietButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.alarms_ring_silence))
            }
            Spacer(Modifier.height(12.dp))
            if (RingPolicy.canSnooze(snoozesLeft)) {
                Text(
                    pluralStringResource(R.plurals.alarms_ring_snooze, snoozesLeft, snoozesLeft),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                NagSchedule.SNOOZE_CHOICES_MIN.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { m ->
                            QuietButton(onClick = { onSnooze(m) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                Text(com.earnd.app.ui.common.formatMinutes(m))
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.alarms_ring_no_snooze),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
