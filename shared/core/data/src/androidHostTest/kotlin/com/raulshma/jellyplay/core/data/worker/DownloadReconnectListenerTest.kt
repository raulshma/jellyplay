package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The reconnect listener should resume interrupted downloads exactly once
 * whenever the app becomes ready to download: validated Online connectivity and
 * Offline Mode disabled. Unlike [PlaybackSyncReconnectListener] there is no
 * startup flush — interrupted downloads are already recovered at cold start by
 * [com.raulshma.jellyplay.startup.DownloadRecoveryInitializer], so the listener
 * only needs to react to genuine Offline → Online transitions.
 */
class DownloadReconnectListenerTest {

    private val networkMonitor: NetworkMonitor = mockk()
    private val offlineModeManager: OfflineModeManager = mockk()
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)

    private fun listener(
        scope: CoroutineScope,
        networkStatus: MutableStateFlow<NetworkStatus>,
        offlineMode: MutableStateFlow<OfflineMode> = MutableStateFlow(OfflineMode.ONLINE),
    ): DownloadReconnectListener {
        every { networkMonitor.networkStatus } returns networkStatus
        every { offlineModeManager.offlineMode } returns offlineMode
        coEvery { downloadRepository.resumeInterruptedDownloads() } returns Unit
        return DownloadReconnectListener(networkMonitor, offlineModeManager, downloadRepository, scope)
    }

    @Test
    fun `start on an already-online process does not resume`() = runTest {
        // No Offline → Online transition occurred, so nothing should resume.
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online))

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition from Offline to Online resumes interrupted downloads`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Online to Offline does not resume`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Online)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Offline
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Offline to Local does not resume`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Offline)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Local
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `transition Local to Online resumes interrupted downloads`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Local)
        val listener = listener(this, status)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            status.value = NetworkStatus.Online
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `repeated Offline Online cycles resume each time`() = runTest {
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

            coVerify(exactly = 2) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `leaving manual Offline Mode resumes interrupted downloads while network remains online`() = runTest {
        val offlineMode = MutableStateFlow(OfflineMode.OFFLINE_MANUAL)
        val listener = listener(this, MutableStateFlow(NetworkStatus.Online), offlineMode)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            offlineMode.value = OfflineMode.ONLINE
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }

    @Test
    fun `leaving manual Offline Mode on a local network does not resume`() = runTest {
        val offlineMode = MutableStateFlow(OfflineMode.OFFLINE_MANUAL)
        val listener = listener(this, MutableStateFlow(NetworkStatus.Local), offlineMode)

        try {
            listener.start()
            testScheduler.advanceUntilIdle()

            offlineMode.value = OfflineMode.ONLINE
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { downloadRepository.resumeInterruptedDownloads() }
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

            coVerify(exactly = 1) { downloadRepository.resumeInterruptedDownloads() }
        } finally {
            listener.stop()
        }
    }
}
