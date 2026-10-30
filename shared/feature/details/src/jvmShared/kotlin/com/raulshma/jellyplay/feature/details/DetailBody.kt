package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.image.rememberMediaImageMemoryCacheKey
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Landscape (two-column) layout: poster + action buttons in the left rail, body
 * in the right. Extracted verbatim from the former `DetailContent` branch;
 * behaviour is identical. [DetailContentBody] is invoked once with
 * `showActionButtons = false` because actions live in the left rail.
 *
 * [promotedActions] is the promoted rail slice of the ⋮ options list — the fixed
 * promoted set resolved by the caller (which owns the display-preference gate
 * and the matching ⋮-menu suppression); empty = nothing renders under the
 * action buttons.
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
    promotedActions: List<MediaOption> = emptyList(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    val detail = state.core.detail

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
                    // LOCAL origin before the server fallback; the loaded item's
                    // id IS state.itemId, so the resolver tags the URL with the
                    // item's own Primary tag.
                    val posterImageUrl = state.core.assets.posterPath
                        ?: item?.let { callbacks.artwork.urls.poster(it) }
                        ?: ""
                    DetailPoster(
                        itemId = state.itemId,
                        primaryBlurHash = item?.blurHashes?.primary,
                        getImageUrl = { posterImageUrl },
                        posterWidth = 220.dp,
                        posterHeight = 330.dp,
                        contentAlphaState = scrollState.contentAlphaState,
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
                    // Promoted rail: the fixed option subset lifted out of
                    // the ⋮ menu (which suppresses exactly these while the rail
                    // shows them). Sits under the action buttons so the TV
                    // focus order reads poster → play → rail actions → body.
                    DetailPromotedActions(options = promotedActions)
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
            val err = state.core.loadState as? DetailUiLoadState.Error
            if (err != null) {
                ErrorScreen(
                    message = err.message,
                    onRetry = if (err.accessDenied) null else callbacks.screen.onRetry,
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
    val detail = state.core.detail

    // Books on compact phones: the hero backdrop already shows the cover
    // sharp edge-to-edge (BookCoverHero.SHARP), so the poster card would be
    // a duplicate. Tablet/TV/desktop keep the poster next to the blurred hero.
    val hideBookPoster = item?.mediaType == MediaType.BOOK && !isTv && !isExpanded

    Column(modifier = Modifier.padding(top = 0.dp)) {
        val posterWidth = when {
            isTv -> 160.dp
            isExpanded -> 140.dp
            else -> 120.dp
        }
        val posterHeight = posterWidth * 1.2f
        val overlap = 40.dp
        val boxHeight = posterHeight - overlap

        if (!hideBookPoster) {
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
                            // The list→detail handoff: the grid tile the user
                            // tapped (PosterCard) decoded this same poster URL
                            // at 360×540 — pass that request's memory-cache key
                            // so Coil paints the tile's cached bitmap as the
                            // placeholder while this larger decode runs. The
                            // loaded item's id IS state.itemId, so the resolver
                            // tags the URL with the item's own Primary tag.
                            val posterUrl = state.core.assets.posterPath
                                ?: item?.let { callbacks.artwork.urls.poster(it) }
                                ?: ""
                            MediaImage(
                                // Prefer the on-disk poster (DetailAssets.posterPath)
                                // for a LOCAL origin before the server fallback.
                                url = posterUrl,
                                contentDescription = null,
                                blurHash = item?.blurHashes?.primary,
                                placeholderMemoryCacheKey = rememberMediaImageMemoryCacheKey(
                                    url = posterUrl,
                                    size = coil3.size.Size(360, 540),
                                ),
                                // Portrait poster (~120 dp × 3× ≈ 432 px) — decode a
                                // right-sized thumbnail instead of the default 512×512
                                // (or larger) bitmap.
                                size = coil3.size.Size(480, 600),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(ShapeCache.smooth8)
                                    .graphicsLayer { alpha = scrollState.contentAlphaState.value },
                                contentScale = ContentScale.Crop,
                            )
                        }
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
            val err = state.core.loadState as? DetailUiLoadState.Error
            if (err != null) {
                ErrorScreen(
                    message = err.message,
                    onRetry = if (err.accessDenied) null else callbacks.screen.onRetry,
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
    /** Snapshot-read content fade; read inside the graphicsLayer below. */
    contentAlphaState: State<Float>,
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
    // The list→detail handoff (the portrait header poster's rationale): the
    // grid tile decoded this same poster URL at 360×540, so its memory-cache
    // key becomes the placeholder while this larger rail decode runs.
    MediaImage(
        url = getImageUrl(itemId),
        contentDescription = null,
        blurHash = primaryBlurHash,
        placeholderMemoryCacheKey = rememberMediaImageMemoryCacheKey(
            url = getImageUrl(itemId),
            size = coil3.size.Size(360, 540),
        ),
        // posterWidth × 3× decode width; 2/3 aspect → proportional height.
        // Avoids under-sized decode (blur on TV) and over-sized decode (memory
        // waste on phone) from the default size.
        size = coil3.size.Size(decodeWidth, decodeHeight),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(smoothShape)
            .graphicsLayer { alpha = contentAlphaState.value }
            .then(sharedPosterModifier),
        contentScale = ContentScale.Crop,
    )
}

/**
 * The landscape/TV detail rail's promoted actions: the fixed subset of
 * the ⋮ options list ([PromotedMediaOptionIds]) rendered as icon+label rows
 * under [DetailActionButtons], so the top actions escape the overflow sheet
 * where TV D-pad cost is highest. Availability logic is unchanged — an option
 * the menu disabled (e.g. an in-flight download row) stays disabled here.
 * Renders nothing when the caller resolved no promoted options (gate off /
 * none available).
 */
@Composable
internal fun DetailPromotedActions(
    options: List<MediaOption>,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option -> DetailPromotedAction(option = option) }
    }
}

/** One promoted rail row: the ⋮ entry's icon + label at the rail's button chrome. */
@Composable
private fun DetailPromotedAction(option: MediaOption) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            // A disabled option stays visible-but-inert (the TV sheet's
            // disabled-item treatment): no focus chrome, no click.
            .then(if (option.enabled) focusState.focusModifier else Modifier)
            .then(
                if (option.enabled) Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = option.enabled,
                onClick = option.onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .graphicsLayer { alpha = if (option.enabled) 1f else 0.38f },
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
