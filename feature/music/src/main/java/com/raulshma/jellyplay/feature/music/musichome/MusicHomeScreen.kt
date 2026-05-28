@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.feature.music.components.BottomMusicNavigation
import com.raulshma.jellyplay.feature.music.components.MusicNavItem
import com.raulshma.jellyplay.feature.music.components.MusicHeader
import com.raulshma.jellyplay.feature.music.components.RecentlyPlayedSection
import com.raulshma.jellyplay.feature.music.components.ArtistsSection
import com.raulshma.jellyplay.feature.music.components.NewReleasesSection

@Composable
fun MusicHomeScreen(
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    onItemClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onTracksClick: () -> Unit,
    onGenresClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    viewModel: MusicHomeViewModel = hiltViewModel(),
) {
    val sections = viewModel.sections
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MusicNavItem.HOME) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        when {
            error != null && sections.isEmpty() -> {
                ErrorScreen(message = error, onRetry = { viewModel.loadSections() })
            }
            else -> {
                if (isLoading && sections.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ContainedLoadingIndicator()
                            Text(
                                "Loading your music...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (sections.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No music available. Check your Jellyfin libraries.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // Find sections by title
                    val recentlyPlayedSection = sections.find { it.title == "Recently Played" }
                    val artistsSection = sections.find { it.title == "Favorite Artists" }
                    val latestAlbumsSection = sections.find { it.title == "Latest Albums" }

                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
                        ),
                    ) {
                        // Header
                        item {
                            MusicHeader(
                                onSwitchToVideo = { onModeChange(HomeMode.VIDEO) },
                                onSettingsClick = onSettingsClick,
                            )
                        }

                        // Recently Played Section
                        if (recentlyPlayedSection != null && recentlyPlayedSection.items.isNotEmpty()) {
                            item {
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

                        // Artists Section
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

                        // New Releases Section
                        if (latestAlbumsSection != null && latestAlbumsSection.items.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                NewReleasesSection(
                                    albums = latestAlbumsSection.items,
                                    onAlbumClick = onAlbumClick,
                                    onAlbumPlayClick = { albumId ->
                                        onAlbumClick(albumId)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAll(latestAlbumsSection.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(latestAlbumsSection.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
