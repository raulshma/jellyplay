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
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
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
    val fallbackFocusRequester = remember { FocusRequester() }
    val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
    val focusedIndexState = rememberInt(initialIndex)
    var focusedIndex by focusedIndexState
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            focusedIndex = focusedIndex.coerceIn(0, itemCount - 1)
        }
    }

    // focusRestorer(fallback) and focusProperties { onEnter } only react to focus *entering* the
    // group from outside; neither proactively grabs focus. So when a column's data arrives after
    // first composition, grab focus on the focused row once. The scroll-to-item before the grab
    // ensures the saved row is composed before requesting focus (returning from a full-screen
    // route can otherwise leave the saved index off-screen and the grab silently no-ops).
    LaunchedEffect(itemCount > 0) {
        if (isTv && requestInitialFocus && itemCount > 0 && !initialFocusRequested) {
            initialFocusRequested = true
            state.scrollToItem(focusedIndex.coerceIn(0, itemCount - 1))
            fallbackFocusRequester.tryRequestFocus("tv_column_init")
        }
    }

    // A completed refresh (filter/folder change) replaces the data under the old cursor: the caller
    // scrolls to 0, so the saved index no longer matches what's composed. Reset the cursor and
    // re-grab — without this, focus falls out of the column during the reload and the drawer rail
    // captures it. Mirrors the same handling in [TvFocusableGrid].
    LaunchedEffect(refreshGeneration) {
        if (isTv && refreshGeneration > 0 && itemCount > 0) {
            focusedIndex = 0
            state.scrollToItem(0)
            for (attempt in 1..3) {
                withFrameNanos { }
                if (fallbackFocusRequester.tryRequestFocus("tv_column_refresh")) break
            }
        }
    }

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
                    focusedIndexState = focusedIndexState,
                    itemCount = itemCount,
                    index = index,
                    fallbackFocusRequester = fallbackFocusRequester,
                    onFocused = {
                        focusedIndex = index
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
 * Renders [content] with [fallbackFocusRequester] attached to the item at [index] only while the
 * tracked focus cursor points at [index]. The [focusedIndexState] read must stay inside this leaf —
 * reading it in the container body recomposes every composed item on each focus move.
 */
@Composable
private fun TvItemFocusFallback(
    focusedIndexState: IntState,
    itemCount: Int,
    index: Int,
    fallbackFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    animatedModifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val currentFocusedIndex = focusedIndexState.intValue.coerceIn(0, itemCount - 1)
    val itemModifier = (if (currentFocusedIndex == index) {
        Modifier.focusRequester(fallbackFocusRequester)
    } else {
        Modifier
    })
        .onFocusChanged {
            if (it.isFocused || it.hasFocus) {
                onFocused()
            }
        }
        .then(animatedModifier)
    content(itemModifier)
}
