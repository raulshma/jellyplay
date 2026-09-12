package com.raulshma.jellyplay.core.notification.scheduler

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The reconnect listener must enqueue an immediate [NewMediaCheckWorker] run
 * exactly on the `Offline`/`Local` → `Online` ready-edge (validated internet +
 * Offline Mode disabled) and never on the initial subscription, on losing
 * connectivity, or on repeated `start()` calls. Mirrors the edge-table of
 * `DownloadReconnectListenerTest` — both listeners share [com.raulshma.jellyplay.core.data.worker.ReconnectTrigger],
 * so a regression here means the shared scaffolding regressed.
 */
class NotificationReconnectListenerTest {

    private val networkMonitor: NetworkMonitor = mockk()
    private val offlineModeManager: OfflineModeManager = mockk()
    private val notificationScheduler: NotificationScheduler = mockk(relaxed = true)

    private fun listener(
        scope: CoroutineScope,
        networkStatus: MutableStateFlow<NetworkStatus>,
        offlineMode: MutableStateFlow<OfflineMode> = MutableStateFlow(OfflineMode.ONLINE),
    ): NotificationReconnectListener {
        every { networkMonitor.networkStatus } returns networkStatus
        every { offlineModeManager.offlineMode } returns offlineMode
        return NotificationReconnectListener(networkMonitor, offlineModeManager, notificationScheduler, scope)
    }

    @Test
    fun `start on an already-online process does not enqueue`() = runTest {
        // No Offline → Online transition occurred, so nothing should be enqueued.
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online))

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            verify(exactly = 0) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition from Offline to Online enqueues an immediate check`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Online to Offline does not enqueue`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Online)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Offline
            testScheduler.advanceUntilIdle()

            verify(exactly = 0) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Offline to Local does not enqueue`() = runTest {
        // Local is an unvalidated LAN — treated the same as Offline.
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Local
            testScheduler.advanceUntilIdle()

            verify(exactly = 0) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Local to Online enqueues an immediate check`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Local)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `repeated Offline Online cycles enqueue each time`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Offline
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            verify(exactly = 2) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `leaving manual Offline Mode enqueues an immediate check while network remains online`() = runTest {
        // Toggling app-level Offline Mode back online emits no network
        // transition, yet the scan must still fire.
        val offlineMode = MutableStateFlow(OfflineMode.OFFLINE_MANUAL)
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online), offlineMode)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            offlineMode.value = OfflineMode.ONLINE
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `leaving manual Offline Mode on a local network does not enqueue`() = runTest {
        val offlineMode = MutableStateFlow(OfflineMode.OFFLINE_MANUAL)
        val listener = listener(this, MutableStateFlow(NetworkStatus.Local), offlineMode)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            offlineMode.value = OfflineMode.ONLINE
            testScheduler.advanceUntilIdle()

            verify(exactly = 0) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `start is idempotent`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            // One edge, one enqueue — not one per redundant start().
            verify(exactly = 1) { notificationScheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `stop disconnects the collector - later transitions do not enqueue`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        listener.start()
        testScheduler.advanceUntilIdle()
        listener.stop()

        status.value = NetworkStatus.Online
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { notificationScheduler.enqueueNow() }
    }
}
