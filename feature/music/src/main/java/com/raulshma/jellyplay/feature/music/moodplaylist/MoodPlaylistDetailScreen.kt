package com.raulshma.jellyplay.feature.music.moodplaylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun MoodPlaylistDetailScreen(
    playlistId: String,
    onTrackClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MoodPlaylistsViewModel = hiltViewModel(),
) {
    val playlist = viewModel.playlists.find { it.id == playlistId }

    LaunchedEffect(playlistId) {
        if (playlist != null && viewModel.selectedPlaylist?.id != playlistId) {
            viewModel.generatePlaylist(playlist)
        }
    }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val navOffsetPx = com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset.current

    val displayTitle = if (playlist != null) "${playlist.emoji} ${playlist.name}" else "Mood Playlist"

    JellyPlayScreenScaffold(
        title = displayTitle,
        onBack = onBack,
    ) { _ ->
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
                viewModel.generatedItems.isEmpty() && !viewModel.isLoading -> {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Music,
                        title = "No tracks match this mood",
                    )
                }
                else -> AnimatedEntrance(visible = true) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = adaptiveInfo.bottomPadding(isTv),
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
                ExtendedFloatingActionButton(
                    onClick = { viewModel.playAll() },
                    icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
                    text = { Text("Play All") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), end = 16.dp)
                        .offset {
                            val maxOffset = com.raulshma.jellyplay.core.designsystem.theme.Dimensions.floatingNavHeight.toPx()
                            val yOffset = (-navOffsetPx).coerceAtMost(maxOffset)
                            androidx.compose.ui.unit.IntOffset(x = 0, y = yOffset.toInt())
                        },
                )
            }
        }
    }
}
