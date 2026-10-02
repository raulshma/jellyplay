package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Encapsulates the "auto-advance to the next episode" decision state that
 * previously lived as two loose `var`s on `VideoPlayerViewModel`
 * (`autoplayNext` / `autoplayCancelled`).
 *
 * Extracted from the ViewModel so the autoplay state machine and its
 * decision rules are unit-testable in isolation. The ViewModel remains the
 * source of truth for the UI-facing mirror fields (`videoAutoplayNext` /
 * `autoplayCancelled` in `VideoPlayerUiState`); it drives this controller and
 * then reflects the change into UI state for rendering.
 *
 * Decision rules (preserved verbatim from the inline logic):
 *  - On natural playback end, auto-advance only when a next episode exists,
 *    autoplay is enabled, and the user has not dismissed the countdown.
 *  - An explicit "skip credits" press auto-advances when a next episode exists
 *    and autoplay is enabled, regardless of the countdown dismissal (the user
 *    took a deliberate action).
 *
 * **"Still watching?" episode counter (feature 1.3):** the controller also
 * owns the unattended-binge streak — [recordAutoAdvance] increments it on each
 * autoplay-driven advance, and every user-driven signal (player open, manual
 * episode navigation, user interaction) resets it via [onUserInteraction].
 * [needsStillWatchingCheck] reports when the streak reached the configured
 * threshold (0 = off); the VM's end-of-playback gate consults it before
 * advancing and raises the confirm overlay instead. The counter deliberately
 * lives OUTSIDE [resetForNewItem]: an auto-advance load runs the same
 * new-item reset, and its streak must survive it.
 */
internal class AutoPlayController {
    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var cancelled: Boolean = false
        private set

    @Volatile
    private var consecutiveAutoPlays: Int = 0

    /** Consecutive auto-plays before the confirm prompt; <= 0 disables the counter. */
    @Volatile
    private var stillWatchingThreshold: Int = 0

    /** Mirrors `UserPreferences.videoAutoplayNext` once it is loaded/synced. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Seeds the still-watching episode threshold (0 = off). Idempotent. */
    fun setStillWatchingThreshold(threshold: Int) {
        stillWatchingThreshold = threshold.coerceAtLeast(0)
    }

    /** User dismissed the upcoming-episode countdown. */
    fun cancel() {
        cancelled = true
    }

    /** A fresh item is loading — re-arm the countdown. */
    fun resetForNewItem() {
        cancelled = false
    }

    /**
     * A user-driven signal (player open, manual episode navigation, or the
     * engine-coordinator's interaction event): the user is present, so the
     * unattended streak restarts from zero.
     */
    fun onUserInteraction() {
        consecutiveAutoPlays = 0
    }

    /** An autoplay-driven advance succeeded — the unattended streak grows. */
    fun recordAutoAdvance() {
        consecutiveAutoPlays += 1
    }

    /**
     * True when the episode arm's threshold is armed (a positive threshold
     * that the still-watching mode includes) and the unattended streak
     * reached it — the VM's end-of-playback gate then raises the
     * "Still watching?" confirm overlay instead of advancing.
     */
    fun needsStillWatchingCheck(): Boolean =
        stillWatchingThreshold > 0 && consecutiveAutoPlays >= stillWatchingThreshold

    /**
     * Natural end-of-playback rule: advance only when a next episode exists,
     * autoplay is enabled, and the countdown was not cancelled.
     */
    fun shouldAutoPlayNext(nextEpisode: MediaItem?): Boolean =
        nextEpisode != null && enabled && !cancelled

    /**
     * Explicit skip-credits rule: advance when a next episode exists and
     * autoplay is enabled (the user took a deliberate action, so a previously
     * dismissed countdown does not block it).
     */
    fun canSkipToNext(nextEpisode: MediaItem?): Boolean =
        nextEpisode != null && enabled
}
