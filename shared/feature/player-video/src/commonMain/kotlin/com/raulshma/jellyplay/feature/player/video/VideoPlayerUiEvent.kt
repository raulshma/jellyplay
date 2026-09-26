package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio

/**
 * Every user intent the video player screen can express. [VideoPlayerViewModel.onEvent]
 * is the single command funnel (the player-audio module's AudioPlayerUiEvent
 * precedent): the VM exposes no per-action command methods beyond it and the
 * state flows/sync getters, so new
 * intents are added here (and routed once in the `when`) rather than as new
 * public members on the VM (whose member ratchet the fold created).
 *
 * Deliberately NOT here:
 *  - controller-slice commands — the screen drives the exposed `cast` /
 *    `syncPlay` / `subtitles` / `sleepTimer` / `abRepeat` / `effects` /
 *    `render` handles directly (the audio funnel's `castController`
 *    carve-out); the VM does not relay slice commands;
 *  - queries with return values — [VideoPlayerViewModel.getImageUrl],
 *    [VideoPlayerViewModel.loadTrickplayThumbnail],
 *    [VideoPlayerViewModel.verifyPlayerLockPin],
 *    [VideoPlayerViewModel.useDownloadedSubtitle] (the hub consumes the
 *    Boolean), the state flows and the sync getters;
 *  - lifecycle — [VideoPlayerViewModel.release] (the screen's dispose hook,
 *    same posture as `onCleared`).
 *
 * Eleven former per-action funs with no production caller died in the fold
 * rather than gaining events: `loadActiveSubtitleCues` / `setPreviewSheetVisible`
 * / `clearActiveSubtitleCues` (the cue-preview sheet drives
 * [SubtitlePreviewController] through [SelectSubtitleTrack] and the aggregate
 * prefs collector only), `setSecondarySubtitleTrack`, `skipCredits`
 * (only the intro button exists in the overlays), `toggleEqualizer` /
 * `setEqualizerSettings` / `setFrameRateMatching` / `setRefreshRateMode`
 * (the settings screens own those preference stores directly) and
 * `setInterpolationTscale` / `setCustomShaderFiles` (the Rendering sheet only
 * exposes the pickers with callers: [SetRenderShaderPack], [SetRenderToneMapping],
 * [SetRenderQuality], [ClearRenderOverride]).
 */
sealed interface VideoPlayerUiEvent {

