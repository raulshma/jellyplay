package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.DownloadProgressRow
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.PersonInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared offline/download entity ⟷ domain mappers
 * ([OfflineMediaMappers]) — the exact bodies moved out of
 * [DownloadRepositoryImpl] (write side) and [OfflineRepositoryImpl] (read
 * side) — so the column contract documented there can never drift silently:
 * the series-subtitle clearing, the genres/studios CSV join and its
 * trim/filter restoration, the actors-only peopleJson blob with null-when-
 * empty, the playback percentage seed, and the enum degradations
 * (mediaType → UNKNOWN, downloads.status → FAILED).
 *
 * Pure-function pins (the StatisticsMathTest placement precedent): no DAO,
 * no Room — the mappers are total functions over rows and models.
 */
class OfflineMediaMappersTest {

    private val actor = PersonInfo(
        id = "person-1",
        name = "Actor One",
        role = "Hero",
        type = "Actor",
        primaryImageTag = "tag-actor",
        primaryBlurHash = "blur-actor",
    )
    private val director = PersonInfo(
        id = "person-2",
        name = "Director Two",
        type = "Director",
    )

    private fun episodeItem() = MediaItem(
        id = "episode-1",
        name = "Pilot",
        originalTitle = "Pilot (Original)",
        overview = "First episode",
        mediaType = MediaType.EPISODE,
        year = 2024,
        communityRating = 8.1f,
        officialRating = "TV-14",
        runTimeTicks = 1_000L,
        playbackPositionTicks = 250L,
        isPlayed = false,
        isFavorite = true,
        premiereDate = "2024-01-01",
        genres = listOf("Sci-Fi", "Drama"),
        studios = listOf("Studio One", "Studio Two"),
        parentId = "season-1",
        seriesId = "series-1",
        seasonId = "season-1",
        seriesName = "Test Series",
        seasonName = "Season 1",
        episodeNumber = 1,
        seasonNumber = 1,
        indexNumber = 1,
        posterAspectRatio = 2f / 3f,
        backdropAspectRatio = 16f / 9f,
    )

    private fun episodeDetail() = MediaDetail(
        item = episodeItem(),
        criticRating = 7.5f,
        taglines = listOf("A tagline", "Ignored second tagline"),
        people = listOf(actor, director),
        providerIds = mapOf("Tmdb" to "12345"),
        externalUrls = listOf(ExternalUrl(name = "IMDb", url = "https://imdb.com/x")),
        chapters = listOf(ChapterInfo(name = "Opening", startPositionTicks = 0L)),
    )

    /** The LEFT JOIN projection shape the DAO returns, seeded from the playback row. */
    private fun OfflineMediaEntity.withPlaybackFrom(item: MediaItem): OfflineMediaWithPlayback {
        val ps = item.toPlaybackState()
        return OfflineMediaWithPlayback(
            media = this,
            playbackPositionTicks = ps.playbackPositionTicks,
            playedPercentage = ps.playedPercentage,
            isPlayed = ps.isPlayed,
            isFavorite = ps.isFavorite,
            lastPlayedDate = ps.lastPlayedDate,
        )
    }

    // ── write side: persisted form ──────────────────────────────────────

    @Test
    fun `detail write persists the documented column form`() {
        val entity = episodeDetail().toOfflineMediaEntity(
            imageUrl = "https://server/poster",
            backdropUrl = "https://server/backdrop",
        )

        // Identity + base mirror.
        assertEquals("episode-1", entity.id)
        assertEquals("EPISODE", entity.mediaType)
        // Series-subtitle rule: an episode KEEPS both subtitles.
        assertEquals("Test Series", entity.seriesName)
        assertEquals("Season 1", entity.seasonName)
        // CSV columns join with a comma — empty never becomes null here.
        assertEquals("Sci-Fi,Drama", entity.genres)
        assertEquals("Studio One,Studio Two", entity.studios)
        // Rich columns.
        assertEquals("Pilot (Original)", entity.originalTitle)
        assertEquals(7.5f, entity.criticRating)
        assertEquals("A tagline", entity.tagline)
        // peopleJson carries actors only; the blob is decodable back to the actor.
        assertEquals(listOf(actor.toOfflinePerson()), decodeCast(entity.peopleJson))
        assertEquals(
            listOf(ChapterInfo(name = "Opening", startPositionTicks = 0L)),
            decodeChapters(entity.chaptersJson),
        )
        assertEquals(mapOf("Tmdb" to "12345"), decodeProviderIds(entity.providerIdsJson))
        assertEquals(listOf(ExternalUrl("IMDb", "https://imdb.com/x")), decodeExternalUrls(entity.externalUrlsJson))
    }

