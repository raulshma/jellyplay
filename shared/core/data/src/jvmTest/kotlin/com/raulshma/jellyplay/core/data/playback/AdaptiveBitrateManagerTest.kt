package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Moved from legacy `:core:data` JUnit4 to shared kotlin.test with the impl:
 * the former mocked `ConnectivityManager` (relaxed → null activeNetwork →
 * metered) became this hand [NetworkMonitor] fake, whose default
 * `isMetered = true` mirrors exactly that scenario — the one where the
 * metered cap matters most.
 */
class AdaptiveBitrateManagerTest {

    /** Hand fake: StateFlow-driven metered flag, defaulting to metered. */
    private class FakeNetworkMonitor(
        initialIsMetered: Boolean = true,
    ) : NetworkMonitor {
        private val metered = MutableStateFlow(initialIsMetered)
        override val networkStatus: StateFlow<NetworkStatus> =
            MutableStateFlow(NetworkStatus.Online)
        override val isMetered: StateFlow<Boolean> = metered
    }

    private val networkMonitor = FakeNetworkMonitor(initialIsMetered = true)
    private val networkOfflineStore: NetworkOfflineStore = mockk()
    private val playbackStore: PlaybackStore = mockk()

    private fun createManager(
        adaptiveBitrateEnabled: Boolean = true,
        manualBandwidthCap: Long = 0L,
    ): AdaptiveBitrateManager {
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            NetworkOfflineSlice(
                adaptiveBitrateEnabled = adaptiveBitrateEnabled,
                manualBandwidthCap = manualBandwidthCap,
            ),
        )
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())
        return AdaptiveBitrateManager(networkMonitor, networkOfflineStore, playbackStore)
    }

    @Test
    fun `AUTO with adaptive disabled sends no cap even on metered network`() {
        // Disabling adaptive bitrate must remove the cap so the server direct-
        // plays instead of transcoding high-bitrate media on metered links.
        val manager = createManager(adaptiveBitrateEnabled = false)
        assertNull(manager.resolveMaxBitrate(StreamingQuality.AUTO))
    }

    @Test
    fun `AUTO with adaptive enabled caps at metered ceiling on metered network`() {
        val manager = createManager(adaptiveBitrateEnabled = true)
        assertEquals(2_500_000L, manager.resolveMaxBitrate(StreamingQuality.AUTO))
    }

    @Test
    fun `AUTO with adaptive disabled but manual cap still honours manual cap`() {
        val manager = createManager(
            adaptiveBitrateEnabled = false,
            manualBandwidthCap = 5_000_000L,
        )
        assertEquals(5_000_000L, manager.resolveMaxBitrate(StreamingQuality.AUTO))
    }
}
