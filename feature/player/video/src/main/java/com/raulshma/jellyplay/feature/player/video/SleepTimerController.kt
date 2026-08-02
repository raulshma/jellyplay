package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the in-player sleep-timer workflow: the two start modes (timed fade-out
 * and end-of-episode), cancel (with pre-fade volume restore), and the
 * end-of-episode trigger fired by the autoplay controller when the credits
 * outro is reached.
 *
 * Extracted from [VideoPlayerViewModel], continuing the collaborator pattern
 * established by [SubtitleManager] / [VideoEffectsController]. The
 * [SleepTimerManager] singleton already owns the countdown + fade ramp +
 * `remainingMs` StateFlow; this class owns only the residue that lived in the
 * VM:
 *  - `preSleepVolume` capture/restore (so cancel never slams to full volume),
 *  - the two callbacks closing over the current engine (pause on expire,
 *    setVolume on each fade tick — both skip writes while the user is muted),
 *  - the sleep-timer slice of [VideoPlayerUiState] (`sleepTimerActive`,
 *    `sleepTimerEndOfEpisode`, `sleepTimerLastUsedDurationMs`), and
 *  - the two preference writes (`setSleepTimerDurationMs` /
 *    `setSleepTimerEndOfEpisode`).
 *
 * Engine + mute access is via lambdas so this class stays ViewModel-agnostic
 * and always reads the *current* engine (the VM swaps engines on retry).
 */
internal class SleepTimerController(
    private val sleepTimerManager: SleepTimerManager,
    private val audioStore: AudioStore,
    private val scope: CoroutineScope,
    private val getEngine: () -> com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?,
    private val isMuted: () -> Boolean,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
) {

    /**
     * Volume captured when a timed timer starts fading, so [cancelSleepTimer]
     * restores the user's level instead of slamming to 1f. Null while muted
     * (mute wins — the fade also skips writes while muted, so there is nothing
     * to restore) or for the end-of-episode timer (no fade, nothing to restore).
     */
    private var preSleepVolume: Float? = null

    /**
     * Start a countdown that fades the volume out over the final stretch and
     * pauses on expiry. [durationMs] is persisted as the last-used duration so
     * the picker can re-offer it.
     */
    fun startSleepTimer(durationMs: Long) {
        scope.launch {
            audioStore.setSleepTimerDurationMs(durationMs)
            audioStore.setSleepTimerEndOfEpisode(false)
        }
        // Capture the pre-fade volume before the fade ramp lowers it. Skip
        // capture while muted (the fade also skips writes when muted, so there
        // is nothing to restore). Mirrors preDuckVolume in the audio-focus path.
        val engineForCapture = getEngine()
        preSleepVolume = if (engineForCapture != null && !isMuted()) {
            engineForCapture.volume
        } else {
            null
        }
        sleepTimerManager.setOnTimerExpired { getEngine()?.pause() }
        sleepTimerManager.setOnFadeProgress { progress ->
            // Skip volume writes while user-muted; let mute state win.
            if (!isMuted()) getEngine()?.setVolume(progress)
        }
        sleepTimerManager.start(durationMs)
        updateUiState {
            it.copy(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = false,
                sleepTimerLastUsedDurationMs = durationMs,
            )
        }
    }

    /**
     * Start an end-of-episode timer: no countdown display, no fade — pauses the
     * moment the autoplay controller reports the credits outro is reached
     * ([triggerSleepTimerEndOfEpisode]).
     */
    fun startSleepTimerEndOfEpisode() {
        scope.launch {
            audioStore.setSleepTimerEndOfEpisode(true)
        }
        // End-of-episode timer has no fade, so there is no pre-fade level to
        // restore on cancel. Clear any value captured by a prior timed timer
        // so cancelSleepTimer leaves the current volume untouched instead of
        // restoring a stale captured level.
        preSleepVolume = null
        sleepTimerManager.setOnTimerExpired { getEngine()?.pause() }
        sleepTimerManager.setOnFadeProgress(null)
        sleepTimerManager.startEndOfEpisode()
        updateUiState {
            it.copy(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = true,
            )
        }
    }

    /**
     * Cancel the active timer (either mode). Restores the pre-fade volume
     * captured by [startSleepTimer] — never slams to 1f and never overrides an
     * active user mute. If no level was captured (muted at start, or
     * end-of-episode timer with no fade), the current volume is left untouched.
     */
    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        val engine = getEngine()
        if (engine != null && !isMuted()) {
            preSleepVolume?.let { engine.setVolume(it) }
        }
        preSleepVolume = null
        updateUiState {
            it.copy(
                sleepTimerActive = false,
                sleepTimerEndOfEpisode = false,
            )
        }
    }

    /**
     * Fire the end-of-episode pause. No-op unless an end-of-episode timer is
     * active (the autoplay controller invokes this when the credits outro is
     * reached). Delegates the mode + active guard to [SleepTimerManager].
     */
    fun triggerSleepTimerEndOfEpisode() {
        sleepTimerManager.triggerEndOfEpisode()
    }

    /** Tear down callbacks so a released engine is never touched by a stray tick. */
    fun onRelease() {
        sleepTimerManager.setOnFadeProgress(null)
    }
}
