package com.earnd.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.ui.common.launchUi

/** Stores whether the intro was completed. */
object Onboarding {
    private const val KEY = "ui.onboarded"
    suspend fun isDone(): Boolean = ServiceLocator.settings.rawGet(KEY) == "1"
    suspend fun markDone() = ServiceLocator.settings.rawPut(KEY, "1")
}

private data class Page(val title: String, val body: String)

private val pages = listOf(
    Page("Welcome to Earnd", "Pick the apps that pull you away. Earnd keeps them closed until you've done a little of what matters to you."),
    Page("Earn your time", "Each habit check-in and finished task adds minutes to your bank. Start a session whenever you like, and the apps open until it ends."),
    Page("Gentle by design", "Days with nothing planned never lock anything. Phone, messages, maps and settings always stay open. Your data stays on this phone."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish = { launchUi { Onboarding.markDone(); onFinished() } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (step < pages.size) {
            val p = pages[step]
            Text(p.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(p.body, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("A few permissions", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("You can change any of these later in Settings.", style = MaterialTheme.typography.bodyLarge)
            PermissionCards()
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { finish() }) { Text("Skip") }
            Button(onClick = { if (step < pages.size) step++ else finish() }) {
                Text(if (step < pages.size) "Next" else "Get started")
            }
        }
    }
}
