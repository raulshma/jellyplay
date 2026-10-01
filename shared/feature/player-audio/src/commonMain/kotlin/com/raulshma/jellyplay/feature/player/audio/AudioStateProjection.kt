package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The projection slice over the engine/queue/sleep/prefs flows — the ONE fold
 * behind the flow-driven half of [AudioPlayerUiState] (the former ~12
 * hand-synced init collectors on [AudioPlayerViewModel], each copying its
 * field into the shared state; now one combine tree emits a
 * [AudioProjectionState] and the VM applies it with a single `_uiState`
 * write). Grouping mirrors the former collectors (track metadata that changes
 * together combines together; position stays OUT — it remains the VM's
 * high-frequency [AudioPlayerViewModel.currentPositionState] holder).
 *
 * Deliberately NOT projected (their writers are choreography, not flows):
 * `isFavorite` (the favorite fetch on the VM), `albumArtBlurHash` (the
 * blur-hash cache), `lyrics.searchResults`/`isSearching` (the search
 * callbacks), `lyrics.karaokeMode` (the UI toggle), and the sleep timer's
 * controller-driven writes. The [AudioProjectionState.appliedTo] fold touches
 * only the fields below, so those writers can never be clobbered.
 *
 * Pure-ish: the flows come in through the constructor and the field mapping
 * lives in [AudioProjectionState.appliedTo], both directly drivable from
 * jvmTest (`AudioStateProjectionTest` pins the mappings and the
 * mirror-getter regressions).
 */
internal class AudioStateProjection(
    engine: AudioPlayerEngine,
    queueManager: AudioQueueManager,
    sleepCountdown: SleepCountdown,
    audioStore: AudioStore,
    scope: CoroutineScope,
) {

    /** The combined projection, re-published whenever any source moves. */
    val state: StateFlow<AudioProjectionState> = combine(
        combine(
            combine(
                engine.title,
                engine.playbackError,
                engine.isLoadingItem,
            ) { title, error, loading -> MetaSlice(title, error, loading) },
            combine(
                engine.artist,
                engine.artistId,
                engine.album,
                engine.albumArtUrl,
            ) { artist, artistId, album, art -> TrackSlice(artist, artistId, album, art) },
            combine(
                engine.isPlaying,
                engine.duration,
                engine.speed,
            ) { playing, duration, speed -> TransportSlice(playing, duration, speed) },
            combine(
                queueManager.shuffleMode,
                queueManager.repeatMode,
                queueManager.queue,
                queueManager.currentIndex,
            ) { shuffle, repeat, queue, index ->
                QueueState(queue = queue, currentIndex = index, shuffleMode = shuffle, repeatMode = repeat)
            },
        ) { meta, track, transport, queue -> CoreSlices(meta, track, transport, queue) },
        combine(
            engine.lyrics,
            engine.currentLyricIndex,
            engine.lyricsSource,
            engine.isFetchingLyrics,
            engine.lyricsOffsetMs,
        ) { lyrics, index, source, fetching, offset ->
            ProjectionLyrics(lyrics, index, source, fetching, offset)
        },
        engine.crossfadeDurationMs,
        combine(
            sleepCountdown.isSleepTimerActive,
            sleepCountdown.isEndOfEpisodeMode,
            audioStore.audio.map { it.sleepTimerDurationMs },
        ) { active, endOfEpisode, lastUsed -> SleepSlice(active, endOfEpisode, lastUsed) },
    ) { core, lyrics, crossfade, sleep ->
        AudioProjectionState(
            title = core.meta.title,
            playbackError = core.meta.playbackError,
            isLoading = core.meta.isLoading,
            artist = core.track.artist,
            artistId = core.track.artistId,
            album = core.track.album,
            albumArtUrl = core.track.albumArtUrl,
            isPlaying = core.transport.isPlaying,
            duration = core.transport.duration,
            speed = core.transport.speed,
            crossfadeDurationMs = crossfade,
            queue = core.queue,
            lyrics = lyrics,
            sleepTimerActive = sleep.active,
            sleepTimerEndOfEpisode = sleep.endOfEpisode,
            sleepTimerLastUsedDurationMs = sleep.lastUsedDurationMs,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AudioProjectionState())

    private data class CoreSlices(
        val meta: MetaSlice,
        val track: TrackSlice,
        val transport: TransportSlice,
        val queue: QueueState,
    )

    private data class MetaSlice(val title: String, val playbackError: String?, val isLoading: Boolean)

    private data class TrackSlice(
        val artist: String,
        val artistId: String?,
        val album: String,
        val albumArtUrl: String,
    )

    private data class TransportSlice(val isPlaying: Boolean, val duration: Long, val speed: Float)

    private data class SleepSlice(
        val active: Boolean,
        val endOfEpisode: Boolean,
        val lastUsedDurationMs: Long,
    )
}

/**
 * The flow-derived slice of [AudioPlayerUiState] — exactly the fields the
 * engine/queue/sleep/prefs flows own.
 */
@Immutable
data class AudioProjectionState(
    val title: String = "",
    val playbackError: String? = null,
    val isLoading: Boolean = false,
    val artist: String = "",
    val artistId: String? = null,
    val album: String = "",
    val albumArtUrl: String = "",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val speed: Float = 1.0f,
    val crossfadeDurationMs: Long = 0L,
    val queue: QueueState = QueueState(),
    val lyrics: ProjectionLyrics = ProjectionLyrics(),
    val sleepTimerActive: Boolean = false,
    val sleepTimerEndOfEpisode: Boolean = false,
    val sleepTimerLastUsedDurationMs: Long = 0L,
)

/** The engine-driven subset of [LyricsState] (search/karaoke stay imperative). */
@Immutable
data class ProjectionLyrics(
    val lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val lyricsSource: com.raulshma.jellyplay.core.model.LyricsSource =
        com.raulshma.jellyplay.core.model.LyricsSource.UNKNOWN,
    val isFetchingLyrics: Boolean = false,
    val lyricsOffsetMs: Long =
        com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS,
)

/**
 * THE field mapping into [AudioPlayerUiState] — the one place the projection
 * fields land on their state fields (pinned field-for-field by
 * `AudioStateProjectionTest`). Only these fields are written; every other
 * [AudioPlayerUiState] field (and the imperative slices inside [LyricsState]
 * / [SleepTimerState]) passes through untouched.
 */
fun AudioProjectionState.appliedTo(state: AudioPlayerUiState): AudioPlayerUiState = state.copy(
    title = title,
    playbackError = playbackError,
    isLoading = isLoading,
    artist = artist,
    artistId = artistId,
    album = album,
    albumArtUrl = albumArtUrl,
    isPlaying = isPlaying,
    duration = duration,
    speed = speed,
    crossfadeDurationMs = crossfadeDurationMs,
    queue = queue,
    lyrics = state.lyrics.copy(
        lyrics = lyrics.lyrics,
        currentLyricIndex = lyrics.currentLyricIndex,
        lyricsSource = lyrics.lyricsSource,
        isFetchingLyrics = lyrics.isFetchingLyrics,
        lyricsOffsetMs = lyrics.lyricsOffsetMs,
    ),
    sleepTimer = state.sleepTimer.copy(
        active = sleepTimerActive,
        endOfEpisode = sleepTimerEndOfEpisode,
        lastUsedDurationMs = sleepTimerLastUsedDurationMs,
    ),
)
