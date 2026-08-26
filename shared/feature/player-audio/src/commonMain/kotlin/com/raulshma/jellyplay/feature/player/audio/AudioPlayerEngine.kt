package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.QueueUndoEvent
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Module-local seam over the parts of the legacy
 * [com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager] that the
 * audio player needs but that are NOT on the two shared playback contracts
 * ([com.raulshma.jellyplay.core.data.playback.AudioQueueManager] for queue
 * mutation/state and [com.raulshma.jellyplay.core.data.playback.AudioEffectsManager]
 * for the DSP surface): track metadata + transport + lyrics search/offset +
 * undo/A→B loop + crossfade/gapless setters.
 *
 * One-framework-per-type: the concrete manager is a 1650-line media3 class
 * that stays Hilt-owned in legacy `:core:data` until Phase X, so the shared
 * ViewModel ctor-types onto the two shared interfaces plus this seam. The
 * Android impl is the app-side lazy interop adapter
 * (`HiltInteropModule.HiltAudioPlayerEngine`, a pure delegate — same shape as
 * the details conveyor's DetailAudioPlayback); there is no desktop impl yet,
 * so the player-audio Koin registration is documented-latent there
 * (Route.AudioPlayer stays guarded in DesktopAppRoot).
 */
interface AudioPlayerEngine {
    val title: StateFlow<String>
    val artist: StateFlow<String>
    val artistId: StateFlow<String?>
    val album: StateFlow<String>
    val albumArtUrl: StateFlow<String>
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val speed: StateFlow<Float>
    val playbackError: StateFlow<String?>
    val isLoadingItem: StateFlow<Boolean>
    val crossfadeDurationMs: StateFlow<Long>
    val undoEvents: SharedFlow<QueueUndoEvent>
    val abLoopStartMs: StateFlow<Long?>
    val abLoopEndMs: StateFlow<Long?>
    val lyrics: StateFlow<List<LyricsLine>>
    val currentLyricIndex: StateFlow<Int>
    val lyricsSource: StateFlow<LyricsSource>
    val isFetchingLyrics: StateFlow<Boolean>
    val lyricsOffsetMs: StateFlow<Long>

    fun play(itemId: String)
    fun seekTo(positionMs: Long)
    fun togglePlayPause()
    fun pause()
    fun changePlaybackSpeed(value: Float)
    fun setSkipPreviousThreshold(ms: Long)
    fun setCrossfadeDurationMs(ms: Long)
    fun setGaplessEnabled(enabled: Boolean)
    fun getImageUrl(itemId: String): String
    fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit)
    fun applyLyrics(lrcLibId: Long)
    fun setLyricsOffset(offsetMs: Long)
    fun stopAndRelease()
    fun undoLastQueueOperation(): Boolean
    fun cycleAbLoop()
}
