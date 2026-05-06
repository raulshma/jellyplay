package com.raulshma.jellyplay.feature.music.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.feature.music.components.TrackRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicBrowseScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: MusicBrowseViewModel = hiltViewModel(),
) {
    val tabs = listOf("Artists", "Albums", "Tracks", "Genres", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Browse Music") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> ArtistsPage(viewModel = viewModel, onItemClick = onArtistClick)
                    1 -> AlbumsPage(viewModel = viewModel, onItemClick = onAlbumClick)
                    2 -> TracksPage(viewModel = viewModel, onItemClick = onTrackClick)
                    3 -> GenresPage(viewModel = viewModel, onItemClick = onGenreClick)
                    4 -> PlaylistsPage(viewModel = viewModel, onItemClick = onPlaylistClick)
                }
            }
        }
    }
}

@Composable
private fun ArtistsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
) {
    val artists = viewModel.artists.collectAsLazyPagingItems()
    PagedGrid(
        items = artists,
        itemKey = { it.id },
    ) { artist ->
        ArtistCard(
            name = artist.name,
            imageUrl = viewModel.getImageUrl(artist.id),
            onClick = { onItemClick(artist.id) },
            blurHash = artist.blurHashes.primary,
        )
    }
}

@Composable
private fun AlbumsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
) {
    val albums = viewModel.albums.collectAsLazyPagingItems()
    PagedGrid(
        items = albums,
        itemKey = { it.id },
    ) { album ->
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

@Composable
private fun TracksPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    Box(modifier = Modifier.fillMaxSize()) {
        when (val refreshState = tracks.loadState.refresh) {
            is LoadState.Loading -> LoadingScreen()
            is LoadState.Error -> ErrorScreen(
                message = refreshState.error.localizedMessage ?: "Failed to load tracks",
                onRetry = { tracks.refresh() },
            )
            is LoadState.NotLoading -> {
                if (tracks.itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tracks found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = tracks.itemCount,
                            key = tracks.itemKey { it.id },
                            contentType = { "mediaItem" },
                        ) { index ->
                            val track = tracks[index] ?: return@items
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
}

@Composable
private fun GenresPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    if (genres.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No genres found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(genres, key = { it.id }, contentType = { "genre" }) { genre ->
                GenreChip(
                    name = genre.name,
                    onClick = { onItemClick(genre.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No playlists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                AlbumCard(
                    name = playlist.name,
                    artist = if (playlist.itemCount > 0) "${playlist.itemCount} items" else null,
                    year = null,
                    imageUrl = viewModel.getImageUrl(playlist.id),
                    onClick = { onItemClick(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun <T : Any> PagedGrid(
    items: androidx.paging.compose.LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val refreshState = items.loadState.refresh) {
            is LoadState.Loading -> LoadingScreen()
            is LoadState.Error -> ErrorScreen(
                message = refreshState.error.localizedMessage ?: "Failed to load",
                onRetry = { items.refresh() },
            )
            is LoadState.NotLoading -> {
                if (items.itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = items.itemCount,
                            key = items.itemKey(itemKey),
                            contentType = { "pagedItem" },
                        ) { index ->
                            val item = items[index]
                            if (item != null) {
                                itemContent(item)
                            }
                        }
                    }
                }
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
