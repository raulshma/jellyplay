package com.raulshma.jellyplay.core.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
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
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.data.session.HomeSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The direct suite of [HomeSectionsSnapshotStore] — the deep owner of the
 * PERSISTED half of the home pipeline (extracted from `MediaRepositoryImpl`;
 * the pins below moved here from `MediaRepositoryHomeSectionsSwrPersistTest`
 * and `MediaRepositoryHomeSectionsCacheTest`, whose remnant suites keep only
 * the REPO-level choreography: the fetch-path-only `onFetched` hook, the
 * identity-transition routing and the SWR layering).
 *
 * Unlike the repo suites' DAO mocks, this suite runs against a REAL
 * in-memory Room database (the `SearchHistoryRepositoryImplTest` /
 * `HomeSectionCacheDaoTest` harness: bundled JVM driver, no Robolectric), so
 * the dedup path observes the rows it itself persisted through the real
 * query/upsert SQL.
 *
 * Pins, per the store's contract ownership:
 *  1. the 60s dedup window — an identical in-window persist skips the
 *     rewrite (both the remembered-fingerprint cheap path and the exact
 *     encode+compare path when the process-restart shape has no remembered
 *     state), an out-of-window identical persist rewrites, a changed payload
 *     persists immediately, and a metadata-only change (fingerprint-equal,
 *     byte-different — the documented caveat) persists one refresh cycle
 *     later;
 *  2. `fetchedAt` semantics — the wall-clock stamp that [cached]'s 24h SWR
 *     ceiling reads, and the ceiling itself (null past it);
 *  3. the key-agnostic, UNCEILINGED offline layout mirror;
 *  4. identity scoping — no identity ⇒ persist is a no-op and reads are
 *     null; a previous identity's row is never served; [clearIdentity]
 *     drops only the named identity's rows, swallows a DAO failure, and a
 *     follow-up persist writes a fresh row despite remembered dedup state.
 */
class HomeSectionsSnapshotStoreTest {

    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val apiClient: AuthApiClient = mockk(relaxed = true)
    private val fakeTimeSource = FakeTimeSource()

    private lateinit var database: JellyPlayDatabase
    private lateinit var homeSectionCacheDao: HomeSectionCacheDao
    private lateinit var homeSession: HomeSession
    private lateinit var store: HomeSectionsSnapshotStore

