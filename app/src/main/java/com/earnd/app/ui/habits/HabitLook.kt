package com.earnd.app.ui.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.earnd.app.data.entity.DaySegment

/** App-owned icon set and colour swatches for habits. Keys are stored in HabitEntity.iconKey. */
object HabitLook {
    val icons: Map<String, ImageVector> = linkedMapOf(
        "star" to Icons.Outlined.Star,
        "water" to Icons.Outlined.LocalDrink,
        "move" to Icons.Outlined.DirectionsRun,
        "read" to Icons.Outlined.MenuBook,
        "meds" to Icons.Outlined.Medication,
        "calm" to Icons.Outlined.SelfImprovement,
        "tidy" to Icons.Outlined.CleaningServices,
        "food" to Icons.Outlined.Restaurant,
        "sleep" to Icons.Outlined.Bedtime,
        "work" to Icons.Outlined.Work,
        "create" to Icons.Outlined.Brush,
        "music" to Icons.Outlined.MusicNote,
        "care" to Icons.Outlined.Favorite,
        "plant" to Icons.Outlined.Spa,
    )

    val colors: List<Int> = listOf(
        0xFF0FA3A3, 0xFF0B3D3A, 0xFFFF7A5C, 0xFF4C8DDB, 0xFF8E6CD8,
        0xFFE0A63A, 0xFF5BAE6A, 0xFFD45D8A, 0xFF6B7A79,
    ).map { it.toInt() }

    fun icon(key: String): ImageVector = icons[key] ?: Icons.Outlined.Star

    fun segmentLabel(s: DaySegment): String = when (s) {
        DaySegment.MORNING -> "Morning"
        DaySegment.AFTERNOON -> "Afternoon"
        DaySegment.EVENING -> "Evening"
        DaySegment.NONE -> "Any time"
    }
}
