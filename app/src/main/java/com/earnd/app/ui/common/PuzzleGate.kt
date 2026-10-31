package com.earnd.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.Settings
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.domain.RemovalPuzzle
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.NumberStyle
import androidx.compose.ui.unit.sp

/**
 * Returns a gate: `gate { action() }` runs the action at once when no puzzle is set, otherwise
 * after the user solves the configured challenge. Cancelling drops the action.
 */
@Composable
fun rememberPuzzleGate(): (() -> Unit) -> Unit {
    val settings by remember { ServiceLocator.settings.settings }.collectAsState(initial = Settings())
    var challenge by remember { mutableStateOf<RemovalPuzzle.Challenge?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    challenge?.let { c ->
        PuzzleDialog(c, onSolved = {
            val a = pendingAction
            challenge = null; pendingAction = null
            a?.invoke()
        }, onCancel = { challenge = null; pendingAction = null })
    }

    return remember(settings.removalPuzzle) {
        { action ->
            val c = RemovalPuzzle.create(settings.removalPuzzle)
            if (c == null) action() else { pendingAction = action; challenge = c }
        }
    }
}

@Composable
private fun PuzzleDialog(c: RemovalPuzzle.Challenge, onSolved: () -> Unit, onCancel: () -> Unit) {
    var input by remember(c) { mutableStateOf("") }
    var wrong by remember(c) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Slow down first") },
        text = {
            Column {
                Text(
                    if (c.kind == PuzzleKind.ARITHMETIC) "Solve this to continue." else "Type this sentence exactly to continue.",
                    style = MaterialTheme.typography.bodyMedium, color = Earnd.palette.secondary,
                )
                if (c.kind == PuzzleKind.ARITHMETIC) {
                    Num(c.prompt, Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onBackground,
                        style = NumberStyle.copy(fontSize = 22.sp))
                } else {
                    Text(c.prompt, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
                }
                OutlinedTextField(
                    input, { input = it; wrong = false }, modifier = Modifier.fillMaxWidth(),
                    singleLine = c.kind == PuzzleKind.ARITHMETIC, isError = wrong,
                    keyboardOptions = if (c.kind == PuzzleKind.ARITHMETIC) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    supportingText = if (wrong) ({ Text("Not quite. Try again.") }) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (RemovalPuzzle.check(c, input)) onSolved() else wrong = true }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Keep it") } },
    )
}
