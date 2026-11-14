package com.earnd.app.ui.home

import com.earnd.app.domain.UnlockCap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.earnd.app.ui.theme.Button
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.earnd.app.ui.theme.ChamferPanel
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.NumberStyle
import androidx.compose.material3.MaterialTheme
import com.earnd.app.ui.theme.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.earnd.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.DrainRuler
import com.earnd.app.ui.theme.DrainScale
import com.earnd.app.ui.theme.QuietButton
import com.earnd.app.data.entity.SessionReason
import com.earnd.app.data.entity.UnlockSessionEntity
import com.earnd.app.ui.common.UiFormats
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.common.formatRemaining
import kotlinx.coroutines.delay

data class BankState(
    val balance: Int,
    val session: UnlockSessionEntity?,
    val hardMode: Boolean,
    val unlockCapMin: Int? = null,
    val allDone: Boolean,
    val allDoneUnlock: Boolean,
    val nextExpiryAt: Long? = null,
    val minutesExpiringNext: Int = 0,
    /** Minutes owed from undone check-ins whose minutes were already spent; the next earnings pay it first. */
    val debtMin: Int = 0,
    /** True until the balance has been read; the card shows a dash instead of a misleading 0. */
    val loading: Boolean = false,
)

/** Scale for the drain ruler when no unlock cap is set. */
private const val DRAIN_REFERENCE_MIN = 60

private val SPEND_CHOICES = listOf(5, 10, 15, 30, 60)

@Composable
fun TimeBankCard(
    state: BankState,
    onSpend: (Int) -> Unit,
    onStop: () -> Unit,
    onAllDoneUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Earnd.palette
    ChamferPanel(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val s = state.session
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(s?.id) { while (s != null) { now = System.currentTimeMillis(); delay(1000) } }
            if (s != null && s.plannedEndAt > now) {
                val leftMs = s.plannedEndAt - now
                val total = (s.plannedEndAt - s.startedAt).coerceAtLeast(1L)
                Text(
                    stringResource(if (s.reason == SessionReason.ALL_DONE) R.string.home_bank_open_rest_of_day else R.string.home_bank_open_time_left),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = p.secondary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${((leftMs + 59_999) / 60_000).toInt()}", style = NumberStyle.copy(fontSize = 64.sp, lineHeight = 64.sp), color = p.amber)
                    Text(" min", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyLarge, color = p.secondary)
                    Spacer(Modifier.weight(1f))
                    Num(formatRemaining(leftMs), Modifier.padding(bottom = 8.dp), color = p.secondary)
                }
                DrainRuler(leftMs.toFloat() / total)
                Num(stringResource(R.string.home_bank_until, UiFormats.value.formatTime(s.plannedEndAt, java.time.ZoneId.systemDefault())), color = p.secondary)
                if (s.reason == SessionReason.SPEND) {
                    Text(stringResource(R.string.home_bank_unused_back), style = MaterialTheme.typography.bodySmall, color = p.secondary)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.home_bank_lock_now)) }
            } else {
                // The unlock cap limits one session, not the bank, so it is not a scale for the balance.
                val scale = maxOf(DRAIN_REFERENCE_MIN, (state.balance + 29) / 30 * 30)
                Text(stringResource(R.string.home_bank_locked_balance), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = p.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (state.loading) "—" else "${state.balance}", style = NumberStyle.copy(fontSize = 64.sp, lineHeight = 64.sp), color = p.amber)
                    Text(" min", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyLarge, color = p.secondary)
                }
                DrainRuler(state.balance, scale)
                DrainScale(scale)
                if (state.nextExpiryAt != null && state.minutesExpiringNext > 0) {
                    Num(
                        stringResource(
                            R.string.home_bank_expire_at, state.minutesExpiringNext,
                            // Weekday included: the rolling window can expire tomorrow, and a bare time reads as past.
                            java.time.Instant.ofEpochMilli(state.nextExpiryAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale.getDefault())),
                        ),
                        color = p.secondary,
                    )
                }
                if (state.debtMin > 0) {
                    Num(stringResource(R.string.home_bank_debt, state.debtMin), color = p.red)
                }
                when {
                    state.allDone && state.allDoneUnlock -> {
                        Text(stringResource(R.string.home_bank_all_done), style = MaterialTheme.typography.bodyMedium, color = p.secondary)
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onAllDoneUnlock, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.home_bank_unlock_today)) }
                    }
                    state.hardMode -> Text(
                        stringResource(R.string.home_bank_hard_mode),
                        style = MaterialTheme.typography.bodyMedium, color = p.secondary,
                    )
                    state.balance > 0 -> SpendPicker(state.balance, state.unlockCapMin, onSpend)
                    else -> Text(stringResource(R.string.home_bank_earn_hint), style = MaterialTheme.typography.bodyMedium, color = p.secondary)
                }
            }
        }
    }
}

@Composable
private fun SpendPicker(balance: Int, cap: Int?, onSpend: (Int) -> Unit) {
    val p = Earnd.palette
    val options = UnlockCap.choices(SPEND_CHOICES, balance, cap).take(5)
    var pick by remember(balance, cap) { mutableIntStateOf(options.first()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    Text(stringResource(R.string.home_bank_minutes_to_open), style = MaterialTheme.typography.labelSmall, color = p.secondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { m ->
            val sel = m == pick
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp)
                    .background(if (sel) p.raised else Color.Transparent)
                    .border(1.dp, if (sel) p.text else p.edge)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { pick = m }),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (m == balance) stringResource(R.string.home_bank_pick_all) else if (m == options.last() && cap != null) stringResource(R.string.home_bank_pick_max, m) else "$m", style = NumberStyle.copy(fontSize = 15.sp, lineHeight = 18.sp), color = if (sel) p.text else p.secondary)
            }
        }
    }
    Button(onClick = { onSpend(pick) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.home_bank_unlock_until, formatMinutes(pick), UiFormats.value.formatTime(now + pick * 60_000L, java.time.ZoneId.systemDefault())), style = MaterialTheme.typography.labelLarge)
    }
}
