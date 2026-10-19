package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform seam: whether this platform can render the ambient hero clip at
 * all. Android/TV (the ExoPlayer-backed [com.raulshma.jellyplay.core.data.playback.AmbientVideoPlayer]
 * via the Koin single) → true; desktop → false, so the loader never fetches
 * and the hero stays static (the desktop posture).
 */
internal expect val heroVideoPlatformCapable: Boolean

/**
 * Platform seam: the muted looping hero-clip surface. Android actual hosts
 * a `TextureView` bound to the process-wide
 * [com.raulshma.jellyplay.core.data.playback.AmbientVideoPlayer] single;
 * desktop actual renders nothing (static hero). The composable is dumb —
 * every start/stop decision lives with the caller (HomeHero's gates), keyed
 * by [url] so a replaced candidate's dispose cannot kill the incoming clip.
 */
@Composable
internal expect fun AmbientHeroVideoSurface(url: String, modifier: Modifier = Modifier)

/**
 * The fallback leg: the muted YouTube trailer embed (the same embed the
 * detail backdrop uses). Android actual delegates to core:ui's WebView
 * `InlineTrailerPlayer` — muted, no controls, cropped to fill, and
 * [onEmbedFailed] folds back to the static hero; desktop actual reports the
 * failure immediately (the same fails-fast posture the details screen's
 * desktop actual has). Only mounted behind [heroVideoPlatformCapable], so
 * the desktop actual is belt-and-braces.
 */
@Composable
internal expect fun HeroTrailerEmbedSurface(
    videoKey: String,
    modifier: Modifier = Modifier,
    onEmbedFailed: () -> Unit = {},
)
