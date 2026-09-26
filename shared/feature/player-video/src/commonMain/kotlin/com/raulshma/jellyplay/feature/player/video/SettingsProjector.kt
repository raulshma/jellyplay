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
 *
 * The *load-time* counterpart of this *change-time* projection is
 * [PlayerPrefsSeed]: the single home of which pref feeds which uiState leaf at
 * session start (one unguarded copy per load). Seven leaves are mapped in both
 * places (showPlaybackMetadata, showClock, showTimeRemaining, tvZoomModePercent,
 * keepScreenOnDuringVideo, passOutProtectionHours, autoPlayCountdownSec) — when
 * moving or adding a leaf, update both or drop one side deliberately. This
 * class's guarded-diff semantics are intentionally NOT merged into the seed.
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

        // Distinct-until-changed guards, all through the one generic
        // [syncPref]: the selector/updater lambdas compose the slice
        // traversal (KProperty1<VideoPlayerUiState,*> does not traverse the
        // slice, so a property-ref helper cannot reach stored slice leaves),
        // preserving the single-copy guard for a leaf in any slice.
        syncPref(
            selector = { it.uiPrefs.showPlaybackMetadata },
            newValue = agg.videoPlayer.videoShowPlaybackMetadata,
            updater = { v -> copy(uiPrefs = uiPrefs.copy(showPlaybackMetadata = v)) },
        )
        syncPref(
            selector = { it.uiPrefs.showClock },
            newValue = agg.videoPlayer.showClockInPlayer,
            updater = { v -> copy(uiPrefs = uiPrefs.copy(showClock = v)) },
        )
        syncPref(
            selector = { it.uiPrefs.showTimeRemaining },
            newValue = agg.videoPlayer.showTimeRemaining,
            updater = { v -> copy(uiPrefs = uiPrefs.copy(showTimeRemaining = v)) },
        )
        syncPref(
            selector = { it.videoFx.tvZoomModePercent },
            newValue = agg.videoPlayer.tvZoomModePercent,
            updater = { v -> copy(videoFx = videoFx.copy(tvZoomModePercent = v)) },
        )
        syncPref(
            selector = { it.uiPrefs.keepScreenOnDuringVideo },
            newValue = agg.playback.keepScreenOnDuringVideo,
            updater = { v -> copy(uiPrefs = uiPrefs.copy(keepScreenOnDuringVideo = v)) },
        )
        syncPref(
            selector = { it.uiPrefs.passOutProtectionHours },
            newValue = agg.videoPlayer.videoPassOutProtectionHours,
            updater = { v -> copy(uiPrefs = uiPrefs.copy(passOutProtectionHours = v)) },
        )
        syncPref(
            selector = { it.autoplay.autoPlayCountdownSec },
            newValue = agg.playback.autoPlayCountdownSec,
            updater = { v -> copy(autoplay = autoplay.copy(autoPlayCountdownSec = v)) },
        )

        // PIN lock: two uiPrefs leaves driven by one pref + one derived flag.
        val hasPin = agg.security.pinHash != null
        if (getUiState().uiPrefs.usePinForPlayerLock != agg.security.usePinForPlayerLock || getUiState().uiPrefs.hasPin != hasPin) {
            updateUiState { it.copy(uiPrefs = it.uiPrefs.copy(usePinForPlayerLock = agg.security.usePinForPlayerLock, hasPin = hasPin)) }
        }

        return subtitleStyleChanged
    }

    /**
     * The one distinct-until-changed guard shared by every leaf above, any
     * slice: [selector] reads the current leaf and [updater] writes it back,
     * each composing the slice traversal a property reference cannot express
     * (`KProperty1<VideoPlayerUiState, *>` does not reach stored slice
     * leaves, and slice `val`s are read-only). An unchanged value performs
     * no copy at all; a changed one rewrites the leaf inside a single
     * uiState copy built from the state `updateUiState` hands over.
     */
    private inline fun <V> syncPref(
        selector: (VideoPlayerUiState) -> V,
        newValue: V,
        crossinline updater: VideoPlayerUiState.(V) -> VideoPlayerUiState,
    ) {
        if (selector(getUiState()) != newValue) {
            updateUiState { it.updater(newValue) }
        }
    }
}
