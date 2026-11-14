package com.earnd.app.ui.onboarding

import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Wordmark
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.ui.common.launchUi

/** Stores whether the intro was completed. */
object Onboarding {
    private const val KEY = "ui.onboarded"
    suspend fun isDone(): Boolean = ServiceLocator.settings.rawGet(KEY) == "1"
    suspend fun markDone() = ServiceLocator.settings.rawPut(KEY, "1")
}

private data class Page(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

private val pages = listOf(
    Page(R.string.onboarding_p1_title, R.string.onboarding_p1_body),
    Page(R.string.onboarding_p2_title, R.string.onboarding_p2_body),
    Page(R.string.onboarding_p3_title, R.string.onboarding_p3_body),
    Page(R.string.onboarding_p4_title, R.string.onboarding_p4_body),
    Page(R.string.onboarding_p5_title, R.string.onboarding_p5_body),
    Page(R.string.onboarding_p6_title, R.string.onboarding_p6_body),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish = { launchUi { Onboarding.markDone(); onFinished() } }
    val lastStep = pages.size + 1
    val total = lastStep + 1
    Column(Modifier.fillMaxSize()) {
        // Progress line: the intro is eight steps long and nothing else says how far in you are.
        Box(Modifier.fillMaxWidth().height(2.dp).background(Earnd.palette.line)) {
            Box(Modifier.fillMaxWidth((step + 1f) / total).height(2.dp).background(Earnd.palette.amber))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            if (step < pages.size) {
                val p = pages[step]
                if (step == 0) Wordmark()
                Text(stringResource(p.titleRes), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(p.bodyRes), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary)
            } else if (step == pages.size) {
                Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onboarding_permissions_later), style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary)
                PermissionCards()
            } else {
                KeepAliveGuide()
            }
        }
        // Pinned: on the long permission and OEM pages the buttons would otherwise sit below the fold.
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.onboarding_skip)) }
            Button(onClick = { if (step < lastStep) step++ else finish() }) {
                Text(stringResource(if (step < lastStep) R.string.onboarding_next else R.string.onboarding_get_started))
            }
        }
    }
}

/** Android 13+ restricted settings and maker-specific battery steps. */
@Composable
fun KeepAliveGuide() {
    val context = LocalContext.current
    Text(stringResource(R.string.onboarding_keep_title), style = MaterialTheme.typography.headlineMedium)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Text(stringResource(R.string.onboarding_restricted_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.onboarding_restricted_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }
        }) { Text(stringResource(R.string.onboarding_open_app_info)) }
    }
    val guide = OemGuidance.forManufacturer(Build.MANUFACTURER)
    if (guide != null) {
        Text(stringResource(R.string.onboarding_oem_title, guide.brand), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        guide.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
    } else {
        Text(
            stringResource(R.string.onboarding_generic_battery),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
