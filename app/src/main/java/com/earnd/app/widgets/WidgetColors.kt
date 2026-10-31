package com.earnd.app.widgets

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/** Day/night colors shared by the Earnd widgets: graphite and paper, amber for earned, red for locked. */
internal object WidgetColors {
    val surface = ColorProvider(day = Color(0xFFF2EFE8), night = Color(0xFF121110))
    val onSurface = ColorProvider(day = Color(0xFF121110), night = Color(0xFFE9E4DA))
    val muted = ColorProvider(day = Color(0xFF5C564C), night = Color(0xFFA39C8F))
    val primary = ColorProvider(day = Color(0xFF9A6A00), night = Color(0xFFFFB000))
    val onPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF121110))
    val accent = ColorProvider(day = Color(0xFFC9281C), night = Color(0xFFFF4A3D))
    val locked = accent
    val bedtime = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1D1B18))
    val onBedtime = onSurface
    val tile = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1D1B18))
}
