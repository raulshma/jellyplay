package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.feature.music.components.RecentlyPlayedSection
import com.raulshma.jellyplay.feature.music.components.ArtistsSection
import com.raulshma.jellyplay.feature.music.components.AudioPlayerScreensSection
import com.raulshma.jellyplay.feature.music.components.NewReleasesSection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MusicHomeScreen(
    onItemClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onTracksClick: () -> Unit,
    onGenresClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onNowPlayingClick: () -> Unit = {},
    onAmbientClick: () -> Unit = {},
    viewModel: MusicHomeViewModel = hiltViewModel(),
) {
    val sections = viewModel.sections
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val backgroundColor = rememberScreenBackgroundColor()
    val scope = rememberCoroutineScope()

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    val initialFocusRequester = remember { FocusRequester() }

    // Focus groups for each section to build a vertical chain
    val playerScreensRow = remember { FocusRequester() }
    
    val artistsHeader = remember { FocusRequester() }
    val artistsRow = remember { FocusRequester() }
    
    val latestAlbumsHeader = remember { FocusRequester() }
    val latestAlbumsRow = remember { FocusRequester() }
    
    val topRatedAlbumsHeader = remember { FocusRequester() }
    val topRatedAlbumsRow = remember { FocusRequester() }
    
    val recentlyPlayedHeader = remember { FocusRequester() }
    val recentlyPlayedRow = remember { FocusRequester() }
    
    val favoriteTracksHeader = remember { FocusRequester() }
    val favoriteTracksRow = remember { FocusRequester() }

    TvGrabInitialFocus(
        focusRequester = initialFocusRequester,
        itemCount = sections.size,
        tag = "music_home_init"
    )

    PullToRefreshBox(
        isRefreshing = viewModel.isLoading && sections.isNotEmpty(),
        onRefresh = {
            viewModel.refresh()
        },
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding(),
    ) {
        when {
            error != null && sections.isEmpty() -> {
                ErrorScreen(message = error, onRetry = { scope.launch { viewModel.loadSections() } })
            }
            else -> {
                if (isLoading && sections.isEmpty()) {
                    ScreenLoadingState(message = "Loading your music...")
                } else if (sections.isEmpty()) {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Music,
                        title = "No music available",
                        description = "Check your Jellyfin libraries.",
                    )
                } else {
                    val recentlyPlayedSection = sections.find { it.title == "Recently Played" }
                    val artistsSection = sections.find { it.title == "Favorite Artists" }
                    val latestAlbumsSection = sections.find { it.title == "Latest Albums" }
                    val topRatedAlbumsSection = sections.find { it.title == "Top Rated Albums" }
                    val favoriteTracksSection = sections.find { it.title == "Favorite Tracks" }

                    // Dynamic list of active sections/cards to build the vertical chain
                    val activeChain = remember(sections) {
                        buildList {
                            // First item is always Player Screens row
                            add(Pair(null as FocusRequester?, playerScreensRow))
                            
                            if (artistsSection != null && artistsSection.items.isNotEmpty()) {
                                add(Pair(artistsHeader, artistsRow))
                            }
                            if (latestAlbumsSection != null && latestAlbumsSection.items.isNotEmpty()) {
                                add(Pair(latestAlbumsHeader, latestAlbumsRow))
                            }
                            if (topRatedAlbumsSection != null && topRatedAlbumsSection.items.isNotEmpty()) {
                                add(Pair(topRatedAlbumsHeader, topRatedAlbumsRow))
                            }
                            if (recentlyPlayedSection != null && recentlyPlayedSection.items.isNotEmpty()) {
                                add(Pair(recentlyPlayedHeader, recentlyPlayedRow))
                            }
                            if (favoriteTracksSection != null && favoriteTracksSection.items.isNotEmpty()) {
                                add(Pair(favoriteTracksHeader, favoriteTracksRow))
                            }
                        }
                    }

                    // Inline helpers to query activeChain neighbors
                    val getHeaderFocusLinks = remember(activeChain) {
                        { index: Int ->
                            if (index <= 0) Pair(null, null)
                            else Pair(activeChain[index - 1].second, activeChain[index].second)
                        }
                    }

                    val getRowFocusLinks = remember(activeChain) {
                        { index: Int ->
                            if (index < 0) Pair(null, null)
                            else {
                                val up = activeChain[index].first ?: (if (index > 0) activeChain[index - 1].second else null)
                                val down = if (index < activeChain.lastIndex) {
                                    activeChain[index + 1].first ?: activeChain[index + 1].second
                                } else {
                                    null
                                }
                                Pair(up, down)
                            }
                        }
                    }

                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv)
                        ),
                    ) {
                        item {
                            val index = activeChain.indexOfFirst { it.second == playerScreensRow }
                            val (_, downLink) = getRowFocusLinks(index)

                            Spacer(modifier = Modifier.height(100.dp))
                            AudioPlayerScreensSection(
                                onNowPlayingClick = onNowPlayingClick,
                                onAmbientClick = onAmbientClick,
                                onTracksClick = onTracksClick,
                                onAlbumsClick = onAlbumsClick,
                                onArtistsClick = onArtistsClick,
                                onGenresClick = onGenresClick,
                                onPlaylistsClick = onPlaylistsClick,
                                firstFocusRequester = initialFocusRequester,
                                rowFocusRequester = playerScreensRow,
                                rowModifier = Modifier.focusProperties {
                                    @Suppress("DEPRECATION")
                                    exit = { direction ->
                                        if (direction == FocusDirection.Down) {
                                            downLink ?: FocusRequester.Default
                                        } else {
                                            FocusRequester.Default
                                        }
                                    }
                                }
                            )
                        }

                        if (artistsSection != null && artistsSection.items.isNotEmpty()) {
                            item {
                                val index = activeChain.indexOfFirst { it.second == artistsRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                ArtistsSection(
                                    artists = artistsSection.items,
                                    onArtistClick = { artistId ->
                                        onItemClick(artistId)
                                    },
                                    onArtistPlayClick = { artistId ->
                                        viewModel.playArtist(artistId)
                                    },
                                    onViewAllClick = onArtistsClick,
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    headerFocusRequester = artistsHeader,
                                    rowFocusRequester = artistsRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        if (latestAlbumsSection != null && latestAlbumsSection.items.isNotEmpty()) {
                            item {
                                val index = activeChain.indexOfFirst { it.second == latestAlbumsRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                NewReleasesSection(
                                    albums = latestAlbumsSection.items,
                                    onAlbumClick = onAlbumClick,
                                    onAlbumPlayClick = { albumId ->
                                        viewModel.playAlbum(albumId)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAlbums(latestAlbumsSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shuffleAlbums(latestAlbumsSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    headerFocusRequester = latestAlbumsHeader,
                                    rowFocusRequester = latestAlbumsRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        if (topRatedAlbumsSection != null && topRatedAlbumsSection.items.isNotEmpty()) {
                            item {
                                val index = activeChain.indexOfFirst { it.second == topRatedAlbumsRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                NewReleasesSection(
                                    albums = topRatedAlbumsSection.items,
                                    onAlbumClick = onAlbumClick,
                                    onAlbumPlayClick = { albumId ->
                                        viewModel.playAlbum(albumId)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAlbums(topRatedAlbumsSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shuffleAlbums(topRatedAlbumsSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    title = "Top Rated Albums",
                                    subtitle = "Highest rated by the community",
                                    headerFocusRequester = topRatedAlbumsHeader,
                                    rowFocusRequester = topRatedAlbumsRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        if (recentlyPlayedSection != null && recentlyPlayedSection.items.isNotEmpty()) {
                            item {
                                val index = activeChain.indexOfFirst { it.second == recentlyPlayedRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                RecentlyPlayedSection(
                                    tracks = recentlyPlayedSection.items,
                                    onTrackClick = onItemClick,
                                    onTrackPlayClick = { idx ->
                                        viewModel.playAll(recentlyPlayedSection.items, idx)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAll(recentlyPlayedSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(recentlyPlayedSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    headerFocusRequester = recentlyPlayedHeader,
                                    rowFocusRequester = recentlyPlayedRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        if (favoriteTracksSection != null && favoriteTracksSection.items.isNotEmpty()) {
                            item {
                                val index = activeChain.indexOfFirst { it.second == favoriteTracksRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                RecentlyPlayedSection(
                                    tracks = favoriteTracksSection.items,
                                    onTrackClick = onItemClick,
                                    onTrackPlayClick = { idx ->
                                        viewModel.playAll(favoriteTracksSection.items, idx)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAll(favoriteTracksSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(favoriteTracksSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    title = "Favorite Tracks",
                                    subtitle = "Songs you love the most",
                                    headerFocusRequester = favoriteTracksHeader,
                                    rowFocusRequester = favoriteTracksRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
