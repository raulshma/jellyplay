package com.raulshma.jellyplay.feature.music.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    onItemClick: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = tracks.loadState.refresh is LoadState.Loading,
        hasError = tracks.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Tracks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Tabler.Outline.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                        status = headerStatus,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val refreshState = tracks.loadState.refresh) {
                    is LoadState.Loading -> {
                        LoadingScreen()
                    }
                    is LoadState.Error -> {
                        ErrorScreen(
                            message = refreshState.error.localizedMessage ?: "Failed to load tracks",
                            onRetry = { tracks.refresh() },
                        )
                    }
                    is LoadState.NotLoading -> {
                        if (tracks.itemCount == 0) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No tracks found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                        } else {
                            val adaptiveInfo = LocalAdaptiveInfo.current
                            val isTv = LocalTvMode.current
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = adaptiveInfo.bottomPadding(isTv),
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
                                                com.raulshma.jellyplay.core.ui.components.formatDurationMs(ticks / 10_000)
                                            },
                                            imageUrl = viewModel.getImageUrl(track.id),
                                            onClick = {
                                                val loadedTracks = tracks.itemSnapshotList.items
                                                val clickIndex = loadedTracks.indexOfFirst { it.id == track.id }
                                                viewModel.playAll(loadedTracks, if (clickIndex >= 0) clickIndex else 0)
                                                onItemClick(track.id)
                                            },
                                            onAddToQueue = { viewModel.addToQueue(track) },
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
}
