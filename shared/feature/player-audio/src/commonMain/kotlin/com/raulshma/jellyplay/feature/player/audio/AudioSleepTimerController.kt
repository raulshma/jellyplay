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
 * (the mute-gated fade callback over its
 * [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]). The
 * countdown itself is core:data's [SleepCountdown] core — since the fold that
 * moved it out of the jvmShared SleepTimerManager into commonMain, audio sees
 * the SAME core video and the reader ride (the former AudioSleepTimerManager
 * interface existed only because the impl lived in jvmShared; that objection
 * is gone). The audio VM never swaps its engine, so this fold is deliberately
 * smaller.
 *
 * **Fade:** the timed arm ramps the engine's software volume out over the
 * countdown's final stretch, mirroring the video controller's shape — the
 * pre-fade level is captured at [startSleepTimer] ([AudioPlayerEngine.volume]),
 * the fade ticks write it programmatically (`isUserChange = false`), and the
 * captured level is restored on cancel AND after the expiry pause (the audio
 * semantic the video host does not have: resuming tomorrow must not start
 * silent). The end-of-episode arm stays fade-free — clearing the capture so a
 * later cancel never restores a stale level — exactly the video controller's
 * capture/clear discipline.
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
     * The expiry action PAUSES first, then restores the pre-fade level (see
     * [preFadeVolume]) — pause, then un-silence, so the next play session is
     * audible.
     */
    private val arming = SleepTimerArming(
        sleepCountdown = sleepCountdown,
        audioStore = audioStore,
        scope = scope,
        onExpirePause = {
            engine.pause()
            restorePreFadeVolume()
        },
    )

    /**
     * Volume captured when a timed timer starts fading (the fade's
     * pre-ramp level), restored on cancel and after the expiry pause so the
     * fade is never the volume the user comes back to. Null while an
     * end-of-episode arm is live (no fade, nothing to restore) or once
     * restored — the exact capture/clear discipline of the video
     * controller's `preSleepVolume`.
     */
    private var preFadeVolume: Float? = null

    /**
     * Start a countdown for [durationMs]; persists it as the last-used duration
     * so the picker can re-offer it. Captures the engine's current volume as
     * the fade's restore point and arms the software-gain ramp over the
     * countdown's final stretch (programmatic writes — a fade tick must never
     * look like a user level).
     */
    fun startSleepTimer(durationMs: Long) {
        preFadeVolume = engine.volume
        arming.armTimed(durationMs, fade = { progress ->
            engine.setVolume(progress, isUserChange = false)
        })
        updateState { it.copy(active = true, endOfEpisode = false, lastUsedDurationMs = durationMs) }
    }

    /**
     * Start an end-of-episode timer: no countdown display, no fade — pauses
     * at the end-of-episode trigger, which the platform queue managers fire
     * themselves (Android on track end, desktop on queue exhaustion); the
     * mode + active guard lives on [SleepCountdown]. Clears any level
     * captured by a prior timed timer so a later cancel/restore never
     * resurrects a stale pre-fade level.
     */
    fun startSleepTimerEndOfEpisode() {
        preFadeVolume = null
        arming.armEndOfEpisode()
        updateState { it.copy(active = true, endOfEpisode = true) }
    }

    fun cancelSleepTimer() {
        arming.disarm()
        restorePreFadeVolume()
        updateState { it.copy(active = false, endOfEpisode = false) }
    }

    /** Restore the captured pre-fade level programmatically, then forget it. */
    private fun restorePreFadeVolume() {
        preFadeVolume?.let { engine.setVolume(it, isUserChange = false) }
        preFadeVolume = null
    }
}
