package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the online/offline + outbox fan-out contract of [PlayedStateSyncImpl] —
 * the user's intent must NEVER be lost, and a destructive cleanup must never
 * run off an unconfirmed flip:
 *  1. `flip` offline: apply locally + stage in the outbox; online success:
 *     push to the server and mirror, no outbox; online failure: apply locally
 *     + stage in the outbox and still report success (optimistic UI);
 *  2. auto-delete-after-watch only fires on a CONFIRMED played flip, only for
 *     COMPLETED downloads, and only when the pref is on;
 *  3. `toggleFavorite` resolves the target locally (offline / failed push) or
 *     from the server (online success) and mirrors it everywhere;
 *  4. `reconcileOfflineRow`: server-favorite wins, server-watched resets the
 *     local row, local-played-mirrors-unplayed, otherwise latest position
 *     wins; missing rows degrade to NoChange;
 *  5. `parseIsoToEpochMillis` accepts offset-aware and bare-local ISO shapes.
 */
class PlayedStateSyncImplTest {

    private lateinit var apiClient: JellyfinApiClient
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var outboxRepository: PlaybackOutboxRepository
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var mediaRepository: MediaRepository
    private lateinit var downloadsStore: DownloadsStore
    private lateinit var downloadRepository: DownloadRepository
    private val fakeTimeSource = FakeTimeSource()
    private lateinit var sync: PlayedStateSyncImpl

    @BeforeTest
    fun setup() {
        apiClient = mockk()
        // relaxUnitFun: the reconcile/fan-out writes are suspend Unit calls
        // that individual tests assert via coVerify, not via stubbed answers.
        offlineRepository = mockk(relaxUnitFun = true)
        outboxRepository = mockk(relaxed = true)
        offlineModeManager = mockk()
        mediaRepository = mockk()
        downloadsStore = mockk()
        downloadRepository = mockk()
        sync = PlayedStateSyncImpl(
            apiClient = apiClient,
            offlineRepository = offlineRepository,
            playbackOutboxRepository = outboxRepository,
            offlineModeManager = offlineModeManager,
            mediaRepository = lazy { mediaRepository },
            downloadsStore = lazy { downloadsStore },
            downloadRepository = lazy { downloadRepository },
            timeSource = fakeTimeSource,
        )
        // Defaults; individual tests override.
        every { offlineModeManager.isOffline } returns false
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
    }

    // ── flip ─────────────────────────────────────────────────────────────────

