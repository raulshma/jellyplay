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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Paging-friendly TV focus grid. Drives an initial focus grab once data arrives (the contract that
 * `focusRestorer(fallback)` and `focusProperties { onEnter }` do not proactively satisfy), clamps
 * the saveable focused index to the live item count, and wires the container + per-item focus
 * modifiers in the correct order (`focusGroup → tvFocusRestorer(fallback) → focusRequester(grid)`).
 *
 * Pass [extraContent] for paged-append footers (load-more indicators) or other extra grid items.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvFocusableGrid(
    itemCount: Int,
    key: (index: Int) -> Any,
    columns: GridCells,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = true,
    state: LazyGridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    contentType: (index: Int) -> Any? = { null },
    onFocusedIndexChange: (Int) -> Unit = {},
    extraContent: LazyGridScope.() -> Unit = {},
    itemContent: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val isTv = LocalTvMode.current
    val gridFocusRequester = remember { FocusRequester() }
    val fallbackFocusRequester = remember { FocusRequester() }
    val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
    var focusedIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            focusedIndex = focusedIndex.coerceIn(0, itemCount - 1)
        }
    }

    // focusRestorer(fallback) and focusProperties { onEnter } only react to focus *entering* the
    // group from outside; neither proactively grabs focus. So when a grid's data arrives after first
    // composition, grab focus on the focused cell once (mirrors Wholphin's page-owned
    // LaunchedEffect(Unit) { gridFocusRequester.requestFocus() } inside the Success branch).
    // The scroll-to-item before the grab ensures the saved cell is actually composed — without it,
    // returning from a full-screen route (e.g. PhotoViewer) can leave the saved index off-screen,
    // the fallbackFocusRequester attached to nothing, and the grab silently no-ops.
    LaunchedEffect(itemCount > 0) {
        if (isTv && requestInitialFocus && itemCount > 0 && !initialFocusRequested) {
            initialFocusRequested = true
            val targetIndex = focusedIndex.coerceIn(0, itemCount - 1)
            state.scrollToItem(targetIndex)
            fallbackFocusRequester.tryRequestFocus("tv_grid_init")
        }
    }

    val currentFocusedIndex = if (itemCount == 0) 0 else focusedIndex.coerceIn(0, itemCount - 1)

    LazyVerticalGrid(
        columns = columns,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        modifier = if (isTv) {
            modifier
                .focusProperties {
                    onEnter = {
                        gridFocusRequester.tryRequestFocus("tv_grid")
                    }
                }
                .focusGroup()
                .tvFocusRestorer(fallbackFocusRequester)
                .focusRequester(gridFocusRequester)
        } else {
            modifier
        },
    ) {
        items(
            count = itemCount,
            key = key,
            contentType = contentType,
        ) { index ->
            val itemModifier = if (isTv) {
                Modifier
                    .then(
                        if (index == currentFocusedIndex) {
                            Modifier.focusRequester(fallbackFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .onFocusChanged {
                        if (it.isFocused || it.hasFocus) {
                            focusedIndex = index
                            currentOnFocusedIndexChange(index)
                        }
                    }
            } else {
                Modifier
            }
            itemContent(index, itemModifier)
        }
        extraContent()
    }
}

/**
 * List-backed convenience overload. Delegates to the paging [TvFocusableGrid] so both variants share
 * the same focus contract (initial-focus grab, saveable index clamping, correct modifier order).
 */
@Composable
fun <T> TvFocusableGrid(
    items: List<T>,
    key: (T) -> Any,
    columns: GridCells,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = true,
    state: LazyGridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex),
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
