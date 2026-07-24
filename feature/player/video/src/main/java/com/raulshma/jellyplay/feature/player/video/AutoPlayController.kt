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
 */
internal class AutoPlayController {
    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var cancelled: Boolean = false
        private set

    /** Mirrors `UserPreferences.videoAutoplayNext` once it is loaded/synced. */
    fun setEnabled(value: Boolean) {
        enabled = value
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
