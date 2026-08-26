package com.raulshma.jellyplay.feature.player.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Module-local seam over the Hilt-owned legacy
 * [com.raulshma.jellyplay.core.data.cast.CastManager] (Google/DLNA/Jellyfin
 * cast strategies, `:core:data` until Phase X). The audio player's whole cast
 * surface funnels through here: the top-bar device picker (discovery,
 * connect/disconnect, connection state) plus the ViewModel's remote-transport
 * passthroughs.
 *
 * Devices are exposed as display names (the picker shows names only — the
 * legacy dialog mapped `devices[which]` the same way); [connect] resolves the
 * name against the current discovery list app-side. The loadMedia call hides
 * the media3 `MediaItem`/`Player.Listener`/`CastMediaOptions` construction the
 * legacy `castToDevice()` built inline.
 *
 * Android impl: the app-side lazy interop adapter
 * (`HiltInteropModule.HiltAudioPlayerCast`, holds the application context the
 * legacy startDiscovery/connect/disconnect calls need). No desktop impl — the
 * cast button renders nothing there (see the CastButton jvm actual).
 */
interface AudioPlayerCast {
    val isConnected: StateFlow<Boolean>
    val discoveredDeviceNames: StateFlow<List<String>>

    fun startDiscovery()
    fun stopDiscovery()
    fun connect(deviceName: String)
    fun disconnect()

    fun acquireConsumer()
    fun releaseConsumer()

    fun loadMedia(itemId: String, startPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
}
