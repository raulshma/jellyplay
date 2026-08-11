package com.raulshma.jellyplay.core.data.playback

import javax.inject.Inject
import javax.inject.Singleton
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore

/**
 * Lifecycle callbacks for the active player engine.
 * Implemented by each engine (ExoPlayer, MPV, LibVLC) differently.
 */
interface PlayerLifecycleCallbacks {
    fun onActivityPause() {}
    fun onActivityResume() {}
}

/**
 * Activity↔engine lifecycle bridge.
 *
 * Bridges the single-activity Compose architecture to the lifecycle-aware
 * engines. The Activity calls [onActivityPause] / [onActivityResume] here, and
 * this class delegates directly to the active engine via [activeCallbacks] —
 * no StateFlow indirection for pause/resume.
 *
 * PiP state lives in [PipController]; this class is concerned only with the
 * engine lifecycle.
 */
@Singleton
class PlayerLifecycleManager @Inject constructor(
    private val playbackStore: PlaybackStore
) {

    // Set by the ViewModel/PlayerSessionManager when an engine is created/released.
    // Allows direct lifecycle calls without going through StateFlow hops.
    @Volatile
    var activeCallbacks: PlayerLifecycleCallbacks? = null

    /** True when background video audio is enabled (engine keeps running on background). */
    val isBackgroundAudioEnabled: Boolean
        get() = playbackStore.playback.value.backgroundVideoAudioEnabled

    /** Clears the active engine callbacks. Called when playback ends. */
    fun reset() {
        activeCallbacks = null
    }

    // Called by the host Activity (PlayerActivity / LiveTvPlayerActivity).
    // Delegates directly to the active engine's callbacks. Every engine
    // overrides both: ExoPlayer, MPV and LibVLC each remember play state on
    // pause and resume playback on resume (unless background audio is on).

    /** Called from Activity.onPause() when NOT in PiP mode */
    fun onActivityPause() {
        if (!playbackStore.playback.value.backgroundVideoAudioEnabled) {
            activeCallbacks?.onActivityPause()
        }
    }

    /** Called from Activity.onResume() */
    fun onActivityResume() {
        activeCallbacks?.onActivityResume()
    }
}
