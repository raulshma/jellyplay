package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel

object ColorGenerator {
    fun generateColorScheme(
        seedColor: Color,
        style: ColorStyle,
        darkTheme: Boolean,
        oledMode: Boolean,
        contrastLevel: ContrastLevel = ContrastLevel.DEFAULT
    ): ColorScheme {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
        val hue = hsl[0]
        val sat = hsl[1]
        val light = hsl[2]

        val primary = getPrimaryColor(hue, sat, darkTheme, style, contrastLevel)
        val primaryContainer = getPrimaryContainerColor(hue, sat, darkTheme, style)
        val secondary = getSecondaryColor(hue, sat, darkTheme, style)
        val secondaryContainer = getSecondaryContainerColor(hue, sat, darkTheme, style)
        val tertiary = getTertiaryColor(hue, sat, darkTheme, style)
        val tertiaryContainer = getTertiaryContainerColor(hue, sat, darkTheme, style)

        val background = getBackgroundColor(hue, sat, darkTheme, oledMode, style)
        val surface = background

        val onPrimary = bestContrast(primary)
        val onPrimaryContainer = bestContrast(primaryContainer)
        val onSecondary = bestContrast(secondary)
        val onSecondaryContainer = bestContrast(secondaryContainer)
        val onTertiary = bestContrast(tertiary)
        val onTertiaryContainer = bestContrast(tertiaryContainer)

        val onSurface = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
        val onBackground = onSurface

        val surfaceVariant = getSurfaceVariantColor(hue, sat, darkTheme)
        val onSurfaceVariant = if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF49454F)
        val outline = if (darkTheme) Color(0xFF938F99) else Color(0xFF79747E)
        val outlineVariant = if (darkTheme) Color(0xFF49454F) else Color(0xFFCAC4D0)

        val error = if (darkTheme) Color(0xFFF2B8B5) else Color(0xFFB3261E)
        val onError = if (darkTheme) Color(0xFF601410) else Color(0xFFFFFFFF)
        val errorContainer = if (darkTheme) Color(0xFF8C1D18) else Color(0xFFF9DEDC)
        val onErrorContainer = if (darkTheme) Color(0xFFF9DEDC) else Color(0xFF410E0B)

        // Surface containers
        val surfaceContainerLowest: Color
        val surfaceContainerLow: Color
        val surfaceContainer: Color
        val surfaceContainerHigh: Color
        val surfaceContainerHighest: Color

