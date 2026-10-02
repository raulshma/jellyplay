package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaStreamSelection

/**
 * Routes one [VideoPlayerAggregate] emission to the side-effecting pref
 * consumers — the half of the aggregate collector the [SettingsProjector]
 * extraction deliberately left in the VM (P4). The VM's collector is now the
 * three-line `cachedAggregate` bookkeeping plus ONE call to
 * [onAggregateChanged]; everything below was the former inline body, in the
 * same order:
 *
 *  1. the pure prefs → uiState projection ([projectPrefs] — still
 *     [SettingsProjector.project]; its Boolean says whether the resolved
 *     subtitle style changed),
 *  2. the three controller seeds (sleep-timer last-used duration, the
 *     per-item audio/subtitle override flags, the Subtitle Manager's default
 *     search language) — now diff-guarded: each fires only when ITS pref
 *     actually changed. Every seed is an idempotent, internally-guarded
 *     setter, so gating them by diff preserves the observable behavior while
 *     an unrelated pref write (dozens emit the aggregate) no longer re-runs
 *     them,
 *  3. the engine-config rebuild triggers (subtitle-style change →
 *     [rebuildEngineConfigIfRunning]; volume-boost / equalizer-settings
 *     diffs → ditto), engine-null-guarded VM-side
 *     (no engine ⇒ no rebuild, exactly the old `engine?.let` posture),
 *  4. the autoplay-controller flip (guarded on the VM's applied-mirror read —
 *     [isAutoplayNextApplied] — then uiState copy + controller flip through
 *     [applyAutoplayNextPref], in that order, as before),
 *  5. the video focus-policy push (the video focus slice): the two focus
 *     prefs ride EVERY emission into [applyVideoFocusPolicy] — the module-side
 *     setter is idempotent, so an unrelated pref write costs nothing. The OS
 *     seat is gated on the prefs' OR (the legacy duck seat was registered on
 *     the duck pref ALONE, so pause-off/duck-on still took the seat); the
 *     loss directive is the duck pref itself. The legacy
 *     `duckOnTransientFocusLoss` register/unregister diff died with
 *     the seat move (the seat belongs to the PlaybackFocus module now); the
 *     `pauseOnAudioFocusLoss` diff no longer rebuilds the engine config
 *     either — no engine consumes it since ExoPlayer's built-in
 *     `handleAudioFocus` was forced off.
 *
 * Engine + session + uiState access is via constructor lambdas so this class
 * stays ViewModel-agnostic (the SettingsProjector seam shape — plain values
 * in, named single-purpose lambdas out; jvmTest-pinned with fakes in
 * PlayerPrefsFanoutTest). The load-time counterpart remains [PlayerPrefsSeed].
 */
internal class PlayerPrefsFanout(
    private val projectPrefs: (VideoPlayerAggregate) -> Boolean,
    private val getCurrentItemId: () -> String?,
    private val seedSleepTimerLastUsedMs: (Long) -> Unit,
    private val onStoredSelectionChanged: (MediaStreamSelection?) -> Unit,
    private val seedDefaultSearchLanguage: (String) -> Unit,
    /** True when the uiState mirror already carries [applied] (the flip guard). */
    private val isAutoplayNextApplied: (Boolean) -> Boolean,
    /** The flip itself: uiState mirror write first, then the controller. */
    private val applyAutoplayNextPref: (Boolean) -> Unit,
    private val rebuildEngineConfigIfRunning: () -> Unit,
    /**
     * The video focus-policy push (`osLegEnabled`, `duckOnTransientLoss`) —
     * derived from the incoming aggregate's two focus prefs and handed to the
     * PlaybackFocus module's [VideoFocusPolicyInput][com.raulshma.jellyplay.core.data.playback.focus.VideoFocusPolicyInput]
     * by the wiring. `osLegEnabled` is the prefs' OR (either pref on keeps
     * the module's OS seat — the legacy duck seat ran on the duck pref
     * alone); `duckOnTransientLoss` is the duck pref directly. Idempotent at
     * the receiver; invoked on every emission.
     */
    private val applyVideoFocusPolicy: (osLegEnabled: Boolean, duckOnTransientLoss: Boolean) -> Unit,
) {

    /**
     * Folds one aggregate emission. [old] is the previously cached aggregate
     * (the VM's `cachedAggregate` before this emission), [new] the incoming
     * one — the diff source for every guard below.
     */
    fun onAggregateChanged(old: VideoPlayerAggregate, new: VideoPlayerAggregate) {
        // Pure prefs → uiState projection (each field guarded so an
        // unrelated pref emission does not re-emit to every collector).
        val subtitleStyleChanged = projectPrefs(new)

        // Prefs seeds for controller-owned slices (the projections
        // SettingsProjector used to apply to the flat fields), diff-guarded:
        //  - the sleep timer's last-used duration,
        //  - the per-item audio/subtitle override flags (for the CURRENT
        //    item — the media-content projector re-seeds on item change, so
        //    guarding by the item's stored selection diff is safe), and
        //  - the Subtitle Manager's default search language.
        if (old.audio.sleepTimerDurationMs != new.audio.sleepTimerDurationMs) {
            seedSleepTimerLastUsedMs(new.audio.sleepTimerDurationMs)
        }
        val itemId = getCurrentItemId()
        val storedSelection = itemId?.let { new.engine.mediaStreamSelections[it] }
        val previousSelection = itemId?.let { old.engine.mediaStreamSelections[it] }
        if (storedSelection != previousSelection) {
            onStoredSelectionChanged(storedSelection)
        }
        val searchLanguage = new.subtitle.preferredSubtitleLanguage ?: "eng"
        if (old.subtitle.preferredSubtitleLanguage ?: "eng" != searchLanguage) {
            seedDefaultSearchLanguage(searchLanguage)
        }

        // Subtitle-style change needs an engine-config rebuild.
        if (subtitleStyleChanged) {
            rebuildEngineConfigIfRunning()
        }

        // Autoplay-next flip also toggles the autoplay controller.
        val autoplayNext = new.videoPlayer.videoAutoplayNext
        if (!isAutoplayNextApplied(autoplayNext)) {
            applyAutoplayNextPref(autoplayNext)
        }

        if (old.audioEffects.volumeBoostEnabled != new.audioEffects.volumeBoostEnabled ||
            old.audioEffects.volumeBoostGain != new.audioEffects.volumeBoostGain ||
            old.audioEffects.equalizerSettings != new.audioEffects.equalizerSettings
        ) {
            rebuildEngineConfigIfRunning()
        }

        // Video focus policy (the video focus slice): folded into this single
        // preferences collector, pushed on EVERY emission — the module-side
        // setter is idempotent. The OS seat runs when EITHER pref wants
        // OS-leg behavior (the legacy duck seat was gated on the duck pref
        // ALONE, so pause-off/duck-on still ducked through phone calls — the
        // OR keeps that); the loss DIRECTIVE stays derived from the duck pref
        // alone (duck on → Duck on transient + restore on regain — the duck
        // pref wins when both prefs are on, the legacy dual-seat race's
        // effective behavior; duck off → the pause-only default).
        applyVideoFocusPolicy(
            new.playback.pauseOnAudioFocusLoss ||
                new.playback.duckOnTransientFocusLoss,
            new.playback.duckOnTransientFocusLoss,
        )
    }
}
