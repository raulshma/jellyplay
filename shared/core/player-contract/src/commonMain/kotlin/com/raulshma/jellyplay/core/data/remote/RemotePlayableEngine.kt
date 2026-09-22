package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.StateFlow

/**
 * Engine-agnostic subset of `MediaEngine`
 * (`com.raulshma.jellyplay.feature.player.video.engine`) used by the Jellyfin
 * "Play To" / remote control receiver. Defined here so the
 * `ActivePlayerController` and the remote-control dispatchers in `core:data`
 * do not need to depend on the `feature:player:video` module.
 *
 * The video `MediaEngine` implements this interface directly — no adapter is
 * required.
 *
 * All control methods are required to be safe to call from any thread.
 * Implementations must marshal the call to the engine's owning thread
 * (typically the main thread) internally. This protects callers like the
 * [RemoteControlReceiver] that run on [kotlinx.coroutines.Dispatchers.Default]
 * from triggering [IllegalStateException]s in strict engines such as ExoPlayer.
 *
 * Home note: this file moved verbatim from shared/core:data's
 * commonMain (SAME package, so no consumer import changes) because `MediaEngine`
 * extends it. The core:data consumers
 * (`ActivePlayerController`, `VideoMiniPlayerState`, remote-control
 * dispatchers) now reach it through core:data's `api(player-contract)` edge.
 */
interface RemotePlayableEngine {
    val currentPositionMs: Long
    val isPlaying: StateFlow<Boolean>

    val volume: Float

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun selectTrack(type: TrackType, index: Int)
    fun setMaxVideoBitrate(bps: Int?)

    /**
     * Sets the engine's volume (normalized 0..1).
     *
     * [isUserChange] distinguishes USER-initiated changes (a slider,
     * a key, a remote "SetVolume") from PROGRAMMATIC ones (sleep-timer fades,
     * audio-focus duck/restore). Engines that remember the volume per content
     * type capture ONLY user changes — a fade must never overwrite the
     * remembered level. Defaults to `true`: every pre-existing call site is
     * user-shaped, and the programmatic paths pass `false` explicitly.
     */
    fun setVolume(value: Float, isUserChange: Boolean = true)
    fun increaseVolume(delta: Float = 0.05f)
    fun decreaseVolume(delta: Float = 0.05f)
    fun setMuted(muted: Boolean)

    /**
     * Per-content-type volume-memory capture hook. Engines whose
     * volume apply path distinguishes user changes (see [setVolume]'s
     * [isUserChange]) invoke the handler for USER-initiated levels only —
     * programmatic fades (sleep timer, duck/restore) never fire it. The
     * session host assigns it with the active item's volume bucket; the
     * default accessors are inert so engines without a memory surface (and
     * every fake) need no override.
     */
    var onUserVolumeChange: ((level: Float) -> Unit)?
        get() = null
        set(value) {}

    /**
     * Releases all native resources held by this engine. After this call returns, the engine
     * is no longer usable. Added at the `core.data.remote` level (rather than only on the
     * video `MediaEngine` interface) so the cross-feature [VideoMiniPlayerState] holder can
     * release the engine it captured without depending on `feature:player:video`.
     */
    fun release()
}
