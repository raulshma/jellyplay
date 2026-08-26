package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.ReverbPreset

/**
 * Single source of truth for the audio player screen, following the project's
 * `StateFlow<UiState>` convention. Cohesive low-frequency fields are grouped into
 * nested [Immutable] blocks so related concerns update atomically and can be passed
 * whole to the composables/sheets that own them (e.g. [effects] feeds [AudioEffectsState]
 * straight to the effects sheet, decoupling it from the ViewModel).
 *
 * The high-frequency [AudioPlayerViewModel.currentPosition] is intentionally kept
 * *outside* this object so the 250ms playback tick triggers a narrow recomposition
 * rather than copying the whole state.
 */
@Immutable
data class AudioPlayerUiState(
    val title: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val album: String = "",
    val albumArtUrl: String = "",
    val albumArtBlurHash: String? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val speed: Float = 1.0f,
    val isFavorite: Boolean = false,
    val playbackError: String? = null,
    val isLoading: Boolean = false,
    val crossfadeDurationMs: Long = 0L,
    val effects: AudioEffectsState = AudioEffectsState(),
    val lyrics: LyricsState = LyricsState(),
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val queue: QueueState = QueueState(),
    // "Add to playlist" picker.
    val showPlaylistPicker: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val isAddingToPlaylist: Boolean = false,
    val playlistMessage: String? = null,
)

@Immutable
data class AudioEffectsState(
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val lrBalance: Float = 0f,
    val pitchSemitones: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val normalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val preAmpDb: Float = 0f,
)

@Immutable
data class LyricsState(
    val lyrics: List<LyricsLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val lyricsSource: LyricsSource = LyricsSource.UNKNOWN,
    val isFetchingLyrics: Boolean = false,
    val lyricsOffsetMs: Long = com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS,
    val searchResults: List<LrcLibTrack> = emptyList(),
    val isSearching: Boolean = false,
    val karaokeMode: Boolean = false,
) {
    /** Whether any loaded line carries word-level timings (enables the karaoke view). */
    val hasKaraokeLyrics: Boolean get() = lyrics.any { it.words.isNotEmpty() }
}

@Immutable
data class SleepTimerState(
    val active: Boolean = false,
    val endOfEpisode: Boolean = false,
    val lastUsedDurationMs: Long = 0L,
)

@Immutable
data class QueueState(
    val queue: List<AudioQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = 0,
)
