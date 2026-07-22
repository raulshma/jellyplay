package com.raulshma.jellyplay.core.data.worker

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
 * The reconnect listener should fire [PlaybackSyncScheduler.enqueueNow] exactly
 * once at [PlaybackSyncReconnectListener.start] (process-death recovery) plus
 * once whenever the app becomes ready to sync: validated Online connectivity
 * and Offline Mode disabled.
 */
class PlaybackSyncReconnectListenerTest {

    private val networkMonitor: NetworkMonitor = mockk()
    private val offlineModeManager: OfflineModeManager = mockk()
    private val scheduler: PlaybackSyncScheduler = mockk(relaxed = true)

    private fun listener(
        scope: CoroutineScope,
        networkStatus: MutableStateFlow<NetworkStatus>,
        offlineMode: MutableStateFlow<OfflineMode> = MutableStateFlow(OfflineMode.ONLINE),
    ): PlaybackSyncReconnectListener {
        every { networkMonitor.networkStatus } returns networkStatus
        every { offlineModeManager.offlineMode } returns offlineMode
        return PlaybackSyncReconnectListener(networkMonitor, offlineModeManager, scheduler, scope)
    }

    @Test
    fun `start enqueues immediately once for process-death recovery`() = runTest {
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online))

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition from Offline to Online enqueues one additional drain`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            verify(exactly = 2) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `staying Online does not enqueue additional drains`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Online)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Offline to Local does not enqueue`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Local
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Local to Online enqueues one additional drain`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Local)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            verify(exactly = 2) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `start is idempotent`() = runTest {
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online))

        try {
            listener.start()
            listener.start()
            testScheduler.advanceUntilIdle()

            verify(exactly = 1) { scheduler.enqueueNow() }
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

            verify(exactly = 3) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `leaving manual Offline Mode enqueues an immediate drain while network remains online`() = runTest {
        val offlineMode = MutableStateFlow(OfflineMode.OFFLINE_MANUAL)
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online), offlineMode)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            offlineMode.value = OfflineMode.ONLINE
            testScheduler.advanceUntilIdle()

            verify(exactly = 2) { scheduler.enqueueNow() }
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

            verify(exactly = 1) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }
}
