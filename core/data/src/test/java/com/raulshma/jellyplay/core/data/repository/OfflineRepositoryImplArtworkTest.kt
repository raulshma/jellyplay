package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for the local-artwork resolution in
 * [OfflineRepositoryImpl.getOfflineDetail].
 *
 * The offline screens render `posterPath`/`backdropPath` verbatim, so rows that
 * persist blank or remote URLs (legacy downloads, or episodes that by design
 * store no backdrop of their own) must resolve their local-file fallback at
 * load time: episodes fall back to the series artwork (mirroring the online
 * detail screen's series-backdrop hero), and series rows fall back to the
 * artwork files written beside their downloaded episodes.
 */
class OfflineRepositoryImplArtworkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private lateinit var repository: OfflineRepositoryImpl

    @Before
    fun setup() {
        repository = OfflineRepositoryImpl(offlineMediaDao, downloadDao, database)
    }

    private fun episodeEntity(
        id: String = "ep-1",
        seriesId: String? = "series-1",
        backdropPath: String? = null,
        posterPath: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Episode",
        mediaType = MediaType.EPISODE.name,
        seriesId = seriesId,
        backdropPath = backdropPath,
        posterPath = posterPath,
    )

    private fun seriesEntity(
        id: String = "series-1",
        backdropPath: String? = null,
        posterPath: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Series",
        mediaType = MediaType.SERIES.name,
        backdropPath = backdropPath,
        posterPath = posterPath,
    )

    private fun downloadEntity(
        mediaItemId: String,
        dir: File,
    ) = DownloadEntity(
        id = "dl-$mediaItemId",
        mediaItemId = mediaItemId,
        name = "Download",
        mediaType = "EPISODE",
        downloadPath = File(dir, "$mediaItemId.mkv").absolutePath,
        downloadUrl = "https://stream",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = "COMPLETED",
        seriesId = "series-1",
    )

    private fun stubDetail(episode: OfflineMediaEntity, download: DownloadEntity?) {
        coEvery { offlineMediaDao.getByIdFlow(episode.id) } returns flowOf(episode)
        coEvery { downloadDao.getDownloadByMediaItemIdFlow(episode.id) } returns flowOf(download)
    }

    @Test
    fun `episode with remote backdrop resolves the series local backdrop`() = runTest {
        val dir = tempFolder.newFolder("seriesArtwork")
        val seriesBackdrop = File(dir, DownloadArtifacts.backdropFile("series-1"))
        seriesBackdrop.writeText("backdrop-bytes")
        val episode = episodeEntity(
            backdropPath = "https://server/Items/ep-1/Images/Backdrop",
            posterPath = File(dir, DownloadArtifacts.posterFile("ep-1")).absolutePath,
        )
        coEvery { offlineMediaDao.getById("series-1") } returns seriesEntity(backdropPath = seriesBackdrop.absolutePath)
        stubDetail(episode, downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals(seriesBackdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `episode with null backdrop resolves the series local backdrop`() = runTest {
        val dir = tempFolder.newFolder("nullBackdrop")
        coEvery { offlineMediaDao.getById("series-1") } returns
            seriesEntity(backdropPath = File(dir, "seriesArtwork.jpg").absolutePath)
        stubDetail(episodeEntity(), downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals(File(dir, "seriesArtwork.jpg").absolutePath, item.backdropPath)
    }

    @Test
    fun `episode keeps local paths unchanged`() = runTest {
        val dir = tempFolder.newFolder("localArtwork")
        val localBackdrop = File(dir, DownloadArtifacts.backdropFile("ep-1")).absolutePath
        val localPoster = File(dir, DownloadArtifacts.posterFile("ep-1")).absolutePath
        coEvery { offlineMediaDao.getById("series-1") } returns
            seriesEntity(backdropPath = "https://server/Items/series-1/Images/Backdrop")
        stubDetail(
            episodeEntity(backdropPath = localBackdrop, posterPath = localPoster),
            downloadEntity("ep-1", dir),
        )

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals(localBackdrop, item.backdropPath)
        assertEquals(localPoster, item.posterPath)
    }

    @Test
    fun `episode falls back to artwork beside its own download when series row has none`() = runTest {
        val dir = tempFolder.newFolder("episodeDir")
        val seriesPoster = File(dir, DownloadArtifacts.posterFile("series-1"))
        seriesPoster.writeText("poster-bytes")
        coEvery { offlineMediaDao.getById("series-1") } returns seriesEntity() // no artwork columns
        stubDetail(episodeEntity(posterPath = "https://server/Items/ep-1/Images/Primary"), downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals(seriesPoster.absolutePath, item.posterPath)
        // No backdrop anywhere → the remote value is preserved, not blanked.
        assertNull(item.backdropPath)
    }

    @Test
    fun `episode without series link is left untouched`() = runTest {
        val dir = tempFolder.newFolder("noSeries")
        val episode = episodeEntity(seriesId = null, backdropPath = "https://server/Items/ep-1/Images/Backdrop")
        stubDetail(episode, downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals("https://server/Items/ep-1/Images/Backdrop", item.backdropPath)
    }

    @Test
    fun `series row with remote artwork resolves files beside a downloaded episode`() = runTest {
        val dir = tempFolder.newFolder("seriesDir")
        val poster = File(dir, DownloadArtifacts.posterFile("series-1"))
        poster.writeText("poster-bytes")
        val backdrop = File(dir, DownloadArtifacts.backdropFile("series-1"))
        backdrop.writeText("backdrop-bytes")
        coEvery { offlineMediaDao.getByIdFlow("series-1") } returns flowOf(
            seriesEntity(
                posterPath = "https://server/Items/series-1/Images/Primary",
                backdropPath = "https://server/Items/series-1/Images/Backdrop",
            ),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("series-1") } returns flowOf(null)
        coEvery { downloadDao.getDownloadsForSeries("series-1") } returns listOf(downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("series-1").first()!!

        assertEquals(poster.absolutePath, item.posterPath)
        assertEquals(backdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `series row with local artwork is left untouched`() = runTest {
        val dir = tempFolder.newFolder("seriesLocal")
        val localPoster = File(dir, DownloadArtifacts.posterFile("series-1")).absolutePath
        coEvery { offlineMediaDao.getByIdFlow("series-1") } returns flowOf(
            seriesEntity(posterPath = localPoster),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("series-1") } returns flowOf(null)

        val item = repository.getOfflineDetail("series-1").first()!!

        assertEquals(localPoster, item.posterPath)
        assertNull(item.backdropPath)
    }
}
