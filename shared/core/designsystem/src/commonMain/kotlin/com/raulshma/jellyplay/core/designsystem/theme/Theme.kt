package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.AppFontScale

enum class AccentColorSwatch(
    val displayName: String,
    val lightColor: Long,
    val darkColor: Long
) {
    DYNAMIC("Dynamic", 0L, 0L),
    BRAND("Brand (Default)", 0xFF904B3E, 0xFFFFB4A6),
    SAPPHIRE("Sapphire Blue", 0xFF1976D2, 0xFF90CAF9),
    EMERALD("Emerald Green", 0xFF388E3C, 0xFFA5D6A7),
    AMETHYST("Amethyst Purple", 0xFF7B1FA2, 0xFFE040FB),
    ROSE("Rose Pink", 0xFFC2185B, 0xFFF48FB1),
    CORAL("Coral Orange", 0xFFF57C00, 0xFFFFCC80),
    AMBER("Amber Gold", 0xFFFBC02D, 0xFFFFE082),
    CRIMSON("Crimson Red", 0xFFD32F2F, 0xFFEF9A9A),
}

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
)

private val MediumContrastLightColorScheme = lightColorScheme(
    primary = md_theme_light_mediumContrast_primary,
    onPrimary = md_theme_light_mediumContrast_onPrimary,
    primaryContainer = md_theme_light_mediumContrast_primaryContainer,
    onPrimaryContainer = md_theme_light_mediumContrast_onPrimaryContainer,
    secondary = md_theme_light_mediumContrast_secondary,
    onSecondary = md_theme_light_mediumContrast_onSecondary,
    secondaryContainer = md_theme_light_mediumContrast_secondaryContainer,
    onSecondaryContainer = md_theme_light_mediumContrast_onSecondaryContainer,
    tertiary = md_theme_light_mediumContrast_tertiary,
    onTertiary = md_theme_light_mediumContrast_onTertiary,
    tertiaryContainer = md_theme_light_mediumContrast_tertiaryContainer,
    onTertiaryContainer = md_theme_light_mediumContrast_onTertiaryContainer,
    error = md_theme_light_mediumContrast_error,
    onError = md_theme_light_mediumContrast_onError,
    errorContainer = md_theme_light_mediumContrast_errorContainer,
    onErrorContainer = md_theme_light_mediumContrast_onErrorContainer,
    background = md_theme_light_mediumContrast_background,
    onBackground = md_theme_light_mediumContrast_onBackground,
    surface = md_theme_light_mediumContrast_surface,
    onSurface = md_theme_light_mediumContrast_onSurface,
    surfaceVariant = md_theme_light_mediumContrast_surfaceVariant,
    onSurfaceVariant = md_theme_light_mediumContrast_onSurfaceVariant,
    outline = md_theme_light_mediumContrast_outline,
    outlineVariant = md_theme_light_mediumContrast_outlineVariant,
    surfaceContainerLowest = md_theme_light_mediumContrast_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_mediumContrast_surfaceContainerLow,
    surfaceContainer = md_theme_light_mediumContrast_surfaceContainer,
    surfaceContainerHigh = md_theme_light_mediumContrast_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_mediumContrast_surfaceContainerHighest,
)

private val HighContrastLightColorScheme = lightColorScheme(
    primary = md_theme_light_highContrast_primary,
    onPrimary = md_theme_light_highContrast_onPrimary,
    primaryContainer = md_theme_light_highContrast_primaryContainer,
    onPrimaryContainer = md_theme_light_highContrast_onPrimaryContainer,
    secondary = md_theme_light_highContrast_secondary,
    onSecondary = md_theme_light_highContrast_onSecondary,
    secondaryContainer = md_theme_light_highContrast_secondaryContainer,
    onSecondaryContainer = md_theme_light_highContrast_onSecondaryContainer,
    tertiary = md_theme_light_highContrast_tertiary,
    onTertiary = md_theme_light_highContrast_onTertiary,
    tertiaryContainer = md_theme_light_highContrast_tertiaryContainer,
    onTertiaryContainer = md_theme_light_highContrast_onTertiaryContainer,
    error = md_theme_light_highContrast_error,
    onError = md_theme_light_highContrast_onError,
    errorContainer = md_theme_light_highContrast_errorContainer,
    onErrorContainer = md_theme_light_highContrast_onErrorContainer,
    background = md_theme_light_highContrast_background,
    onBackground = md_theme_light_highContrast_onBackground,
    surface = md_theme_light_highContrast_surface,
    onSurface = md_theme_light_highContrast_onSurface,
    surfaceVariant = md_theme_light_highContrast_surfaceVariant,
    onSurfaceVariant = md_theme_light_highContrast_onSurfaceVariant,
    outline = md_theme_light_highContrast_outline,
    outlineVariant = md_theme_light_highContrast_outlineVariant,
    surfaceContainerLowest = md_theme_light_highContrast_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_highContrast_surfaceContainerLow,
    surfaceContainer = md_theme_light_highContrast_surfaceContainer,
    surfaceContainerHigh = md_theme_light_highContrast_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_highContrast_surfaceContainerHighest,
)

