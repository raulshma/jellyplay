package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct pin for the extracted download writer (D6): the happy-path
 * [OfflineDownloadWriterCore.startDownload] creates the PENDING `downloads`
 * row — storage cap consulted, path resolved through the layout contract,
 * episode series/season linkage carried onto the row verbatim (the linkage
 * deleteOfflineSeries depends on), and the row handed back as a
 * [com.raulshma.jellyplay.core.model.DownloadItem]. The write bodies were
 * moved verbatim out of [DownloadRepositoryImpl]; this suite pins them on the
 * new home, over narrow DAO/seam mocks — no repository construction needed.
 */
class OfflineDownloadWriterCoreTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val downloadsStore: DownloadsStore = mockk(relaxed = true)
    private val storagePolicy: StoragePolicy = mockk(relaxed = true)
    private val storageLayout: DownloadStorageLayoutContract = mockk(relaxed = true)
    private val syncComparator: OfflineSyncComparator = mockk(relaxed = true)
    private val downloadEnqueuer: DownloadEnqueueCoordinator = mockk(relaxed = true)
    private val imagePreloader: OfflineImagePreloader = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)

    private fun writer() = OfflineDownloadWriterCore(
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        downloadsStore = downloadsStore,
        storagePolicy = storagePolicy,
        storageLayout = storageLayout,
        syncComparator = syncComparator,
        downloadEnqueuer = downloadEnqueuer,
        imagePreloader = imagePreloader,
        playbackRepository = playbackRepository,
        playbackIdentity = mockk(relaxed = true),
        httpClient = OkHttpClient(),
        json = Json,
        timeSource = com.raulshma.jellyplay.core.model.SystemTimeSource(),
        mediaRepository = MediaRepositoryAccess { mediaRepository },
    )

    private fun stubLayout(filePath: String) {
        coEvery { storagePolicy.enforce(precomputedCurrentBytes = null) } returns 0L
        every {
            storageLayout.resolve(
                mediaType = any(),
                storageLocationPref = any(),
                name = any(),
                idHint = any(),
                container = any(),
            )
        } returns ResolvedDownloadPath(
            baseDir = mockk(relaxed = true),
            fileName = "file.mkv",
            filePath = filePath,
        )
    }

    @Test
    fun `startDownload creates the PENDING row with the episode series linkage`() = runTest {
        coEvery { downloadDao.getDownloadByMediaItemId("ep-1") } returns null
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        stubLayout(filePath = "/downloads/Season 1/Episode_p8x1.mkv")

        val result = writer().startDownload(
            DownloadStartRequest(
                mediaItemId = "ep-1",
                name = "Episode",
                mediaType = "EPISODE",
                mediaSourceId = "src-1",
                downloadUrl = "https://stream/ep-1",
                imageUrl = "https://img/ep-1",
                seriesId = "series-1",
                seasonId = "season-1",
                seriesName = "Series",
                seasonName = "Season 1",
                episodeNumber = 3,
                seasonNumber = 1,
            ),
        )

        val item = result.getOrThrow()
        assertEquals("ep-1", item.mediaItemId)
        assertEquals(DownloadStatus.PENDING, item.status)
        assertEquals("series-1", item.seriesId)
        assertEquals("season-1", item.seasonId)
        assertEquals("/downloads/Season 1/Episode_p8x1.mkv", item.downloadPath)

        val entity = slot<DownloadEntity>()
        coVerify(exactly = 1) { downloadDao.insertDownload(capture(entity)) }
        with(entity.captured) {
            assertEquals("ep-1", mediaItemId)
            assertEquals(DownloadStatus.PENDING.name, status)
            // The linkage fields the deleteOfflineSeries cascade keys on.
            assertEquals("series-1", seriesId)
            assertEquals("season-1", seasonId)
            assertEquals("src-1", mediaSourceId)
            assertEquals(0L, downloadedBytes)
            assertTrue(id.isNotBlank())
        }
        // No worker enqueued here — the delegate enqueues after the artifact
        // bundle, on the returned row.
        verify(exactly = 0) { downloadEnqueuer.enqueue(any()) }
    }

    @Test
    fun `startDownload dedupes to the completed row when its file still exists`() = runTest {
        val tmp = kotlin.io.path.createTempDirectory("writer-core-dedupe")
        try {
            val mediaFile = java.io.File(tmp.toFile(), "Movie.mkv").apply { writeText("media") }
            val existing = DownloadEntity(
                id = "dl-1",
                mediaItemId = "movie-1",
                name = "Movie",
                mediaType = "MOVIE",
                downloadPath = mediaFile.absolutePath,
                downloadUrl = "https://stream/movie-1",
                totalSizeBytes = 10L,
                downloadedBytes = 10L,
                status = DownloadStatus.COMPLETED.name,
            )
            coEvery { downloadDao.getDownloadByMediaItemId("movie-1") } returns existing
            every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())

            val result = writer().startDownload(
                DownloadStartRequest(
                    mediaItemId = "movie-1",
                    name = "Movie",
                    mediaType = "MOVIE",
                    mediaSourceId = "src-1",
                    downloadUrl = "https://stream/movie-1",
                    imageUrl = null,
                ),
            )

            val item = result.getOrThrow()
            assertEquals("dl-1", item.id)
            assertEquals(DownloadStatus.COMPLETED, item.status)
            // Same row returned — no second insert, no re-enqueue, no path
            // resolution (the completed file short-circuits the whole write).
            coVerify(exactly = 0) { downloadDao.insertDownload(any()) }
            coVerify(exactly = 0) { storagePolicy.enforce(any()) }
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun `enqueueDownload hands the row to the platform coordinator`() {
        writer().enqueueDownload("dl-9")
        verify(exactly = 1) { downloadEnqueuer.enqueue("dl-9") }
    }
}