    private val query = HomeSectionQuery()
    private val key = query.cacheKey()

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        homeSectionCacheDao = database.homeSectionCacheDao()
        every { apiClient.session } returns sessionFlow
        homeSession = HomeSession(
            apiClient,
            // Unconfined (the sibling repo suites' pattern): the session
            // assignment drives the classifier synchronously on the setter's
            // stack, so signIn needs no settle delay.
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        store = HomeSectionsSnapshotStore(homeSectionCacheDao, homeSession, fakeTimeSource)
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** Observers run on Dispatchers.Unconfined (see [setup]) — nothing to wait for. */
    private fun signIn(userId: String = "user-A", serverId: String = "server-1") {
        sessionFlow.value = ActiveSession(serverInfo(serverId), userInfo(userId, serverId))
    }

    private suspend fun rowFor(userId: String = "user-A", cacheKey: String = key) =
        homeSectionCacheDao.get("server-1", userId, cacheKey)

    private fun homeResult(
        positionTicks: Long = 30_000_000L,
        name: String = "Item 1",
    ) = HomeSectionsResult(
        sections = listOf(
            HomeSection(
                id = "cw",
                title = "Continue Watching",
                type = HomeSectionType.CONTINUE_WATCHING,
                items = listOf(
                    MediaItem(
                        id = "item-1",
                        name = name,
                        mediaType = MediaType.MOVIE,
                        playbackPositionTicks = positionTicks,
                    ),
                ),
            ),
        ),
    )

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

    // ── the 60s dedup window ─────────────────────────────────────────────────

    @Test
    fun `an identical persist inside the dedup window skips the rewrite`() = runTest {
        signIn()
        store.persist(key, homeResult()) // t=1000, remembered fingerprint
        fakeTimeSource.nowMs += 30_000L // inside the 60s window

        store.persist(key, homeResult()) // byte-identical → cheap-path skip

        // One row, still stamped with the FIRST persist's fetchedAt — the
        // dedup window skipped the rewrite (a rewrite would slide fetchedAt).
        assertEquals(1_000L, rowFor()!!.fetchedAt)
    }

    @Test
    fun `a byte-identical persist with no remembered dedup state still skips the rewrite`() = runTest {
        // The process-restart shape: the exact encode+compare path (a miss on
        // the remembered-fingerprint cheap path must still skip the
        // byte-identical rewrite). A NEW store instance over the same rows
        // has no dedup memory — like a fresh process after a reboot.
        signIn()
        store.persist(key, homeResult())
        fakeTimeSource.nowMs += 30_000L

        val restartedStore = HomeSectionsSnapshotStore(homeSectionCacheDao, homeSession, fakeTimeSource)
        restartedStore.persist(key, homeResult())

        assertEquals(1_000L, rowFor()!!.fetchedAt)
    }

    @Test
    fun `an identical persist past the dedup window rewrites the row`() = runTest {
        signIn()
        store.persist(key, homeResult())
        fakeTimeSource.nowMs += 61_000L // past the window → the rewrite is due

        store.persist(key, homeResult())

        assertEquals(62_000L, rowFor()!!.fetchedAt)
    }

    @Test
    fun `a changed payload persists immediately even inside the dedup window`() = runTest {
        signIn()
        store.persist(key, homeResult(positionTicks = 30_000_000L))
        fakeTimeSource.nowMs += 30_000L // inside the window…

        store.persist(key, homeResult(positionTicks = 45_000_000L)) // …but the content moved

        // The persisted payload decodes back to the NEW position — a user-data
        // change must never be held hostage by the dedup window.
        val decoded = store.cached(query)!!
        assertEquals(45_000_000L, decoded.sections.single().items.single().playbackPositionTicks)
    }

    @Test
    fun `a metadata-only change inside the window persists one refresh cycle later`() = runTest {
        // The documented caveat (see [HomeSnapshotFingerprint]): `name` is
        // NOT fingerprinted, so the in-window fingerprint match skips even
        // though the bytes differ — the change lands on the NEXT persist past
        // the window. Never inventing that delay for fingerprinted fields is
        // `a changed payload persists immediately` above; this pins the
        // metadata-only half of the trade.
        signIn()
        store.persist(key, homeResult(name = "Item 1"))
        fakeTimeSource.nowMs += 30_000L
        store.persist(key, homeResult(name = "Renamed")) // fingerprint equal → skipped

        val skipped = rowFor()!!
        assertEquals(1_000L, skipped.fetchedAt)
        assertEquals("Item 1", store.cached(query)!!.sections.single().items.single().name)

        fakeTimeSource.nowMs += 31_000L // past the window → the rewrite is due
        store.persist(key, homeResult(name = "Renamed"))

        assertEquals("Renamed", store.cached(query)!!.sections.single().items.single().name)
    }

    // ── fetchedAt semantics + the SWR staleness ceiling ──────────────────────

    @Test
    fun `persist stamps fetchedAt with the wall-clock read`() = runTest {
        signIn()
        fakeTimeSource.nowMs = 1_800_000_000_000L

        store.persist(key, homeResult())

        // fetchedAt is load-bearing: cached() reads it against
        // HomeFreshness's 24h SWR staleness ceiling.
        assertEquals(1_800_000_000_000L, rowFor()!!.fetchedAt)
    }

    @Test
    fun `cached serves a fresh snapshot and returns null past the 24h SWR ceiling`() = runTest {
        signIn()
        store.persist(key, homeResult(positionTicks = 30_000_000L))

        val fresh = store.cached(query)
        assertNotNull(fresh)
        assertEquals(30_000_000L, fresh.sections.single().items.single().playbackPositionTicks)

        // A 25h-old snapshot must not instant-paint on cold open — the
        // ceiling turns it into a miss so the UI shows a spinner and the
        // normal refresh re-persists.
        fakeTimeSource.nowMs += 25 * 60 * 60_000L
        assertNull(store.cached(query))
    }

    // ── the offline layout mirror ────────────────────────────────────────────

    @Test
    fun `offlineLayout is key-agnostic and prefers the newest row across cache keys`() = runTest {
        // A preference change while offline shifts the cacheKey; the offline
        // home still renders the layout the user last saw (issue #147).
        signIn()
        store.persist(key, homeResult(positionTicks = 30_000_000L)) // t=1000
        fakeTimeSource.nowMs += 30_000L
        val customQuery = HomeSectionQuery(hiddenCwItemIds = setOf("x"))
        store.persist(customQuery.cacheKey(), homeResult(positionTicks = 45_000_000L)) // newer

        val layout = store.offlineLayout()

        assertNotNull(layout)
        assertEquals(45_000_000L, layout.sections.single().items.single().playbackPositionTicks)
        // The SWR read stays key-scoped: the default key still serves its own row.
        assertEquals(
            30_000_000L,
            store.cached(query)!!.sections.single().items.single().playbackPositionTicks,
        )
    }

    @Test
    fun `offlineLayout has no freshness ceiling`() = runTest {
        // Deliberate contrast with cached(): a 25h-old row must still render
        // offline (staleness only costs section order/titles — content is
        // re-filtered against the offline store).
        signIn()
        store.persist(key, homeResult())
        fakeTimeSource.nowMs += 25 * 60 * 60_000L

        assertNotNull(store.offlineLayout())
        assertNull(store.cached(query))
    }

    // ── identity scoping ─────────────────────────────────────────────────────

    @Test
    fun `persist is a no-op and the reads are null without an identity`() = runTest {
        // Signed out: the persist drops the snapshot instead of keying it to
        // a stale/unknown identity.
        store.persist(key, homeResult())

        assertNull(store.cached(query))
        assertNull(store.offlineLayout())

        // Nothing was persisted anywhere: once signed in, the key still misses.
        signIn()
        assertNull(rowFor())
    }

    @Test
    fun `cached is scoped to the identity - a previous user's row is not served`() = runTest {
        signIn("user-A")
        store.persist(key, homeResult())

        signIn("user-B")

        assertNull(store.cached(query))
    }

    @Test
    fun `clearIdentity drops only the named identity's rows`() = runTest {
        signIn("user-A")
        store.persist(key, homeResult())
        signIn("user-B")
        store.persist(key, homeResult())

        store.clearIdentity("server-1", "user-A")

        assertNull(homeSectionCacheDao.get("server-1", "user-A", key))
        assertNotNull(homeSectionCacheDao.get("server-1", "user-B", key))
    }

    @Test
    fun `a persist after clearIdentity writes a fresh row despite remembered dedup state`() = runTest {
        // The cheap path requires an existing row; after the privacy clear
        // the dedup memory alone can never suppress the rewrite.
        signIn()
        store.persist(key, homeResult())
        store.clearIdentity("server-1", "user-A")
        fakeTimeSource.nowMs += 30_000L // inside the would-be window

        store.persist(key, homeResult())

        assertEquals(31_000L, rowFor()!!.fetchedAt)
    }

    @Test
    fun `clearIdentity swallows a DAO failure`() = runTest {
        // Failure is logged, not swallowed-into-a-crash: a throwing DAO must
        // not take down the identity-transition handler that calls this.
        val throwingDao: HomeSectionCacheDao = mockk {
            coEvery { clearForIdentity(any(), any()) } throws RuntimeException("disk on fire")
        }
        val storeOverThrowingDao = HomeSectionsSnapshotStore(throwingDao, homeSession, fakeTimeSource)

        storeOverThrowingDao.clearIdentity("server-1", "user-A") // must not throw
    }
}
