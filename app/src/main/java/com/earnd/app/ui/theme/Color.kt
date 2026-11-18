package com.earnd.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The five-role palette from DESIGN.md: graphite, text greys, amber (earned) and signal red (locked). */
@Immutable
data class EarndPalette(
    val bg: Color,
    val raised: Color,
    val line: Color,
    val divider: Color,
    val text: Color,
    val secondary: Color,
    val muted: Color,
    val done: Color,
    val amber: Color,
    val onAmber: Color,
    val red: Color,
    val onRed: Color,
    val drainEmpty: Color,
    /** Outline of controls (3:1 on the background, WCAG 1.4.11). */
    val edge: Color,
    /** Amber for large filled areas: a little less saturated than [amber], which stays on numerals and text. */
    val amberFill: Color,
)

val DarkPalette = EarndPalette(
    bg = Color(0xFF121110),
    raised = Color(0xFF1D1B18),
    line = Color(0xFF3E3A34),
    divider = Color(0xFF38342E),
    text = Color(0xFFE9E4DA),
    secondary = Color(0xFFA39C8F),
    muted = Color(0xFF8F887B),
    done = Color(0xFF6F695F),
    amber = Color(0xFFFFB000),
    onAmber = Color(0xFF121110),
    red = Color(0xFFFF4A3D),
    onRed = Color(0xFF121110),
    drainEmpty = Color(0xFF45413B),
    edge = Color(0xFF7A7368),
    amberFill = Color(0xFFE9A412),
)

// Light tokens picked with a WCAG contrast script: amber and done clear 4.5:1 on both bg and
// raised; edge clears 3:1; line/drainEmpty are ~1.7-2:1 so empty tracks and hairlines stay visible
// on cream instead of dissolving as they did at #D6D0C4/#E2DDD3. The dark hairlines above are
// tuned the same way (~1.5-1.7:1 on bg/raised) instead of the old 1.17:1 graphite-on-graphite.
val LightPalette = EarndPalette(
    bg = Color(0xFFF2EFE8),
    raised = Color(0xFFFFFFFF),
    line = Color(0xFFC0B9A8),
    divider = Color(0xFFCCC5B6),
    text = Color(0xFF121110),
    secondary = Color(0xFF5C564C),
    muted = Color(0xFF6B655B),
    done = Color(0xFF6F695D),
    amber = Color(0xFF855C00),
    onAmber = Color(0xFFFFFFFF),
    red = Color(0xFFC9281C),
    onRed = Color(0xFFFFFFFF),
    drainEmpty = Color(0xFFC0B9A8),
    edge = Color(0xFF7D7669),
    amberFill = Color(0xFF855C00),
)

val LocalEarndPalette = staticCompositionLocalOf { DarkPalette }
