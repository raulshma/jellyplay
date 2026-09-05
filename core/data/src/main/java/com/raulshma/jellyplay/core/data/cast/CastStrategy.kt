package com.raulshma.jellyplay.core.data.cast

import androidx.compose.runtime.Stable
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

@Stable
data class CastDevice(
    val id: String,
    val name: String,
    val type: String,
    val tag: Any? = null,
    val strategyName: String = "",
)

interface CastStrategy {
    val isAvailable: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    val isConnecting: StateFlow<Boolean>
    val discoveredDevices: StateFlow<List<CastDevice>>
    fun startDiscovery(context: android.content.Context)
    fun stopDiscovery()
    fun connect(context: android.content.Context, device: CastDevice)
    fun disconnect(context: android.content.Context)

    /**
     * Transport surface. These used to be per-strategy when-chains inside
     * CastManager; they live on the strategy so dispatch is a single call.
     * Defaults are no-ops so strategies without their own transport (e.g.
     * GoogleCastStrategy, whose transport is the manager-owned CastPlayer
     * adapter) need no overrides.
     */
    fun play() {}
    fun pause() {}
    fun seekTo(positionMs: Long) {}
    fun setRendererVolume(volume: Float) {}

    /**
     * Whether [loadMedia] hands [listener] over to a Player that fans events
     * back out (the manager-owned CastPlayer adapter does). Renderer-protocol
     * transports have no Player, so the manager drops its stale external
     * listener instead of handing one over.
     */
    val ownsExternalListener: Boolean get() = false

    /**
     * Starts playback of [mediaItem] on the connected renderer. [listener] is
     * only consumed by transports that surface Player events (the local
     * CastPlayer); renderer-protocol strategies ignore it.
     *
     * @return true when a load was issued, false when the transport had no
     *   target (no connected renderer / no CastPlayer) — callers skip the
     *   post-load state refresh in that case.
     */
    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long,
        listener: Player.Listener,
        options: CastMediaOptions,
    ): Boolean = false

    /**
     * Releases strategy-owned listeners and resources. Called once when the
     * owning [CastManager] is released. Default implementation is a no-op so
     * strategies that own no listeners (e.g. DLNA) don't need to override.
     *
     * Implementations must be idempotent — [CastManager.release] may invoke
     * this more than once across the application lifecycle (e.g. once on
     * logout and once on process shutdown).
     */
    fun release() {}
}
