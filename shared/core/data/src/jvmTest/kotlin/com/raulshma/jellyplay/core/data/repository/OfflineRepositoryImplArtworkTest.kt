package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.dao.personReferenceLikePattern
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.data.util.SystemTimeSource
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
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
 * Unit tests for [OfflineArtworkResolver] — the disk-backed local-artwork
 * resolution subsystem extracted verbatim from the offline repository's read
 * paths (D4) — exercised DIRECTLY over its two DAO seams and a temp dir.
 *
 * The offline screens render `posterPath`/`backdropPath` verbatim, so rows that
 * persist blank or remote URLs (legacy downloads, or episodes that by design
 * store no backdrop of their own) must resolve their local-file fallback at
 * load time: episodes fall back to the series artwork (mirroring the online
 * detail screen's series-backdrop hero), and series rows fall back to the
 * artwork files written beside their downloaded episodes. Cast images resolve
 * beside the same dirs, keyed by personId.
 *
 * The two repository-level delete pins at the bottom are kept as integration
 * coverage: the delete scopes own the resolver's memo lifecycle through the
 * deletion core's `evictArtworkMemo` hook, and the cast-image cleanup they pin
 * runs behind that choreography.
 */
class OfflineRepositoryImplArtworkTest {

    /** kotlin.test has no TemporaryFolder rule — same contract, one root dir. */
    private val tempRoot = createTempDirectory("offline-artwork-test")

    private fun newFolder(name: String): File =
        tempRoot.resolve(name).toFile().apply { mkdirs() }

    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)

    private lateinit var resolver: OfflineArtworkResolver

    @BeforeTest
    fun setup() {
        resolver = OfflineArtworkResolver(
            offlineMediaDao = offlineMediaDao,
            downloadDao = downloadDao,
        )
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    // ── Direct resolver fixtures ─────────────────────────────────────────────

    private fun episodeItem(
        id: String = "ep-1",
        seriesId: String? = "series-1",
        posterPath: String? = null,
        backdropPath: String? = null,
        dir: File? = null,
        cast: List<OfflinePersonInfo> = emptyList(),
    ) = OfflineMediaItem(
        id = id,
        name = "Episode",
        mediaType = MediaType.EPISODE,
        seriesId = seriesId,
        posterPath = posterPath,
        backdropPath = backdropPath,
        downloadPath = dir?.let { File(it, "$id.mkv").absolutePath },
        downloadStatus = if (dir != null) DownloadStatus.COMPLETED else null,
        cast = cast,
    )

    private fun seriesItem(
        id: String = "series-1",
        posterPath: String? = null,
        backdropPath: String? = null,
        cast: List<OfflinePersonInfo> = emptyList(),
    ) = OfflineMediaItem(
        id = id,
        name = "Series",
        mediaType = MediaType.SERIES,
        posterPath = posterPath,
        backdropPath = backdropPath,
        cast = cast,
    )

    private fun movieItem(
        id: String = "movie-1",
        posterPath: String? = null,
        backdropPath: String? = null,
        dir: File? = null,
        cast: List<OfflinePersonInfo> = emptyList(),
    ) = OfflineMediaItem(
        id = id,
        name = "Movie",
        mediaType = MediaType.MOVIE,
        posterPath = posterPath,
        backdropPath = backdropPath,
        downloadPath = dir?.let { File(it, "$id.mkv").absolutePath },
        downloadStatus = if (dir != null) DownloadStatus.COMPLETED else null,
        cast = cast,
    )

    /** The offline_media row the episode→series ladder resolves via the DAO. */
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

    private fun castJson(vararg people: OfflinePersonInfo): String =
        encodeCast(people.toList())

    private fun stubSeriesRow(
        posterPath: String? = null,
        backdropPath: String? = null,
        id: String = "series-1",
    ) {
        coEvery { offlineMediaDao.getById(id) } returns seriesEntity(
            id = id,
            posterPath = posterPath,
            backdropPath = backdropPath,
        )
    }

    // ── Episode → series fallback ladder (detail paths) ──────────────────────

    @Test
    fun `episode with remote backdrop resolves the series local backdrop`() = runTest {
        val dir = newFolder("seriesArtwork")
        val seriesBackdrop = File(dir, DownloadArtifacts.backdropFile("series-1"))
        seriesBackdrop.writeText("backdrop-bytes")
        stubSeriesRow(backdropPath = seriesBackdrop.absolutePath)

        val item = resolver.resolveItemArtwork(
            episodeItem(
                backdropPath = "https://server/Items/ep-1/Images/Backdrop",
                posterPath = File(dir, DownloadArtifacts.posterFile("ep-1")).absolutePath,
                dir = dir,
            ),
        )

        assertEquals(seriesBackdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `episode with null backdrop resolves the series local backdrop`() = runTest {
        val dir = newFolder("nullBackdrop")
        stubSeriesRow(backdropPath = File(dir, "seriesArtwork.jpg").absolutePath)

        val item = resolver.resolveItemArtwork(episodeItem(dir = dir))

        assertEquals(File(dir, "seriesArtwork.jpg").absolutePath, item.backdropPath)
    }

    @Test
    fun `episode keeps local paths unchanged`() = runTest {
        val dir = newFolder("localArtwork")
        val localBackdrop = File(dir, DownloadArtifacts.backdropFile("ep-1")).absolutePath
        val localPoster = File(dir, DownloadArtifacts.posterFile("ep-1")).absolutePath
        stubSeriesRow(backdropPath = "https://server/Items/series-1/Images/Backdrop")

        val item = resolver.resolveItemArtwork(
            episodeItem(posterPath = localPoster, backdropPath = localBackdrop, dir = dir),
        )

        assertEquals(localBackdrop, item.backdropPath)
        assertEquals(localPoster, item.posterPath)
    }

    @Test
    fun `episode falls back to artwork beside its own download when series row has none`() = runTest {
        val dir = newFolder("episodeDir")
        val seriesPoster = File(dir, DownloadArtifacts.posterFile("series-1"))
        seriesPoster.writeText("poster-bytes")
        stubSeriesRow() // no artwork columns

        val item = resolver.resolveItemArtwork(
            episodeItem(posterPath = "https://server/Items/ep-1/Images/Primary", dir = dir),
        )

        assertEquals(seriesPoster.absolutePath, item.posterPath)
        // No backdrop anywhere → the remote value is preserved, not blanked.
        assertNull(item.backdropPath)
    }

    @Test
    fun `episode without series link is left untouched`() = runTest {
        val dir = newFolder("noSeries")

        val item = resolver.resolveItemArtwork(
            episodeItem(
                seriesId = null,
                backdropPath = "https://server/Items/ep-1/Images/Backdrop",
                dir = dir,
            ),
        )

        assertEquals("https://server/Items/ep-1/Images/Backdrop", item.backdropPath)
    }

    // ── Series → downloaded-episode-dir fallback ladder ──────────────────────

    @Test
    fun `series row with remote artwork resolves files beside a downloaded episode`() = runTest {
        val dir = newFolder("seriesDir")
        val poster = File(dir, DownloadArtifacts.posterFile("series-1"))
        poster.writeText("poster-bytes")
        val backdrop = File(dir, DownloadArtifacts.backdropFile("series-1"))
        backdrop.writeText("backdrop-bytes")
        coEvery { downloadDao.getDownloadsForSeries("series-1") } returns listOf(downloadEntity("ep-1", dir))

        val item = resolver.resolveItemArtwork(
            seriesItem(
                posterPath = "https://server/Items/series-1/Images/Primary",
                backdropPath = "https://server/Items/series-1/Images/Backdrop",
            ),
        )

        assertEquals(poster.absolutePath, item.posterPath)
        assertEquals(backdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `series row with local artwork is left untouched`() = runTest {
        val dir = newFolder("seriesLocal")
        val localPoster = File(dir, DownloadArtifacts.posterFile("series-1")).absolutePath

        val item = resolver.resolveItemArtwork(seriesItem(posterPath = localPoster))

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

        val item = resolver.resolveItemArtwork(
            movieItem(
                posterPath = "https://server/Items/movie-1/Images/Primary",
                backdropPath = "https://server/Items/movie-1/Images/Backdrop",
                dir = dir,
            ),
        )

        assertEquals(localPoster.absolutePath, item.posterPath)
        assertEquals(localBackdrop.absolutePath, item.backdropPath)
    }

    @Test
    fun `movie with no local artwork keeps the remote url`() = runTest {
        val dir = newFolder("movieNoArt")

        val item = resolver.resolveItemArtwork(
            movieItem(posterPath = "https://server/Items/movie-1/Images/Primary", dir = dir),
        )

        // No disk file → remote URL preserved so it still loads online.
        assertEquals("https://server/Items/movie-1/Images/Primary", item.posterPath)
    }

    // ── List-path resolution (library grid, episode lists, album tracks) ─────
    // Local-artwork resolution runs in every read path, not just the detail
    // screen, so legacy/remote-URL rows render offline in grids too. The list
    // entry point bulk-prefetches the parent-series context: one getByIds /
    // one getDownloadPathsForSeries per emission, never a per-item round-trip.

    @Test
    fun `library grid resolves a movie row with a remote poster to its local file`() = runTest {
        val dir = newFolder("libMovieDir")
        val localPoster = File(dir, DownloadArtifacts.posterFile("movie-1"))
        localPoster.writeText("poster-bytes")

        val items = resolver.resolveArtworkList(
            listOf(movieItem(posterPath = "https://server/Items/movie-1/Images/Primary", dir = dir)),
        )

        assertEquals(localPoster.absolutePath, items.single().posterPath)
    }

    @Test
    fun `episode list resolves a remote poster to the local file beside the download`() = runTest {
        val dir = newFolder("epListDir")
        val localPoster = File(dir, DownloadArtifacts.posterFile("ep-1"))
        localPoster.writeText("poster-bytes")

        val items = resolver.resolveArtworkList(
            listOf(
                episodeItem(posterPath = "https://server/Items/ep-1/Images/Primary", dir = dir)
                    .copy(seasonId = "season-1"),
            ),
        )

        assertEquals(localPoster.absolutePath, items.single().posterPath)
    }

    @Test
    fun `season of episodes prefetches its parent series row once for the whole list`() = runTest {
        // The list-path fallback ladder: all episodes of a season share one
        // seriesId, so the parent series row must come from ONE getByIds
        // prefetch — not a per-episode getById round-trip per emission.
        val dir = newFolder("seasonPrefetch")
        val seriesBackdrop = File(dir, DownloadArtifacts.backdropFile("series-1"))
        seriesBackdrop.writeText("backdrop-bytes")
        coEvery { offlineMediaDao.getByIds(listOf("series-1")) } returns listOf(
            seriesEntity(backdropPath = seriesBackdrop.absolutePath),
        )

        val items = resolver.resolveArtworkList(
            listOf(
                episodeItem(id = "ep-1", dir = dir).copy(seasonId = "season-1"),
                episodeItem(id = "ep-2", dir = dir).copy(seasonId = "season-1"),
            ),
        )

        assertEquals(seriesBackdrop.absolutePath, items[0].backdropPath)
        assertEquals(seriesBackdrop.absolutePath, items[1].backdropPath)
        coVerify(exactly = 1) { offlineMediaDao.getByIds(listOf("series-1")) }
        coVerify(exactly = 0) { offlineMediaDao.getById(any()) }
    }

    @Test
    fun `episode list without the series fallback resolves own artwork only`() = runTest {
        // The offline home's episodes flow resolves OWN artwork only (the
        // repo's getOfflineEpisodes passes episodeSeriesArtworkFallback=false)
        // so its Continue Watching cards match the online row's backdrop→
        // primary fallback chain — the series substitution must not run.
        val dir = newFolder("ownOnly")
        val localPoster = File(dir, DownloadArtifacts.posterFile("ep-1"))
        localPoster.writeText("poster-bytes")
        // The series artifact exists beside the episode dir — but with the
        // fallback off it must NOT be substituted (issue #147 image parity).
        File(dir, DownloadArtifacts.backdropFile("series-1")).writeText("backdrop-bytes")
        coEvery { offlineMediaDao.getByIds(listOf("series-1")) } returns emptyList()

        val items = resolver.resolveArtworkList(
            listOf(
                episodeItem(
                    posterPath = "https://server/Items/ep-1/Images/Primary",
                    dir = dir,
                ).copy(seasonId = "season-1"),
            ),
            episodeSeriesArtworkFallback = false,
        )

        assertEquals(localPoster.absolutePath, items.single().posterPath)
        assertNull(items.single().backdropPath, "no own backdrop → stays null (no series substitution)")
        coVerify(exactly = 0) { offlineMediaDao.getById(any()) }
    }

    // ── Cast/person image resolution (issue #109) ───────────────────────────
    // Cast images are persisted to disk at download time (keyed by personId) so
    // the offline cast row survives Coil memory-cache eviction. The resolver
    // substitutes the local path on read; persons without a disk file keep a
    // null localImagePath and the detail screen falls back to the remote URL.

    @Test
    fun `movie detail resolves cast local images from beside the download`() = runTest {
        val dir = newFolder("movieCast")
        // Two cast members persisted; only one has a disk file (the other was
        // never downloaded or its write failed).
        val actor1File = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actor1File.writeText("actor1-bytes")
        val cast = listOf(
            OfflinePersonInfo(id = "person-1", name = "Lead"),
            OfflinePersonInfo(id = "person-2", name = "Director", type = "Director"),
        )

        val item = resolver.resolveItemArtwork(movieItem(dir = dir, cast = cast))

        assertEquals(actor1File.absolutePath, item.cast[0].localImagePath)
        // No disk file for person-2 → null, detail screen falls back to remote URL.
        assertNull(item.cast[1].localImagePath)
    }

    @Test
    fun `series detail resolves cast local images from beside a downloaded episode`() = runTest {
        val dir = newFolder("seriesCast")
        val actorFile = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actorFile.writeText("actor-bytes")
        coEvery { downloadDao.getDownloadsForSeries("series-1") } returns listOf(downloadEntity("ep-1", dir))

        val item = resolver.resolveItemArtwork(
            seriesItem(cast = listOf(OfflinePersonInfo(id = "person-1", name = "Lead"))),
        )

        assertEquals(actorFile.absolutePath, item.cast.single().localImagePath)
    }

    @Test
    fun `cast without disk files keeps null local paths`() = runTest {
        val dir = newFolder("noCastArt")

        val item = resolver.resolveItemArtwork(
            movieItem(dir = dir, cast = listOf(OfflinePersonInfo(id = "person-1", name = "Lead"))),
        )

        assertNull(item.cast.single().localImagePath)
    }

    @Test
    fun `memo hit skips re-resolution until evicted`() = runTest {
        // Progress-tick pin: a repeated resolve with unchanged inputs replays
        // the cached result without re-statting the artifacts (the eviction is
        // the delete paths' job, handed over through the repo's hook below).
        val dir = newFolder("memoHit")
        val localPoster = File(dir, DownloadArtifacts.posterFile("movie-1"))
        localPoster.writeText("poster-bytes")
        val item = movieItem(posterPath = "https://server/Items/movie-1/Images/Primary", dir = dir)

        resolver.resolveItemArtwork(item)
        resolver.resolveItemArtwork(item)

        // The resolver reads no DAO at all for a movie's own-artifact pass —
        // the memo assertion lives on the episode ladder instead: one series
        // lookup across two resolutions.
        val epItem = episodeItem(dir = dir)
        stubSeriesRow()
        resolver.resolveItemArtwork(epItem)
        resolver.resolveItemArtwork(epItem)
        coVerify(exactly = 1) { offlineMediaDao.getById("series-1") }

        resolver.evictMemo()
        resolver.resolveItemArtwork(epItem)
        coVerify(exactly = 2) { offlineMediaDao.getById("series-1") }
        assertEquals(localPoster.absolutePath, resolver.resolveItemArtwork(item).posterPath)
    }

    // ── Repository-level delete pins (memo handover + cast cleanup) ──────────
    // These exercise the full repository so the delete scopes' artwork-memo
    // hook plumb (delete → OfflineDeletionCore → resolver.evictMemo) and the
    // cast-image cleanup stay pinned at the integration seam.

    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private lateinit var repository: OfflineRepositoryImpl

    private fun movieEntityWithCast(
        people: List<OfflinePersonInfo>,
        id: String = "movie-1",
    ): OfflineMediaEntity = OfflineMediaEntity(
        id = id,
        name = "Movie",
        mediaType = MediaType.MOVIE.name,
    ).copy(
        peopleJson = castJson(*people.toTypedArray()),
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

    @BeforeTest
    fun setupRepository() {
        // deleteOfflineItem/Series/Season wrap their DAO deletes in a Room
        // withTransaction block; mock the module's own KMP helper
        // (repository/RoomTransactions.kt, the androidMain androidx.room
        // extension's replacement) so the block runs inline.
        mockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        // By default no surviving row references any candidate; each delete test
        // overrides this when it needs a "still referenced" sibling.
        coEvery { offlineMediaDao.isPersonReferenced(any()) } returns false
        repository = OfflineRepositoryImpl(
            offlineMediaDao,
            playbackStateDao,
            syncBaselineDao,
            downloadDao,
            database,
            timeSource = SystemTimeSource(),
        )
    }

    @AfterTest
    fun tearDownRepository() {
        io.mockk.unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `deleteOfflineItem removes an orphaned cast image`() = runTest {
        val dir = newFolder("deleteCast")
        val actorFile = File(dir, DownloadArtifacts.personImageFile("person-1"))
        actorFile.writeText("actor-bytes")
        // Cast ids are captured from the metadata rows of the collected downloads
        // (the deletion core's getByIds lookup).
        coEvery { offlineMediaDao.getByIds(listOf("movie-1")) } returns listOf(
            movieEntityWithCast(people = listOf(OfflinePersonInfo(id = "person-1", name = "Lead"))),
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
        coEvery { offlineMediaDao.getByIds(listOf("movie-1")) } returns listOf(
            movieEntityWithCast(people = listOf(OfflinePersonInfo(id = "person-1", name = "Lead"))),
        )
        coEvery { downloadDao.getDownloadByMediaItemId("movie-1") } returns
            movieDownloadEntity("movie-1", dir)
        // A surviving sibling row still references person-1 → keep the shared file.
        coEvery { offlineMediaDao.isPersonReferenced(personReferenceLikePattern("person-1")) } returns true

        repository.deleteOfflineItem("movie-1")

        assertTrue(actorFile.exists(), "referenced cast image must be kept")
    }
}
