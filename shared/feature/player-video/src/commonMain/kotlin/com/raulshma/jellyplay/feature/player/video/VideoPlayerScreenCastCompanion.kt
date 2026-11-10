package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.feature.player.video.components.CompanionDashboard

/**
 * Cast-connected branch: the companion dashboard replaces the video surface
 * entirely. The cast slice's hot flows are collected HERE — this branch is
 * their only consumer, so the receiver's status cadence (the duration ticks
 * especially) recomposes at most this leaf, never the screen root (the
 * collection-placement law; see the root's cast-slice note).
 */
@Composable
internal fun CastCompanionDashboardBranch(
    viewModel: VideoPlayerViewModel,
    title: String,
    subtitle: String,
    uiState: VideoPlayerUiState,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    doTogglePlayPause: () -> Unit,
    doSeekBack: () -> Unit,
    doSeekForward: () -> Unit,
    doSeekTo: (Long) -> Unit,
    onToggleOrientation: () -> Unit,
    disconnectCast: () -> Unit,
) {
    val cast = viewModel.sessionSlices.cast
    val castDurationMs by cast.castDurationMs.collectAsStateWithLifecycle()
    // Track slice — collected here (the cast dashboard is the
    // only consumer on this branch) rather than through the residual uiState.
    val trackState by viewModel.sessionSlices.controls.trackState.collectAsStateWithLifecycle()
    CompanionDashboard(
        title = title,
        subtitle = subtitle,
        overview = uiState.media.overview,
        people = uiState.media.people,
        lyricsLines = uiState.media.lyricsLines,
        artworkUrl = uiState.media.artworkUrl,
        isPlaying = isPlaying,
        castPositionFlow = cast.castPositionMs,
        castVolumeFlow = cast.castVolume,
        durationMs = castDurationMs,
        isConnecting = isCastConnecting,
        audioTracks = trackState.audioTracks,
        subtitleTracks = trackState.subtitleTracks,
        episodes = uiState.episodes.seasonEpisodes,
        onPlayPause = doTogglePlayPause,
        onSeekBack = doSeekBack,
        onSeekForward = doSeekForward,
        onSeekTo = doSeekTo,
        onVolumeChange = { vol -> cast.setCastVolume(vol) },
        onDisconnect = { cast.onCastDisconnected(); disconnectCast() },
        onSelectAudioTrack = { viewModel.onEvent(VideoPlayerUiEvent.SelectAudioTrack(it)) },
        onSelectSubtitleTrack = { viewModel.onEvent(VideoPlayerUiEvent.SelectSubtitleTrack(it)) },
        onPlayEpisode = { epId -> viewModel.onEvent(VideoPlayerUiEvent.PlayEpisode(epId)) },
        personPortraitUrl = { person -> viewModel.personPortraitUrl(person) },
        episodePosterUrl = { episode -> viewModel.posterUrl(episode, 300) },
        onToggleOrientation = onToggleOrientation,
        modifier = Modifier.fillMaxSize()
    )
}
