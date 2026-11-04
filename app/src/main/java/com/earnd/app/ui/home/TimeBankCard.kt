package com.earnd.app.ui.home

import com.earnd.app.domain.UnlockCap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.earnd.app.ui.theme.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.earnd.app.data.entity.SessionReason
import com.earnd.app.data.entity.UnlockSessionEntity
import com.earnd.app.ui.common.UiFormats
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.common.formatRemaining
import kotlinx.coroutines.delay

data class BankState(
    val balance: Int,
    /** True until the balance has been read; the card shows a dash instead of a misleading 0. */
    val loading: Boolean = false,
    val session: UnlockSessionEntity?,
    val hardMode: Boolean,
    val unlockCapMin: Int? = null,
    val allDone: Boolean,
    val allDoneUnlock: Boolean,
    val nextExpiryAt: Long? = null,
    val minutesExpiringNext: Int = 0,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val s = state.session
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(s?.id) { while (s != null) { now = System.currentTimeMillis(); delay(1000) } }
            if (s != null && s.plannedEndAt > now) {
                Text(
                    if (s.reason == SessionReason.ALL_DONE) "Open for the rest of today" else "Open",
                    style = MaterialTheme.typography.titleSmall, color = p.amber,
                )
                Text(formatRemaining(s.plannedEndAt - now) + " left", style = MaterialTheme.typography.displaySmall, color = p.amber)
                if (s.reason == SessionReason.SPEND) {
                    Text("Unused whole minutes go back to the bank.", style = MaterialTheme.typography.bodySmall, color = p.secondary)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onStop) { Text("Lock now") }
            } else {
                Text("LOCKED · BALANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = p.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (state.loading) "—" else "${state.balance}", style = NumberStyle.copy(fontSize = 34.sp, lineHeight = 38.sp), color = p.amber)
                    Text(" min", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = p.secondary)
                }
                DrainRuler(state.balance, state.unlockCapMin ?: DRAIN_REFERENCE_MIN)
                if (state.nextExpiryAt != null && state.minutesExpiringNext > 0) {
                    Num(
                        "${state.minutesExpiringNext} min expire at " +
                            UiFormats.value.formatTime(state.nextExpiryAt, java.time.ZoneId.systemDefault()),
                        color = p.secondary,
                    )
                }
                when {
                    state.allDone && state.allDoneUnlock -> {
                        Text("Everything for today is done.", style = MaterialTheme.typography.bodyMedium, color = p.secondary)
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onAllDoneUnlock, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Unlock for today") }
                    }
                    state.hardMode -> Text(
                        "Hard mode: apps open once today's habits are done.",
                        style = MaterialTheme.typography.bodyMedium, color = p.secondary,
                    )
                    state.balance > 0 -> SpendPicker(state.balance, state.unlockCapMin, onSpend)
                    else -> Text("Check in a habit or finish a task to earn minutes.", style = MaterialTheme.typography.bodyMedium, color = p.secondary)
                }
            }
        }
    }
}

@Composable
private fun SpendPicker(balance: Int, cap: Int?, onSpend: (Int) -> Unit) {
    val options = UnlockCap.choices(SPEND_CHOICES, balance, cap)
    var pick by remember(balance, cap) { mutableIntStateOf(options.first()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.take(5).forEach { m ->
            FilterChip(
                selected = m == pick, onClick = { pick = m },
                label = { Text(if (m == balance) "All" else if (m == options.last() && cap != null) "Max $m" else "$m", style = NumberStyle) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Earnd.palette.amber, selectedLabelColor = Earnd.palette.onAmber,
                ),
            )
        }
    }
    Button(onClick = { onSpend(pick) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Unlock ${formatMinutes(pick)}") }
}
