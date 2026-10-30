package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.testfixtures.FakeTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The direct suite for [MediaFreshnessLedger] — the freshness/cache chassis
 * extracted from MediaRepositoryImpl. Pins the ledger's own contract at its
 * own seam (behavioral pins, not source text):
 *
 *  - **spec registry completeness** — every simple cache concept joins the
 *    wholesale ladder (a concept missing from the registry list would keep
 *    serving its cache across [MediaFreshnessLedger.invalidateCaches]);
 *  - **stale-group arming per group** — the user-data-write channel arms
 *    only the gap riders, the confirmed-write channel arms every group, the
 *    identity transition resets all markers;
 *  - **write-generation bump-after-operation ordering** — the funnel's bump
 *    drops the assembled payload and stall-guards the in-flight flight;
 *  - **composite eviction fan-out** — per-item detail drop + the USER_DATA
 *    group + the network sub-call caches + the series-resolution funnel;
 *  - **wholesale drop** — every spec refetches; the catalogue and the
 *    sub-call caches drop with it.
 *
 * Construction mirrors [MediaRepoStackHarness]'s invariants (real identity
 * machinery over an atomic session flow on Dispatchers.Unconfined, real
 * detail cluster over a relaxed client seam, one shared
 * [MediaRepositoryInternals]); the snapshot store and the two
 * eviction-facing collaborators are relaxed doubles — the suite observes the
 * ladders through them.
 */
class MediaFreshnessLedgerTest {

    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val authClient: AuthApiClient = mockk(relaxed = true)
    private val apiClient: LibraryApiClient = mockk(relaxed = true)
    private val episodeCatalogue: EpisodeCatalogue = mockk(relaxed = true)
    private val homeSectionsCachePort: HomeSectionsCachePort = mockk(relaxed = true)
    private val homeSnapshotStore: HomeSectionsSnapshotStore = mockk(relaxed = true)

    private val fakeTimeSource = FakeTimeSource()

    private lateinit var ledger: MediaFreshnessLedger
    private lateinit var internals: MediaRepositoryInternals
    private lateinit var homeSession: HomeSession

