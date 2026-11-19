package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.SystemTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The [DownloadQueue] placement composites — [DownloadRepositoryImpl.moveToFront] /
 * [DownloadRepositoryImpl.moveToBack], the downloads screen's move-to-front /
 * lower-priority row actions. The placement number is computed INSIDE the
 * repository over the authoritative DAO snapshot at call time (before this
 * moved behind the seam, the ViewModel computed it over its possibly-stale
 * uiState list copy), then persisted through the same `updatePriority` write
 * [DownloadRepositoryImpl.setDownloadPriority] runs. Higher priority = closer
 * to the front (the DAO orders PENDING rows `priority DESC`); the empty-queue
 * baseline is 0 (front → 1, back → −1).
 *
 * Robolectric lane note mirrors DownloadRepositoryImplResumeTest: the shared
 * Log facade's Android actual delegates to android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadRepositoryImplPlacementTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val detailFeed: DetailFeed = mockk(relaxed = true)
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)
    private val downloadDelegate: DownloadDelegate = mockk(relaxed = true)
    private val storagePolicy: StoragePolicy = mockk(relaxed = true)
    private val downloadEnqueuer: DownloadEnqueueCoordinator = mockk(relaxed = true)
    private val episodeCatalogue: EpisodeCatalogue = mockk(relaxed = true)
    private val writer: OfflineDownloadWriterCore = mockk(relaxed = true)

    private fun repository() = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        mediaRepository = MediaRepositoryAccess { detailFeed },
        episodeCatalogue = episodeCatalogue,
        imageUrlProvider = imageUrlProvider,
        downloadsStore = preferencesStore,
        storagePolicy = storagePolicy,
        downloadEnqueuer = downloadEnqueuer,
        timeSource = SystemTimeSource(),
        writer = writer,
        downloadDelegate = downloadDelegate,
    )

    @Test
    fun `moveToFront persists max priority plus one from the DAO snapshot`() = runTest {
        coEvery { downloadDao.getAllDownloadsSnapshot() } returns listOf(
            entity("a", priority = 3),
            entity("b", priority = 7),
            entity("c", priority = 5),
        )

        // Typed as the seam: the placement verbs are DownloadQueue members.
        val queue: DownloadQueue = repository()
        val result = queue.moveToFront("a")

        coVerify(exactly = 1) { downloadDao.updatePriority("a", 8) }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `moveToFront on an empty queue uses the zero baseline plus one`() = runTest {
        coEvery { downloadDao.getAllDownloadsSnapshot() } returns emptyList()

        repository().moveToFront("solo")

        coVerify(exactly = 1) { downloadDao.updatePriority("solo", 1) }
    }

    @Test
    fun `moveToBack persists min priority minus one from the DAO snapshot`() = runTest {
        coEvery { downloadDao.getAllDownloadsSnapshot() } returns listOf(
            entity("a", priority = 3),
            entity("b", priority = 7),
            entity("c", priority = 5),
        )

        repository().moveToBack("c")

        coVerify(exactly = 1) { downloadDao.updatePriority("c", 2) }
    }

    @Test
    fun `moveToBack on an empty queue uses the zero baseline minus one`() = runTest {
        coEvery { downloadDao.getAllDownloadsSnapshot() } returns emptyList()

        repository().moveToBack("solo")

        coVerify(exactly = 1) { downloadDao.updatePriority("solo", -1) }
    }

    @Test
    fun `placement recomputes against the queue state at call time`() = runTest {
        // The read happens per call: a row that joined the queue between two
        // placements (the stale-snapshot case the ViewModel arithmetic lost
        // to) is seen by the second call.
        val snapshot = mutableListOf(entity("old", priority = 3))
        coEvery { downloadDao.getAllDownloadsSnapshot() } answers { snapshot.toList() }

        val queue: DownloadQueue = repository()
        queue.moveToFront("old")
        snapshot += entity("newcomer", priority = 9)
        queue.moveToFront("old")

        // The second placement used the post-arrival max (9 + 1), not the
        // first call's snapshot — each call re-read the DAO state.
        coVerify(exactly = 1) { downloadDao.updatePriority("old", 4) }
        coVerify(exactly = 1) { downloadDao.updatePriority("old", 10) }
    }

    private fun entity(id: String, priority: Int) = DownloadEntity(
        id = id,
        mediaItemId = id,
        name = "Item $id",
        mediaType = "MOVIE",
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = "PENDING",
        priority = priority,
    )
}
