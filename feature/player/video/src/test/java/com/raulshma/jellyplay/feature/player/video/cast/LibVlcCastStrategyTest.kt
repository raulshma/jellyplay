package com.raulshma.jellyplay.feature.player.video.cast

import com.raulshma.jellyplay.core.data.cast.CastDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibVlcCastStrategyTest {

    @Test
    fun initialStates_areUnconnectedAndUnavailable() {
        val strategy = LibVlcCastStrategy(
            libVlcProvider = { null },
            mediaPlayerProvider = { null },
        )

        assertFalse(strategy.isAvailable.value)
        assertFalse(strategy.isConnected.value)
        assertFalse(strategy.isConnecting.value)
        assertEquals(0, strategy.discoveredDevices.value.size)
    }

    @Test
    fun stopDiscovery_resetsStateFlows() {
        val strategy = LibVlcCastStrategy(
            libVlcProvider = { null },
            mediaPlayerProvider = { null },
        )

        strategy.stopDiscovery()

        assertFalse(strategy.isAvailable.value)
        assertFalse(strategy.isConnected.value)
        assertEquals(0, strategy.discoveredDevices.value.size)
    }

    @Test
    fun disconnect_resetsConnectedState() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val strategy = LibVlcCastStrategy(
            libVlcProvider = { null },
            mediaPlayerProvider = { null },
        )

        strategy.disconnect(context)
        assertFalse(strategy.isConnected.value)
    }
}
