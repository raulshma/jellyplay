package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.image.rememberMediaImageMemoryCacheKey
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * How BOOK items (which ship no 16:9 backdrop — their one asset is the
 * portrait cover) render the hero band:
 *  - [NONE] — non-book items; the hero shows the 16:9 backdrop.
 *  - [BLURRED] — the cover over-scaled 1.25× and blurred 28dp (the book-app
 *    hero on tablet/TV/desktop, where the poster card also shows).
 *  - [SHARP] — the cover cropped edge-to-edge with no blur (compact phones,
 *    where the hero replaces the poster card entirely).
 */
internal enum class BookCoverHero {
    NONE,
    BLURRED,
    SHARP,
}

/**
 * The full-bleed parallax backdrop behind the detail content: animated image
 * swap on item change, an optional muted autoplay trailer, and the scrim
 * gradient that fades the backdrop into the body background.
 *
 * Extracted verbatim from the former `DetailContent` in `MediaDetailScreen.kt`;
 * behaviour is identical.
 */
@Composable
internal fun DetailBackdrop(
    targetBackdropId: String,
    backdropBlurHash: String?,
    getBackdropUrl: (String) -> String,
    trailers: List<DetailUiState.TrailerEntry>,
    preferences: DetailPreferences,
    scrollState: DetailScrollState,
    modifier: Modifier = Modifier,
    /**
     * On-disk backdrop path for the current item (DetailAssets.backdropPath).
     * Preferred over the server [getBackdropUrl] so a LOCAL origin renders
     * without a network round-trip. Null for REMOTE (behavior unchanged).
     */
    localBackdropPath: String? = null,
    coverHero: BookCoverHero = BookCoverHero.NONE,
    /**
     * D22: the resolved server ThemeVideo stream URL (the `themeVideo`
     * enrichment's write; null = none resolved). When present — and the
     * platform can render ambient video — it is preferred OVER the YouTube
     * embed (the muted ExoPlayer surface degrades to the static backdrop on
     * desktop, where the URL is simply never fetched).
     */
    themeVideoStreamUrl: String? = null,
    /**
     * D22: the play-target focus port (DetailContent provides the instance
     * the Play button writes). Null = no gating wired — the non-gated
     * posture, so a call site that never heard of the port keeps today's
     * immediate mount.
     */
    trailerFocusPreview: TrailerFocusPreviewState? = null,
) {
    val baseBackdropHeight = scrollState.baseBackdropHeight
    val backdropHeight = scrollState.backdropHeight
    // Capture the State refs once so the graphicsLayer/drawBehind lambdas read
    // snapshot state *inside* the lambda. This defers invalidation to the draw
    // phase only — the backdrop subtree is NOT recomposed every scroll frame.
    val scrollOffsetState = scrollState.scrollOffsetState
    val scrollFractionState = scrollState.scrollFractionState
    val backgroundColorState = scrollState.backgroundColorState

    // D3: the autoplay pick runs over the MERGED trailer list. With
    // `preferLocalTrailers` on, a local clip wins the pick — but only the
    // pick: the embed below is the WebView YouTube mount and cannot play a
    // local file, and the ThemeVideo mount below takes only the server
    // ThemeVideo clip, so a Local pick leaves the hero on the backdrop image
    // (mounting local file clips stays with the later video-hero wave).
    // Off (or no local) → the historical YouTube trailer/teaser rule.
    val trailerPick = remember(trailers, preferences.preferLocalTrailers) {
        pickBackdropTrailerEntry(trailers, preferences.preferLocalTrailers)
    }
    val trailerVideo = (trailerPick as? DetailUiState.TrailerEntry.Remote)?.video
    var autoplayEmbedFailed by remember(targetBackdropId) { mutableStateOf(false) }

    // D22: the focus-gated mount decision (a no-op off TV — the pure
    // machine mounts immediately whenever `trailerAutoplay` holds and the
    // focus-preview semantic is not gating). The port being absent counts
    // as NOT focus-gated: an unwired call site keeps today's mount.
    val isTv = LocalTvMode.current
    val windowVisible = rememberWindowVisible()
    val mountPreview = trailerPreviewShouldMount(
        trailerAutoplayEnabled = preferences.trailerAutoplay,
        focusPreviewEnabled = preferences.trailerPreviewOnFocus,
        isTv = isTv,
        playTargetFocused = trailerFocusPreview?.playTargetFocused ?: false,
        windowVisible = windowVisible,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(backdropHeight)
            .graphicsLayer {
                val scrollOffset = scrollOffsetState.value
                translationY = -scrollOffset * 0.5f
                alpha = 1f - (scrollFractionState.value * 0.8f)
            },
    ) {
        // Capture in composable scope; AnimatedContent's transitionSpec is not composable.
        val backdropFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val backdropScaleIn = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val backdropFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val backdropScaleOut = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedContent(
            targetState = targetBackdropId,
            transitionSpec = {
                fadeIn(
                    animationSpec = backdropFadeIn,
                ) + scaleIn(
                    initialScale = 1.035f,
                    animationSpec = backdropScaleIn,
                ) togetherWith fadeOut(
                    animationSpec = backdropFadeOut,
                ) + scaleOut(
                    targetScale = 0.99f,
                    animationSpec = backdropScaleOut,
                )
            },
            label = "detailBackdrop",
        ) { backdropId ->
            val backdropModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f + (scrollOffsetState.value * 0.001f).coerceAtLeast(0f)
                    // Blur mode over-scales so the blur edges never show.
                    val coverBoost = if (coverHero == BookCoverHero.BLURRED) 1.25f else 1f
                    scaleX = scale * coverBoost
                    scaleY = scale * coverBoost
                }
            // Prefer the on-disk backdrop (DetailAssets.backdropPath, or the
            // book cover's poster path) for a LOCAL origin; fall back to the
            // server URL for REMOTE or when no local path was resolved.
            // The placeholder handoff reuses the wide-row tile's cached decode
            // (the resume/next-up cards show the SAME server backdrop URL at
            // MediaImage's 384² default) while this full-bleed decode runs, so
            // the artwork stays on screen across the navigation instead of
            // dropping to the blurHash. Key built from the DISPLAY url: a local
            // origin hands off from the offline row's identical local path.
            MediaImage(
                url = localBackdropPath ?: getBackdropUrl(backdropId),
                contentDescription = null,
                blurHash = backdropBlurHash,
                placeholderMemoryCacheKey = rememberMediaImageMemoryCacheKey(
                    url = localBackdropPath ?: getBackdropUrl(backdropId),
                    size = CoilSize(384, 384),
                ),
                modifier = backdropModifier
                    .then(
                        if (coverHero == BookCoverHero.BLURRED) Modifier.blur(28.dp)
                        else Modifier,
                    ),
                contentScale = ContentScale.Crop,
                // Full-bleed hero backdrop: decode large enough for 4K TV width
                // (3840 px). The default 384² produces visible blur on TV.
                // performanceModeAware stays true: MediaImage now tiers the
                // clamp so a ≥1080 request decodes at 768² in performance mode
                // (crisp full-screen on phones) instead of 256².
                size = CoilSize(1920, 1080),
            )
        }

        // Trailer/clip preview rendered outside AnimatedContent so it composes
        // independently when the merged trailer list loads asynchronously.
        // D22: the whole preview family is gated by [mountPreview] — autoplay
        // off mounts nothing, TV focus-gating unmounts on focus-exit (the
        // embed's own lifecycle observer handles an app backgrounding while
        // mounted). Within the family the server ThemeVideo clip is
        // preferred over the embed (both are muted, crop-to-fill fills).
        // Mounted only for a Remote pick (the embed cannot play a local
        // clip — see the pick note above).
        if (mountPreview) {
            // mountPreview already carries the `trailerAutoplay` leg (the
            // pure machine returns false without it), so both branches below
            // are the autoplay-on posture.
            val themeVideoUrl = themeVideoStreamUrl
            val trailerKey = trailerVideo?.key
            when {
                themeVideoUrl != null ->
                    AmbientThemeVideoSurface(
                        url = themeVideoUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 1f + (scrollOffsetState.value * 0.001f).coerceAtLeast(0f)
                                scaleX = scale
                                scaleY = scale
                            },
                    )

                trailerVideo != null && trailerKey != null && !autoplayEmbedFailed ->
                    InlineTrailerPlayerHost(
                        videoKey = trailerKey,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 1f + (scrollOffsetState.value * 0.001f).coerceAtLeast(0f)
                                scaleX = scale
                                scaleY = scale
                            },
                        muted = true,
                        showControls = false,
                        autoplay = true,
                        focusable = false,
                        cropToFill = true,
                        onEmbedFailed = { autoplayEmbedFailed = true },
                    )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // drawWithCache rebuilds the scrim Brush only when the snapshot-
                // read background colour actually changes, instead of allocating
                // a fresh gradient on every draw pass of the colour animation.
                .drawWithCache {
                    val backgroundColor = backgroundColorState.value
                    // Bottom-anchored fade, identical on every window size class.
                    // The scroll spacer puts body content at baseBackdropHeight
                    // - 150dp on every tier, so starting the gradient 50dp earlier
                    // dissolves the artwork into the background colour under the
                    // arriving content on phones, tablets and TV alike. Landscape
                    // tablets previously got a full-height 0.5→1.0 wash instead,
                    // which read as a dimmed backdrop rather than a fade.
                    val scrimBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.4f),
                            backgroundColor.copy(alpha = 0.9f),
                            backgroundColor,
                        ),
                        startY = (baseBackdropHeight - 200.dp).toPx(),
                        endY = backdropHeight.toPx(),
                    )
                    onDrawBehind {
                        drawRect(scrimBrush)
                    }
                },
        )
    }
}
