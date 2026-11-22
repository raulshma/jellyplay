package com.raulshma.jellyplay.feature.player.video

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLoadWatchdogTest {

    @Test
    fun testFastLoadCompletesWithoutWarnings() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val warnings = mutableListOf<String>()
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            onWarning = { warnings.add(it) },
            onTimeout = {}
        )

        watchdog.start("Preparing")
        testScheduler.advanceTimeBy(100)

        watchdog.recordStage("FetchingMetadata")
        testScheduler.advanceTimeBy(100)

        watchdog.recordStage("ResolvingStream")
        testScheduler.advanceTimeBy(150)

        watchdog.recordStage("InitializingEngine")
        testScheduler.advanceTimeBy(50)

        watchdog.recordStage("PrimingBuffer")
        testScheduler.advanceTimeBy(200)

        watchdog.complete()

        assertTrue("No warnings should be emitted for fast load", warnings.isEmpty())
        val latencies = watchdog.stageLatencies
        assertTrue(latencies.containsKey("Preparing"))
        assertTrue(latencies.containsKey("FetchingMetadata"))
        assertTrue(latencies.containsKey("ResolvingStream"))
        assertTrue(latencies.containsKey("InitializingEngine"))
        assertTrue(latencies.containsKey("PrimingBuffer"))
    }

    @Test
    fun testSlowServerMetadataWarningEmittedAtThreshold() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val warnings = mutableListOf<String>()
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            onWarning = { warnings.add(it) }
        )

        watchdog.start("FetchingMetadata")

        testScheduler.advanceTimeBy(1400)
        assertTrue(warnings.isEmpty())

        testScheduler.advanceTimeBy(150)
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("network slow"))
    }

    @Test
    fun testSlowStreamResolutionWarning() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val warnings = mutableListOf<String>()
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            onWarning = { warnings.add(it) }
        )

        watchdog.start("ResolvingStream")

        testScheduler.advanceTimeBy(1900)
        assertTrue(warnings.isEmpty())

        testScheduler.advanceTimeBy(150)
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("Resolving stream"))
    }

    @Test
    fun testSlowBufferPrimingLadder() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val warnings = mutableListOf<String>()
        val timeouts = mutableListOf<String>()
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            onWarning = { warnings.add(it) },
            onTimeout = { timeouts.add(it) }
        )

        watchdog.start("PrimingBuffer")

        testScheduler.advanceTimeBy(2900)
        assertTrue(warnings.isEmpty())

        // 3s mark: slow buffer priming warning
        testScheduler.advanceTimeBy(150)
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("Priming player buffer"))

        // 8s mark: actionable prompt
        testScheduler.advanceTimeBy(5000)
        assertEquals(2, warnings.size)
        assertTrue(warnings[1].contains("taking longer"))

        // 20s mark: timeout
        testScheduler.advanceTimeBy(12000)
        assertEquals(1, timeouts.size)
        assertEquals("PrimingBuffer", timeouts.first())
    }

    @Test
    fun testCompleteCancelsWatchdogs() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val warnings = mutableListOf<String>()
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            onWarning = { warnings.add(it) }
        )

        watchdog.start("PrimingBuffer")
        testScheduler.advanceTimeBy(1000)
        watchdog.complete()

        testScheduler.advanceTimeBy(25000)
        assertTrue("No warnings should fire after complete()", warnings.isEmpty())
    }

    /**
     * A stage re-entered across an engine replacement (the mid-priming
     * reloadWithEngine re-arms PrimingBuffer) must ACCUMULATE its completed
     * durations instead of overwriting the prior visit: the 4-second visit
     * followed by the 2-second visit totals 6 seconds. A second complete()
     * is idempotent — it must not re-count the final stage.
     */
    @Test
    fun testRepeatedStageVisits_accumulateInsteadOfOverwriting() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val warnings = mutableListOf<String>()
        val timeouts = mutableListOf<String>()
        // The clock is offset past zero: the watchdog treats a stage start
        // timestamp of 0 as "never started" and would skip the latency write.
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            onWarning = { warnings.add(it) },
            onTimeout = { timeouts.add(it) },
            nowMs = { 1_000L + testScheduler.currentTime },
        )

        // "Preparing" arms no warning timers, so the virtual-time visits
        // below drive latencies without polluting the callback lists.
        watchdog.start("Preparing")
        testScheduler.advanceTimeBy(4000)
        watchdog.recordStage("InitializingEngine")
        testScheduler.advanceTimeBy(1000)
        // Re-entering "Preparing" (the replacement flow's re-arm shape).
        watchdog.recordStage("Preparing")
        testScheduler.advanceTimeBy(2000)
        watchdog.complete()

        assertEquals(6000L, watchdog.stageLatencies["Preparing"])
        assertEquals(1000L, watchdog.stageLatencies["InitializingEngine"])

        // Idempotent completion: a second complete() re-records nothing.
        watchdog.complete()
        assertEquals(6000L, watchdog.stageLatencies["Preparing"])
        assertTrue(warnings.isEmpty())
        assertTrue(timeouts.isEmpty())
    }

    /** A single visit keeps the plain elapsed value (no accumulation drift). */
    @Test
    fun testSingleStageVisit_preservesThePlainElapsed() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val watchdog = SessionLoadWatchdog(
            scope = scope,
            nowMs = { 1_000L + testScheduler.currentTime },
        )

        watchdog.start("Preparing")
        testScheduler.advanceTimeBy(3200)
        watchdog.recordStage("FetchingMetadata")
        testScheduler.advanceTimeBy(800)
        watchdog.complete()

        assertEquals(3200L, watchdog.stageLatencies["Preparing"])
        assertEquals(800L, watchdog.stageLatencies["FetchingMetadata"])
    }
}
