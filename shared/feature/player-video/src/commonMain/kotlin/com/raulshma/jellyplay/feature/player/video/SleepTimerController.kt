package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepTimerArming
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.feature.player.video.state.SleepTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the in-player sleep-timer workflow: the two start modes (timed fade-out
 * and end-of-episode), cancel (with pre-fade volume restore), and the
 * end-of-episode trigger fired by the autoplay controller when the credits
 * outro is reached.
 *
 * Extracted from [VideoPlayerViewModel], continuing the collaborator pattern
 * established by [SubtitleManager] / [VideoEffectsController]. The countdown
 * itself is core:data's [SleepCountdown] core (the single fold home of the
 * former SleepTimerManager — its KDoc records the alias kill); this class
 * owns only the residue that lived in the VM:
 *  - `preSleepVolume` capture/restore (so cancel never slams to full volume),
 *  - the two callbacks closing over the current engine (pause on expire,
 *    setVolume on each fade tick — both skip writes while the user is muted),
 *  - the sleep-timer slice [SleepTimerState] (`sleepTimerActive`,
 *    `sleepTimerEndOfEpisode`, `sleepTimerLastUsedDurationMs`) — this class is
 *    its single home, exposed as a read-only [StateFlow], and
 *  - the two preference writes (`setSleepTimerDurationMs` /
 *    `setSleepTimerEndOfEpisode`).
 *
 * **Item-switch semantics: a running timer deliberately PERSISTS across
 * episodes** (it was whitelisted in the ViewModel's former reset ritual).
 * Persistence is the default — there is no `resetForItem()`. Only full VM
 * teardown (`onRelease`) detaches the fade callback.
 *
 * Engine + mute access is via lambdas so this class stays ViewModel-agnostic
 * and always reads the *current* engine (the VM swaps engines on retry).
 */
internal class SleepTimerController(
    private val sleepCountdown: SleepCountdown,
    private val audioStore: AudioStore,
    private val scope: CoroutineScope,
    private val getEngine: () -> com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?,
    private val isMuted: () -> Boolean,
) {

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    /**
     * The shared core:data arming machine (the [SleepTimerArming] fold of the
     * store writes + countdown dispatch both player hosts hand-copied): this
     * class keeps only the video-specific residue — pre-fade capture, the
     * mute-gated fade lambda and the [SleepTimerState] slice.
     */
    private val arming = SleepTimerArming(
        sleepCountdown = sleepCountdown,
        audioStore = audioStore,
        scope = scope,
        // Fresh engine read per expiry — the VM swaps engines on retry.
        onExpirePause = { getEngine()?.pause() },
    )

    /**
     * Countdown display, sourced directly from [SleepCountdown]. Kept OUT
     * of any wide state bag (and out of [state]) so a 5 s tick — or the 100 ms
     * fade-out burst — re-invalidates only the leaf composables that render
     * the countdown (overflow-menu label, SleepTimerSheet).
     */
    val remainingMs: StateFlow<Long> get() = sleepCountdown.sleepTimerRemainingMs

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
        // Capture the pre-fade volume before the fade ramp lowers it. Skip
        // capture while muted (the fade also skips writes when muted, so there
        // is nothing to restore). Mirrors preDuckVolume in the audio-focus path.
        val engineForCapture = getEngine()
        preSleepVolume = if (engineForCapture != null && !isMuted()) {
            engineForCapture.volume
        } else {
            null
        }
        arming.armTimed(durationMs, fade = { progress ->
            // Skip volume writes while user-muted; let mute state win. The
            // fade is PROGRAMMATIC: engines with per-content-type
            // volume memory must not capture it as a user level.
            if (!isMuted()) getEngine()?.setVolume(progress, isUserChange = false)
        })
        _state.update {
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
        // End-of-episode timer has no fade, so there is no pre-fade level to
        // restore on cancel. Clear any value captured by a prior timed timer
        // so cancelSleepTimer leaves the current volume untouched instead of
        // restoring a stale captured level.
        preSleepVolume = null
        arming.armEndOfEpisode()
        _state.update {
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
        arming.disarm()
        val engine = getEngine()
        if (engine != null && !isMuted()) {
            // Restore is programmatic too — cancel must not look like the user
            // re-chose the pre-fade level.
            preSleepVolume?.let { engine.setVolume(it, isUserChange = false) }
        }
        preSleepVolume = null
        _state.update {
            it.copy(
                sleepTimerActive = false,
                sleepTimerEndOfEpisode = false,
            )
        }
    }

    /**
     * Fire the end-of-episode pause. No-op unless an end-of-episode timer is
     * active (the autoplay controller invokes this when the credits outro is
     * reached). Delegates the mode + active guard to [SleepCountdown].
     */
    fun triggerSleepTimerEndOfEpisode() {
        arming.triggerEndOfEpisode()
    }

    /**
     * Seeds [SleepTimerState.sleepTimerLastUsedDurationMs] from the persisted
     * [AudioStore] preference. The former projection lived in
     * [SettingsProjector]; it moves here because the field's home moved. Guarded
     * so an unrelated preference emission does not re-emit the state flow.
     */
    fun seedLastUsedDurationMs(durationMs: Long) {
        if (_state.value.sleepTimerLastUsedDurationMs != durationMs) {
            _state.update { it.copy(sleepTimerLastUsedDurationMs = durationMs) }
        }
    }

    /** Tear down callbacks so a released engine is never touched by a stray tick. */
    fun onRelease() {
        arming.release()
    }
}
