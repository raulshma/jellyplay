package com.raulshma.jellyplay.feature.music.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val tabs = listOf("Artists", "Albums", "Tracks", "Genres", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(title = { Text("Browse Music") }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Expressive tab row with spring animations
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

            // Animated pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        // Expressive slide transition
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) { height -> height } + fadeIn() togetherWith
                        slideOutVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) { height -> -height } + fadeOut()
                    },
                    label = "pageTransition"
                ) { targetPage ->
                    when (targetPage) {
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
    PagedGrid(
        items = artists,
        itemKey = { it.id },
        contentPad = contentPad,
        gridMin = gridMin,
        spacing = spacing,
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
    contentPad: Dp = 16.dp,
    gridMin: Dp = 150.dp,
    spacing: Dp = 12.dp,
) {
    val albums = viewModel.albums.collectAsLazyPagingItems()
    PagedGrid(
        items = albums,
        itemKey = { it.id },
        contentPad = contentPad,
        gridMin = gridMin,
        spacing = spacing,
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
    contentPad: Dp = 16.dp,
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    Box(modifier = Modifier.fillMaxSize()) {
        when (val refreshState = tracks.loadState.refresh) {
            is LoadState.Loading -> ExpressiveLoadingState()
            is LoadState.Error -> ErrorScreen(
                message = refreshState.error.localizedMessage ?: "Failed to load tracks",
                onRetry = { tracks.refresh() },
            )
            is LoadState.NotLoading -> {
                if (tracks.itemCount == 0) {
                    ExpressiveEmptyState(
                        message = "No tracks found",
                        icon = Tabler.Outline.Music
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = contentPad, vertical = 8.dp),
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
                                    com.raulshma.jellyplay.core.ui.components.formatDurationMs(ticks / 10_000)
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
    onItemClick: (id: String, name: String) -> Unit,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 120.dp,
    spacing: Dp = 8.dp,
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    if (genres.isEmpty()) {
        ExpressiveEmptyState(
            message = "No genres found",
            icon = Tabler.Outline.Music
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(genres, key = { it.id }, contentType = { "genre" }) { genre ->
                GenreChip(
                    name = genre.name,
                    onClick = { onItemClick(genre.id, genre.name) },
                )
            }
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
        ExpressiveEmptyState(
            message = "No playlists found",
            icon = Tabler.Outline.Playlist
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridMin),
            contentPadding = PaddingValues(contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(48.dp),
            )
            
            Text(
                text = "Loading your music...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpressiveEmptyState(
    message: String,
    icon: ImageVector = Tabler.Outline.Music
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun <T : Any> PagedGrid(
    items: androidx.paging.compose.LazyPagingItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPad: Dp = 16.dp,
    gridMin: Dp = 150.dp,
    spacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val refreshState = items.loadState.refresh) {
            is LoadState.Loading -> ExpressiveLoadingState()
            is LoadState.Error -> ErrorScreen(
                message = refreshState.error.localizedMessage ?: "Failed to load",
                onRetry = { items.refresh() },
            )
            is LoadState.NotLoading -> {
                if (items.itemCount == 0) {
                    ExpressiveEmptyState(
                        message = "Nothing found",
                        icon = Tabler.Outline.Search
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(gridMin),
                        contentPadding = PaddingValues(contentPad),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
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