private val MediumContrastDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_mediumContrast_primary,
    onPrimary = md_theme_dark_mediumContrast_onPrimary,
    primaryContainer = md_theme_dark_mediumContrast_primaryContainer,
    onPrimaryContainer = md_theme_dark_mediumContrast_onPrimaryContainer,
    secondary = md_theme_dark_mediumContrast_secondary,
    onSecondary = md_theme_dark_mediumContrast_onSecondary,
    secondaryContainer = md_theme_dark_mediumContrast_secondaryContainer,
    onSecondaryContainer = md_theme_dark_mediumContrast_onSecondaryContainer,
    tertiary = md_theme_dark_mediumContrast_tertiary,
    onTertiary = md_theme_dark_mediumContrast_onTertiary,
    tertiaryContainer = md_theme_dark_mediumContrast_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_mediumContrast_onTertiaryContainer,
    error = md_theme_dark_mediumContrast_error,
    onError = md_theme_dark_mediumContrast_onError,
    errorContainer = md_theme_dark_mediumContrast_errorContainer,
    onErrorContainer = md_theme_dark_mediumContrast_onErrorContainer,
    background = md_theme_dark_mediumContrast_background,
    onBackground = md_theme_dark_mediumContrast_onBackground,
    surface = md_theme_dark_mediumContrast_surface,
    onSurface = md_theme_dark_mediumContrast_onSurface,
    surfaceVariant = md_theme_dark_mediumContrast_surfaceVariant,
    onSurfaceVariant = md_theme_dark_mediumContrast_onSurfaceVariant,
    outline = md_theme_dark_mediumContrast_outline,
    outlineVariant = md_theme_dark_mediumContrast_outlineVariant,
    surfaceContainerLowest = md_theme_dark_mediumContrast_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_mediumContrast_surfaceContainerLow,
    surfaceContainer = md_theme_dark_mediumContrast_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_mediumContrast_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_mediumContrast_surfaceContainerHighest,
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_highContrast_primary,
    onPrimary = md_theme_dark_highContrast_onPrimary,
    primaryContainer = md_theme_dark_highContrast_primaryContainer,
    onPrimaryContainer = md_theme_dark_highContrast_onPrimaryContainer,
    secondary = md_theme_dark_highContrast_secondary,
    onSecondary = md_theme_dark_highContrast_onSecondary,
    secondaryContainer = md_theme_dark_highContrast_secondaryContainer,
    onSecondaryContainer = md_theme_dark_highContrast_onSecondaryContainer,
    tertiary = md_theme_dark_highContrast_tertiary,
    onTertiary = md_theme_dark_highContrast_onTertiary,
    tertiaryContainer = md_theme_dark_highContrast_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_highContrast_onTertiaryContainer,
    error = md_theme_dark_highContrast_error,
    onError = md_theme_dark_highContrast_onError,
    errorContainer = md_theme_dark_highContrast_errorContainer,
    onErrorContainer = md_theme_dark_highContrast_onErrorContainer,
    background = md_theme_dark_highContrast_background,
    onBackground = md_theme_dark_highContrast_onBackground,
    surface = md_theme_dark_highContrast_surface,
    onSurface = md_theme_dark_highContrast_onSurface,
    surfaceVariant = md_theme_dark_highContrast_surfaceVariant,
    onSurfaceVariant = md_theme_dark_highContrast_onSurfaceVariant,
    outline = md_theme_dark_highContrast_outline,
    outlineVariant = md_theme_dark_highContrast_outlineVariant,
    surfaceContainerLowest = md_theme_dark_highContrast_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_highContrast_surfaceContainerLow,
    surfaceContainer = md_theme_dark_highContrast_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_highContrast_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_highContrast_surfaceContainerHighest,
)

