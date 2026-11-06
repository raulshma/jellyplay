package com.raulshma.jellyplay.feature.player.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The screen-facing slice projections over [AudioPlayerViewModel.uiState] —
 * the read-side of the whole-state split. The VM's [AudioPlayerUiState]
 * stays the single source of truth with its one writer set (the
 * [AudioStateProjection] fold + the imperative writers); this holder maps it
 * into per-slice flows so the screen root collects only the slices it
 * renders — a queue emission, favorite flip or speed change re-invalidates
 * only the composables that read that slice, not the whole player body.
 *
 * Each projection re-maps on every state emission and Compose's
 * structural-equality state writes drop the no-op renews — an unchanged
 * slice never reaches its readers. The initial values fold the
 * CONSTRUCTION-time state, so a re-entry onto a warm ViewModel paints its
 * real values from the first frame (a defaulted initial would flash empty
 * for one frame on every back-stack return).
 *
 * Screen-scoped by construction: the host hands in its composition scope,
 * so the sharing stops with the screen (a `WhileSubscribed` sharing policy
 * stops the upstream work when the last collector leaves). The VM's
 * ownership guard stays untouched — these members live here, not on the
 * ViewModel.
 */
internal class AudioPlayerStateSlices(
    uiState: StateFlow<AudioPlayerUiState>,
    scope: CoroutineScope,
) {

    private val initial: AudioPlayerUiState = uiState.value

    /** Track identity + artwork — changes only when the playing track changes. */
    val trackMeta: StateFlow<AudioTrackMetaState> = uiState
        .map { it.toTrackMeta() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial.toTrackMeta())

    /** Engine playback status: play/pause, duration, loading, speed, error. */
    val transport: StateFlow<AudioTransportState> = uiState
        .map { it.toTransport() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial.toTransport())

    /** Queue window + shuffle/repeat — the queue sheet and up-next row's slice. */
    val queueState: StateFlow<QueueState> = uiState
        .map { it.queue }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial.queue)

    /** Lyrics content + search/karaoke flags — the artwork overlay's slice. */
    val lyricsState: StateFlow<LyricsState> = uiState
        .map { it.lyrics }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial.lyrics)

    /** Sleep-timer configuration slice — the top bar and sleep sheet's slice. */
    val sleepTimerState: StateFlow<SleepTimerState> = uiState
        .map { it.sleepTimer }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial.sleepTimer)

    /**
     * The current track's favorite flag — a scalar leaf: only the secondary
     * controls row renders it, so the flip stays out of every other slice.
     */
    val isFavorite: StateFlow<Boolean> = uiState
        .map { it.isFavorite }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial.isFavorite)

    private fun AudioPlayerUiState.toTrackMeta() = AudioTrackMetaState(
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumArtUrl = albumArtUrl,
        albumArtBlurHash = albumArtBlurHash,
    )

    private fun AudioPlayerUiState.toTransport() = AudioTransportState(
        isPlaying = isPlaying,
        duration = duration,
        isLoading = isLoading,
        speed = speed,
        playbackError = playbackError,
    )
}
