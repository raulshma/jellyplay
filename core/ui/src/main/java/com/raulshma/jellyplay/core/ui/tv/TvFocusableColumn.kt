package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
 * (`focusGroup → tvFocusRestorer(fallback) → focusRequester(column)`), a saveable focused-index
 * memory clamped to the live item count, and a one-shot initial-focus grab once data arrives.
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
    val isTv = LocalTvMode.current
    val columnFocusRequester = remember { FocusRequester() }
    val fallbackFocusRequester = remember { FocusRequester() }
    val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
    var focusedIndex by rememberInt(initialIndex)
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            focusedIndex = focusedIndex.coerceIn(0, items.lastIndex)
        }
    }

    // focusRestorer(fallback) and focusProperties { onEnter } only react to focus *entering* the
    // group from outside; neither proactively grabs focus. So when a column's data arrives after
    // first composition, grab focus on the focused row once. The scroll-to-item before the grab
    // ensures the saved row is composed before requesting focus (returning from a full-screen
    // route can otherwise leave the saved index off-screen and the grab silently no-ops).
    LaunchedEffect(items.isNotEmpty()) {
        if (isTv && requestInitialFocus && items.isNotEmpty() && !initialFocusRequested) {
            initialFocusRequested = true
            state.scrollToItem(focusedIndex.coerceIn(0, items.lastIndex))
            fallbackFocusRequester.tryRequestFocus("tv_column_init")
        }
    }

    val currentFocusedIndex = if (items.isEmpty()) 0 else focusedIndex.coerceIn(0, items.lastIndex)

    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        modifier = if (isTv) {
            modifier
                .focusProperties {
                    onEnter = {
                        columnFocusRequester.tryRequestFocus("tv_column")
                    }
                }
                .focusGroup()
                .tvFocusRestorer(fallbackFocusRequester)
                .focusRequester(columnFocusRequester)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        } else {
            modifier
        },
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) },
            contentType = { index, item -> contentType(index, item) },
        ) { index, item ->
            // animateItem (LazyItemScope) animates placement so reorders/removals glide instead of
            // snapping. Placement spec routes through lazyItemPlacementSpec() so it snaps under
            // reduce-motion. Keys are required for placement tracking; the `key` param above is
            // mandatory in TvFocusableColumn's contract.
            val placementSpec = lazyItemPlacementSpec()
            val itemModifier = (if (isTv) {
                Modifier
                    .ifElse(index == currentFocusedIndex, Modifier.focusRequester(fallbackFocusRequester))
                    .onFocusChanged {
                        if (it.isFocused || it.hasFocus) {
                            focusedIndex = index
                            currentOnFocusedIndexChange(index)
                        }
                    }
            } else {
                Modifier
            }).animateItem(placementSpec = placementSpec)
            itemContent(index, item, itemModifier)
        }
        extraContent()
    }
}
