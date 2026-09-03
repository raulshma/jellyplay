package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ResyncCategory
import com.raulshma.jellyplay.core.model.ResyncOptions
import com.raulshma.jellyplay.core.model.ResyncPhase
import com.raulshma.jellyplay.core.model.ResyncStep
import com.raulshma.jellyplay.core.model.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the decision + persistence orchestration of [OfflineSyncManager] (the
 * pure diff rules themselves live in [OfflineSyncComparatorTest]):
 *  1. a missing baseline row reports UNKNOWN without touching the server;
 *  2. a TTL-fresh baseline resolves from persistence with zero fetches, while
 *     `force = true` bypasses the gate;
 *  3. an expired baseline fetches fresh detail (force-read) and re-seeds the
 *     persisted row — CURRENT when nothing changed, UPDATE_AVAILABLE with
 *     per-axis flags when metadata changed;
 *  4. a fetch failure records `syncError` on the row (badge persistence past
 *     the TTL) and reports ERROR without wiping the prior baseline;
 *  5. an offline device resolves from the persisted row and never fetches;
 *  6. first contact (a row with no stored signatures) seeds CURRENT instead of
 *     flagging a spurious update on the very first check;
 *  7. resync: metadata-only resync persists the row, updates the baseline and
 *     clears the update-available flag; a failed subtitle fetch rolls its
 *     signature back to the prior baseline and keeps the pending retry flag;
 *     a MediaSource change is surfaced as mediaFileChanged on the result;
 *  8. batch checks preserve input order.
 */
class OfflineSyncManagerTest {

    private lateinit var mediaRepository: MediaRepository
    private lateinit var writer: com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineMediaDao: OfflineMediaDao
    private lateinit var syncBaselineDao: SyncBaselineDao
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var manager: OfflineSyncManager

    private val comparator = OfflineSyncComparator()

