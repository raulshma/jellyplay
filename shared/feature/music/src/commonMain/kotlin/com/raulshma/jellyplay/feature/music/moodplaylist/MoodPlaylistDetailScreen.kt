package com.raulshma.jellyplay.feature.music.moodplaylist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mood_playlist
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_tracks_mood
import com.raulshma.jellyplay.feature.music.generated.resources.music_one_track
import com.raulshma.jellyplay.feature.music.generated.resources.music_play_all
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks_count

@Composable
fun MoodPlaylistDetailScreen(
    playlistId: String,
    onTrackClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MoodPlaylistsViewModel = koinViewModel(),
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

    val displayTitle = playlist?.name ?: stringResource(Res.string.music_mood_playlist)

    // TV focus-on-launch: focus the first track once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val listFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = viewModel.generatedItems.size,
        tag = "mood_playlist_detail_init",
    )

    JellyPlayScreenScaffold(
        title = displayTitle,
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
                viewModel.generatedItems.isEmpty() && !viewModel.isLoading -> {
                    ScreenEmptyState(
                        icon = Tabler.Outline.Music,
                        title = stringResource(Res.string.music_no_tracks_mood),
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
                        if (playlist != null) {
                            item {
                                MoodHeroHeader(
                                    playlist = playlist,
                                    trackCount = viewModel.generatedItems.size,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }
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

@Composable
private fun getDetailMoodIcon(id: String): ImageVector {
    return when (id) {
        "happy" -> Tabler.Outline.MoodSmile
        "chill" -> Tabler.Outline.Sunset
        "energetic" -> Tabler.Outline.Bolt
        "focus" -> Tabler.Outline.Brain
        "workout" -> Tabler.Outline.Flame
        "sad" -> Tabler.Outline.MoodSad
        "romantic" -> Tabler.Outline.Heart
        "party" -> Tabler.Outline.Confetti
        "sleep" -> Tabler.Outline.Moon
        "driving" -> Tabler.Outline.Car
        else -> Tabler.Outline.Music
    }
}

@Composable
private fun MoodHeroHeader(
    playlist: MoodPlaylist,
    trackCount: Int,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = playlist.themeColorHex?.let { hex ->
        parseMoodThemeColor(hex)
    } ?: MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (backgroundColor != MaterialTheme.colorScheme.primaryContainer) {
        val luminance = backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f
        if (luminance > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val expressiveShape = remember {
        smoothCornerShape(
            cornerRadiusTL = 32.dp,
            cornerRadiusTR = 12.dp,
            cornerRadiusBL = 12.dp,
            cornerRadiusBR = 32.dp,
            smoothnessAsPercent = 60,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(expressiveShape)
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                if (playlist.description.isNotEmpty()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    text = if (trackCount == 1) stringResource(Res.string.music_one_track) else stringResource(Res.string.music_tracks_count, trackCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(modifier = Modifier.size(24.dp))
            Icon(
                imageVector = getDetailMoodIcon(playlist.id),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}
