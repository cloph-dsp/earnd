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
val NumberStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)

/** Scale with a 13sp floor (small light-on-dark text reads worse): body 15-17sp, labels 13sp, screen titles 22sp. */
val EarndTypography = Typography(
    displayLarge = ui(22, FontWeight.SemiBold),
    displayMedium = ui(22, FontWeight.SemiBold),
    displaySmall = ui(22, FontWeight.SemiBold, 28),
    headlineLarge = ui(22, FontWeight.SemiBold, 28),
    headlineMedium = ui(22, FontWeight.SemiBold, 28),
    headlineSmall = ui(22, FontWeight.SemiBold, 28),
    titleLarge = ui(22, FontWeight.SemiBold, 28),
    titleMedium = ui(17, FontWeight.SemiBold, 22),
    titleSmall = ui(13, FontWeight.SemiBold, 18),
    bodyLarge = ui(17, line = 22),
    bodyMedium = ui(15, line = 20),
    bodySmall = ui(13, line = 18),
    labelLarge = ui(15, FontWeight.SemiBold, 20),
    labelMedium = ui(13, line = 18),
    labelSmall = ui(13, line = 16),
)
