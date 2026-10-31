package com.earnd.app.blocking

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.domain.BlockCause
import com.earnd.app.domain.RefuseReason

/** Full-screen lock UI. Stateless: takes state and callbacks. */
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
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(cause, target, until, ui.balanceMinutes)
            if (message != null) {
                Text(refuseText(message), color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium)
            }
            when (cause) {
                BlockCause.BEDTIME -> FilledTonalButton(onGrace, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.blocking_grace))
                }
                else -> SessionChoices(ui, onStartSession)
            }
            if (cause != BlockCause.BEDTIME) {
                Text(stringResource(R.string.blocking_earn_title), style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            OutlinedButton(onHome, Modifier.fillMaxWidth()) { Text(stringResource(R.string.blocking_go_home)) }
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
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (cause == BlockCause.BEDTIME) Icons.Outlined.Bedtime else Icons.Outlined.Lock, null,
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            when (cause) {
                BlockCause.BEDTIME -> stringResource(R.string.blocking_title_bedtime, target)
                else -> stringResource(R.string.blocking_title, target)
            },
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        val sub = when {
            cause == BlockCause.BEDTIME && until != null -> stringResource(R.string.blocking_bedtime_until, until)
            else -> pluralStringResource(R.plurals.blocking_balance, balance, balance)
        }
        Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun SessionChoices(ui: LockedUi, onStart: (Int) -> Unit) {
    val choices = LockedViewModel.SESSION_CHOICES.filter { it <= ui.balanceMinutes }
    when {
        ui.hardMode -> Text(stringResource(R.string.blocking_hard_mode_note),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ui.balanceMinutes <= 0 -> Unit
        else -> {
            Text(stringResource(R.string.blocking_use_time), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = (choices.take(3) + listOf(ui.balanceMinutes)).distinct().sorted().takeLast(3)
                shown.forEach { m ->
                    Button({ onStart(m) }, Modifier.weight(1f)) {
                        Text(stringResource(R.string.blocking_minutes_short, m))
                    }
                }
            }
        }
    }
}

@Composable
private fun EarnRow(title: String, detail: String, minutes: Int, photo: Boolean, onClick: () -> Unit) {
    Card(onClick, Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.blocking_plus_minutes, minutes),
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.padding(4.dp))
            Icon(if (photo) Icons.Outlined.CameraAlt else Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.blocking_check_in),
                tint = MaterialTheme.colorScheme.primary)
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

