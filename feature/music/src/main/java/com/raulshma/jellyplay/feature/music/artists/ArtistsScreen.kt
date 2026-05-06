package com.raulshma.jellyplay.feature.music.artists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.feature.music.components.ArtistCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    onItemClick: (String) -> Unit,
    viewModel: ArtistsViewModel = hiltViewModel(),
) {
    val artists = viewModel.artists.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Artists") })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val refreshState = artists.loadState.refresh) {
                is LoadState.Loading -> {
                    LoadingScreen(modifier = Modifier.padding(padding))
                }
                is LoadState.Error -> {
                    ErrorScreen(
                        message = refreshState.error.localizedMessage ?: "Failed to load artists",
                        onRetry = { artists.refresh() },
                        modifier = Modifier.padding(padding),
                    )
                }
                is LoadState.NotLoading -> {
                    if (artists.itemCount == 0) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No artists found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = padding.calculateTopPadding() + 8.dp,
                                bottom = padding.calculateBottomPadding() + 8.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = artists.itemCount,
                                key = artists.itemKey { it.id },
                                contentType = { "mediaItem" },
                            ) { index ->
                                val artist = artists[index]
                                if (artist != null) {
                                    ArtistCard(
                                        name = artist.name,
                                        imageUrl = viewModel.getImageUrl(artist.id),
                                        onClick = { onItemClick(artist.id) },
                                        blurHash = artist.blurHashes.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (val appendState = artists.loadState.append) {
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
