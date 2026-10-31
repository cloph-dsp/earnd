package com.earnd.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.earnd.app.data.entity.ThemeMode

private val LightColors = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = MistVariant, onPrimaryContainer = DeepGreen,
    secondary = DeepGreen, onSecondary = Color.White,
    tertiary = Coral, onTertiary = Color.White,
    background = Mist, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = MistVariant, onSurfaceVariant = DeepGreen,
)

private val DarkColors = darkColorScheme(
    primary = TealLight, onPrimary = DeepGreenDarker,
    primaryContainer = DeepGreen, onPrimaryContainer = TealLight,
    secondary = Teal, onSecondary = Color.White,
    tertiary = CoralSoft, onTertiary = DeepGreenDarker,
    background = DeepGreenDarker, onBackground = MistVariant,
    surface = DeepGreenDarker, onSurface = MistVariant,
    surfaceVariant = DeepGreen, onSurfaceVariant = MistVariant,
)

/** Resolves the user's theme choice against the system setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun EarndTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
