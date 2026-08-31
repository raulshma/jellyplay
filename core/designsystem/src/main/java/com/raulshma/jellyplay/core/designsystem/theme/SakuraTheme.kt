package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.raulshma.jellyplay.core.model.ColorStyle

internal val sakuraFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Quicksand"),
        fontProvider = fontProvider,
    ),
)

/** Accent options for the Sakura variant. First entry is the default. */
val SakuraAccents = listOf(
    VariantAccent("rose", "Blossom Rose", Color(0xFFB05771), Color(0xFFF2A3BC)),
    VariantAccent("peach", "Peach", Color(0xFF9B634B), Color(0xFFF3B697)),
    VariantAccent("lavender", "Lavender", Color(0xFF74699F), Color(0xFFBFB3EA)),
    VariantAccent("mint", "Mint", Color(0xFF417962), Color(0xFF93D6BC)),
)

/**
 * Sakura schemes run the accent through the same tone-correct generator as the
 * global accent swatches. [ColorStyle.MUTED] gives the soft desaturated pastel
 * ramps (tertiary +30° analogous) the variant is named for; the seed hues keep
 * the blush/peach/lavender/mint identities.
 */
fun getSakuraColorScheme(accent: String, isDark: Boolean): ColorScheme {
    val accentDef = SakuraAccents.find { it.id == accent.lowercase() } ?: SakuraAccents.first()
    return ColorGenerator.generateColorScheme(
        seedColor = if (isDark) accentDef.darkColor else accentDef.lightColor,
        style = ColorStyle.MUTED,
        darkTheme = isDark,
        oledMode = false,
    )
}

/** Sakura typography — rounded Quicksand throughout for a soft, friendly feel. */
val SakuraTypography = Typography(
    displayLarge = JellyPlayTypography.displayLarge.copy(fontFamily = sakuraFontFamily),
    displayMedium = JellyPlayTypography.displayMedium.copy(fontFamily = sakuraFontFamily),
    displaySmall = JellyPlayTypography.displaySmall.copy(fontFamily = sakuraFontFamily),
    headlineLarge = JellyPlayTypography.headlineLarge.copy(fontFamily = sakuraFontFamily),
    headlineMedium = JellyPlayTypography.headlineMedium.copy(fontFamily = sakuraFontFamily),
    headlineSmall = JellyPlayTypography.headlineSmall.copy(fontFamily = sakuraFontFamily),
    titleLarge = JellyPlayTypography.titleLarge.copy(fontFamily = sakuraFontFamily),
    titleMedium = JellyPlayTypography.titleMedium.copy(fontFamily = sakuraFontFamily),
    titleSmall = JellyPlayTypography.titleSmall.copy(fontFamily = sakuraFontFamily),
    bodyLarge = JellyPlayTypography.bodyLarge.copy(fontFamily = sakuraFontFamily),
    bodyMedium = JellyPlayTypography.bodyMedium.copy(fontFamily = sakuraFontFamily),
    bodySmall = JellyPlayTypography.bodySmall.copy(fontFamily = sakuraFontFamily),
    labelLarge = JellyPlayTypography.labelLarge.copy(fontFamily = sakuraFontFamily),
    labelMedium = JellyPlayTypography.labelMedium.copy(fontFamily = sakuraFontFamily),
    labelSmall = JellyPlayTypography.labelSmall.copy(fontFamily = sakuraFontFamily),
)
