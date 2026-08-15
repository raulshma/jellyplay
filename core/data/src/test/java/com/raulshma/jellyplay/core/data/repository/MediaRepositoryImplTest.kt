package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.network.LrcLibApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val lrcLibApi: LrcLibApi = mockk(relaxed = true)
    private val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val playedStateSync: PlayedStateSync = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)

    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setup() {
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        // The repository delegates getSeasons/getEpisodes/getAllEpisodesGrouped
        // to a real EpisodeCatalogueImpl, which in turn calls back into the
        // mocked apiClient — so the existing series/episodes stubs keep working
        // end-to-end through the catalogue transplant.
        val episodeCatalogue = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl(
            apiClient,
            offlineRepository,
        )
        repository = MediaRepositoryImpl(
            apiClient,
            lrcLibApi,
            lyricsCacheDao,
            homeSectionCacheDao,
            networkMonitor,
            playedStateSync,
            episodeCatalogue,
        )
    }

    @Test
    fun `getLyricsWithFallback returns cached synced lyrics`() = runTest {
        val cachedEntity = LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = "[00:05.000]Hello\n[00:10.000]World",
        )
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns cachedEntity

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        val lyrics = result.getOrNull()!!
        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(5000L, lyrics.lines[0].timeMs)
        assertEquals("World", lyrics.lines[1].text)
        assertEquals(10000L, lyrics.lines[1].timeMs)
        assertEquals(LyricsSource.LRCLIB, lyrics.source)
    }

    @Test
    fun `getSpecialFeatures delegates to apiClient and maps items`() = runTest {
        val extras = listOf(
            MediaItem(id = "extra-1", name = "Making Of", mediaType = MediaType.MOVIE),
            MediaItem(id = "extra-2", name = "Deleted Scenes", mediaType = MediaType.MOVIE),
        )
        coEvery { apiClient.getSpecialFeatures("item-1") } returns Result.success(extras)

        val result = repository.getSpecialFeatures("item-1")

        assertTrue(result.isSuccess)
        assertEquals(extras, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSpecialFeatures("item-1") }
    }

    @Test
    fun `getSpecialFeatures empty when apiClient returns empty`() = runTest {
        coEvery { apiClient.getSpecialFeatures("item-1") } returns Result.success(emptyList())

        val result = repository.getSpecialFeatures("item-1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().isNullOrEmpty())
    }

    @Test
    fun `getLyricsWithFallback returns cached plain lyrics when synced is null`() = runTest {
        val cachedEntity = LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = null,
            plainLyrics = "Line 1\nLine 2\n\nLine 3",
        )
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns cachedEntity

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        val lyrics = result.getOrNull()!!
        assertEquals(3, lyrics.lines.size)
        assertEquals("Line 1", lyrics.lines[0].text)
        assertEquals(0L, lyrics.lines[0].timeMs)
        assertEquals("Line 3", lyrics.lines[2].text)
    }

    @Test
    fun `getLyricsWithFallback falls back to Jellyfin API when no cache`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(
                lines = listOf(
                    LyricsLine(timeMs = 1000L, text = "From API"),
                ),
                source = LyricsSource.EMBEDDED,
            )
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals("From API", result.getOrNull()!!.lines[0].text)
        coVerify { lyricsCacheDao.upsert(any()) }
    }

    @Test
    fun `getLyricsWithFallback falls back to LrcLib when Jellyfin returns empty`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )
        coEvery { lrcLibApi.getBestMatch("Artist", "Track", null) } returns Result.success(
            LrcLibTrack(
                id = 1L,
                trackName = "Track",
                artistName = "Artist",
                syncedLyrics = "[00:03.500]LrcLib Line",
            )
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals("LrcLib Line", result.getOrNull()!!.lines[0].text)
        assertEquals(LyricsSource.LRCLIB, result.getOrNull()!!.source)
    }

    @Test
    fun `getLyricsWithFallback returns empty when no artist or track name`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )

        val result = repository.getLyricsWithFallback("item-1", null, null, null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
    }

    @Test
    fun `getLyricsWithFallback handles instrumental track from LrcLib`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )
        coEvery { lrcLibApi.getBestMatch("Artist", "Track", null) } returns Result.success(
            LrcLibTrack(
                id = 1L,
                trackName = "Track",
                artistName = "Artist",
                instrumental = true,
            )
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
        assertEquals(LyricsSource.LRCLIB, result.getOrNull()!!.source)
    }

    @Test
    fun `getLyricsWithFallback skips LrcLib in Local mode`() = runTest {
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Local)
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
        assertEquals(LyricsSource.UNKNOWN, result.getOrNull()!!.source)
        coVerify(exactly = 0) { lrcLibApi.getBestMatch(any(), any(), any()) }
    }

    @Test
    fun `getMediaDetail caches result`() = runTest {
        coEvery { apiClient.getMediaDetail("item-1") } returns Result.success(
            mockk(relaxed = true)
        )

        val first = repository.getMediaDetail("item-1")
        assertTrue(first.isSuccess)

        val second = repository.getMediaDetail("item-1")
        assertTrue(second.isSuccess)

        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    // ------------------------------------------------------------------
    // Single-flight dedup: concurrent callers must share one network fetch.
    // Regression guard for the in-flight Deferred introduced to dedupe
    // parallel getMediaDetail calls (home row tap + deep link + download
    // resume hitting the same item).
    // ------------------------------------------------------------------

    @Test
    fun `getMediaDetail collapses concurrent calls into one fetch`() = runTest {
        // Gate the mock so the first caller is deterministically suspended
        // INSIDE the fetch before the second caller arrives. The previous
        // version signaled via a StateFlow it never awaited, so under runTest's
        // scheduler the first async may not have started before the second
        // launched — the test could pass via the cache rather than via the
        // in-flight Deferred. CompletableDeferred makes the handoff explicit.
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { apiClient.getMediaDetail("item-1") } coAnswers {
            fetchStarted.complete(Unit) // signal: originator is now in-flight
            releaseFetch.await()        // hold until both awaiters are registered
            Result.success(mockk<MediaDetail>(relaxed = true))
        }

        val (a, b) = coroutineScope {
            val a = async { repository.getMediaDetail("item-1") }
            // Deterministically wait until the first fetch is suspended inside
            // the mock before launching the second caller, so the single-flight
            // (in-flight Deferred) path is the one exercised, not a cache hit.
            fetchStarted.await()
            val b = async { repository.getMediaDetail("item-1") }
            // Yield so the second caller advances to the point it has registered
            // on the shared in-flight Deferred before the fetch completes.
            delay(1)
            releaseFetch.complete(Unit)
            Pair(a.await(), b.await())
        }

        assertTrue(a.isSuccess)
        assertTrue(b.isSuccess)
        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `getMediaDetail with force re-fetches instead of serving the cached entry`() = runTest {
        coEvery { apiClient.getMediaDetail("item-1") } returns Result.success(
            mockk(relaxed = true)
        )

        repository.getMediaDetail("item-1")
        repository.getMediaDetail("item-1", force = true)

        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    // ------------------------------------------------------------------
    // detailCacheEpoch stale-snapshot guard: a slow fetch that completes AFTER
    // a concurrent invalidation (user-data mutation) must NOT re-insert its
    // (now stale) snapshot into the cache. Otherwise the next read would serve
    // pre-mutation user-data for the full TTL. This is the highest-risk
    // correctness property of the single-flight cache and is otherwise
    // untested.
    // ------------------------------------------------------------------

    @Test
    fun `getMediaDetail does not cache a fetch that raced a user-data mutation`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { apiClient.getMediaDetail("item-1") } coAnswers {
            fetchStarted.complete(Unit)
            releaseFetch.await() // hold the fetch open across the invalidation
            Result.success(mockk<MediaDetail>(relaxed = true))
        }

        val first = async { repository.getMediaDetail("item-1") }
        fetchStarted.await()
        // A user-data mutation lands while the fetch is in flight: its
        // internal eviction bumps detailCacheEpoch, so the completing fetch
        // must skip the cache write.
        repository.markUnplayed("item-1")
        releaseFetch.complete(Unit)
        assertTrue(first.await().isSuccess)

        // The next read must re-fetch (the racing fetch was not cached).
        coEvery { apiClient.getMediaDetail("item-1") } returns Result.success(
            mockk(relaxed = true)
        )
        repository.getMediaDetail("item-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("item-1") }
    }

    // ------------------------------------------------------------------
    // similarCache invalidation: regression guard for the key-mismatch bug
    // where getSimilarItems stored under "similar_${id}_$limit" but
    // invalidateDetailCache removed "similar_$id" (no suffix) — a no-op.
    // ------------------------------------------------------------------

    @Test
    fun `getSimilarItems is cached per limit`() = runTest {
        coEvery { apiClient.getSimilarItems("item-1", 12) } returns Result.success(
            listOf(mediaItem("s1"), mediaItem("s2"))
        )
        coEvery { apiClient.getSimilarItems("item-1", 9) } returns Result.success(
            listOf(mediaItem("s1"))
        )

        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)

        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 12) }
        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 9) }
    }

    @Test
    fun `markPlayed evicts every similar-items limit variant`() = runTest {
        coEvery { apiClient.getSimilarItems("item-1", any()) } returns Result.success(
            listOf(mediaItem("s1"))
        )
        coEvery { apiClient.markPlayed("item-1") } returns Result.success(Unit)

        // Populate caches for two different limits (detail screen = 12, widget = 9).
        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)
        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 12) }
        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 9) }

        // markPlayed triggers invalidateUserDataCaches -> invalidateDetailCache,
        // which must evict BOTH limit variants (regression: previously only the
        // non-existent "similar_item-1" key was removed, leaving both cached).
        repository.markPlayed("item-1")

        repository.getSimilarItems("item-1", limit = 12)
        repository.getSimilarItems("item-1", limit = 9)

        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 12) }
        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 9) }
    }

    // ------------------------------------------------------------------
    // Series-scoped caches (seasons / episodes). Added so re-entry into a
    // series detail (back from player, tab switch) doesn't re-fire the full
    // episode storm. invalidateSeriesCache must drop the entry so user-data
    // mutations on an episode or the series are reflected on re-entry.
    // ------------------------------------------------------------------

    @Test
    fun `getSeasons caches result per series`() = runTest {
        // getSeasons now delegates to the consolidated catalogue snapshot, which
        // fetches seasons + episodes together — so both primitives are stubbed.
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("s1"), seasonItem("s2"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        repository.getSeasons("series-1")
        repository.getSeasons("series-1")

        coVerify(exactly = 1) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `markUnplayed on a series drops seasons so next call re-fetches`() = runTest {
        // Plan 08 step 2: the seasons/episodes cache is dropped through the
        // mutation itself (was: invalidateSeriesCache, which had no production
        // callers outside this funnel).
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("s1"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())
        coEvery { apiClient.markUnplayed("series-1") } returns Result.success(Unit)

        repository.getMediaDetail("series-1")
        repository.getSeasons("series-1")
        repository.markUnplayed("series-1")
        repository.getSeasons("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    // ------------------------------------------------------------------
    // getAllEpisodesGrouped: single round-trip + groupBy(seasonId), cached.
    // ------------------------------------------------------------------

    @Test
    fun `getAllEpisodesGrouped groups episodes by seasonId and caches`() = runTest {
        // The catalogue snapshot loads seasons + episodes together; stub the
        // seasons primitive so the snapshot completes.
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("season-1"), seasonItem("season-2"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(
                episodeItem("e1", seriesId = "series-1", seasonId = "season-1"),
                episodeItem("e2", seriesId = "series-1", seasonId = "season-1"),
                episodeItem("e3", seriesId = "series-1", seasonId = "season-2"),
            )
        )

        val first = repository.getAllEpisodesGrouped("series-1")
        val second = repository.getAllEpisodesGrouped("series-1")

        assertTrue(first.isSuccess)
        assertEquals(setOf("season-1", "season-2"), first.getOrNull()!!.keys)
        assertEquals(listOf("e1", "e2"), first.getOrNull()!!["season-1"]!!.map { it.id })
        assertEquals(listOf("e3"), first.getOrNull()!!["season-2"]!!.map { it.id })
        // Cached: only one network fetch across two calls.
        coVerify(exactly = 1) { apiClient.getAllEpisodes("series-1") }
        // No fall-back to per-season fetches.
        coVerify(exactly = 0) { apiClient.getEpisodes(any(), any()) }
    }

    @Test
    fun `markUnplayed on an episode drops grouped episodes so next call re-fetches`() = runTest {
        coEvery { apiClient.getMediaDetail("episode-1") } returns Result.success(
            MediaDetail(
                item = episodeItem("episode-1", seriesId = "series-1", seasonId = "season-1"),
            )
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(emptyList())
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        coEvery { apiClient.markUnplayed("episode-1") } returns Result.success(Unit)

        repository.getMediaDetail("episode-1")
        repository.getAllEpisodesGrouped("series-1")
        repository.markUnplayed("episode-1")
        repository.getAllEpisodesGrouped("series-1")

        coVerify(exactly = 2) { apiClient.getAllEpisodes("series-1") }
    }

    // ------------------------------------------------------------------
    // getEpisodes: a per-season call after the series-wide cache is populated
    // must be served from the grouped cache (no extra network hit). This is
    // the optimization that lets DetailViewModel batch-load via the grouped
    // call and then have per-season UI reads hit memory.
    // ------------------------------------------------------------------

    @Test
    fun `getEpisodes serves from grouped cache after getAllEpisodesGrouped`() = runTest {
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("season-1"), seasonItem("season-2"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(
                episodeItem("e1", seriesId = "series-1", seasonId = "season-1"),
                episodeItem("e2", seriesId = "series-1", seasonId = "season-2"),
            )
        )

        repository.getAllEpisodesGrouped("series-1")

        val seasonOne = repository.getEpisodes("series-1", "season-1")

        assertTrue(seasonOne.isSuccess)
        assertEquals(listOf("e1"), seasonOne.getOrNull()!!.map { it.id })
        // No per-season network fetch — served from the grouped snapshot.
        coVerify(exactly = 0) { apiClient.getEpisodes("series-1", "season-1") }
    }

    @Test
    fun `getEpisodes fetches and merges into series cache when absent`() = runTest {
        // Per-season fetch path (no prior grouped load).
        coEvery { apiClient.getEpisodes("series-1", "season-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )

        repository.getEpisodes("series-1", "season-1")

        // A subsequent getEpisodesGrouped must reuse the merged cache, not re-fetch.
        val grouped = repository.getAllEpisodesGrouped("series-1")
        assertTrue(grouped.isSuccess)
        assertEquals(listOf("e1"), grouped.getOrNull()!!["season-1"]!!.map { it.id })
        coVerify(exactly = 0) { apiClient.getAllEpisodes(any()) }
    }

    // ------------------------------------------------------------------
    // invalidateUserDataCaches: a user-data mutation on an EPISODE must
    // invalidate the parent series' seasons + episodes caches (not just the
    // episode's own detail). This is the regression guard for the seriesId
    // lookup in invalidateUserDataCaches.
    // ------------------------------------------------------------------

    @Test
    fun `markPlayed on an episode invalidates the parent series episodes cache`() = runTest {
        // Seed the detail cache with an episode whose seriesId points at series-1.
        coEvery { apiClient.getMediaDetail("episode-1") } returns Result.success(
            MediaDetail(
                item = episodeItem("episode-1", seriesId = "series-1", seasonId = "season-1"),
            )
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("season-1"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("episode-1", seriesId = "series-1", seasonId = "season-1"))
        )
        coEvery { apiClient.markPlayed("episode-1") } returns Result.success(Unit)

        // Populate: detail (episode) + seasons + grouped episodes for the series.
        repository.getMediaDetail("episode-1")
        repository.getSeasons("series-1")
        repository.getAllEpisodesGrouped("series-1")

        // Mutate episode user-data → must drop the parent series' caches.
        repository.markPlayed("episode-1")

        // Re-reads must re-fetch (caches were invalidated via seriesId lookup).
        repository.getSeasons("series-1")
        repository.getAllEpisodesGrouped("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
        coVerify(exactly = 2) { apiClient.getAllEpisodes("series-1") }
    }

    @Test
    fun `markPlayed on a series invalidates its own series cache`() = runTest {
        // Seed the detail cache with the series itself (mediaType == SERIES).
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())
        coEvery { apiClient.markPlayed("series-1") } returns Result.success(Unit)

        repository.getMediaDetail("series-1")
        repository.getSeasons("series-1")
        repository.markPlayed("series-1")
        repository.getSeasons("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `markPlayed on a movie does not touch any series cache`() = runTest {
        // A movie has no seriesId and is not itself a SERIES, so invalidateUserDataCaches
        // must leave the (unrelated) series caches untouched.
        coEvery { apiClient.getMediaDetail("movie-1") } returns Result.success(
            MediaDetail(item = mediaItem("movie-1"))
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())
        coEvery { apiClient.markPlayed("movie-1") } returns Result.success(Unit)

        repository.getMediaDetail("movie-1")
        repository.getSeasons("series-1")
        repository.markPlayed("movie-1")
        repository.getSeasons("series-1")

        coVerify(exactly = 1) { apiClient.getSeasons("series-1") }
    }

    // ------------------------------------------------------------------
    // getAlbumTracks epoch guard: a fetch that races a user-data invalidation
    // must NOT cache its (now-stale) snapshot. Mirrors the detailCacheEpoch
    // guard already covered for getMediaDetail.
    // ------------------------------------------------------------------

    @Test
    fun `getAlbumTracks does not cache a fetch that raced an invalidation`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { apiClient.getAlbumTracks("album-1") } coAnswers {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            Result.success(listOf(mediaItem("track-1")))
        }

        val first = async { repository.getAlbumTracks("album-1") }
        fetchStarted.await()
        // A user-data mutation lands while the fetch is in flight → its
        // internal eviction bumps detailCacheEpoch.
        repository.markUnplayed("album-1")
        releaseFetch.complete(Unit)
        assertTrue(first.await().isSuccess)

        // Next read must re-fetch (the racing fetch was not cached).
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))
        repository.getAlbumTracks("album-1")

        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
    }

    @Test
    fun `getAlbumTracks caches result when no invalidation races`() = runTest {
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))

        repository.getAlbumTracks("album-1")
        repository.getAlbumTracks("album-1")

        coVerify(exactly = 1) { apiClient.getAlbumTracks("album-1") }
    }

    // ------------------------------------------------------------------
    // getCollectionItems: cached per (collectionId, startIndex, limit) so
    // paginated reads don't re-hit the network on re-entry.
    // ------------------------------------------------------------------

    @Test
    fun `getCollectionItems caches per page and serves from cache`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 40, startIndex = 0)
        )
        coEvery { apiClient.getCollectionItems("col-1", 20, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c2")), totalRecordCount = 40, startIndex = 20)
        )

        repository.getCollectionItems("col-1", 0, 20)
        repository.getCollectionItems("col-1", 0, 20) // cache hit (same page)
        repository.getCollectionItems("col-1", 20, 20) // cache miss (different page)

        coVerify(exactly = 1) { apiClient.getCollectionItems("col-1", 0, 20) }
        coVerify(exactly = 1) { apiClient.getCollectionItems("col-1", 20, 20) }
    }

    // ------------------------------------------------------------------
    // Collection write/list paths are uncached passthroughs to the apiClient
    // (the picker refetches on every open so a freshly-created collection is
    // immediately selectable). Pin the delegation here.
    // ------------------------------------------------------------------

    @Test
    fun `getCollections delegates to apiClient`() = runTest {
        val collections = listOf(
            com.raulshma.jellyplay.core.model.CollectionSummary(id = "c1", name = "Marvel", itemCount = 4),
        )
        coEvery { apiClient.getCollections(100) } returns Result.success(collections)

        val result = repository.getCollections()

        assertTrue(result.isSuccess)
        assertEquals(collections, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getCollections(100) }
    }

    @Test
    fun `createCollection delegates name and seed ids to apiClient`() = runTest {
        coEvery { apiClient.createCollection("My Set", listOf("m1")) } returns Result.success("col-new")

        val result = repository.createCollection("My Set", listOf("m1"))

        assertEquals("col-new", result.getOrNull())
        coVerify(exactly = 1) { apiClient.createCollection("My Set", listOf("m1")) }
    }

    @Test
    fun `addItemsToCollection delegates collectionId and item ids to apiClient`() = runTest {
        coEvery { apiClient.addItemsToCollection("c1", listOf("m1", "m2")) } returns Result.success(Unit)

        val result = repository.addItemsToCollection("c1", listOf("m1", "m2"))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiClient.addItemsToCollection("c1", listOf("m1", "m2")) }
    }

    // ------------------------------------------------------------------
    // detailCacheEpoch stale-snapshot guard for the series-scoped caches.
    // Each of getSeasons / getSimilarItems / getAllEpisodesGrouped captures the
    // epoch at fetch start and skips the cache write if an invalidation landed
    // in flight — otherwise a pre-mutation snapshot would be pinned for the
    // full TTL. The guard is already covered for getMediaDetail and
    // getAlbumTracks; these mirror it for the remaining three caches so the
    // property is pinned everywhere it is implemented.
    // ------------------------------------------------------------------

    @Test
    fun `getSeasons does not cache a fetch that raced an invalidation`() = runTest {
        // getSeasons delegates to the catalogue snapshot, which fetches seasons
        // + episodes. Gate the seasons primitive so the invalidation lands
        // mid-fetch; the snapshot's cache write must be skipped (epoch guard).
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { apiClient.getSeasons("series-1") } coAnswers {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            Result.success(listOf(seasonItem("s1")))
        }
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        val first = async { repository.getSeasons("series-1") }
        fetchStarted.await()
        // Invalidation lands while the fetch is in flight → epoch bumps.
        // markPlayed on the series itself triggers invalidateUserDataCaches,
        // which calls invalidateSeriesCache (drops the catalogue snapshot) +
        // invalidateDetailCache (epoch bump). The fetch in flight must skip its
        // cache write.
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.markPlayed("series-1") } returns Result.success(Unit)
        repository.getMediaDetail("series-1")
        repository.markPlayed("series-1")
        releaseFetch.complete(Unit)
        assertTrue(first.await().isSuccess)

        // Next read must re-fetch (the racing fetch was not cached).
        repository.getSeasons("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `getSimilarItems does not cache a fetch that raced an invalidation`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { apiClient.getSimilarItems("item-1", 12) } coAnswers {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            Result.success(listOf(mediaItem("s1")))
        }

        val first = async { repository.getSimilarItems("item-1", limit = 12) }
        fetchStarted.await()
        // A user-data mutation lands while the fetch is in flight → its
        // internal eviction bumps detailCacheEpoch.
        repository.markUnplayed("item-1")
        releaseFetch.complete(Unit)
        assertTrue(first.await().isSuccess)

        // Next read must re-fetch (the racing fetch was not cached).
        repository.getSimilarItems("item-1", limit = 12)

        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 12) }
    }

    @Test
    fun `getAllEpisodesGrouped does not cache a fetch that raced an invalidation`() = runTest {
        // The catalogue snapshot fetches seasons (quick) then episodes (gated);
        // the invalidation lands during the episodes fetch, so the snapshot's
        // cache write must be skipped (epoch guard).
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(emptyList())
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        coEvery { apiClient.getAllEpisodes("series-1") } coAnswers {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            Result.success(listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1")))
        }

        val first = async { repository.getAllEpisodesGrouped("series-1") }
        fetchStarted.await()
        // markPlayed on the series triggers invalidateUserDataCaches, which calls
        // both invalidateSeriesCache (drops the catalogue snapshot) AND
        // invalidateDetailCache (bumps detailCacheEpoch). The fetch in flight
        // captured the pre-bump epoch, so its completing write must be skipped —
        // otherwise the stale pre-mutation episode list would be pinned for the
        // full TTL.
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.markPlayed("series-1") } returns Result.success(Unit)
        repository.getMediaDetail("series-1")
        repository.markPlayed("series-1")
        releaseFetch.complete(Unit)
        assertTrue(first.await().isSuccess)

        // Next read must re-fetch (the racing fetch was not cached).
        repository.getAllEpisodesGrouped("series-1")

        coVerify(exactly = 2) { apiClient.getAllEpisodes("series-1") }
    }

    // ------------------------------------------------------------------
    // getEpisodes edge cases: (1) when the grouped cache exists but does NOT
    // contain the requested season, the per-season call must fall through to
    // the network instead of returning null; (2) multiple per-season fetches
    // must merge into one grouped entry under the critical section so a later
    // getAllEpisodesGrouped sees every season.
    // ------------------------------------------------------------------

    @Test
    fun `getEpisodes falls back to network when season absent from grouped cache`() = runTest {
        // Snapshot populated for season-1 only (seasons primitive stubbed so
        // the catalogue's loadSeriesEpisodes completes).
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("season-1"), seasonItem("season-2"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        // Per-season fetch for the missing season-2.
        coEvery { apiClient.getEpisodes("series-1", "season-2") } returns Result.success(
            listOf(episodeItem("e2", seriesId = "series-1", seasonId = "season-2"))
        )

        repository.getAllEpisodesGrouped("series-1")
        val seasonTwo = repository.getEpisodes("series-1", "season-2")

        assertTrue(seasonTwo.isSuccess)
        assertEquals(listOf("e2"), seasonTwo.getOrNull()!!.map { it.id })
        // The per-season path fired because season-2 was absent from the snapshot.
        coVerify(exactly = 1) { apiClient.getEpisodes("series-1", "season-2") }
    }

    @Test
    fun `getEpisodes merges multiple seasons into the grouped cache`() = runTest {
        coEvery { apiClient.getEpisodes("series-1", "season-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        coEvery { apiClient.getEpisodes("series-1", "season-2") } returns Result.success(
            listOf(episodeItem("e2", seriesId = "series-1", seasonId = "season-2"))
        )

        // Two per-season fetches on the fallback path — both must land in the
        // single grouped entry under the merge critical section.
        repository.getEpisodes("series-1", "season-1")
        repository.getEpisodes("series-1", "season-2")

        val grouped = repository.getAllEpisodesGrouped("series-1")
        assertTrue(grouped.isSuccess)
        assertEquals(setOf("season-1", "season-2"), grouped.getOrNull()!!.keys)
        // No re-fetch of the full series — served from the merged cache.
        coVerify(exactly = 0) { apiClient.getAllEpisodes(any()) }
    }

    // ------------------------------------------------------------------
    // Mutation parity: toggleFavorite and markUnplayed route through the same
    // invalidateUserDataCaches path as markPlayed. The existing suite only pins
    // the invalidation contract for markPlayed; these guard against a future
    // refactor that drops the invalidation from one of the other two mutation
    // entry points (a regression that would pin stale favorite/played state
    // for the full TTL).
    // ------------------------------------------------------------------

    @Test
    fun `toggleFavorite invalidates the detail cache so next call re-fetches`() = runTest {
        coEvery { apiClient.getMediaDetail("movie-1") } returns Result.success(
            MediaDetail(item = mediaItem("movie-1"))
        )
        coEvery { apiClient.toggleFavorite("movie-1") } returns Result.success(true)

        repository.getMediaDetail("movie-1")
        repository.toggleFavorite("movie-1")
        repository.getMediaDetail("movie-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("movie-1") }
    }

    @Test
    fun `markUnplayed invalidates the detail cache so next call re-fetches`() = runTest {
        coEvery { apiClient.getMediaDetail("movie-1") } returns Result.success(
            MediaDetail(item = mediaItem("movie-1"))
        )
        coEvery { apiClient.markUnplayed("movie-1") } returns Result.success(Unit)

        repository.getMediaDetail("movie-1")
        repository.markUnplayed("movie-1")
        repository.getMediaDetail("movie-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("movie-1") }
    }

    // ------------------------------------------------------------------
    // Broad invalidation: invalidateCaches (the module-internal wholesale
    // drop, reached from the identity observer and the sync worker) must drop
    // the similar cache alongside the detail cache, and additionally the
    // series-scoped caches, so a user/server switch cannot leak one user's
    // seasons/episodes/album-tracks into another's session.
    // ------------------------------------------------------------------

    @Test
    fun `invalidateCaches clears the similar cache wholesale`() = runTest {
        coEvery { apiClient.getSimilarItems("item-1", 12) } returns Result.success(
            listOf(mediaItem("s1"))
        )

        repository.getSimilarItems("item-1", limit = 12)
        coVerify(exactly = 1) { apiClient.getSimilarItems("item-1", 12) }

        // Wholesale invalidation (logout / user switch path drops every
        // detail + similar entry).
        repository.invalidateCaches()
        repository.getSimilarItems("item-1", limit = 12)

        coVerify(exactly = 2) { apiClient.getSimilarItems("item-1", 12) }
    }

    @Test
    fun `invalidateCaches clears the series-scoped caches`() = runTest {
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))
        coEvery { apiClient.getCollectionItems("col-1", 0, 100) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )

        // Populate every series-scoped cache.
        repository.getSeasons("series-1")
        repository.getAllEpisodesGrouped("series-1")
        repository.getAlbumTracks("album-1")
        repository.getCollectionItems("col-1", 0, 100)

        // Full invalidation (user/server switch) must drop all of them.
        repository.invalidateCaches()

        repository.getSeasons("series-1")
        repository.getAllEpisodesGrouped("series-1")
        repository.getAlbumTracks("album-1")
        repository.getCollectionItems("col-1", 0, 100)

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
        coVerify(exactly = 2) { apiClient.getAllEpisodes("series-1") }
        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 100) }
    }

    @Test
    fun `parseLrc parses single timestamp correctly`() {
        val lrc = "[00:01.500]Hello World"
        val lines = parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello World", lines[0].text)
    }

    @Test
    fun `parseLrc sorts lines by time`() {
        val lrc = "[00:10.000]Second\n[00:05.000]First\n[00:15.000]Third"
        val lines = parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
        assertEquals("Third", lines[2].text)
    }

    @Test
    fun `parseLrc handles empty lines`() {
        val lrc = "[00:01.000]\n[00:05.000]Hello"
        val lines = parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertEquals(1000L, lines[0].timeMs)
    }

    @Test
    fun `parseLrc returns empty list for invalid input`() {
        val lines = parseLrc("no timestamps here")
        assertEquals(0, lines.size)
    }

    // ------------------------------------------------------------------
    // Plan 08 step 0 — characterization pins for self-invalidation
    // (docs/architecture/plans/08-mediarepository-self-invalidation.md).
    //
    // The two original pins asserted the *future* mutation-owned invalidation
    // and were @Ignore'd against the bare-passthrough mutations; steps 1 and 3
    // un-ignored them (season marks own the parent-series drop; collection
    // edits drop their items cache). The suite below keeps both assertions as
    // permanent regression guards.
    // ------------------------------------------------------------------

    @Test
    fun `markSeasonPlayed drops the series catalogue so re-entry re-fetches`() = runTest {
        // Plan 08 step 0 pin, un-ignored by step 1: the series screen supplies
        // both ids, so the season-aware mutation owns the parent-series
        // invalidation the reactor used to perform by hand.
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("season-1"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        coEvery { apiClient.markPlayed("season-1") } returns Result.success(Unit)

        repository.getSeasons("series-1")
        repository.markSeasonPlayed("season-1", seriesId = "series-1")
        repository.getSeasons("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `markSeasonUnplayed drops the series detail so re-entry re-fetches`() = runTest {
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.markUnplayed("season-1") } returns Result.success(Unit)

        repository.getMediaDetail("series-1")
        repository.markSeasonUnplayed("season-1", seriesId = "series-1")
        repository.getMediaDetail("series-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("series-1") }
    }

    @Test
    fun `addItemsToCollection then getCollectionItems re-fetches`() = runTest {
        // Plan 08 step 0 pin, un-ignored by step 3: the mutation self-
        // invalidates, so DetailViewModel's manual compensation is gone.
        coEvery { apiClient.getCollectionItems("col-1", 0, 50) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { apiClient.addItemsToCollection("col-1", listOf("m1")) } returns Result.success(Unit)

        repository.getCollectionItems("col-1")
        repository.addItemsToCollection("col-1", listOf("m1"))
        repository.getCollectionItems("col-1")

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 50) }
    }

    @Test
    fun `addItemsToCollection drops collection items but not an unrelated item's detail`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 50) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { apiClient.getMediaDetail("movie-1") } returns Result.success(
            MediaDetail(item = mediaItem("movie-1"))
        )
        coEvery { apiClient.addItemsToCollection("col-1", listOf("m1")) } returns Result.success(Unit)

        repository.getCollectionItems("col-1")
        repository.getMediaDetail("movie-1")
        repository.addItemsToCollection("col-1", listOf("m1"))
        repository.getCollectionItems("col-1")
        repository.getMediaDetail("movie-1")

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 50) }
        // The unrelated item's detail was untouched.
        coVerify(exactly = 1) { apiClient.getMediaDetail("movie-1") }
    }

    @Test
    fun `createCollection invalidates the new collection's items cache`() = runTest {
        coEvery { apiClient.getCollectionItems("col-new", 0, 50) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { apiClient.createCollection("My Set", listOf("m1")) } returns Result.success("col-new")

        repository.getCollectionItems("col-new")
        repository.createCollection("My Set", listOf("m1"))
        repository.getCollectionItems("col-new")

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-new", 0, 50) }
    }

    // ------------------------------------------------------------------
    // Playlist edits self-invalidate (plan 08 step 3): getPlaylistItems is an
    // uncached passthrough, so the one cached projection of a playlist is its
    // detail entry — one drop per edit covers the playlist screen's refresh.
    // ------------------------------------------------------------------

    @Test
    fun `removeItemsFromPlaylist invalidates the playlist's detail cache`() = runTest {
        coEvery { apiClient.getMediaDetail("pl-1") } returns Result.success(
            MediaDetail(item = mediaItem("pl-1"))
        )
        coEvery { apiClient.removeItemsFromPlaylist("pl-1", listOf("e1")) } returns Result.success(Unit)

        repository.getMediaDetail("pl-1")
        repository.removeItemsFromPlaylist("pl-1", listOf("e1"))
        repository.getMediaDetail("pl-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("pl-1") }
    }

    @Test
    fun `addItemsToPlaylist invalidates the playlist's detail cache`() = runTest {
        coEvery { apiClient.getMediaDetail("pl-1") } returns Result.success(
            MediaDetail(item = mediaItem("pl-1"))
        )
        coEvery { apiClient.addItemsToPlaylist("pl-1", listOf("m1")) } returns Result.success(Unit)

        repository.getMediaDetail("pl-1")
        repository.addItemsToPlaylist("pl-1", listOf("m1"))
        repository.getMediaDetail("pl-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("pl-1") }
    }

    @Test
    fun `movePlaylistItem invalidates the playlist's detail cache`() = runTest {
        coEvery { apiClient.getMediaDetail("pl-1") } returns Result.success(
            MediaDetail(item = mediaItem("pl-1"))
        )
        coEvery { apiClient.movePlaylistItem("pl-1", "e1", 2) } returns Result.success(Unit)

        repository.getMediaDetail("pl-1")
        repository.movePlaylistItem("pl-1", "e1", 2)
        repository.getMediaDetail("pl-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("pl-1") }
    }

    // ------------------------------------------------------------------
    // invalidateFor: the single per-type dispatch (plan 08 step 3) that
    // absorbed the provider's invalidateByType table. One encoding of the
    // "what did this detail's type affect" rule, asserted per branch.
    // ------------------------------------------------------------------

    @Test
    fun `invalidateFor on a series drops its detail and catalogue`() = runTest {
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        val detail = repository.getMediaDetail("series-1").getOrThrow()
        repository.getSeasons("series-1")

        repository.invalidateFor(detail)

        repository.getMediaDetail("series-1")
        repository.getSeasons("series-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("series-1") }
        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `invalidateFor on an episode drops the parent series catalogue`() = runTest {
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        repository.getSeasons("series-1")
        repository.invalidateFor(
            MediaDetail(item = episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        repository.getSeasons("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `invalidateFor on an album drops its detail and tracks`() = runTest {
        coEvery { apiClient.getMediaDetail("album-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "album-1", name = "Album", mediaType = MediaType.ALBUM))
        )
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))

        val detail = repository.getMediaDetail("album-1").getOrThrow()
        repository.getAlbumTracks("album-1")

        repository.invalidateFor(detail)

        repository.getMediaDetail("album-1")
        repository.getAlbumTracks("album-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("album-1") }
        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
    }

    @Test
    fun `invalidateFor on a collection drops its items cache`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 50) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )

        repository.getCollectionItems("col-1")
        repository.invalidateFor(
            MediaDetail(item = MediaItem(id = "col-1", name = "Box Set", mediaType = MediaType.COLLECTION))
        )
        repository.getCollectionItems("col-1")

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 50) }
    }

    @Test
    fun `invalidateFor on a movie touches no series cache`() = runTest {
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        repository.getSeasons("series-1")
        repository.invalidateFor(MediaDetail(item = mediaItem("movie-1")))
        repository.getSeasons("series-1")

        coVerify(exactly = 1) { apiClient.getSeasons("series-1") }
    }

    // ── Played-state propagation to offline store ─────────────────────
    // Fan-out behaviour (online mirror, offline apply+enqueue, transient-failure
    // enqueue) moved to PlayedStateSyncImpl and is covered by
    // PlayedStateSyncImplTest. MediaRepositoryImpl.markPlayed/markUnplayed now
    // only own cache invalidation and delegate the fan-out.
}

private fun mediaItem(id: String) = MediaItem(
    id = id,
    name = id,
    mediaType = MediaType.MOVIE,
)

private fun seasonItem(id: String) = MediaItem(
    id = id,
    name = id,
    mediaType = MediaType.SEASON,
)

private fun episodeItem(
    id: String,
    seriesId: String,
    seasonId: String,
) = MediaItem(
    id = id,
    name = id,
    mediaType = MediaType.EPISODE,
    seriesId = seriesId,
    seasonId = seasonId,
)

private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})]""")

private fun parseLrc(lrcContent: String): List<LyricsLine> {
    val lines = mutableListOf<LyricsLine>()
    lrcContent.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        val times = TIME_REGEX.findAll(line).map { match ->
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toDouble()
            minutes * 60_000 + (seconds * 1000).toLong()
        }.toList()
        if (times.isEmpty()) return@forEach
        val textStart = line.lastIndexOf(']') + 1
        val text = line.substring(textStart).trim()
        if (text.isEmpty()) {
            times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = "")) }
        } else {
            times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = text)) }
        }
    }
    return lines.sortedBy { it.timeMs }
}