private val OledColorScheme = darkColorScheme(
    primary = oled_theme_primary,
    onPrimary = oled_theme_onPrimary,
    primaryContainer = oled_theme_primaryContainer,
    onPrimaryContainer = oled_theme_onPrimaryContainer,
    secondary = oled_theme_secondary,
    onSecondary = oled_theme_onSecondary,
    secondaryContainer = oled_theme_secondaryContainer,
    onSecondaryContainer = oled_theme_onSecondaryContainer,
    tertiary = oled_theme_tertiary,
    onTertiary = oled_theme_onTertiary,
    tertiaryContainer = oled_theme_tertiaryContainer,
    onTertiaryContainer = oled_theme_onTertiaryContainer,
    error = oled_theme_error,
    onError = oled_theme_onError,
    errorContainer = oled_theme_errorContainer,
    onErrorContainer = oled_theme_onErrorContainer,
    background = oled_theme_background,
    onBackground = oled_theme_onBackground,
    surface = oled_theme_surface,
    onSurface = oled_theme_onSurface,
    surfaceVariant = oled_theme_surfaceVariant,
    onSurfaceVariant = oled_theme_onSurfaceVariant,
    outline = oled_theme_outline,
    outlineVariant = oled_theme_outlineVariant,
    surfaceContainerLowest = oled_theme_surfaceContainerLowest,
    surfaceContainerLow = oled_theme_surfaceContainerLow,
    surfaceContainer = oled_theme_surfaceContainer,
    surfaceContainerHigh = oled_theme_surfaceContainerHigh,
    surfaceContainerHighest = oled_theme_surfaceContainerHighest,
)

private val DefaultShapes = Shapes(
    extraSmall = AbsoluteSmoothCornerShape(12.dp, 60),
    small = AbsoluteSmoothCornerShape(14.dp, 60),
    medium = AbsoluteSmoothCornerShape(20.dp, 60),
    large = AbsoluteSmoothCornerShape(28.dp, 60),
    extraLarge = AbsoluteSmoothCornerShape(36.dp, 60),
)

val LocalJellyPlayColorScheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }
val LocalJellyPlayTypography = staticCompositionLocalOf<androidx.compose.material3.Typography> { error("No Typography provided") }
val LocalJellyPlayShapes = staticCompositionLocalOf<Shapes> { error("No Shapes provided") }
val LocalIsSynthwave = staticCompositionLocalOf { false }
val LocalIsSoothingTheme = staticCompositionLocalOf { false }
val LocalIsMonochromeTheme = staticCompositionLocalOf { false }
val LocalThemeVariant = staticCompositionLocalOf { ThemeVariant.STANDARD }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JellyPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    oledMode: Boolean = false,
    contrastLevel: ContrastLevel = ContrastLevel.DEFAULT,
    isTv: Boolean = false,
    performanceMode: Boolean = false,
    reduceMotion: Boolean = false,
    accentColorSwatch: String = "dynamic",
    colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    synthwaveMode: Boolean = false,
    synthwaveAccent: String = "magenta",
    soothingMode: Boolean = false,
    soothingAccent: String = "ocean",
    monochromeMode: Boolean = false,
    appFontScale: AppFontScale = AppFontScale.DEFAULT,
    content: @Composable () -> Unit,
) {
    val effectiveDarkTheme = darkTheme || isTv || synthwaveMode
    val effectiveOledMode = (oledMode || monochromeMode) && effectiveDarkTheme && !synthwaveMode && !soothingMode

    // Bridge the Compose-visible synthwave flag to the global state that
    // `SynthwaveDynamicShape` reads from `Shape.createOutline` (which runs outside of
    // composition and therefore can't consult `LocalIsSynthwave` directly). Using
    // `SideEffect` ensures the write only happens after a *successful* composition, so
    // intermediate recompositions from previews or parallel compositions don't flicker the
    // global state. The CompositionLocal itself remains the source of truth for any
    // composable consumer; this SideEffect only mirrors it for the non-composable Shape path.
    androidx.compose.runtime.SideEffect { _isSynthwaveActive.value = synthwaveMode }

    // Material You dynamic color is a platform seam: null on desktop/web means
    // the branch falls through to the brand/contrast schemes below.
    val platformDynamic = if (accentColorSwatch == "dynamic" && dynamicColor &&
        contrastLevel == ContrastLevel.DEFAULT && !isTv
    ) {
        dynamicPlatformColorScheme(effectiveDarkTheme)
    } else {
        null
    }

    val colorScheme = when {
        synthwaveMode -> {
            remember(synthwaveAccent) { getSynthwaveColorScheme(synthwaveAccent) }
        }
        soothingMode -> {
            remember(soothingAccent, effectiveDarkTheme) { getSoothingColorScheme(soothingAccent, effectiveDarkTheme) }
        }
        monochromeMode -> {
            remember(effectiveDarkTheme) { getMonochromeColorScheme(effectiveDarkTheme) }
        }
        platformDynamic != null -> {
            if (effectiveOledMode && contrastLevel != ContrastLevel.HIGH) {
                platformDynamic.withOledSurfaces()
            } else {
                platformDynamic
            }
        }
        accentColorSwatch == "dynamic" && effectiveDarkTheme -> {
            val base = when (contrastLevel) {
                ContrastLevel.MEDIUM -> MediumContrastDarkColorScheme
                ContrastLevel.HIGH -> HighContrastDarkColorScheme
                ContrastLevel.DEFAULT -> DarkColorScheme
            }
            // High Contrast takes precedence over OLED surfaces.
            if (effectiveOledMode && contrastLevel != ContrastLevel.HIGH) base.withOledSurfaces() else base
        }
        accentColorSwatch == "dynamic" -> when (contrastLevel) {
            ContrastLevel.MEDIUM -> MediumContrastLightColorScheme
            ContrastLevel.HIGH -> HighContrastLightColorScheme
            ContrastLevel.DEFAULT -> LightColorScheme
        }
        else -> {
            val swatch = if (accentColorSwatch == "dynamic") {
                AccentColorSwatch.BRAND
            } else {
                AccentColorSwatch.entries.find { it.name.lowercase() == accentColorSwatch } ?: AccentColorSwatch.BRAND
            }
            val seedColor = Color(if (effectiveDarkTheme) swatch.darkColor else swatch.lightColor)
            ColorGenerator.generateColorScheme(
                seedColor = seedColor,
                style = colorStyle,
                darkTheme = effectiveDarkTheme,
                oledMode = effectiveOledMode,
                contrastLevel = contrastLevel
            )
        }
    }

    val shapes = when {
        synthwaveMode -> {
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            )
        }
        soothingMode -> {
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            )
        }
        monochromeMode -> {
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            )
        }
        else -> {
            DefaultShapes
        }
    }

    val typography = when {
        synthwaveMode -> SynthwaveTypography
        soothingMode -> SoothingTypography
        monochromeMode -> MonochromeTypography
        isTv -> TvTypography
        else -> JellyPlayTypography
    }.let { base ->
        if (appFontScale != AppFontScale.DEFAULT) {
            base.copy(
                displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * appFontScale.scale),
                displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * appFontScale.scale),
                displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * appFontScale.scale),
                headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * appFontScale.scale),
                headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * appFontScale.scale),
                headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * appFontScale.scale),
                titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * appFontScale.scale),
                titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * appFontScale.scale),
                titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * appFontScale.scale),
                bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * appFontScale.scale),
                bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * appFontScale.scale),
                bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * appFontScale.scale),
                labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * appFontScale.scale),
                labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * appFontScale.scale),
                labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * appFontScale.scale),
            )
        } else base
    }

    CompositionLocalProvider(
        LocalJellyPlayColorScheme provides colorScheme,
        LocalJellyPlayTypography provides typography,
        LocalJellyPlayShapes provides shapes,
        LocalIsLightTheme provides isLightColor(colorScheme.background),
        LocalExtendedColors provides ExtendedColors(),
        LocalIsSynthwave provides synthwaveMode,
        LocalIsSoothingTheme provides soothingMode,
        LocalIsMonochromeTheme provides monochromeMode,
        LocalThemeVariant provides when {
            synthwaveMode -> ThemeVariant.SYNTHWAVE
            soothingMode -> ThemeVariant.SOOTHING
            monochromeMode -> ThemeVariant.MONOCHROME
            else -> ThemeVariant.STANDARD
        },
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            motionScheme = if (performanceMode || reduceMotion) ReducedMotionScheme else ExpressiveMotionScheme,
            content = content,
        )
    }
}

private fun ColorScheme.withOledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

