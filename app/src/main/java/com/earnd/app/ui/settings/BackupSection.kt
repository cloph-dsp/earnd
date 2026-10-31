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
import androidx.compose.ui.unit.dp
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
    var askClear by remember { mutableStateOf(false) }
    val say: (String) -> Unit = { msg -> launchUi { snackbar.showSnackbar(msg) } }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) launchUi {
            runCatching { store.export(context, uri) }
                .onSuccess { say("Exported $it rows.") }
                .onFailure { say("Export failed: ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) launchUi {
            runCatching { store.read(context, uri) }
                .onSuccess { toImport = it }
                .onFailure { say("Can't import: ${it.message}") }
        }
    }

    Text(
        "A JSON file with your habits, tasks, history, time bank, blocked apps and settings. Photos are not included.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exporter.launch("earnd-backup-${LocalDate.now()}.json") }) { Text("Export") }
        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import") }
    }
    TextButton(onClick = { gate { askClear = true } }) { Text("Clear all data", color = Earnd.palette.red) }

    toImport?.let { data ->
        AlertDialog(
            onDismissRequest = { toImport = null },
            title = { Text("Replace everything?") },
            text = {
                Text("This backup has ${data.rowCount()} rows. Everything on this phone is replaced by it. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    toImport = null
                    gate {
                        launchUi {
                            runCatching { store.restore(data) }
                                .onSuccess { say("Backup imported.") }
                                .onFailure { say("Import failed: ${it.message}") }
                        }
                    }
                }) { Text("Replace", color = Earnd.palette.red) }
            },
            dismissButton = { TextButton(onClick = { toImport = null }) { Text("Cancel") } },
        )
    }

    if (askClear) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Clear all data?") },
            text = {
                Column {
                    Text("Habits, tasks, history, saved minutes, blocked apps and settings are all deleted. Type $CLEAR_WORD to confirm.")
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == CLEAR_WORD, onClick = {
                    askClear = false
                    launchUi {
                        runCatching { store.clearAll() }
                            .onSuccess { say("All data cleared.") }
                            .onFailure { say("Couldn't clear: ${it.message}") }
                    }
                }) { Text("Delete everything", color = Earnd.palette.red) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Cancel") } },
        )
    }
}
