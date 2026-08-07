package com.raulshma.jellyplay.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.library.R

@Composable
fun StudioDetailScreen(
    studioName: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StudioDetailViewModel = hiltViewModel(),
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val serverHealth by LocalServerHealth.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = items.loadState.refresh is LoadState.Loading,
        hasError = items.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
        serverHealth = serverHealth,
    )

    // Long-press / TV-Menu quick actions for studio cards
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember { { item: MediaItem -> studioQuickActions(item) } },
        executeAction = remember(viewModel, onItemClick) {
            { item: MediaItem, action: QuickAction ->
                when (action) {
                    QuickAction.PLAY -> onItemClick(item.id)
                    QuickAction.MARK_WATCHED -> viewModel.markItemPlayed(item, true)
                    QuickAction.MARK_UNWATCHED -> viewModel.markItemPlayed(item, false)
                    QuickAction.DETAILS -> onItemClick(item.id)
                    else -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions.
    var tvFocusedItem by remember { mutableStateOf<MediaItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    tvFocusedItem?.let { quickActionController.show(it) }
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionController) {
        JellyPlayScreenScaffold(
            title = studioName,
            onBack = onBack,
            actions = {
                HeaderStatusIndicator(
                    status = headerStatus,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val refreshState = items.loadState.refresh) {
                is LoadState.Loading -> {
                    ScreenLoadingState()
                }
                is LoadState.Error -> {
                    ErrorScreen(
                        message = refreshState.error.localizedMessage
                            ?: stringResource(R.string.library_failed_to_load_items),
                        onRetry = { items.refresh() },
                    )
                }
                is LoadState.NotLoading -> {
                    if (items.itemCount == 0) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Movie,
                            title = stringResource(R.string.library_no_items_found),
                        )
                    } else {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = LocalTvMode.current
                        val spanCount = when {
                            adaptiveInfo.windowSizeClass == com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Expanded -> 5
                            adaptiveInfo.windowSizeClass == com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Medium -> 4
                            else -> 3
                        }
                        TvFocusableGrid(
                            itemCount = items.itemCount,
                            key = items.safeItemKey { it.id },
                            columns = GridCells.Fixed(spanCount),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentType = { "mediaItem" },
                            onFocusedIndexChange = { index -> items[index]?.let { tvFocusedItem = it } },
                        ) { index, itemModifier ->
                            val item = items[index]
                            if (item != null) {
                                val progress = item.progressFraction()
                                PosterCard(
                                    item = item,
                                    imageUrl = viewModel.getImageUrl(item.id),
                                    onClick = { onItemClick(item.id) },
                                    showProgress = progress != null && progress > 0f,
                                    progressPercent = progress ?: 0f,
                                    modifier = itemModifier,
                                )
                            }
                        }
                    }
                }
            }

            when (val appendState = items.loadState.append) {
                is LoadState.Loading -> {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
                is LoadState.Error -> {
                    Text(
                        text = appendState.error.localizedMessage
                            ?: stringResource(R.string.library_failed_to_load_more),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
                is LoadState.NotLoading -> Unit
            }
        }
        } // close scaffold content lambda
        } // close CompositionLocalProvider
    } // close Box
    MediaQuickActionHost(quickActionController)
} // close StudioDetailScreen

/**
 * Which quick actions apply to a studio card Playable entries
 * get play / mark-watched / details.
 */
private fun studioQuickActions(item: MediaItem): List<QuickAction> = buildList {
    when (item.mediaType) {
        MediaType.MOVIE, MediaType.SERIES, MediaType.SEASON, MediaType.EPISODE,
        MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST,
        MediaType.MUSIC_VIDEO, MediaType.COLLECTION, MediaType.LIVE_TV, MediaType.CHANNEL -> {
            add(QuickAction.PLAY)
            add(if (item.isPlayed) QuickAction.MARK_UNWATCHED else QuickAction.MARK_WATCHED)
            add(QuickAction.DETAILS)
        }
        else -> Unit
    }
}
