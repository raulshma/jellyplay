package com.raulshma.jellyplay.core.data.streaming

import com.raulshma.jellyplay.core.model.AudioBitrateTier
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.network.interceptor.BandwidthInterceptor
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pins the tier-selection arithmetic in [AdaptiveBitrateSelector]:
 *
 *  - the usable bandwidth is the **max** of the network-interceptor and the
 *    local-audio measurement — whichever path is proving itself faster;
 *  - a non-positive estimate (no samples yet, or garbage) always degrades to
 *    [AudioBitrateTier.LOW];
 *  - the estimate is divided by 1.5 (headroom for TCP slow-start and bursty
 *    delivery) before comparing against the tier targets (MEDIUM 192, HIGH
 *    320, LOSSLESS 1411 kbps), so the exact thresholds are 288 / 480 / 2116.5
 *    kbps of available bandwidth — the boundaries are pinned exactly;
 *  - the picked tier is clamped to [maxAllowed] ([minOf]);
 *  - [AdaptiveBitrateSelector.resolveBitrate] maps the fixed
 *    [StreamingQuality] choices through the same table (and still honours the
 *    clamp), while AUTO delegates to [AdaptiveBitrateSelector.selectTier].
 *
 * Both measurements are mocked as [StateFlow]s so no real sampling runs.
 */
class AdaptiveBitrateSelectorTest {

    private val networkKbps = MutableStateFlow(0.0)
    private val localKbps = MutableStateFlow(0.0)

    private val interceptor: BandwidthInterceptor = mockk {
        every { estimatedBandwidthKbps } returns networkKbps
    }
    private val monitor: BandwidthMonitor = mockk {
        every { estimatedBandwidthKbps } returns localKbps
    }

    private fun selector(): AdaptiveBitrateSelector = AdaptiveBitrateSelector(monitor, interceptor)

    /** Asserts [availableKbps] selects [expected] via the /1.5 headroom rule. */
    private fun assertTier(availableKbps: Double, expected: AudioBitrateTier, maxAllowed: AudioBitrateTier = AudioBitrateTier.LOSSLESS) {
        networkKbps.value = availableKbps
        localKbps.value = 0.0
        assertEquals(expected, selector().selectTier(maxAllowed), "available=$availableKbps")
    }

    // ── selectTier: zero / negative bandwidth ───────────────────────────

    @Test
    fun `zero bandwidth on both measurements selects LOW`() {
        assertTier(availableKbps = 0.0, expected = AudioBitrateTier.LOW)
    }

    @Test
    fun `negative bandwidth selects LOW`() {
        assertTier(availableKbps = -250.0, expected = AudioBitrateTier.LOW)
    }

    // ── selectTier: tier boundaries (available = target * 1.5) ──────────

    @Test
    fun `bandwidth below the MEDIUM threshold selects LOW`() {
        // 287.999 / 1.5 = 191.999 < 192 → LOW.
        assertTier(availableKbps = 287.999, expected = AudioBitrateTier.LOW)
    }

    @Test
    fun `exactly 288 kbps hits the MEDIUM threshold`() {
        // 288 / 1.5 = 192 = MEDIUM.targetKbps.
        assertTier(availableKbps = 288.0, expected = AudioBitrateTier.MEDIUM)
    }

    @Test
    fun `just below the HIGH threshold stays MEDIUM`() {
        // 479.999 / 1.5 = 319.999 < 320 → MEDIUM.
        assertTier(availableKbps = 479.999, expected = AudioBitrateTier.MEDIUM)
    }

    @Test
    fun `exactly 480 kbps hits the HIGH threshold`() {
        // 480 / 1.5 = 320 = HIGH.targetKbps.
        assertTier(availableKbps = 480.0, expected = AudioBitrateTier.HIGH)
    }

    @Test
    fun `just below the LOSSLESS threshold stays HIGH`() {
        // 2116.4999 / 1.5 < 1411 → HIGH.
        assertTier(availableKbps = 2116.4999, expected = AudioBitrateTier.HIGH)
    }

    @Test
    fun `exactly 2116_5 kbps hits the LOSSLESS threshold`() {
        // 2116.5 / 1.5 = 1411 = LOSSLESS.targetKbps.
        assertTier(availableKbps = 2116.5, expected = AudioBitrateTier.LOSSLESS)
    }

