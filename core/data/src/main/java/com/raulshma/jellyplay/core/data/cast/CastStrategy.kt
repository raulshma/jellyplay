package com.raulshma.jellyplay.core.data.cast

import kotlinx.coroutines.flow.StateFlow

data class CastDevice(
    val id: String,
    val name: String,
    val type: String,
    val tag: Any? = null,
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
}
