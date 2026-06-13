package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
fun <T> TvFocusableItemRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = false,
    onFocusedIndexChange: (Int) -> Unit = {},
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    itemContent: @Composable (
        index: Int,
        item: T,
        modifier: Modifier,
    ) -> Unit,
) {
    val isTv = LocalTvMode.current
    val rowFocusRequester = remember { FocusRequester() }
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
    // group; neither proactively grabs focus. When requestInitialFocus is true, grab focus on the
    // focused cell the first time the row becomes non-empty (for single-row screens; multi-row
    // screens like Home should leave this false and drive focus from the page instead).
    // The scroll-to-item before the grab ensures the saved cell is composed before requesting focus.
    LaunchedEffect(items.isNotEmpty()) {
        if (isTv && requestInitialFocus && items.isNotEmpty() && !initialFocusRequested) {
            initialFocusRequested = true
            state.scrollToItem(focusedIndex.coerceIn(0, items.lastIndex))
            fallbackFocusRequester.tryRequestFocus("tv_row_init")
        }
    }

    val currentFocusedIndex = if (items.isEmpty()) 0 else focusedIndex.coerceIn(0, items.lastIndex)

    LazyRow(
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        modifier = if (isTv) {
            modifier
                .focusProperties {
                    onEnter = {
                        rowFocusRequester.tryRequestFocus("tv_row")
                    }
                }
                .focusGroup()
                .tvFocusRestorer(fallbackFocusRequester)
                .focusRequester(rowFocusRequester)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (onRowFocused != null) {
                        Modifier.onFocusChanged { if (it.hasFocus) onRowFocused.invoke() }
                    } else {
                        Modifier
                    },
                )
        } else {
            modifier
        },
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) },
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
