package com.raulshma.jellyplay.feature.music.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.raulshma.jellyplay.feature.music.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    onItemClick: (String) -> Unit,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tracks") })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val refreshState = tracks.loadState.refresh) {
                is LoadState.Loading -> {
                    LoadingScreen(modifier = Modifier.padding(padding))
                }
                is LoadState.Error -> {
                    ErrorScreen(
                        message = refreshState.error.localizedMessage ?: "Failed to load tracks",
                        onRetry = { tracks.refresh() },
                        modifier = Modifier.padding(padding),
                    )
                }
                is LoadState.NotLoading -> {
                    if (tracks.itemCount == 0) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No tracks found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = padding.calculateTopPadding() + 8.dp,
                                bottom = padding.calculateBottomPadding() + 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = tracks.itemCount,
                                key = tracks.itemKey { it.id },
                                contentType = { "mediaItem" },
                            ) { index ->
                                val track = tracks[index]
                                if (track != null) {
                                    TrackRow(
                                        name = track.name,
                                        artist = track.albumArtist,
                                        album = track.album,
                                        duration = track.runTimeTicks?.let { ticks ->
                                            formatDuration(ticks / 10_000)
                                        },
                                        imageUrl = viewModel.getImageUrl(track.id),
                                        onClick = { onItemClick(track.id) },
                                        blurHash = track.blurHashes.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (val appendState = tracks.loadState.append) {
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
