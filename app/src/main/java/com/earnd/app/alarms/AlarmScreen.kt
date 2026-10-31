package com.earnd.app.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.earnd.app.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import com.earnd.app.ui.theme.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.earnd.app.R

@Composable
internal fun AlarmScreen(
    label: String,
    snoozesLeft: Int,
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.alarms_ring_kicker),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                label.ifBlank { stringResource(R.string.alarms_ring_fallback) },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text(stringResource(R.string.alarms_ring_stop), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(16.dp))
            if (RingPolicy.canSnooze(snoozesLeft)) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text(pluralStringResource(R.plurals.alarms_ring_snooze, snoozesLeft, snoozeMinutes, snoozesLeft))
                }
            } else {
                Text(
                    stringResource(R.string.alarms_ring_no_snooze),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.alarms_ring_mark_done))
            }
        }
    }
}
