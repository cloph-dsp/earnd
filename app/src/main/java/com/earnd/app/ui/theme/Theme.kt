package com.earnd.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp
import com.earnd.app.data.entity.ThemeMode

/** 7dp chamfer on top-left and bottom-right: time-bank card, buttons and the lock badge only. */
val Chamfer = CutCornerShape(topStart = 7.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 0.dp)

private val Flat = RoundedCornerShape(2.dp)

private val EarndShapes = Shapes(
    extraSmall = Flat,
    small = Flat,
    medium = Flat,
    large = Chamfer,
    extraLarge = RoundedCornerShape(4.dp),
)

private fun EarndPalette.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // Neutral on purpose: stock Material components (tab indicator, slider, text-field focus)
        // must not paint themselves amber, or amber stops meaning "earned time".
        primary = text, onPrimary = bg,
        primaryContainer = raised, onPrimaryContainer = text,
        inversePrimary = bg,
        secondary = secondary, onSecondary = bg,
        secondaryContainer = line, onSecondaryContainer = text,
        tertiary = red, onTertiary = onRed,
        tertiaryContainer = raised, onTertiaryContainer = text,
        error = red, onError = onRed,
        errorContainer = raised, onErrorContainer = red,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = raised, onSurfaceVariant = secondary,
        surfaceTint = bg,
        inverseSurface = text, inverseOnSurface = bg,
        outline = muted, outlineVariant = divider,
        scrim = bg,
        surfaceBright = raised, surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = bg,
        surfaceContainer = raised, surfaceContainerHigh = raised, surfaceContainerHighest = line,
    )
}

/** Resolves the user's theme choice against the system setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Earnd's own palette roles beyond Material's (done, muted, amber, red). */
object Earnd {
    val palette: EarndPalette
        @Composable @ReadOnlyComposable get() = LocalEarndPalette.current
}

@Composable
fun EarndTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalEarndPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme(darkTheme),
            typography = EarndTypography,
            shapes = EarndShapes,
            content = content,
        )
    }
}
