package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Desktop actual of the hero-video seam: desktop cannot render the
 * ambient clip — [heroVideoPlatformCapable] is false so the loader never
 * fetches, and the surface actual renders nothing. The hero stays the static
 * backdrop image.
 */
internal actual val heroVideoPlatformCapable: Boolean = false

@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun AmbientHeroVideoSurface(url: String, modifier: Modifier) {
    // Static hero: intentionally empty.
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun HeroTrailerEmbedSurface(
    videoKey: String,
    modifier: Modifier,
    onEmbedFailed: () -> Unit,
) {
    // Fails fast exactly like the details screen's desktop actual: report the
    // broken embed so the caller folds back to the static hero.
    androidx.compose.runtime.LaunchedEffect(videoKey) { onEmbedFailed() }
}
