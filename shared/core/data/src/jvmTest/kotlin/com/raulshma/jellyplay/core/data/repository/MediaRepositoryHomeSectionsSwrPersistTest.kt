package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.data.testutil.FakeTimeSource
import com.raulshma.jellyplay.core.model.ActiveSession
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the REPO-level choreography around the home-sections SWR pipeline —
 * the parts [HomeSectionsSnapshotStoreTest] (the extracted persisted half's
 * direct suite: dedup window, fingerprint cheap/encode paths, fetchedAt
 * semantics, identity-scoped clear, offline layout) cannot see:
 *
 *  1. the fetch-path-only persist hook — `getOrFetch`'s `onFetched` runs
 *     [HomeSectionsSnapshotStore.persist] after the in-memory put on the
 *     FETCH path only, never on a cache hit, so a hit cannot slide the
 *     persisted row's `fetchedAt` forward and defeat the 24h SWR staleness
 *     ceiling;
 *  2. the identity-transition reaction — a user/server switch or sign-out
 *     routes the transition's PREVIOUS identity into
 *     [HomeSectionsSnapshotStore.clearIdentity] (privacy: the just-logged-out
 *     user's home payload must not cold-open for the next user), while a
 *     first sign-in clears nothing;
 *  3. [MediaRepositoryImpl.notifyUserDataChanged] — the synthetic push the
 *     offline outbox drain uses to refresh open screens without a WS echo:
 *     distinct item ids under the current identity, and a silent no-op for
 *     an empty list or a missing identity.
 *
 * The DAO mock is backed by an in-memory map so the persist path observes
 * the rows it itself persisted (a plain relaxed mock would answer `get` with
 * null and never exercise the row state).
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
        // Snapshot-store extraction: the repo delegates the persisted half of
        // the home pipeline to this store (the same single the Koin graph
        // wires); the suite's pins stay end-to-end through the real store.
        val homeSnapshotStore = HomeSectionsSnapshotStore(
            homeSectionCacheDao,
            homeSession,
            fakeTimeSource,
        )
        return MediaRepositoryImpl(
            // One union mock covers both family seams (the JellyfinApiClient
            // mock implements each of them).
            apiClient,
            // The home cache-maintenance port (inert here — this suite pins
            // the persisted SWR half through the real store).
            mockk(relaxed = true),
            apiClient,
            homeSnapshotStore,
            playedStateSync,
            episodeCatalogue,
            realtimeChannel,
            fakeTimeSource,
            homeSession,
            sessionCacheRegistry,
            // Facade split: the detail cluster now lives on the shared
            // internals holder (construction-only ctor re-point).
            MediaRepositoryInternals(apiClient, homeSession),
            // The deepened createSyncPlayGroup's engine (inert here).
            mockk(relaxed = true),
        )
    }

    private fun homeResult() = Result.success(
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
                            playbackPositionTicks = 30_000_000L,
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

    // (The 60s dedup window's own pins — in-window skip, past-window rewrite,
    // immediate changed-payload persist, no-identity no-op — and the two
    // cold-open reads' pins moved to HomeSectionsSnapshotStoreTest, the
    // extracted store's direct suite.)

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
}
