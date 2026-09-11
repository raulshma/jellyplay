package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the audio player's sleep-timer workflow — the two start modes (timed
 * countdown and end-of-episode), the store writes for the last-used duration /
 * end-of-episode flag, the expiry callback, and the synchronous
 * [SleepTimerState] slice updates — extracted from [AudioPlayerViewModel]
 * (four hand-rolled functions, with the explicit-pause rationale comment
 * hand-copied at both start sites).
 *
 * This is audio's OWN controller, not a reuse of player-video's
 * `SleepTimerController`: player-audio does not depend on player-video (and
 * must not — wrong direction), and video's variant carries video-only concerns
 * (pre-fade volume capture/restore, the mute-gated fade callback over its
 * [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]). Audio's
 * [SleepTimerManager] owns the countdown + fade ramp internally and the audio
 * VM never swaps its engine, so this fold is deliberately smaller.
 *
 * The [SleepTimerState] slice stays ON the ViewModel's uiState (screens read
 * `uiState.sleepTimer`); this controller mutates it through the
 * [updateState] seam (SettingsProjector-style lambda) so the screen surface is
 * unchanged. The VM's `isActive`/`isEndOfEpisodeMode`/last-used-duration flow
 * collectors keep feeding the same slice — these synchronous writes only pin
 * the value in the same frame as the command, exactly as before.
 *
 * Not a Koin type: the ViewModel constructs it directly over its own [scope].
 */
internal class AudioSleepTimerController(
    private val scope: CoroutineScope,
    private val sleepTimerManager: SleepTimerManager,
    private val audioStore: AudioStore,
    private val engine: AudioPlayerEngine,
    private val updateState: ((SleepTimerState) -> SleepTimerState) -> Unit,
) {

    /**
     * Start a countdown for [durationMs]; persists it as the last-used duration
     * so the picker can re-offer it.
     */
    fun startSleepTimer(durationMs: Long) {
        scope.launch {
            audioStore.setSleepTimerDurationMs(durationMs)
            audioStore.setSleepTimerEndOfEpisode(false)
        }
        armExpiryPause()
        sleepTimerManager.start(durationMs)
        updateState { it.copy(active = true, endOfEpisode = false, lastUsedDurationMs = durationMs) }
    }

    /**
     * Start an end-of-episode timer: no countdown display, no fade — pauses the
     * moment [triggerSleepTimerEndOfEpisode] fires.
     */
    fun startSleepTimerEndOfEpisode() {
        scope.launch {
            audioStore.setSleepTimerEndOfEpisode(true)
        }
        armExpiryPause()
        sleepTimerManager.startEndOfEpisode()
        updateState { it.copy(active = true, endOfEpisode = true) }
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        updateState { it.copy(active = false, endOfEpisode = false) }
    }

    /** Fire the end-of-episode pause; mode + active guard live on the manager. */
    fun triggerSleepTimerEndOfEpisode() = sleepTimerManager.triggerEndOfEpisode()

    /**
     * The ONE home for the expiry callback — explicit pause rather than
     * togglePlayPause(): if the user paused manually after arming the timer,
     * the toggle would otherwise RESUME playback, the opposite of the timer's
     * intent. Both start modes arm the same callback.
     */
    private fun armExpiryPause() {
        sleepTimerManager.setOnTimerExpired { engine.pause() }
    }
}
