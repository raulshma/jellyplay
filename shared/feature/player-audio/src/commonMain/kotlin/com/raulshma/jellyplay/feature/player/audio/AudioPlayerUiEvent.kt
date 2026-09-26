package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.ReverbPreset

/**
 * Every user intent the audio player screen can express. [AudioPlayerViewModel.onEvent]
 * is the single command funnel (the HomeUiEvent precedent): the VM exposes no
 * per-action command methods beyond it and the state flows/sync getters, so new
 * intents are added here (and routed once in the `when`) rather than as new
 * public members on the VM. Deliberately NOT here: cast transport control
 * (discovery/connect/disconnect ride the exposed [AudioPlayerViewModel.castController]
 * directly), and crossfade/gapless/replay-gain-pre-amp writes, which the audio
 * settings screen owns through the preference stores —
 * [AudioEffectsController.seedForPlayback] re-applies them at track start. The
 * former per-action funs with no production caller died in the fold rather
 * than gaining events.
 */
sealed interface AudioPlayerUiEvent {
    /** Starts playback of [itemId], seeding the store prefs + effects onto the engine. */
    data class Play(val itemId: String) : AudioPlayerUiEvent

    /** Removes the queue row at [index] (undoable — surfaces an undoEvents entry). */
    data class RemoveFromQueue(val index: Int) : AudioPlayerUiEvent

    /**
     * Restores the queue to before the most recent destructive op, if any. The
     * handler's success [Boolean] is discarded — the caller only offers undo
     * after the snackbar action fired, so there is no outcome to surface.
     */
    data object UndoLastQueueOperation : AudioPlayerUiEvent

    /** Cycles A→B loop: set A → set B → clear. */
    data object CycleAbLoop : AudioPlayerUiEvent

    /** Skips to the next queue entry. */
    data object SkipToNext : AudioPlayerUiEvent

    /** Skips to the previous queue entry. */
    data object SkipToPrevious : AudioPlayerUiEvent

    /** Seeks the engine to an absolute [positionMs]. */
    data class SeekTo(val positionMs: Long) : AudioPlayerUiEvent

    /** Toggles play/pause on the engine. */
    data object TogglePlayPause : AudioPlayerUiEvent

    /**
     * Flings the current track (at its live position) to the selected cast
     * session and pauses the local engine. No-op when nothing is playing.
     */
    data object CastToDevice : AudioPlayerUiEvent

    /** Changes the playback speed. */
    data class ChangePlaybackSpeed(val value: Float) : AudioPlayerUiEvent

    /** Toggles queue shuffle. */
    data object ToggleShuffle : AudioPlayerUiEvent

    /** Cycles the repeat mode. */
    data object CycleRepeatMode : AudioPlayerUiEvent

    /** Jumps playback to the queue row at [index]. */
    data class PlayFromQueue(val index: Int) : AudioPlayerUiEvent

    /** Toggles dialogue boost. */
    data object ToggleDialogueBoost : AudioPlayerUiEvent

    /** Sets the dialogue boost strength. */
    data class SetDialogueBoostStrength(val strength: EffectStrength) : AudioPlayerUiEvent

    /** Toggles night mode. */
    data object ToggleNightMode : AudioPlayerUiEvent

    /** Sets the night mode strength. */
    data class SetNightModeStrength(val strength: EffectStrength) : AudioPlayerUiEvent

    /** Sets the replay-gain normalization mode. */
    data class SetReplayGainMode(val mode: AudioNormalizationMode) : AudioPlayerUiEvent

    /** Toggles the equalizer. */
    data object ToggleEqualizer : AudioPlayerUiEvent

    /** Sets one equalizer band's level. */
    data class SetEqualizerBand(val bandIndex: Int, val levelDb: Int) : AudioPlayerUiEvent

    /** Resets the equalizer to flat and clears the preset. */
    data object ResetEqualizer : AudioPlayerUiEvent

    /** Applies an equalizer preset. */
    data class SetEqualizerPreset(val preset: EqualizerPreset) : AudioPlayerUiEvent

    /** Toggles bass boost. */
    data object ToggleBassBoost : AudioPlayerUiEvent

    /** Sets the bass boost strength. */
    data class SetBassBoostStrength(val strength: EffectStrength) : AudioPlayerUiEvent

    /** Toggles the virtualizer. */
    data object ToggleVirtualizer : AudioPlayerUiEvent

    /** Sets the virtualizer strength (0..1000). */
    data class SetVirtualizerStrength(val strength: Int) : AudioPlayerUiEvent

    /** Applies a reverb preset. */
    data class SetReverbPreset(val preset: ReverbPreset) : AudioPlayerUiEvent

    /** Sets the L/R balance (-1f..1f). */
    data class SetLrBalance(val balance: Float) : AudioPlayerUiEvent

    /** Sets the pitch shift in semitones. */
    data class SetPitchSemitones(val semitones: Float) : AudioPlayerUiEvent

    /** Enables or disables genre-based AutoEQ. */
    data class SetAutoEqByGenre(val enabled: Boolean) : AudioPlayerUiEvent

    /** Searches external lyric providers; results land in the lyrics slice. */
    data class SearchLyrics(val query: String) : AudioPlayerUiEvent

    /** Applies a searched [track] and clears the search results. */
    data class ApplyLyrics(val track: LrcLibTrack) : AudioPlayerUiEvent

    /** Empties the lyrics search results (sheet dismissal). */
    data object ClearLyricsSearch : AudioPlayerUiEvent

    /** Offsets the lyric rendering by [offsetMs]. */
    data class SetLyricsOffset(val offsetMs: Long) : AudioPlayerUiEvent

    /** Starts the sleep timer for [durationMs] and persists it as last-used. */
    data class StartSleepTimer(val durationMs: Long) : AudioPlayerUiEvent

    /** Arms the end-of-episode sleep timer. */
    data object StartSleepTimerEndOfEpisode : AudioPlayerUiEvent

    /** Cancels the sleep timer. */
    data object CancelSleepTimer : AudioPlayerUiEvent

    /** Flips the current track's favorite state (no-op when nothing is playing). */
    data object ToggleFavorite : AudioPlayerUiEvent

    /** Opens the add-to-playlist picker and loads the user's editable playlists. */
    data object OpenPlaylistPicker : AudioPlayerUiEvent

    /** Closes the playlist picker (guarded while an add is in flight). */
    data object DismissPlaylistPicker : AudioPlayerUiEvent

    /** Adds the current track to [playlist]; success posts its name as the message. */
    data class AddToPlaylist(val playlist: Playlist) : AudioPlayerUiEvent

    /** Sets the karaoke view on/off (mirrored into the lyrics slice). */
    data class SetKaraokeModeEnabled(val enabled: Boolean) : AudioPlayerUiEvent

    /** Flips the karaoke view. */
    data object ToggleKaraokeMode : AudioPlayerUiEvent

    /** Persists the lyrics overlay visibility so it survives across sessions. */
    data class SetLyricsVisible(val enabled: Boolean) : AudioPlayerUiEvent

    /**
     * Track-download flip: a completed download routes to the confirm-guarded
     * remove, anything else to the shared
     * [com.raulshma.jellyplay.core.data.download.TrackDownloadActions] flip.
     * No-op when nothing is playing.
     */
    data object DownloadCurrentTrack : AudioPlayerUiEvent
}
