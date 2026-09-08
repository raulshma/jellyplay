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
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
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
 * Drain-loop resilience companions to [PlaybackSyncWorkerTest]: the seams the
 * happy-path suite leaves open — every one of them a way a real device has of
 * breaking an offline→online sync:
 *
 *  1. suppression is ORDER-INDEPENDENT — a STOP captured before the watched
 *     flip must still be dropped when a PLAYED intent is staged for the item
 *     (a trailing near-end STOP replayed after markPlayedItem is the #153
 *     "watched online again" poison producer);
 *  2. a Room read failure during derived-flip derivation (getOfflineItem
 *     throws) must degrade to "no derivation", never crash the drain — the
 *     telemetry itself is still replayed;
 *  3. a failing played-intent probe must fall back to REPLAYING telemetry
 *     (getOrDefault(false) — suppression requires a confirmed intent, not an
 *     unknown), so a transient DB error cannot silently delete position
 *     reports;
 *  4. notification plumbing (foreground promotion, mid-drain count update,
 *     dismissal) is best-effort: an OEM-restricted or otherwise throwing
 *     notification path must never fail the drain itself.
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
                    outbox,
                    playbackRepository,
                    offlineModeManager,
                    playedStateSync,
                    offlineRepository,
                    userDataSyncScheduler,
                    mediaRepository,
                    cacheInvalidator,
                )
            })
            .setRunAttemptCount(runAttemptCount)
            .build()


    private fun mirrorRow(
        isPlayed: Boolean = false,
        playedPercentage: Double = 50.0,
    ) = OfflineMediaItem(
        id = ITEM_ID,
        name = "Episode",
        mediaType = MediaType.EPISODE,
        isPlayed = isPlayed,
        playedPercentage = playedPercentage,
        runTimeTicks = 60_000_000L,
        playbackPositionTicks = ((playedPercentage / 100.0) * 60_000_000L).toLong(),
    )

    // ── 1. Suppression is order-independent (#153) ──────────────────────

    @Test
    fun `telemetry captured before the watched flip is still suppressed`() = runTest {
        // Drain order is oldest-first by createdAt; a STOP recorded at t=5
        // with the PLAYED intent staged at t=6 still precedes it in the list.
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
            entry("e2", ITEM_ID, PlaybackOutboxEventType.PROGRESS, positionTicks = 30_000_000L),
            entry("e3", ITEM_ID, PlaybackOutboxEventType.PLAYED),
        )
        coEvery { outbox.hasUnsyncedPlayedIntent(ITEM_ID) } returns true

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        // Only the PLAYED intent is replayed; both telemetry rows are dropped
        // in place — replaying either would re-poison the server row.
        coVerify(exactly = 1) {
            playbackRepository.replayOutboxEntry(match { it.eventType == PlaybackOutboxEventType.PLAYED })
        }
        coVerify(exactly = 0) {
            playbackRepository.replayOutboxEntry(match { it.eventType == PlaybackOutboxEventType.STOP })
            playbackRepository.replayOutboxEntry(match { it.eventType == PlaybackOutboxEventType.PROGRESS })
        }
        coVerify(exactly = 3) { outbox.delete(any()) }
    }

    // ── 2. Mirror-row read failure degrades, never crashes ──────────────

    @Test
    fun `a failing mirror-row read replays telemetry and skips the derived flip`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
        )
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } throws
            android.database.SQLException("room read failed")

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
        // No mirror row → no derivation; markPlayed must not fire blind.
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    // ── 3. A failing intent probe replays instead of suppressing ────────

    @Test
    fun `a failing played-intent probe replays telemetry instead of deleting it`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
        )
        // Unknown ≠ staged: suppression needs a confirmed intent, so a DB
        // failure must fall back to replaying the position report.
        coEvery { outbox.hasUnsyncedPlayedIntent(ITEM_ID) } throws
            android.database.SQLException("room read failed")
        // Mirror row mid-watch: nothing to derive even if it were readable.
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns mirrorRow(playedPercentage = 40.0)

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { playbackRepository.replayOutboxEntry(any()) }
        coVerify(exactly = 1) { outbox.delete("e1") }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
    }

    // ── 4. Notification plumbing is best-effort ─────────────────────────

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

    private companion object {
        const val ITEM_ID = "item-1"
        const val OTHER_ITEM_ID = "item-2"
    }
}
