package com.raulshma.jellyplay.feature.music.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_artists
import com.raulshma.jellyplay.feature.music.generated.resources.music_browse_music
import com.raulshma.jellyplay.feature.music.generated.resources.music_genres
import com.raulshma.jellyplay.feature.music.generated.resources.music_items_count
import com.raulshma.jellyplay.feature.music.generated.resources.music_playlists
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import com.raulshma.jellyplay.feature.music.collection.MusicSortOption
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.feature.music.components.MusicSortMenuButton
import com.raulshma.jellyplay.feature.music.components.MusicCollectionPagedGrid
import com.raulshma.jellyplay.feature.music.components.PagedList
import com.raulshma.jellyplay.feature.music.components.SimpleCollectionGrid
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.raulshma.jellyplay.feature.music.components.rememberPagedCollectionStatus
import com.raulshma.jellyplay.feature.music.components.rememberSimpleCollectionStatus
import kotlinx.coroutines.launch

/**
 * The MusicBrowse tab. Every page is a thin configuration of the one
 * collection chassis: its [MusicCollectionKind] supplies the declared sort
 * set and empty/error presentation, the shared ladder
 * ([MusicCollectionPagedGrid]/[PagedList]/[SimpleCollectionGrid]) supplies
 * refresh/empty/error/pull-to-refresh/append-footer, and the page keeps only
 * its card factory and click navigation.
 *
 * Behavior changes vs the former hand-rolled pages (pure additions on this side):
 * every page gains the full ladder — pull-to-refresh, header status
 * indicator, append footer, and the standalone screens' refresh semantics
 * (stale pages stay visible under a refresh instead of blanking) — and the
 * artists page's sort menu gains DATE_PLAYED (unified on the richer
 * standalone list, see [MusicCollectionKind]).
 */
