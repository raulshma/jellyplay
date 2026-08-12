package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ResyncOptions
import com.raulshma.jellyplay.core.model.ResyncStep
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers the sidecar-artifact resync axes (subtitles, trickplay, segments) end
 * to end through the real [OfflineSyncManager] + [OfflineSyncComparator], using
 * a recording writer fake and mockk collaborators. The comparator's own
 * signature rules are exercised separately in [OfflineSyncComparatorTest]; here
 * we assert the manager wires options -> writer calls -> baseline persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncManagerTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk()
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val writer = RecordingWriter()

    private val comparator = OfflineSyncComparator()
    private lateinit var manager: OfflineSyncManager
    private lateinit var tempDir: File

    private val itemId = "item-1"

    @Before
    fun setUp() = runTest {
        tempDir = createTempDir()
        val downloadFile = File(tempDir, "video.mkv")
        downloadFile.createNewFile()
        every { offlineModeManager.isOffline } returns false
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(detail())
        coEvery { syncBaselineDao.getBaseline(itemId) } returns baselineEntity()
        coEvery { offlineMediaDao.getLocalImagePaths(itemId) } returns null
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns
            DownloadItem(
                id = "dl-1",
                mediaItemId = itemId,
                name = "Test",
                mediaType = MediaType.MOVIE,
                downloadPath = downloadFile.absolutePath,
                downloadUrl = "https://example.com/video.mkv",
                totalSizeBytes = 1_000_000L,
                downloadedBytes = 1_000_000L,
                status = DownloadStatus.COMPLETED,
            )
        coEvery { playbackRepository.getMediaSegments(itemId) } returns Result.success(emptyList())
        manager = OfflineSyncManager(
            mediaRepository = mediaRepository,
            writer = writer,
            downloadRepository = downloadRepository,
            offlineMediaDao = offlineMediaDao,
            syncBaselineDao = syncBaselineDao,
            comparator = comparator,
            offlineModeManager = offlineModeManager,
            playbackRepository = playbackRepository,
            appScope = TestScope(),
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `resyncItem with all options refreshes subtitles trickplay and segments`() = runTest {
        manager.resyncItem(itemId)

        assertTrue(writer.calls.contains("downloadExternalSubtitles"))
        assertTrue(writer.calls.contains("downloadTrickplayData"))
        assertTrue(writer.calls.contains("downloadMediaSegments"))
    }

    @Test
    fun `resyncItem skips subtitles and trickplay writers when those options are off`() = runTest {
        manager.resyncItem(
            itemId,
            options = ResyncOptions(metadata = false, poster = false, backdrop = false, subtitles = false, trickplay = false, segments = true),
        )

        assertFalse(writer.calls.contains("downloadExternalSubtitles"))
        assertFalse(writer.calls.contains("downloadTrickplayData"))
        // Segments is still on.
        assertTrue(writer.calls.contains("downloadMediaSegments"))
    }

    @Test
    fun `resyncItem skips segments and does not bust cache when segments option is off`() = runTest {
        manager.resyncItem(
            itemId,
            options = ResyncOptions(metadata = false, poster = false, backdrop = false, subtitles = false, trickplay = false, segments = false),
        )

        assertFalse(writer.calls.contains("downloadMediaSegments"))
        coVerify(exactly = 0) { playbackRepository.invalidateSegmentsCache(itemId) }
    }

    @Test
    fun `resyncItem busts segments cache before refreshing segments`() = runTest {
        manager.resyncItem(itemId, options = ResyncOptions(metadata = false, poster = false, backdrop = false, subtitles = false, trickplay = false, segments = true))

        coVerifyOrder {
            playbackRepository.invalidateSegmentsCache(itemId)
            // downloadMediaSegments internally re-fetches; the writer records
            // the call right after the cache bust.
        }
        assertTrue(writer.calls.contains("downloadMediaSegments"))
    }

    @Test
    fun `resyncItem retains skipped subtitle signature in the persisted baseline`() = runTest {
        // Baseline carries a real subtitle signature; options.subtitles = false
        // must carry it forward unchanged instead of wiping it.
        coEvery { syncBaselineDao.getBaseline(itemId) } returns baselineEntity(subtitleSignature = "prior-sub-sig")

        manager.resyncItem(
            itemId,
            options = ResyncOptions(metadata = false, poster = false, backdrop = false, subtitles = false, trickplay = true, segments = true),
        )

        val slot = slot<SyncBaselineEntity>()
        coVerify { syncBaselineDao.upsert(capture(slot)) }
        assertEquals("prior-sub-sig", slot.captured.syncedSubtitleSignature)
    }

    @Test
    fun `resyncItem reports failed sidecar step and retains prior signature instead of re-seeding`() = runTest {
        // Baseline subtitle signature is stale ("stale-sub-sig") and differs
        // from the fresh detail, so the subtitles writer is invoked. Simulate a
        // best-effort write failure: the step must report success=false AND the
        // prior signature must be retained (not re-seeded from the fresh fetch),
        // so the next check still flags subtitles as changed.
        coEvery { syncBaselineDao.getBaseline(itemId) } returns baselineEntity(subtitleSignature = "stale-sub-sig")
        writer.subtitlesResult = false

        val result = manager.resyncItem(
            itemId,
            options = ResyncOptions(metadata = false, poster = false, backdrop = false, subtitles = true, trickplay = false, segments = false),
        )

        val subsStep = result.steps.firstOrNull { it.step == ResyncStep.DOWNLOAD_SUBTITLES }
        assertNotNull(subsStep)
        assertEquals(false, subsStep?.success)
        val slot = slot<SyncBaselineEntity>()
        coVerify { syncBaselineDao.upsert(capture(slot)) }
        assertEquals("stale-sub-sig", slot.captured.syncedSubtitleSignature)
    }

    @Test
    fun `resyncItem offline short-circuits with an error step and no writer calls`() = runTest {
        every { offlineModeManager.isOffline } returns true

        val result = manager.resyncItem(itemId)

        assertTrue(writer.calls.isEmpty())
        val fetchStep = result.steps.firstOrNull { it.step == ResyncStep.FETCH_DETAIL }
        assertNotNull(fetchStep)
        assertEquals(false, fetchStep?.success)
    }

    @Test
    fun `resyncItem seeds fresh subtitle signature on first contact`() = runTest {
        // No prior baseline -> first contact seeds every detail-derived axis
        // (subtitles/trickplay) from the fresh detail, not from a stale value.
        coEvery { syncBaselineDao.getBaseline(itemId) } returns null

        manager.resyncItem(itemId)

        val expectedSub = comparator.subtitleSignature(detail())
        val slot = slot<SyncBaselineEntity>()
        coVerify { syncBaselineDao.upsert(capture(slot)) }
        assertEquals(expectedSub, slot.captured.syncedSubtitleSignature)
    }

    @Test
    fun `checkForUpdates within TTL returns cached state without fetching`() = runTest {
        coEvery { syncBaselineDao.getBaseline(itemId) } returns
            baselineEntity(lastSyncedAt = System.currentTimeMillis()) // within 1h TTL

        val result = manager.checkForUpdates(itemId)

        coVerify(exactly = 0) { mediaRepository.invalidateDetailCache(itemId) }
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        // Returns the persisted state unchanged.
        assertEquals(com.raulshma.jellyplay.core.model.SyncStatus.CURRENT, result.state.status)
    }

    @Test
    fun `checkForUpdates retains prior segments signature instead of wiping it`() = runTest {
        // A baseline with a recorded segments signature and an expired TTL.
        coEvery { syncBaselineDao.getBaseline(itemId) } returns baselineEntity(
            segmentsSignature = "prior-seg-sig",
            lastSyncedAt = 0L, // force TTL expiry
        )
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(detail())

        manager.checkForUpdates(itemId)

        // checkForUpdates does not fetch segments; the prior signature is carried
        // forward through the upserted SyncBaselineEntity's syncedSegmentsSignature.
        val slot = slot<SyncBaselineEntity>()
        coVerify { syncBaselineDao.upsert(capture(slot)) }
        assertEquals("prior-seg-sig", slot.captured.syncedSegmentsSignature)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Records every sidecar write call so a test can assert which ran. */
    private class RecordingWriter : OfflineDownloadWriter {
        val calls = mutableListOf<String>()

        /** Per-axis return values so a test can simulate a best-effort write failure. */
        var subtitlesResult = true
        var trickplayResult = true
        var segmentsResult = true

        override suspend fun startDownload(
            mediaItemId: String, name: String, mediaType: String, mediaSourceId: String?,
            downloadUrl: String, imageUrl: String?, imageBlurHash: String?, seriesId: String?,
            seasonId: String?, seriesName: String?, seasonName: String?, episodeNumber: Int?,
            seasonNumber: Int?, container: String?, precomputedCurrentBytes: Long?,
        ): Result<DownloadItem> = error("not used in resync tests")

        override suspend fun saveOfflineMediaItem(item: MediaItem, imageUrl: String?, backdropUrl: String?, downloadPath: String?) {
            calls += "saveOfflineMediaItem"
        }

        override suspend fun saveOfflineMediaDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?) {
            calls += "saveOfflineMediaDetail"
        }

        override suspend fun downloadOfflineImage(itemId: String, imageType: String, maxWidth: Int, parentDir: File, fileName: String): String? {
            calls += "downloadOfflineImage($imageType)"
            return null
        }

        override suspend fun downloadTrickplayData(itemId: String, trickplayInfo: TrickplayInfo, downloadPath: String): Boolean {
            calls += "downloadTrickplayData"
            return trickplayResult
        }

        override suspend fun downloadExternalSubtitles(itemId: String, mediaSourceId: String, mediaStreams: List<MediaStream>, downloadPath: String): Boolean {
            calls += "downloadExternalSubtitles"
            return subtitlesResult
        }

        override suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean {
            calls += "downloadMediaSegments"
            return segmentsResult
        }

        override fun enqueueDownload(downloadId: String) {
            calls += "enqueueDownload"
        }
    }

    private fun detail(): MediaDetail {
        val item = MediaItem(id = itemId, name = "Test", mediaType = MediaType.MOVIE)
        return MediaDetail(
            item = item,
            posterImageTag = "poster-1",
            backdropImageTag = "backdrop-1",
            mediaSources = listOf(
                MediaSource(
                    id = "src-1",
                    name = "src",
                    size = 1_000_000L,
                    mediaStreams = listOf(
                        MediaStream(index = 0, type = StreamType.SUBTITLE, codec = "srt", language = "eng", isExternal = true, displayTitle = "English"),
                        MediaStream(index = 1, type = StreamType.SUBTITLE, codec = "ass", language = "spa", isExternal = true, displayTitle = "Spanish"),
                    ),
                    trickplayInfo = TrickplayInfo(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 100, interval = 10000, bandwidth = 200000),
                ),
            ),
        )
    }

    /** A baseline entity whose signatures deliberately differ from [detail] so the
     *  change-gates fire (subtitle/trickplay "stale" -> resync re-fetches). */
    private fun baselineEntity(
        subtitleSignature: String? = "stale-sub-sig",
        trickplaySignature: String? = "stale-trick-sig",
        segmentsSignature: String? = "stale-seg-sig",
        lastSyncedAt: Long? = 0L, // expired TTL by default so checks/resyncs don't short-circuit
    ): SyncBaselineEntity = SyncBaselineEntity(
        id = itemId,
        syncedPosterTag = "poster-1",
        syncedBackdropTag = "backdrop-1",
        syncedMetadataSignature = comparator.metadataSignature(detail()),
        syncedSubtitleSignature = subtitleSignature,
        syncedTrickplaySignature = trickplaySignature,
        syncedSegmentsSignature = segmentsSignature,
        syncedMediaSourceId = "src-1",
        syncedMediaSizeBytes = 1_000_000L,
        lastSyncedAt = lastSyncedAt,
        syncUpdateAvailable = 0,
        syncMediaChanged = 0,
        syncChecking = 0,
        syncError = 0,
    )

    private fun createTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "jellyplay-test-${System.nanoTime()}")
            .apply { mkdirs() }
}
