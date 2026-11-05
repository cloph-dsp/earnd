package com.earnd.app.ui.habits

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.earnd.app.R
import com.earnd.app.data.entity.HabitEntity

/** Asks how much was done; the amount is added to today's total. */
@Composable
internal fun AmountDialog(h: HabitEntity, onDismiss: () -> Unit, onAdd: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = HabitRules.parseAmount(text)
    val unit = h.unit ?: stringResource(R.string.home_amount_default_unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(h.name) },
        text = {
            OutlinedTextField(
                text, { text = it.take(10) },
                label = { Text(stringResource(R.string.home_amount_prompt, unit)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { amount?.let(onAdd) }) { Text(stringResource(R.string.common_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