@Composable
fun MusicBrowseScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onGenreClick: (id: String, name: String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: MusicBrowseViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val gridMin = adaptiveInfo.gridMinSize(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    val tabs = listOf(
        stringResource(Res.string.music_artists),
        stringResource(Res.string.music_albums),
        stringResource(Res.string.music_tracks),
        stringResource(Res.string.music_genres),
        stringResource(Res.string.music_playlists),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_browse_music),
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

/**
 * A browse page's sort menu — the selected option, the kind's declared
 * admission set, and the setter, bundled so the header can never see them
 * disagree. Null on the sortless list-sourced pages (genres/playlists).
 */
private data class CollectionSortMenu(
    val selected: MusicSortOption,
    val options: List<MusicSortOption>,
    val onSelect: (MusicSortOption) -> Unit,
)

/**
 * The status + sort cluster above a browse page — the in-page home of the
 * header status indicator (the browse scaffold is shared across tabs, so the
 * indicator rides the page like the sort menu always did). Sort controls
 * render only where a [CollectionSortMenu] is supplied.
 */
@Composable
private fun CollectionPageHeader(
    status: HeaderStatus,
    sortMenu: CollectionSortMenu?,
    contentPad: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = contentPad, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderStatusIndicator(
            status = status,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (sortMenu != null) {
            MusicSortMenuButton(
                selected = sortMenu.selected,
                options = sortMenu.options,
                onSelect = sortMenu.onSelect,
            )
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
    val kind = MusicCollectionKind.ARTISTS
    Column(modifier = Modifier.fillMaxSize()) {
        CollectionPageHeader(
            status = rememberPagedCollectionStatus(artists),
            sortMenu = CollectionSortMenu(sort, kind.sortOptions, viewModel::setArtistSort),
            contentPad = contentPad,
        )
        MusicCollectionPagedGrid(
            items = artists,
            itemKey = { it.id },
            kind = kind,
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
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
    val kind = MusicCollectionKind.ALBUMS
    Column(modifier = Modifier.fillMaxSize()) {
        CollectionPageHeader(
            status = rememberPagedCollectionStatus(albums),
            sortMenu = CollectionSortMenu(sort, kind.sortOptions, viewModel::setAlbumSort),
            contentPad = contentPad,
        )
        MusicCollectionPagedGrid(
            items = albums,
            itemKey = { it.id },
            kind = kind,
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
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

/**
 * List-variant page — previously hand-rolled its own refresh ladder; now the
 * chassis [PagedList] (gaining pull-to-refresh, append footer, TV
 * focus-on-launch and the status indicator).
 */
@Composable
private fun TracksPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
    contentPad: Dp = 16.dp,
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val sort by viewModel.trackSort.collectAsStateWithLifecycle()
    val kind = MusicCollectionKind.TRACKS
    Column(modifier = Modifier.fillMaxSize()) {
        CollectionPageHeader(
            status = rememberPagedCollectionStatus(tracks),
            sortMenu = CollectionSortMenu(sort, kind.sortOptions, viewModel::setTrackSort),
            contentPad = contentPad,
        )
        PagedList(
            items = tracks,
            itemKey = { it.id },
            contentPadding = PaddingValues(horizontal = contentPad, vertical = 8.dp),
            emptyIcon = kind.emptyIcon,
            emptyTitle = stringResource(kind.emptyTitleRes),
            errorFallbackMessage = stringResource(kind.errorFallbackRes),
            tvInitialFocusTag = "browse_tracks_init",
        ) { track ->
            val imageUrl = remember(track.id) { viewModel.getImageUrl(track.id) }
            TrackRow(
                name = track.name,
                artist = track.albumArtist,
                album = track.album,
                duration = track.runTimeTicks?.let { ticks ->
                    remember(ticks) { formatDurationMs(ticks / 10_000) }
                },
                imageUrl = imageUrl,
                onClick = { onItemClick(track.id) },
                blurHash = track.blurHashes.primary,
            )
        }
    }
}

/**
 * List-sourced page over [SimpleCollectionGrid] — declared additions: the
 * loading/error ladder, pull-to-refresh and the status indicator (the page
 * previously rendered only the grid or a bare empty state).
 */
@Composable
private fun GenresPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (id: String, name: String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 120.dp,
    spacing: Dp = 8.dp,
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val isLoading by viewModel.genresLoading.collectAsStateWithLifecycle()
    val error by viewModel.genresError.collectAsStateWithLifecycle()
    val kind = MusicCollectionKind.GENRES
    Column(modifier = Modifier.fillMaxSize()) {
        CollectionPageHeader(
            status = rememberSimpleCollectionStatus(
                isLoading = isLoading,
                hasError = error != null,
            ),
            sortMenu = null,
            contentPad = contentPad,
        )
        SimpleCollectionGrid(
            items = genres,
            itemKey = { it.id },
            isLoading = isLoading,
            error = error,
            errorFallbackMessage = stringResource(kind.errorFallbackRes),
            onRefresh = viewModel::refreshGenres,
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            emptyIcon = kind.emptyIcon,
            emptyTitle = stringResource(kind.emptyTitleRes),
        ) { _, genre, itemModifier ->
            GenreChip(
                name = genre.name,
                onClick = { onItemClick(genre.id, genre.name) },
                modifier = itemModifier,
            )
        }
    }
}

/**
 * List-sourced page over [SimpleCollectionGrid] — same declared additions as
 * [GenresPage] (the standalone playlists screen's create/delete/edit flows
 * are per-collection commands and stay on that adapter, not here).
 */
@Composable
private fun PlaylistsPage(
    viewModel: MusicBrowseViewModel,
    onItemClick: (String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 160.dp,
    spacing: Dp = 12.dp,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isLoading by viewModel.playlistsLoading.collectAsStateWithLifecycle()
    val error by viewModel.playlistsError.collectAsStateWithLifecycle()
    val kind = MusicCollectionKind.PLAYLISTS
    Column(modifier = Modifier.fillMaxSize()) {
        CollectionPageHeader(
            status = rememberSimpleCollectionStatus(
                isLoading = isLoading,
                hasError = error != null,
            ),
            sortMenu = null,
            contentPad = contentPad,
        )
        SimpleCollectionGrid(
            items = playlists,
            itemKey = { it.id },
            isLoading = isLoading,
            error = error,
            errorFallbackMessage = stringResource(kind.errorFallbackRes),
            onRefresh = viewModel::refreshPlaylists,
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            emptyIcon = kind.emptyIcon,
            emptyTitle = stringResource(kind.emptyTitleRes),
        ) { _, playlist, itemModifier ->
            val imageUrl = remember(playlist.id) { viewModel.getImageUrl(playlist.id) }
            AlbumCard(
                name = playlist.name,
                artist = if (playlist.itemCount > 0) stringResource(Res.string.music_items_count, playlist.itemCount) else null,
                year = null,
                imageUrl = imageUrl,
                onClick = { onItemClick(playlist.id) },
                modifier = itemModifier,
            )
        }
    }
}
