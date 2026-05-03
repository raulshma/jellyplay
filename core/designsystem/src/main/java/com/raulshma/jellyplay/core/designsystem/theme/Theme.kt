package com.raulshma.jellyplay.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
)

private val DefaultShapes = Shapes()

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
    kidsMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        kidsMode -> if (darkTheme) KidsDarkColorScheme else KidsLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapes = if (kidsMode) KidsShapes else DefaultShapes

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JellyPlayTypography,
        shapes = shapes,
        content = content,
    )
}
