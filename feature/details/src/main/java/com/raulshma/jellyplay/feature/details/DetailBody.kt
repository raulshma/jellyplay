package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.image.MediaImage
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Landscape (two-column) layout: poster + action buttons in the left rail, body
 * in the right. Extracted verbatim from the former `DetailContent` branch;
 * behaviour is identical. [DetailContentBody] is invoked once with
 * `showActionButtons = false` because actions live in the left rail.
 */
@Composable
internal fun DetailBodyLandscape(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    scrollState: DetailScrollState,
    contentVisible: Boolean,
    contentFocusRequester: FocusRequester,
    isAudio: Boolean,
    isAlbum: Boolean,
    isExpanded: Boolean,
    item: MediaItem?,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    val detail = state.detail

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = adaptiveInfo.contentPadding(isTv)),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left column: poster + action buttons
        AnimatedVisibility(
            visible = contentVisible,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            Column(
                modifier = Modifier.width(220.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FadingItem {
                    // Prefer the on-disk poster (DetailAssets.posterPath) for a
                    // LOCAL origin before the server getImageUrl fallback.
                    val posterImageUrl = state.assets.posterPath
                        ?: callbacks.getImageUrl(state.itemId)
                    DetailPoster(
                        itemId = state.itemId,
                        primaryBlurHash = item?.blurHashes?.primary,
                        getImageUrl = { posterImageUrl },
                        posterWidth = 220.dp,
                        posterHeight = 330.dp,
                        contentAlpha = scrollState.contentAlpha,
                        sharedElementKey = "poster_${state.itemId}",
                        smoothShape = ShapeCache.smooth12,
                        decodeWidth = 660,
                        decodeHeight = 990,
                    )
                }
                if (detail != null && item != null) {
                    DetailActionButtons(
                        state = state,
                        callbacks = callbacks,
                        vertical = true,
                        contentFocusRequester = contentFocusRequester,
                    )
                }
            }
        }

        if (detail != null && item != null) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier.weight(1f),
            ) {
                DetailContentBody(
                    state = state,
                    callbacks = callbacks,
                    contentFocusRequester = contentFocusRequester,
                    showActionButtons = false,
                )
            }
        } else {
            val err = state.loadState as? DetailUiLoadState.Error
            if (err != null) {
                ErrorScreen(
                    message = err.message,
                    onRetry = if (err.accessDenied) null else callbacks.onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Portrait (single-column) layout: overlapping poster above the body.
 * Extracted verbatim from the former `DetailContent` branch; behaviour is
 * identical. [DetailContentBody] is invoked once with `showActionButtons = true`
 * because actions render inside the body.
 */
@Composable
internal fun DetailBodyPortrait(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    scrollState: DetailScrollState,
    contentVisible: Boolean,
    contentFocusRequester: FocusRequester,
    isAudio: Boolean,
    isAlbum: Boolean,
    isExpanded: Boolean,
    isTv: Boolean,
    item: MediaItem?,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val detail = state.detail

    Column(modifier = Modifier.padding(top = 0.dp)) {
        val posterWidth = when {
            isTv -> 160.dp
            isExpanded -> 140.dp
            else -> 120.dp
        }
        val posterHeight = posterWidth * 1.2f
        val overlap = 40.dp
        val boxHeight = posterHeight - overlap

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(boxHeight),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = adaptiveInfo.contentPadding(isTv))
                    .offset(y = -overlap),
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    FadingItem(
                        modifier = Modifier
                            .width(posterWidth)
                            .requiredHeight(posterHeight),
                    ) {
                        MediaImage(
                            // Prefer the on-disk poster (DetailAssets.posterPath)
                            // for a LOCAL origin before the server fallback.
                            url = state.assets.posterPath
                                ?: callbacks.getImageUrl(state.itemId),
                            contentDescription = null,
                            blurHash = item?.blurHashes?.primary,
                            // Portrait poster (~120 dp × 3× ≈ 432 px) — decode a
                            // right-sized thumbnail instead of the default 512×512
                            // (or larger) bitmap.
                            size = coil3.size.Size(480, 600),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(ShapeCache.smooth8)
                                .graphicsLayer { alpha = scrollState.contentAlpha },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (detail != null && item != null) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                DetailContentBody(
                    state = state,
                    callbacks = callbacks,
                    contentFocusRequester = contentFocusRequester,
                )
            }
        } else {
            val err = state.loadState as? DetailUiLoadState.Error
            if (err != null) {
                ErrorScreen(
                    message = err.message,
                    onRetry = if (err.accessDenied) null else callbacks.onRetry,
                )
            }
        }
    }
}

/**
 * The shared-transition-aware poster used by the landscape left rail. Kept here
 * (not inlined) so the `ExperimentalSharedTransitionApi` opt-in is scoped to
 * one call site.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DetailPoster(
    itemId: String,
    primaryBlurHash: String?,
    getImageUrl: (String) -> String,
    posterWidth: androidx.compose.ui.unit.Dp,
    posterHeight: androidx.compose.ui.unit.Dp,
    contentAlpha: Float,
    sharedElementKey: String,
    smoothShape: androidx.compose.ui.graphics.Shape,
    decodeWidth: Int,
    decodeHeight: Int,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedPosterModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = sharedElementKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else Modifier
    MediaImage(
        url = getImageUrl(itemId),
        contentDescription = null,
        blurHash = primaryBlurHash,
        // posterWidth × 3× decode width; 2/3 aspect → proportional height.
        // Avoids under-sized decode (blur on TV) and over-sized decode (memory
        // waste on phone) from the default size.
        size = coil3.size.Size(decodeWidth, decodeHeight),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(smoothShape)
            .graphicsLayer { alpha = contentAlpha }
            .then(sharedPosterModifier),
        contentScale = ContentScale.Crop,
    )
}
