package com.earnd.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.earnd.app.data.entity.SessionReason
import com.earnd.app.data.entity.UnlockSessionEntity
import com.earnd.app.ui.common.formatMinutes
import com.earnd.app.ui.common.formatRemaining
import kotlinx.coroutines.delay

data class BankState(
    val balance: Int,
    val session: UnlockSessionEntity?,
    val hardMode: Boolean,
    val allDone: Boolean,
    val allDoneUnlock: Boolean,
)

private val SPEND_CHOICES = listOf(5, 10, 15, 30, 60)

@Composable
fun TimeBankCard(
    state: BankState,
    onSpend: (Int) -> Unit,
    onStop: () -> Unit,
    onAllDoneUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val s = state.session
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(s?.id) { while (s != null) { now = System.currentTimeMillis(); delay(1000) } }
            if (s != null && s.plannedEndAt > now) {
                Text(
                    if (s.reason == SessionReason.ALL_DONE) "Everything's done. Enjoy the rest of today." else "Leisure apps are open",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(formatRemaining(s.plannedEndAt - now) + " left", style = MaterialTheme.typography.displaySmall)
                OutlinedButton(onClick = onStop) { Text("Lock again now") }
                if (s.reason == SessionReason.SPEND) {
                    Text("Unused whole minutes go back to your bank.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("You have", style = MaterialTheme.typography.titleSmall)
                Text(formatMinutes(state.balance) + " saved", style = MaterialTheme.typography.displaySmall)
                when {
                    state.allDone && state.allDoneUnlock -> {
                        Text("Everything for today is done.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onAllDoneUnlock, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        )) { Text("Open leisure apps for today") }
                    }
                    state.hardMode -> Text(
                        "Hard mode is on: leisure opens once today's habits are done.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.balance > 0 -> SpendPicker(state.balance, onSpend)
                    else -> Text("Check in a habit or finish a task to add minutes.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SpendPicker(balance: Int, onSpend: (Int) -> Unit) {
    val options = (SPEND_CHOICES.filter { it < balance } + balance).distinct()
    var pick by remember(balance) { mutableIntStateOf(options.first()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.take(5).forEach { m ->
            FilterChip(selected = m == pick, onClick = { pick = m }, label = { Text(if (m == balance) "All" else "$m") })
        }
    }
    Button(onClick = { onSpend(pick) }) { Text("Use ${formatMinutes(pick)}") }
}
