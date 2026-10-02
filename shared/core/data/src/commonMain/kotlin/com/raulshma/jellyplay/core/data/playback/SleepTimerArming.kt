package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The ONE sleep-timer ARMING state machine behind the player feature
 * controllers (video's `SleepTimerController`, audio's
 * `AudioSleepTimerController`): the per-mode arm/cancel choreography both
 * previously hand-copied — the [AudioStore] preference writes (last-used
 * duration + end-of-episode flag), the expiry/ramp callback arming over
 * [SleepCountdown], and the countdown start/cancel/trigger dispatch.
 *
 * What is deliberately NOT here: the hosts' UI-facing surfaces. The video
 * controller keeps its pre-fade volume capture/restore, its mute-gated fade
 * lambda and its own [com.raulshma.jellyplay.feature.player.video.state.SleepTimerState]
 * slice; the audio controller keeps its explicit-pause rationale and its
 * uiState `SleepTimerState` updates. Both pass their expiry action as
 * [onExpirePause] (video reads the CURRENT engine per expiry — it swaps
 * engines on retry; audio pauses its fixed engine) and their per-mode ramp
 * as the [armTimed] fade parameter.
 *
 * Ordering contract (pinned by SleepTimerArmingTest): the callbacks are armed
 * BEFORE the countdown starts, so a zero-duration arm expires into a live
 * callback, and the preference writes launch first — the exact order both
 * controllers ran inline.
 *
 * Not a Koin type: hosts construct it over their own scope beside the
 * [SleepCountdown] they already receive.
 */
class SleepTimerArming(
    private val sleepCountdown: SleepCountdown,
    private val audioStore: AudioStore,
    private val scope: CoroutineScope,
    private val onExpirePause: () -> Unit,
) {

    /**
     * Start a timed countdown for [durationMs]: persist it as the last-used
     * duration (so the picker re-offers it), clear the end-of-episode flag,
     * arm [onExpirePause] as the expiry action and [fade] as the final-stretch
     * ramp (`null` for hosts without a fade), then start the countdown.
     */
    fun armTimed(durationMs: Long, fade: ((Float) -> Unit)?) {
        scope.launch {
            audioStore.setSleepTimerDurationMs(durationMs)
            audioStore.setSleepTimerEndOfEpisode(false)
        }
        sleepCountdown.setOnTimerExpired(onExpirePause)
        sleepCountdown.setOnExpiring(fade)
        sleepCountdown.startSleepTimer(durationMs)
    }

    /**
     * Arm the end-of-episode boundary timer: persist the flag, arm
     * [onExpirePause], clear any ramp (no fade on this mode) and start the
     * boundary arm.
     */
    fun armEndOfEpisode() {
        scope.launch {
            audioStore.setSleepTimerEndOfEpisode(true)
        }
        sleepCountdown.setOnTimerExpired(onExpirePause)
        sleepCountdown.setOnExpiring(null)
        sleepCountdown.startEndOfEpisodeTimer()
    }

    /** Cancel the active arm (either mode) — the countdown fires the hosts' restore pulses. */
    fun disarm() {
        sleepCountdown.cancelSleepTimer()
    }

    /** Fire the end-of-episode pause; the mode + active guard lives on [SleepCountdown]. */
    fun triggerEndOfEpisode() {
        sleepCountdown.triggerEndOfEpisode()
    }

    /** Tear the ramp down so a released engine is never touched by a stray tick. */
    fun release() {
        sleepCountdown.setOnExpiring(null)
    }
}
