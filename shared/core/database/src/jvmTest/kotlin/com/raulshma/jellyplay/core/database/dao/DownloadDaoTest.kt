package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class DownloadDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var downloadDao: DownloadDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        downloadDao = database.downloadDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createDownload(
        id: String = "dl-1",
        mediaItemId: String = "item-1",
        name: String = "Test Movie",
        status: String = "PENDING",
        mediaType: String = "MOVIE",
        seriesId: String? = null,
        seasonId: String? = null,
        downloadedBytes: Long = 0L,
        totalSizeBytes: Long = 1000L,
        createdAt: Long = System.currentTimeMillis(),
    ) = DownloadEntity(
        id = id,
        mediaItemId = mediaItemId,
        name = name,
        mediaType = mediaType,
        downloadPath = "/downloads/test.mkv",
        downloadUrl = "https://test.example.com/Videos/item-1/stream",
        totalSizeBytes = totalSizeBytes,
        downloadedBytes = downloadedBytes,
        status = status,
        seriesId = seriesId,
        seasonId = seasonId,
        createdAt = createdAt,
    )

    @Test
    fun `insertDownload and getDownloadById`() = runTest {
        val download = createDownload()
        downloadDao.insertDownload(download)

        val result = downloadDao.getDownloadById("dl-1")
        assertNotNull(result)
        assertEquals(result!!.name, "Test Movie")
    }

    @Test
    fun `getDownloadById returns null for non-existent`() = runTest {
        assertNull(downloadDao.getDownloadById("nonexistent"))
    }

    @Test
    fun `getDownloadByMediaItemId finds matching download`() = runTest {
        downloadDao.insertDownload(createDownload(mediaItemId = "item-1"))

        val result = downloadDao.getDownloadByMediaItemId("item-1")
        assertNotNull(result)
    }

    @Test
    fun `getAllDownloads returns all downloads`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", mediaItemId = "item-1"))
        downloadDao.insertDownload(createDownload(id = "dl-2", mediaItemId = "item-2"))

        val downloads = downloadDao.getAllDownloads().first()
        assertEquals(2, downloads.size)
    }

    @Test
    fun `getCompletedAudioDownloads pages completed audio newest first`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", mediaItemId = "item-1", status = "COMPLETED", mediaType = "MUSIC", createdAt = 1000L))
        downloadDao.insertDownload(createDownload(id = "dl-2", mediaItemId = "item-2", status = "COMPLETED", mediaType = "AUDIO", createdAt = 2000L))
        downloadDao.insertDownload(createDownload(id = "dl-3", mediaItemId = "item-3", status = "DOWNLOADING", mediaType = "MUSIC", createdAt = 3000L))
        downloadDao.insertDownload(createDownload(id = "dl-4", mediaItemId = "item-4", status = "COMPLETED", mediaType = "MOVIE", createdAt = 4000L))

        assertEquals(listOf("dl-2"), downloadDao.getCompletedAudioDownloads(limit = 1, offset = 0).map { it.id })
        assertEquals(listOf("dl-1"), downloadDao.getCompletedAudioDownloads(limit = 1, offset = 1).map { it.id })
        assertEquals(emptyList(), downloadDao.getCompletedAudioDownloads(limit = 1, offset = 2))
    }

    @Test
    fun `updateProgress updates bytes and status`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", status = "DOWNLOADING", downloadedBytes = 0L))
        downloadDao.updateProgress("dl-1", 500L, "DOWNLOADING")

        val result = downloadDao.getDownloadById("dl-1")
        assertEquals(500L, result!!.downloadedBytes)
        assertEquals(result.status, "DOWNLOADING")
    }

    @Test
    fun `deleteDownloadById removes download`() = runTest {
        downloadDao.insertDownload(createDownload())
        downloadDao.deleteDownloadById("dl-1")

        assertNull(downloadDao.getDownloadById("dl-1"))
    }

    @Test
    fun `getActiveDownloadCount counts pending queued downloading and paused`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", status = "PENDING"))
        downloadDao.insertDownload(createDownload(id = "dl-2", status = "QUEUED"))
        downloadDao.insertDownload(createDownload(id = "dl-3", status = "DOWNLOADING"))
        downloadDao.insertDownload(createDownload(id = "dl-4", status = "PAUSED"))
        downloadDao.insertDownload(createDownload(id = "dl-5", status = "COMPLETED"))

        val count = downloadDao.getActiveDownloadCount().first()
        assertEquals(4, count)
    }

    @Test
    fun `getCompletedDownloadByMediaItemId returns completed only`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", mediaItemId = "item-1", status = "DOWNLOADING"))

        assertNull(downloadDao.getCompletedDownloadByMediaItemId("item-1"))

        downloadDao.updateProgress("dl-1", 1000L, "COMPLETED")

        assertNotNull(downloadDao.getCompletedDownloadByMediaItemId("item-1"))
    }

    @Test
    fun `getTotalDownloadedBytes sums completed downloads`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", status = "COMPLETED", downloadedBytes = 500L))
        downloadDao.insertDownload(createDownload(id = "dl-2", status = "COMPLETED", downloadedBytes = 300L))
        downloadDao.insertDownload(createDownload(id = "dl-3", status = "DOWNLOADING", downloadedBytes = 200L))

        val total = downloadDao.getTotalDownloadedBytes()
        assertEquals(800L, total)
    }

    @Test
    fun `getDownloadsForSeries returns series downloads`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", seriesId = "series-1"))
        downloadDao.insertDownload(createDownload(id = "dl-2", seriesId = "series-2"))
        downloadDao.insertDownload(createDownload(id = "dl-3", seriesId = null))

        val result = downloadDao.getDownloadsForSeries("series-1")
        assertEquals(1, result.size)
        assertEquals(result[0].id, "dl-1")
    }

    @Test
    fun `getDownloadedEpisodeIdsBySeries returns one row per download and excludes null series`() = runTest {
        downloadDao.insertDownload(createDownload(id = "dl-1", mediaItemId = "item-1", seriesId = "series-1"))
        downloadDao.insertDownload(createDownload(id = "dl-2", mediaItemId = "item-2", seriesId = "series-1"))
        downloadDao.insertDownload(createDownload(id = "dl-3", mediaItemId = "item-3", seriesId = "series-2"))
        downloadDao.insertDownload(createDownload(id = "dl-4", mediaItemId = "item-4", seriesId = null))

        val result = downloadDao.getDownloadedEpisodeIdsBySeries()

        // Flat (seriesId, mediaItemId) rows — grouping by series happens in the repository.
        assertEquals(3, result.size)
        val series1Ids = result.filter { it.seriesId == "series-1" }.map { it.mediaItemId }.toSet()
        assertEquals(setOf("item-1", "item-2"), series1Ids)
        val series2Ids = result.filter { it.seriesId == "series-2" }.map { it.mediaItemId }.toSet()
        assertEquals(setOf("item-3"), series2Ids)
        assertTrue(result.none { it.seriesId == null })
    }
}
