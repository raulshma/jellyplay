package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the deliberate no-op contract of [DesktopAudioPlayerCast] (plan §4 —
 * Cast stays Android-only): the desktop implementation is a permanent
 * never-connected stub. [AudioPlayerCast.isConnected] must read false before
 * AND after every entry point, discovery must never surface a device, and the
 * acquire/release ref-counting (the shared ViewModel's singleton lifetime
 * contract: init acquires, onCleared releases) plus every transport call must
 * be individually harmless — no throw, no state flip. The jvm CastButton
 * actual renders nothing so the picker can never open, but a defensive guard
 * that DOES route into these calls (castToDevice's current-item check) must
 * find the world unchanged.
 */
class DesktopAudioPlayerCastTest {

    @Test
    fun implementsTheSharedCastContract() {
        val cast: AudioPlayerCast = DesktopAudioPlayerCast()
        assertTrue(cast is DesktopAudioPlayerCast)
    }

    @Test
    fun isConnectedIsPermanentlyFalse() {
        val cast = DesktopAudioPlayerCast()
        assertEquals(false, cast.isConnected.value)
        assertEquals(emptyList(), cast.discoveredDeviceNames.value)
    }

    @Test
    fun discoveryAndConnectNeverProduceADeviceOrAConnection() {
        val cast = DesktopAudioPlayerCast()
        cast.startDiscovery()
        assertEquals(emptyList(), cast.discoveredDeviceNames.value, "no cast stack on desktop — discovery finds nothing")
        cast.connect("Living Room Chromecast")
        assertEquals(false, cast.isConnected.value, "connect is a dead no-op")
        cast.stopDiscovery()
        cast.disconnect()
        assertEquals(false, cast.isConnected.value)
        assertEquals(emptyList(), cast.discoveredDeviceNames.value)
    }

    @Test
    fun refCountAcquireReleaseIsHarmlessInAnyOrderOrMultiplicity() {
        val cast = DesktopAudioPlayerCast()
        // The shared ViewModel pairs init/onCleared; hostile sequences (double
        // acquire, unbalanced release, release without acquire) must all be
        // inert — there is no ref-count state to corrupt.
        cast.acquireConsumer()
        cast.acquireConsumer()
        cast.releaseConsumer()
        cast.releaseConsumer()
        cast.releaseConsumer() // unbalanced on purpose
        assertEquals(false, cast.isConnected.value)
        assertEquals(emptyList(), cast.discoveredDeviceNames.value)
    }

    @Test
    fun transportCallsAreInertAndLeaveTheWorldUnchanged() {
        val cast = DesktopAudioPlayerCast()
        cast.loadMedia(itemId = "item1", startPositionMs = 30_000L)
        cast.play()
        cast.pause()
        cast.seekTo(positionMs = 90_000L)
        cast.setVolume(volume = 0.5f)
        assertEquals(false, cast.isConnected.value, "transport must never imply a session")
        assertEquals(emptyList(), cast.discoveredDeviceNames.value)
    }
}
