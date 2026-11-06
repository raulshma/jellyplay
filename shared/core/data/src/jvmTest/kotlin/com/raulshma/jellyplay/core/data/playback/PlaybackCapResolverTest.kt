package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.testutil.FakeNetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.PlaybackCaps
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.ResolvedPerformanceProfile
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.VideoPerformanceMatrix
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import com.raulshma.jellyplay.core.model.VideoPerformanceTuning
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The §5.14 PlaybackCapResolverTest bullets, table-driven: the profile is a
 * **ceiling** — stricter wins, nothing is raised, ties belong to the user,
 * and `FORCE_DIRECT_PLAY` flips `capsBypassed` without erasing the computed
 * caps (diagnostics still show what would have applied).
 */
class PlaybackCapResolverTest {

    private val networkMonitor = FakeNetworkMonitor(initialIsMetered = false)
    private val networkOfflineStore: NetworkOfflineStore = mockk()
    private val playbackStore: PlaybackStore = mockk()
    private val performanceResolver: VideoPerformanceResolver = mockk()

    private fun stubStores(
        adaptiveBitrateEnabled: Boolean = true,
        manualBandwidthCap: Long = 0L,
        dataSaverEnabled: Boolean = false,
        homeStreamingQuality: String = "AUTO",
        wifiQuality: StreamingQuality = StreamingQuality.AUTO,
        cellularQuality: StreamingQuality = StreamingQuality.AUTO,
    ) {
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            NetworkOfflineSlice(
                adaptiveBitrateEnabled = adaptiveBitrateEnabled,
                manualBandwidthCap = manualBandwidthCap,
                dataSaverEnabled = dataSaverEnabled,
                homeStreamingQuality = homeStreamingQuality,
            ),
        )
        every { playbackStore.playback } returns MutableStateFlow(
            PlaybackSlice(
                streamingQuality = wifiQuality,
                cellularStreamingQuality = cellularQuality,
            ),
        )
    }

    private fun stubTuning(tier: DevicePerformanceTier) {
        coEvery { performanceResolver.resolve(any()) } returns ResolvedPerformanceProfile(
            requested = VideoPerformanceProfile.AUTO,
            tier = tier,
            tuning = VideoPerformanceMatrix.forTier(tier),
            detection = null,
        )
    }

    private fun resolver(): PlaybackCapResolver = PlaybackCapResolver(
        adaptiveBitrateManager = AdaptiveBitrateManager(networkMonitor, networkOfflineStore, playbackStore),
        performanceResolver = performanceResolver,
        playbackStore = playbackStore,
    )

    /** Unmetered, adaptive-on, no data saver: the plain ladder. */
    private fun resolvedByPersisted(
        tier: DevicePerformanceTier = DevicePerformanceTier.LOW,
        serverHost: String? = null,
        mode: PlaybackMode = PlaybackMode.AUTO,
        stub: () -> Unit = { stubStores() },
    ): PlaybackCaps = kotlinx.coroutines.runBlocking {
        stub()
        stubTuning(tier)
        resolver().forPersistedQuality(serverHost, mode)
    }

    private fun resolvedByUser(
        quality: StreamingQuality,
        tier: DevicePerformanceTier = DevicePerformanceTier.LOW,
        serverHost: String? = null,
        mode: PlaybackMode = PlaybackMode.AUTO,
        stub: () -> Unit = { stubStores() },
    ): PlaybackCaps = kotlinx.coroutines.runBlocking {
        stub()
        stubTuning(tier)
        resolver().forUserQuality(quality, serverHost, mode)
    }

    // ── forUserQuality: the quality ladder + the profile ceiling ──────────

    @Test
    fun `user AUTO with LOW caps at 1080p and 8 Mbps`() {
        // Unmetered AUTO has no base cap → the profile cap IS the effective cap.
        val caps = resolvedByUser(StreamingQuality.AUTO)
        assertEquals(8_000_000L, caps.maxBitrate)
        assertEquals(1080, caps.maxHeight)
        assertTrue(caps.profileCappedBitrate)
        assertTrue(caps.profileCappedHeight)
        assertFalse(caps.capsBypassed)
    }

    @Test
    fun `user 4K with LOW is pulled down to 1080p and 8 Mbps`() {
        val caps = resolvedByUser(StreamingQuality.UHD_4K)
        assertEquals(8_000_000L, caps.maxBitrate)
        assertEquals(1080, caps.maxHeight)
        assertTrue(caps.profileCappedBitrate)
        assertTrue(caps.profileCappedHeight)
    }

    @Test
    fun `user 720p with LOW keeps the stricter user caps`() {
        val caps = resolvedByUser(StreamingQuality.HD_720P)
        assertEquals(3_000_000L, caps.maxBitrate)
        assertEquals(720, caps.maxHeight)
        assertFalse(caps.profileCappedBitrate, "the profile was NOT the stricter value")
        assertFalse(caps.profileCappedHeight)
    }

    @Test
    fun `user AUTO with HIGH leaves the base network cap unchanged`() {
        // Metered AUTO's 2.5 Mbps ladder cap must survive an untouched HIGH profile.
        val caps = resolvedByUser(
            StreamingQuality.AUTO,
            tier = DevicePerformanceTier.HIGH,
            stub = {
                networkMonitor.isMetered.value = true
                stubStores()
            },
        )
        assertEquals(2_500_000L, caps.maxBitrate)
        assertNull(caps.maxHeight)
        assertFalse(caps.profileCappedBitrate)
        assertFalse(caps.profileCappedHeight)
    }

    // ── forPersistedQuality: the full network fold + the profile ceiling ──

    @Test
    fun `data saver with LOW wins at 1 point 5 Mbps`() {
        val caps = resolvedByPersisted(
            stub = {
                networkMonitor.isMetered.value = true
                stubStores(dataSaverEnabled = true)
            },
        )
        assertEquals(1_500_000L, caps.maxBitrate, "data saver's frugal ceiling beats LOW's 8 Mbps")
        assertFalse(caps.profileCappedBitrate)
    }

    @Test
    fun `metered AUTO with LOW caps at 2 point 5 Mbps`() {
        val caps = resolvedByPersisted(
            stub = {
                networkMonitor.isMetered.value = true
                stubStores(cellularQuality = StreamingQuality.AUTO)
            },
        )
        assertEquals(2_500_000L, caps.maxBitrate)
    }

    @Test
    fun `home 4K tier with LOW caps at 1080p and 8 Mbps`() {
        // A LOCAL server honours the home tier for the bitrate; 4K home is
        // uncapped by the network, so LOW's 8 Mbps becomes the effective cap.
        val caps = resolvedByPersisted(
            serverHost = "192.168.1.10",
            stub = { stubStores(homeStreamingQuality = StreamingQuality.UHD_4K.name) },
        )
        assertEquals(8_000_000L, caps.maxBitrate)
        assertEquals(1080, caps.maxHeight)
        assertTrue(caps.profileCappedBitrate)
        assertTrue(caps.profileCappedHeight)
    }

    @Test
    fun `manual 2 Mbps bandwidth cap with LOW wins at 2 Mbps`() {
        val caps = resolvedByPersisted(
            stub = { stubStores(manualBandwidthCap = 2_000_000L) },
        )
        assertEquals(2_000_000L, caps.maxBitrate)
        assertFalse(caps.profileCappedBitrate)
    }

    @Test
    fun `persisted AUTO on unmetered wifi with LOW caps at 1080p and 8 Mbps`() {
        val caps = resolvedByPersisted()
        assertEquals(8_000_000L, caps.maxBitrate)
        assertEquals(1080, caps.maxHeight)
    }

    // ── bypass semantics ───────────────────────────────────────────────────

    @Test
    fun `FORCE_DIRECT_PLAY with LOW flags bypass but still computes the caps`() {
        val caps = resolvedByUser(StreamingQuality.AUTO, mode = PlaybackMode.FORCE_DIRECT_PLAY)
        assertTrue(caps.capsBypassed)
        assertEquals(8_000_000L, caps.maxBitrate, "caps still computed for diagnostics")
        assertEquals(1080, caps.maxHeight)
    }

    @Test
    fun `FORCE_TRANSCODE does not flag bypass`() {
        val caps = resolvedByUser(StreamingQuality.AUTO, mode = PlaybackMode.FORCE_TRANSCODE)
        assertFalse(caps.capsBypassed)
    }

    @Test
    fun `null base cap with LOW falls to the profile cap`() {
        val caps = resolvedByUser(StreamingQuality.UHD_4K)
        assertEquals(8_000_000L, caps.maxBitrate)
        assertEquals(1080, caps.maxHeight)
    }

    // ── the pure min-fold: ties and strict wins ────────────────────────────

    @Test
    fun `profileCapped flags are exact on ties and strict wins`() = kotlinx.coroutines.runBlocking {
        val resolver = resolver()
        val low = VideoPerformanceMatrix.forTier(DevicePerformanceTier.LOW)
        val medium = VideoPerformanceMatrix.forTier(DevicePerformanceTier.MEDIUM)

        // Exact tie → the user's value stands and the profile did NOT cap.
        resolver.applyProfileCeiling(8_000_000L, 1080, low, PlaybackMode.AUTO).let { caps ->
            assertEquals(8_000_000L, caps.maxBitrate)
            assertEquals(1080, caps.maxHeight)
            assertFalse(caps.profileCappedBitrate)
            assertFalse(caps.profileCappedHeight)
        }
        // Base 20 Mbps/1440p (MEDIUM's own numbers) vs LOW → profile wins both.
        resolver.applyProfileCeiling(20_000_000L, 1440, low, PlaybackMode.AUTO).let { caps ->
            assertEquals(8_000_000L, caps.maxBitrate)
            assertEquals(1080, caps.maxHeight)
            assertTrue(caps.profileCappedBitrate)
            assertTrue(caps.profileCappedHeight)
        }
        // Stricter user bitrate over a stricter profile height → mixed flags.
        resolver.applyProfileCeiling(5_000_000L, 2160, low, PlaybackMode.AUTO).let { caps ->
            assertEquals(5_000_000L, caps.maxBitrate)
            assertEquals(1080, caps.maxHeight)
            assertFalse(caps.profileCappedBitrate)
            assertTrue(caps.profileCappedHeight)
        }
        // Null base + MEDIUM → the profile cap applies and flags it.
        resolver.applyProfileCeiling(null, null, medium, PlaybackMode.AUTO).let { caps ->
            assertEquals(20_000_000L, caps.maxBitrate)
            assertEquals(1440, caps.maxHeight)
            assertTrue(caps.profileCappedBitrate)
            assertTrue(caps.profileCappedHeight)
        }
        // HIGH carries no caps → everything passes through untouched.
        resolver.applyProfileCeiling(12_000_000L, 2160, VideoPerformanceTuning.NONE, PlaybackMode.AUTO).let { caps ->
            assertEquals(12_000_000L, caps.maxBitrate)
            assertEquals(2160, caps.maxHeight)
            assertFalse(caps.profileCappedBitrate)
            assertFalse(caps.profileCappedHeight)
        }
    }

    @Test
    fun `forUserQuality ignores the home-network tier by design`() {
        // §5.7 last paragraph: the user just picked a quality; the home tier is
        // the DEFAULT path's persisted preference. Even with a 4K home tier on
        // a local host, the user-quality path resolves the quality's own ladder.
        val caps = resolvedByUser(
            StreamingQuality.HD_720P,
            serverHost = "192.168.1.10",
            stub = { stubStores(homeStreamingQuality = StreamingQuality.UHD_4K.name) },
        )
        assertEquals(3_000_000L, caps.maxBitrate, "the 720p user choice, not the 4K home tier")
        assertEquals(720, caps.maxHeight)
    }
}
