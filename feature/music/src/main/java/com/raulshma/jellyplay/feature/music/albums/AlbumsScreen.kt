package com.raulshma.jellyplay.feature.music.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun AlbumsScreen(
    onItemClick: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = albums.loadState.refresh is LoadState.Loading,
        hasError = albums.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
    )

    JellyPlayScreenScaffold(
        title = "Albums",
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val refreshState = albums.loadState.refresh) {
                is LoadState.Loading -> {
                    ScreenLoadingState()
                }
                is LoadState.Error -> {
                    ErrorScreen(
                        message = refreshState.error.localizedMessage ?: "Failed to load albums",
                        onRetry = { albums.refresh() },
                    )
                }
                is LoadState.NotLoading -> {
                    if (albums.itemCount == 0) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Disc,
                            title = "No albums found",
                        )
                    } else {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = LocalTvMode.current
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(adaptiveInfo.gridCellSize(isTv)),
                            contentPadding = PaddingValues(
                                start = adaptiveInfo.contentPadding(isTv),
                                end = adaptiveInfo.contentPadding(isTv),
                                top = 8.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = albums.itemCount,
                                key = albums.itemKey { it.id },
                                contentType = { "mediaItem" },
                            ) { index ->
                                val album = albums[index]
                                if (album != null) {
                                    AlbumCard(
                                        name = album.name,
                                        artist = album.albumArtist,
                                        year = album.year,
                                        imageUrl = viewModel.getImageUrl(album.id),
                                        onClick = { onItemClick(album.id) },
                                        blurHash = album.blurHashes.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (val appendState = albums.loadState.append) {
                is LoadState.Loading -> {
                    LoadingScreen(
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                is LoadState.Error -> {
                    Text(
                        text = appendState.error.localizedMessage ?: "Failed to load more",
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
    }
}
