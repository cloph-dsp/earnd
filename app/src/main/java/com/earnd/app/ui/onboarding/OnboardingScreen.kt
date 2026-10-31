package com.earnd.app.ui.onboarding

import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Wordmark
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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.earnd.app.ui.theme.Button
import com.earnd.app.ui.theme.OutlinedButton
import androidx.compose.ui.platform.LocalContext
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
    Page("How it works: habits", "Add a few habits and pick when they happen. Check in with a tap, or with a photo if you want proof. Each check-in earns minutes."),
    Page("How it works: tasks", "Tasks are for one-off things. They never lock anything, but a task can carry a reward that adds minutes when you finish it."),
    Page("How it works: unlocking", "Spend saved minutes to open your leisure apps for a while, or finish everything planned today and they open on their own. Saved minutes reset each day or week."),
    Page("Gentle by design", "Days with nothing planned never lock anything. Phone, messages, maps and settings always stay open. Your data stays on this phone."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish = { launchUi { Onboarding.markDone(); onFinished() } }
    val lastStep = pages.size + 1
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (step < pages.size) {
            val p = pages[step]
            if (step == 0) Wordmark()
            Text(p.title, style = MaterialTheme.typography.headlineMedium)
            Text(p.body, style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary)
        } else if (step == pages.size) {
            Text("Permissions", style = MaterialTheme.typography.headlineMedium)
            Text("You can change these later in Settings.", style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary)
            PermissionCards()
        } else {
            KeepAliveGuide()
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { finish() }) { Text("Skip") }
            Button(onClick = { if (step < lastStep) step++ else finish() }) {
                Text(if (step < lastStep) "Next" else "Get started")
            }
        }
    }
}

/** Android 13+ restricted settings and maker-specific battery steps. */
@Composable
fun KeepAliveGuide() {
    val context = LocalContext.current
    Text("Keep Earnd running", style = MaterialTheme.typography.headlineMedium)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Text("If the blocking service is greyed out", style = MaterialTheme.typography.titleMedium)
        Text(
            "Android may mark it as a restricted setting. Open App info, tap the three-dot menu at the top right, " +
                "choose Allow restricted settings, then turn the blocking service on again.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }
        }) { Text("Open App info") }
    }
    val guide = OemGuidance.forManufacturer(Build.MANUFACTURER)
    if (guide != null) {
        Text("On ${guide.brand} phones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        guide.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
    } else {
        Text(
            "If reminders stop or blocking turns itself off, check that battery use for Earnd is set to Unrestricted.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
