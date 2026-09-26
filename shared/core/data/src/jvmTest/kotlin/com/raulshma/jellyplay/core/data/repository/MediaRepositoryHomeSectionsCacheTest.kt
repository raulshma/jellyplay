package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.data.testutil.FakeTimeSource
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the in-memory home-sections cache in [MediaRepositoryImpl], which was
 * previously a hand-rolled `@Volatile` triple with zero test coverage and is
 * now a single-entry identity-keyed [com.raulshma.jellyplay.core.model.TtlCache].
 *
 * Port of the orphaned legacy `:core:data` suite of the same name onto the
 * shared module's 8-arg MediaRepositoryImpl constructor (the lyrics deps the
 * old wiring carried are gone — lyrics own LyricsRepositoryImpl now).
 *
 * The headline case is the cross-user leak that motivated refactor C9: a wrong
 * identity must be a guaranteed cache miss by construction. The cache-invalidation
 * observers run on `Dispatchers.Unconfined` (see [buildRepository]), so each
 * session assignment has processed the identity chain by the time it returns.
 *
 * Also covers the two freshness policies that had zero expiry coverage before
 * HomeFreshness: the 60s in-memory TTL and the 24h Room SWR staleness ceiling
 * (both via [FakeTimeSource]; the ceiling additionally stubs
 * `HomeSectionCacheEntity.fetchedAt`).
 */
class MediaRepositoryHomeSectionsCacheTest {

