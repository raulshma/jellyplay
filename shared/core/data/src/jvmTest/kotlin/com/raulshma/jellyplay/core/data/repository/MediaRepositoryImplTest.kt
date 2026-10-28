package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.SystemTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of the orphaned legacy `:core:data` suite of the same name (the legacy
 * module's test source set stopped compiling when MediaRepositoryImpl moved to
 * shared:core:data with its 8-arg constructor and lyrics were extracted into
 * LyricsRepositoryImpl). The lyrics tests live in LyricsRepositoryImplTest now;
 * everything here targets the current MediaRepositoryImpl constructor wiring.
 */
class MediaRepositoryImplTest {

    /** Late-binding handle the announcer's arming lambda reads (set at build time). */
    private var built: MediaRepositoryImpl? = null

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true)
    private val playedStateSync: PlayedStateSync = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val userDataRealtimeChannel: UserDataRealtimeChannel = mockk(relaxed = true)

    private lateinit var repository: MediaRepositoryImpl

    // Facade split: the playlist members moved to PlaylistRepositoryImpl;
    // built over the SAME MediaRepositoryInternals single-construction (and
    // the same mocked apiClient — the union mock doubles as the
    // LibraryApiClient family view), so the self-invalidation tests below
    // still pin the cross-surface behavior: an edit through the playlist
    // impl drops a detail cached through the media impl. Construction-only
    // re-point; every assertion is unchanged.
    private lateinit var playlistRepository: PlaylistRepositoryImpl

    // The real catalogue single the repo's invalidation funnel drops into.
    // The union's series-read pass-throughs retired (zero repo-typed
    // callers), so the mutation-funnel pins below observe the drop through
    // THIS reference — the same production surface the funnel invalidates.
    private lateinit var episodeCatalogue: EpisodeCatalogueImpl

    @BeforeTest
    fun setup() {
        // Real (permanently-null) session flow behind the shared HomeSession
        // identity detector — this suite never switches identity, so
        // CacheIdentity.UNKNOWN is the expected key surface.
        every { apiClient.session } returns MutableStateFlow(null)
        // User-data mutations fan out through PlayedStateSync now (the repo
        // only owns cache invalidation around the write) — pin the mock's
        // write results so the invalidation wrappers see a successful write.
        coEvery { playedStateSync.flip(any(), any()) } returns Result.success(Unit)
        coEvery { playedStateSync.flip(any(), any(), any()) } returns Result.success(Unit)
        coEvery { playedStateSync.toggleFavorite(any()) } returns Result.success(true)
        // The repo's invalidation funnel drops a real EpisodeCatalogueImpl's
        // series snapshot, which in turn calls back into the mocked
        // apiClient — the series/episodes stubs keep working end-to-end
        // through the catalogue transplant.
        val homeSession = HomeSession(
            apiClient,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val sessionCacheRegistry = SessionCacheRegistry(
            homeSession,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        episodeCatalogue = EpisodeCatalogueImpl(
            apiClient,
            offlineRepository,
            homeSession,
            sessionCacheRegistry,
        )
        val internals = MediaRepositoryInternals(apiClient, homeSession, sessionCacheRegistry)
        val timeSource = SystemTimeSource()
        // The repo ctor-injects the persisted
        // half of the home pipeline (the same store the Koin graph wires);
        // this suite never touches home persistence, so the relaxed DAO
        // mock under the store is inert.
        val homeSnapshotStore = HomeSectionsSnapshotStore(
            homeSectionCacheDao,
            homeSession,
            timeSource,
        )
        repository = MediaRepositoryImpl(
            // One union mock covers both family seams (the JellyfinApiClient
            // mock implements each of them).
            libraryApiClient = apiClient,
            collectionApiClient = apiClient,
            // The home cache-maintenance port (inert here — this suite pins
            // the detail-cache / staleness choreography).
            homeSectionsCachePort = mockk(relaxed = true),
            homeSnapshotStore = homeSnapshotStore,
            playedStateSync = playedStateSync,
            episodeCatalogue = episodeCatalogue,
            userDataRealtimeChannel = userDataRealtimeChannel,
            timeSource = timeSource,
            homeSession = homeSession,
            userDataAnnouncer = announcerFor(homeSession) { built },
            sessionCacheRegistry = sessionCacheRegistry,
            internals = internals,
        ).also { built = it }
        playlistRepository = PlaylistRepositoryImpl(apiClient, internals)
    }

    @Test
    fun `getMediaDetail caches result`() = runTest {
        coEvery { apiClient.getMediaDetail("item-1") } returns Result.success(
            mockk(relaxed = true)
        )

        val first = repository.getMediaDetail("item-1", force = false)
        assertTrue(first.isSuccess)

        val second = repository.getMediaDetail("item-1", force = false)
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
            val a = async { repository.getMediaDetail("item-1", force = false) }
            // Deterministically wait until the first fetch is suspended inside
            // the mock before launching the second caller, so the single-flight
            // (in-flight Deferred) path is the one exercised, not a cache hit.
            fetchStarted.await()
            val b = async { repository.getMediaDetail("item-1", force = false) }
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

        repository.getMediaDetail("item-1", force = false)
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

        val first = async { repository.getMediaDetail("item-1", force = false) }
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
        repository.getMediaDetail("item-1", force = false)

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
    // Series-catalogue invalidation funnel: a user-data mutation on a series
    // or episode must drop the catalogue's snapshot for that series, so
    // re-entry into the series detail reflects the write. The reads observe
    // the funnel through the catalogue single itself (the union's
    // series-read pass-throughs retired — the catalogue is the read seam;
    // the catalogue's own caching/merging semantics are pinned by
    // EpisodeCatalogueImplTest).
    // ------------------------------------------------------------------

    @Test
    fun `markUnplayed on a series drops the catalogue snapshot so next load re-fetches`() = runTest {
        // The seasons/episodes cache is dropped through the
        // mutation itself (was: invalidateSeriesCache, which had no production
        // callers outside this funnel).
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("s1"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        repository.getMediaDetail("series-1", force = false)
        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.markUnplayed("series-1")
        episodeCatalogue.loadSeriesEpisodes("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `markUnplayed on an episode drops the catalogue snapshot so next load re-fetches`() = runTest {
        coEvery { apiClient.getMediaDetail("episode-1") } returns Result.success(
            MediaDetail(
                item = episodeItem("episode-1", seriesId = "series-1", seasonId = "season-1"),
            )
        )
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(emptyList())
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )

        repository.getMediaDetail("episode-1", force = false)
        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.markUnplayed("episode-1")
        episodeCatalogue.loadSeriesEpisodes("series-1")

        coVerify(exactly = 2) { apiClient.getAllEpisodes("series-1") }
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

        // Populate: detail (episode) + the series' catalogue snapshot.
        repository.getMediaDetail("episode-1", force = false)
        episodeCatalogue.loadSeriesEpisodes("series-1")

        // Mutate episode user-data → must drop the parent series' caches.
        repository.markPlayed("episode-1")

        // Re-reads must re-fetch (caches were invalidated via seriesId lookup).
        episodeCatalogue.loadSeriesEpisodes("series-1")

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

        repository.getMediaDetail("series-1", force = false)
        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.markPlayed("series-1")
        episodeCatalogue.loadSeriesEpisodes("series-1")

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

        repository.getMediaDetail("movie-1", force = false)
        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.markPlayed("movie-1")
        episodeCatalogue.loadSeriesEpisodes("series-1")

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

        val first = async { repository.getAlbumTracks("album-1", force = false) }
        fetchStarted.await()
        // A user-data mutation lands while the fetch is in flight → its
        // internal eviction bumps detailCacheEpoch.
        repository.markUnplayed("album-1")
        releaseFetch.complete(Unit)
        assertTrue(first.await().isSuccess)

        // Next read must re-fetch (the racing fetch was not cached).
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))
        repository.getAlbumTracks("album-1", force = false)

        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
    }

    @Test
    fun `getAlbumTracks caches result when no invalidation races`() = runTest {
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))

        repository.getAlbumTracks("album-1", force = false)
        repository.getAlbumTracks("album-1", force = false)

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

        repository.getCollectionItems("col-1", 0, 20, force = false)
        repository.getCollectionItems("col-1", 0, 20, force = false) // cache hit (same page)
        repository.getCollectionItems("col-1", 20, 20, force = false) // cache miss (different page)

        coVerify(exactly = 1) { apiClient.getCollectionItems("col-1", 0, 20) }
        coVerify(exactly = 1) { apiClient.getCollectionItems("col-1", 20, 20) }
    }

    @Test
    fun `getCollectionItems with force re-fetches instead of serving the cached page`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 40, startIndex = 0)
        )

        repository.getCollectionItems("col-1", 0, 20, force = false)
        repository.getCollectionItems("col-1", 0, 20, force = false) // cache hit
        repository.getCollectionItems("col-1", 0, 20, force = true)

        // The deferred silent refresh's freshness lever: a member item's flip
        // never evicts the collection's page key, so only the explicit force
        // can bypass it.
        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 20) }
    }

    @Test
    fun `getCollectionItems with force evicts every cached page of the collection`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 40, startIndex = 0)
        )
        coEvery { apiClient.getCollectionItems("col-1", 20, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c2")), totalRecordCount = 40, startIndex = 20)
        )

        repository.getCollectionItems("col-1", 0, 20, force = false)
        repository.getCollectionItems("col-1", 20, 20, force = false)
        // Forcing one page drops the collection's whole page family — same
        // prefix evict as invalidateCollectionItemsCache — so a paginated
        // caller's force-heal never leaves stale sibling pages behind.
        repository.getCollectionItems("col-1", 0, 20, force = true)
        repository.getCollectionItems("col-1", 20, 20, force = false)

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 20) }
        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 20, 20) }
    }

    @Test
    fun `collection prefix evict never drops another collection whose id extends it`() = runTest {
        // "12" vs "123": without the trailing underscore in
        // [MediaRepositoryImpl.collectionItemsKey]'s evict prefix,
        // "collection_12" is a prefix of "collection_123_0_20" too, so
        // force-healing collection 12 would silently drop collection 123's
        // cached pages.
        coEvery { apiClient.getCollectionItems("12", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c12")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { apiClient.getCollectionItems("123", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c123")), totalRecordCount = 1, startIndex = 0)
        )

        repository.getCollectionItems("12", 0, 20, force = false)
        repository.getCollectionItems("123", 0, 20, force = false)
        repository.getCollectionItems("12", 0, 20, force = true)
        repository.getCollectionItems("123", 0, 20, force = false) // must still be a cache hit

        coVerify(exactly = 2) { apiClient.getCollectionItems("12", 0, 20) }
        coVerify(exactly = 1) { apiClient.getCollectionItems("123", 0, 20) }
    }

    @Test
    fun `getAlbumTracks with force re-fetches instead of serving the cached list`() = runTest {
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("t1")))

        repository.getAlbumTracks("album-1", force = false)
        repository.getAlbumTracks("album-1", force = false) // cache hit
        repository.getAlbumTracks("album-1", force = true)

        // The deferred silent refresh's freshness lever (mirrors the
        // collection page lever above): a track flip never evicts the
        // album's tracks key, so only the explicit force can bypass it.
        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
    }

    // ------------------------------------------------------------------
    // Announced staleness for the gap groups (#157 generalized): a user-data
    // flip evicts only the flipped item's OWN keys — `tracks_<trackId>` and
    // `detail_<itemId>` — never the album's `tracks_<albumId>` entry or the
    // collection's page keys, because the owning album's/collection's
    // identity is not recorded anywhere in the reverse direction. The
    // composite eviction arms one marker per group; the next NON-forced read
    // consumes it as a one-shot force, so the detail hosts no longer need to
    // hand-thread force for exactly this healing.
    // ------------------------------------------------------------------

    @Test
    fun `a track flip arms the album-tracks marker - the next non-forced read refetches once`() = runTest {
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("t1")))

        repository.getAlbumTracks("album-1", force = false) // populate the album's tracks entry
        // Flip a MEMBER (not the album): the eviction drops `tracks_track-1`
        // and `detail_track-1`, never `tracks_album-1` — only the armed
        // marker can make the album's next read bypass it.
        repository.markUnplayed("track-1")
        repository.getAlbumTracks("album-1", force = false) // consumes the marker as a force
        repository.getAlbumTracks("album-1", force = false) // marker gone: cached

        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
    }

    @Test
    fun `a failed album-tracks read re-arms the marker for the next read`() = runTest {
        var fetchCalls = 0
        coEvery { apiClient.getAlbumTracks("album-1") } coAnswers {
            if (++fetchCalls == 2) Result.failure(RuntimeException("offline blip"))
            else Result.success(listOf(mediaItem("t1")))
        }

        repository.getAlbumTracks("album-1", force = false) // populate
        repository.markUnplayed("track-1") // arm the marker
        val failed = repository.getAlbumTracks("album-1", force = false) // consumes, fetch fails
        repository.getAlbumTracks("album-1", force = false) // re-armed: refetches

        assertTrue(failed.isFailure)
        coVerify(exactly = 3) { apiClient.getAlbumTracks("album-1") }
    }

    @Test
    fun `a member flip arms the collection-items marker - the next non-forced page read refetches`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )

        repository.getCollectionItems("col-1", 0, 20, force = false) // populate the page entry
        // Flip a MEMBER (not the collection): the eviction drops
        // `detail_member-1`, never the collection's page key.
        repository.markUnplayed("member-1")
        repository.getCollectionItems("col-1", 0, 20, force = false) // consumes the marker
        repository.getCollectionItems("col-1", 0, 20, force = false) // marker gone: cached

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 20) }
    }

    @Test
    fun `a failed collection-items read re-arms the marker for the next read`() = runTest {
        var fetchCalls = 0
        coEvery { apiClient.getCollectionItems("col-1", 0, 20) } coAnswers {
            if (++fetchCalls == 2) Result.failure(RuntimeException("offline blip"))
            else Result.success(SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0))
        }

        repository.getCollectionItems("col-1", 0, 20, force = false) // populate
        repository.markUnplayed("member-1") // arm the marker
        val failed = repository.getCollectionItems("col-1", 0, 20, force = false) // consumes, fetch fails
        repository.getCollectionItems("col-1", 0, 20, force = false) // re-armed: refetches

        assertTrue(failed.isFailure)
        coVerify(exactly = 3) { apiClient.getCollectionItems("col-1", 0, 20) }
    }

    @Test
    fun `a forced album-detail read does not arm the gap markers`() = runTest {
        // The provider's forced album-detail read ends in
        // cacheInvalidation.invalidateFor(detail) (UnifiedMediaDetailProviderImpl),
        // whose ALBUM branch rides the composite eviction — no user-data
        // change behind it, so it must not arm the gap markers: an armed
        // collectionItemsStale would force the next non-forced collection
        // read with nothing to heal. Arming is a user-data statement and
        // lives with the user-data callers (the mutation wrapper,
        // invalidateForUserDataChange). The seam is called directly because
        // getMediaDetail(force) itself never reaches it — the provider owns
        // that call.
        val albumDetail = MediaDetail(
            item = MediaItem(id = "album-1", name = "Album", mediaType = MediaType.ALBUM),
        )
        coEvery { apiClient.getCollectionItems("col-1", 0, 20) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )

        repository.getCollectionItems("col-1", 0, 20, force = false) // populate the page entry
        repository.invalidateFor(albumDetail) // evicts; must NOT arm
        repository.getCollectionItems("col-1", 0, 20, force = false) // marker not armed: cached

        coVerify(exactly = 1) { apiClient.getCollectionItems("col-1", 0, 20) }
    }

    // ------------------------------------------------------------------
    // Collection write paths are uncached passthroughs to the apiClient
    // (the picker refetches on every open so a freshly-created collection is
    // immediately selectable). Pin the delegation here.
    // ------------------------------------------------------------------

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
    // detailCacheEpoch stale-snapshot guard. getSimilarItems captures the
    // epoch at fetch start and skips the cache write if an invalidation
    // landed in flight — otherwise a pre-mutation snapshot would be pinned
    // for the full TTL. The guard is already covered for getMediaDetail and
    // getAlbumTracks here; the series reads' own epoch guard lives in the
    // catalogue and is pinned by EpisodeCatalogueImplTest (the union's
    // series-read pass-throughs retired).
    // ------------------------------------------------------------------

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

        repository.getMediaDetail("movie-1", force = false)
        repository.toggleFavorite("movie-1")
        repository.getMediaDetail("movie-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("movie-1") }
    }

    @Test
    fun `markUnplayed invalidates the detail cache so next call re-fetches`() = runTest {
        coEvery { apiClient.getMediaDetail("movie-1") } returns Result.success(
            MediaDetail(item = mediaItem("movie-1"))
        )

        repository.getMediaDetail("movie-1", force = false)
        repository.markUnplayed("movie-1")
        repository.getMediaDetail("movie-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("movie-1") }
    }

    // ------------------------------------------------------------------
    // Spec-registry pin: the composite user-data eviction clears exactly the
    // USER_DATA group's member caches. Latest media is a member (its entries
    // carry per-item UserData under parent-folder keys — latestMediaSpec);
    // genres is not — so a user-data mutation refetches the former while the
    // latter still serves from cache.
    // ------------------------------------------------------------------

    @Test
    fun `a user-data mutation refetches the userData-group cache but serves a non-member from cache`() = runTest {
        coEvery { apiClient.getLatestMedia("folder-1", 20) } returns Result.success(listOf(mediaItem("l1")))
        coEvery { apiClient.getGenres(any()) } returns Result.success(emptyList())

        // Populate both caches.
        repository.getLatestMedia("folder-1", 20)
        repository.getGenres(parentId = null, force = false)
        coVerify(exactly = 1) { apiClient.getLatestMedia("folder-1", 20) }
        coVerify(exactly = 1) { apiClient.getGenres(null) }

        // The composite eviction runs through the mutation wrapper (markPlayed
        // → withUserDataMutationCacheInvalidation → invalidateUserDataCaches).
        repository.markPlayed("movie-1")

        // Group member: whole-cache drop → refetch. Non-member: still cached.
        repository.getLatestMedia("folder-1", 20)
        repository.getGenres(parentId = null, force = false)

        coVerify(exactly = 2) { apiClient.getLatestMedia("folder-1", 20) }
        coVerify(exactly = 1) { apiClient.getGenres(null) }
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
        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.getAlbumTracks("album-1", force = false)
        repository.getCollectionItems("col-1", 0, 100, force = false)

        // Full invalidation (user/server switch) must drop all of them.
        repository.invalidateCaches()

        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.getAlbumTracks("album-1", force = false)
        repository.getCollectionItems("col-1", 0, 100, force = false)

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
        coVerify(exactly = 2) { apiClient.getAllEpisodes("series-1") }
        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 100) }
    }

    // ------------------------------------------------------------------
    // Characterization pins for mutation-owned self-invalidation: season
    // marks own the parent-series drop; collection edits drop their items
    // cache. Both assertions stay as permanent regression guards.
    // ------------------------------------------------------------------

    @Test
    fun `markSeasonPlayed drops the series catalogue so re-entry re-fetches`() = runTest {
        // The series screen supplies
        // both ids, so the season-aware mutation owns the parent-series
        // invalidation the reactor used to perform by hand.
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(
            listOf(seasonItem("season-1"))
        )
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(
            listOf(episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )

        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.markSeasonPlayed("season-1", seriesId = "series-1")
        episodeCatalogue.loadSeriesEpisodes("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `markSeasonUnplayed drops the series detail so re-entry re-fetches`() = runTest {
        // The season-aware mutation routes the composite invalidation through
        // the series id (the caller-supplied hint doubles as the evicted item
        // id), so both the series' cached detail and its catalogue drop.
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "series-1", name = "Show", mediaType = MediaType.SERIES))
        )

        repository.getMediaDetail("series-1", force = false)
        repository.markSeasonUnplayed("season-1", seriesId = "series-1")
        repository.getMediaDetail("series-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("series-1") }
    }

    @Test
    fun `addItemsToCollection then getCollectionItems re-fetches`() = runTest {
        // The mutation self-
        // invalidates, so DetailViewModel's manual compensation is gone.
        coEvery { apiClient.getCollectionItems("col-1", 0, 50) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )
        coEvery { apiClient.addItemsToCollection("col-1", listOf("m1")) } returns Result.success(Unit)

        repository.getCollectionItems("col-1", startIndex = 0, limit = 50, force = false)
        repository.addItemsToCollection("col-1", listOf("m1"))
        repository.getCollectionItems("col-1", startIndex = 0, limit = 50, force = false)

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

        repository.getCollectionItems("col-1", startIndex = 0, limit = 50, force = false)
        repository.getMediaDetail("movie-1", force = false)
        repository.addItemsToCollection("col-1", listOf("m1"))
        repository.getCollectionItems("col-1", startIndex = 0, limit = 50, force = false)
        repository.getMediaDetail("movie-1", force = false)

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

        repository.getCollectionItems("col-new", startIndex = 0, limit = 50, force = false)
        repository.createCollection("My Set", listOf("m1"))
        repository.getCollectionItems("col-new", startIndex = 0, limit = 50, force = false)

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-new", 0, 50) }
    }

    // ------------------------------------------------------------------
    // Playlist edits self-invalidate: getPlaylistItems is an
    // uncached passthrough, so the one cached projection of a playlist is its
    // detail entry — one drop per edit covers the playlist screen's refresh.
    // ------------------------------------------------------------------

    @Test
    fun `removeItemsFromPlaylist invalidates the playlist's detail cache`() = runTest {
        coEvery { apiClient.getMediaDetail("pl-1") } returns Result.success(
            MediaDetail(item = mediaItem("pl-1"))
        )
        coEvery { apiClient.removeItemsFromPlaylist("pl-1", listOf("e1")) } returns Result.success(Unit)

        repository.getMediaDetail("pl-1", force = false)
        playlistRepository.removeItemsFromPlaylist("pl-1", listOf("e1"))
        repository.getMediaDetail("pl-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("pl-1") }
    }

    @Test
    fun `addItemsToPlaylist invalidates the playlist's detail cache`() = runTest {
        coEvery { apiClient.getMediaDetail("pl-1") } returns Result.success(
            MediaDetail(item = mediaItem("pl-1"))
        )
        coEvery { apiClient.addItemsToPlaylist("pl-1", listOf("m1")) } returns Result.success(Unit)

        repository.getMediaDetail("pl-1", force = false)
        playlistRepository.addItemsToPlaylist("pl-1", listOf("m1"))
        repository.getMediaDetail("pl-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("pl-1") }
    }

    @Test
    fun `movePlaylistItem invalidates the playlist's detail cache`() = runTest {
        coEvery { apiClient.getMediaDetail("pl-1") } returns Result.success(
            MediaDetail(item = mediaItem("pl-1"))
        )
        coEvery { apiClient.movePlaylistItem("pl-1", "e1", 2) } returns Result.success(Unit)

        repository.getMediaDetail("pl-1", force = false)
        playlistRepository.movePlaylistItem("pl-1", "e1", 2)
        repository.getMediaDetail("pl-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("pl-1") }
    }

    // ------------------------------------------------------------------
    // invalidateFor: the single per-type dispatch that
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

        val detail = repository.getMediaDetail("series-1", force = false).getOrThrow()
        episodeCatalogue.loadSeriesEpisodes("series-1")

        repository.invalidateFor(detail)

        repository.getMediaDetail("series-1", force = false)
        episodeCatalogue.loadSeriesEpisodes("series-1")

        coVerify(exactly = 2) { apiClient.getMediaDetail("series-1") }
        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `invalidateFor on an episode drops the parent series catalogue`() = runTest {
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.invalidateFor(
            MediaDetail(item = episodeItem("e1", seriesId = "series-1", seasonId = "season-1"))
        )
        episodeCatalogue.loadSeriesEpisodes("series-1")

        coVerify(exactly = 2) { apiClient.getSeasons("series-1") }
    }

    @Test
    fun `invalidateFor on an album drops its detail and tracks`() = runTest {
        coEvery { apiClient.getMediaDetail("album-1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "album-1", name = "Album", mediaType = MediaType.ALBUM))
        )
        coEvery { apiClient.getAlbumTracks("album-1") } returns Result.success(listOf(mediaItem("track-1")))

        val detail = repository.getMediaDetail("album-1", force = false).getOrThrow()
        repository.getAlbumTracks("album-1", force = false)

        repository.invalidateFor(detail)

        repository.getMediaDetail("album-1", force = false)
        repository.getAlbumTracks("album-1", force = false)

        coVerify(exactly = 2) { apiClient.getMediaDetail("album-1") }
        coVerify(exactly = 2) { apiClient.getAlbumTracks("album-1") }
    }

    @Test
    fun `invalidateFor on a collection drops its items cache`() = runTest {
        coEvery { apiClient.getCollectionItems("col-1", 0, 50) } returns Result.success(
            SearchResult(items = listOf(mediaItem("c1")), totalRecordCount = 1, startIndex = 0)
        )

        repository.getCollectionItems("col-1", startIndex = 0, limit = 50, force = false)
        repository.invalidateFor(
            MediaDetail(item = MediaItem(id = "col-1", name = "Box Set", mediaType = MediaType.COLLECTION))
        )
        repository.getCollectionItems("col-1", startIndex = 0, limit = 50, force = false)

        coVerify(exactly = 2) { apiClient.getCollectionItems("col-1", 0, 50) }
    }

    @Test
    fun `invalidateFor on a movie touches no series cache`() = runTest {
        coEvery { apiClient.getSeasons("series-1") } returns Result.success(listOf(seasonItem("s1")))
        coEvery { apiClient.getAllEpisodes("series-1") } returns Result.success(emptyList())

        episodeCatalogue.loadSeriesEpisodes("series-1")
        repository.invalidateFor(MediaDetail(item = mediaItem("movie-1")))
        episodeCatalogue.loadSeriesEpisodes("series-1")

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