    @Test
    fun `very fast link selects LOSSLESS`() {
        assertTier(availableKbps = 100_000.0, expected = AudioBitrateTier.LOSSLESS)
    }

    // ── selectTier: max-of-network-and-local ────────────────────────────

    @Test
    fun `local measurement rescues the pick when the network estimate is zero`() {
        networkKbps.value = 0.0
        localKbps.value = 480.0

        assertEquals(AudioBitrateTier.HIGH, selector().selectTier())
    }

    @Test
    fun `faster of the two measurements wins`() {
        networkKbps.value = 300.0 // → MEDIUM on its own
        localKbps.value = 480.0 // → HIGH

        assertEquals(AudioBitrateTier.HIGH, selector().selectTier())

        networkKbps.value = 480.0 // → HIGH
        localKbps.value = 300.0 // → MEDIUM

        assertEquals(AudioBitrateTier.HIGH, selector().selectTier())
    }

    // ── selectTier: maxAllowed clamp ────────────────────────────────────

    @Test
    fun `picked tier is clamped down to maxAllowed`() {
        assertTier(availableKbps = 100_000.0, expected = AudioBitrateTier.HIGH, maxAllowed = AudioBitrateTier.HIGH)
        assertTier(availableKbps = 100_000.0, expected = AudioBitrateTier.MEDIUM, maxAllowed = AudioBitrateTier.MEDIUM)
        assertTier(availableKbps = 100_000.0, expected = AudioBitrateTier.LOW, maxAllowed = AudioBitrateTier.LOW)
    }

    @Test
    fun `clamp never upgrades a slow link`() {
        // maxAllowed above what the link supports must not inflate the pick.
        assertTier(availableKbps = 100.0, expected = AudioBitrateTier.LOW, maxAllowed = AudioBitrateTier.LOSSLESS)
    }

    // ── resolveBitrate: fixed quality table ─────────────────────────────

    @Test
    fun `fixed qualities map through the tier table`() {
        val selector = selector()

        assertEquals(AudioBitrateTier.LOW, selector.resolveBitrate(StreamingQuality.LOW_360P))
        assertEquals(AudioBitrateTier.LOW, selector.resolveBitrate(StreamingQuality.SD_480P))
        assertEquals(AudioBitrateTier.MEDIUM, selector.resolveBitrate(StreamingQuality.HD_720P))
        assertEquals(AudioBitrateTier.HIGH, selector.resolveBitrate(StreamingQuality.FHD_1080P))
        assertEquals(AudioBitrateTier.LOSSLESS, selector.resolveBitrate(StreamingQuality.UHD_4K))
    }

    @Test
    fun `fixed qualities are still clamped by maxAllowed`() {
        val selector = selector()

        assertEquals(AudioBitrateTier.MEDIUM, selector.resolveBitrate(StreamingQuality.UHD_4K, maxAllowed = AudioBitrateTier.MEDIUM))
        assertEquals(AudioBitrateTier.HIGH, selector.resolveBitrate(StreamingQuality.FHD_1080P, maxAllowed = AudioBitrateTier.HIGH))
        assertEquals(AudioBitrateTier.LOW, selector.resolveBitrate(StreamingQuality.HD_720P, maxAllowed = AudioBitrateTier.LOW))
    }

    @Test
    fun `AUTO delegates to selectTier with the current measurements`() {
        networkKbps.value = 480.0

        assertEquals(AudioBitrateTier.HIGH, selector().resolveBitrate(StreamingQuality.AUTO))

        networkKbps.value = 0.0
        localKbps.value = 0.0

        assertEquals(AudioBitrateTier.LOW, selector().resolveBitrate(StreamingQuality.AUTO))
    }

    @Test
    fun `AUTO is clamped by maxAllowed`() {
        networkKbps.value = 100_000.0

        assertEquals(
            AudioBitrateTier.HIGH,
            selector().resolveBitrate(StreamingQuality.AUTO, maxAllowed = AudioBitrateTier.HIGH),
        )
    }

    // ── exposure ────────────────────────────────────────────────────────

    @Test
    fun `bandwidthKbps exposes the interceptor flow directly`() {
        val selector = selector()

        assertSame(networkKbps, selector.bandwidthKbps)
        assertEquals(0.0, selector.bandwidthKbps.value)
    }
}
