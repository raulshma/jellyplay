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
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests [PlaybackSyncWorker] — the offline outbox drain + reconciliation.
 *
 * The worker is built via [TestListenableWorkerBuilder], which wires the
 * WorkManager foreground-notification infrastructure that `setForeground`
 * depends on. Constructing the worker directly makes `setForeground` hang on
 * its internal ListenableFuture. The builder's `WorkerFactory` injects the
 * mocked repository deps; assertions cover drain order, delete-on-success,
 * retry/failure policy, reconciliation branches, and the post-drain
 * `enqueueNow` trigger.
 *
 * The entry-type → API-call mapping itself is exercised in
 * `PlaybackRepositoryImplTest` (the repository owns it now); these tests stub
 * [PlaybackRepository.replayOutboxEntry] and assert the drain-loop behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSyncWorkerTest {

    private lateinit var context: Context
    private val outbox: PlaybackOutboxRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val playedStateSync: PlayedStateSync = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val userDataSyncScheduler: UserDataSyncScheduler = mockk(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { offlineModeManager.isOffline } returns false
        // Default: every replay lands. Tests override per entry/item to model failure.
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns true
        // Default: empty outbox; tests override via coEvery { outbox.drain() }.
        coEvery { outbox.drain() } returns emptyList()
        coEvery { outbox.count() } returns 0
        // Default: no downloaded items, so the reconcile batch is just the
        // outbox items (preserves the pre-Gap-A behaviour). Tests that exercise
        // the downloaded-item reconcile override this.
        coEvery { offlineRepository.getDownloadedItemIds() } returns emptyList()
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
                    outbox,
                    playbackRepository,
                    offlineModeManager,
                    playedStateSync,
                    offlineRepository,
                    userDataSyncScheduler,
                )
            })
            .setRunAttemptCount(runAttemptCount)
            .build()

    private fun entry(
        id: String,
        itemId: String,
        type: PlaybackOutboxEventType,
        sessionId: String = "s1",
        positionTicks: Long = 100L,
    ) = PlaybackOutboxEntry(
        id = id,
        itemId = itemId,
        eventType = type,
        sessionId = sessionId,
        positionTicks = positionTicks,
        isPaused = false,
        playMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId = null,
        recordedAt = 1_000L,
        createdAt = 1_000L,
    )

    // ── Empty / offline gating ────────────────────────────────────────

    @Test
    fun `empty outbox returns success without replaying`() = runTest {
        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 0) { userDataSyncScheduler.enqueueNow() }
    }

    @Test
    fun `offline completes without draining so manual reconnect can enqueue immediately`() = runTest {
        every { offlineModeManager.isOffline } returns true
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PROGRESS))

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { playbackRepository.replayOutboxEntry(any()) }
    }

    // ── Downloaded-item reconcile (Gap A: empty outbox still reconciles) ──

    @Test
    fun `downloaded items are reconciled even with an empty outbox`() = runTest {
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf("d1", "d2")
        coEvery { playedStateSync.reconcileOfflineRow(any()) } returns PlayedStateSync.ComputeResult.PLAYED

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { playedStateSync.reconcileOfflineRow("d1") }
        coVerify(exactly = 1) { playedStateSync.reconcileOfflineRow("d2") }
        // reconcile changed (PLAYED) → refresh online caches.
        coVerify(exactly = 1) { userDataSyncScheduler.enqueueNow() }
    }

    @Test
    fun `downloaded items reconcile with all NOOP does not trigger userDataSync`() = runTest {
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf("d1")
        coEvery { playedStateSync.reconcileOfflineRow(any()) } returns PlayedStateSync.ComputeResult.NOOP

        buildWorker().doWork()

        coVerify(exactly = 1) { playedStateSync.reconcileOfflineRow("d1") }
        coVerify(exactly = 0) { userDataSyncScheduler.enqueueNow() }
    }

    @Test
    fun `empty outbox and no downloads returns success without reconcile`() = runTest {
        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { playedStateSync.reconcileOfflineRow(any()) }
        coVerify(exactly = 0) { userDataSyncScheduler.enqueueNow() }
    }

    // ── Happy path: all entries succeed ───────────────────────────────

    @Test
    fun `all entries replay and are deleted on success`() = runTest {
        val entries = listOf(
            entry("e1", "item-1", PlaybackOutboxEventType.START),
            entry("e2", "item-1", PlaybackOutboxEventType.PROGRESS),
            entry("e3", "item-1", PlaybackOutboxEventType.STOP),
        )
        coEvery { outbox.drain() } returns entries

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 3) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 3) { outbox.delete(any()) }
        coVerify(exactly = 1) { userDataSyncScheduler.enqueueNow() }
    }

    @Test
    fun `successful drain triggers userDataSync enqueueNow exactly once`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PROGRESS))

        buildWorker().doWork()

        coVerify(exactly = 1) { userDataSyncScheduler.enqueueNow() }
    }

    @Test
    fun `PLAYED entry is replayed and deleted on success`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PLAYED))

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
    }

    @Test
    fun `UNPLAYED entry is replayed and deleted on success`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.UNPLAYED))

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
    }

    @Test
    fun `replay failure retains the entry for retry`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PLAYED))
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns false

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { outbox.delete(any()) }
    }

    // ── Failure / retry policy ────────────────────────────────────────

    @Test
    fun `early attempt on a failed entry returns retry and retains the entry`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PROGRESS))
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns false

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { outbox.delete(any()) }
    }

    @Test
    fun `exhausted retries dead-letter a failing entry and returns success`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PLAYED))
        coEvery { playbackRepository.replayOutboxEntry(any()) } returns false

        // runAttemptCount >= MAX_RETRIES (3) triggers the dead-letter path.
        val result = buildWorker(runAttemptCount = 3).doWork()

        // Dead-lettered: the entry is flagged (not hard-deleted) so the row is
        // retained for audit but skipped by future drains — countFlow() still
        // reaches 0 and the sync indicator clears. The drain converges.
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 0) { outbox.delete("e1") }
        coVerify(exactly = 1) { outbox.markDeadLetter("e1") }
        // Nothing was reconciled — the report never landed on the server.
        coVerify(exactly = 0) { playedStateSync.reconcileOfflineRow(any()) }
    }

    @Test
    fun `exhausted retries still drain successes and dead-letter only failures`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", "item-1", PlaybackOutboxEventType.PROGRESS),
            entry("e2", "item-2", PlaybackOutboxEventType.PROGRESS),
        )
        coEvery { playbackRepository.replayOutboxEntry(match { it.itemId == "item-1" }) } returns true
        coEvery { playbackRepository.replayOutboxEntry(match { it.itemId == "item-2" }) } returns false
        // Success-side reconciliation early-returns (no offline row).

        val result = buildWorker(runAttemptCount = 3).doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { outbox.delete("e1") }
        coVerify(exactly = 0) { outbox.delete("e2") }
        coVerify(exactly = 1) { outbox.markDeadLetter("e2") }
        // Only the pushed item is reconciled; the dead-lettered one is not.
        coVerify(exactly = 1) { playedStateSync.reconcileOfflineRow("item-1") }
        coVerify(exactly = 0) { playedStateSync.reconcileOfflineRow("item-2") }
    }

    @Test
    fun `partial failure replays successful entries and retries failed ones`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", "item-1", PlaybackOutboxEventType.PROGRESS),
            entry("e2", "item-2", PlaybackOutboxEventType.PROGRESS),
        )
        coEvery { playbackRepository.replayOutboxEntry(match { it.itemId == "item-1" }) } returns true
        coEvery { playbackRepository.replayOutboxEntry(match { it.itemId == "item-2" }) } returns false

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)
        // Only the successful entry is deleted; the failed one is retained.
        coVerify(exactly = 1) { outbox.delete("e1") }
        coVerify(exactly = 0) { outbox.delete("e2") }
    }

    // ── Reconcile branches ────────────────────────────────────────────
    // Reconcile behaviour is verified in PlayedStateSyncImplTest — the worker
    // now delegates the merge to PlayedStateSync, so the worker test only
    // asserts that the worker *calls* reconcile for each drained item.

    @Test
    fun `reconcile failure during drain does not fail the worker`() = runTest {
        coEvery { outbox.drain() } returns listOf(entry("e1", "item-1", PlaybackOutboxEventType.PROGRESS))
        coEvery { playedStateSync.reconcileOfflineRow("item-1") } throws RuntimeException("reconcile failed")

        val result = buildWorker().doWork()

        // Reconcile is best-effort (wrapped in runCatching); the push still succeeded.
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
    }

    // ── Helpers ───────────────────────────────────────────────────────
}