        if (darkTheme) {
            if (oledMode) {
                surfaceContainerLowest = Color.Black
                surfaceContainerLow = Color(0xFF0A0A0A)
                surfaceContainer = Color(0xFF111111)
                surfaceContainerHigh = Color(0xFF1A1A1A)
                surfaceContainerHighest = Color(0xFF222222)
            } else {
                surfaceContainerLowest = Color(0xFF0F0E11)
                surfaceContainerLow = Color(0xFF1D1B20)
                surfaceContainer = Color(0xFF211F26)
                surfaceContainerHigh = Color(0xFF2B2930)
                surfaceContainerHighest = Color(0xFF36343B)
            }
        } else {
            surfaceContainerLowest = Color.White
            surfaceContainerLow = Color(0xFFF7F2FA)
            surfaceContainer = Color(0xFFF3EDF7)
            surfaceContainerHigh = Color(0xFFECE6F0)
            surfaceContainerHighest = Color(0xFFE6E0E9)
        }

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
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                surfaceContainerLowest = surfaceContainerLowest,
                surfaceContainerLow = surfaceContainerLow,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest
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
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                surfaceContainerLowest = surfaceContainerLowest,
                surfaceContainerLow = surfaceContainerLow,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest
            )
        }
    }

    private fun getPrimaryColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        style: ColorStyle,
        contrast: ContrastLevel
    ): Color {
        val s = when (style) {
            ColorStyle.MONOCHROME -> 0f
            ColorStyle.MUTED -> (sat * 0.5f).coerceAtLeast(0.15f)
            ColorStyle.VIBRANT, ColorStyle.EXPRESSIVE -> (sat * 1.2f).coerceAtMost(1f)
            else -> sat
        }
        val baseL = if (darkTheme) 0.80f else 0.40f
        val l = when (contrast) {
            ContrastLevel.MEDIUM -> if (darkTheme) 0.85f else 0.35f
            ContrastLevel.HIGH -> if (darkTheme) 0.90f else 0.25f
            else -> baseL
        }
        val h = if (style == ColorStyle.EXPRESSIVE) (hue + 240) % 360 else hue
        return hslToColor(floatArrayOf(h, s, l))
    }

    private fun getPrimaryContainerColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        style: ColorStyle
    ): Color {
        val s = when (style) {
            ColorStyle.MONOCHROME -> 0f
            ColorStyle.MUTED -> sat * 0.4f
            ColorStyle.VIBRANT, ColorStyle.EXPRESSIVE -> sat * 1.1f
            else -> sat * 0.8f
        }
        val l = if (darkTheme) 0.30f else 0.90f
        val h = if (style == ColorStyle.EXPRESSIVE) (hue + 240) % 360 else hue
        return hslToColor(floatArrayOf(h, s, l))
    }

    private fun getSecondaryColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        style: ColorStyle
    ): Color {
        val s = when (style) {
            ColorStyle.MONOCHROME -> 0f
            ColorStyle.MUTED -> sat * 0.2f
            ColorStyle.VIBRANT -> sat * 0.6f
            ColorStyle.EXPRESSIVE -> sat * 0.8f
            else -> sat * 0.3f
        }
        val l = if (darkTheme) 0.70f else 0.50f
        return hslToColor(floatArrayOf(hue, s, l))
    }

    private fun getSecondaryContainerColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        style: ColorStyle
    ): Color {
        val s = when (style) {
            ColorStyle.MONOCHROME -> 0f
            ColorStyle.MUTED -> sat * 0.15f
            ColorStyle.VIBRANT -> sat * 0.5f
            ColorStyle.EXPRESSIVE -> sat * 0.7f
            else -> sat * 0.2f
        }
        val l = if (darkTheme) 0.22f else 0.92f
        return hslToColor(floatArrayOf(hue, s, l))
    }

    private fun getTertiaryColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        style: ColorStyle
    ): Color {
        val s = when (style) {
            ColorStyle.MONOCHROME -> 0f
            ColorStyle.MUTED -> sat * 0.3f
            ColorStyle.VIBRANT, ColorStyle.EXPRESSIVE -> (sat * 1.1f).coerceAtMost(1f)
            else -> sat * 0.5f
        }
        val l = if (darkTheme) 0.80f else 0.40f
        val offset = when (style) {
            ColorStyle.MUTED -> 30f
            ColorStyle.VIBRANT, ColorStyle.EXPRESSIVE -> 120f
            else -> 60f
        }
        val h = (hue + offset) % 360
        return hslToColor(floatArrayOf(h, s, l))
    }

    private fun getTertiaryContainerColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        style: ColorStyle
    ): Color {
        val s = when (style) {
            ColorStyle.MONOCHROME -> 0f
            ColorStyle.MUTED -> sat * 0.2f
            ColorStyle.VIBRANT, ColorStyle.EXPRESSIVE -> sat * 0.9f
            else -> sat * 0.4f
        }
        val l = if (darkTheme) 0.30f else 0.90f
        val offset = when (style) {
            ColorStyle.MUTED -> 30f
            ColorStyle.VIBRANT, ColorStyle.EXPRESSIVE -> 120f
            else -> 60f
        }
        val h = (hue + offset) % 360
        return hslToColor(floatArrayOf(h, s, l))
    }

    private fun getBackgroundColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean,
        oledMode: Boolean,
        style: ColorStyle
    ): Color {
        if (darkTheme && oledMode) return Color.Black
        val s = if (style == ColorStyle.MONOCHROME) 0f else (sat * 0.05f).coerceAtMost(0.04f)
        val l = if (darkTheme) 0.08f else 0.98f
        return hslToColor(floatArrayOf(hue, s, l))
    }

    private fun getSurfaceVariantColor(
        hue: Float,
        sat: Float,
        darkTheme: Boolean
    ): Color {
        val s = sat * 0.1f
        val l = if (darkTheme) 0.22f else 0.90f
        return hslToColor(floatArrayOf(hue, s, l))
    }

    private fun bestContrast(background: Color): Color {
        val luminance = ColorUtils.calculateLuminance(background.toArgb())
        return if (luminance > 0.5) Color.Black else Color.White
    }

    private fun hslToColor(hsl: FloatArray): Color {
        val argb = ColorUtils.HSLToColor(hsl)
        return Color(argb)
    }
}
