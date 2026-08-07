package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.feature.music.R

@Composable
fun <T : Any> PagedGrid(
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 150.dp,
    spacing: Dp = 12.dp,
    emptyIcon: ImageVector = Tabler.Outline.Search,
    emptyTitle: String = stringResource(R.string.music_nothing_found),
    itemContent: @Composable (T, Modifier) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val refreshState = items.loadState.refresh) {
            is LoadState.Loading -> ScreenLoadingState()
            is LoadState.Error -> ErrorScreen(
                // was a raw "Failed to load" literal — reuse the
                // localized string so the error path matches the empty path.
                message = refreshState.error.localizedMessage
                    ?: stringResource(R.string.music_failed_load),
                onRetry = { items.refresh() },
            )
            is LoadState.NotLoading -> {
                if (items.itemCount == 0) {
                    ScreenEmptyState(
                        icon = emptyIcon,
                        title = emptyTitle,
                    )
                } else {
                    TvFocusableGrid(
                        itemCount = items.itemCount,
                        key = items.safeItemKey(itemKey),
                        columns = GridCells.Adaptive(gridMin),
                        contentPadding = PaddingValues(contentPad),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxSize(),
                        contentType = { "pagedItem" },
                    ) { index, itemModifier ->
                        val item = items[index]
                        if (item != null) {
                            itemContent(item, itemModifier)
                        }
                    }
                }
            }
        }
    }
}