    @Test
    fun `offline flip applies locally and stages in the outbox without a server call`() = runTest {
        every { offlineModeManager.isOffline } returns true

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState(ITEM_ID, true) }
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, true) }
        coVerify(exactly = 0) { apiClient.markPlayed(any()) }
        coVerify(exactly = 0) { apiClient.markUnplayed(any()) }
    }

    @Test
    fun `online played flip pushes to the server and mirrors locally without the outbox`() = runTest {
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState(ITEM_ID, true) }
        coVerify(exactly = 0) { outboxRepository.enqueuePlayedState(any(), any()) }
    }

    @Test
    fun `a failed online flip applies locally, enqueues for retry and still reports success`() = runTest {
        coEvery { apiClient.markUnplayed(ITEM_ID) } returns
            Result.failure(java.io.IOException("HTTP 503: Service Unavailable"))

        val result = sync.flip(ITEM_ID, played = false)

        assertTrue(result.isSuccess, "the caller's optimistic flip must run on a transient failure")
        coVerify(exactly = 1) { offlineRepository.applyPlayedState(ITEM_ID, false) }
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, false) }
    }

    @Test
    fun `an offline flip survives a failing offline-store mirror`() = runTest {
        // The mirror write is best-effort in BOTH offline paths: a Room/disk
        // failure must not lose the outbox staging (the drain would then
        // deliver nothing while the UI believes the flip is queued).
        every { offlineModeManager.isOffline } returns true
        coEvery { offlineRepository.applyPlayedState(ITEM_ID, true) } throws RuntimeException("disk full")

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, true) }
    }

    @Test
    fun `an offline flip never crashes on a failing outbox enqueue`() = runTest {
        every { offlineModeManager.isOffline } returns true
        coEvery { outboxRepository.enqueuePlayedState(ITEM_ID, true) } throws RuntimeException("db locked")

        val result = sync.flip(ITEM_ID, played = true)

        // The optimistic-UI contract: the flip call itself must not throw —
        // the mirror row still applied and the UI state is recoverable on the
        // next reconcile even though the intent row was not staged.
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState(ITEM_ID, true) }
    }

    @Test
    fun `auto-delete-after-watch removes a completed download on a confirmed played flip`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDeleteAfterWatch = true))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns completedDownload()

        sync.flip(ITEM_ID, played = true)

        coVerify(exactly = 1) { downloadRepository.deleteDownload(DOWNLOAD_ID) }
    }

    @Test
    fun `auto-delete-after-watch never fires when the pref is off`() = runTest {
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)

        sync.flip(ITEM_ID, played = true)

        coVerify(exactly = 0) { downloadRepository.getDownloadByMediaItemId(any()) }
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun `auto-delete-after-watch never fires on an unconfirmed flip`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDeleteAfterWatch = true))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns
            Result.failure(java.io.IOException("HTTP 500: Internal Server Error"))
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns completedDownload()

        sync.flip(ITEM_ID, played = true)

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun `auto-delete-after-watch never destroys an in-flight download`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDeleteAfterWatch = true))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns
            completedDownload().copy(status = DownloadStatus.DOWNLOADING)

        sync.flip(ITEM_ID, played = true)

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun `auto-delete-after-watch also fires on an offline watched flip`() = runTest {
        // Parity with the confirmed online flip: cleanup is local-only, so an
        // offline watched flip can reclaim the disk immediately — nothing to
        // sync (the PLAYED intent row itself is unaffected).
        every { offlineModeManager.isOffline } returns true
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDeleteAfterWatch = true))
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns completedDownload()

        val result = sync.flip(ITEM_ID, played = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { downloadRepository.deleteDownload(DOWNLOAD_ID) }
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, true) }
    }

    @Test
    fun `auto-delete-after-watch never fires on an offline unwatched flip`() = runTest {
        every { offlineModeManager.isOffline } returns true
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDeleteAfterWatch = true))

        sync.flip(ITEM_ID, played = false)

        coVerify(exactly = 0) { downloadRepository.getDownloadByMediaItemId(any()) }
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    // ── toggleFavorite ───────────────────────────────────────────────────────

    @Test
    fun `offline favorite toggle resolves the target from the local row and enqueues`() = runTest {
        every { offlineModeManager.isOffline } returns true
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isFavorite = false)

        val result = sync.toggleFavorite(ITEM_ID)

        assertTrue(result.getOrThrow(), "local false → target true")
        coVerify(exactly = 1) { offlineRepository.applyFavoriteState(ITEM_ID, true) }
        coVerify(exactly = 1) { outboxRepository.enqueueFavoriteState(ITEM_ID, true) }
        coVerify(exactly = 0) { apiClient.toggleFavorite(any(), any()) }
    }

    @Test
    fun `offline favorite toggle without a local row treats the item as unfavorited`() = runTest {
        every { offlineModeManager.isOffline } returns true
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns null

        assertTrue(sync.toggleFavorite(ITEM_ID).getOrThrow())
        coVerify(exactly = 1) { outboxRepository.enqueueFavoriteState(ITEM_ID, true) }
    }

    @Test
    fun `online favorite toggle mirrors the server-resolved target locally`() = runTest {
        coEvery { apiClient.toggleFavorite(ITEM_ID, currentIsFavorite = null) } returns Result.success(true)

        val result = sync.toggleFavorite(ITEM_ID)

        assertTrue(result.getOrThrow())
        coVerify(exactly = 1) { offlineRepository.applyFavoriteState(ITEM_ID, true) }
        coVerify(exactly = 0) { outboxRepository.enqueueFavoriteState(any(), any()) }
    }

    @Test
    fun `a failed online favorite toggle falls back to local resolution`() = runTest {
        coEvery { apiClient.toggleFavorite(ITEM_ID, currentIsFavorite = null) } returns
            Result.failure(java.io.IOException("HTTP 429: Too Many Requests"))
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isFavorite = true)

        val result = sync.toggleFavorite(ITEM_ID)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow(), "local true → target false")
        coVerify(exactly = 1) { offlineRepository.applyFavoriteState(ITEM_ID, false) }
        coVerify(exactly = 1) { outboxRepository.enqueueFavoriteState(ITEM_ID, false) }
    }

    @Test
    fun `an online favorite toggle survives a failing offline mirror`() = runTest {
        // The server flip is authoritative; the local mirror is best-effort —
        // a Room failure must not surface as a failed toggle.
        coEvery { apiClient.toggleFavorite(ITEM_ID, currentIsFavorite = null) } returns Result.success(true)
        coEvery { offlineRepository.applyFavoriteState(ITEM_ID, true) } throws RuntimeException("disk")

        val result = sync.toggleFavorite(ITEM_ID)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
    }

    @Test
    fun `an offline favorite toggle survives a failing local read`() = runTest {
        // getOfflineItem failing degrades to "unfavorited" (getOrNull ?:
        // false) — the toggle still applies + enqueues the resolved target.
        every { offlineModeManager.isOffline } returns true
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } throws RuntimeException("db")

        val result = sync.toggleFavorite(ITEM_ID)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow(), "read failure degrades to unfavorited → target true")
        coVerify(exactly = 1) { outboxRepository.enqueueFavoriteState(ITEM_ID, true) }
    }

    // ── reconcileOfflineRow ──────────────────────────────────────────────────

    @Test
    fun `reconcile reports NoChange without an offline row`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns null

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, sync.reconcileOfflineRow(ITEM_ID))
    }

    @Test
    fun `reconcile reports NoChange when the server view is unavailable`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem()
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.failure(java.io.IOException("offline"))

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, sync.reconcileOfflineRow(ITEM_ID))
    }

    @Test
    fun `the server favorite state wins over the local row`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isFavorite = false)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isFavorite = true))

        sync.reconcileOfflineRow(ITEM_ID)

        coVerify(exactly = 1) { offlineRepository.applyFavoriteState(ITEM_ID, true) }
    }

    @Test
    fun `a server-watched item resets the local row to played and clean`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(isPlayed = false, playbackPositionTicks = 600_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true))

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.PLAYED), result)
        coVerify(exactly = 1) {
            offlineRepository.updatePlaybackProgress(ITEM_ID, 0L, 100.0, true)
        }
    }

    @Test
    fun `a played server row with a stale resume position is healed by re-asserting markPlayed (#157)`() = runTest {
        // /Items/Resume filters on position > 0 only, so Played=true plus a
        // leftover position keeps the item resumable server-side forever.
        // Reconcile re-asserts markPlayedItem, whose resetPosition zeroes it.
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true, playbackPositionTicks = 5_000_000L))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)
        // The repair pushes through the intent row + delivery probe.
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns true

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.PLAYED), result)
        coVerify(exactly = 1) { apiClient.markPlayed(ITEM_ID) }
        coVerify(exactly = 1) { offlineRepository.updatePlaybackProgress(ITEM_ID, 0L, 100.0, true) }
    }

    @Test
    fun `a failed heal stages the intent and reports undelivered (#157)`() = runTest {
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true, playbackPositionTicks = 5_000_000L))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.failure(RuntimeException("5xx"))

        val result = sync.reconcileOfflineRow(ITEM_ID)

        // The failed heal re-stages a PLAYED row (the drain retries it) and
        // reports UndeliveredIntent — the server row stays poisoned until the
        // flip lands, and the local reset is deferred with it.
        assertEquals(PlayedStateSync.ReconcileOutcome.UndeliveredIntent, result)
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, isPlayed = true) }
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    @Test
    fun `a played server row without a resume position is not re-asserted`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = false)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true, playbackPositionTicks = 0L))

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.PLAYED), result)
        coVerify(exactly = 0) { apiClient.markPlayed(ITEM_ID) }
    }

    @Test
    fun `a locally-played item mirrors a server-unplayed flip`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = false))

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.UNPLAYED), result)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState(ITEM_ID, false) }
    }

    @Test
    fun `an unsynced PLAYED intent pushes instead of mirroring the server unplayed (#153)`() = runTest {
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        // The intent row exists (branch taken) and the push delivers it.
        coEvery { outboxRepository.hasUnsyncedPlayedIntent(ITEM_ID) } returns true
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns true
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = false))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.PLAYED), result)
        coVerify(exactly = 0) { offlineRepository.applyPlayedState(ITEM_ID, false) }
        coVerify(exactly = 1) { apiClient.markPlayed(ITEM_ID) }
        coVerify(exactly = 1) { outboxRepository.deletePlayedStateIntents(ITEM_ID) }
    }

    @Test
    fun `an unsynced UNPLAYED intent pushes instead of adopting the server watched state (#153)`() = runTest {
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = false)
        coEvery { outboxRepository.hasUnsyncedUnplayedIntent(ITEM_ID) } returns true
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = false) } returns true
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true))
        coEvery { apiClient.markUnplayed(ITEM_ID) } returns Result.success(Unit)

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.UNPLAYED), result)
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
        coVerify(exactly = 1) { apiClient.markUnplayed(ITEM_ID) }
        coVerify(exactly = 1) { outboxRepository.deletePlayedStateIntents(ITEM_ID) }
    }

    @Test
    fun `a failed push for an unsynced PLAYED intent re-stages it and reports undelivered (#153)`() = runTest {
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        // The row is deleted before the push, but the failed flip re-enqueues
        // it — so the delivery probe still reports an undelivered intent.
        coEvery { outboxRepository.hasUnsyncedPlayedIntent(ITEM_ID) } returns true
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns false
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = false))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.failure(RuntimeException("down"))

        val result = sync.reconcileOfflineRow(ITEM_ID)

        // flip() still reports success to its caller, but reconcile must not
        // claim a delivery that did not happen — the surviving row carries the
        // intent to the next drain.
        assertEquals(PlayedStateSync.ReconcileOutcome.UndeliveredIntent, result)
        coVerify(exactly = 0) { offlineRepository.applyPlayedState(ITEM_ID, false) }
        coVerify(exactly = 1) { outboxRepository.enqueuePlayedState(ITEM_ID, true) }
    }

    @Test
    fun `a newer server position updates the local resume point`() = runTest {
        val serverPlayedAt = "2024-06-15T10:30:00Z"
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "2024-01-01T00:00:00Z", runTimeTicks = 100_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = serverPlayedAt,
                    playbackPositionTicks = 30_000_000L,
                ),
            )

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.POSITION_UPDATED), result)
        val itemSlot = slot<String>()
        val positionSlot = slot<Long>()
        val percentageSlot = slot<Double>()
        val playedSlot = slot<Boolean>()
        coVerify(exactly = 1) {
            offlineRepository.updatePlaybackProgress(
                capture(itemSlot),
                capture(positionSlot),
                capture(percentageSlot),
                capture(playedSlot),
            )
        }
        assertEquals(ITEM_ID, itemSlot.captured)
        assertEquals(30_000_000L, positionSlot.captured)
        // 30_000_000 / 100_000_000 → 30% (epsilon-compare: the derivation is
        // floating point, so the product can sit an ulp off 30.0).
        assertEquals(30.0, percentageSlot.captured, 0.001)
        assertEquals(false, playedSlot.captured)
    }

    @Test
    fun `reconcile is a NOOP when the server has no played timestamp`() = runTest {
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem()
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = false, lastPlayedDate = null))

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, sync.reconcileOfflineRow(ITEM_ID))
    }

    // ── reconcile ladder on the injected clock ───────────────────────────────
    // The server-vs-local ladder used to read the raw wall clock, so its
    // ordering branches were only testable by luck of the current date. With
    // the [TimeSource] seam the fake clock pins all three arms.

    @Test
    fun `a server stamp in the future of the injected clock is ignored as clock skew`() = runTest {
        // Fake now sits one minute BEFORE the server stamp — the sanity guard
        // must reject it instead of mirroring an impossible "future watch".
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:29:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(lastPlayedDate = "2024-01-01T00:00:00Z")
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 30_000_000L,
                ),
            )

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, sync.reconcileOfflineRow(ITEM_ID))
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    @Test
    fun `a newer server stamp past the injected now updates the resume point`() = runTest {
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:31:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "2024-01-01T00:00:00Z", runTimeTicks = 100_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 30_000_000L,
                ),
            )

        assertEquals(
            PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.POSITION_UPDATED),
            sync.reconcileOfflineRow(ITEM_ID),
        )
    }

    @Test
    fun `a local row newer than the server stamp wins (NoChange) regardless of the wall clock`() = runTest {
        // Same server stamp, but the local row watched LATER — even though the
        // real wall clock (2026+) is newer than both, the ladder must compare
        // the two stamps against each other, not against now.
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:31:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "2024-06-15T12:00:00Z")
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 30_000_000L,
                ),
            )

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, sync.reconcileOfflineRow(ITEM_ID))
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    @Test
    fun `a server stamp equal to the local stamp is NoChange`() = runTest {
        // Equal timestamps: strictly-newer wins means nothing to do — writing
        // the same position back would be a no-op that still trips the
        // worker's anyChanged cache refresh.
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:31:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "2024-06-15T10:30:00Z", runTimeTicks = 100_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 30_000_000L,
                ),
            )

        assertEquals(PlayedStateSync.ReconcileOutcome.NoChange, sync.reconcileOfflineRow(ITEM_ID))
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    @Test
    fun `an unparseable local lastPlayedDate loses to any valid server stamp`() = runTest {
        // Corrupt legacy row: parseIsoToEpochMillis null → offlineMillis = 0,
        // so ANY valid (past) server stamp is newer and repairs the row.
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:31:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "garbage-stamp", runTimeTicks = 100_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 30_000_000L,
                ),
            )

        assertEquals(
            PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.POSITION_UPDATED),
            sync.reconcileOfflineRow(ITEM_ID),
        )
    }

    @Test
    fun `a server row without runtime falls back to the offline row's runtime`() = runTest {
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:31:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "2024-01-01T00:00:00Z", runTimeTicks = 100_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 50_000_000L,
                    runTimeTicks = null,
                ),
            )

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(
            PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.POSITION_UPDATED),
            result,
        )
        coVerify(exactly = 1) { offlineRepository.updatePlaybackProgress(ITEM_ID, 50_000_000L, 50.0, false) }
    }

    @Test
    fun `a server position beyond the runtime clamps the percentage to 100`() = runTest {
        // A server that reports position > runtime (clock skew mid-session,
        // trimmed runtime) must not yield a >100% percentage into the offline
        // row's progress math.
        fakeTimeSource.nowMs = epochMillis("2024-06-15T10:31:00Z")
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(lastPlayedDate = "2024-01-01T00:00:00Z", runTimeTicks = 100_000_000L)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(
                detail(
                    isPlayed = false,
                    lastPlayedDate = "2024-06-15T10:30:00Z",
                    playbackPositionTicks = 150_000_000L,
                ),
            )

        sync.reconcileOfflineRow(ITEM_ID)

        // The raw ticks pass through (they are the server's resume point);
        // only the derived percentage is clamped.
        coVerify(exactly = 1) { offlineRepository.updatePlaybackProgress(ITEM_ID, 150_000_000L, 100.0, false) }
    }

    @Test
    fun `the intent push deletes the stale outbox rows before flipping`() = runTest {
        // Delete-before-push (#153): a dead-lettered row removed only AFTER a
        // failed push would linger as a zombie every later reconcile
        // re-pushes; the order is the contract.
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns offlineItem(isPlayed = true)
        coEvery { outboxRepository.hasUnsyncedPlayedIntent(ITEM_ID) } returns true
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = true) } returns true
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = false))
        coEvery { apiClient.markPlayed(ITEM_ID) } returns Result.success(Unit)

        sync.reconcileOfflineRow(ITEM_ID)

        io.mockk.coVerifyOrder {
            outboxRepository.deletePlayedStateIntents(ITEM_ID)
            apiClient.markPlayed(ITEM_ID)
        }
    }

    @Test
    fun `reconcile adopts the server favorite before an intent push branch`() = runTest {
        // The favorite ladder runs FIRST regardless of which played branch
        // follows — a push for an undelivered unwatch must not skip the
        // favorite adoption for the same row.
        every { offlineModeManager.isOffline } returns false
        coEvery { offlineRepository.getOfflineItem(ITEM_ID) } returns
            offlineItem(isPlayed = false, isFavorite = false)
        coEvery { outboxRepository.hasUnsyncedUnplayedIntent(ITEM_ID) } returns true
        coEvery { outboxRepository.isPlayedStateIntentDelivered(ITEM_ID, played = false) } returns true
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(isPlayed = true, isFavorite = true))
        coEvery { apiClient.markUnplayed(ITEM_ID) } returns Result.success(Unit)

        val result = sync.reconcileOfflineRow(ITEM_ID)

        assertEquals(PlayedStateSync.ReconcileOutcome.Changed(PlayedStateSync.ComputeResult.UNPLAYED), result)
        coVerify(exactly = 1) { offlineRepository.applyFavoriteState(ITEM_ID, true) }
        coVerify(exactly = 1) { apiClient.markUnplayed(ITEM_ID) }
    }

    // ── parseIsoToEpochMillis ────────────────────────────────────────────────

    @Test
    fun `parseIsoToEpochMillis handles offset-aware, bare-local and invalid inputs`() {
        assertNull(PlayedStateSyncImpl.parseIsoToEpochMillis(null))
        assertNull(PlayedStateSyncImpl.parseIsoToEpochMillis("  "))
        assertNull(PlayedStateSyncImpl.parseIsoToEpochMillis("not-a-date"))

        val offsetAware = PlayedStateSyncImpl.parseIsoToEpochMillis("2024-01-15T10:30:00Z")
        assertEquals(
            java.time.OffsetDateTime.parse("2024-01-15T10:30:00Z").toInstant().toEpochMilli(),
            offsetAware,
        )

        val bare = PlayedStateSyncImpl.parseIsoToEpochMillis("2024-01-15T10:30:00")
        assertEquals(
            java.time.LocalDateTime.parse("2024-01-15T10:30:00")
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            bare,
        )
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun offlineItem(
        isPlayed: Boolean = false,
        isFavorite: Boolean = false,
        lastPlayedDate: String? = null,
        runTimeTicks: Long? = null,
        playbackPositionTicks: Long? = null,
    ) = OfflineMediaItem(
        id = ITEM_ID,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        lastPlayedDate = lastPlayedDate,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = playbackPositionTicks,
    )

    private fun detail(
        isPlayed: Boolean = false,
        isFavorite: Boolean = false,
        lastPlayedDate: String? = null,
        playbackPositionTicks: Long? = null,
        runTimeTicks: Long? = null,
    ) = MediaDetail(
        item = MediaItem(
            id = ITEM_ID,
            name = "Movie",
            mediaType = MediaType.MOVIE,
            isPlayed = isPlayed,
            isFavorite = isFavorite,
            lastPlayedDate = lastPlayedDate,
            playbackPositionTicks = playbackPositionTicks,
            runTimeTicks = runTimeTicks,
        ),
    )

    private fun completedDownload() = DownloadItem(
        id = DOWNLOAD_ID,
        mediaItemId = ITEM_ID,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = "/downloads/item1/video.mkv",
        downloadUrl = "https://server/video",
        totalSizeBytes = 1000L,
        downloadedBytes = 1000L,
        status = DownloadStatus.COMPLETED,
    )

    private companion object {
        const val ITEM_ID = "item-1"
        const val DOWNLOAD_ID = "dl-1"

        fun epochMillis(iso: String): Long =
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }

    /**
     * Controllable [TimeSource] for the reconcile ladder — same shape as the
     * fake in LyricsRepositoryImplTest (core:data deliberately hosts no shared
     * test fakes; see TimeSource's KDoc). The default now sits one minute
     * after the fixture server stamp ("2024-06-15T10:30:00Z") so the ladder
     * tests that don't care about now still pass the skew guard.
     */
    private class FakeTimeSource(var nowMs: Long = epochMillis("2024-06-15T10:31:00Z")) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2024, 6, 15)
    }
}
