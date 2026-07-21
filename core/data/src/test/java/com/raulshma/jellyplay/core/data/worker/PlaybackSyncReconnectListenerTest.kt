package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.NetworkStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The reconnect listener should fire [PlaybackSyncScheduler.enqueueNow] exactly
 * once at [PlaybackSyncReconnectListener.start] (process-death recovery) plus
 * once per Offline→Online transition. It must not fire on steady Online
 * emissions or transitions that remain without validated internet (Local).
 *
 * Each test calls [PlaybackSyncReconnectListener.stop] so the infinite
 * `collect` does not leave a child job that would fail `runTest`.
 */
class PlaybackSyncReconnectListenerTest {

    private val networkMonitor: NetworkMonitor = mockk()
    private val scheduler: PlaybackSyncScheduler = mockk(relaxed = true)

    @Test
    fun `start enqueues immediately once for process-death recovery`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Online)
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

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
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            // 1 recovery + 1 transition.
            verify(exactly = 2) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `staying Online does not enqueue additional drains`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Online)
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()
            // Equal value — StateFlow emits nothing.
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
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Local // still no validated internet
            testScheduler.advanceUntilIdle()

            // Only the recovery call — Local is not "online" for sync purposes.
            verify(exactly = 1) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Local to Online enqueues one additional drain`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Local)
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            // 1 recovery + 1 transition.
            verify(exactly = 2) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `start is idempotent`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Online)
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

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
        every { networkMonitor.networkStatus } returns status
        val listener = PlaybackSyncReconnectListener(networkMonitor, scheduler, this)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Offline
            testScheduler.advanceUntilIdle()
            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            // 1 recovery + 2 transitions.
            verify(exactly = 3) { scheduler.enqueueNow() }
        } finally {
            listener.stop()
        }
    }
}
