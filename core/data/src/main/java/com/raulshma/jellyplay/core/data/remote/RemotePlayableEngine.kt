package com.raulshma.jellyplay.core.data.remote

import androidx.media3.common.Player
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.StateFlow

/**
 * Engine-agnostic subset of [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]
 * used by the Jellyfin "Play To" / remote control receiver. Defined here so
 * the [ActivePlayerController] and the remote-control dispatchers in
 * `core:data` do not need to depend on the `feature:player:video` module.
 *
 * The video [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine]
 * implements this interface directly — no adapter is required.
 *
 * All control methods are required to be safe to call from any thread.
 * Implementations must marshal the call to the engine's owning thread
 * (typically the main thread) internally. This protects callers like the
 * [RemoteControlReceiver] that run on [kotlinx.coroutines.Dispatchers.Default]
 * from triggering [IllegalStateException]s in strict engines such as ExoPlayer.
 */
interface RemotePlayableEngine {
    val currentPositionMs: Long
    val isPlaying: StateFlow<Boolean>
    val underlyingPlayer: Player?

    val volume: Float

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun selectTrack(type: TrackType, index: Int)
    fun setMaxVideoBitrate(bps: Int?)

    fun setVolume(value: Float)
    fun increaseVolume(delta: Float = 0.05f)
    fun decreaseVolume(delta: Float = 0.05f)
    fun setMuted(muted: Boolean)

    /**
     * Releases all native resources held by this engine. After this call returns, the engine
     * is no longer usable. Added at the `core.data.remote` level (rather than only on the
     * video `MediaEngine` interface) so the cross-feature [VideoMiniPlayerState] holder can
     * release the engine it captured without depending on `feature:player:video`.
     */
    fun release()
}
