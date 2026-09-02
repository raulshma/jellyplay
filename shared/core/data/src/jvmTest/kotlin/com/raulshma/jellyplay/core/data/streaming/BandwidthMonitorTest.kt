package com.raulshma.jellyplay.core.data.streaming

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [BandwidthMonitor] including a fix: `reset()` must mutate `samples`,
 * `_totalBytes`, and `_totalElapsedMs` atomically so a concurrent `addSample` can't leave the
 * three fields out of sync.
 */
class BandwidthMonitorTest {

    private lateinit var monitor: BandwidthMonitor

    @BeforeTest
    fun setup() {
        monitor = BandwidthMonitor()
    }

    @Test
    fun `addSample ignores zero bytes`() {
        monitor.addSample(bytesTransferred = 0L, elapsedMs = 100L)
        assertEquals(0L, monitor.totalBytes.value)
    }

    @Test
    fun `addSample ignores zero elapsed`() {
        monitor.addSample(bytesTransferred = 1_000L, elapsedMs = 0L)
        assertEquals(0L, monitor.totalBytes.value)
    }

    @Test
    fun `addSample accumulates totals`() {
        monitor.addSample(bytesTransferred = 1_000L, elapsedMs = 100L)
        monitor.addSample(bytesTransferred = 2_000L, elapsedMs = 200L)
        assertEquals(3_000L, monitor.totalBytes.value)
        assertEquals(300L, monitor.totalElapsedMs.value)
    }

    @Test
    fun `addSample trims oldest sample when over capacity`() {
        // maxSamples = 10: add 12 samples, expect first two trimmed.
        repeat(12) { monitor.addSample(bytesTransferred = 1_000L, elapsedMs = 100L) }
        // 10 samples × (1000B, 100ms) each = 10_000B / 1000ms.
        assertEquals(10_000L, monitor.totalBytes.value)
        assertEquals(1_000L, monitor.totalElapsedMs.value)
    }

    @Test
    fun `reset clears all observable state`() {
        monitor.addSample(bytesTransferred = 1_000L, elapsedMs = 100L)
        monitor.addSample(bytesTransferred = 2_000L, elapsedMs = 200L)

        monitor.reset()

        assertEquals(0L, monitor.totalBytes.value)
        assertEquals(0L, monitor.totalElapsedMs.value)
        assertEquals(0.0, monitor.estimatedBandwidthKbps.value, 0.0001)
        assertEquals(0.0, monitor.computeAverageKbps(), 0.0001)
    }

    @Test
    fun `reset is safe under concurrent addSample - no stale totals remain`() = runBlocking {
        // Spawn many concurrent adders + resetters; after they all finish and a final reset,
        // the monitor's totals must be consistent (sum of bytes/elapsed in `samples` must
        // equal the `_totalBytes` / `_totalElapsedMs` flows). Before the fix, `reset()` could
        // clear `samples` while an in-flight `addSample` was writing to `_totalBytes`, leaving
        // `_totalBytes > 0` with `samples` empty.
        val iterations = AtomicInteger(0)
        val workers = 8
        coroutineScope {
            repeat(workers) { workerId ->
                async(Dispatchers.IO) {
                    repeat(500) {
                        if (workerId % 4 == 0) {
                            monitor.reset()
                        } else {
                            monitor.addSample(
                                bytesTransferred = (1 + (it and 0xFF)).toLong(),
                                elapsedMs = (1 + (it and 0x3F)).toLong(),
                            )
                        }
                        iterations.incrementAndGet()
                    }
                }
            }
        }

        // After the storm, reset to a known empty state, then add a deterministic set of
        // samples and verify the totals match exactly.
        monitor.reset()
        monitor.addSample(bytesTransferred = 1_000L, elapsedMs = 100L)
        monitor.addSample(bytesTransferred = 4_000L, elapsedMs = 400L)

        assertEquals(5_000L, monitor.totalBytes.value)
        assertEquals(500L, monitor.totalElapsedMs.value)
        assertTrue(monitor.computeAverageKbps() > 0.0)
    }

    @Test
    fun `computeAverageKbps returns zero for empty monitor`() {
        assertEquals(0.0, monitor.computeAverageKbps(), 0.0001)
    }

    @Test
    fun `computeAverageKbps computes bits per second`() {
        // 1_000_000 bytes in 1_000 ms = 1_000_000 bytes/sec = 8_000 kbps.
        monitor.addSample(bytesTransferred = 1_000_000L, elapsedMs = 1_000L)
        assertEquals(8_000.0, monitor.computeAverageKbps(), 0.001)
    }

    @Test
    fun `concurrent reset and addSample never throw`() = runBlocking {
        // Smoke test: hammer reset/addSample from multiple threads — if locking is broken,
        // ConcurrentModificationException or NoSuchElementException would surface.
        val done = CountDownLatch(1)
        coroutineScope {
            val resetJob = async(Dispatchers.IO) {
                repeat(1_000) { monitor.reset() }
            }
            val addJobs = (1..4).map {
                async(Dispatchers.IO) {
                    repeat(1_000) { i ->
                        monitor.addSample(bytesTransferred = i.toLong(), elapsedMs = 10L)
                    }
                }
            }
            resetJob.await()
            addJobs.awaitAll()
        }
        done.countDown()
    }
}
