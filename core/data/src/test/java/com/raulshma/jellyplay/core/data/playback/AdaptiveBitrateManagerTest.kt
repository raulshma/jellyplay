package com.raulshma.jellyplay.core.data.playback

import android.net.ConnectivityManager
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveBitrateManagerTest {

    // Relaxed mock: activeNetwork is null, so isUnmeteredConnection() reports a
    // metered connection — exactly the scenario where the cap matters most.
    private val connectivityManager: ConnectivityManager = mockk(relaxed = true)
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
        return AdaptiveBitrateManager(connectivityManager, networkOfflineStore, playbackStore)
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
