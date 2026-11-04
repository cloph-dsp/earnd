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
    val session: UnlockSessionEntity?,
    val hardMode: Boolean,
    val unlockCapMin: Int? = null,
    val allDone: Boolean,
    val allDoneUnlock: Boolean,
    val nextExpiryAt: Long? = null,
    val minutesExpiringNext: Int = 0,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val s = state.session
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(s?.id) { while (s != null) { now = System.currentTimeMillis(); delay(1000) } }
            if (s != null && s.plannedEndAt > now) {
                val leftMs = s.plannedEndAt - now
                val total = (s.plannedEndAt - s.startedAt).coerceAtLeast(1L)
                Text(
                    if (s.reason == SessionReason.ALL_DONE) "OPEN FOR THE REST OF TODAY" else "OPEN · TIME LEFT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = p.secondary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${((leftMs + 59_999) / 60_000).toInt()}", style = NumberStyle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = p.amber)
                    Text(" min", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium, color = p.secondary)
                    Spacer(Modifier.weight(1f))
                    Num(formatRemaining(leftMs), Modifier.padding(bottom = 6.dp), color = p.secondary)
                }
                DrainRuler(leftMs.toFloat() / total)
                Num("Until " + UiFormats.value.formatTime(s.plannedEndAt, java.time.ZoneId.systemDefault()), color = p.secondary)
                if (s.reason == SessionReason.SPEND) {
                    Text("Unused whole minutes go back to the bank.", style = MaterialTheme.typography.bodySmall, color = p.secondary)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Lock now") }
            } else {
                val scale = state.unlockCapMin ?: DRAIN_REFERENCE_MIN
                Text("LOCKED · BALANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = p.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (state.loading) "—" else "${state.balance}", style = NumberStyle.copy(fontSize = 34.sp, lineHeight = 38.sp), color = p.amber)
                    Text(" min", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = p.secondary)
                    Spacer(Modifier.weight(1f))
                    Num("of $scale min", Modifier.padding(bottom = 4.dp), color = p.secondary)
                }
                DrainRuler(state.balance, scale)
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
    val p = Earnd.palette
    val options = UnlockCap.choices(SPEND_CHOICES, balance, cap).take(5)
    var pick by remember(balance, cap) { mutableIntStateOf(options.first()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    Text("Minutes to open", style = MaterialTheme.typography.labelSmall, color = p.secondary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { m ->
            val sel = m == pick
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (sel) p.amber else Color.Transparent)
                    .border(1.dp, if (sel) p.amber else p.line)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { pick = m }),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (m == balance) "All" else if (m == options.last() && cap != null) "Max $m" else "$m", style = NumberStyle, color = if (sel) p.onAmber else p.text)
            }
        }
    }
    Button(onClick = { onSpend(pick) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Unlock ${formatMinutes(pick)}") }
    Text(
        "Leisure apps until " + UiFormats.value.formatTime(now + pick * 60_000L, java.time.ZoneId.systemDefault()),
        style = MaterialTheme.typography.bodySmall, color = p.secondary,
    )
}
