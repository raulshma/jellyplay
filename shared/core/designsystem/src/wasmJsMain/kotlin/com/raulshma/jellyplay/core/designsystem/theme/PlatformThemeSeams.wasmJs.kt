package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// Material You wallpaper color is Android-only; brand schemes apply instead.
@Composable
internal actual fun dynamicPlatformColorScheme(darkTheme: Boolean): ColorScheme? = null

// Desktop artwork palette lands with the desktop image pipeline (plan §V2);
// consumers already render the neutral fallback when this returns null.
@Composable
actual fun rememberArtworkColors(imageUrl: String?): ArtworkColors? = null
