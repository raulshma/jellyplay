package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec

/**
 * Default cache window for TV horizontal rows — prefetch 2× the viewport ahead and 0.5× behind.
 * Default Compose cache windows cause visible "popping" of cards during fast D-pad scrolling.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val TvRowCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 0.5f)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T> TvFocusableItemRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(cacheWindow = TvRowCacheWindow),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    initialIndex: Int = 0,
    requestInitialFocus: Boolean = false,
    onFocusedIndexChange: (Int) -> Unit = {},
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clipToBounds: Boolean = false,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
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
    var focusedIndex by rememberInt(initialIndex)
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
        modifier = (if (clipToBounds) modifier.clipToBounds() else modifier).let { m ->
            if (isTv) {
                m
                    .focusProperties {
                        onEnter = {
                            rowFocusRequester.tryRequestFocus("tv_row")
                        }
                    }
                    .focusGroup()
                    .tvFocusRestorer(fallbackFocusRequester)
                    .focusRequester(rowFocusRequester)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .ifElse(
                        condition = onRowFocused != null,
                        ifTrueModifier = Modifier.onFocusChanged { if (it.hasFocus) onRowFocused?.invoke() },
                    )
            } else {
                m
            }
        },
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) },
            contentType = { index, item -> contentType(index, item) },
        ) { index, item ->
            // animateItem (LazyItemScope) animates placement so reorders/removals
            // glide instead of snapping. Placement spec routes through
            // lazyItemPlacementSpec() so it snaps under reduce-motion. Keys are
            // required for placement tracking; the `key` param above is mandatory
            // in TvFocusableItemRow's contract.
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
    }
}
