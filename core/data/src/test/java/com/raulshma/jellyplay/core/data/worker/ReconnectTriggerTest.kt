package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pins the [ReconnectTrigger] edge-detection protocol described in its KDoc:
 *
 *  - `start()` is idempotent — returns false without re-launching when a
 *    collector is already active.
 *  - `onReady` fires **exactly once** per `not-ready → ready` edge, where
 *    ready = `NetworkStatus.Online && OfflineMode.ONLINE` (Local counts as
 *    not-ready; toggling app-level Offline Mode back online also fires, since
 *    OfflineModeManager is watched alongside NetworkMonitor).
 *  - The first collect is seeded (`wasReady` from current flow values), so
 *    starting already-ready fires nothing.
 *  - An [onReady] exception is swallowed via Log.w and **does not kill the
 *    collector** — the next ready edge still fires.
 *  - `stop()` cancels the collector and `start()` can restart watching — the
 *    TestScope protocol: tests must stop() so runTest sees no leaked jobs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTriggerTest {

    private class FakeNetworkMonitor(initial: NetworkStatus) : NetworkMonitor {
        override val networkStatus = MutableStateFlow(initial)
        override val isMetered = MutableStateFlow(false)
    }

    private class FakeOfflineModeManager(initial: OfflineMode) : OfflineModeManager {
        override val offlineMode = MutableStateFlow(initial)
        override val isOffline: Boolean get() = offlineMode.value != OfflineMode.ONLINE
        override val networkStatus = MutableStateFlow(NetworkStatus.Online)
        override fun toggleManualOffline() {
            offlineMode.value = if (offlineMode.value == OfflineMode.ONLINE) {
                OfflineMode.OFFLINE_MANUAL
            } else {
                OfflineMode.ONLINE
            }
        }
        override fun checkNetworkAndAutoDetect() = Unit
    }

    private fun TestScope.trigger(
        networkMonitor: FakeNetworkMonitor,
        offlineModeManager: FakeOfflineModeManager,
        calls: MutableList<Unit>,
        failFirstCall: Boolean = false,
    ): ReconnectTrigger = ReconnectTrigger(
        networkMonitor = networkMonitor,
        offlineModeManager = offlineModeManager,
        scope = this,
        tag = "ReconnectTriggerTest",
        onReady = {
            calls += Unit
            if (failFirstCall && calls.size == 1) throw IOException("transient flush failure")
        },
    )

    @Test
    fun `start returns true and seeds wasReady so an already-ready state fires nothing`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val calls = mutableListOf<Unit>()
        val reconnect = trigger(monitor, offline, calls)

        val started = reconnect.start()
        advanceUntilIdle()

        assertTrue(started)
        assertEquals(0, calls.size)
        reconnect.stop()
    }

    @Test
    fun `start returns false when a collector is already active`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Offline)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val calls = mutableListOf<Unit>()
        val reconnect = trigger(monitor, offline, calls)

        assertTrue(reconnect.start())
        assertFalse(reconnect.start())

        // Still a single collector: one transition → exactly one call.
        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(1, calls.size)
        reconnect.stop()
    }

    @Test
    fun `Offline to Online transition fires onReady exactly once`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Offline)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val calls = mutableListOf<Unit>()
        val reconnect = trigger(monitor, offline, calls)

        reconnect.start()
        advanceUntilIdle()
        assertEquals(0, calls.size)

        // Local (captive portal) is treated as not-ready.
        monitor.networkStatus.value = NetworkStatus.Local
        advanceUntilIdle()
        assertEquals(0, calls.size)

        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(1, calls.size)

        // No further transitions → no further calls.
        advanceUntilIdle()
        assertEquals(1, calls.size)
        reconnect.stop()
    }

    @Test
    fun `toggling Offline Mode back online fires onReady without a network transition`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Online)
        val offline = FakeOfflineModeManager(OfflineMode.OFFLINE_MANUAL)
        val calls = mutableListOf<Unit>()
        val reconnect = trigger(monitor, offline, calls)

        reconnect.start()
        advanceUntilIdle()
        assertEquals(0, calls.size)

        offline.toggleManualOffline() // OFFLINE_MANUAL → ONLINE, no network change
        advanceUntilIdle()

        assertEquals(1, calls.size)
        reconnect.stop()
    }

    @Test
    fun `onReady exception is logged not rethrown and the collector survives`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Offline)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val calls = mutableListOf<Unit>()
        val reconnect = trigger(monitor, offline, calls, failFirstCall = true)

        reconnect.start()
        advanceUntilIdle()

        // First edge → onReady throws (swallowed via Log.w path).
        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(1, calls.size)

        // Collector is still alive: the next edge fires again.
        monitor.networkStatus.value = NetworkStatus.Offline
        advanceUntilIdle()
        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(2, calls.size)
        reconnect.stop()
    }

    @Test
    fun `stop cancels the collector and start can restart watching`() = runTest {
        val monitor = FakeNetworkMonitor(NetworkStatus.Offline)
        val offline = FakeOfflineModeManager(OfflineMode.ONLINE)
        val calls = mutableListOf<Unit>()
        val reconnect = trigger(monitor, offline, calls)

        reconnect.start()
        reconnect.stop()

        // No collector running: transitions fire nothing.
        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(0, calls.size)

        // Restart: wasReady is re-seeded from the current (Online) values, so
        // nothing fires until a fresh not-ready → ready edge.
        assertTrue(reconnect.start())
        advanceUntilIdle()
        assertEquals(0, calls.size)

        monitor.networkStatus.value = NetworkStatus.Offline
        advanceUntilIdle()
        monitor.networkStatus.value = NetworkStatus.Online
        advanceUntilIdle()
        assertEquals(1, calls.size)
        reconnect.stop()
    }
}
