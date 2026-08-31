package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
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
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_check_jellyfin_libraries
import com.raulshma.jellyplay.feature.music.generated.resources.music_loading_music
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_music_available
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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
    viewModel: MusicHomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = uiState.sections
    val isLoading = uiState.isLoading
    val error = uiState.error
    val backgroundColorState = rememberScreenBackgroundColorState()
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
        isRefreshing = isLoading && sections.isNotEmpty(),
        onRefresh = {
            viewModel.refresh()
        },
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColorState.value) }
            .statusBarsPadding(),
    ) {
        when {
            error != null && sections.isEmpty() -> {
                ErrorScreen(message = error, onRetry = { scope.launch { viewModel.loadSections() } })
            }
            else -> {
                if (isLoading && sections.isEmpty()) {
                    ScreenLoadingState(message = stringResource(Res.string.music_loading_music))
                } else if (sections.isEmpty()) {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Music,
                        title = stringResource(Res.string.music_no_music_available),
                        description = stringResource(Res.string.music_check_jellyfin_libraries),
                    )
                } else {
                    // Look up each section by typed identity (exhaustive over MusicHomeSectionType),
                    // so a renamed label can never silently hide a section.
                    fun section(type: MusicHomeSectionType): MusicHomeSection? =
                        sections.firstOrNull { it.type == type }

                    val artistsSection = section(MusicHomeSectionType.FAVORITE_ARTISTS)
                    val latestAlbumsSection = section(MusicHomeSectionType.LATEST_ALBUMS)
                    val topRatedAlbumsSection = section(MusicHomeSectionType.TOP_RATED_ALBUMS)
                    val recentlyPlayedSection = section(MusicHomeSectionType.RECENTLY_PLAYED)
                    val favoriteTracksSection = section(MusicHomeSectionType.FAVORITE_TRACKS)

                    // Dynamic list of active sections/cards to build the vertical chain.
                    // Each entry is (headerFocusRequester?, rowFocusRequester); the Player Screens
                    // row has no header so its header slot is null.
                    val activeChain = remember(sections) {
                        buildList {
                            add(Pair(null as FocusRequester?, playerScreensRow))
                            if (artistsSection != null) add(Pair(artistsHeader, artistsRow))
                            if (latestAlbumsSection != null) add(Pair(latestAlbumsHeader, latestAlbumsRow))
                            if (topRatedAlbumsSection != null) add(Pair(topRatedAlbumsHeader, topRatedAlbumsRow))
                            if (recentlyPlayedSection != null) add(Pair(recentlyPlayedHeader, recentlyPlayedRow))
                            if (favoriteTracksSection != null) add(Pair(favoriteTracksHeader, favoriteTracksRow))
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

                        artistsSection?.let { section ->
                            item {
                                val index = activeChain.indexOfFirst { it.second == artistsRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                ArtistsSection(
                                    artists = section.items,
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

                        latestAlbumsSection?.let { section ->
                            item {
                                val index = activeChain.indexOfFirst { it.second == latestAlbumsRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                NewReleasesSection(
                                    albums = section.items,
                                    onAlbumClick = onAlbumClick,
                                    onAlbumPlayClick = { albumId ->
                                        viewModel.playAlbum(albumId)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAlbums(section.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shuffleAlbums(section.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    headerFocusRequester = latestAlbumsHeader,
                                    rowFocusRequester = latestAlbumsRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        topRatedAlbumsSection?.let { section ->
                            item {
                                val index = activeChain.indexOfFirst { it.second == topRatedAlbumsRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                NewReleasesSection(
                                    albums = section.items,
                                    onAlbumClick = onAlbumClick,
                                    onAlbumPlayClick = { albumId ->
                                        viewModel.playAlbum(albumId)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAlbums(section.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shuffleAlbums(section.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    title = stringResource(section.type.displayNameRes),
                                    subtitle = stringResource(section.type.subtitleRes),
                                    headerFocusRequester = topRatedAlbumsHeader,
                                    rowFocusRequester = topRatedAlbumsRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        recentlyPlayedSection?.let { section ->
                            item {
                                val index = activeChain.indexOfFirst { it.second == recentlyPlayedRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                RecentlyPlayedSection(
                                    tracks = section.items,
                                    onTrackClick = onItemClick,
                                    onTrackPlayClick = { idx ->
                                        viewModel.playAll(section.items, idx)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAll(section.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(section.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    headerFocusRequester = recentlyPlayedHeader,
                                    rowFocusRequester = recentlyPlayedRow,
                                    upFocusRequester = headerUp,
                                    downFocusRequester = rowDown,
                                )
                            }
                        }

                        favoriteTracksSection?.let { section ->
                            item {
                                val index = activeChain.indexOfFirst { it.second == favoriteTracksRow }
                                val (headerUp, _) = getHeaderFocusLinks(index)
                                val (_, rowDown) = getRowFocusLinks(index)

                                Spacer(modifier = Modifier.height(24.dp))
                                RecentlyPlayedSection(
                                    tracks = section.items,
                                    onTrackClick = onItemClick,
                                    onTrackPlayClick = { idx ->
                                        viewModel.playAll(section.items, idx)
                                    },
                                    onPlayAllClick = {
                                        viewModel.playAll(section.items)
                                    },
                                    onShuffleClick = {
                                        viewModel.shufflePlay(section.items)
                                    },
                                    imageUrlBuilder = { viewModel.getImageUrl(it) },
                                    title = stringResource(section.type.displayNameRes),
                                    subtitle = stringResource(section.type.subtitleRes),
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
