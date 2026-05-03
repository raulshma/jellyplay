package com.raulshma.jellyplay.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.feature.library.components.LibraryFilterSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onItemClick: (String) -> Unit,
    onSmartPlaylistsClick: () -> Unit = {},
    onMoodPlaylistsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()

    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onSmartPlaylistsClick) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Smart Playlists",
                        )
                    }
                    IconButton(onClick = onMoodPlaylistsClick) {
                        Icon(
                            imageVector = Icons.Default.Mood,
                            contentDescription = "Mood Playlists",
                        )
                    }
                    IconButton(onClick = { viewModel.toggleShowFilters() }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filters",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading && pagedItems.itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null && pagedItems.itemCount == 0) {
            ErrorScreen(
                message = error!!,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (folders.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFolder == null,
                                onClick = { viewModel.selectFolder(null) },
                                label = { Text("All") },
                            )
                        }
                        items(folders.size) { index ->
                            val folder = folders[index]
                            FilterChip(
                                selected = selectedFolder?.id == folder.id,
                                onClick = { viewModel.selectFolder(folder) },
                                label = { Text(folder.name) },
                            )
                        }
                    }
                }

                if (filters.mediaTypes.isNotEmpty() || filters.genres.isNotEmpty() || filters.playedStatus != PlayedStatus.ALL) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filters.mediaTypes.forEach { mediaType ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    viewModel.updateFilters(filters.copy(mediaTypes = filters.mediaTypes - mediaType))
                                },
                                label = { Text(mediaType.name) },
                            )
                        }
                        if (filters.playedStatus != PlayedStatus.ALL) {
                            InputChip(
                                selected = true,
                                onClick = {
                                    viewModel.updateFilters(filters.copy(playedStatus = PlayedStatus.ALL))
                                },
                                label = { Text(filters.playedStatus.displayName) },
                            )
                        }
                        if (filters.mediaTypes.isNotEmpty() || filters.genres.isNotEmpty() || filters.playedStatus != PlayedStatus.ALL) {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.clearFilters() },
                                label = { Text("Clear all") },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 8.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = pagedItems.itemCount,
                            key = pagedItems.itemKey { it.id },
                        ) { index ->
                            val item = pagedItems[index]
                            if (item != null) {
                                PosterCard(
                                    item = item,
                                    imageUrl = viewModel.getImageUrl(item.id),
                                    onClick = { onItemClick(item.id) },
                                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                                        (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                                    } else 0f,
                                )
                            }
                        }
                    }

                    when (val appendState = pagedItems.loadState.append) {
                        is LoadState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                            )
                        }
                        is LoadState.Error -> {
                            Text(
                                text = appendState.error.localizedMessage ?: "Failed to load more items",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                            )
                        }
                        is LoadState.NotLoading -> Unit
                    }

                    when (val refreshState = pagedItems.loadState.refresh) {
                        is LoadState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        is LoadState.Error -> {
                            ErrorScreen(
                                message = refreshState.error.localizedMessage ?: "Failed to load items",
                                onRetry = { pagedItems.refresh() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        is LoadState.NotLoading -> Unit
                    }
                }
            }
        }
    }

    if (showFilters) {
        LibraryFilterSheet(
            currentFilters = filters,
            genres = genres,
            onApply = { newFilters ->
                viewModel.updateFilters(newFilters)
                viewModel.toggleShowFilters()
            },
            onDismiss = { viewModel.toggleShowFilters() },
        )
    }
}
