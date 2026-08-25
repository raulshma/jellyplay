package com.raulshma.jellyplay.core.data.playback

/**
 * Lifecycle callbacks for the active player engine.
 * Implemented by each engine (ExoPlayer, MPV, LibVLC) differently.
 *
 * Home note (Phase W.3): born in shared/core:data's `PlayerLifecycleManager.kt`
 * and moved here verbatim (SAME package, so no consumer import changes) because
 * `MediaEngine` extends it and this module needs a wasmJs target for
 * `HtmlVideoEngine` — shared/core:data has no wasm build (Room) and never will
 * (web v1 keeps the server as source of truth). `PlayerLifecycleManager` (the
 * delegating bridge) stays in shared/core:data.
 */
interface PlayerLifecycleCallbacks {
    fun onActivityPause() {}
    fun onActivityResume() {}
}
