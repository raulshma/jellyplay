package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.UserDataChange
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the PERSISTED halves of the home-sections pipeline that
 * [MediaRepositoryHomeSectionsCacheTest] (in-memory TTL, identity keying,
 * SWR staleness ceiling) leaves open:
 *
 *  1. the SWR snapshot persist ([MediaRepositoryImpl.persistHomeSectionsSnapshot])
 *     — fetch-path-only writes, and the 60s byte-identical dedup window that
 *     keeps the ~1/min foreground refresh from re-encoding + rewriting a
 *     several-hundred-KB payload that did not change (a rewrite would also
 *     slide `fetchedAt` forward, which the 24h ceiling reads);
 *  2. [MediaRepositoryImpl.getOfflineHomeLayout] — the offline home's
 *     key-agnostic layout mirror with NO freshness ceiling (staleness only
 *     costs order/titles offline, never content);
 *  3. the identity-transition reaction — a user/server switch clears the
 *     PREVIOUS identity's SWR rows (privacy: the just-logged-out user's home
 *     payload must not cold-open for the next user), while a first sign-in
 *     clears nothing;
 *  4. [MediaRepositoryImpl.notifyUserDataChanged] — the synthetic push the
 *     offline outbox drain uses to refresh open screens without a WS echo:
 *     distinct item ids under the current identity, and a silent no-op for
 *     an empty list or a missing identity.
 *
 * The DAO mock is backed by an in-memory map so the dedup path observes the
 * rows it itself persisted (a plain relaxed mock would answer `get` with
 * null and never exercise the window).
 */
