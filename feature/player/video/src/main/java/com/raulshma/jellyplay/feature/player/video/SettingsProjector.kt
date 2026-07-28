package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.UserPreferences

/**
 * Projects the user-preferences slice of [VideoPlayerUiState]: reads the latest
 * [UserPreferences] + the current uiState and applies only the fields that
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
    fun project(prefs: UserPreferences): Boolean {
        val state = getUiState()
        var subtitleStyleChanged = false

        // Per-item audio/subtitle override flags — derived from the stored
        // selection for the current item. Skipped wholesale when unchanged
        // (the most common case: these rarely flip).
        val stored = getItemId()?.let { prefs.mediaStreamSelections[it] }
        val newAudio = stored?.audioStreamIndex != null
        val newSub = stored?.subtitleStreamIndex != null
        if (state.hasAudioOverride != newAudio || state.hasSubtitleOverride != newSub) {
            updateUiState { it.copy(hasAudioOverride = newAudio, hasSubtitleOverride = newSub) }
        }

        // HDR-aware subtitle style: the resolved style depends on whether the
        // current streams carry HDR, so re-derive on every prefs tick. The VM
        // must rebuild the engine config when this changes.
        val resolvedSubtitleStyle = prefs.resolvedSubtitleStyle(
            isHdr = prefs.isHdrFromStreams(getMediaStreams()),
        )
        if (getUiState().subtitleStyle != resolvedSubtitleStyle) {
            updateUiState { it.copy(subtitleStyle = resolvedSubtitleStyle) }
            subtitleStyleChanged = true
        }

        diff(prefs.sleepTimerDurationMs, VideoPlayerUiState::sleepTimerLastUsedDurationMs) {
            it.copy(sleepTimerLastUsedDurationMs = prefs.sleepTimerDurationMs)
        }
        diff(prefs.videoShowPlaybackMetadata, VideoPlayerUiState::showPlaybackMetadata) {
            it.copy(showPlaybackMetadata = prefs.videoShowPlaybackMetadata)
        }
        diff(prefs.showClockInPlayer, VideoPlayerUiState::showClock) {
            it.copy(showClock = prefs.showClockInPlayer)
        }
        diff(prefs.showTimeRemaining, VideoPlayerUiState::showTimeRemaining) {
            it.copy(showTimeRemaining = prefs.showTimeRemaining)
        }
        diff(prefs.tvZoomModePercent, VideoPlayerUiState::tvZoomModePercent) {
            it.copy(tvZoomModePercent = prefs.tvZoomModePercent)
        }
        diff(prefs.keepScreenOnDuringVideo, VideoPlayerUiState::keepScreenOnDuringVideo) {
            it.copy(keepScreenOnDuringVideo = prefs.keepScreenOnDuringVideo)
        }
        diff(prefs.videoPassOutProtectionHours, VideoPlayerUiState::passOutProtectionHours) {
            it.copy(passOutProtectionHours = prefs.videoPassOutProtectionHours)
        }
        diff(prefs.autoPlayCountdownSec, VideoPlayerUiState::autoPlayCountdownSec) {
            it.copy(autoPlayCountdownSec = prefs.autoPlayCountdownSec)
        }

        // PIN lock: two uiState fields driven by one pref + one derived flag.
        val hasPin = prefs.pinHash != null
        if (getUiState().usePinForPlayerLock != prefs.usePinForPlayerLock || getUiState().hasPin != hasPin) {
            updateUiState { it.copy(usePinForPlayerLock = prefs.usePinForPlayerLock, hasPin = hasPin) }
        }

        // Default the Subtitle Manager's Search-tab language to the user's
        // preferred subtitle language (ISO 639-2/3, e.g. "eng").
        val searchLang = prefs.preferredSubtitleLanguage ?: DEFAULT_SEARCH_LANGUAGE
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
