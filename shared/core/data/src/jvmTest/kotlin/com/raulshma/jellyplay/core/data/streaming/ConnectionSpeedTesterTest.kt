package com.raulshma.jellyplay.core.data.streaming

import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the speed probe's pure sides: the measured-Mbps → quality-tier table
 * (over a fake sampler — no I/O), the max-of-two-samples fold, and the
 * all-samples-failed → null contract.
 */
class ConnectionSpeedTesterTest {

    /** Fake sampler yielding a fixed sample (bytes over elapsedMs), or failing when [bytes] is null. */
    private class FakeExecutor(
        val bytes: Long?,
        val elapsedMs: Long = 1_000L,
    ) : ConnectionSpeedTester.SpeedSampleExecutor {
        var calls: Int = 0

        override suspend fun sample(url: String, maxBytes: Long, deadlineMs: Long): ConnectionSpeedTester.SpeedSample? {
            calls += 1
            return bytes?.let { ConnectionSpeedTester.SpeedSample(bytes = it, elapsedMs = elapsedMs) }
        }
    }

    // ── proposedQualityTier (the pure mapping) ───────────────────────────

    @Test
    fun `tier boundaries map onto the quality tiers`() {
        val table = listOf(
            0f to StreamingQuality.LOW_360P,
            1.9f to StreamingQuality.LOW_360P,
            2f to StreamingQuality.SD_480P,
            4.9f to StreamingQuality.SD_480P,
            5f to StreamingQuality.HD_720P,
            9.9f to StreamingQuality.HD_720P,
            10f to StreamingQuality.FHD_1080P,
            19.9f to StreamingQuality.FHD_1080P,
            20f to StreamingQuality.UHD_4K,
            40f to StreamingQuality.UHD_4K,
            400f to StreamingQuality.UHD_4K,
        )
        table.forEach { (mbps, expected) ->
            assertEquals(expected, ConnectionSpeedTester.proposedQualityTier(mbps), "mbps=$mbps")
        }
    }

    // ── measureMbps over the fake sampler ─────────────────────────────────

    @Test
    fun `two samples are taken and the faster one wins`() = runTest {
        val executor = FakeExecutor(bytes = 1_000_000L, elapsedMs = 1_000L) // 8 Mbps per sample
        val tester = ConnectionSpeedTester(executor)
        val mbps = tester.measureMbps()
        assertEquals(2, executor.calls, "the probe takes exactly two samples")
        assertEquals(8f, mbps)
    }

    @Test
    fun `one failed sample still yields the surviving one`() = runTest {
        val failingFirst = object : ConnectionSpeedTester.SpeedSampleExecutor {
            var calls = 0
            override suspend fun sample(url: String, maxBytes: Long, deadlineMs: Long): ConnectionSpeedTester.SpeedSample? {
                return if (calls++ == 0) null else ConnectionSpeedTester.SpeedSample(2_000_000, 1_000)
            }
        }
        val mbps = ConnectionSpeedTester(failingFirst).measureMbps()
        assertEquals(16f, mbps)
    }

    @Test
    fun `both samples failing yields null`() = runTest {
        val executor = FakeExecutor(bytes = null)
        assertNull(ConnectionSpeedTester(executor).measureMbps())
        assertEquals(2, executor.calls)
    }

    @Test
    fun `degenerate samples are treated as failures`() = runTest {
        // Zero elapsed time and zero bytes must not divide/explode into a
        // bogus infinite rating.
        val executor = object : ConnectionSpeedTester.SpeedSampleExecutor {
            var calls = 0
            override suspend fun sample(url: String, maxBytes: Long, deadlineMs: Long): ConnectionSpeedTester.SpeedSample? {
                return when (calls++) {
                    0 -> ConnectionSpeedTester.SpeedSample(1_000_000, 0)
                    else -> ConnectionSpeedTester.SpeedSample(0, 1_000)
                }
            }
        }
        assertNull(ConnectionSpeedTester(executor).measureMbps())
    }

    @Test
    fun `the probe url is the cloudflare speed endpoint with the bounded payload`() {
        assertTrue(ConnectionSpeedTester.SPEED_TEST_URL.startsWith("https://speed.cloudflare.com/__down"))
        assertTrue(ConnectionSpeedTester.SPEED_TEST_URL.contains("bytes=8000000"))
        assertEquals(8_000_000L, ConnectionSpeedTester.PAYLOAD_BYTES)
        assertEquals(12_000L, ConnectionSpeedTester.SAMPLE_DEADLINE_MS)
    }
}
