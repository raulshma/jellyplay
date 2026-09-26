package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.model.LiveStreamOption

/**
 * Every user intent the live-TV player screen can express. [LiveTvPlayerViewModel.onEvent]
 * is the single command funnel (the VideoPlayerUiEvent / AudioPlayerUiEvent
 * precedent): the VM exposes no per-action command methods beyond it and the
 * state flows/queries, so new intents are added here (and routed once in the
 * `when`) rather than as new public members on the VM (whose member ratchet
 * this fold lowered).
 *
 * Deliberately NOT here: the lifecycle [LiveTvPlayerViewModel.stop] (the
 * screen's `onDispose` hook, same posture as `onCleared`), the platform-relay
 * [LiveTvPlayerViewModel.onVideoSizeChanged] (a media3 callback forward, not
 * an intent) and the queries [LiveTvPlayerViewModel.engineForRendering] /
 * [LiveTvPlayerViewModel.logoUrlFor].
 */
sealed interface LiveTvPlayerUiEvent {

    /** Screen entry: loads the channel list and tunes [channelId] (idempotent per screen mount). */
    data class Initialize(
        val channelId: String,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
    ) : LiveTvPlayerUiEvent

    /** Zaps one channel up (D-pad / button / PiP SKIP action vocabulary), with stream overrides. */
    data class ChannelUp(
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
    ) : LiveTvPlayerUiEvent

    /** Zaps one channel down, with stream overrides. */
    data class ChannelDown(
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
    ) : LiveTvPlayerUiEvent

    /** Tunes the channel whose id matches [channelId] (the channel-list sheet). */
    data class SelectChannelById(val channelId: String) : LiveTvPlayerUiEvent

    /** Adds/removes [channelId] from the user's favorite channels. */
    data class ToggleFavorite(val channelId: String) : LiveTvPlayerUiEvent

    /** Schedules a single-episode timer for the current program. */
    data object RecordCurrentProgramOnce : LiveTvPlayerUiEvent

    /** Schedules a series timer rooted at the current program. */
    data object RecordCurrentProgramSeries : LiveTvPlayerUiEvent

    /** Cancels the single timer on the current program (if one is set). */
    data object CancelCurrentProgramTimer : LiveTvPlayerUiEvent

    /** Cancels the series timer on the current program (if one is set). */
    data object CancelCurrentProgramSeries : LiveTvPlayerUiEvent

    /** Toggles play/pause on the live engine. */
    data object TogglePlayPause : LiveTvPlayerUiEvent

    /** Jumps to the live edge. */
    data object SeekToLiveEdge : LiveTvPlayerUiEvent

    /** Seeks within the DVR window to an absolute [positionMs]. */
    data class SeekWithinDvr(val positionMs: Long) : LiveTvPlayerUiEvent

    /** Restarts the current program from the beginning of the DVR window. */
    data object PlayFromStart : LiveTvPlayerUiEvent

    /** Polls the engine's live window (the screen's 500 ms seek-bar refresh). */
    data object RefreshPosition : LiveTvPlayerUiEvent

    /** Toggles mute (pre-mute volume remembered on the [LiveMuteMemory] chip). */
    data object ToggleMute : LiveTvPlayerUiEvent

    /** Re-tunes the current channel, optionally re-resolving with stream overrides. */
    data class Retry(
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
    ) : LiveTvPlayerUiEvent

    /** Sets the live stream delivery option (auto/direct/transcode) and re-resolves the channel. */
    data class SetLiveStreamOption(val option: LiveStreamOption) : LiveTvPlayerUiEvent
}