/**
 * Wraps [content] so it renders as a dark "chrome zone" while preserving the user's accent.
 *
 * On-video control bars float over arbitrary video content, so they always need a dark scrim with
 * light text/icons — regardless of the app theme. Without this, a light ambient theme resolves
 * `onSurface` to dark colors, producing invisible dark-on-dark text against the dark scrim. This
 * wrapper forces the dark [ColorScheme] (so `onSurface`/`onSurfaceVariant` become light), sets
 * [androidx.compose.material3.LocalContentColor] to a light foreground (MaterialTheme alone does
 * not), and reports `LocalIsLightTheme = false` so helpers like [playerScrimColor] resolve to the
 * dark variant. The accent (`primary`/`secondary`/`tertiary`) is copied from the ambient scheme so
 * play buttons and sliders stay on-brand.
 *
 * Scope this to the control-bar subtree only — drawers and other surfaces that should respect the
 * app theme must sit outside it.
 */
@Composable
fun PlayerDarkTheme(content: @Composable () -> Unit) {
    val ambient = MaterialTheme.colorScheme
    val darkScheme = remember(ambient.primary, ambient.secondary, ambient.tertiary) {
        darkColorScheme(
            primary = ambient.primary,
            onPrimary = ambient.onPrimary,
            primaryContainer = ambient.primaryContainer,
            onPrimaryContainer = ambient.onPrimaryContainer,
            secondary = ambient.secondary,
            onSecondary = ambient.onSecondary,
            tertiary = ambient.tertiary,
            onTertiary = ambient.onTertiary,
            // Carry the ambient theme's remaining roles through so error/outline/
            // surfaceVariant (and the surface containers) don't fall back to M3
            // defaults that clash with Synthwave/brand chrome.
            error = ambient.error,
            onError = ambient.onError,
            errorContainer = ambient.errorContainer,
            onErrorContainer = ambient.onErrorContainer,
            outline = ambient.outline,
            outlineVariant = ambient.outlineVariant,
            surfaceVariant = ambient.surfaceVariant,
            onSurfaceVariant = ambient.onSurfaceVariant,
            surfaceTint = ambient.surfaceTint,
            inverseSurface = ambient.inverseSurface,
            inverseOnSurface = ambient.inverseOnSurface,
            inversePrimary = ambient.inversePrimary,
            scrim = ambient.scrim,
        )
    }
    MaterialTheme(colorScheme = darkScheme) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides darkScheme.onSurface,
            LocalIsLightTheme provides false,
        ) {
            content()
        }
    }
}

fun getSynthwaveColorScheme(accent: String): ColorScheme {
    val primaryColor = when (accent.lowercase()) {
        "cyan" -> Color(0xFF00F0FF)
        "violet" -> Color(0xFF9D00FF)
        "orange" -> Color(0xFFFF5E00)
        else -> Color(0xFFFF007F) // magenta
    }
    val secondaryColor = when (accent.lowercase()) {
        "cyan" -> Color(0xFFFF007F)
        "violet" -> Color(0xFFFF007F)
        "orange" -> Color(0xFF00F0FF)
        else -> Color(0xFF00F0FF) // magenta -> cyan
    }
    val tertiaryColor = when (accent.lowercase()) {
        "cyan" -> Color(0xFF9D00FF)
        "violet" -> Color(0xFF00F0FF)
        "orange" -> Color(0xFFFF007F)
        else -> Color(0xFFFFE600) // magenta -> yellow
    }

    return darkColorScheme(
        primary = primaryColor,
        onPrimary = Color(0xFF0C061A),
        primaryContainer = primaryColor.copy(alpha = 0.2f),
        onPrimaryContainer = primaryColor,
        secondary = secondaryColor,
        onSecondary = Color(0xFF0C061A),
        secondaryContainer = secondaryColor.copy(alpha = 0.2f),
        onSecondaryContainer = secondaryColor,
        tertiary = tertiaryColor,
        onTertiary = Color(0xFF0C061A),
        tertiaryContainer = tertiaryColor.copy(alpha = 0.2f),
        onTertiaryContainer = tertiaryColor,
        background = Color(0xFF0C061A),
        onBackground = Color(0xFFF5EEFC),
        surface = Color(0xFF120926),
        onSurface = Color(0xFFF5EEFC),
        surfaceVariant = Color(0xFF241542),
        onSurfaceVariant = Color(0xFFD8C8F0),
        outline = primaryColor,
        outlineVariant = Color(0xFF462C75),
        surfaceContainerLowest = Color(0xFF06030D),
        surfaceContainerLow = Color(0xFF0F0720),
        surfaceContainer = Color(0xFF160C2D),
        surfaceContainerHigh = Color(0xFF20123E),
        surfaceContainerHighest = Color(0xFF2B1952),
    )
}

