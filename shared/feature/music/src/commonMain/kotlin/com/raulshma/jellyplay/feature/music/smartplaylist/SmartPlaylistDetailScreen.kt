package com.raulshma.jellyplay.feature.music.smartplaylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_tracks_criteria
import com.raulshma.jellyplay.feature.music.generated.resources.music_play_all
import com.raulshma.jellyplay.feature.music.generated.resources.music_smart_playlist
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun SmartPlaylistDetailScreen(
    playlistId: String,
    onTrackClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SmartPlaylistsViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    val playlist = viewModel.playlists.find { it.id == playlistId }

    LaunchedEffect(playlistId) {
        if (playlist != null) {
            viewModel.generatePlaylist(playlist)
        }
    }

    // TV focus-on-launch: focus the first track once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val listFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = viewModel.generatedItems.size,
        tag = "smart_playlist_detail_init",
    )

    JellyPlayScreenScaffold(
        title = if (playlist != null) smartPlaylistDisplayName(playlist) else stringResource(Res.string.music_smart_playlist),
        onBack = onBack,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.isLoading -> {
                    ScreenLoadingState()
                }
                viewModel.error != null -> {
                    ErrorScreen(
                        message = viewModel.error!!,
                        onRetry = {
                            if (playlist != null) viewModel.generatePlaylist(playlist)
                        },
                    )
                }
                viewModel.generatedItems.isEmpty() -> {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Wand,
                        title = stringResource(Res.string.music_no_tracks_criteria),
                    )
                }
                else -> AnimatedEntrance(visible = true) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer()
                            .focusRequester(listFocusRequester),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = adaptiveInfo.bottomPadding(isTv) + innerPadding.calculateBottomPadding(),
                            start = contentPad,
                            end = contentPad,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(viewModel.generatedItems.size, key = { viewModel.generatedItems[it].id }, contentType = { "mediaItem" }) { index ->
                            val track = viewModel.generatedItems[index]
                            TrackRow(
                                name = track.name,
                                artist = track.albumArtist,
                                album = track.album,
                                duration = track.runTimeTicks?.let { ticks ->
                                    val totalSeconds = ticks / 10_000_000
                                    val minutes = (totalSeconds % 3600) / 60
                                    val seconds = totalSeconds % 60
                                    String.format("%d:%02d", minutes, seconds)
                                },
                                imageUrl = viewModel.getImageUrl(track.id),
                                onClick = { viewModel.playAll(index) },
                                blurHash = track.blurHashes.primary,
                            )
                        }
                    }
                }
            }

            val firstTrack = viewModel.generatedItems.firstOrNull()
            if (firstTrack != null && !viewModel.isLoading) {
                val playAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                ExtendedFloatingActionButton(
                    onClick = { viewModel.playAll() },
                    icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
                    text = { Text(stringResource(Res.string.music_play_all)) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(playAllFocusState.focusModifier)
                        .tvFocusIndicator(playAllFocusState, ShapeCache.smooth16)
                        .padding(end = 16.dp)
                        .clearFloatingNav(),
                )
            }
        }
    }
}
