package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.model.ColorStyle

/** Accent options for the Vector Pop variant. First entry is the default. */
val VectorPopAccents = listOf(
    VariantAccent("cobalt", "Cobalt", Color(0xFF1D4ED8), Color(0xFF5B8DEF)),
    VariantAccent("tomato", "Tomato", Color(0xFFD83542), Color(0xFFFF6B6B)),
    VariantAccent("sunflower", "Sunflower", Color(0xFFF2B705), Color(0xFFFFD34D)),
    VariantAccent("kelly", "Kelly Green", Color(0xFF0D864B), Color(0xFF4CC38A)),
)

/**
 * Vector Pop schemes run the accent through the same tone-correct generator as
 * the global accent swatches — [ColorStyle.VIBRANT]'s +120° tertiary gives the
 * two-colour poster triad. The flat identity lives in the variant's 2 dp ink
 * card borders and zero elevations, so the only scheme override is a true-ink
 * outline role (the generator's soft gray can't carry the signature border).
 */
fun getVectorPopColorScheme(accent: String, isDark: Boolean): ColorScheme {
    val accentDef = VectorPopAccents.find { it.id == accent.lowercase() } ?: VectorPopAccents.first()
    return ColorGenerator.generateColorScheme(
        seedColor = if (isDark) accentDef.darkColor else accentDef.lightColor,
        style = ColorStyle.VIBRANT,
        darkTheme = isDark,
        oledMode = false,
    ).copy(outline = if (isDark) Color(0xFFD6D6D0) else Color(0xFF141414))
}

/** Vector Pop typography — geometric Poppins, poster-clean. */
val VectorPopTypography: Typography
    @Composable get() = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = vectorPopFontFamily, textGeometricTransform = null),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = vectorPopFontFamily, textGeometricTransform = null),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = vectorPopFontFamily, textGeometricTransform = null),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = vectorPopFontFamily),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = vectorPopFontFamily),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = vectorPopFontFamily),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = vectorPopFontFamily),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = vectorPopFontFamily),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = vectorPopFontFamily),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = vectorPopFontFamily),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = vectorPopFontFamily),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = vectorPopFontFamily),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = vectorPopFontFamily),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = vectorPopFontFamily),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = vectorPopFontFamily),
)
