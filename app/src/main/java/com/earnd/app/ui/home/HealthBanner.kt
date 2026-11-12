package com.earnd.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.earnd.app.R
import com.earnd.app.ui.theme.Earnd

/**
 * Shown on Today while something the app depends on is off (accessibility, notifications, exact alarms, battery) or
 * the alarm volume is zero. Re-checked every time the screen comes back, so it vanishes once it is fixed.
 */
@Composable
fun HealthBanner(onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    var health by remember { mutableStateOf(Health(emptyList(), false)) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumes++ } }
    LaunchedEffect(resumes) { health = HealthCheck.read(context) }
    if (health.ok) return
    val p = Earnd.palette
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        if (health.missing.isNotEmpty()) Column(
            Modifier.fillMaxWidth().border(1.dp, p.red).clickable(onClick = onOpenPermissions).padding(12.dp),
        ) {
            Text(stringResource(R.string.health_missing_title), style = MaterialTheme.typography.titleSmall, color = p.red)
            Text(health.missing.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = p.text)
            Text(stringResource(R.string.health_missing_tap), style = MaterialTheme.typography.bodySmall, color = p.secondary)
        }
        if (health.alarmMuted) Column(
            Modifier.fillMaxWidth().padding(top = if (health.missing.isNotEmpty()) 8.dp else 0.dp)
                .border(1.dp, p.amber)
                .clickable { runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.health_alarm_muted_title), style = MaterialTheme.typography.titleSmall, color = p.amber)
            Text(stringResource(R.string.health_alarm_muted_body), style = MaterialTheme.typography.bodySmall, color = p.secondary)
        }
    }
}
