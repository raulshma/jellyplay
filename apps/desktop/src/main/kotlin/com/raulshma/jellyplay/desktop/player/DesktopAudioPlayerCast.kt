package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop [AudioPlayerCast] (wave 9B): never-connected no-op. The desktop has
 * no cast stack (plan §4 — Cast stays Android-only), so discovery returns no
 * devices, [isConnected] is permanently false, and every transport call is a
 * dead no-op. The jvm CastButton actual renders nothing, so the picker never
 * opens; `castToDevice()` in the ViewModel guards on the (never-set) current
 * item and would route into these no-ops harmlessly.
 *
 * The acquire/release ref-counting keeps the singleton lifetime contract the
 * shared ViewModel relies on (init acquires, onCleared releases).
 */
class DesktopAudioPlayerCast : AudioPlayerCast {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _discoveredDeviceNames = MutableStateFlow<List<String>>(emptyList())
    override val discoveredDeviceNames: StateFlow<List<String>> = _discoveredDeviceNames.asStateFlow()

    override fun startDiscovery() {}
    override fun stopDiscovery() {}
    override fun connect(deviceName: String) {}
    override fun disconnect() {}

    override fun acquireConsumer() {}
    override fun releaseConsumer() {}

    override fun loadMedia(itemId: String, startPositionMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun setVolume(volume: Float) {}
}
