package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Platform Material You dynamic color. Android 12+ returns the wallpaper-derived
 * scheme; every other platform returns null and the theme falls through to the
 * brand/contrast schemes.
 */
@Composable
internal expect fun dynamicPlatformColorScheme(darkTheme: Boolean): ColorScheme?

/**
 * Artwork-driven palette for ambient theming, resolved from [imageUrl] through
 * the platform image stack. Android extracts swatches via Palette from a Coil
 * bitmap; desktop ports the same swatch classification over Skia pixels; web
 * returns null until its image pipeline lands (consumers already handle null).
 */
@Composable
expect fun rememberArtworkColors(imageUrl: String?): ArtworkColors?
