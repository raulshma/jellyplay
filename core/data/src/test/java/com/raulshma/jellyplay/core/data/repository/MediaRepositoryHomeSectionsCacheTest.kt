package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers the in-memory home-sections cache in [MediaRepositoryImpl], which was
 * previously a hand-rolled `@Volatile` triple with zero test coverage and is
 * now a single-entry identity-keyed [TtlCache].
 *
 * The headline case is the cross-user leak that motivated refactor C9: a wrong
 * identity must be a guaranteed cache miss by construction. The cache-invalidation
 * observer runs on `Dispatchers.Default`, so tests use [runBlocking] + a short
 * [delay] to let the collector process each flow emission before asserting.
 *
 * Also covers the two freshness policies that had zero expiry coverage before
 * HomeFreshness: the 60s in-memory TTL (via the repo's [MediaRepositoryImpl.cacheClockMs]
 * test seam) and the 24h Room SWR staleness ceiling (via [FakeTimeSource] and
 * a DAO-stubbed `HomeSectionCacheEntity.fetchedAt`).
 */
class MediaRepositoryHomeSectionsCacheTest {

    private val serverFlow = MutableStateFlow<ServerInfo?>(null)
    private val userFlow = MutableStateFlow<UserInfo?>(null)
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true) {
        coEvery { get(any(), any(), any()) } returns null
    }

    /** Wall-clock fake behind the SWR staleness check; tests move [nowMs] across the 24h ceiling. */
    private val fakeTimeSource = FakeTimeSource()

    private fun buildRepository(): MediaRepositoryImpl {
        every { apiClient.currentServer } returns serverFlow
        every { apiClient.currentUser } returns userFlow
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        val lrcLibApi: LrcLibApi = mockk(relaxed = true)
        val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
        val playedStateSync: PlayedStateSync = mockk(relaxed = true)
        val offlineRepository: OfflineRepository = mockk(relaxed = true)
        val episodeCatalogue = com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl(
            apiClient,
            offlineRepository,
        )
        return MediaRepositoryImpl(
            apiClient,
            lrcLibApi,
            lyricsCacheDao,
            homeSectionCacheDao,
            networkMonitor,
            playedStateSync,
            episodeCatalogue,
            mockk<UserDataRealtimeChannel>(relaxed = true),
            fakeTimeSource,
        )
    }

    private fun homeResult(tag: String) = Result.success(
        HomeSectionsResult(sections = listOf(homeSection(tag)))
    )

    private fun homeSection(tag: String) = mockk<HomeSection>(relaxed = true)

    /** Waits long enough for the `Dispatchers.Default` collector to observe the latest emission. */
    private suspend fun waitForCacheObserver() {
        delay(150)
    }

    private suspend fun signIn(serverId: String, userId: String) {
        serverFlow.value = serverInfo(serverId)
        userFlow.value = userInfo(userId)
        waitForCacheObserver()
    }

    private suspend fun switchUser(userId: String) {
        userFlow.value = userInfo(userId)
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

    @Test
    fun `toggleFavorite invalidates the home-sections cache`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")
        coEvery { apiClient.toggleFavorite(any()) } returns Result.success(true)

        repository.getHomeSections(HomeSectionQuery())
        repository.toggleFavorite("item-1")
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `markPlayed invalidates the home-sections cache`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery())
        repository.markPlayed("item-1")
        repository.getHomeSections(HomeSectionQuery())

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getHomeSections re-fetches once the 60s memory TTL expires`() = runBlocking {
        // The TTL expiry itself had zero coverage: retarget the repo's cache
        // clock (the TtlCache normally reads SystemClock.elapsedRealtime) and
        // walk it past HomeFreshness.REPO_MEMORY_TTL_MS between two calls.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        var cacheNowMs = 0L
        repository.cacheClockMs = { cacheNowMs }

        repository.getHomeSections(HomeSectionQuery()) // cached at t=0
        cacheNowMs += HomeFreshness.REPO_MEMORY_TTL_MS + 1_000L // 61s later
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
        assertEquals(1, cached?.sections?.size)
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
     * Controllable [TimeSource] for the SWR wall-clock check — same shape as
     * feature/home's FakeTimeSource; kept local because core:data deliberately
     * hosts no test fakes (see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
