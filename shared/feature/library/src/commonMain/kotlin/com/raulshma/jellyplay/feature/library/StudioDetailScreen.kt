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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshEffect
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.QuickActionAdapter
import com.raulshma.jellyplay.core.ui.components.QuickActionIntakeHost
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.model.progressFraction
import com.raulshma.jellyplay.core.ui.components.rememberQuickActionIntake
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_items
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_more
import com.raulshma.jellyplay.feature.library.generated.resources.library_no_items_found

@Composable
fun StudioDetailScreen(
    studioName: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StudioDetailViewModel = koinViewModel(),
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val serverHealth by LocalServerHealth.current.collectAsStateWithLifecycle()

    DeferredRefreshEffect(viewModel.deferredRefresher)

    val headerStatus = resolveHeaderStatus(
        isLoading = items.loadState.refresh is LoadState.Loading,
        hasError = items.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
        serverHealth = serverHealth,
    )

    // Collected (not read as a .value snapshot inside the intake's
    // isDownloaded lambda) so the resolver is rebuilt when the downloaded set
    // changes — a download completing flips the card's Download↔Remove-download
    // action without waiting for an unrelated recomposition. The set is
    // distinct-collapsed upstream, so active transfers don't churn it.
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    // Long-press / TV-Menu quick actions for studio cards, on the shared
    // intake (core/ui — see QuickActionIntake). Download / Remove download
    // ride the same intake as the library grid (#147).
    val quickActionIntake = rememberQuickActionIntake(
        scope = MediaQuickActionScope.LIBRARY,
        includeDownload = true,
        isDownloaded = remember(downloadedIds) {
            { item: MediaItem -> downloadedIds.contains(item.id) }
        },
        adapter = remember(viewModel, onItemClick) {
            QuickActionAdapter(
                onPlay = { item -> onItemClick(item.id) },
                onOpenDetail = { item -> onItemClick(item.id) },
                onMarkPlayed = viewModel::markItemPlayed,
                onDownload = { item -> viewModel.downloadItem(item, onOpenDetail = onItemClick) },
                onRemoveDownload = viewModel::removeItemDownload,
            )
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    quickActionIntake.openFocusedItem()
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionIntake.controller) {
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
                            ?: stringResource(Res.string.library_failed_to_load_items),
                        onRetry = { items.refresh() },
                    )
                }
                is LoadState.NotLoading -> {
                    if (items.itemCount == 0) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Movie,
                            title = stringResource(Res.string.library_no_items_found),
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
                            onFocusedIndexChange = { index -> items[index]?.let { quickActionIntake.tvFocusedItem = it } },
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
                            ?: stringResource(Res.string.library_failed_to_load_more),
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
    // Quick-action sheet + remove-download confirm — the shared intake hosts
    // both (removal only ever deletes the local download; the server copy is
    // untouched).
    QuickActionIntakeHost(quickActionIntake)
} // close StudioDetailScreen
