package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.testutil.FakeNetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Moved from legacy `:core:data` JUnit4 to shared kotlin.test with the impl:
 * the former mocked `ConnectivityManager` (relaxed → null activeNetwork →
 * metered) became a [NetworkMonitor] fake, whose default
 * `isMetered = true` mirrors exactly that scenario — the one where the
 * metered cap matters most.
 */
class AdaptiveBitrateManagerTest {

    private val networkMonitor = FakeNetworkMonitor(initialIsMetered = true)
    private val networkOfflineStore: NetworkOfflineStore = mockk()
    private val playbackStore: PlaybackStore = mockk()

    private fun createManager(
        adaptiveBitrateEnabled: Boolean = true,
        manualBandwidthCap: Long = 0L,
        homeStreamingQuality: String = "AUTO",
        wifiQuality: StreamingQuality = StreamingQuality.AUTO,
        cellularQuality: StreamingQuality = StreamingQuality.AUTO,
    ): AdaptiveBitrateManager {
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            NetworkOfflineSlice(
                adaptiveBitrateEnabled = adaptiveBitrateEnabled,
                manualBandwidthCap = manualBandwidthCap,
                homeStreamingQuality = homeStreamingQuality,
            ),
        )
        every { playbackStore.playback } returns MutableStateFlow(
            PlaybackSlice(
                streamingQuality = wifiQuality,
                cellularStreamingQuality = cellularQuality,
            ),
        )
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

    // ── the network-split home tier (#19) ─────────────────────────────────

    /** The unmetered tier table (resolveMaxBitrate's per-tier caps). */
    private val tierCaps = mapOf(
        StreamingQuality.LOW_360P to 500_000L,
        StreamingQuality.SD_480P to 1_500_000L,
        StreamingQuality.HD_720P to 3_000_000L,
        StreamingQuality.FHD_1080P to 8_000_000L,
    )

    @Test
    fun `local host with non-AUTO home tier caps per the home tier`() {
        tierCaps.forEach { (tier, cap) ->
            val manager = createManager(homeStreamingQuality = tier.name)
            assertEquals(cap, manager.resolveEffectiveMaxBitrate("192.168.1.10"), tier.name)
        }
    }

    @Test
    fun `local host with AUTO home tier keeps the network ladder`() {
        // The test fake is METERED, so the ladder arm resolves the cellular
        // tier — AUTO cellular caps at the metered ceiling (the historical
        // posture, NOT the home tier).
        val manager = createManager(
            homeStreamingQuality = "AUTO",
            cellularQuality = StreamingQuality.AUTO,
        )
        assertEquals(2_500_000L, manager.resolveEffectiveMaxBitrate("http://192.168.1.10:8096"))
    }

    @Test
    fun `unparseable home tier name degrades to the network ladder`() {
        val manager = createManager(homeStreamingQuality = "SOMETHING_NEW")
        assertEquals(2_500_000L, manager.resolveEffectiveMaxBitrate("192.168.1.10"))
    }

    @Test
    fun `remote host ignores the home tier and uses the network ladder`() {
        // Metered fake + the cellular tier (4K) → the cellular tier's cap
        // (null — 4K is uncapped): the point is the home tier's 8 Mbps 1080p
        // ceiling is NOT applied to a remote host.
        val manager = createManager(
            homeStreamingQuality = StreamingQuality.FHD_1080P.name,
            cellularQuality = StreamingQuality.UHD_4K,
        )
        assertNull(manager.resolveEffectiveMaxBitrate("jellyfin.example.com"))
    }

    @Test
    fun `local host honours the home tier even on a metered connection`() {
        // The split is by SERVER HOST, not by the active network: a local
        // server reached over a metered hotspot still gets the explicit home
        // tier (the user set it FOR the home server).
        val manager = createManager(homeStreamingQuality = StreamingQuality.SD_480P.name)
        assertEquals(1_500_000L, manager.resolveEffectiveMaxBitrate("192.168.1.10"))
    }

    @Test
    fun `null or blank host keeps the existing behaviour`() {
        val manager = createManager(homeStreamingQuality = StreamingQuality.SD_480P.name)
        // Metered fake + AUTO cellular tier → the metered AUTO ceiling, not
        // the home tier's 480p cap.
        assertEquals(2_500_000L, manager.resolveEffectiveMaxBitrate(null))
        assertEquals(2_500_000L, manager.resolveEffectiveMaxBitrate(""))
    }

    @Test
    fun `data saver still clamps the home tier`() {
        val manager = managerWithDataSaver(homeStreamingQuality = StreamingQuality.HD_720P.name)
        assertEquals(1_500_000L, manager.resolveEffectiveMaxBitrate("10.0.0.5"))
    }

    private fun managerWithDataSaver(homeStreamingQuality: String): AdaptiveBitrateManager {
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            NetworkOfflineSlice(
                dataSaverEnabled = true,
                homeStreamingQuality = homeStreamingQuality,
            ),
        )
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())
        return AdaptiveBitrateManager(networkMonitor, networkOfflineStore, playbackStore)
    }
}
