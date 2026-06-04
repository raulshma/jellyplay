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
import com.raulshma.jellyplay.feature.music.components.RecentlyPlayedSection
import com.raulshma.jellyplay.feature.music.components.ArtistsSection
import com.raulshma.jellyplay.feature.music.components.AudioPlayerScreensSection
import com.raulshma.jellyplay.feature.music.components.NewReleasesSection
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
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

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.loadSections()
            isRefreshing = false
        },
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding(),
    ) {
        when {
            error != null && sections.isEmpty() -> {
                ErrorScreen(message = error, onRetry = { viewModel.loadSections() })
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

                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv)
                        ),
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            AudioPlayerScreensSection(
                                onNowPlayingClick = onNowPlayingClick,
                                onAmbientClick = onAmbientClick,
                                onTracksClick = onTracksClick,
                                onAlbumsClick = onAlbumsClick,
                                onArtistsClick = onArtistsClick,
                                onGenresClick = onGenresClick,
                                onPlaylistsClick = onPlaylistsClick,
                            )
                        }

                        if (artistsSection != null && artistsSection.items.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                ArtistsSection(
                                    artists = artistsSection.items,
                                    onArtistClick = { artistId ->
                                        onItemClick(artistId)
                                    },
                                    onArtistPlayClick = { artistId ->
                                        onItemClick(artistId)
                                    },
                                    onViewAllClick = onArtistsClick,
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                )
                            }
                        }

                        if (latestAlbumsSection != null && latestAlbumsSection.items.isNotEmpty()) {
                            item {
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
                                )
                            }
                        }

                        if (topRatedAlbumsSection != null && topRatedAlbumsSection.items.isNotEmpty()) {
                            item {
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
                                )
                            }
                        }

                        if (recentlyPlayedSection != null && recentlyPlayedSection.items.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                RecentlyPlayedSection(
                                    tracks = recentlyPlayedSection.items,
                                    onTrackClick = onItemClick,
                                    onPlayAllClick = {
                                        viewModel.playAll(recentlyPlayedSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(recentlyPlayedSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                )
                            }
                        }

                        if (favoriteTracksSection != null && favoriteTracksSection.items.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                RecentlyPlayedSection(
                                    tracks = favoriteTracksSection.items,
                                    onTrackClick = onItemClick,
                                    onPlayAllClick = {
                                        viewModel.playAll(favoriteTracksSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(favoriteTracksSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    title = "Favorite Tracks",
                                    subtitle = "Songs you love the most",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
