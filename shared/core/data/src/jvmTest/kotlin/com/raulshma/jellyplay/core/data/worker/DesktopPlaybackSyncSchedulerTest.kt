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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [DesktopPlaybackSyncScheduler]'s trigger shell around the shared
 * [PlaybackOutboxDrainer] — the drain choreography itself is pinned by
 * [PlaybackOutboxDrainerTest]; this suite pins WHEN a drain pass is launched:
 *
 *  - once at [DesktopPlaybackSyncScheduler.start] (and only once — a
 *    redundant start does not re-run the startup drain),
 *  - on the network Offline → Online transition,
 *  - on the app-level Offline Mode toggling back online with NO network
 *    transition — the desktop alignment with Android's
 *    [PlaybackSyncReconnectListener] made when this scheduler collapsed onto
 *    the shared [ReconnectTrigger] (the former hand-rolled watcher observed
 *    `networkStatus` alone; see the scheduler KDoc's declared delta),
 *  - on [DesktopPlaybackSyncScheduler.enqueueNow] (the manual "sync now"),
 *  - and NOT on [DesktopPlaybackSyncScheduler.enqueuePeriodic] — desktop's
 *    recorded no-periodic-backstop delta: a row staged while continuously
 *    online waits for the next edge / manual sync / restart.
 *
 * Every drain is a fresh `attempt = 0` pass. The scheduler runs on a detached
 * scope over the test's virtual-time scheduler (the lane's
 * DesktopAutoDownloadSchedulerTest pattern): its trigger collector is eternal
 * and the scheduler has no stop() — started once per process — so it must not
 * be a TestScope child (runTest would report it unfinished, and
 * backgroundScope work is invisible to advanceUntilIdle); the harness cancels
 * the scope after each test to abandon the collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackSyncSchedulerTest {

    /** Records one attempt number per drainOnce pass. */
    private class RecordingDrainer : PlaybackOutboxDrainer {
        val attempts = mutableListOf<Int>()

        override suspend fun drainOnce(attempt: Int): PlaybackOutboxDrainer.DrainResult {
            attempts += attempt
            return PlaybackOutboxDrainer.DrainResult()
        }
    }

    private var driverScope: CoroutineScope? = null

    @AfterTest
    fun tearDown() {
        driverScope?.cancel()
        driverScope = null
    }

    private fun TestScope.buildScheduler(
        monitor: FakeNetworkMonitor,
        offline: FakeOfflineModeManager,
        drainer: RecordingDrainer,
    ): DesktopPlaybackSyncScheduler {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        driverScope = scope
        return DesktopPlaybackSyncScheduler(
            drainer = drainer,
            networkMonitor = monitor,
            offlineModeManager = offline,
            scope = scope,
        )
    }

    @Test
    fun `start drains once at startup when already online`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val drainer = RecordingDrainer()
        val scheduler = buildScheduler(monitor, offline, drainer)

        scheduler.start()
        advanceUntilIdle()

        // Seeded already-ready: the startup pass only — no spurious
        // reconnect drain from the first collect.
        assertEquals(listOf(0), drainer.attempts)
    }

    @Test
    fun `redundant start does not re-run the startup drain`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val drainer = RecordingDrainer()
        val scheduler = buildScheduler(monitor, offline, drainer)

        scheduler.start()
        scheduler.start()
        advanceUntilIdle()

        assertEquals(listOf(0), drainer.attempts)
    }

    @Test
    fun `Offline to Online network transition drains`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Offline)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val drainer = RecordingDrainer()
        val scheduler = buildScheduler(monitor, offline, drainer)

        scheduler.start()
        advanceUntilIdle()
        assertEquals(1, drainer.attempts.size)

        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(2, drainer.attempts.size)
    }

    @Test
    fun `offline mode toggled back online drains without a network transition`() = runTest {
        // The alignment pin: the network stays Online throughout; only the
        // app-level Offline Mode flips. The former network-only watcher fired
        // nothing here — on Android this always drained.
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.OFFLINE_MANUAL)
        val drainer = RecordingDrainer()
        val scheduler = buildScheduler(monitor, offline, drainer)

        scheduler.start()
        advanceUntilIdle()
        assertEquals(1, drainer.attempts.size) // startup only

        offline.toggleManualOffline() // OFFLINE_MANUAL → ONLINE, no network change
        advanceUntilIdle()

        assertEquals(2, drainer.attempts.size)
    }

    @Test
    fun `enqueueNow drains manually`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val drainer = RecordingDrainer()
        val scheduler = buildScheduler(monitor, offline, drainer)

        scheduler.start()
        advanceUntilIdle()
        assertEquals(1, drainer.attempts.size)

        scheduler.enqueueNow()
        advanceUntilIdle()
        assertEquals(2, drainer.attempts.size)
    }

    @Test
    fun `enqueuePeriodic is a no-op - no periodic backstop on desktop`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val drainer = RecordingDrainer()
        val scheduler = buildScheduler(monitor, offline, drainer)

        scheduler.start()
        advanceUntilIdle()
        assertEquals(1, drainer.attempts.size)

        // The recorded desktop delta: no WorkManager-style periodic backstop
        // in process — enqueuePeriodic must not schedule anything.
        scheduler.enqueuePeriodic()
        advanceUntilIdle()

        assertEquals(1, drainer.attempts.size)
    }
}