    @BeforeTest
    fun setup() {
        mediaRepository = mockk()
        writer = mockk()
        downloadRepository = mockk()
        offlineMediaDao = mockk()
        syncBaselineDao = mockk()
        offlineModeManager = mockk()
        playbackRepository = mockk()
        // Default; offline-specific tests override via everyIsOffline(true).
        io.mockk.every { offlineModeManager.isOffline } returns false
        coEvery { offlineMediaDao.getLocalImagePaths(any()) } returns null
        // Write-path defaults: the flows under test always mark/clear the
        // checking flag and persist the row — individual tests override the
        // reads (getBaseline) and verify the writes.
        coEvery { syncBaselineDao.setSyncChecking(any(), any()) } returns Unit
        coEvery { syncBaselineDao.clearAllCheckingFlags() } returns Unit
        coEvery { syncBaselineDao.upsert(any()) } returns Unit
        manager = OfflineSyncManager(
            mediaRepository = mediaRepository,
            writer = writer,
            downloadRepository = downloadRepository,
            offlineMediaDao = offlineMediaDao,
            syncBaselineDao = syncBaselineDao,
            comparator = comparator,
            offlineModeManager = offlineModeManager,
            playbackRepository = playbackRepository,
            appScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun detail(
        overview: String = "original plot",
        posterTag: String = "poster-1",
        sourceId: String = "src-1",
        withSubtitle: Boolean = false,
    ) = MediaDetail(
        item = MediaItem(id = ITEM_ID, name = "Movie", mediaType = MediaType.MOVIE, overview = overview),
        posterImageTag = posterTag,
        backdropImageTag = "backdrop-1",
        mediaSources = listOf(
            MediaSource(
                id = sourceId,
                name = "Main",
                size = 1000L,
                mediaStreams = if (withSubtitle) {
                    listOf(subtitleStream())
                } else {
                    emptyList()
                },
            ),
        ),
    )

    private fun subtitleStream() = com.raulshma.jellyplay.core.model.MediaStream(
        index = 1,
        type = com.raulshma.jellyplay.core.model.StreamType.SUBTITLE,
        codec = "srt",
        language = "eng",
        displayTitle = "English (SRT)",
        isExternal = true,
    )

    private fun entityFor(baseline: SyncBaseline, lastSyncedAt: Long?): SyncBaselineEntity =
        SyncBaselineEntity(
            id = ITEM_ID,
            syncedPosterTag = baseline.posterTag,
            syncedBackdropTag = baseline.backdropTag,
            syncedMetadataSignature = baseline.metadataSignature,
            syncedSubtitleSignature = baseline.subtitleSignature.ifEmpty { null },
            syncedTrickplaySignature = baseline.trickplaySignature.ifEmpty { null },
            syncedSegmentsSignature = baseline.segmentsSignature.ifEmpty { null },
            syncedMediaSourceId = baseline.mediaSourceId,
            syncedMediaSizeBytes = baseline.mediaSizeBytes,
            lastSyncedAt = lastSyncedAt,
        )

    private fun downloadRow(path: String = "/downloads/item1/video.mkv") = DownloadItem(
        id = "dl-1",
        mediaItemId = ITEM_ID,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = path,
        downloadUrl = "https://server/video",
        totalSizeBytes = 1000L,
        downloadedBytes = 1000L,
        status = DownloadStatus.COMPLETED,
    )

    private suspend fun capturedUpsert(): SyncBaselineEntity {
        val slot = slot<SyncBaselineEntity>()
        coVerify(exactly = 1) { syncBaselineDao.upsert(capture(slot)) }
        return slot.captured
    }

    // ── checkForUpdates ──────────────────────────────────────────────────────

    @Test
    fun `a missing baseline row reports UNKNOWN without touching the server`() = runTest {
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns null

        val result = manager.checkForUpdates(ITEM_ID)

        assertEquals(SyncStatus.UNKNOWN, result.state.status)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        coVerify(exactly = 0) { syncBaselineDao.setSyncChecking(any(), any()) }
    }

    @Test
    fun `a TTL-fresh baseline resolves from persistence without a fetch`() = runTest {
        val baseline = comparator.baseline(detail())
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns entityFor(baseline, lastSyncedAt = System.currentTimeMillis())

        val result = manager.checkForUpdates(ITEM_ID)

        assertEquals(SyncStatus.CURRENT, result.state.status)
        assertFalse(result.state.needsResync)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `force bypasses the TTL gate and fetches`() = runTest {
        val fresh = detail()
        val baseline = comparator.baseline(fresh)
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns entityFor(baseline, lastSyncedAt = System.currentTimeMillis())
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(fresh)

        manager.checkForUpdates(ITEM_ID, force = true)

        coVerify(exactly = 1) { mediaRepository.getMediaDetail(ITEM_ID, force = true) }
    }

    @Test
    fun `an expired baseline re-seeds the row as CURRENT when nothing changed`() = runTest {
        val fresh = detail()
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns
            entityFor(comparator.baseline(fresh), lastSyncedAt = System.currentTimeMillis() - OfflineSyncManager.SYNC_TTL_MS * 2)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(fresh)

        val result = manager.checkForUpdates(ITEM_ID)

        assertEquals(SyncStatus.CURRENT, result.state.status)
        val persisted = capturedUpsert()
        assertEquals(0, persisted.syncError)
        assertEquals(0, persisted.syncChecking)
        assertNull(persisted.syncedSegmentsSignature, "a check never fetches segments; the empty axis stays empty")
        assertTrue(persisted.lastSyncedAt != null, "the check stamps a fresh lastSyncedAt")
    }

    @Test
    fun `changed server metadata flags UPDATE_AVAILABLE with per-axis flags`() = runTest {
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns
            entityFor(comparator.baseline(detail()), lastSyncedAt = System.currentTimeMillis() - OfflineSyncManager.SYNC_TTL_MS * 2)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.success(detail(overview = "rewritten plot"))

        val result = manager.checkForUpdates(ITEM_ID)

        assertEquals(SyncStatus.UPDATE_AVAILABLE, result.state.status)
        assertTrue(result.state.metadataChanged)
        assertTrue(result.state.needsResync)
        val persisted = capturedUpsert()
        assertEquals(1, persisted.syncUpdateAvailable)
        assertEquals(1, persisted.syncMetadataChanged)
        assertEquals(0, persisted.syncImagesChanged, "unchanged image tags must not flag the images axis")
    }

    @Test
    fun `a fetch failure records the error flag and preserves the prior baseline`() = runTest {
        val baseline = comparator.baseline(detail())
        val row = entityFor(baseline, lastSyncedAt = System.currentTimeMillis() - OfflineSyncManager.SYNC_TTL_MS * 2)
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns row
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.failure(java.io.IOException("server unreachable"))

        val result = manager.checkForUpdates(ITEM_ID)

        assertEquals(SyncStatus.ERROR, result.state.status)
        val persisted = capturedUpsert()
        assertEquals(1, persisted.syncError, "the error must outlive the TTL gate")
        assertEquals(baseline.metadataSignature, persisted.syncedMetadataSignature, "the prior baseline survives")
        assertEquals(0, persisted.syncChecking)
    }

    @Test
    fun `an offline device resolves from the persisted row and never fetches`() = runTest {
        val baseline = comparator.baseline(detail())
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns
            entityFor(baseline, lastSyncedAt = System.currentTimeMillis() - OfflineSyncManager.SYNC_TTL_MS * 2)
        everyIsOffline(true)

        val result = manager.checkForUpdates(ITEM_ID)

        assertEquals(SyncStatus.CURRENT, result.state.status)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        coVerify(exactly = 0) { syncBaselineDao.setSyncChecking(any(), any()) }
    }

    @Test
    fun `first contact seeds the persisted row CURRENT instead of flagging a spurious update`() = runTest {
        // A pre-feature row: exists but carries no stored signatures. The DB
        // badge must not light up on the very first check (the comparator's raw
        // diff flags the null→known image/source transitions, but the
        // first-contact projection clears them before persisting).
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns SyncBaselineEntity(id = ITEM_ID)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(detail())

        manager.checkForUpdates(ITEM_ID)

        val persisted = capturedUpsert()
        assertEquals(0, persisted.syncUpdateAvailable, "opening a dormant download must not prompt a resync badge")
        assertFalse(persisted.syncMetadataChanged == 1)
        assertFalse(persisted.syncImagesChanged == 1)
        assertEquals(0, persisted.syncError)
    }

    @Test
    fun `batch checks preserve input order`() = runTest {
        val fresh = detail()
        val baseline = comparator.baseline(fresh)
        coEvery { syncBaselineDao.getBaselines(listOf("a", "b")) } returns listOf(
            entityFor(baseline, lastSyncedAt = System.currentTimeMillis()).copy(id = "a"),
            entityFor(baseline, lastSyncedAt = System.currentTimeMillis()).copy(id = "b"),
        )
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.success(fresh)

        val results = manager.checkForUpdatesBatch(listOf("a", "b"))

        assertEquals(listOf("a", "b"), results.map { it.itemId })
    }

    @Test
    fun `an empty batch short-circuits`() = runTest {
        assertTrue(manager.checkForUpdatesBatch(emptyList()).isEmpty())
        coVerify(exactly = 0) { syncBaselineDao.getBaselines(any()) }
    }

    // ── resyncItem ───────────────────────────────────────────────────────────

    @Test
    fun `offline resync fails fast on FETCH_DETAIL`() = runTest {
        everyIsOffline(true)

        val result = manager.resyncItem(ITEM_ID)

        assertFalse(result.succeeded)
        assertEquals(1, result.steps.size)
        assertEquals(ResyncStep.FETCH_DETAIL, result.steps.single().step)
        assertEquals("Offline", result.steps.single().message)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `a failed detail fetch produces a failed FETCH_DETAIL step`() = runTest {
        everyIsOffline(false)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns
            Result.failure(java.io.IOException("boom"))

        val result = manager.resyncItem(ITEM_ID)

        assertFalse(result.succeeded)
        assertEquals(ResyncStep.FETCH_DETAIL, result.steps.single().step)
        assertFalse(result.steps.single().success)
    }

    @Test
    fun `a metadata-only resync persists the row and clears the update flag`() = runTest {
        everyIsOffline(false)
        val fresh = detail()
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(fresh)
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns
            entityFor(comparator.baseline(fresh), lastSyncedAt = System.currentTimeMillis())
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns null
        coEvery { writer.saveOfflineMediaDetail(fresh, null, null) } returns Unit

        val result = manager.resyncItem(ITEM_ID, ResyncOptions.of(ResyncCategory.METADATA))

        assertTrue(result.succeeded)
        assertFalse(result.mediaFileChanged)
        assertTrue(result.steps.any { it.step == ResyncStep.PERSIST_METADATA && it.success })
        coVerify(exactly = 1) { writer.saveOfflineMediaDetail(fresh, null, null) }
        val persisted = capturedUpsert()
        assertEquals(0, persisted.syncUpdateAvailable, "a synced category's flag clears")
        assertEquals(0, persisted.syncError)
        assertEquals(fresh.mediaSources.single().id, persisted.syncedMediaSourceId)
    }

    @Test
    fun `a failed subtitle fetch rolls its signature back and keeps the pending flag`() = runTest {
        everyIsOffline(false)
        // Prior baseline: subtitle bundle is pending (never landed) with an
        // empty prior signature; the server now advertises a subtitle.
        val prior = comparator.baseline(detail()).copy(subtitleSignature = "prior-sig", subtitlesPending = true)
        val fresh = detail(withSubtitle = true)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(fresh)
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns entityFor(prior, lastSyncedAt = System.currentTimeMillis())
            .copy(syncSubtitlesPending = 1)
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns downloadRow()
        coEvery { writer.downloadExternalSubtitles(ITEM_ID, any(), any(), any()) } returns false

        val result = manager.resyncItem(ITEM_ID, ResyncOptions.of(ResyncCategory.SUBTITLES))

        assertFalse(result.succeeded, "the subtitle step must report its failure honestly")
        val subtitleStep = result.steps.single { it.step == ResyncStep.DOWNLOAD_SUBTITLES }
        assertFalse(subtitleStep.success)

        val persisted = capturedUpsert()
        assertEquals(1, persisted.syncSubtitlesPending, "a failed fetch must keep the retry flag lit")
        assertEquals(
            "prior-sig",
            persisted.syncedSubtitleSignature,
            "the signature must NOT re-seed from a fetch whose bytes never reached disk",
        )
    }

    @Test
    fun `a successful subtitle fetch re-seeds the signature and clears the pending flag`() = runTest {
        everyIsOffline(false)
        val prior = comparator.baseline(detail()).copy(subtitleSignature = "", subtitlesPending = true)
        val fresh = detail(withSubtitle = true)
        val freshSig = comparator.subtitleSignature(fresh)
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(fresh)
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns entityFor(prior, lastSyncedAt = System.currentTimeMillis())
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns downloadRow()
        coEvery { writer.downloadExternalSubtitles(ITEM_ID, any(), any(), any()) } returns true

        manager.resyncItem(ITEM_ID, ResyncOptions.of(ResyncCategory.SUBTITLES))

        val persisted = capturedUpsert()
        assertEquals(0, persisted.syncSubtitlesPending, "only a successful fetch may clear the flag")
        assertEquals(freshSig, persisted.syncedSubtitleSignature)
    }

    @Test
    fun `a media source change is surfaced as mediaFileChanged`() = runTest {
        everyIsOffline(false)
        val fresh = detail(sourceId = "src-CHANGED")
        coEvery { mediaRepository.getMediaDetail(ITEM_ID, force = true) } returns Result.success(fresh)
        coEvery { syncBaselineDao.getBaseline(ITEM_ID) } returns
            entityFor(comparator.baseline(detail(sourceId = "src-1")), lastSyncedAt = System.currentTimeMillis())
        coEvery { downloadRepository.getDownloadByMediaItemId(ITEM_ID) } returns null
        coEvery { writer.saveOfflineMediaDetail(fresh, null, null) } returns Unit

        val result = manager.resyncItem(ITEM_ID, ResyncOptions.of(ResyncCategory.METADATA))

        assertTrue(result.mediaFileChanged, "a MediaSource id change needs a full re-download, not a resync")
        val persisted = capturedUpsert()
        assertEquals("src-CHANGED", persisted.syncedMediaSourceId)
    }

    @Test
    fun `resyncBatch drives every item through batchProgress to DONE`() = runTest {
        val fresh = detail()
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.success(fresh)
        coEvery { syncBaselineDao.getBaseline(any()) } returns null
        coEvery { downloadRepository.getDownloadByMediaItemId(any()) } returns null
        coEvery { writer.saveOfflineMediaDetail(any(), any(), any()) } returns Unit
        val batchScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val batchManager = OfflineSyncManager(
            mediaRepository = mediaRepository,
            writer = writer,
            downloadRepository = downloadRepository,
            offlineMediaDao = offlineMediaDao,
            syncBaselineDao = syncBaselineDao,
            comparator = comparator,
            offlineModeManager = offlineModeManager,
            playbackRepository = playbackRepository,
            appScope = batchScope,
        )
        everyIsOffline(false)

        batchManager.resyncBatch(listOf("x1", "x2"))
        withTimeoutOrNull(10_000) {
            batchManager.batchProgress.first { progress ->
                progress.items.values.all { it.phase == ResyncPhase.DONE }
            }
        } ?: throw AssertionError("batch progress never reached DONE for all items: ${batchManager.batchProgress.value}")

        assertEquals(2, batchManager.batchProgress.value.total)
        batchManager.clearBatchProgress()
        assertTrue(batchManager.batchProgress.value.items.isEmpty())
    }

    private fun everyIsOffline(offline: Boolean) {
        io.mockk.every { offlineModeManager.isOffline } returns offline
        io.mockk.every { offlineModeManager.offlineMode } returns MutableStateFlow(
            if (offline) com.raulshma.jellyplay.core.model.OfflineMode.OFFLINE_MANUAL
            else com.raulshma.jellyplay.core.model.OfflineMode.ONLINE,
        )
    }

    private companion object {
        const val ITEM_ID = "item-1"
    }
}
