package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlayedStateSyncImpl
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cross-layer contract tests for the offline→online watch-state pipeline
 * (#153 / #157 regression class). Unlike [PlaybackSyncWorkerTest] — which
 * stubs the repositories to pin the drain loop in isolation — this suite
 * wires the REAL [PlayedStateSyncImpl] (flip + reconcile + the #157 heal) and
 * the REAL [PlaybackRepositoryImpl] (outbox-entry → API-call mapping) into
 * the real [PlaybackSyncWorker], so the seams between the layers are covered
 * end-to-end: the exact failure modes that twice produced "watched episode
 * stuck in Continue Watching".
 *
 * Scenario inventory (each pins one invariant the shipped fixes rely on):
 *  1. a watched-offline session drains to exactly one markPlayedItem; the
 *     trailing telemetry is suppressed (the #153 server-poison producer);
 *  2. a poisoned server row (Played=true + stale position — the #157 state)
 *     is healed by re-asserting markPlayed during the drain's reconcile pass;
 *  3. a heal that fails still resets the local row and re-stages the intent
 *     for the next drain (best-effort, no silent loss);
 *  4. an offline UNPLAYED intent replays and suppresses the derived watched
 *     flip — the user's unwatch must not be undone;
 *  5. a lost intent row is recovered from the sticky mirror row (derive net);
 *  6. the reconcile-only backstop path (empty outbox) heals poisoned rows —
 *     the path that cleans up state poisoned by older builds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineWatchSyncContractTest {

    private lateinit var context: Context
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val outbox: PlaybackOutboxRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val offlineRepository: OfflineRepository = mockk(relaxUnitFun = true)
    private val userDataSyncScheduler: UserDataSyncScheduler = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val cacheInvalidator: MediaCacheInvalidator = mockk(relaxed = true)
    private val downloadsStore: DownloadsStore = mockk()
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)

    private val fakeTimeSource = object : TimeSource {
        override fun nowEpochMillis(): Long = 1_770_000_000_000L
        override fun nowElapsedRealtimeMillis(): Long = 1_770_000_000_000L
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 2, 1)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { offlineModeManager.isOffline } returns false
        every { apiClient.session } returns MutableStateFlow(null)
        // Auto-delete-after-watch off: the heal is a repair, not a watch
        // event, and these tests never assert download deletion anyway.
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        coEvery { outbox.drain() } returns emptyList()
        coEvery { offlineRepository.getDownloadedItemIds() } returns emptyList()
        coEvery { mediaRepository.markPlayed(any()) } returns Result.success(Unit)
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns
            Result.failure(java.io.IOException("no detail"))
        coEvery { apiClient.markPlayed(any()) } returns Result.success(Unit)
        coEvery { apiClient.markUnplayed(any()) } returns Result.success(Unit)
        coEvery { apiClient.reportPlaybackStart(any(), any(), any()) } returns Result.success(Unit)
        coEvery { apiClient.reportPlaybackProgress(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { apiClient.reportPlaybackStopped(any(), any(), any()) } returns Result.success(Unit)
    }

    private fun buildWorker(runAttemptCount: Int = 0): PlaybackSyncWorker {
        val homeSession = HomeSession(apiClient, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val sessionCacheRegistry = SessionCacheRegistry(homeSession, CoroutineScope(SupervisorJob()))
        val playbackRepository = PlaybackRepositoryImpl(
            apiClient, outbox, offlineModeManager, homeSession, sessionCacheRegistry,
        )
        val playedStateSync = PlayedStateSyncImpl(
            apiClient = apiClient,
            offlineRepository = offlineRepository,
            playbackOutboxRepository = outbox,
            offlineModeManager = offlineModeManager,
            mediaRepository = lazy { mediaRepository },
            downloadsStore = lazy { downloadsStore },
            downloadRepository = lazy { downloadRepository },
            timeSource = fakeTimeSource,
        )
        return TestListenableWorkerBuilder<PlaybackSyncWorker>(context)
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
    }


    private fun offlineRow(
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

    private fun serverRow(
        isPlayed: Boolean,
        positionTicks: Long? = null,
    ) = MediaDetail(
        item = MediaItem(
            id = ITEM_ID,
            name = "Episode",
            mediaType = MediaType.EPISODE,
            isPlayed = isPlayed,
            playbackPositionTicks = positionTicks,
            runTimeTicks = 60_000_000L,
        ),
    )

    // ── 1. Watched-offline drain: one flip, zero telemetry (#153) ─────

    @Test
    fun `watched-offline drain delivers only the played flip and suppresses trailing telemetry`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.START),
            entry("e2", ITEM_ID, PlaybackOutboxEventType.PROGRESS, positionTicks = 55_800_000L),
            entry("e3", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
            entry("e4", ITEM_ID, PlaybackOutboxEventType.PLAYED),
        )
        coEvery { outbox.hasUnsyncedPlayedIntent(ITEM_ID) } returns true
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        // The flip lands through the REAL replay mapping...
        coVerify(exactly = 1) { apiClient.markPlayed(ITEM_ID) }
        // ...and NOT ONE position report reaches the server — a trailing STOP
        // replayed after markPlayedItem is what left the server with a
        // near-end position and a resumable watched episode (#153).
        coVerify(exactly = 0) { apiClient.reportPlaybackStart(any(), any(), any()) }
        coVerify(exactly = 0) { apiClient.reportPlaybackProgress(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiClient.reportPlaybackStopped(any(), any(), any()) }
        coVerify(exactly = 4) { outbox.delete(any()) }
    }

    // ── 2. Poisoned server row heals during the drain (#157) ──────────

    @Test
    fun `drained item whose server row is played with a stale position is healed by re-asserting markPlayed`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.PROGRESS, positionTicks = 30_000_000L),
        )
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineRow()
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, any()) } returns
            Result.success(serverRow(isPlayed = true, positionTicks = 5_000_000L))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        // The heal pushes through the intent row + delivery probe.
        coEvery { outbox.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns true

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        // The heal: the server's resume point is zeroed so /Items/Resume
        // stops listing the watched episode.
        coVerify(exactly = 1) { apiClient.markPlayed(ITEM_ID) }
        coVerify(exactly = 1) { offlineRepository.updatePlaybackProgress(ITEM_ID, 0L, 100.0, true) }
        // The drain changed server state → home/detail caches must drop now,
        // not on the next TTL tick.
        coVerify(exactly = 1) { cacheInvalidator.invalidateCaches() }
        coVerify(exactly = 1) { mediaRepository.notifyUserDataChanged(listOf(ITEM_ID)) }
        coVerify(exactly = 1) { userDataSyncScheduler.enqueueNow() }
    }

    // ── 3. Heal failure: local reset survives, intent re-staged ───────

    @Test
    fun `a failing heal still resets the local row and stages the intent for the next drain`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.PROGRESS, positionTicks = 30_000_000L),
        )
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineRow()
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, any()) } returns
            Result.success(serverRow(isPlayed = true, positionTicks = 5_000_000L))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.failure(java.io.IOException("5xx"))

        val result = buildWorker().doWork()

        // The re-staged PLAYED row is undelivered — the drain must retry so
        // the intent is not stranded until the 4h backstop (#153 policy).
        assertTrue(result is androidx.work.ListenableWorker.Result.Retry)
        // The failed heal re-stages the intent; the local reset is deferred
        // until the flip lands (no partial application).
        coVerify(exactly = 1) { outbox.enqueuePlayedState(ITEM_ID, isPlayed = true) }
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    // ── 4. Offline unwatch wins over the local played mirror ──────────

    @Test
    fun `an offline UNPLAYED intent replays and never derives a watched flip`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.UNPLAYED),
        )
        // The sticky mirror still reads played (only applyPlayedStateToHierarchy
        // can clear it) — the UNPLAYED intent is the authority regardless.
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineRow(isPlayed = true, playedPercentage = 97.0)
        coEvery { outbox.hasUnsyncedUnplayedIntent(ITEM_ID) } returns true

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { apiClient.markUnplayed(ITEM_ID) }
        coVerify(exactly = 0) { mediaRepository.markPlayed(any()) }
        coVerify(exactly = 0) { apiClient.markPlayed(any()) }
    }

    // ── 5. Lost intent row: the sticky mirror recovers the flip ───────

    @Test
    fun `a watched-offline session whose PLAYED row was lost is recovered from the mirror row`() = runTest {
        coEvery { outbox.drain() } returns listOf(
            entry("e1", ITEM_ID, PlaybackOutboxEventType.STOP, positionTicks = 58_000_000L),
        )
        // Process death at the threshold: no PLAYED intent was staged, but the
        // mirror row already reads watched (≥ the 95% threshold).
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineRow(isPlayed = false, playedPercentage = 97.0)
        coEvery { outbox.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns true

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        // Telemetry settles first (the STOP is genuinely replayed — no intent
        // authorizes dropping it), then the derived flip lands LAST so the
        // server's final state is played with a zeroed position.
        coVerify(exactly = 1) { apiClient.reportPlaybackStopped(ITEM_ID, "s1", 58_000_000L) }
        coVerify(exactly = 1) { mediaRepository.markPlayed(ITEM_ID) }
    }

    // ── 6. Reconcile-only backstop heals legacy-poisoned rows ─────────

    @Test
    fun `empty outbox still heals a downloaded item poisoned by an older build`() = runTest {
        // No telemetry, no intent — the #157 state was written by a
        // pre-guard build. The periodic backstop is what cleans it up.
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf(ITEM_ID)
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineRow(isPlayed = true, playedPercentage = 100.0)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, any()) } returns
            Result.success(serverRow(isPlayed = true, positionTicks = 45_000_000L))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        coEvery { outbox.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns true

        val result = buildWorker().doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        coVerify(exactly = 1) { apiClient.markPlayed(ITEM_ID) }
        coVerify(exactly = 1) { offlineRepository.updatePlaybackProgress(ITEM_ID, 0L, 100.0, true) }
        coVerify(exactly = 1) { cacheInvalidator.invalidateCaches() }
    }

    private companion object {
        const val ITEM_ID = "item-1"
    }
}
