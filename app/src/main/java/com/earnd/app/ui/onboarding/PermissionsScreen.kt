package com.earnd.app.ui.onboarding

import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.RowDivider
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import com.earnd.app.ui.theme.QuietButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.earnd.app.R

/** Cards for each permission; re-checks status when the user returns from system settings. */
@Composable
fun PermissionCards(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    Column(modifier) {
        val perms = AppPermissions.all()
        perms.forEachIndexed { i, p ->
            val granted = remember(tick) { runCatching { p.granted(context) }.getOrDefault(false) }
            run {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (p.optional) stringResource(R.string.onboarding_perm_optional, p.title) else p.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (granted) Text(stringResource(R.string.onboarding_perm_on), style = MaterialTheme.typography.labelMedium, color = Earnd.palette.amber) else Text(stringResource(R.string.onboarding_perm_off), style = MaterialTheme.typography.labelMedium, color = Earnd.palette.secondary)
                    }
                    Text(p.why, style = MaterialTheme.typography.bodySmall, color = Earnd.palette.secondary, modifier = Modifier.padding(top = 2.dp))
                    if (!granted) {
                        QuietButton(onClick = {
                            val intentFn = p.settingsIntent
                            if (intentFn == null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                }
                            } else {
                                runCatching { context.startActivity(intentFn(context)) }
                                    .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                            }
                        }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_perm_allow)) }
                    }
                }
            }
            if (i < perms.lastIndex) RowDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.onboarding_permissions_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.onboarding_back)) }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            PermissionCards()
            Spacer(Modifier.height(16.dp))
            KeepAliveGuide()
        }
    }
}