    @Test
    fun `movie series subtitle is cleared on write`() {
        val movie = episodeItem().copy(
            id = "movie-1",
            mediaType = MediaType.MOVIE,
            // A server movie whose item still carries the (meaningless) series subtitle.
            seriesName = "Should Not Persist",
            seasonName = "Neither Should This",
        )
        val entity = movie.toOfflineMediaEntity(imageUrl = null, backdropUrl = null)

        assertNull(entity.seriesName)
        assertNull(entity.seasonName)
    }

    @Test
    fun `season keeps series subtitle but clears season subtitle`() {
        val season = episodeItem().copy(mediaType = MediaType.SEASON)
        val entity = season.toOfflineMediaEntity(imageUrl = null, backdropUrl = null)

        assertEquals("Test Series", entity.seriesName)
        assertNull(entity.seasonName)
    }

    @Test
    fun `empty collections persist as empty CSV string and null blobs`() {
        val detail = MediaDetail(
            item = episodeItem().copy(genres = emptyList(), studios = emptyList()),
        ) // every collection empty
        val entity = detail.toOfflineMediaEntity(imageUrl = null, backdropUrl = null)

        assertEquals("", entity.genres)
        assertEquals("", entity.studios)
        assertNull(entity.tagline)
        assertNull(entity.peopleJson)
        assertNull(entity.providerIdsJson)
        assertNull(entity.externalUrlsJson)
        assertNull(entity.chaptersJson)
    }

    // ── playback seed ───────────────────────────────────────────────────

    @Test
    fun `playback state seeds derived percentage and no lastPlayedDate`() {
        // 250 / 1000 ticks = 25%.
        val partial = episodeItem().toPlaybackState()
        assertEquals(250L, partial.playbackPositionTicks)
        assertEquals(25.0, partial.playedPercentage)
        assertEquals(false, partial.isPlayed)
        assertEquals(true, partial.isFavorite)
        assertNull(partial.lastPlayedDate)

        // isPlayed short-circuits to 100 regardless of the raw position.
        val played = episodeItem().copy(isPlayed = true, playbackPositionTicks = 10L).toPlaybackState()
        assertEquals(100.0, played.playedPercentage)

        // No position / no runtime → 0, never a divide-by-zero.
        val bare = episodeItem().copy(playbackPositionTicks = null, runTimeTicks = null).toPlaybackState()
        assertEquals(0.0, bare.playedPercentage)
    }

    // ── read side: restoration ──────────────────────────────────────────

    @Test
    fun `episode detail round-trips entity to domain`() {
        val detail = episodeDetail()
        val entity = detail.toOfflineMediaEntity(imageUrl = "p", backdropUrl = "b")

        val restored = entity.withPlaybackFrom(detail.item).toOfflineMediaItem()

        assertEquals(detail.item.id, restored.id)
        assertEquals(MediaType.EPISODE, restored.mediaType)
        assertEquals(detail.item.name, restored.name)
        assertEquals(detail.item.overview, restored.overview)
        assertEquals(detail.item.year, restored.year)
        assertEquals(detail.item.communityRating, restored.communityRating)
        assertEquals(detail.item.officialRating, restored.officialRating)
        assertEquals(detail.item.runTimeTicks, restored.runTimeTicks)
        assertEquals(detail.item.seriesId, restored.seriesId)
        assertEquals(detail.item.seasonId, restored.seasonId)
        assertEquals(detail.item.seriesName, restored.seriesName)
        assertEquals(detail.item.seasonName, restored.seasonName)
        assertEquals(detail.item.episodeNumber, restored.episodeNumber)
        assertEquals(detail.item.seasonNumber, restored.seasonNumber)
        assertEquals("p", restored.posterPath)
        assertEquals("b", restored.backdropPath)
        assertEquals(detail.item.genres, restored.genres)
        assertEquals(detail.item.studios, restored.studios)
        assertEquals(entity.createdAt, restored.createdAt)
        // Rich fields restore through the lenient blob codecs.
        assertEquals(detail.item.originalTitle, restored.originalTitle)
        assertEquals(detail.criticRating, restored.criticRating)
        assertEquals("A tagline", restored.tagline)
        assertEquals(listOf(actor.toOfflinePerson()), restored.cast)
        assertEquals(detail.providerIds, restored.providerIds)
        assertEquals(detail.externalUrls, restored.externalUrls)
        assertEquals(detail.chapters, restored.chapters)
        // Playback restores from the seeded row.
        assertEquals(250L, restored.playbackPositionTicks)
        assertEquals(25.0, restored.playedPercentage)
        assertEquals(false, restored.isPlayed)
        assertEquals(true, restored.isFavorite)
        assertNull(restored.lastPlayedDate)
    }

