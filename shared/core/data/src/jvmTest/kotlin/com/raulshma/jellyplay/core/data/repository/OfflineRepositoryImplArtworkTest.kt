package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.OfflinePeopleRow
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.io.File
import kotlin.io.path.createTempDirectory

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

    /** kotlin.test has no TemporaryFolder rule — same contract, one root dir. */
    private val tempRoot = createTempDirectory("offline-artwork-test")

    private fun newFolder(name: String): File =
        tempRoot.resolve(name).toFile().apply { mkdirs() }

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)

    private lateinit var repository: OfflineRepositoryImpl

    @BeforeTest
    fun setup() {
        // deleteOfflineItem/Series/Season wrap their DAO deletes in a Room
        // withTransaction block; mock the module's own KMP helper
        // (repository/RoomTransactions.kt, the androidMain androidx.room
        // extension's replacement) so the block runs inline.
        mockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        // By default no surviving rows reference anyone; each delete test
        // overrides this when it needs a "still referenced" sibling.
        coEvery { offlineMediaDao.getAllPeopleJson() } returns emptyList()
        repository = OfflineRepositoryImpl(offlineMediaDao, playbackStateDao, syncBaselineDao, downloadDao, database)
    }

    @AfterTest
    fun tearDown() {
        io.mockk.unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        tempRoot.toFile().deleteRecursively()
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

    private fun movieEntity(
        id: String = "movie-1",
        backdropPath: String? = null,
        posterPath: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Movie",
        mediaType = MediaType.MOVIE.name,
        backdropPath = backdropPath,
        posterPath = posterPath,
    )

    private fun movieDownloadEntity(
        mediaItemId: String,
        dir: File,
    ) = DownloadEntity(
        id = "dl-$mediaItemId",
        mediaItemId = mediaItemId,
        name = "Movie Download",
        mediaType = "MOVIE",
        downloadPath = File(dir, "$mediaItemId.mkv").absolutePath,
        downloadUrl = "https://stream",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = "COMPLETED",
    )

    private fun OfflineMediaEntity.withPlayback() = OfflineMediaWithPlayback(
        media = this,
        playbackPositionTicks = null,
        playedPercentage = null,
        isPlayed = null,
        isFavorite = null,
        lastPlayedDate = null,
    )

    private fun stubDetail(episode: OfflineMediaEntity, download: DownloadEntity?) {
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow(episode.id) } returns flowOf(episode.withPlayback())
        coEvery { downloadDao.getDownloadByMediaItemIdFlow(episode.id) } returns flowOf(download)
    }

    @Test
    fun `episode with remote backdrop resolves the series local backdrop`() = runTest {
        val dir = newFolder("seriesArtwork")
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
        val dir = newFolder("nullBackdrop")
        coEvery { offlineMediaDao.getById("series-1") } returns
            seriesEntity(backdropPath = File(dir, "seriesArtwork.jpg").absolutePath)
        stubDetail(episodeEntity(), downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals(File(dir, "seriesArtwork.jpg").absolutePath, item.backdropPath)
    }

    @Test
    fun `episode keeps local paths unchanged`() = runTest {
        val dir = newFolder("localArtwork")
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
        val dir = newFolder("episodeDir")
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
        val dir = newFolder("noSeries")
        val episode = episodeEntity(seriesId = null, backdropPath = "https://server/Items/ep-1/Images/Backdrop")
        stubDetail(episode, downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("ep-1").first()!!

        assertEquals("https://server/Items/ep-1/Images/Backdrop", item.backdropPath)
    }

    @Test
    fun `series row with remote artwork resolves files beside a downloaded episode`() = runTest {
        val dir = newFolder("seriesDir")
        val poster = File(dir, DownloadArtifacts.posterFile("series-1"))
        poster.writeText("poster-bytes")
        val backdrop = File(dir, DownloadArtifacts.backdropFile("series-1"))
        backdrop.writeText("backdrop-bytes")
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("series-1") } returns flowOf(
            seriesEntity(
                posterPath = "https://server/Items/series-1/Images/Primary",
                backdropPath = "https://server/Items/series-1/Images/Backdrop",
            ).withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("series-1") } returns flowOf(null)
        coEvery { downloadDao.getDownloadsForSeries("series-1") } returns listOf(downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("series-1").first()!!

        assertEquals(poster.absolutePath, item.posterPath)
        assertEquals(backdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `series row with local artwork is left untouched`() = runTest {
        val dir = newFolder("seriesLocal")
        val localPoster = File(dir, DownloadArtifacts.posterFile("series-1")).absolutePath
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("series-1") } returns flowOf(
            seriesEntity(posterPath = localPoster).withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("series-1") } returns flowOf(null)

        val item = repository.getOfflineDetail("series-1").first()!!

        assertEquals(localPoster, item.posterPath)
        assertNull(item.backdropPath)
    }

    // ── Universal resolver: MOVIE/AUDIO own-artifact resolution ──────────────
    // The resolver previously skipped movies/albums entirely (only EPISODE and
    // SERIES were handled). A movie whose persisted poster is a remote URL (a
    // legacy download, or an image-write-failure fallback at download time) now
    // resolves the local file written beside its media on every read path.

    @Test
    fun `movie with remote poster resolves its own local poster beside the download`() = runTest {
        val dir = newFolder("movieDir")
        val localPoster = File(dir, DownloadArtifacts.posterFile("movie-1"))
        localPoster.writeText("poster-bytes")
        val localBackdrop = File(dir, DownloadArtifacts.backdropFile("movie-1"))
        localBackdrop.writeText("backdrop-bytes")
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("movie-1") } returns flowOf(
            movieEntity(
                posterPath = "https://server/Items/movie-1/Images/Primary",
                backdropPath = "https://server/Items/movie-1/Images/Backdrop",
            ).withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("movie-1") } returns
            flowOf(movieDownloadEntity("movie-1", dir))

        val item = repository.getOfflineDetail("movie-1").first()!!

        assertEquals(localPoster.absolutePath, item.posterPath)
        assertEquals(localBackdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `movie with no local artwork keeps the remote url`() = runTest {
        val dir = newFolder("movieNoArt")
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("movie-1") } returns flowOf(
            movieEntity(posterPath = "https://server/Items/movie-1/Images/Primary").withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("movie-1") } returns
            flowOf(movieDownloadEntity("movie-1", dir))

        val item = repository.getOfflineDetail("movie-1").first()!!

        // No disk file → remote URL preserved so it still loads online.
        assertEquals("https://server/Items/movie-1/Images/Primary", item.posterPath)
    }

    // ── List-path resolution (library grid, episode lists, album tracks) ─────
    // Local-artwork resolution now runs in every read path, not just the detail
    // screen, so legacy/remote-URL rows render offline in grids too.

    @Test
    fun `library grid resolves a movie row with a remote poster to its local file`() = runTest {
        val dir = newFolder("libMovieDir")
        val localPoster = File(dir, DownloadArtifacts.posterFile("movie-1"))
        localPoster.writeText("poster-bytes")
        coEvery { offlineMediaDao.getTopLevelItems() } returns flowOf(
            listOf(
                movieEntity(posterPath = "https://server/Items/movie-1/Images/Primary").withPlayback(),
            ),
        )
        coEvery { downloadDao.getDownloadsByMediaItemIdsFlow(listOf("movie-1")) } returns
            flowOf(listOf(movieDownloadEntity("movie-1", dir)))

        val items = repository.getOfflineLibrary().first()

        assertEquals(localPoster.absolutePath, items.single().posterPath)
    }

    @Test
    fun `episode list resolves a remote poster to the local file beside the download`() = runTest {
        val dir = newFolder("epListDir")
        val localPoster = File(dir, DownloadArtifacts.posterFile("ep-1"))
        localPoster.writeText("poster-bytes")
        coEvery { offlineMediaDao.getEpisodesForSeason("season-1") } returns flowOf(
            listOf(
                episodeEntity(
                    id = "ep-1",
                    posterPath = "https://server/Items/ep-1/Images/Primary",
                ).copy(seasonId = "season-1").withPlayback(),
            ),
        )
        coEvery { downloadDao.getDownloadsByMediaItemIdsFlow(listOf("ep-1")) } returns
            flowOf(listOf(downloadEntity("ep-1", dir)))

        val items = repository.getEpisodesForSeason("season-1").first()

        assertEquals(localPoster.absolutePath, items.single().posterPath)
    }

    // ── Cast/person image resolution (issue #109) ───────────────────────────
    // Cast images are persisted to disk at download time (keyed by personId) so
    // the offline cast row survives Coil memory-cache eviction. The resolver
    // substitutes the local path on read; persons without a disk file keep a
    // null localImagePath and the detail screen falls back to the remote URL.

    private fun castJson(vararg people: OfflinePersonInfo): String =
        encodeCast(people.toList())

    @Test
    fun `movie detail resolves cast local images from beside the download`() = runTest {
        val dir = newFolder("movieCast")
        // Two cast members persisted; only one has a disk file (the other was
        // never downloaded or its write failed).
        val actor1File = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actor1File.writeText("actor1-bytes")
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("movie-1") } returns flowOf(
            movieEntity().copy(
                peopleJson = castJson(
                    OfflinePersonInfo(id = "person-1", name = "Lead"),
                    OfflinePersonInfo(id = "person-2", name = "Director", type = "Director"),
                ),
            ).withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("movie-1") } returns
            flowOf(movieDownloadEntity("movie-1", dir))

        val item = repository.getOfflineDetail("movie-1").first()!!

        assertEquals(actor1File.absolutePath, item.cast[0].localImagePath)
        // No disk file for person-2 → null, detail screen falls back to remote URL.
        assertNull(item.cast[1].localImagePath)
    }

    @Test
    fun `series detail resolves cast local images from beside a downloaded episode`() = runTest {
        val dir = newFolder("seriesCast")
        val actorFile = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actorFile.writeText("actor-bytes")
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("series-1") } returns flowOf(
            seriesEntity().copy(
                peopleJson = castJson(OfflinePersonInfo(id = "person-1", name = "Lead")),
            ).withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("series-1") } returns flowOf(null)
        coEvery { downloadDao.getDownloadsForSeries("series-1") } returns listOf(downloadEntity("ep-1", dir))

        val item = repository.getOfflineDetail("series-1").first()!!

        assertEquals(actorFile.absolutePath, item.cast.single().localImagePath)
    }

    @Test
    fun `cast without disk files keeps null local paths`() = runTest {
        val dir = newFolder("noCastArt")
        coEvery { offlineMediaDao.getByIdWithPlaybackFlow("movie-1") } returns flowOf(
            movieEntity().copy(
                peopleJson = castJson(OfflinePersonInfo(id = "person-1", name = "Lead")),
            ).withPlayback(),
        )
        coEvery { downloadDao.getDownloadByMediaItemIdFlow("movie-1") } returns
            flowOf(movieDownloadEntity("movie-1", dir))

        val item = repository.getOfflineDetail("movie-1").first()!!

        assertNull(item.cast.single().localImagePath)
    }

    // ── Cast-image cleanup on delete (issue #109 follow-up) ─────────────────
    // cleanupCastArtwork is invoked by every delete path. A person's image file
    // (keyed by personId) is shared across items, so deleting one item must only
    // remove the file when no surviving row still references that person —
    // otherwise a sibling item's offline cast row loses its image.

    private fun movieEntityWithCast(
        people: List<OfflinePersonInfo>,
        id: String = "movie-1",
    ): OfflineMediaEntity = movieEntity(id = id).copy(
        peopleJson = castJson(*people.toTypedArray()),
    )

    @Test
    fun `deleteOfflineItem removes an orphaned cast image`() = runTest {
        val dir = newFolder("deleteCast")
        val actorFile = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actorFile.writeText("actor-bytes")
        coEvery { offlineMediaDao.getById("movie-1") } returns movieEntityWithCast(
            people = listOf(OfflinePersonInfo(id = "person-1", name = "Lead")),
        )
        coEvery { downloadDao.getDownloadByMediaItemId("movie-1") } returns
            movieDownloadEntity("movie-1", dir)
        // No surviving rows reference person-1 → file is an orphan.

        repository.deleteOfflineItem("movie-1")

        assertFalse(actorFile.exists(), "orphaned cast image must be deleted")
    }

    @Test
    fun `deleteOfflineItem keeps a cast image still referenced by another row`() = runTest {
        val dir = newFolder("keepCast")
        val actorFile = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actorFile.writeText("actor-bytes")
        coEvery { offlineMediaDao.getById("movie-1") } returns movieEntityWithCast(
            people = listOf(OfflinePersonInfo(id = "person-1", name = "Lead")),
        )
        coEvery { downloadDao.getDownloadByMediaItemId("movie-1") } returns
            movieDownloadEntity("movie-1", dir)
        // A surviving sibling row still references person-1 → keep the shared file.
        coEvery { offlineMediaDao.getAllPeopleJson() } returns listOf(
            OfflinePeopleRow(
                id = "movie-2",
                peopleJson = castJson(OfflinePersonInfo(id = "person-1", name = "Lead")),
            ),
        )

        repository.deleteOfflineItem("movie-1")

        assertTrue(actorFile.exists(), "referenced cast image must be kept")
    }
}
