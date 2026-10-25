package com.raulshma.jellyplay.core.data.streaming

import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    // ── SpeedSampleExecutors.okHttp (the production executor) ─────────────
    // These pin the Issue-#6 fix shape: the blocking transfer must run off the
    // caller's dispatcher and the sample deadline must interrupt a stalled
    // read — both verified against a real socket (runBlocking, real time; the
    // virtual clock of runTest would mask a hung blocking call).

    @Test
    fun `okHttp executor counts the streamed bytes into a speed sample`() {
        val payload = ByteArray(256 * 1024) { ('a' + (it % 26)).code.toByte() }
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        server.start()
        try {
            val executor = SpeedSampleExecutors.okHttp(OkHttpClient())
            val sample = runBlocking {
                executor.sample(server.url("/down").toString(), payload.size.toLong(), deadlineMs = 10_000L)
            }
            assertEquals(payload.size.toLong(), sample?.bytes)
            assertTrue((sample?.elapsedMs ?: 0L) > 0L)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `okHttp executor runs the blocking transfer off the caller's dispatcher`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        // A synchronous OkHttp interceptor executes on the thread that called
        // execute() — so if the transfer escaped the IO hop it would run on the
        // named caller thread below, which is exactly the ANR shape (Issue #6).
        val callingThread = AtomicReference<String?>(null)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callingThread.compareAndSet(null, Thread.currentThread().name)
                chain.proceed(chain.request())
            }
            .build()
        val caller = newSingleThreadContext("speed-test-caller")
        try {
            val executor = SpeedSampleExecutors.okHttp(client)
            runBlocking(caller) {
                executor.sample(server.url("/down").toString(), maxBytes = 1024L, deadlineMs = 10_000L)
            }
            assertNotEquals(
                "speed-test-caller",
                callingThread.get(),
                "the blocking transfer must not run on the caller's dispatcher",
            )
        } finally {
            caller.close()
            server.shutdown()
        }
    }

    @Test
    fun `okHttp executor enforces the sample deadline against a stalled transfer`() {
        val server = MockWebServer()
        // Headers arrive immediately; the first body byte stalls past the
        // deadline, parking the read loop inside the socket — exactly the
        // shape that used to ANR the settings screen.
        server.enqueue(MockResponse().setBody("stalled").setBodyDelay(30, TimeUnit.SECONDS))
        server.start()
        try {
            val executor = SpeedSampleExecutors.okHttp(OkHttpClient())
            val startedAt = System.nanoTime()
            val sample = runBlocking {
                executor.sample(server.url("/down").toString(), maxBytes = 1024L, deadlineMs = 500L)
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            assertNull(sample, "a stalled transfer must fold to the null failure contract")
            assertTrue(elapsedMs < 5_000L, "the deadline must interrupt the blocking read, took ${elapsedMs}ms")
        } finally {
            server.shutdown()
        }
    }
}