fun getSoothingColorScheme(accent: String, isDark: Boolean): ColorScheme {
    val accentColor = if (isDark) {
        when (accent.lowercase()) {
            "lavender" -> Color(0xFFB4A7FF)
            "sage" -> Color(0xFF7ECFA0)
            "coral" -> Color(0xFFFF8A80)
            "amber" -> Color(0xFFFFD180)
            "rose" -> Color(0xFFFF80AB)
            else -> Color(0xFF6CACDE)
        }
    } else {
        when (accent.lowercase()) {
            "lavender" -> Color(0xFF8B7FE8)
            "sage" -> Color(0xFF4CAF6E)
            "coral" -> Color(0xFFE85D5D)
            "amber" -> Color(0xFFE8A43A)
            "rose" -> Color(0xFFE85A8A)
            else -> Color(0xFF1877F2)
        }
    }

    return if (isDark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color(0xFF0D1117),
            primaryContainer = accentColor.copy(alpha = 0.18f),
            onPrimaryContainer = accentColor,
            secondary = accentColor.copy(alpha = 0.7f),
            onSecondary = Color(0xFFE6EDF3),
            background = Color(0xFF0D1117),
            onBackground = Color(0xFFE6EDF3),
            surface = Color(0xFF161B22),
            onSurface = Color(0xFFE6EDF3),
            surfaceVariant = Color(0xFF21262D),
            onSurfaceVariant = Color(0xFFB1BAC4),
            outline = Color(0xFF30363D),
            outlineVariant = Color(0xFF21262D),
            surfaceContainerLowest = Color(0xFF0A0E14),
            surfaceContainerLow = Color(0xFF161B22),
            surfaceContainer = Color(0xFF1C2128),
            surfaceContainerHigh = Color(0xFF21262D),
            surfaceContainerHighest = Color(0xFF2D333B),
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.12f),
            onPrimaryContainer = accentColor,
            secondary = accentColor.copy(alpha = 0.65f),
            onSecondary = Color.White,
            background = Color(0xFFF0F2F5),
            onBackground = Color(0xFF1C1E21),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1C1E21),
            surfaceVariant = Color(0xFFE4E6EB),
            onSurfaceVariant = Color(0xFF606770),
            outline = Color(0xFFCED0D4),
            outlineVariant = Color(0xFFD8DADF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF0F2F5),
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFE4E6EB),
            surfaceContainerHighest = Color(0xFFCED0D4),
        )
    }
}

fun getMonochromeColorScheme(isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF1C1C1C),
            onPrimaryContainer = Color.White,
            secondary = Color.White,
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF1E1E1E),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFFE51937),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFF2E0509),
            onTertiaryContainer = Color(0xFFFFB4B5),
            error = Color(0xFFE51937),
            onError = Color.White,
            errorContainer = Color(0xFF4C0008),
            onErrorContainer = Color(0xFFFFDADA),
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF0C0C0C),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFFCCCCCC),
            outline = Color(0xFF2C2C2C),
            outlineVariant = Color(0xFF1A1A1A),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF262626),
        )
    } else {
        lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEEEEEE),
            onPrimaryContainer = Color.Black,
            secondary = Color.Black,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEAEAEA),
            onSecondaryContainer = Color.Black,
            tertiary = Color(0xFFE51937),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFECEE),
            onTertiaryContainer = Color(0xFFE51937),
            error = Color(0xFFE51937),
            onError = Color.White,
            errorContainer = Color(0xFFFFDADA),
            onErrorContainer = Color(0xFF4C0008),
            background = Color.White,
            onBackground = Color.Black,
            surface = Color(0xFFF9F9F9),
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFEAEAEA),
            onSurfaceVariant = Color(0xFF333333),
            outline = Color(0xFFCCCCCC),
            outlineVariant = Color(0xFFE0E0E0),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF9F9F9),
            surfaceContainer = Color(0xFFF0F0F0),
            surfaceContainerHigh = Color(0xFFE5E5E5),
            surfaceContainerHighest = Color(0xFFDCDCDC),
        )
    }
}
