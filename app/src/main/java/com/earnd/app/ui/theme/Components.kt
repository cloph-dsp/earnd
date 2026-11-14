package com.earnd.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon

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

/** Secondary action: 1dp outline in the line color, text-colored label. No chamfer: that belongs to filled buttons. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick, modifier.heightIn(min = 48.dp), enabled, shape = RectangleShape,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    border = BorderStroke(1.dp, if (enabled) Earnd.palette.edge else Earnd.palette.line),
    contentPadding = ButtonPadding, content = content,
)

/** Quiet action: 1dp amber outline, amber label, transparent fill; still a 48dp touch target. */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick, modifier.heightIn(min = 48.dp), enabled, shape = RectangleShape,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = Earnd.palette.amber, disabledContentColor = Earnd.palette.muted),
    border = BorderStroke(1.dp, if (enabled) Earnd.palette.amber else Earnd.palette.line),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), content = content,
)

/** Flat tappable list row (>= [minHeight]); the row itself is the button. Pair with [RowDivider]. */
@Composable
fun ListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier.fillMaxWidth().heightIn(min = minHeight).clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically, content = content,
)

/** 10-segment drain ruler: filled amber segments = [balance] relative to [full]. */
@Composable
fun DrainRuler(balance: Int, full: Int, modifier: Modifier = Modifier) {
    val p = Earnd.palette
    val f = full.coerceAtLeast(1)
    val filled = if (balance <= 0) 0 else kotlin.math.ceil(balance.coerceAtMost(f) * DRAIN_SEGMENTS.toDouble() / f).toInt()
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(DRAIN_SEGMENTS) { i ->
            Box(Modifier.weight(1f).height(8.dp).background(if (i < filled) p.amberFill else p.drainEmpty))
        }
    }
}

/**
 * Continuous drain ruler: [fraction] (0..1) of the 10 segments is amber, the boundary segment fills partially,
 * so a live countdown visibly drains between whole-segment steps.
 */
@Composable
fun DrainRuler(fraction: Float, modifier: Modifier = Modifier) {
    val p = Earnd.palette
    val level = fraction.coerceIn(0f, 1f) * DRAIN_SEGMENTS
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(DRAIN_SEGMENTS) { i ->
            val part = (level - i).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).height(8.dp).background(p.drainEmpty)) {
                if (part > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(part).background(p.amberFill))
            }
        }
    }
}

private const val DRAIN_SEGMENTS = 10

/** Scale under a [DrainRuler]: 0, half and full, in minutes, so the shape can be read as a quantity. */
@Composable
fun DrainScale(full: Int, modifier: Modifier = Modifier) {
    val style = NumberStyle.copy(fontSize = 12.sp)
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Num("0", color = Earnd.palette.muted, style = style)
        Num("${full / 2}", color = Earnd.palette.muted, style = style)
        Num("$full", color = Earnd.palette.muted, style = style)
    }
}

/** Icon-only add button: a quiet square, so it never competes with the one red action. */
@Composable
fun AddFab(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    val p = Earnd.palette
    Surface(onClick, modifier.size(56.dp), shape = RectangleShape, color = p.raised, border = BorderStroke(1.dp, p.edge)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, description, tint = p.text) }
    }
}

/** Segmented progress: [done] of [total] cells filled, in the same shape language as [DrainRuler]. */
@Composable
fun SegmentBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    val p = Earnd.palette
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total.coerceAtLeast(1)) { i ->
            Box(Modifier.weight(1f).height(6.dp).background(if (i < done) p.amberFill else p.drainEmpty))
        }
    }
}

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
 * Square 20dp outline checkbox inside a 48dp touch target. Amber when [reward] (habits, rewarded tasks),
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
        checked && reward -> p.amberFill
        checked -> p.done
        reward -> p.amber
        else -> p.text
    }
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else Modifier
    Box(modifier.then(toggle).size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp)
                .border(2.dp, edge, RectangleShape)
                // A rewarded tick fills amber: the same "progress" fill as the ruler and segment bar.
                .background(if (checked) edge else Color.Transparent, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            // A tick, not just a fill: done must be readable without relying on color alone.
            if (checked) Icon(Icons.Outlined.Check, null, tint = if (reward) p.onAmber else p.text, modifier = Modifier.size(14.dp))
        }
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
    Text("earnd", modifier, color = Earnd.palette.amber, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))

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
