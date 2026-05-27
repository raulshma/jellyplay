package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object BrandColors {
    val tmdb = Color(0xFF01B4E4)
    val imdb = Color(0xFFF5C518)
    val tvdb = Color(0xFF32A852)
}

object SyncStatusColors {
    val synced = Color(0xFF4CAF50)
    val syncing = Color(0xFF2196F3)
    val else_ = Color(0xFFFFC107)
}

object StatusColors {
    val available = Color(0xFF4CAF50)
    val pending = Color(0xFFFFA726)
    val requested = Color(0xFF42A5F5)
    val success = Color(0xFF4CAF50)
}

object RatingColors {
    val star = Color(0xFFFFC107)
}

object HdrColors {
    val hdr10Gold = Color(0xFFB8860B)
    val dolbyVisionGold = Color(0xFFFFD700)
}

object AmbientColors {
    val deepIndigo = Color(0xFF1a237e)
    val deepPurple = Color(0xFF4a148c)
    val deepTeal = Color(0xFF004d40)
    val deepRed = Color(0xFFb71c1c)
}

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

data class ExtendedColors(
    val statsOverlayText: Color = Color(0xFF8AB4F8),
    val hdrBadgeBackground: Color = Color(0xFF1A1A1A),
)
