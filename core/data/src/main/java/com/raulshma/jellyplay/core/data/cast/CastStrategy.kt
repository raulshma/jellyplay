package com.raulshma.jellyplay.core.data.cast

import kotlinx.coroutines.flow.StateFlow

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