    @kotlin.test.BeforeTest
    fun setup() {
        every { authClient.session } returns sessionFlow
        homeSession = HomeSession(
            authClient,
            // Unconfined: the session assignment drives the classifier AND
            // the registry reactions synchronously on the setter's stack.
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        val sessionCacheRegistry = SessionCacheRegistry(
            homeSession,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        internals = MediaRepositoryInternals(apiClient, homeSession, sessionCacheRegistry)
        ledger = MediaFreshnessLedger(
            homeSession = homeSession,
            sessionCacheRegistry = sessionCacheRegistry,
            homeSnapshotStore = homeSnapshotStore,
            episodeCatalogue = episodeCatalogue,
            internals = internals,
            homeSectionsCachePort = homeSectionsCachePort,
            timeSource = fakeTimeSource,
        )
    }

    private fun signIn(serverId: String, userId: String) {
        sessionFlow.value = ActiveSession(serverInfo(serverId), userInfo(userId))
    }

    // ── Spec registry completeness / wholesale drop ────────────────────────

    @Test
    fun `wholesale drop clears every spec in the registry`() = runBlocking {
        // Each simple cache concept is populated through its ledger read and
        // must REFETCH after invalidateCaches — a concept missing from the
        // registry list would keep serving its cache below (the fetch-count
        // assertion is the completeness pin).
        signIn("server-1", "user-A")
        var foldersFetches = 0
        var genresFetches = 0
        var studiosFetches = 0
        var tagsFetches = 0
        var latestFetches = 0
        var collectionFetches = 0
        var photoUrlFetches = 0

        suspend fun populateAll() {
            ledger.libraryFolders(force = false) { foldersFetches++; Result.success(emptyList()) }
            ledger.genres(parentId = null, force = false) { genresFetches++; Result.success(emptyList()) }
            ledger.studios(parentId = null) { studiosFetches++; Result.success(emptyList()) }
            ledger.tags(parentId = null) { tagsFetches++; Result.success(emptyList()) }
            ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
            ledger.collectionPages("col-1", 0, 20, force = false) { collectionFetches++; Result.success(page()) }
            ledger.photoFolderChildUrls("folder-1") { photoUrlFetches++; emptyList() }
        }
        populateAll()
        assertEquals(1, foldersFetches)
        assertEquals(1, genresFetches)
        assertEquals(1, studiosFetches)
        assertEquals(1, tagsFetches)
        assertEquals(1, latestFetches)
        assertEquals(1, collectionFetches)
        assertEquals(1, photoUrlFetches)

        ledger.invalidateCaches()

        populateAll()
        assertEquals(2, foldersFetches, "folders spec missing from the WHOLESALE ladder")
        assertEquals(2, genresFetches, "genres spec missing from the WHOLESALE ladder")
        assertEquals(2, studiosFetches, "studios spec missing from the WHOLESALE ladder")
        assertEquals(2, tagsFetches, "tags spec missing from the WHOLESALE ladder")
        assertEquals(2, latestFetches, "latest-media spec missing from the WHOLESALE ladder")
        assertEquals(2, collectionFetches, "collection-items spec missing from the WHOLESALE ladder")
        assertEquals(2, photoUrlFetches, "photo-urls spec missing from the WHOLESALE ladder")

        // The wholesale drop reaches past the registry: the catalogue's
        // snapshot store and the network layer's home sub-call caches.
        verify(exactly = 1) { episodeCatalogue.invalidateAll() }
        verify(exactly = 1) { homeSectionsCachePort.invalidateSubcallCaches() }
    }

    @Test
    fun `reads serve from cache between wholesale drops`() = runBlocking {
        signIn("server-1", "user-A")
        var foldersFetches = 0
        repeat(3) {
            ledger.libraryFolders(force = false) { foldersFetches++; Result.success(emptyList()) }
        }
        assertEquals(1, foldersFetches)
    }

    // ── Stale-group arming per group ───────────────────────────────────────

    @Test
    fun `a user-data write arms the gap riders but not home sections`() = runBlocking {
        signIn("server-1", "user-A")
        var homeFetches = 0
        var albumFetches = 0
        var collectionFetches = 0

        // Populate every group.
        ledger.homeSections("key", force = false) { homeFetches++; homeResult() }
        ledger.albumTracks(force = false) { albumFetches++; Result.success(emptyList<MediaItem>()) }
        ledger.collectionPages("col-1", 0, 20, force = false) { collectionFetches++; Result.success(page()) }
        assertEquals(1, homeFetches)

        // The unconditional user-data-write channel: riders only.
        ledger.invalidateForUserDataChange("item-1")

        // Home was NOT armed: the non-forced read is a cache hit (no fetch).
        ledger.homeSections("key", force = false) { homeFetches++; homeResult() }
        assertEquals(1, homeFetches, "home must not arm on the unconditional channel")
        // The riders armed: each next non-forced read refetches.
        ledger.albumTracks(force = false) { albumFetches++; Result.success(emptyList<MediaItem>()) }
        assertEquals(2, albumFetches, "album-tracks rider must arm on the unconditional channel")
        ledger.collectionPages("col-1", 0, 20, force = false) { collectionFetches++; Result.success(page()) }
        assertEquals(2, collectionFetches, "collection-items rider must arm on the unconditional channel")
    }

    @Test
    fun `a confirmed write arms every group - home included`() = runBlocking {
        signIn("server-1", "user-A")
        var homeFetches = 0
        var albumFetches = 0

        ledger.homeSections("key", force = false) { homeFetches++; homeResult() }
        ledger.albumTracks(force = false) { albumFetches++; Result.success(emptyList<MediaItem>()) }
        assertEquals(1, homeFetches)

        ledger.armStaleReadGroupsForConfirmedWrite()

        ledger.homeSections("key", force = false) { homeFetches++; homeResult() }
        assertEquals(2, homeFetches, "confirmed write must arm home sections")
        ledger.albumTracks(force = false) { albumFetches++; Result.success(emptyList<MediaItem>()) }
        assertEquals(2, albumFetches, "confirmed write must satisfy the riders' channel too")
    }

    @Test
    fun `the staleness marker is one-shot per arm`() = runBlocking {
        signIn("server-1", "user-A")
        var homeFetches = 0
        ledger.homeSections("key", force = false) { homeFetches++; homeResult() } // populate
        ledger.armStaleReadGroupsForConfirmedWrite()
        ledger.homeSections("key", force = false) { homeFetches++; homeResult() } // consumes, refetches
        ledger.homeSections("key", force = false) { homeFetches++; homeResult() } // marker spent: cached
        assertEquals(2, homeFetches, "the marker must arm exactly one forced read")
    }

    @Test
    fun `an identity transition resets the armed markers and clears the previous identity's SWR rows`() = runBlocking {
        signIn("server-1", "user-A")
        ledger.armStaleReadGroupsForConfirmedWrite()

        signIn("server-1", "user-B") // Unconfined: the transition chain is synchronous

        val flags = mutableListOf<Boolean>()
        ledger.homeSections("key", force = false) { flags += it; homeResult() }
        assertEquals(listOf(false), flags, "the next user's first read must not be forced")
        coVerify(exactly = 1) { homeSnapshotStore.clearIdentity("server-1", "user-A") }
    }

    // ── Write-generation funnel ────────────────────────────────────────────

    @Test
    fun `bump after the operation drops the assembled payload - the next read refetches`() = runBlocking {
        signIn("server-1", "user-A")
        var fetches = 0
        ledger.homeSections("key", force = false) { fetches++; homeResult() }
        ledger.homeSections("key", force = false) { fetches++; homeResult() }
        assertEquals(1, fetches, "within the TTL the assembled payload serves from cache")

        // The row-mutation paths' post-operation bump: the funnel pairs the
        // generation bump with the assembled-payload drop.
        ledger.bumpHomeWriteGeneration()

        ledger.homeSections("key", force = false) { fetches++; homeResult() }
        assertEquals(2, fetches, "the bumped payload must not be replayed")

        ledger.homeSections("key", force = false) { fetches++; homeResult() }
        assertEquals(2, fetches, "nothing bumped after the operation: the settled payload pins")
    }

    @Test
    fun `a home fetch in flight across a bump is returned but never pinned`() = runTest {
        signIn("server-1", "user-A")
        val fetchGate = CompletableDeferred<Result<HomeSectionsResult>>()
        val raced = async { ledger.homeSections("key", force = false) { fetchGate.await() } }
        runCurrent() // the flight captured its epoch and parks on the gate

        ledger.bumpHomeWriteGeneration()
        fetchGate.complete(homeResult())
        assertTrue(raced.await().isSuccess, "the raced fetch still returns its result to its caller")

        var fetches = 0
        ledger.homeSections("key", force = false) { fetches++; homeResult() }
        assertEquals(1, fetches, "the raced write was vetoed: the next read refetches")
    }

    // ── Composite eviction fan-out ─────────────────────────────────────────

    @Test
    fun `the composite eviction drops the item, the USER_DATA group and the subcall caches, and resolves the series`() = runBlocking {
        signIn("server-1", "user-A")
        // Seed the item's cached detail (an episode of series-1): the
        // series-resolution inside the eviction discovers the parent from it.
        coEvery { apiClient.getMediaDetail("episode-1") } returns Result.success(
            MediaDetail(item = episodeItem("episode-1", seriesId = "series-1")),
        )
        internals.detailCaches.detail("episode-1", force = false)

        var latestFetches = 0
        var genresFetches = 0
        var albumFetches = 0
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        ledger.genres(parentId = null, force = false) { genresFetches++; Result.success(emptyList()) }
        ledger.albumTracks(force = false) { albumFetches++; Result.success(emptyList<MediaItem>()) }

        ledger.withUserDataMutationCacheInvalidation("episode-1") { Result.success(Unit) }

        // Series resolution: the parent series' catalogue snapshot drops.
        verify(exactly = 1) { episodeCatalogue.invalidateSeries("series-1") }
        // The network layer's home sub-call caches drop with the write.
        verify(exactly = 1) { homeSectionsCachePort.invalidateSubcallCaches() }

        // USER_DATA group member: whole-cache drop → refetch.
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        assertEquals(2, latestFetches, "the USER_DATA group member must drop")
        // Non-member: still cached.
        ledger.genres(parentId = null, force = false) { genresFetches++; Result.success(emptyList()) }
        assertEquals(1, genresFetches, "a non-USER_DATA spec must not drop on a user-data write")
        // The album-tracks gap rider armed: the next non-forced read refetches.
        ledger.albumTracks(force = false) { albumFetches++; Result.success(emptyList<MediaItem>()) }
        assertEquals(2, albumFetches, "the gap rider must arm on the mutation bracket")
    }

    @Test
    fun `the eviction runs on BOTH outcomes of the mutation`() = runBlocking {
        signIn("server-1", "user-A")
        var latestFetches = 0
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }

        ledger.withUserDataMutationCacheInvalidation("item-1") {
            Result.failure<Unit>(RuntimeException("write failed"))
        }

        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        assertEquals(2, latestFetches, "the finally-armed eviction covers a failed write too")
    }

    // ── invalidateFor type table ───────────────────────────────────────────

    @Test
    fun `invalidateFor on a series drops its detail entry and its catalogue`() = runBlocking {
        signIn("server-1", "user-A")
        coEvery { apiClient.getMediaDetail("series-1") } returns Result.success(
            MediaDetail(item = seriesItem("series-1")),
        )
        internals.detailCaches.detail("series-1", force = false)

        ledger.invalidateFor(MediaDetail(item = seriesItem("series-1")))

        verify(exactly = 1) { episodeCatalogue.invalidateSeries("series-1") }
        // The detail entry dropped: the re-read refetches.
        internals.detailCaches.detail("series-1", force = false)
        coVerify(exactly = 2) { apiClient.getMediaDetail("series-1") }
    }

    @Test
    fun `invalidateFor on an episode drops the parent series catalogue`() = runBlocking {
        ledger.invalidateFor(MediaDetail(item = episodeItem("e1", seriesId = "series-1")))
        verify(exactly = 1) { episodeCatalogue.invalidateSeries("series-1") }
    }

    @Test
    fun `invalidateFor on an album runs the composite eviction but arms no gap marker`() = runBlocking {
        signIn("server-1", "user-A")
        var latestFetches = 0
        var collectionFetches = 0
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        ledger.collectionPages("col-1", 0, 20, force = false) { collectionFetches++; Result.success(page()) }

        ledger.invalidateFor(MediaDetail(item = MediaItem(id = "album-1", name = "Album", mediaType = MediaType.ALBUM)))

        // The ALBUM branch rides the composite eviction (the USER_DATA
        // whole-cache drops, the subcall caches) but arms no marker — a
        // forced detail read is not a user-data write. The collection page
        // was NOT evicted by the album branch, and the next read must not be
        // forced either (no second fetch).
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        assertEquals(2, latestFetches)
        ledger.collectionPages("col-1", 0, 20, force = false) { collectionFetches++; Result.success(page()) }
        assertEquals(1, collectionFetches, "the ALBUM branch must not arm or evict collection pages")
    }

    @Test
    fun `invalidateFor on a collection drops the collection's page family only`() = runBlocking {
        signIn("server-1", "user-A")
        var latestFetches = 0
        var pageFetches = 0
        var page2Fetches = 0
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        ledger.collectionPages("col-1", 0, 20, force = false) { pageFetches++; Result.success(page()) }
        ledger.collectionPages("col-1", 20, 20, force = false) { page2Fetches++; Result.success(page()) }

        ledger.invalidateFor(MediaDetail(item = MediaItem(id = "col-1", name = "Box", mediaType = MediaType.COLLECTION)))

        // The prefix evict drops BOTH pages; the unrelated spec stays cached.
        ledger.collectionPages("col-1", 0, 20, force = false) { pageFetches++; Result.success(page()) }
        ledger.collectionPages("col-1", 20, 20, force = false) { page2Fetches++; Result.success(page()) }
        assertEquals(2, pageFetches)
        assertEquals(2, page2Fetches)
        ledger.latestMedia("folder-1", 20) { latestFetches++; Result.success(emptyList()) }
        assertEquals(1, latestFetches, "the COLLECTION branch must not touch the USER_DATA group")
    }

    @Test
    fun `invalidateFor on a plain item touches nothing`() = runBlocking {
        ledger.invalidateFor(MediaDetail(item = MediaItem(id = "movie-1", name = "Movie", mediaType = MediaType.MOVIE)))
        verify(exactly = 0) { episodeCatalogue.invalidateSeries(any()) }
        verify(exactly = 0) { homeSectionsCachePort.invalidateSubcallCaches() }
    }

    // ── Collection prefix-evict isolation ──────────────────────────────────

    @Test
    fun `one collection's evict never drops another whose id extends it`() = runBlocking {
        signIn("server-1", "user-A")
        var col12Fetches = 0
        var col123Fetches = 0
        ledger.collectionPages("12", 0, 20, force = false) { col12Fetches++; Result.success(page()) }
        ledger.collectionPages("123", 0, 20, force = false) { col123Fetches++; Result.success(page()) }

        ledger.invalidateCollectionItems("12")

        ledger.collectionPages("12", 0, 20, force = false) { col12Fetches++; Result.success(page()) }
        ledger.collectionPages("123", 0, 20, force = false) { col123Fetches++; Result.success(page()) }
        assertEquals(2, col12Fetches)
        assertEquals(1, col123Fetches, "the trailing-underscore prefix must not match the extended id")
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private fun homeResult() = Result.success(HomeSectionsResult(sections = emptyList()))

    private fun page() = SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)

    private fun seriesItem(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.SERIES)

    private fun episodeItem(id: String, seriesId: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        seriesId = seriesId,
    )

    private fun userInfo(id: String) = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token",
        serverId = "server-1",
        isAdmin = false,
        maxParentalAgeRating = null,
        primaryImageTag = null,
        enabledFolderIds = emptyList(),
    )

    private fun serverInfo(id: String) = ServerInfo(
        id = id,
        name = "server-$id",
        address = "https://example.com",
        userId = null,
        accessToken = null,
    )
}
