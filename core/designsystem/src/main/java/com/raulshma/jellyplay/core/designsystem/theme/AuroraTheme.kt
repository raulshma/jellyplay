package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.ColorStyle

internal val auroraFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Manrope"),
        fontProvider = fontProvider,
    ),
)

/** Accent options for the Aurora variant. First entry is the default. */
val AuroraAccents = listOf(
    VariantAccent("emerald", "Emerald", Color(0xFF34D399), Color(0xFF6EE7B7)),
    VariantAccent("violet", "Violet", Color(0xFFA78BFA), Color(0xFFC4B5FD)),
    VariantAccent("cyan", "Cyan", Color(0xFF22D3EE), Color(0xFF67E8F9)),
    VariantAccent("rose", "Rose", Color(0xFFFB7185), Color(0xFFFDA4AF)),
    VariantAccent("ice", "Ice", Color(0xFF93C5FD), Color(0xFFBFDBFE)),
)

/**
 * Aurora is a night-sky variant: dark-only (the gradient background and glowing
 * accents only read against deep tones). Accent roles come from the same
 * tone-correct generator as the accent swatches; the indigo/teal surfaces are
 * Aurora's own so the scheme melts into [auroraBackgroundBrush].
 */
fun getAuroraColorScheme(accent: String): ColorScheme {
    val accentDef = AuroraAccents.find { it.id == accent.lowercase() } ?: AuroraAccents.first()
    return ColorGenerator.generateColorScheme(
        seedColor = accentDef.darkColor,
        style = ColorStyle.TONAL_SPOT,
        darkTheme = true,
        oledMode = false,
    ).copy(
        background = ThemeVariantColors.AURORA_BACKGROUND,
        onBackground = Color(0xFFE2ECF5),
        surface = Color(0xFF081426),
        onSurface = Color(0xFFE2ECF5),
        surfaceVariant = Color(0xFF14273D),
        onSurfaceVariant = Color(0xFFA9C1D4),
        outline = Color(0xFF2A4258),
        outlineVariant = Color(0xFF1A2F44),
        surfaceContainerLowest = Color(0xFF030812),
        surfaceContainerLow = Color(0xFF071224),
        surfaceContainer = ThemeVariantColors.AURORA_DETAIL_BG,
        surfaceContainerHigh = Color(0xFF122640),
        surfaceContainerHighest = Color(0xFF193252),
    )
}

/** Aurora typography — airy Manrope with calm, loose body tracking. */
val AuroraTypography = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = auroraFontFamily, letterSpacing = (-0.2).sp),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = auroraFontFamily, letterSpacing = (-0.2).sp),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = auroraFontFamily),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = auroraFontFamily),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = auroraFontFamily),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = auroraFontFamily),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = auroraFontFamily),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = auroraFontFamily),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = auroraFontFamily),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = auroraFontFamily, letterSpacing = 0.2.sp),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = auroraFontFamily, letterSpacing = 0.2.sp),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = auroraFontFamily, letterSpacing = 0.3.sp),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = auroraFontFamily),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = auroraFontFamily),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = auroraFontFamily),
)
