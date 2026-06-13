package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    itemContent: @Composable (
        index: Int,
        item: T,
        modifier: Modifier,
    ) -> Unit,
) {
    val isTv = LocalTvMode.current
    val gridFocusRequester = remember { FocusRequester() }
    val fallbackFocusRequester = remember { FocusRequester() }
    val currentOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)
    var focusedIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            focusedIndex = focusedIndex.coerceIn(0, items.lastIndex)
        }
    }

    // focusRestorer(fallback) and focusProperties { onEnter } only react to focus *entering* the
    // group from outside; neither proactively grabs focus. So when a grid's data arrives after first
    // composition, grab focus on the focused cell once (mirrors Wholphin's page-owned
    // LaunchedEffect(Unit) { gridFocusRequester.requestFocus() } inside the Success branch).
    LaunchedEffect(items.isNotEmpty()) {
        if (isTv && requestInitialFocus && items.isNotEmpty() && !initialFocusRequested) {
            initialFocusRequested = true
            fallbackFocusRequester.tryRequestFocus("tv_grid_init")
        }
    }

    val currentFocusedIndex = if (items.isEmpty()) 0 else focusedIndex.coerceIn(0, items.lastIndex)

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
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) },
            contentType = { _, item -> contentType(item) },
        ) { index, item ->
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
            itemContent(index, item, itemModifier)
        }
    }
}
