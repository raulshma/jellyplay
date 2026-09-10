package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Worker-adapter resilience companions to [OfflineWatchSyncContractTest] —
 * the WorkManager-shaped seams of [PlaybackSyncWorker], which is now a thin
 * adapter over the shared [PlaybackOutboxDrainer] (the drain-policy suites
 * moved with the body to :shared:core:data's jvmTest lane):
 *
 *  1. notification plumbing (foreground promotion, mid-drain count update,
 *     dismissal) is best-effort: an OEM-restricted or otherwise throwing
 *     notification path must never fail the drain itself;
 *  2. the adapter's one decision — mapping the drain's `retriesPending` onto
 *     WorkManager's Result.retry() — pins so a failing replay cannot be
 *     reported as success.
 *
 * The worker is built via [TestListenableWorkerBuilder], which wires the
 * WorkManager foreground-notification infrastructure that `setForeground`
 * depends on (constructing the worker directly makes `setForeground` hang on
 * its internal ListenableFuture). The drainer here is the REAL shared impl
 * over the same mocked repositories, with the worker itself serving as its
 * Notifier — exactly the production wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSyncWorkerResilienceTest {

    private lateinit var context: Context
    private val outbox: PlaybackOutboxRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val playedStateSync: PlayedStateSync = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val userDataSyncScheduler: UserDataSyncScheduler = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val cacheInvalidator: MediaCacheInvalidator = mockk(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { offlineModeManager.isOffline } returns false
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns true
        coEvery { outbox.drain() } returns emptyList()
        coEvery { offlineRepository.getDownloadedItemIds() } returns emptyList()
        coEvery { outbox.hasUnsyncedPlayedIntent(any()) } returns false
        coEvery { outbox.hasUnsyncedUnplayedIntent(any()) } returns false
        coEvery { offlineRepository.getOfflineItem(any()) } returns null
        coEvery { outbox.isPlayedStateIntentDelivered(any(), any()) } returns true
        coEvery { mediaRepository.markPlayed(any()) } returns Result.success(Unit)
        coEvery { playedStateSync.reconcileOfflineRow(any()) } returns
            PlayedStateSync.ReconcileOutcome.NoChange
    }

    @After
    fun teardown() {
        runCatching { unmockkObject(PlaybackSyncNotificationHelper) }
    }

    private fun buildWorker(runAttemptCount: Int = 0): PlaybackSyncWorker =
        TestListenableWorkerBuilder<PlaybackSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): PlaybackSyncWorker = PlaybackSyncWorker(
                    appContext,
                    workerParameters,
                    createDrainer = { notifier ->
                        PlaybackOutboxDrainerImpl(
                            outbox = outbox,
                            playbackRepository = playbackRepository,
                            offlineModeManager = offlineModeManager,
                            playedStateSync = playedStateSync,
                            offlineRepository = offlineRepository,
                            mediaRepository = mediaRepository,
                            cacheInvalidator = cacheInvalidator,
                            userDataSyncTrigger = PlaybackOutboxDrainer.UserDataSyncTrigger {
                                userDataSyncScheduler.enqueueNow()
                            },
                            notifier = notifier,
                        )
                    },
                )
            })
            .setRunAttemptCount(runAttemptCount)
            .build()

    // ── 1. Notification plumbing is best-effort ─────────────────────────

    @Test
    fun `a failing foreground promotion does not fail the drain`() = runTest {
        mockkObject(PlaybackSyncNotificationHelper)
        every { PlaybackSyncNotificationHelper.createForegroundInfo(any(), any()) } throws
            RuntimeException("OEM foreground restriction")
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.PLAYED),
        )

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
    }

    @Test
    fun `a failing mid-drain notification update does not fail the drain`() = runTest {
        mockkObject(PlaybackSyncNotificationHelper)
        every { PlaybackSyncNotificationHelper.updateNotification(any(), any()) } throws
            RuntimeException("notification service dead")
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.PLAYED),
            entry("e2", OTHER_ITEM_ID, PlaybackOutboxEventType.UNPLAYED),
        )

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 2) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 2) { outbox.delete(any()) }
    }

    @Test
    fun `a failing notification dismissal does not fail the drain`() = runTest {
        mockkObject(PlaybackSyncNotificationHelper)
        every { PlaybackSyncNotificationHelper.dismissNotification(any()) } throws
            RuntimeException("notification service dead")
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.PLAYED),
        )

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { outbox.delete("e1") }
    }

    // ── 2. The adapter's retry() mapping ─────────────────────────────────

    @Test
    fun `a retries-pending drain maps to WorkManager retry`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", ITEM_ID, PlaybackOutboxEventType.PLAYED))
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns false

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)
    }

    private companion object {
        const val ITEM_ID = "item-1"
        const val OTHER_ITEM_ID = "item-2"
    }
}
