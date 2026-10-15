package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec

/**
 * Default cache window for TV grids — prefetch 2× the viewport ahead and 0.5× behind so paged cards
 * are ready before the D-pad scroll reaches them. Default
 * Compose cache windows cause visible "popping" of cards during fast D-pad scrolling; this kills it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val TvGridCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 0.5f)

/**
 * Paging-friendly TV focus grid. Drives an initial focus grab once data arrives (the contract that
 * `focusRestorer(fallback)` and `focusProperties { onEnter }` do not proactively satisfy), clamps
 * the saveable focused index to the live item count, and wires the container + per-item focus
 * modifiers in the correct order (`tvFocusRestorer(fallback) → focusGroup` — the restorer must wrap
 * the group's focus target, not sit inside it).
 *
 * Pass [extraContent] for paged-append footers (load-more indicators) or other extra grid items.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvFocusableGrid(
    itemCount: Int,
    key: (index: Int) -> Any,
    columns: GridCells,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = true,
    /** Bump to reset the cursor and re-grab focus after the backing data is replaced (filter/folder change). */
    refreshGeneration: Int = 0,
    state: LazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        cacheWindow = TvGridCacheWindow,
    ),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    contentType: (index: Int) -> Any? = { null },
    onFocusedIndexChange: (Int) -> Unit = {},
    extraContent: LazyGridScope.() -> Unit = {},
    itemContent: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val isTv = LocalTvMode.current
    val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
    // The shared focus cursor (fallback leaf + clamp/initial-grab/refresh-regrab
    // ladder + tags) — TvFocusCursor.kt owns the whole contract.
    val cursor = rememberTvFocusCursor(
        layout = TvFocusLayout.Grid,
        initialIndex = initialIndex,
        isTv = isTv,
        requestInitialFocus = requestInitialFocus,
        itemCount = itemCount,
        refreshGeneration = refreshGeneration,
        scrollToItem = { state.scrollToItem(it) },
    )
    val fallbackFocusRequester = cursor.fallback

    LazyVerticalGrid(
        columns = columns,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        modifier = if (isTv) {
            // Order matters: tvFocusRestorer must wrap the grid's focus GROUP
            // (before focusGroup). Placed after, it attaches to the grid's items
            // instead of the group, and any restorer/focusProperties aggregated
            // from an outer level clobbers these single-slot onEnter/onExit
            // hooks. With this order, group entry restores the last-focused
            // card, falling back to the tracked focusedIndex card.
            modifier
                .tvFocusRestorer(fallbackFocusRequester)
                .focusGroup()
        } else {
            modifier
        },
    ) {
        items(
            count = itemCount,
            key = key,
            contentType = contentType,
        ) { index ->
            // animateItem (LazyItemScope) animates placement so reorders/removals
            // glide instead of snapping. Placement spec routes through
            // lazyItemPlacementSpec() so it snaps under reduce-motion. Keys are
            // required for placement tracking; the `key` param above is mandatory
            // in TvFocusableGrid's contract.
            val placementSpec = lazyItemPlacementSpec()
            if (isTv) {
                TvItemFocusFallback(
                    cursor = cursor,
                    itemCount = itemCount,
                    index = index,
                    onFocused = {
                        cursor.focusedIndex = index
                        currentOnFocusedIndexChange(index)
                    },
                    animatedModifier = Modifier.animateItem(placementSpec = placementSpec),
                ) { itemModifier ->
                    itemContent(index, itemModifier)
                }
            } else {
                itemContent(index, Modifier.animateItem(placementSpec = placementSpec))
            }
        }
        extraContent()
    }
}

/**
 * List-backed convenience overload. Delegates to the paging [TvFocusableGrid] so both variants share
 * the same focus contract (initial-focus grab, saveable index clamping, correct modifier order).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T> TvFocusableGrid(
    items: List<T>,
    key: (T) -> Any,
    columns: GridCells,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = true,
    /** Bump to reset the cursor and re-grab focus after the backing data is replaced (filter/folder change). */
    refreshGeneration: Int = 0,
    state: LazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex,
        cacheWindow = TvGridCacheWindow,
    ),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    contentType: (T) -> Any? = { null },
    onFocusedIndexChange: (Int) -> Unit = {},
    extraContent: LazyGridScope.() -> Unit = {},
    itemContent: @Composable (
        index: Int,
        item: T,
        modifier: Modifier,
    ) -> Unit,
) {
    TvFocusableGrid(
        itemCount = items.size,
        key = { index -> key(items[index]) },
        columns = columns,
        modifier = modifier,
        initialIndex = initialIndex,
        requestInitialFocus = requestInitialFocus,
        refreshGeneration = refreshGeneration,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        contentType = { index -> contentType(items[index]) },
        onFocusedIndexChange = onFocusedIndexChange,
        extraContent = extraContent,
    ) { index, itemModifier ->
        itemContent(index, items[index], itemModifier)
    }
}
