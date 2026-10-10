package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The parts of the legacy
 * [com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager] that the
 * audio player needs but that are NOT on the two shared playback contracts
 * ([AudioQueueManager] for queue mutation/state and [AudioEffectsManager]
 * for the DSP surface): track metadata + transport + lyrics search/offset +
 * undo/A→B loop + crossfade/gapless setters.
 *
 * Lives here (core/data commonMain, beside the playback cores) so the
 * concrete managers implement it DIRECTLY — no app-side 36-member delegate.
 * One-framework-per-type: Android's media3
 * [com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager] stays the
 * Koin single (androidCoreDataModule, which aliases
 * AudioQueueManager/AudioEffectsManager/AudioPlayerEngine onto it), while
 * desktop's `DesktopAudioQueueManager` (this module's jvmMain, beside the
 * chassis core it delegates to; Koin home still apps/desktop's
 * desktopPlayerModule for the desktop-only collaborators) implements it —
 * the same one-object-two-contracts shape over an audio-only mpv engine —
 * real audio, Route.AudioPlayer unguarded.
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

    /**
     * Current playback volume in `[0f, 1f]` — the audio sleep-timer fade's
     * pre-ramp capture source (`AudioSleepTimerController`). Software
     * gain only: it reads the engine's own level, never the OS stream volume.
     */
    val volume: Float

    /**
     * Programmatic volume write — the sleep-timer fade/restore path. The
     * `isUserChange = false` contract mirrors the video engine's: engines
     * with per-content-type volume memory must not capture a fade tick (or
     * the post-expiry restore) as the user's chosen level, and the write
     * must stay software-only — the OS stream volume is the USER path's
     * business, never a fade tick's.
     */
    fun setVolume(volume: Float, isUserChange: Boolean = false)

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
