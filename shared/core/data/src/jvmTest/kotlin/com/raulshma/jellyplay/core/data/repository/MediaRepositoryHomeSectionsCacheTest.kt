package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.data.util.TimeSource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    /**
     * Controllable [TimeSource] for the SWR wall-clock check and the
     * in-memory TTL's monotonic clock — same shape as feature/home's
     * FakeTimeSource; kept local because core:data deliberately hosts no test
     * fakes (see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
