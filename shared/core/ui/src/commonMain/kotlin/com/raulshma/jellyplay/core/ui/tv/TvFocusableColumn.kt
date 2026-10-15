package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec

/**
 * Default cache window for TV vertical lists — prefetch 2× the viewport ahead and 0.5× behind so
 * rows are ready before the D-pad scroll reaches them. Same tuning as [TvRowCacheWindow] /
 * [TvGridCacheWindow]; default Compose cache windows cause visible "popping" during fast D-pad
 * scrolling.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val TvColumnCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 0.5f)

/**
 * TV focus contract for vertical card/row lists — the 1-D vertical analogue of [TvFocusableItemRow]
 * (horizontal) and [TvFocusableGrid] (2-D). Wraps a [LazyColumn] with the canonical modifier order
 * (`tvFocusRestorer(fallback) → focusGroup` — the restorer must wrap the group's focus target, not
 * sit inside it), a saveable focused-index memory clamped to the live item count, and a one-shot
 * initial-focus grab once data arrives.
 *
 * Use this for any full-screen vertical list of focusable cards/rows (LiveTV sub-screens, track
 * lists, ArrQueue, calendar). For a short, static, non-scrolling column prefer a plain `Column`
 * wrapped in `focusGroup()` — this primitive is for scrollable lists where cursor memory and
 * layout-race-safe focus restoration matter.
 *
 * Pass [extraContent] for headers/footers (section titles, load-more indicators). [itemContent]
 * receives the per-item focus modifier (attach it to the focusable row/card root so focus
 * restoration + index tracking work).
 *
 * The contract this replaces (the manual wiring every vertical list used to redo):
 * 1. `LazyColumn(modifier = Modifier.tvFocusRestorer(fallback).focusGroup())`
 * 2. `TvGrabInitialFocus` / `RequestOrRestoreFocus` to grab focus once data lands
 * 3. Per-item `Modifier.focusRequester(fallback).onFocusChanged { … }` to track the focused index
 *
 * @param requestInitialFocus default `true` — vertical lists are usually the sole focus root of
 * their screen, so grab focus once data arrives. Set `false` when the list shares the screen with
 * other focusables (e.g. an app bar) and the page drives initial focus itself.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T> TvFocusableColumn(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(cacheWindow = TvColumnCacheWindow),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = true,
    /** Bump to reset the cursor and re-grab focus after the backing data is replaced (filter change). */
    refreshGeneration: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    focusRequester: FocusRequester? = null,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    extraContent: LazyListScope.() -> Unit = {},
    itemContent: @Composable (
        index: Int,
        item: T,
        modifier: Modifier,
    ) -> Unit,
) {
    TvFocusablePagingColumn(
        itemCount = items.size,
        key = { index -> key(items[index]) },
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        initialIndex = initialIndex,
        requestInitialFocus = requestInitialFocus,
        refreshGeneration = refreshGeneration,
        onFocusedIndexChange = onFocusedIndexChange,
        focusRequester = focusRequester,
        contentType = { index -> contentType(index, items[index]) },
        extraContent = extraContent,
    ) { index, itemModifier ->
        itemContent(index, items[index], itemModifier)
    }
}

/**
 * Count-based paging analogue of [TvFocusableColumn] for `LazyPagingItems`-style APIs where items
 * are addressed by index and may be null while placeholders load — the same relationship
 * [TvFocusableGrid]'s count overload has to its list overload. Identical focus contract: canonical
 * modifier order, saveable cursor memory, one-shot initial grab, and a [refreshGeneration] re-grab
 * after the data is replaced.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvFocusablePagingColumn(
    itemCount: Int,
    key: (index: Int) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(cacheWindow = TvColumnCacheWindow),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = true,
    /** Bump to reset the cursor and re-grab focus after the backing data is replaced (filter change). */
    refreshGeneration: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    focusRequester: FocusRequester? = null,
    contentType: (index: Int) -> Any? = { null },
    extraContent: LazyListScope.() -> Unit = {},
    itemContent: @Composable (index: Int, itemModifier: Modifier) -> Unit,
) {
    val isTv = LocalTvMode.current
    val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
    // The shared focus cursor (fallback leaf + clamp/initial-grab/refresh-regrab
    // ladder + tags) — TvFocusCursor.kt owns the whole contract.
    val cursor = rememberTvFocusCursor(
        layout = TvFocusLayout.Column,
        initialIndex = initialIndex,
        isTv = isTv,
        requestInitialFocus = requestInitialFocus,
        itemCount = itemCount,
        refreshGeneration = refreshGeneration,
        scrollToItem = { state.scrollToItem(it) },
    )
    val fallbackFocusRequester = cursor.fallback

    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        modifier = if (isTv) {
            // Order matters: tvFocusRestorer must wrap the column's focus GROUP
            // (before focusGroup). Placed after, it attaches to the items instead
            // of the group, and outer aggregated restorers clobber these
            // single-slot onEnter/onExit hooks. Group entry restores the
            // last-focused row, falling back to the tracked focusedIndex row.
            modifier
                .tvFocusRestorer(fallbackFocusRequester)
                .focusGroup()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        } else {
            modifier
        },
    ) {
        items(
            count = itemCount,
            key = key,
            contentType = contentType,
        ) { index ->
            // animateItem (LazyItemScope) animates placement so reorders/removals glide instead of
            // snapping. Placement spec routes through lazyItemPlacementSpec() so it snaps under
            // reduce-motion. Keys are required for placement tracking; the `key` param above is
            // mandatory in this contract.
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
