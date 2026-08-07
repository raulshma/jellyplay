package com.raulshma.jellyplay.feature.music.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.feature.music.R
import com.raulshma.jellyplay.feature.music.albums.MusicSortOption
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.feature.music.components.PagedGrid
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch

@Composable
fun MusicBrowseScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onGenreClick: (id: String, name: String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: MusicBrowseViewModel = hiltViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val gridMin = adaptiveInfo.gridMinSize(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    val tabs = listOf(
        stringResource(R.string.music_artists),
        stringResource(R.string.music_albums),
        stringResource(R.string.music_tracks),
        stringResource(R.string.music_genres),
        stringResource(R.string.music_playlists),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    JellyPlayScreenScaffold(
        title = stringResource(R.string.music_browse_music),
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> ArtistsPage(viewModel = viewModel, onItemClick = onArtistClick, contentPad = contentPad, gridMin = gridMin, spacing = spacing)
                    1 -> AlbumsPage(viewModel = viewModel, onItemClick = onAlbumClick, contentPad = contentPad, gridMin = gridMin, spacing = spacing)
                    2 -> TracksPage(viewModel = viewModel, onItemClick = onTrackClick, contentPad = contentPad)
                    3 -> GenresPage(viewModel = viewModel, onItemClick = onGenreClick, contentPad = contentPad, gridMin = gridMin, spacing = spacing)
                    4 -> PlaylistsPage(viewModel = viewModel, onItemClick = onPlaylistClick, contentPad = contentPad, gridMin = gridMin, spacing = spacing)
                }
            }
        }
    }
}

@Composable
private fun ArtistsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 150.dp,
    spacing: Dp = 12.dp,
) {
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val sort by viewModel.artistSort.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        SortMenuRow(
            selected = sort,
            options = listOf(MusicSortOption.NAME, MusicSortOption.DATE_ADDED, MusicSortOption.RANDOM),
            onSelect = viewModel::setArtistSort,
            contentPad = contentPad,
        )
        PagedGrid(
            items = artists,
            itemKey = { it.id },
            contentPad = contentPad,
            gridMin = gridMin,
            spacing = spacing,
        ) { artist, itemModifier ->
            val imageUrl = remember(artist.id) { viewModel.getImageUrl(artist.id) }
            ArtistCard(
                name = artist.name,
                imageUrl = imageUrl,
                onClick = { onItemClick(artist.id) },
                modifier = itemModifier,
                blurHash = artist.blurHashes.primary,
            )
        }
    }
}

@Composable
private fun AlbumsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 150.dp,
    spacing: Dp = 12.dp,
) {
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val sort by viewModel.albumSort.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        SortMenuRow(
            selected = sort,
            options = MusicSortOption.entries,
            onSelect = viewModel::setAlbumSort,
            contentPad = contentPad,
        )
        PagedGrid(
            items = albums,
            itemKey = { it.id },
            contentPad = contentPad,
            gridMin = gridMin,
            spacing = spacing,
        ) { album, itemModifier ->
            val imageUrl = remember(album.id) { viewModel.getImageUrl(album.id) }
            AlbumCard(
                name = album.name,
                artist = album.albumArtist,
                year = album.year,
                imageUrl = imageUrl,
                onClick = { onItemClick(album.id) },
                modifier = itemModifier,
                blurHash = album.blurHashes.primary,
            )
        }
    }
}

@Composable
private fun TracksPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
    contentPad: Dp = 16.dp,
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val sort by viewModel.trackSort.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        SortMenuRow(
            selected = sort,
            options = listOf(MusicSortOption.NAME, MusicSortOption.DATE_ADDED, MusicSortOption.DATE_PLAYED, MusicSortOption.RANDOM),
            onSelect = viewModel::setTrackSort,
            contentPad = contentPad,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (val refreshState = tracks.loadState.refresh) {
                is LoadState.Loading -> ScreenLoadingState()
                is LoadState.Error -> ErrorScreen(
                    message = refreshState.error.localizedMessage ?: stringResource(R.string.music_failed_load_tracks),
                    onRetry = { tracks.refresh() },
                )
                is LoadState.NotLoading -> {
                    if (tracks.itemCount == 0) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Music,
                            title = stringResource(R.string.music_no_tracks_found),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = contentPad, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = tracks.itemCount,
                                key = tracks.safeItemKey { it.id },
                                contentType = { "mediaItem" },
                            ) { index ->
                                val track = tracks[index] ?: return@items
                                val imageUrl = remember(track.id) { viewModel.getImageUrl(track.id) }
                                TrackRow(
                                    name = track.name,
                                    artist = track.albumArtist,
                                    album = track.album,
                                    duration = track.runTimeTicks?.let { ticks ->
                                        com.raulshma.jellyplay.core.ui.components.formatDurationMs(ticks / 10_000)
                                    },
                                    imageUrl = imageUrl,
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
}

/**
 * Compact sort control placed above a browse grid/list. Shows the active
 * option's label and opens a dropdown of the allowed [options].
 */
@Composable
private fun SortMenuRow(
    selected: MusicSortOption,
    options: List<MusicSortOption>,
    onSelect: (MusicSortOption) -> Unit,
    contentPad: Dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = contentPad, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val focusState = rememberTvFocusState()
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.then(focusState.focusModifier).tvFocusIndicator(focusState, CircleShape),
        ) {
            Text(
                text = stringResource(selected.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GenresPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (id: String, name: String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 120.dp,
    spacing: Dp = 8.dp,
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    if (genres.isEmpty()) {
        ScreenEmptyState(
            icon = Tabler.Outline.Music,
            title = stringResource(R.string.music_no_genres_found),
        )
    } else {
        TvFocusableGrid(
            items = genres,
            key = { it.id },
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize(),
            contentType = { "genre" },
        ) { _, genre, itemModifier ->
            GenreChip(
                name = genre.name,
                onClick = { onItemClick(genre.id, genre.name) },
                modifier = itemModifier,
            )
        }
    }
}

@Composable
private fun PlaylistsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 160.dp,
    spacing: Dp = 12.dp,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    if (playlists.isEmpty()) {
        ScreenEmptyState(
            icon = Tabler.Outline.Playlist,
            title = stringResource(R.string.music_no_playlists_found),
        )
    } else {
        TvFocusableGrid(
            items = playlists,
            key = { it.id },
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize(),
            contentType = { "playlist" },
        ) { _, playlist, itemModifier ->
            val imageUrl = remember(playlist.id) { viewModel.getImageUrl(playlist.id) }
            AlbumCard(
                name = playlist.name,
                artist = if (playlist.itemCount > 0) stringResource(R.string.music_items_count, playlist.itemCount) else null,
                year = null,
                imageUrl = imageUrl,
                onClick = { onItemClick(playlist.id) },
                modifier = itemModifier,
            )
        }
    }
}
