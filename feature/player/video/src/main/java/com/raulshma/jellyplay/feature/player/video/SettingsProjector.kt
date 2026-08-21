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
        var subtitleStyleChanged = false

        // Note: three former projections moved out when their fields' homes
        // moved to the owning controllers — the per-item audio/subtitle
        // override flags (now TrackSelectionHelper.onStoredSelectionChanged),
        // `sleepTimerLastUsedDurationMs` (now SleepTimerController
        // .seedLastUsedDurationMs) and `defaultSearchLanguage` (now
        // SubtitleManager.seedDefaultSearchLanguage). The VM's aggregate
        // collector routes those.

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

        // Slice-aware distinct-until-changed guards. The former generic
        // `diff` property-select helper cannot reach stored slice leaves
        // (KProperty1<VideoPlayerUiState,*> does not traverse the slice),
        // so each leaf uses a slice-scoped helper that preserves the
        // single-copy guard without duplicating the if/update shape.
        syncUiPref(
            selector = { it.showPlaybackMetadata },
            newValue = agg.videoPlayer.videoShowPlaybackMetadata,
            updater = { prefs, v -> prefs.copy(showPlaybackMetadata = v) },
        )
        syncUiPref(
            selector = { it.showClock },
            newValue = agg.videoPlayer.showClockInPlayer,
            updater = { prefs, v -> prefs.copy(showClock = v) },
        )
        syncUiPref(
            selector = { it.showTimeRemaining },
            newValue = agg.videoPlayer.showTimeRemaining,
            updater = { prefs, v -> prefs.copy(showTimeRemaining = v) },
        )
        syncVideoFxPref(
            selector = { it.tvZoomModePercent },
            newValue = agg.videoPlayer.tvZoomModePercent,
            updater = { fx, v -> fx.copy(tvZoomModePercent = v) },
        )
        syncUiPref(
            selector = { it.keepScreenOnDuringVideo },
            newValue = agg.playback.keepScreenOnDuringVideo,
            updater = { prefs, v -> prefs.copy(keepScreenOnDuringVideo = v) },
        )
        syncUiPref(
            selector = { it.passOutProtectionHours },
            newValue = agg.videoPlayer.videoPassOutProtectionHours,
            updater = { prefs, v -> prefs.copy(passOutProtectionHours = v) },
        )
        syncAutoplayPref(
            selector = { it.autoPlayCountdownSec },
            newValue = agg.playback.autoPlayCountdownSec,
            updater = { ap, v -> ap.copy(autoPlayCountdownSec = v) },
        )

        // PIN lock: two uiPrefs leaves driven by one pref + one derived flag.
        val hasPin = agg.security.pinHash != null
        if (getUiState().uiPrefs.usePinForPlayerLock != agg.security.usePinForPlayerLock || getUiState().uiPrefs.hasPin != hasPin) {
            updateUiState { it.copy(uiPrefs = it.uiPrefs.copy(usePinForPlayerLock = agg.security.usePinForPlayerLock, hasPin = hasPin)) }
        }

        return subtitleStyleChanged
    }

    private inline fun <T> syncUiPref(
        selector: (com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState) -> T,
        newValue: T,
        crossinline updater: (com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState, T) -> com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState,
    ) {
        val current = selector(getUiState().uiPrefs)
        if (current != newValue) {
            updateUiState { it.copy(uiPrefs = updater(it.uiPrefs, newValue)) }
        }
    }

    private inline fun <T> syncVideoFxPref(
        selector: (com.raulshma.jellyplay.feature.player.video.state.VideoFxState) -> T,
        newValue: T,
        crossinline updater: (com.raulshma.jellyplay.feature.player.video.state.VideoFxState, T) -> com.raulshma.jellyplay.feature.player.video.state.VideoFxState,
    ) {
        val current = selector(getUiState().videoFx)
        if (current != newValue) {
            updateUiState { it.copy(videoFx = updater(it.videoFx, newValue)) }
        }
    }

    private inline fun <T> syncAutoplayPref(
        selector: (com.raulshma.jellyplay.feature.player.video.state.AutoplayState) -> T,
        newValue: T,
        crossinline updater: (com.raulshma.jellyplay.feature.player.video.state.AutoplayState, T) -> com.raulshma.jellyplay.feature.player.video.state.AutoplayState,
    ) {
        val current = selector(getUiState().autoplay)
        if (current != newValue) {
            updateUiState { it.copy(autoplay = updater(it.autoplay, newValue)) }
        }
    }
}
