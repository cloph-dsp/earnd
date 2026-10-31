package com.earnd.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private val ButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/** The one primary action on a screen: signal red, chamfered. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Earnd.palette.red, contentColor = Earnd.palette.onRed,
        disabledContainerColor = Earnd.palette.line, disabledContentColor = Earnd.palette.muted,
    ),
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick, modifier, enabled, shape = Chamfer, colors = colors, contentPadding = ButtonPadding, content = content,
)

/** Amber chamfered button for spending or earning time. */
@Composable
fun AmberButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier, enabled, ButtonDefaults.buttonColors(
        containerColor = Earnd.palette.amber, contentColor = Earnd.palette.onAmber,
        disabledContainerColor = Earnd.palette.line, disabledContentColor = Earnd.palette.muted,
    ), content)

/** Secondary action: chamfered 1dp outline in the line color, text-colored label. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick, modifier, enabled, shape = Chamfer,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    border = BorderStroke(1.dp, if (enabled) Earnd.palette.muted else Earnd.palette.line),
    contentPadding = ButtonPadding, content = content,
)

/** Raised neutral button. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.FilledTonalButton(
    onClick, modifier, enabled, shape = Chamfer,
    colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Earnd.palette.line, contentColor = MaterialTheme.colorScheme.onBackground,
    ),
    contentPadding = ButtonPadding, content = content,
)

/**
 * Square 14dp outline checkbox inside a 48dp touch target. Amber when [reward] (habits, rewarded tasks),
 * text-colored otherwise; filled muted once done.
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reward: Boolean = false,
) {
    val p = Earnd.palette
    val edge = when {
        !enabled -> p.done
        checked -> p.done
        reward -> p.amber
        else -> p.text
    }
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else Modifier
    Box(modifier.then(toggle).size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(14.dp)
                .border(1.5.dp, edge, RectangleShape)
                .background(if (checked) p.done else Color.Transparent, RectangleShape),
        )
    }
}

/** Thin 3dp amber progress bar. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p = Earnd.palette
    Box(modifier.fillMaxWidth().height(3.dp).background(p.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(p.amber))
    }
}

/** Flat 1dp row divider. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier, thickness = 1.dp, color = Earnd.palette.divider)

/** Lowercase "earnd" in Chakra Petch 600, amber. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) =
    Text("earnd", modifier, color = Earnd.palette.amber, style = MaterialTheme.typography.titleLarge)

/** Mono numerals for counts, times and minutes. */
@Composable
fun Num(text: String, modifier: Modifier = Modifier, color: Color = Earnd.palette.secondary, style: TextStyle = NumberStyle) =
    Text(text, modifier, color = color, style = style)

/** Raised chamfered panel; only for the time-bank card and the lock badge. */
@Composable
fun ChamferPanel(modifier: Modifier = Modifier, color: Color = Earnd.palette.raised, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = Chamfer, color = color, border = BorderStroke(1.dp, Earnd.palette.line)) {
        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp), content = content)
    }
}
