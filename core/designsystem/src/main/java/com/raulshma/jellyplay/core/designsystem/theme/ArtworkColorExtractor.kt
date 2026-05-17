package com.raulshma.jellyplay.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

data class ArtworkColors(
    val vibrant: Color?,
    val darkVibrant: Color?,
    val lightVibrant: Color?,
    val muted: Color?,
    val darkMuted: Color?,
    val lightMuted: Color?,
    val dominant: Color?,
) {
    /**
     * Deep muted background tint derived from artwork — matches Pixel Player's
     * solid-color player background style.
     */
    val tintedBackground: Color
        get() {
            val base = darkMuted ?: muted ?: dominant ?: Color(0xFF2D1F2D)
            // Darken and desaturate to get a deep, muted plum tone
            return base.copy(alpha = 1f).let { c ->
                Color(
                    red = (c.red * 0.45f).coerceIn(0f, 1f),
                    green = (c.green * 0.35f).coerceIn(0f, 1f),
                    blue = (c.blue * 0.45f).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            }
        }

    /** Lighter variant of tintedBackground for gradient top edge. */
    val tintedBackgroundLight: Color
        get() {
            val base = muted ?: darkMuted ?: dominant ?: Color(0xFF3D2F3D)
            return base.copy(alpha = 1f).let { c ->
                Color(
                    red = (c.red * 0.55f + 0.08f).coerceIn(0f, 1f),
                    green = (c.green * 0.45f + 0.05f).coerceIn(0f, 1f),
                    blue = (c.blue * 0.55f + 0.08f).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            }
        }

    /** Accent color for seek bar, active controls — a soft pink/vibrant from the artwork. */
    val accentColor: Color
        get() = lightVibrant ?: vibrant ?: lightMuted ?: Color(0xFFE8B4C8)

    /** Semi-transparent surface for pill-shaped control containers. */
    val pillSurface: Color
        get() {
            val base = darkMuted ?: muted ?: dominant ?: Color(0xFF3A2A3A)
            return base.copy(alpha = 0.55f)
        }

    /** Darker pill surface for the secondary controls row. */
    val pillSurfaceDark: Color
        get() {
            val base = darkMuted ?: dominant ?: Color(0xFF2A1A2A)
            return base.copy(alpha = 0.7f)
        }
}

object ArtworkColorExtractor {

    fun extractColors(bitmap: Bitmap): ArtworkColors {
        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .generate()

        return ArtworkColors(
            vibrant = palette.vibrantSwatch?.toComposeColor(),
            darkVibrant = palette.darkVibrantSwatch?.toComposeColor(),
            lightVibrant = palette.lightVibrantSwatch?.toComposeColor(),
            muted = palette.mutedSwatch?.toComposeColor(),
            darkMuted = palette.darkMutedSwatch?.toComposeColor(),
            lightMuted = palette.lightMutedSwatch?.toComposeColor(),
            dominant = palette.dominantSwatch?.toComposeColor(),
        )
    }

    fun generateColorScheme(
        artworkColors: ArtworkColors,
        darkTheme: Boolean,
    ): ColorScheme {
        val seedColor = artworkColors.vibrant
            ?: artworkColors.dominant
            ?: artworkColors.muted
            ?: return if (darkTheme) darkColorScheme() else lightColorScheme()

        val primary = seedColor
        val onPrimary = bestContrast(seedColor, Color.White, Color.Black)
        val primaryContainer = if (darkTheme) {
            artworkColors.darkVibrant ?: seedColor.copy(alpha = 0.7f)
        } else {
            artworkColors.lightVibrant ?: seedColor.copy(alpha = 0.3f)
        }
        val onPrimaryContainer = bestContrast(primaryContainer, Color.White, Color.Black)

        val secondary = artworkColors.muted ?: artworkColors.lightMuted ?: seedColor
        val onSecondary = bestContrast(secondary, Color.White, Color.Black)
        val secondaryContainer = if (darkTheme) {
            secondary.copy(alpha = 0.6f)
        } else {
            secondary.copy(alpha = 0.3f)
        }
        val onSecondaryContainer = bestContrast(secondaryContainer, Color.White, Color.Black)

        val tertiary = artworkColors.lightVibrant ?: artworkColors.lightMuted ?: seedColor
        val onTertiary = bestContrast(tertiary, Color.White, Color.Black)
        val tertiaryContainer = if (darkTheme) {
            tertiary.copy(alpha = 0.6f)
        } else {
            tertiary.copy(alpha = 0.3f)
        }
        val onTertiaryContainer = bestContrast(tertiaryContainer, Color.White, Color.Black)

        return if (darkTheme) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
            )
        }
    }

    private fun bestContrast(background: Color, light: Color, dark: Color): Color {
        val luminance = background.luminance()
        return if (luminance > 0.5f) dark else light
    }

    private fun Color.luminance(): Float {
        val r = red * 0.299f
        val g = green * 0.587f
        val b = blue * 0.114f
        return r + g + b
    }

    private fun Palette.Swatch.toComposeColor(): Color = Color(rgb)
}