class MediaRepositoryHomeSectionsSwrPersistTest {

    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)

    /** In-memory stand-in for the Room table, keyed like the DAO's PK. */
    private val storedRows = mutableMapOf<Triple<String, String, String>, HomeSectionCacheEntity>()
    private val clearedIdentities = mutableListOf<Pair<String, String>>()
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk()

    private val fakeTimeSource = FakeTimeSource()

    init {
        coEvery { homeSectionCacheDao.get(any(), any(), any()) } answers {
            storedRows[Triple(firstArg(), secondArg(), thirdArg())]
        }
        coEvery { homeSectionCacheDao.getLatestForIdentity(any(), any()) } answers {
            storedRows.values
                .filter { it.serverId == firstArg<String>() && it.userId == secondArg<String>() }
                .maxByOrNull { it.fetchedAt }
        }
        coEvery { homeSectionCacheDao.upsert(any()) } answers {
            val entity = firstArg<HomeSectionCacheEntity>()
            storedRows[Triple(entity.serverId, entity.userId, entity.cacheKey)] = entity
        }
        coEvery { homeSectionCacheDao.clearForIdentity(any(), any()) } answers {
            clearedIdentities += firstArg<String>() to secondArg<String>()
            storedRows.keys.removeAll { (s, u, _) -> s == firstArg<String>() && u == secondArg<String>() }
        }
    }

    private fun buildRepository(realtimeChannel: UserDataRealtimeChannel = mockk {
        every { changes } returns emptyFlow()
    }): MediaRepositoryImpl {
        every { apiClient.session } returns sessionFlow
        val playedStateSync: PlayedStateSync = mockk(relaxed = true)
        val offlineRepository: OfflineRepository = mockk(relaxed = true)
        val homeSession = HomeSession(
            apiClient,
            // Unconfined (not Dispatchers.Default): the session assignment then
            // drives HomeSession's classifier AND the registry's reactions
            // synchronously on the setter's stack, so signIn/switch* need no
            // settle delay — the observers cannot lag the assertion.
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
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
            realtimeChannel,
            fakeTimeSource,
            homeSession,
            sessionCacheRegistry,
        )
    }

    private fun homeResult(positionTicks: Long = 30_000_000L) = Result.success(
        HomeSectionsResult(
            sections = listOf(
                HomeSection(
                    id = "cw",
                    title = "Continue Watching",
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = listOf(
                        MediaItem(
                            id = "item-1",
                            name = "Item 1",
                            mediaType = MediaType.MOVIE,
                            playbackPositionTicks = positionTicks,
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * Observers run on Dispatchers.Unconfined (see [buildRepository]): the
     * session assignment processes the classifier and the registry reactions
     * synchronously, so there is nothing to wait for.
     */
    private suspend fun waitForCacheObserver() = Unit

    private suspend fun signIn(userId: String = "user-A", serverId: String = "server-1") {
        sessionFlow.value = ActiveSession(serverInfo(serverId), userInfo(userId, serverId))
        waitForCacheObserver()
    }

    private suspend fun switchUser(userId: String) {
        sessionFlow.value = ActiveSession(serverInfo("server-1"), userInfo(userId, "server-1"))
        waitForCacheObserver()
    }

    private suspend fun switchServer(serverId: String) {
        sessionFlow.value = ActiveSession(serverInfo(serverId), userInfo("user-A", serverId))
        waitForCacheObserver()
    }

    // ── SWR persist: write-on-fetch, never on a cache hit ───────────────────

    @Test
    fun `a fetched snapshot is persisted once and a cache hit does not rewrite it`() = runBlocking {
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()

        repository.getHomeSections(HomeSectionQuery()) // fetch → persist
        fakeTimeSource.nowMs += 5_000L // inside the memory TTL → cache hit
        repository.getHomeSections(HomeSectionQuery())

        val row = storedRows.values.single()
        assertEquals("user-A", row.userId)
        assertEquals("server-1", row.serverId)
        assertEquals(HomeSectionQuery().cacheKey(), row.cacheKey)
        // fetchedAt is the FIRST fetch's wall clock — the hit path never runs
        // onFetched, so it cannot slide the SWR staleness ceiling forward.
        assertEquals(1_000L, row.fetchedAt)
    }

    @Test
    fun `a byte-identical forced refresh inside the dedup window skips the rewrite`() = runBlocking {
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()

        repository.getHomeSections(HomeSectionQuery()) // persist #1
        fakeTimeSource.nowMs += 30_000L // 30s — inside the 60s dedup window
        repository.getHomeSections(HomeSectionQuery(), force = true) // refetch, identical

        // One row, still stamped with the FIRST fetch's fetchedAt — the dedup
        // window skipped both the encode-compare rewrite and the upsert.
        val row = storedRows.values.single()
        assertEquals(1_000L, row.fetchedAt)
        coVerify(exactly = 1) { homeSectionCacheDao.upsert(any()) }
    }

    @Test
    fun `an identical refresh past the dedup window rewrites the row`() = runBlocking {
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()

        repository.getHomeSections(HomeSectionQuery())
        fakeTimeSource.nowMs += 61_000L // past the window → the rewrite is due
        repository.getHomeSections(HomeSectionQuery(), force = true)

        val row = storedRows.values.single()
        assertEquals(1_000L + 61_000L, row.fetchedAt)
        coVerify(exactly = 2) { homeSectionCacheDao.upsert(any()) }
    }

    @Test
    fun `a changed payload persists immediately even inside the dedup window`() = runBlocking {
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns
            homeResult(positionTicks = 30_000_000L) andThen
            homeResult(positionTicks = 45_000_000L)

        repository.getHomeSections(HomeSectionQuery()) // position 30M
        fakeTimeSource.nowMs += 30_000L // inside the window…
        repository.getHomeSections(HomeSectionQuery(), force = true) // …but the content moved

        coVerify(exactly = 2) { homeSectionCacheDao.upsert(any()) }
        // The persisted payload decodes back to the NEW position — a user-data
        // change must never be held hostage by the dedup window.
        val decoded = repository.getCachedHomeSections(HomeSectionQuery())!!
        assertEquals(45_000_000L, decoded.sections.single().items.single().playbackPositionTicks)
    }

    @Test
    fun `a refresh without an identity persists nothing`() = runBlocking {
        // Signed out between fetch and persist (edge of the identity race):
        // currentIdentity() is null → the snapshot is dropped, not keyed to a
        // stale/unknown identity.
        val repository = buildRepository()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()

        // Signed out for the whole call: the fetch runs under the UNKNOWN
        // cache identity but the persist drops the snapshot (currentIdentity
        // is null) instead of keying it to a stale/unknown identity.
        repository.getHomeSections(HomeSectionQuery())

        assertTrue(storedRows.isEmpty())
    }

    // ── getOfflineHomeLayout: the offline home's layout mirror ──────────────

    @Test
    fun `getOfflineHomeLayout returns the latest persisted payload`() = runBlocking {
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()
        repository.getHomeSections(HomeSectionQuery())

        val layout = repository.getOfflineHomeLayout()

        assertNotNull(layout)
        assertEquals(listOf("item-1"), layout.sections.single().items.map { it.id })
    }

    @Test
    fun `getOfflineHomeLayout is key-agnostic across query changes`() = runBlocking {
        // A preference change while offline shifts the cacheKey; the offline
        // home still renders the last layout the user actually saw.
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()

        val customQuery = HomeSectionQuery(hiddenCwItemIds = setOf("x"))
        repository.getHomeSections(customQuery) // persists under the custom key

        val layout = repository.getOfflineHomeLayout()

        assertNotNull(layout)
        assertEquals("item-1", layout.sections.single().items.single().id)
    }

    @Test
    fun `getOfflineHomeLayout prefers the newest row across cache keys`() = runBlocking {
        // Two keys persist for the identity (a pref change shifted the
        // cacheKey mid-session): the offline home must render the payload of
        // the LATEST fetch, not whichever row the DAO happens to return —
        // stale content is exactly the "offline home shows pre-watch state"
        // regression class.
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns
            homeResult(positionTicks = 30_000_000L) andThen
            homeResult(positionTicks = 45_000_000L)

        repository.getHomeSections(HomeSectionQuery()) // default key, t=1000
        fakeTimeSource.nowMs += 30_000L
        repository.getHomeSections(HomeSectionQuery(hiddenCwItemIds = setOf("x")), force = true) // newer key

        val layout = repository.getOfflineHomeLayout()

        assertNotNull(layout)
        assertEquals(45_000_000L, layout.sections.single().items.single().playbackPositionTicks)
    }

    @Test
    fun `getOfflineHomeLayout has no freshness ceiling`() = runBlocking {
        // Deliberate contrast with getCachedHomeSections: a 25h-old row must
        // still render offline (staleness only costs section order/titles —
        // content is re-filtered against the offline store).
        val repository = buildRepository()
        signIn()
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()
        repository.getHomeSections(HomeSectionQuery())

        fakeTimeSource.nowMs += 25 * 60 * 60_000L
        assertNotNull(repository.getOfflineHomeLayout())
        assertNull(repository.getCachedHomeSections(HomeSectionQuery()))
    }

    @Test
    fun `getOfflineHomeLayout returns null without an identity or rows`() = runBlocking {
        val repository = buildRepository()

        assertNull(repository.getOfflineHomeLayout()) // signed out

        signIn()
        assertNull(repository.getOfflineHomeLayout()) // signed in, nothing persisted
    }

    @Test
    fun `getCachedHomeSections returns null without an identity`() = runBlocking {
        val repository = buildRepository()

        assertNull(repository.getCachedHomeSections(HomeSectionQuery()))
    }

    // ── Identity transitions clear the PREVIOUS identity's SWR rows ────────

    @Test
    fun `a user switch clears only the previous user's SWR rows`() = runBlocking {
        val repository = buildRepository()
        signIn("user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()
        repository.getHomeSections(HomeSectionQuery()) // user-A row persisted

        switchUser("user-B")

        assertEquals(listOf("server-1" to "user-A"), clearedIdentities)
        assertTrue(storedRows.isEmpty(), "user-A's home payload must not cold-open for user-B")
    }

    @Test
    fun `a server switch clears only the previous server's SWR rows`() = runBlocking {
        val repository = buildRepository()
        signIn("user-A", serverId = "server-1")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()
        repository.getHomeSections(HomeSectionQuery())

        switchServer("server-2")

        assertEquals(listOf("server-1" to "user-A"), clearedIdentities)
    }

    @Test
    fun `signing out clears the logged-out identity's rows`() = runBlocking {
        val repository = buildRepository()
        signIn("user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult()
        repository.getHomeSections(HomeSectionQuery())

        sessionFlow.value = null
        waitForCacheObserver()

        assertEquals(listOf("server-1" to "user-A"), clearedIdentities)
    }

    @Test
    fun `a first sign-in clears nothing`() = runBlocking {
        // SignedIn carries no previous identity — clearing would be a no-op
        // keyed against nothing, and must not fire for the new user either.
        val repository = buildRepository()

        signIn("user-A")

        assertTrue(clearedIdentities.isEmpty())
    }

    // ── notifyUserDataChanged: the synthetic WS-equivalent push ────────────

    @Test
    fun `notifyUserDataChanged emits distinct item ids for the current identity`() = runBlocking {
        val repository = buildRepository()
        signIn("user-A")
        val received = mutableListOf<UserDataChange>()
        // UNDISPATCHED start + settle delay: the synthetic flow has replay=0,
        // and merge() subscribes its upstream flows via dispatched child
        // coroutines — the notify must not fire before that subscription
        // lands, or the emission is dropped.
        val collector = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            repository.userDataChanges.collect { received += it }
        }
        delay(150)

        repository.notifyUserDataChanged(listOf("item-1", "item-1", "item-2"))
        delay(150) // let the shared-flow emission reach the collector

        assertEquals(listOf(UserDataChange(userId = "user-A", itemIds = listOf("item-1", "item-2"))), received)
        collector.cancel()
    }

    @Test
    fun `notifyUserDataChanged with an empty list emits nothing`() = runBlocking {
        val repository = buildRepository()
        signIn("user-A")
        val received = mutableListOf<UserDataChange>()
        val collector = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            repository.userDataChanges.collect { received += it }
        }
        delay(150) // subscription must land first or the test proves nothing

        repository.notifyUserDataChanged(emptyList())
        delay(150)

        assertTrue(received.isEmpty())
        collector.cancel()
    }

    @Test
    fun `notifyUserDataChanged without an identity emits nothing`() = runBlocking {
        // Pre-login: emitting would risk refreshing another account's screens
        // on a stale collector — the guard drops the push entirely.
        val repository = buildRepository()
        val received = mutableListOf<UserDataChange>()
        val collector = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            repository.userDataChanges.collect { received += it }
        }
        delay(150) // subscription must land first or the test proves nothing

        repository.notifyUserDataChanged(listOf("item-1"))
        delay(150)

        assertTrue(received.isEmpty())
        collector.cancel()
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun userInfo(id: String, serverId: String = "server-1") = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token",
        serverId = serverId,
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
     * Same shape as MediaRepositoryHomeSectionsCacheTest's fake: one clock
     * drives the in-memory TTL (monotonic read) AND the SWR wall-clock reads
     * (fetchedAt, the 24h ceiling). Starts at t=1000; tests advance it.
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
