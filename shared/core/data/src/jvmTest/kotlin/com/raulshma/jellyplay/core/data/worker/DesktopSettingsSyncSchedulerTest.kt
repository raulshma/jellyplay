package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.testutil.FakeNetworkMonitor
import com.raulshma.jellyplay.core.data.testutil.FakeOfflineModeManager
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [DesktopSettingsSyncScheduler]'s dirty-write debounce — the burst of
 * [DesktopSettingsSyncScheduler.enqueueNow] calls a save-heavy session mints
 * (search history, reader annotations) collapsing to ONE flush after the
 * burst settles, and a write after the window fired scheduling a fresh one.
 * The startup/reconnect/focus trigger choreography is the shared
 * [ReconnectTrigger]'s, pinned by [DesktopPlaybackSyncSchedulerTest] and
 * [ReconnectTriggerTest]; the flush itself is the engine's, pinned by the
 * profile-sync suites.
 *
 * The scheduler runs on a detached scope over the test's virtual-time
 * scheduler (the lane's DesktopPlaybackSyncSchedulerTest pattern): its
 * trigger collector is eternal and the scheduler has no stop() — started once
 * per process — so it must not be a TestScope child (runTest would report it
 * unfinished, and backgroundScope work is invisible to advanceUntilIdle); the
 * harness cancels the scope after each test to abandon the collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSettingsSyncSchedulerTest {

    /** Counts flush passes — the flush seam takes no trigger argument. */
    private class FlushCounter {
        var count = 0
    }

    private var driverScope: CoroutineScope? = null

    @AfterTest
    fun tearDown() {
        driverScope?.cancel()
        driverScope = null
    }

    private fun TestScope.buildScheduler(counter: FlushCounter): DesktopSettingsSyncScheduler {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        driverScope = scope
        return DesktopSettingsSyncScheduler(
            networkMonitor = FakeNetworkMonitor(NetworkStatus.Online),
            offlineModeManager = FakeOfflineModeManager(OfflineMode.ONLINE),
            scope = scope,
            flush = { counter.count++ },
        )
    }

    @Test
    fun `burst of dirty writes inside the window coalesces into one flush`() = runTest {
        val counter = FlushCounter()
        val scheduler = buildScheduler(counter)

        scheduler.start()
        advanceUntilIdle()
        assertEquals(1, counter.count) // the startup flush

        // N writes inside the window: each reschedules the timer, none fires.
        repeat(5) { scheduler.enqueueNow() }
        advanceTimeBy(DIRTY_WRITE_DEBOUNCE_MILLIS - 1)
        assertEquals(1, counter.count)

        // The settle window lapses: exactly one flush for the whole burst.
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(2, counter.count)
    }

    @Test
    fun `a dirty write after the window fired flushes again`() = runTest {
        val counter = FlushCounter()
        val scheduler = buildScheduler(counter)

        scheduler.start()
        advanceUntilIdle()
        assertEquals(1, counter.count)

        scheduler.enqueueNow()
        advanceUntilIdle()
        assertEquals(2, counter.count)

        // A later write starts its own window — debouncing never swallows it.
        scheduler.enqueueNow()
        advanceUntilIdle()
        assertEquals(3, counter.count)
    }
}

/** The scheduler's dirty-write window — class-private, so re-declared here. */
private const val DIRTY_WRITE_DEBOUNCE_MILLIS = 500L
