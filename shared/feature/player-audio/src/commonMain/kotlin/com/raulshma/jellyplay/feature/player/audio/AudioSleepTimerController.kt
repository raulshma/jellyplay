package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepTimerArming
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the audio player's sleep-timer workflow — the two start modes (timed
 * countdown and end-of-episode), the expiry callback, and the synchronous
 * [SleepTimerState] slice updates — extracted from [AudioPlayerViewModel]
 * (four hand-rolled functions, with the explicit-pause rationale comment
 * hand-copied at both start sites). The store writes + countdown dispatch
 * (the choreography both player hosts hand-copied) moved to core:data's
 * [SleepTimerArming]; the host-side residue stays here.
 *
 * This is audio's OWN controller, not a reuse of player-video's
 * `SleepTimerController`: player-audio does not depend on player-video (and
 * must not — wrong direction), and video's variant carries video-only concerns
 * (pre-fade volume capture/restore, the mute-gated fade callback over its
 * [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]). The
 * countdown itself is core:data's [SleepCountdown] core — since the fold that
 * moved it out of the jvmShared SleepTimerManager into commonMain, audio sees
 * the SAME core video and the reader ride (the former AudioSleepTimerManager
 * interface existed only because the impl lived in jvmShared; that objection
 * is gone). The audio VM never swaps its engine, so this fold is deliberately
 * smaller.
 *
 * The [SleepTimerState] slice stays ON the ViewModel's uiState (screens read
 * `uiState.sleepTimer`); this controller mutates it through the
 * [updateState] seam (SettingsProjector-style lambda) so the screen surface is
 * unchanged. The VM's `isSleepTimerActive`/`isEndOfEpisodeMode`/last-used-duration flow
 * collectors keep feeding the same slice — these synchronous writes only pin
 * the value in the same frame as the command, exactly as before.
 *
 * Not a Koin type: the ViewModel constructs it directly over its own [scope].
 */
internal class AudioSleepTimerController(
    private val scope: CoroutineScope,
    private val sleepCountdown: SleepCountdown,
    private val audioStore: AudioStore,
    private val engine: AudioPlayerEngine,
    private val updateState: ((SleepTimerState) -> SleepTimerState) -> Unit,
) {

    /**
     * The shared core:data arming machine (the [SleepTimerArming] fold of the
     * store writes + countdown dispatch both player hosts hand-copied); this
     * controller keeps the slice updates and the explicit-pause callback.
     * No fade on any audio arm (`fade = null`) — audio has no volume ramp.
     */
    private val arming = SleepTimerArming(
        sleepCountdown = sleepCountdown,
        audioStore = audioStore,
        scope = scope,
        onExpirePause = { engine.pause() },
    )

    /**
     * Start a countdown for [durationMs]; persists it as the last-used duration
     * so the picker can re-offer it.
     */
    fun startSleepTimer(durationMs: Long) {
        arming.armTimed(durationMs, fade = null)
        updateState { it.copy(active = true, endOfEpisode = false, lastUsedDurationMs = durationMs) }
    }

    /**
     * Start an end-of-episode timer: no countdown display, no fade — pauses
     * at the end-of-episode trigger, which the platform queue managers fire
     * themselves (Android on track end, desktop on queue exhaustion); the
     * mode + active guard lives on [SleepCountdown].
     */
    fun startSleepTimerEndOfEpisode() {
        arming.armEndOfEpisode()
        updateState { it.copy(active = true, endOfEpisode = true) }
    }

    fun cancelSleepTimer() {
        arming.disarm()
        updateState { it.copy(active = false, endOfEpisode = false) }
    }
}
