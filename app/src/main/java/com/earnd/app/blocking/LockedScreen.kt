package com.earnd.app.blocking

import androidx.compose.material3.TextButton
import com.earnd.app.domain.UnlockCap
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.RowDivider
import com.earnd.app.ui.theme.Chamfer
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.AmberButton
import com.earnd.app.ui.common.SectionTitle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import com.earnd.app.ui.theme.Checkbox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Bedtime
import com.earnd.app.ui.theme.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.domain.BlockCause
import com.earnd.app.domain.RefuseReason

/** What the gate tells about a metered app: [realMinutes] of use left at [ratePct] (null = free, rate 0). */
data class GateInfo(val realMinutes: Int?, val ratePct: Int)

/** Full-screen lock/gate UI. Stateless: takes state and callbacks. */
@Composable
fun LockedScreen(
    cause: BlockCause,
    target: String,
    until: String?,
    ui: LockedUi,
    message: String?,
    onCheckIn: (EarnItem.Habit) -> Unit,
    onTask: (EarnItem.Task) -> Unit,
    onStartSession: (Int) -> Unit,
    onGrace: () -> Unit,
    onHome: () -> Unit,
    gate: GateInfo? = null,
    onOpen: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (cause == BlockCause.CONFIRM && gate != null) GateHeader(target, gate) else Header(cause, target, until, ui.balanceMinutes)
            if (message != null) {
                Text(refuseText(message), color = Earnd.palette.red,
                    style = MaterialTheme.typography.bodyMedium)
            }
            when (cause) {
                BlockCause.BEDTIME -> FilledTonalButton(onGrace, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.blocking_grace))
                }
                BlockCause.CONFIRM -> {
                    AmberButton(onOpen, Modifier.fillMaxWidth()) { Text(stringResource(R.string.blocking_gate_open)) }
                    SessionChoices(ui, onStartSession, title = stringResource(R.string.blocking_gate_pass))
                }
                else -> SessionChoices(ui, onStartSession)
            }
            if (cause != BlockCause.BEDTIME) {
                SectionTitle(stringResource(R.string.blocking_earn_title))
                LazyColumn(Modifier.weight(1f)) {
                    if (ui.items.isEmpty()) item {
                        Text(stringResource(R.string.blocking_nothing_to_earn),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(ui.items, key = { it.key() }) { item ->
                        when (item) {
                            is EarnItem.Habit -> EarnRow(item.day.habit.name, progress(item), item.minutes,
                                photo = item.needsPhoto) { onCheckIn(item) }
                            is EarnItem.Task -> EarnRow(item.title,
                                if (item.overdue) stringResource(R.string.blocking_task_overdue) else
                                    stringResource(R.string.blocking_task_today), item.minutes, photo = false) { onTask(item) }
                        }
                        RowDivider()
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(onHome, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.blocking_go_home), color = Earnd.palette.secondary)
            }
        }
    }
}

private fun EarnItem.key(): String = when (this) {
    is EarnItem.Habit -> "h${day.habit.id}"
    is EarnItem.Task -> "t$id"
}

@Composable
private fun progress(item: EarnItem.Habit): String =
    stringResource(R.string.blocking_habit_progress, item.day.done, item.day.required)

@Composable
private fun Header(cause: BlockCause, target: String, until: String?, balance: Int) {
    val p = Earnd.palette
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.Start) {
        Row(
            Modifier.background(p.red, Chamfer).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (cause == BlockCause.BEDTIME) Icons.Outlined.Bedtime else Icons.Outlined.Lock, null,
                tint = p.onRed, modifier = Modifier.size(14.dp))
            Spacer(Modifier.padding(3.dp))
            Text(
                when (cause) {
                    BlockCause.BEDTIME -> stringResource(R.string.blocking_title_bedtime, target)
                    else -> stringResource(R.string.blocking_title, target)
                },
                style = MaterialTheme.typography.labelLarge, color = p.onRed,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.blocking_not_yet), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        val sub = when {
            cause == BlockCause.BEDTIME && until != null -> stringResource(R.string.blocking_bedtime_until, until)
            else -> pluralStringResource(R.plurals.blocking_balance, balance, balance)
        }
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.secondary)
    }
}

@Composable
private fun GateHeader(target: String, gate: GateInfo) {
    val p = Earnd.palette
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.Start) {
        Row(
            Modifier.background(p.amber, Chamfer).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.blocking_gate_badge), style = MaterialTheme.typography.labelLarge, color = p.onAmber)
        }
        Spacer(Modifier.height(16.dp))
        Text(target, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (gate.realMinutes == null) stringResource(R.string.blocking_gate_free)
            else stringResource(R.string.blocking_gate_left, gate.realMinutes, gate.ratePct),
            style = MaterialTheme.typography.bodyMedium, color = p.secondary,
        )
    }
}

@Composable
private fun SessionChoices(ui: LockedUi, onStart: (Int) -> Unit, title: String = stringResource(R.string.blocking_use_time)) {
    when {
        ui.hardMode -> Text(stringResource(R.string.blocking_hard_mode_note),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ui.balanceMinutes <= 0 -> Unit
        else -> {
            SectionTitle(title)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val all = UnlockCap.choices(LockedViewModel.SESSION_CHOICES, ui.balanceMinutes, ui.unlockCapMin)
                val shown = (all.dropLast(1).take(2) + all.last()).distinct()
                shown.forEach { m ->
                    AmberButton({ onStart(m) }, Modifier.weight(1f)) {
                        Text(stringResource(R.string.blocking_minutes_short, m))
                    }
                }
            }
        }
    }
}

@Composable
private fun EarnRow(title: String, detail: String, minutes: Int, photo: Boolean, onClick: () -> Unit) {
    val p = Earnd.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = p.secondary)
        }
        Num(stringResource(R.string.blocking_plus_minutes, minutes), color = p.amber)
        Spacer(Modifier.padding(6.dp))
        if (photo) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = stringResource(R.string.blocking_check_in),
                tint = p.amber, modifier = Modifier.size(16.dp))
        } else {
            Checkbox(false, null, reward = true)
        }
    }
}

@Composable
private fun refuseText(reason: String): String = when (reason) {
    RefuseReason.HARD_MODE -> stringResource(R.string.blocking_hard_mode_note)
    RefuseReason.NOT_ENOUGH -> stringResource(R.string.blocking_not_enough)
    RefuseReason.GRACE_USED -> stringResource(R.string.blocking_grace_used)
    RefuseReason.NOT_BEDTIME -> stringResource(R.string.blocking_not_bedtime)
    else -> stringResource(R.string.blocking_try_again)
}

