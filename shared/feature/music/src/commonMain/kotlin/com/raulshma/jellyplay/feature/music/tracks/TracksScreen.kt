package com.raulshma.jellyplay.feature.music.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_more
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_tracks
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_tracks_found
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    onItemClick: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: TracksViewModel = koinViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = tracks.loadState.refresh is LoadState.Loading,
        hasError = tracks.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
    )

    // TV focus-on-launch: focus the first track once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = tracks.itemCount,
        tag = "tracks_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_tracks),
        onBack = onBack,
        actions = {
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                val sortFocusState = rememberTvFocusState()
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.then(sortFocusState.focusModifier).tvFocusIndicator(sortFocusState, CircleShape),
                ) {
                    Text(
                        text = stringResource(selectedSort.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    listOf(
                        MusicSortOption.NAME,
                        MusicSortOption.DATE_ADDED,
                        MusicSortOption.DATE_PLAYED,
                        MusicSortOption.RANDOM,
                    ).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            onClick = {
                                viewModel.setSort(option)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        PullToRefreshBox(
            isRefreshing = tracks.loadState.refresh is LoadState.Loading && tracks.itemCount > 0,
            onRefresh = { tracks.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0 -> {
                    ScreenLoadingState()
                }
                tracks.loadState.refresh is LoadState.Error -> {
                    ErrorScreen(
                        message = (tracks.loadState.refresh as LoadState.Error).error.localizedMessage
                            ?: stringResource(Res.string.music_failed_load_tracks),
                        onRetry = { tracks.refresh() },
                    )
                }
                else -> {
                    if (tracks.itemCount == 0) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Music,
                            title = stringResource(Res.string.music_no_tracks_found),
                        )
                    } else {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = LocalTvMode.current
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .tvFocusRestorer()
                                .focusRequester(listFocusRequester),
                        ) {
                            items(
                                count = tracks.itemCount,
                                key = tracks.safeItemKey { it.id },
                                contentType = { "mediaItem" },
                            ) { index ->
                                val track = tracks[index]
                                if (track != null) {
                                    // Memoize per-item so getImageUrl + the click
                                    // lambdas aren't rebuilt on every recomposition
                                    // of this visible row (matches Search/Library).
                                    val imageUrl = remember(track.id) { viewModel.getImageUrl(track.id) }
                                    val onClick = remember(track.id) {
                                        {
                                            val loadedTracks = tracks.itemSnapshotList.items
                                            val clickIndex = loadedTracks.indexOfFirst { it.id == track.id }
                                            viewModel.playAll(loadedTracks, if (clickIndex >= 0) clickIndex else 0)
                                            onItemClick(track.id)
                                        }
                                    }
                                    val onAddToQueue = remember(track.id) { { viewModel.addToQueue(track) } }
                                    TrackRow(
                                        name = track.name,
                                        artist = track.albumArtist,
                                        album = track.album,
                                        duration = track.runTimeTicks?.let { ticks ->
                                            remember(ticks) {
                                                com.raulshma.jellyplay.core.ui.components.formatDurationMs(ticks / 10_000)
                                            }
                                        },
                                        imageUrl = imageUrl,
                                        onClick = onClick,
                                        onAddToQueue = onAddToQueue,
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
                    JellyPlayLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
                is LoadState.Error -> {
                    AppendErrorFooter(
                        message = appendState.error.localizedMessage
                            ?: stringResource(Res.string.music_failed_load_more),
                        onRetry = { tracks.retry() },
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
