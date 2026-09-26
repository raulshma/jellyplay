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
 *     [rebuildEngineConfigIfRunning]; volume-boost / equalizer-settings /
 *     pause-on-audio-focus-loss diffs → ditto), engine-null-guarded VM-side
 *     (no engine ⇒ no rebuild, exactly the old `engine?.let` posture),
 *  4. the autoplay-controller flip (guarded on the VM's applied-mirror read —
 *     [isAutoplayNextApplied] — then uiState copy + controller flip through
 *     [applyAutoplayNextPref], in that order, as before),
 *  5. the audio-focus duck registration for phone-call transient loss.
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
    private val isAudioFocusActive: () -> Boolean,
    private val registerAudioFocus: () -> Unit,
    private val unregisterAudioFocus: () -> Unit,
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
            old.audioEffects.equalizerSettings != new.audioEffects.equalizerSettings ||
            old.playback.pauseOnAudioFocusLoss != new.playback.pauseOnAudioFocusLoss
        ) {
            rebuildEngineConfigIfRunning()
        }

        // Duck on transient audio focus loss (phone calls). Folded into
        // this single preferences collector (was a duplicate collector)
        // so a pref write rebuilds the snapshot once, not twice.
        if (new.playback.duckOnTransientFocusLoss && !isAudioFocusActive()) {
            registerAudioFocus()
        } else if (!new.playback.duckOnTransientFocusLoss && isAudioFocusActive()) {
            unregisterAudioFocus()
        }
    }
}
