package com.earnd.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
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
        if (health.missing.isNotEmpty()) HealthRow(
            text = health.missing.joinToString(", "),
            tint = p.red,
            onClick = onOpenPermissions,
        )
        if (health.alarmMuted) HealthRow(
            text = stringResource(R.string.health_alarm_muted_title),
            tint = p.amber,
            modifier = Modifier.padding(top = if (health.missing.isNotEmpty()) 6.dp else 0.dp),
            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        )
    }
}

/** One quiet line: a colored square, what is off, and a chevron. The screen keeps its hierarchy. */
@Composable
private fun HealthRow(text: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = Earnd.palette
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).border(1.dp, p.divider).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(tint))
        Text(
            text, Modifier.weight(1f).padding(horizontal = 10.dp),
            style = MaterialTheme.typography.bodySmall, color = p.text, maxLines = 1,
        )
        Icon(
            androidx.compose.material.icons.Icons.Outlined.ChevronRight, null,
            tint = p.muted, modifier = Modifier.size(16.dp),
        )
    }
}
