package com.raulshma.jellyplay.core.data.playback

/**
 * Lifecycle callbacks for the active player engine.
 * Implemented by each engine (ExoPlayer, MPV, LibVLC) differently.
 *
 * Home note: born in shared/core:data's `PlayerLifecycleManager.kt`
 * and moved here verbatim (SAME package, so no consumer import changes) because
 * `MediaEngine` extends it. `PlayerLifecycleManager` (the
 * delegating bridge) stays in shared/core:data.
 */
interface PlayerLifecycleCallbacks {
    fun onActivityPause() {}
    fun onActivityResume() {}
}
