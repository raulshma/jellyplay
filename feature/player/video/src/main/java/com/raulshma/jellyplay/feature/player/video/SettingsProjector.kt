package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate

/**
 * Projects the user-preferences slice of [VideoPlayerUiState]: reads the latest
 * [VideoPlayerAggregate] + the current uiState and applies only the fields that
 * actually changed, preserving the distinct-until-changed guards the inline
 * collector previously hand-wrote per field.
 *
 * Extracted from the `init { preferencesStore.preferences.collect { … } }`
 * block of [VideoPlayerViewModel]. That collector mixed two concerns:
 *  1. **Pure projection** of ~12 prefs → uiState fields (this class), and
 *  2. **Side effects** triggered by pref changes — rebuilding the engine
 *     config (`updateConfigWithUiState`), toggling autoplay
 *     (`autoplayController.setEnabled`), and registering/unregistering audio
 *     focus. Those stay in the VM: they are reactions, not state copies.
 *
 * [project] returns the set of fields the VM must still react to (today: only
 * a subtitle-style change needs an engine-config rebuild) so the VM keeps its
 * side-effect block without re-deriving the diff.
 *
 * Engine + session access is via lambdas so this class stays ViewModel-agnostic.
 * The `getItemId` / `getMediaStreams` reads feed the per-item audio/subtitle
 * override + HDR-aware subtitle-style resolution.
 */
internal class SettingsProjector(
    private val getUiState: () -> VideoPlayerUiState,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
    private val getItemId: () -> String?,
    private val getMediaStreams: () -> List<com.raulshma.jellyplay.core.model.MediaStream>,
) {

    /**
     * Apply the prefs-derived uiState slice. Each field is guarded so an
     * unrelated pref write (dozens emit UserPreferences) does not allocate a
     * fresh uiState copy or re-emit to every collector.
     *
     * Returns `true` when the resolved subtitle style changed — the VM must
     * then rebuild the engine config (`updateConfigWithUiState`); every other
     * projected field has no downstream side effect.
     */
    fun project(agg: VideoPlayerAggregate): Boolean {
        val state = getUiState()
        var subtitleStyleChanged = false

        // Per-item audio/subtitle override flags — derived from the stored
        // selection for the current item. Skipped wholesale when unchanged
        // (the most common case: these rarely flip).
        val stored = getItemId()?.let { agg.engine.mediaStreamSelections[it] }
        val newAudio = stored?.audioStreamIndex != null
        val newSub = stored?.subtitleStreamIndex != null
        if (state.hasAudioOverride != newAudio || state.hasSubtitleOverride != newSub) {
            updateUiState { it.copy(hasAudioOverride = newAudio, hasSubtitleOverride = newSub) }
        }

        // HDR-aware subtitle style: the resolved style depends on whether the
        // current streams carry HDR, so re-derive on every prefs tick. The VM
        // must rebuild the engine config when this changes.
        //
        // Subtitle delay is per-media and authoritative via the per-item store
        // (written by setSubtitleDelay), NOT the global style bucket. Preserve
        // the resolved per-item delay here so a DataStore re-emission — including
        // the very write that just stored this delay — can't clobber the live
        // offsetMs back to the global default. Without this, writing the delay
        // re-emits the slice, this projector overwrites offsetMs to the global
        // default (often 0), and the resulting config push reloads media with the
        // delay removed. Mirrors the engineFlow collector's resolve step.
        val resolvedSubtitleStyle = resolveSubtitleStyleWithDelay(
            agg.subtitle,
            getItemId(),
            isHdr = isHdrFromStreams(getMediaStreams()),
        )
        if (getUiState().subtitleStyle != resolvedSubtitleStyle) {
            updateUiState { it.copy(subtitleStyle = resolvedSubtitleStyle) }
            subtitleStyleChanged = true
        }

        diff(agg.audio.sleepTimerDurationMs, VideoPlayerUiState::sleepTimerLastUsedDurationMs) {
            it.copy(sleepTimerLastUsedDurationMs = agg.audio.sleepTimerDurationMs)
        }
        diff(agg.videoPlayer.videoShowPlaybackMetadata, VideoPlayerUiState::showPlaybackMetadata) {
            it.copy(showPlaybackMetadata = agg.videoPlayer.videoShowPlaybackMetadata)
        }
        diff(agg.videoPlayer.showClockInPlayer, VideoPlayerUiState::showClock) {
            it.copy(showClock = agg.videoPlayer.showClockInPlayer)
        }
        diff(agg.videoPlayer.showTimeRemaining, VideoPlayerUiState::showTimeRemaining) {
            it.copy(showTimeRemaining = agg.videoPlayer.showTimeRemaining)
        }
        diff(agg.videoPlayer.tvZoomModePercent, VideoPlayerUiState::tvZoomModePercent) {
            it.copy(tvZoomModePercent = agg.videoPlayer.tvZoomModePercent)
        }
        diff(agg.playback.keepScreenOnDuringVideo, VideoPlayerUiState::keepScreenOnDuringVideo) {
            it.copy(keepScreenOnDuringVideo = agg.playback.keepScreenOnDuringVideo)
        }
        diff(agg.videoPlayer.videoPassOutProtectionHours, VideoPlayerUiState::passOutProtectionHours) {
            it.copy(passOutProtectionHours = agg.videoPlayer.videoPassOutProtectionHours)
        }
        diff(agg.playback.autoPlayCountdownSec, VideoPlayerUiState::autoPlayCountdownSec) {
            it.copy(autoPlayCountdownSec = agg.playback.autoPlayCountdownSec)
        }

        // PIN lock: two uiState fields driven by one pref + one derived flag.
        val hasPin = agg.security.pinHash != null
        if (getUiState().usePinForPlayerLock != agg.security.usePinForPlayerLock || getUiState().hasPin != hasPin) {
            updateUiState { it.copy(usePinForPlayerLock = agg.security.usePinForPlayerLock, hasPin = hasPin) }
        }

        // Default the Subtitle Manager's Search-tab language to the user's
        // preferred subtitle language (ISO 639-2/3, e.g. "eng").
        val searchLang = agg.subtitle.preferredSubtitleLanguage ?: DEFAULT_SEARCH_LANGUAGE
        if (getUiState().defaultSearchLanguage != searchLang) {
            updateUiState { it.copy(defaultSearchLanguage = searchLang) }
        }

        return subtitleStyleChanged
    }

    /**
     * Single-field diff helper: apply [copy] only when the current value of
     * [selector] differs from [newValue]. Collapses the repeated 3-line
     * `if (_uiState.value.X != prefs.X) _uiState.update { it.copy(…) }` pattern
     * while keeping the distinct-until-changed guard per field.
     */
    private fun <T> diff(
        newValue: T,
        selector: kotlin.reflect.KProperty1<VideoPlayerUiState, T>,
        copy: (VideoPlayerUiState) -> VideoPlayerUiState,
    ) {
        if (selector.get(getUiState()) != newValue) {
            updateUiState(copy)
        }
    }

    private companion object {
        const val DEFAULT_SEARCH_LANGUAGE = "eng"
    }
}
