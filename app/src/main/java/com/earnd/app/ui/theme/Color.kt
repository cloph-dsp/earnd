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
)

val DarkPalette = EarndPalette(
    bg = Color(0xFF121110),
    raised = Color(0xFF1D1B18),
    line = Color(0xFF2B2824),
    divider = Color(0xFF24211E),
    text = Color(0xFFE9E4DA),
    secondary = Color(0xFFA39C8F),
    muted = Color(0xFF7A7368),
    done = Color(0xFF57524A),
    amber = Color(0xFFFFB000),
    onAmber = Color(0xFF121110),
    red = Color(0xFFFF4A3D),
    onRed = Color(0xFF121110),
    drainEmpty = Color(0xFF3A342A),
)

val LightPalette = EarndPalette(
    bg = Color(0xFFF2EFE8),
    raised = Color(0xFFFFFFFF),
    line = Color(0xFFD6D0C4),
    divider = Color(0xFFE2DDD3),
    text = Color(0xFF121110),
    secondary = Color(0xFF5C564C),
    muted = Color(0xFF7A7368),
    done = Color(0xFFA8A194),
    amber = Color(0xFF9A6A00),
    onAmber = Color(0xFFFFFFFF),
    red = Color(0xFFC9281C),
    onRed = Color(0xFFFFFFFF),
    drainEmpty = Color(0xFFD6D0C4),
)

val LocalEarndPalette = staticCompositionLocalOf { DarkPalette }