    /** Starts playback of [itemId], routing remote-play/mini-player reclaim inside the session. */
    data class Initialize(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0L,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : VideoPlayerUiEvent

    /** Starts playback of a picked episode at its saved position. */
    data class PlayEpisode(val episodeId: String, val startPositionTicks: Long = 0L) : VideoPlayerUiEvent

    /** Restarts the current item from the beginning (the resume-reminder chip). */
    data object RestartPlayback : VideoPlayerUiEvent

    /** Errors: retry on the same engine. */
    data object RetryPlayback : VideoPlayerUiEvent

    /** Errors: retry on a specific [playerType] (the switch-engine button). */
    data class RetryWithEngine(val playerType: PlayerType) : VideoPlayerUiEvent

    /** Errors: dismiss the playback-error dialog without retrying. */
    data object DismissPlaybackError : VideoPlayerUiEvent

    /** Arms/disarms the controls auto-hide clock (controls visibility changes). */
    data class SetControlsVisible(val visible: Boolean) : VideoPlayerUiEvent

    /** Installs a user-picked font (SAF/document uri string) and applies it to the subtitle style. */
    data class InstallUserFont(val uri: String) : VideoPlayerUiEvent

    /** Re-binds the media session after background casting returned the player to the foreground. */
    data object ReattachFromBackgroundCast : VideoPlayerUiEvent

    /** Swaps the media-session owner to the cast receiver before backgrounding. */
    data object DetachForBackgroundCast : VideoPlayerUiEvent

    /** Locks/unlocks the screen (the lock button + the PIN overlay's unlock). */
    data class SetScreenLocked(val locked: Boolean) : VideoPlayerUiEvent

    /**
     * The transport play/pause funnel: SyncPlay group first, cast receiver
     * second, local engine last (the A1 routing — the PiP window's
     * PLAY/PAUSE actions land on the same path).
     */
    data class TransportPlay(val play: Boolean) : VideoPlayerUiEvent

    /** Seeks the engine to an absolute [positionMs] (user-initiated — segment-clamped). */
    data class SeekTo(val positionMs: Long) : VideoPlayerUiEvent

    /** Steps the seek by the configured window ([direction] < 0 back, else forward). */
    data class SeekByStep(val direction: Int) : VideoPlayerUiEvent

    /** Toggles mute on the engine (persisted when remember-muted is on). */
    data object ToggleMute : VideoPlayerUiEvent

    /** Resets the pass-out interaction clock (gestures / controls interaction). */
    data object UserInteraction : VideoPlayerUiEvent

    /** The hold-to-speed gesture pressed. */
    data object StartHoldSpeed : VideoPlayerUiEvent

    /** The hold-to-speed gesture released. */
    data object StopHoldSpeed : VideoPlayerUiEvent

    /** Pushes the current subtitle style to the engine (after font/zoom-affecting UI events). */
    data object ApplySubtitleStyle : VideoPlayerUiEvent

    /** Forwards the video surface's window bounds to the PiP window as its source-rect hint. */
    data class UpdatePipSourceRect(val left: Int, val top: Int, val right: Int, val bottom: Int) : VideoPlayerUiEvent

    /** Skips to the previous episode. */
    data object PlayPreviousEpisode : VideoPlayerUiEvent

    /** Skips to the next episode (also the Up Next overlay's play and the PiP NEXT action). */
    data object PlayNextEpisode : VideoPlayerUiEvent

    /** "Mark watched & skip": marks the current item played, then advances (or closes). */
    data object MarkWatchedAndSkip : VideoPlayerUiEvent

    /** "Mark unwatched & exit": clears the played flag and closes the player. */
    data object MarkUnwatchedAndQuit : VideoPlayerUiEvent

    /** Toggles dialogue boost. */
    data object ToggleDialogueBoost : VideoPlayerUiEvent

    /** Sets the dialogue boost strength. */
    data class SetDialogueBoostStrength(val strength: EffectStrength) : VideoPlayerUiEvent

    /** Toggles the video-stats overlay. */
    data object ToggleVideoStats : VideoPlayerUiEvent

    /** Toggles audio-only playback (video surface off). */
    data object ToggleAudioOnly : VideoPlayerUiEvent

    /** Offsets the subtitle delay (the AV-sync slider — persisted per item). */
    data class SetSubtitleDelay(val ms: Long) : VideoPlayerUiEvent

    /** Skips the active intro segment (the overlay button). */
    data object SkipIntro : VideoPlayerUiEvent

    /** Skips the user-tapped [segment] (the segment overlay's skip button). */
    data class SkipSegment(val segment: MediaSegment) : VideoPlayerUiEvent

    /** Saves the brightness level (persisted when remember-brightness is on). */
    data class SaveBrightness(val level: Float) : VideoPlayerUiEvent

    /** Changes the playback speed. */
    data class SetPlaybackSpeed(val speed: Float) : VideoPlayerUiEvent

    /** Selects an audio track (the audio picker). */
    data class SelectAudioTrack(val option: TrackOption) : VideoPlayerUiEvent

    /** Selects a subtitle track (the subtitle picker / downloaded-subtitle activation). */
    data class SelectSubtitleTrack(val option: TrackOption) : VideoPlayerUiEvent

    /** Clears the stored audio-track override. */
    data object ResetAudioTrack : VideoPlayerUiEvent

    /** Clears the stored subtitle-track override. */
    data object ResetSubtitleTrack : VideoPlayerUiEvent

    /** Saves/clears the per-series preferred audio language (null forgets). */
    data class SetSeriesAudioLanguagePreference(val language: String?) : VideoPlayerUiEvent

    /** Saves/clears the per-series preferred subtitle descriptor (null language forgets). */
    data class SetSeriesSubtitlePreference(
        val language: String?,
        val forced: Boolean? = null,
        val hearingImpaired: Boolean? = null,
    ) : VideoPlayerUiEvent

    /** Saves/clears the per-series "subtitles off" intent. */
    data class SetSeriesSubtitleDisabled(val disabled: Boolean) : VideoPlayerUiEvent

    /** Changes the aspect-ratio resize mode. */
    data class SetAspectRatio(val ratio: AspectRatio) : VideoPlayerUiEvent

    /** Applies a subtitle-style edit (mirror + engine sync + global persist). */
    data class SetSubtitleStyle(val style: SubtitleStyle) : VideoPlayerUiEvent

    /** Changes the playback mode (auto/direct/transcode) and reloads. */
    data class SetPlaybackMode(val mode: PlaybackMode) : VideoPlayerUiEvent

    /** Changes the streaming-quality tier and reloads. */
    data class SetStreamingQuality(val quality: StreamingQuality) : VideoPlayerUiEvent

    /** Toggles adaptive bitrate (the AUTO-mode network cap). */
    data class SetAdaptiveBitrateEnabled(val enabled: Boolean) : VideoPlayerUiEvent

    /** Applies a video-effects (filters) config to the session, persisted per item. */
    data class SetVideoEffects(val effects: VideoEffectsConfig) : VideoPlayerUiEvent

    /** The Rendering sheet's shader-pack pick (persist pins the per-series/item override). */
    data class SetRenderShaderPack(val pack: MpvShaderPack, val persist: Boolean) : VideoPlayerUiEvent

    /** The Rendering sheet's tone-mapping pick (same choreography as [SetRenderShaderPack]). */
    data class SetRenderToneMapping(val mapping: MpvToneMapping, val persist: Boolean) : VideoPlayerUiEvent

    /** The Rendering sheet's render-quality pick (a global mpv preference). */
    data class SetRenderQuality(val quality: MpvRenderQuality) : VideoPlayerUiEvent

    /** "Inherit (follow global)": clears the persisted render override + the session lens. */
    data object ClearRenderOverride : VideoPlayerUiEvent

    /** Cycles the session-scoped deinterlace override AUTO→ON→OFF→AUTO. */
    data object CycleDeinterlace : VideoPlayerUiEvent

    /** Cancels the pending autoplay countdown (Up Next overlay). */
    data object CancelAutoplay : VideoPlayerUiEvent

    /** Flips the autoplay-next-episode preference (Up Next card toggle). */
    data class SetVideoAutoplayNext(val enabled: Boolean) : VideoPlayerUiEvent

    /** Sets the SyncPlay group repeat mode. */
    data class SetSyncPlayRepeatMode(val mode: SyncPlayRepeatMode) : VideoPlayerUiEvent

    /** Sets the SyncPlay group shuffle mode. */
    data class SetSyncPlayShuffleMode(val mode: SyncPlayShuffleMode) : VideoPlayerUiEvent

    /** Loads one season's episode list (episode sheet season click). */
    data class LoadSeasonEpisodes(val seasonId: String) : VideoPlayerUiEvent
}
