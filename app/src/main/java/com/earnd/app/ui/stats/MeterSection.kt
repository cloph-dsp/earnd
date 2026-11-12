package com.earnd.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.startOfDay
import com.earnd.app.domain.today
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num

/** Minutes of the bank the meter charged per leisure app over the last week. */
@Composable
fun MeterSection(days: Int = 7) {
    val sl = ServiceLocator
    val context = LocalContext.current
    val since = remember { sl.clock.startOfDay(sl.clock.today().minusDays(days - 1L)) }
    val totals by remember { sl.db.meterUseDao().observeTotals(since) }.collectAsState(initial = emptyList())
    val labels by produceState(emptyMap<String, String>()) { value = sl.db.appRuleDao().all().associate { it.key to it.label } }
    SectionTitle(stringResource(R.string.stats_meter_title))
    if (totals.isEmpty()) {
        Text(stringResource(R.string.stats_meter_empty), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary)
        return
    }
    val p = Earnd.palette
    val max = totals.first().minutes.coerceAtLeast(1)
    totals.take(8).forEach { u ->
        val name = labels[u.appKey]?.takeIf { it.isNotBlank() && it != u.appKey } ?: UsageReport.label(context, u.appKey)
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Num(formatMinutes(u.minutes))
            }
            Box(Modifier.padding(top = 3.dp).fillMaxWidth(u.minutes.toFloat() / max).height(4.dp).background(p.amber))
        }
    }
}
