package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.ColorStyle

/** Accent options for the Vivid variant. First entry is the default. */
val VividAccents = listOf(
    VariantAccent("punch", "Punch Pink", Color(0xFFDB1C5D), Color(0xFFFF6B9D)),
    VariantAccent("azure", "Electric Azure", Color(0xFF2962FF), Color(0xFF7C9EFF)),
    VariantAccent("lime", "Lime Surge", Color(0xFF4D7E2B), Color(0xFFAED581)),
    VariantAccent("tangerine", "Tangerine", Color(0xFFC54118), Color(0xFFFF8A50)),
    VariantAccent("grape", "Grape Pop", Color(0xFF8E24AA), Color(0xFFCE93D8)),
)

/**
 * Vivid schemes run the accent through the same tone-correct generator as the
 * global accent swatches (primary pinned to legible tones, on-roles chosen by
 * luminance). [ColorStyle.VIBRANT] boosts saturation and spins tertiary +120°
 * for the two-tone energy the variant is named for.
 */
fun getVividColorScheme(accent: String, isDark: Boolean): ColorScheme {
    val accentDef = VividAccents.find { it.id == accent.lowercase() } ?: VividAccents.first()
    return ColorGenerator.generateColorScheme(
        seedColor = if (isDark) accentDef.darkColor else accentDef.lightColor,
        style = ColorStyle.VIBRANT,
        darkTheme = isDark,
        oledMode = false,
    )
}

/** Vivid typography — geometric Outfit with tight, punchy display tracking. */
val VividTypography: Typography
    @Composable get() = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = vividFontFamily, letterSpacing = (-0.5).sp),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = vividFontFamily, letterSpacing = (-0.5).sp),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = vividFontFamily),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = vividFontFamily),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = vividFontFamily),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = vividFontFamily),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = vividFontFamily),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = vividFontFamily),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = vividFontFamily),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = vividFontFamily),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = vividFontFamily),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = vividFontFamily),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = vividFontFamily),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = vividFontFamily),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = vividFontFamily),
)
