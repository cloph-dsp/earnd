package com.earnd.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.backup.BackupData
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.common.rememberPuzzleGate
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.OutlinedButton
import java.time.LocalDate

private const val CLEAR_WORD = "DELETE"

/** Export to / import from a JSON file, and wiping everything. */
@Composable
fun BackupSection(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val store = ServiceLocator.backup
    val gate = rememberPuzzleGate()
    var toImport by remember { mutableStateOf<BackupData?>(null) }
    val say: (String) -> Unit = { msg -> launchUi { snackbar.showSnackbar(msg) } }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) launchUi {
            runCatching { store.export(context, uri) }
                .onSuccess { say(context.getString(R.string.backup_exported, it)) }
                .onFailure { say(context.getString(R.string.backup_export_failed, it.message)) }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchUi {
            runCatching { store.read(context, uri) }
                .onSuccess { toImport = it }
                .onFailure { say(context.getString(R.string.backup_cant_import, it.message)) }
        }
    }

    Text(
        stringResource(R.string.backup_blurb),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exporter.launch("earnd-backup-${LocalDate.now()}.json") }) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.backup_import)) }
    }

    toImport?.let { data ->
        AlertDialog(
            onDismissRequest = { toImport = null },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Text(stringResource(R.string.backup_replace_body, data.rowCount()))
            },
            confirmButton = {
                TextButton(onClick = {
                    toImport = null
                    gate {
                        launchUi {
                            runCatching { store.restore(data) }
                                .onSuccess { say(context.getString(R.string.backup_imported)) }
                                .onFailure { say(context.getString(R.string.backup_import_failed, it.message)) }
                        }
                    }
                }) { Text(stringResource(R.string.backup_replace_confirm), color = Earnd.palette.red) }
            },
            dismissButton = { TextButton(onClick = { toImport = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/** The one destructive action, kept apart from export and import. */
@Composable
fun ClearDataSection(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val store = ServiceLocator.backup
    val gate = rememberPuzzleGate()
    var askClear by remember { mutableStateOf(false) }
    val say: (String) -> Unit = { msg -> launchUi { snackbar.showSnackbar(msg) } }
    Text(
        stringResource(R.string.backup_clear_blurb),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { gate { askClear = true } }) { Text(stringResource(R.string.backup_clear_button), color = Earnd.palette.red) }

    if (askClear) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.backup_clear_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_clear_body, CLEAR_WORD))
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == CLEAR_WORD, onClick = {
                    askClear = false
                    launchUi {
                        runCatching { store.clearAll() }
                            .onSuccess { say(context.getString(R.string.backup_cleared)) }
                            .onFailure { say(context.getString(R.string.backup_clear_failed, it.message)) }
                    }
                }) { Text(stringResource(R.string.backup_clear_confirm), color = Earnd.palette.red) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
