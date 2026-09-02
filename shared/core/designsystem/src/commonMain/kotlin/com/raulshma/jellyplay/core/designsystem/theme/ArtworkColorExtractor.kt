package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.raulshma.jellyplay.core.model.ColorStyle

@Immutable
data class ArtworkColors(
    val vibrant: Color?,
    val darkVibrant: Color?,
    val lightVibrant: Color?,
    val muted: Color?,
    val darkMuted: Color?,
    val lightMuted: Color?,
    val dominant: Color?,
) {
    val tintedBackground: Color = run {
        val base = darkMuted ?: muted ?: dominant ?: Color(0xFF2D1F2D)
        base.copy(alpha = 1f).let { c ->
            Color(
                red = (c.red * 0.45f).coerceIn(0f, 1f),
                green = (c.green * 0.35f).coerceIn(0f, 1f),
                blue = (c.blue * 0.45f).coerceIn(0f, 1f),
                alpha = 1f,
            )
        }
    }

    val tintedBackgroundLight: Color = run {
        val base = muted ?: darkMuted ?: dominant ?: Color(0xFF3D2F3D)
        base.copy(alpha = 1f).let { c ->
            Color(
                red = (c.red * 0.55f + 0.08f).coerceIn(0f, 1f),
                green = (c.green * 0.45f + 0.05f).coerceIn(0f, 1f),
                blue = (c.blue * 0.55f + 0.08f).coerceIn(0f, 1f),
                alpha = 1f,
            )
        }
    }

    val accentColor: Color = lightVibrant ?: vibrant ?: lightMuted ?: Color(0xFFE8B4C8)

    val pillSurface: Color = run {
        val base = darkMuted ?: muted ?: dominant ?: Color(0xFF3A2A3A)
        base.copy(alpha = 0.55f)
    }

    val pillSurfaceDark: Color = run {
        val base = darkMuted ?: dominant ?: Color(0xFF2A1A2A)
        base.copy(alpha = 0.7f)
    }
}

object ArtworkColorExtractor {

    /**
     * Derives a full [ColorScheme] from artwork swatches. The bitmap → swatch
     * extraction itself is a platform seam ([rememberArtworkColors]); this half
     * is pure color math and shared by every target.
     */
    fun generateColorScheme(
        artworkColors: ArtworkColors,
        darkTheme: Boolean,
        oledMode: Boolean = false,
        colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
        accentColorSwatch: String = "dynamic",
    ): ColorScheme {
        val swatch = AccentColorSwatch.entries.find { it.name.lowercase() == accentColorSwatch }
        val artworkSeed = artworkColors.vibrant
            ?: artworkColors.dominant
            ?: artworkColors.muted
        val seedColor = if (swatch != null && swatch != AccentColorSwatch.DYNAMIC) {
            Color(if (darkTheme) swatch.darkColor else swatch.lightColor)
        } else {
            artworkSeed
        }
        if (seedColor == null) {
            return if (darkTheme) darkColorScheme() else lightColorScheme()
        }

        return ColorGenerator.generateColorScheme(
            seedColor = seedColor,
            style = colorStyle,
            darkTheme = darkTheme,
            oledMode = oledMode,
        )
    }

    internal fun bestContrast(background: Color, light: Color, dark: Color): Color {
        val luminance = background.luminance()
        return if (luminance > 0.5f) dark else light
    }

    private fun Color.luminance(): Float {
        val r = red * 0.299f
        val g = green * 0.587f
        val b = blue * 0.114f
        return r + g + b
    }
}
