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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer
import com.raulshma.jellyplay.core.ui.image.MediaImage

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
    relatedVideos: List<SeerrRelatedVideo>,
    preferences: DetailPreferences,
    scrollState: DetailScrollState,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    /**
     * On-disk backdrop path for the current item (DetailAssets.backdropPath).
     * Preferred over the server [getBackdropUrl] so a LOCAL origin renders
     * without a network round-trip. Null for REMOTE (behavior unchanged).
     */
    localBackdropPath: String? = null,
) {
    val baseBackdropHeight = scrollState.baseBackdropHeight
    val backdropHeight = scrollState.backdropHeight
    // Capture the State refs once so the graphicsLayer/drawBehind lambdas read
    // snapshot state *inside* the lambda. This defers invalidation to the draw
    // phase only — the backdrop subtree is NOT recomposed every scroll frame.
    val scrollOffsetState = scrollState.scrollOffsetState
    val scrollFractionState = scrollState.scrollFractionState
    val backgroundColorState = scrollState.backgroundColorState

    val trailerVideo = remember(relatedVideos) {
        relatedVideos.firstOrNull {
            it.site?.lowercase() == "youtube" &&
                (it.type?.lowercase() == "trailer" || it.type?.lowercase() == "teaser")
        } ?: relatedVideos.firstOrNull { it.site?.lowercase() == "youtube" }
    }
    var autoplayEmbedFailed by remember(targetBackdropId) { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isLandscapeExpanded = isExpanded && adaptiveInfo.isLandscape

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
                    scaleX = scale
                    scaleY = scale
                }
            // Prefer the on-disk backdrop (DetailAssets.backdropPath) for a LOCAL
            // origin; fall back to the server URL for REMOTE or when no local
            // path was resolved.
            MediaImage(
                url = localBackdropPath ?: getBackdropUrl(backdropId),
                contentDescription = null,
                blurHash = backdropBlurHash,
                modifier = backdropModifier,
                contentScale = ContentScale.Crop,
                // Full-bleed hero backdrop: decode large enough for 4K TV width
                // (3840 px). The default 384² produces visible blur on TV.
                // performanceModeAware stays true: MediaImage now tiers the
                // clamp so a ≥1080 request decodes at 768² in performance mode
                // (crisp full-screen on phones) instead of 256².
                size = CoilSize(1920, 1080),
            )
        }

        // Trailer player rendered outside AnimatedContent so it composes
        // independently when relatedVideos loads asynchronously.
        val playAutoplayTrailer = preferences.trailerAutoplay && trailerVideo != null && !autoplayEmbedFailed
        val trailerKey = trailerVideo?.key
        if (playAutoplayTrailer && trailerKey != null) {
            InlineTrailerPlayer(
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val backgroundColor = backgroundColorState.value
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isLandscapeExpanded) backgroundColor.copy(alpha = 0.5f) else Color.Transparent,
                                backgroundColor.copy(alpha = if (isLandscapeExpanded) 0.8f else 0.4f),
                                backgroundColor.copy(alpha = 0.9f),
                                backgroundColor,
                            ),
                            startY = if (isLandscapeExpanded) 0f else (baseBackdropHeight - 200.dp).toPx(),
                            endY = backdropHeight.toPx(),
                        ),
                    )
                },
        )
    }
}
