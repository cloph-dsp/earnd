package com.earnd.app.widgets

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/** Day/night colors shared by the Earnd widgets. */
internal object WidgetColors {
    val surface = ColorProvider(day = Color(0xFFF4FBFA), night = Color(0xFF0B3D3A))
    val onSurface = ColorProvider(day = Color(0xFF0B3D3A), night = Color(0xFFE3F4F2))
    val muted = ColorProvider(day = Color(0xFF4F6F6C), night = Color(0xFFA5C4C0))
    val primary = ColorProvider(day = Color(0xFF0FA3A3), night = Color(0xFF2E9E9B))
    val onPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val accent = ColorProvider(day = Color(0xFFD95F48), night = Color(0xFFFF9B85))
    val locked = ColorProvider(day = Color(0xFF0B3D3A), night = Color(0xFF16524E))
    val bedtime = ColorProvider(day = Color(0xFF3B4A6B), night = Color(0xFF2A3554))
}
