package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch
import com.raulshma.jellyplay.core.ui.components.SeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.mouseScroll

/**
 * A single display-only (non-user-scrollable) horizontal row of [SeerrMediaCard]s.
 *
 * This unifies the previously duplicated rendering for the Seerr "Discover"
 * grid rows and the *arr "Recently Grabbed / Coming Soon" row, which were
 * near-identical: same `LazyRow` + `CompositionLocalProvider` + `SeerrMediaCard`
 * + onClick `when(mediaType)` block. The card onClick / request lambdas are
 * memoized once per row so they are not reallocated per-item per-recomposition.
 *
 * @param items the Seerr/TMDB-keyed cards to render in this row.
 * @param itemWidth computed card width (the row is laid out so all items share it).
 * @param rowHorizontalPadding outer horizontal padding + vertical centering pad.
 * @param spacing gap between cards.
 * @param backgroundColor row background color.
 * @param homeBackdropEnabled when true the ambient backdrop is active, so the
 *  row stays transparent to let it show through (mirrors the media rows); when
 *  false the row paints the flat [backgroundColor].
 * @param clippingEnabled when true, clips items to the row bounds (experimental card clipping).
 * @param seerrCardLoadingState per-card loading state (start/stop on prefetch).
 * @param seerrPrefetch prefetches Seerr details before navigating to the request flow.
 * @param onSeerrItemClick invoked after prefetch completes — opens the Seerr item.
 * @param onSeerrRequest invoked when the card's request action is tapped.
 */
@Composable
internal fun SeerrDiscoverRow(
    items: List<SeerrSearchItem>,
    itemWidth: Dp,
    rowHorizontalPadding: Dp,
    spacing: Dp,
    backgroundColor: androidx.compose.ui.graphics.Color,
    homeBackdropEnabled: Boolean,
    clippingEnabled: Boolean,
    seerrCardLoadingState: SeerrCardLoadingState,
    seerrPrefetch: (Int, String, () -> Unit) -> Unit,
    onSeerrItemClick: (Int, String) -> Unit,
    onSeerrRequest: (SeerrSearchItem) -> Unit,
) {
    // Memoize the per-card lambdas once per row. Previously these were allocated
    // inside the `items {}` block, so every card got a fresh lambda (with the
    // `when(mediaType)` block) on every recomposition.
    val onCardClick = remember(seerrCardLoadingState, seerrPrefetch, onSeerrItemClick) {
        { item: SeerrSearchItem ->
            val mediaType = normalizeSeerrMediaType(item.mediaType)
            seerrCardLoadingState.startLoading(item.id)
            seerrPrefetch(item.id, mediaType) {
                seerrCardLoadingState.stopLoading(item.id)
                onSeerrItemClick(item.id, mediaType)
            }
        }
    }
    val onCardRequestClick = remember(onSeerrRequest) {
        { item: SeerrSearchItem -> onSeerrRequest(item) }
    }

    CompositionLocalProvider(
        LocalSeerrCardLoadingState provides seerrCardLoadingState,
        LocalSeerrPrefetch provides seerrPrefetch,
        // Home is a no-peek surface: every home media card long-presses into the
        // quick-action sheet, so the press-and-hold preview is suppressed here.
        // These TMDB-keyed cards have no server quick actions, so long-press is
        // simply inert on them rather than opening a different affordance.
        LocalMediaPreviewController provides null,
    ) {
        // userScrollEnabled stays false (the row is display-only for touch),
        // but the state is hoisted so the desktop mouse-drag / wheel modifiers
        // can still scroll it — on desktop this row was otherwise unscrollable.
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .tvFocusRestorer()
                .then(if (clippingEnabled) Modifier.clipToBounds() else Modifier)
                // Stay transparent over the ambient backdrop (matches the media
                // rows); otherwise paint the flat background colour.
                .then(if (homeBackdropEnabled) Modifier else Modifier.background(backgroundColor))
                .padding(horizontal = rowHorizontalPadding, vertical = spacing / 2)
                .mouseScroll(rowState, Orientation.Horizontal),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            userScrollEnabled = false,
        ) {
            items(
                count = items.size,
                key = { idx -> items[idx].id },
                contentType = { "seerrCard" },
            ) { idx ->
                val seerrItem = items[idx]
                SeerrMediaCard(
                    item = seerrItem,
                    imageUrl = seerrItem.posterUrl,
                    isLoading = seerrCardLoadingState.isLoading(seerrItem.id),
                    clipToShape = clippingEnabled,
                    onClick = { onCardClick(seerrItem) },
                    onRequestClick = { onCardRequestClick(seerrItem) },
                    modifier = Modifier.animateItem(placementSpec = lazyItemPlacementSpec()).width(itemWidth),
                )
            }
        }
    }
}

/**
 * Normalizes an *arr media type for the detail routes: case-folded `movie` /
 * `tv`, everything else passed through. Internal so the test asserts THIS
 * function instead of a local copy of the rule.
 */
internal fun normalizeSeerrMediaType(raw: String): String = when {
    raw.equals("movie", ignoreCase = true) -> "movie"
    raw.equals("tv", ignoreCase = true) -> "tv"
    else -> raw
}
