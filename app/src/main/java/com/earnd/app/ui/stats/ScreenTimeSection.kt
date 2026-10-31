package com.earnd.app.ui.stats

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.startOfDay
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.onboarding.AppPermissions

private const val TOP_APPS = 8

/** Today's screen time from Usage Access: most-used apps and an hour-by-hour chart. */
@Composable
fun ScreenTimeSection() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(AppPermissions.usageAccessOn(context)) }
    var summary by remember { mutableStateOf<UsageSummary?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumes++ }
    }
    LaunchedEffect(resumes) {
        granted = AppPermissions.usageAccessOn(context)
        if (granted) {
            val sl = ServiceLocator
            val now = sl.clock.now()
            summary = UsageReport.today(context, sl.clock.startOfDay(sl.clock.today()), now)
        }
    }

    SectionTitle("Screen time today")
    if (!granted) {
        Text(
            "Allow usage access to see which apps took your time today. It is read on this phone only.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Allow usage access") }
        return
    }
    val s = summary ?: return
    Text("${formatMinutes((s.totalMs / 60_000).toInt())} on screen so far.", style = MaterialTheme.typography.bodyMedium)
    val top = s.perApp.take(TOP_APPS)
    val max = (top.firstOrNull()?.second ?: 1L).coerceAtLeast(1L)
    top.forEach { (pkg, ms) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(UsageReport.label(context, pkg), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(formatMinutes((ms / 60_000).toInt()), style = MaterialTheme.typography.labelMedium)
            }
            Box(
                Modifier.fillMaxWidth(ms.toFloat() / max).height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
    Text("By hour", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    HourBars(s.perHour)
}

@Composable
private fun HourBars(perHour: LongArray) {
    val max = (perHour.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        perHour.forEach { v ->
            Box(
                Modifier.weight(1f).height((80f * v / max).dp.coerceAtLeast(1.dp))
                    .background(
                        if (v == 0L) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiary,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00", "06", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
