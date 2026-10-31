package com.earnd.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.earnd.app.R

val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

private fun ui(size: Int, weight: FontWeight = FontWeight.Medium, line: Int = size + 5) = TextStyle(
    fontFamily = ChakraPetch, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
)

/** Numbers, times and counters only. */
val NumberStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)

/** Compact scale: body 13-14sp, section headings 12sp, screen titles 18sp, time bank 22sp. */
val EarndTypography = Typography(
    displayLarge = ui(22, FontWeight.SemiBold),
    displayMedium = ui(22, FontWeight.SemiBold),
    displaySmall = ui(22, FontWeight.SemiBold, 28),
    headlineLarge = ui(18, FontWeight.SemiBold),
    headlineMedium = ui(18, FontWeight.SemiBold),
    headlineSmall = ui(18, FontWeight.SemiBold),
    titleLarge = ui(18, FontWeight.SemiBold),
    titleMedium = ui(14, FontWeight.SemiBold),
    titleSmall = ui(12, FontWeight.SemiBold, 16),
    bodyLarge = ui(14),
    bodyMedium = ui(13),
    bodySmall = ui(12, line = 16),
    labelLarge = ui(13, FontWeight.SemiBold),
    labelMedium = ui(12),
    labelSmall = ui(11, line = 14),
)