    @Test
    fun `csv read trims elements drops empties and treats null as empty`() {
        val base = episodeItem().toOfflineMediaEntity(imageUrl = null, backdropUrl = null)

        val messy = base.copy(genres = " Sci-Fi ,, Drama ,", studios = " A , ,B ")
        val restored = messy.withPlaybackFrom(episodeItem()).toOfflineMediaItem()

        assertEquals(listOf("Sci-Fi", "Drama"), restored.genres)
        assertEquals(listOf("A", "B"), restored.studios)

        val nullColumns = base.copy(genres = null, studios = null)
        val emptyRestored = nullColumns.withPlaybackFrom(episodeItem()).toOfflineMediaItem()

        assertTrue(emptyRestored.genres.isEmpty())
        assertTrue(emptyRestored.studios.isEmpty())
    }

    @Test
    fun `unknown persisted media type and missing playback row degrade per contract`() {
        val base = episodeItem().toOfflineMediaEntity(imageUrl = null, backdropUrl = null)

        val garbageType = base.copy(mediaType = "PODCAST", childCount = null)
        val noPlayback = OfflineMediaWithPlayback(
            media = garbageType,
            playbackPositionTicks = null,
            playedPercentage = null,
            isPlayed = null,
            isFavorite = null,
            lastPlayedDate = null,
        )
        val restored = noPlayback.toOfflineMediaItem()

        assertEquals(MediaType.UNKNOWN, restored.mediaType)
        assertEquals(0, restored.childCount)
        assertEquals(0.0, restored.playedPercentage)
        assertEquals(false, restored.isPlayed)
        assertEquals(false, restored.isFavorite)
        assertNull(restored.playbackPositionTicks)
        // A garbage blob column must degrade to empty, never throw.
        assertTrue(
            garbageType.copy(peopleJson = "not json")
                .withPlaybackFrom(episodeItem())
                .toOfflineMediaItem()
                .cast
                .isEmpty(),
        )
    }

    // ── downloads table ─────────────────────────────────────────────────

    @Test
    fun `download row maps with unknown media type and failed status fallbacks`() {
        val row = DownloadEntity(
            id = "dl-1",
            mediaItemId = "episode-1",
            name = "Pilot",
            mediaType = "PODCAST",
            downloadPath = "/data/pilot.mkv",
            downloadUrl = "https://server/v",
            totalSizeBytes = 100L,
            downloadedBytes = 40L,
            status = "GARBAGE",
            speedBytesPerSec = 12L,
            container = "mkv",
        )

        val item = row.toDownloadItem()

        assertEquals(MediaType.UNKNOWN, item.mediaType)
        assertEquals(DownloadStatus.FAILED, item.status)
        assertEquals(40L, item.downloadedBytes)
        assertEquals(12L, item.speedBytesPerSec)
        assertEquals("mkv", item.container)

        val healthy = row.copy(mediaType = "EPISODE", status = "DOWNLOADING").toDownloadItem()
        assertEquals(MediaType.EPISODE, healthy.mediaType)
        assertEquals(DownloadStatus.DOWNLOADING, healthy.status)
    }

    @Test
    fun `progress projection carries only the hot columns`() {
        val progress = DownloadProgressRow(id = "dl-1", downloadedBytes = 40L, speedBytesPerSec = 12L)
            .toDownloadProgress()

        assertEquals("dl-1", progress.id)
        assertEquals(40L, progress.downloadedBytes)
        assertEquals(12L, progress.speedBytesPerSec)
    }

    /** The persisted form of [PersonInfo] as written by the cast blob. */
    private fun PersonInfo.toOfflinePerson() = OfflinePersonInfo(
        id = id,
        name = name,
        role = role,
        type = type,
        imageTag = primaryImageTag,
        blurHash = primaryBlurHash,
    )
}