    // Atomic (server, user) session flow — what the shared HomeSession
    // identity detector consumes (see JellyfinApiEngine.session). One value per
    // identity step, replacing the separate server/user flows the repo's own
    // observer used to combine.
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true) {
        coEvery { get(any(), any(), any()) } returns null
    }

    /** Wall-clock fake behind the SWR staleness check; tests move [nowMs] across the 24h ceiling. */
    private val fakeTimeSource = FakeTimeSource()

    private fun buildRepository(): MediaRepositoryImpl {
        every { apiClient.session } returns sessionFlow
        val playedStateSync: PlayedStateSync = mockk(relaxed = true)
        val offlineRepository: OfflineRepository = mockk(relaxed = true)
        val homeSession = HomeSession(
            apiClient,
            // Unconfined (not Dispatchers.Default): the session assignment then
            // drives the classifier AND the registry reactions synchronously on
            // the setter's stack, so the identity chain the suite pins (session
            // emission → transition → cache drop) needs no settle delay.
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        // Real registry over the real HomeSession — same chain production runs,
        // minus the cross-thread settle window.
        val sessionCacheRegistry = SessionCacheRegistry(
            homeSession,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        val episodeCatalogue = EpisodeCatalogueImpl(
            apiClient,
            offlineRepository,
            homeSession,
            sessionCacheRegistry,
        )
        return MediaRepositoryImpl(
            apiClient,
            homeSectionCacheDao,
            playedStateSync,
            episodeCatalogue,
            mockk<UserDataRealtimeChannel>(relaxed = true),
            fakeTimeSource,
            homeSession,
            sessionCacheRegistry,
            // Facade split: the detail cluster now lives on the shared
            // internals holder (construction-only ctor re-point).
            MediaRepositoryInternals(apiClient, homeSession),
        )
    }

    private fun homeResult(tag: String) = Result.success(
        HomeSectionsResult(sections = listOf(homeSection(tag)))
    )

    private fun homeSection(tag: String) = mockk<HomeSection>(relaxed = true)

    /**
     * Observers run on Dispatchers.Unconfined (see [buildRepository]): the
     * session assignment processes the identity chain synchronously, so there
     * is nothing to wait for.
     */
    private suspend fun waitForCacheObserver() = Unit

    private suspend fun signIn(serverId: String, userId: String) {
        sessionFlow.value = ActiveSession(serverInfo(serverId), userInfo(userId))
        waitForCacheObserver()
    }

    private suspend fun switchUser(userId: String) {
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo(userId))
        waitForCacheObserver()
    }

    @Test
    fun `getHomeSections caches result on repeat calls`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery())
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 1) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getHomeSections re-fetches on a forced read`() = runBlocking {
        // Plan 08: the pull-to-refresh freshness lever is the force parameter
        // (the old global invalidateCaches knob is module-internal now; the
        // internal variant's wholesale coverage is asserted in
        // MediaRepositoryImplTest).
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery())
        repository.getHomeSections(HomeSectionQuery(), force = true)

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getHomeSections re-fetches after the internal wholesale invalidation`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery())
        repository.invalidateCaches()
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `rerollDiscoverRow returns the fresh items and commits the row memo`() = runBlocking {
        // The dice roll as one operation: the fresh fetch is returned to the
        // caller AND committed into the network layer's per-row memo (the
        // invalidate → fetch → seed ordering is the repository's), so the next
        // home fetch serves the rolled set instead of re-querying (re-rolling)
        // the server.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val rolledItems = listOf(mockk<MediaItem>(relaxed = true))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")
        coEvery { apiClient.getDiscoverRowItems(row) } returns Result.success(rolledItems)

        val result = repository.rerollDiscoverRow(row)

        assertEquals(rolledItems, result.getOrNull())
        coVerify(exactly = 1) { apiClient.invalidateDiscoverRowCache(row.id) }
        coVerify(exactly = 1) { apiClient.getDiscoverRowItems(row) }
        coVerify(exactly = 1) { apiClient.seedDiscoverRowCache(row, rolledItems) }
    }

    @Test
    fun `rerollDiscoverRow drops the cached home payload - the next ordinary read refetches`() = runBlocking {
        // "The roll survives the periodic refresh" at the repo layer: the
        // pre-roll assembled payload the reroll dropped cannot be replayed by
        // the next TTL-served read — that read refetches through the network
        // layer, which now serves the seeded row memo.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")
        coEvery { apiClient.getDiscoverRowItems(any()) } returns
            Result.success(listOf(mockk<MediaItem>(relaxed = true)))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        repository.getHomeSections(HomeSectionQuery())
        repository.rerollDiscoverRow(row)
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `a home fetch in flight across a reroll does not pin its pre-roll payload`() = runTest {
        // The epoch guard through the new operation: a getHomeSections already
        // on the wire when the reroll lands captured the pre-roll payloads; its
        // completion is returned to its caller but must not be written into the
        // in-memory cache, or the next TTL-served periodic read would replay
        // them and revert the on-screen roll. Not pinned ⇔ the next ordinary
        // read refetches (2 network calls, not 1).
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val fetchGate = CompletableDeferred<Result<HomeSectionsResult>>()
        coEvery { apiClient.getHomeSections(any(), any()) } coAnswers { fetchGate.await() }
        coEvery { apiClient.getDiscoverRowItems(any()) } returns
            Result.success(listOf(mockk<MediaItem>(relaxed = true)))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        val racedFetch = async { repository.getHomeSections(HomeSectionQuery()) }
        runCurrent() // the fetch captures its epoch and parks on the gate
        repository.rerollDiscoverRow(row)

        fetchGate.complete(homeResult("pre-roll"))
        assertTrue(racedFetch.await().isSuccess, "the raced fetch still returns its result to its caller")

        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("post-roll")
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `rerollDiscoverRow failure returns the failure without committing the memo`() = runBlocking {
        // A failed roll skips the commit: the network memo is untouched and
        // only the pre-fetch drop remains — the next home fetch re-queries the
        // row rather than replaying pre-roll items.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getDiscoverRowItems(any()) } returns
            Result.failure(RuntimeException("flaky"))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        val result = repository.rerollDiscoverRow(row)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { apiClient.invalidateDiscoverRowCache(row.id) }
        coVerify(exactly = 0) { apiClient.seedDiscoverRowCache(any(), any()) }
    }

    @Test
    fun `getHomeSections identity-keyed - user A cached result not served to user B`() = runBlocking {
        // The headline C9 test: a wrong identity must be a guaranteed miss by
        // construction, so the previous user's home payload can never surface
        // for the next user within the TTL window.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery()) // populates user-A entry

        // Switch to user B on the same server.
        switchUser("user-B")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("B")

        repository.getHomeSections(HomeSectionQuery())

        // Two distinct network fetches — user A's cached entry did NOT serve user B.
        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    /**
     * Lazy home staleness, the #157 contract (see MediaRepositoryImpl's
     * homeSectionsStale): a confirmed own-write (toggleFavorite / markPlayed /
     * markUnplayed / delivered STOP / outbox drain) announces through
     * [MediaRepository.notifyUserDataChanged], which does NOT eagerly clear
     * the cache — it arms a marker the next read consumes as a one-shot
     * force. Freshness of the old eager eviction (an unwatched row re-enters
     * Continue Watching within the TTL window, not after 60s of staleness)
     * without the blocking refetch while nobody is reading home.
     */
    @Test
    fun `a user-data announcement makes the next home read refetch within the TTL window`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery())
        repository.notifyUserDataChanged(listOf("item-1"))
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `a failed forced read re-arms the staleness marker for the next read`() = runBlocking {
        // The one-shot force is consumed BEFORE the fetch; if that fetch
        // fails (offline blip) the read produced nothing, and the marker
        // must come back — or the pre-announce cached payload would serve
        // until the next announce or the 60s TTL, exactly the window #157
        // exists to close.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        var fetchCalls = 0
        coEvery { apiClient.getHomeSections(any(), any()) } coAnswers {
            if (++fetchCalls == 1) {
                homeResult("A")
            } else {
                Result.failure(RuntimeException("offline blip"))
            }
        }

        repository.getHomeSections(HomeSectionQuery()) // populate the cache
        repository.notifyUserDataChanged(listOf("item-1")) // arm the marker
        val failed = repository.getHomeSections(HomeSectionQuery()) // consumes, fetch fails
        assertTrue(failed.isFailure)
        repository.getHomeSections(HomeSectionQuery()) // re-armed: refetches

        coVerify(exactly = 3) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `a staleness-consumed read passes force to the network layer`() = runBlocking {
        // The one-shot marker must propagate past the in-memory cache: the
        // fetch lambda hands its flag to the api client, whose sub-call
        // memo caches (Latest Media / Recommendations) only bypass on
        // force. A staleness read that refetched but passed force=false
        // would re-serve the pre-announce sub-call rows.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val forcedFlags = mutableListOf<Boolean>()
        coEvery { apiClient.getHomeSections(any(), any()) } answers {
            forcedFlags.add(secondArg())
            homeResult("A")
        }

        repository.getHomeSections(HomeSectionQuery()) // populate, force = false
        repository.notifyUserDataChanged(listOf("item-1")) // arm the marker
        repository.getHomeSections(HomeSectionQuery()) // consumes the marker

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
        assertEquals(listOf(false, true), forcedFlags)
    }

    @Test
    fun `a home read without an intervening announcement serves the cached result`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        val first = repository.getHomeSections(HomeSectionQuery())
        val second = repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 1) { apiClient.getHomeSections(any(), any()) }
        assertEquals(first.getOrNull(), second.getOrNull())
    }

    @Test
    fun `the staleness marker is one-shot - a second read without a new announcement serves cache`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery())
        repository.notifyUserDataChanged(listOf("item-1"))
        repository.getHomeSections(HomeSectionQuery()) // consumes the marker, refetches
        repository.getHomeSections(HomeSectionQuery()) // marker gone: cached

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `identity switch clears the armed staleness marker - the next user's first read is not forced`() = runBlocking {
        // The marker is armed by the PREVIOUS user's confirmed writes; the
        // media-identity-clear action must reset it, or it survives the
        // switch and burns the next user's first read as a force —
        // redundant work: the identity switch already dropped the cache, and
        // force also bypasses the network layer's sub-call caches. The
        // refetch itself still happens (identity-keyed miss); the flag
        // handed to the api is what distinguishes reset from stranded.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val forcedFlags = mutableListOf<Boolean>()
        coEvery { apiClient.getHomeSections(any(), any()) } answers {
            forcedFlags.add(secondArg())
            homeResult("A")
        }

        repository.getHomeSections(HomeSectionQuery()) // populate, force = false
        repository.notifyUserDataChanged(listOf("item-1")) // arm the marker
        switchUser("user-B")
        repository.getHomeSections(HomeSectionQuery()) // identity miss refetches anyway

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
        assertEquals(listOf(false, false), forcedFlags)
    }

    // The album-tracks / collection-items gap-group arming (the #157 rule
    // generalized) is pinned by MediaRepositoryImplTest's marker block —
    // same arming path (a flip funnels into notifyUserDataChanged), so
    // re-pinning it through the direct announce here would only duplicate.

    @Test
    fun `getHomeSections re-fetches once the 60s memory TTL expires`() = runBlocking {
        // The TTL expiry itself had zero coverage: walk the shared fake clock
        // (the TtlCache reads the injected [TimeSource], same as the SWR
        // ceiling) past HomeFreshness.REPO_MEMORY_TTL_MS between two calls.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        fakeTimeSource.nowMs = 1_000L

        repository.getHomeSections(HomeSectionQuery()) // cached at t=1000
        fakeTimeSource.nowMs += HomeFreshness.REPO_MEMORY_TTL_MS + 1_000L // 61s later
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getCachedHomeSections returns null when the Room snapshot is stale`() = runBlocking {
        // A 25h-old SWR row must not instant-paint on cold open — the 24h
        // ceiling (HomeFreshness.ROOM_SWR_STALE_MS) turns it into a miss so
        // the UI shows a spinner and the normal refresh re-persists.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        fakeTimeSource.nowMs = NOW_WALL_MS
        coEvery { homeSectionCacheDao.get(any(), any(), any()) } returns
            swrEntity(fetchedAt = NOW_WALL_MS - 25 * 60 * 60_000L)

        assertNull(repository.getCachedHomeSections(HomeSectionQuery()))
    }

    @Test
    fun `getCachedHomeSections returns payload when the Room snapshot is fresh`() = runBlocking {
        // 1h old — inside the ceiling: the cold open instant-paints from Room.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        fakeTimeSource.nowMs = NOW_WALL_MS
        coEvery { homeSectionCacheDao.get(any(), any(), any()) } returns
            swrEntity(fetchedAt = NOW_WALL_MS - 1 * 60 * 60_000L)

        val cached = repository.getCachedHomeSections(HomeSectionQuery())

        assertNotNull(cached)
        assertEquals(1, cached.sections.size)
    }

    /** Arbitrary fixed epoch the SWR tests measure fetchedAt against. */
    private companion object {
        const val NOW_WALL_MS = 1_800_000_000_000L
    }

    /** DAO-shaped SWR row with a real, encodable payload (the read path decodes it). */
    private fun swrEntity(fetchedAt: Long): HomeSectionCacheEntity {
        val payload = HomeSectionsResult(
            sections = listOf(
                HomeSection(
                    id = "cw",
                    title = "Continue Watching",
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = listOf(MediaItem(id = "item-1", name = "Item 1", mediaType = MediaType.MOVIE)),
                ),
            ),
        )
        return HomeSectionCacheEntity(
            serverId = "server-1",
            userId = "user-A",
            cacheKey = "irrelevant-get-is-stubbed-with-any",
            payloadJson = com.raulshma.jellyplay.core.database.Converters.encodeHomeSectionsResult(payload),
            fetchedAt = fetchedAt,
        )
    }

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
