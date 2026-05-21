package com.raulshma.jellyplay.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

private val KidsLightColorScheme = lightColorScheme(
    primary = kids_theme_light_primary,
    onPrimary = kids_theme_light_onPrimary,
    primaryContainer = kids_theme_light_primaryContainer,
    onPrimaryContainer = kids_theme_light_onPrimaryContainer,
    secondary = kids_theme_light_secondary,
    onSecondary = kids_theme_light_onSecondary,
    secondaryContainer = kids_theme_light_secondaryContainer,
    onSecondaryContainer = kids_theme_light_onSecondaryContainer,
    tertiary = kids_theme_light_tertiary,
    onTertiary = kids_theme_light_onTertiary,
    tertiaryContainer = kids_theme_light_tertiaryContainer,
    onTertiaryContainer = kids_theme_light_onTertiaryContainer,
    error = kids_theme_light_error,
    onError = kids_theme_light_onError,
    errorContainer = kids_theme_light_errorContainer,
    onErrorContainer = kids_theme_light_onErrorContainer,
    background = kids_theme_light_background,
    onBackground = kids_theme_light_onBackground,
    surface = kids_theme_light_surface,
    onSurface = kids_theme_light_onSurface,
    surfaceVariant = kids_theme_light_surfaceVariant,
    onSurfaceVariant = kids_theme_light_onSurfaceVariant,
    outline = kids_theme_light_outline,
    outlineVariant = kids_theme_light_outlineVariant,
    surfaceContainerLowest = kids_theme_light_surfaceContainerLowest,
    surfaceContainerLow = kids_theme_light_surfaceContainerLow,
    surfaceContainer = kids_theme_light_surfaceContainer,
    surfaceContainerHigh = kids_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = kids_theme_light_surfaceContainerHighest,
)

private val KidsDarkColorScheme = darkColorScheme(
    primary = kids_theme_dark_primary,
    onPrimary = kids_theme_dark_onPrimary,
    primaryContainer = kids_theme_dark_primaryContainer,
    onPrimaryContainer = kids_theme_dark_onPrimaryContainer,
    secondary = kids_theme_dark_secondary,
    onSecondary = kids_theme_dark_onSecondary,
    secondaryContainer = kids_theme_dark_secondaryContainer,
    onSecondaryContainer = kids_theme_dark_onSecondaryContainer,
    tertiary = kids_theme_dark_tertiary,
    onTertiary = kids_theme_dark_onTertiary,
    tertiaryContainer = kids_theme_dark_tertiaryContainer,
    onTertiaryContainer = kids_theme_dark_onTertiaryContainer,
    error = kids_theme_dark_error,
    onError = kids_theme_dark_onError,
    errorContainer = kids_theme_dark_errorContainer,
    onErrorContainer = kids_theme_dark_onErrorContainer,
    background = kids_theme_dark_background,
    onBackground = kids_theme_dark_onBackground,
    surface = kids_theme_dark_surface,
    onSurface = kids_theme_dark_onSurface,
    surfaceVariant = kids_theme_dark_surfaceVariant,
    onSurfaceVariant = kids_theme_dark_onSurfaceVariant,
    outline = kids_theme_dark_outline,
    outlineVariant = kids_theme_dark_outlineVariant,
    surfaceContainerLowest = kids_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = kids_theme_dark_surfaceContainerLow,
    surfaceContainer = kids_theme_dark_surfaceContainer,
    surfaceContainerHigh = kids_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = kids_theme_dark_surfaceContainerHighest,
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
    extraSmall = ShapeCache.smooth12,
    small = ShapeCache.smooth14,
    medium = ShapeCache.smooth20,
    large = ShapeCache.smooth28,
    extraLarge = ShapeCache.smooth36,
)

private val KidsShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun JellyPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    oledMode: Boolean = false,
    kidsMode: Boolean = false,
    isTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveDarkTheme = darkTheme || isTv
    val effectiveOledMode = oledMode && effectiveDarkTheme

    val colorScheme = when {
        kidsMode -> if (effectiveDarkTheme) KidsDarkColorScheme else KidsLightColorScheme
        dynamicColor && !isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDarkTheme) {
                val dynamic = dynamicDarkColorScheme(context)
                if (effectiveOledMode) dynamic.withOledSurfaces() else dynamic
            } else {
                dynamicLightColorScheme(context)
            }
        }
        effectiveOledMode -> OledColorScheme
        effectiveDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapes = if (kidsMode) KidsShapes else DefaultShapes

    val typography = if (isTv) {
        TvTypography
    } else {
        JellyPlayTypography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        motionScheme = ExpressiveMotionScheme,
        content = content,
    )
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
