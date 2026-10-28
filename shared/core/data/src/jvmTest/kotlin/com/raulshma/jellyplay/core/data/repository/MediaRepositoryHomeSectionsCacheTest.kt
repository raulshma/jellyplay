package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.testfixtures.FakeTimeSource
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * Also covers the in-memory half of the freshness policy: the 60s TTL via
 * [FakeTimeSource] (the PERSISTED half's 24h Room SWR staleness ceiling is
 * pinned by HomeSectionsSnapshotStoreTest — the extracted store's direct
 * suite).
 */
class MediaRepositoryHomeSectionsCacheTest {

    /** The announcer leaf the build wires (tests announce through it directly). */
    private lateinit var announcer: UserDataAnnouncerImpl

    // Atomic (server, user) session flow — what the shared HomeSession
    // identity detector consumes (see JellyfinApiEngine.session). One value per
    // identity step, replacing the separate server/user flows the repo's own
    // observer used to combine.
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    // The library family seam (the home fetch surface the suite stubs); the
    // remaining ctor seams take the harness's inert defaults.
    private val apiClient: LibraryApiClient = mockk(relaxed = true)
    /**
     * The home cache-maintenance port (the complete roll operation, the
     * single-row refetch and the sub-call cache drop) — the network side of
     * the write/roll paths, so the reroll pins verify the repo's half against
     * it.
     */
    private val homeSectionsCachePort: HomeSectionsCachePort = mockk(relaxed = true)
    private val homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true) {
        coEvery { get(any(), any(), any()) } returns null
    }

    /** Wall-clock fake behind the SWR staleness check; tests move [nowMs] across the 24h ceiling. */
    private val fakeTimeSource = FakeTimeSource()

    private fun buildRepository(): MediaRepositoryImpl {
        val stack = mediaRepoStackFor(
            libraryApiClient = apiClient,
            sessionFlow = sessionFlow,
            // Unconfined (not the harness's Default): the session assignment
            // then drives the classifier AND the registry reactions
            // synchronously on the setter's stack, so the identity chain the
            // suite pins (session emission → transition → cache drop) needs
            // no settle delay.
            scopeDispatcher = Dispatchers.Unconfined,
            timeSource = fakeTimeSource,
            homeSectionCacheDao = homeSectionCacheDao,
            homeSectionsCachePort = homeSectionsCachePort,
        )
        announcer = stack.announcer
        return stack.repository
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

        repository.getHomeSections(HomeSectionQuery(), force = false)
        repository.getHomeSections(HomeSectionQuery(), force = false)

        coVerify(exactly = 1) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getHomeSections re-fetches on a forced read`() = runBlocking {
        // The pull-to-refresh freshness lever is the force parameter
        // (the old global invalidateCaches knob is module-internal now; the
        // internal variant's wholesale coverage is asserted in
        // MediaRepositoryImplTest).
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery(), force = false)
        repository.getHomeSections(HomeSectionQuery(), force = true)

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getHomeSections re-fetches after the internal wholesale invalidation`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery(), force = false)
        repository.invalidateCaches()
        repository.getHomeSections(HomeSectionQuery(), force = false)

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `rerollDiscoverRow returns the fresh items through the network operation`() = runBlocking {
        // The dice roll as one operation: the invalidate → fetch → seed
        // choreography lives INSIDE the network layer's op now (the fetcher
        // owns the row memo and its write guard — the commit itself is pinned
        // on the real stack by HomeFeedRealStackContractTest and in
        // core:network's fetcher suite). This side pins the routing and the
        // outcome: the fresh fetch result is returned to the caller verbatim.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val rolledItems = listOf(mockk<MediaItem>(relaxed = true))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")
        coEvery { homeSectionsCachePort.rerollDiscoverRow(row) } returns Result.success(rolledItems)

        val result = repository.rerollDiscoverRow(row)

        assertEquals(rolledItems, result.getOrNull())
        coVerify(exactly = 1) { homeSectionsCachePort.rerollDiscoverRow(row) }
    }

    @Test
    fun `rerollDiscoverRow drops the cached home payload - the next ordinary read refetches`() = runBlocking {
        // "The roll survives the periodic refresh" at the repo layer: the
        // pre-roll assembled payload cannot be replayed by the next
        // TTL-served read — the post-operation bump dropped it, so that read
        // refetches through the network layer, which now serves the seeded
        // row memo.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")
        coEvery { homeSectionsCachePort.rerollDiscoverRow(any()) } returns
            Result.success(listOf(mockk<MediaItem>(relaxed = true)))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        repository.getHomeSections(HomeSectionQuery(), force = false)
        repository.rerollDiscoverRow(row)
        repository.getHomeSections(HomeSectionQuery(), force = false)

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
        coEvery { homeSectionsCachePort.rerollDiscoverRow(any()) } returns
            Result.success(listOf(mockk<MediaItem>(relaxed = true)))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        val racedFetch = async { repository.getHomeSections(HomeSectionQuery(), force = false) }
        runCurrent() // the fetch captures its epoch and parks on the gate
        repository.rerollDiscoverRow(row)

        fetchGate.complete(homeResult("pre-roll"))
        assertTrue(racedFetch.await().isSuccess, "the raced fetch still returns its result to its caller")

        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("post-roll")
        repository.getHomeSections(HomeSectionQuery(), force = false)

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `the roll bumps the write generation once after the operation returns - the funnel is the only writer`() = runTest {
        // The one-funnel pin, retargeted for the operation hand-off: the
        // roll's cache choreography lives inside the network op (behind the
        // fetcher's own guard), and the repo answers with ONE post-operation
        // bump (bump + assembled-payload drop as one indivisible unit — no
        // repo-side invalidate step exists anymore; the token is repo-local,
        // so the pin is behavioral). Three halves:
        //  - a home fetch parked BEFORE the roll captures the pre-bump
        //    value; released after the op returned, its write is refused
        //    (still returned to its caller);
        //  - a read landing while the op is in flight joins the parked
        //    flight (single-flight: one network call, no second fetch — the
        //    old double-fetch wart stays dead);
        //  - the post-roll ordinary read refetches (the raced write did not
        //    pin), captures the settled generation, and PINS; a further read
        //    is served from the cache — nothing bumped after the operation.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val rolledItems = listOf(mockk<MediaItem>(relaxed = true))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        // The network op takes real time: the roll parks on this gate,
        // holding the post-operation bump back and opening the in-flight
        // window a home read can land inside.
        val rowGate = CompletableDeferred<Unit>()
        coEvery { homeSectionsCachePort.rerollDiscoverRow(row) } coAnswers { rowGate.await(); Result.success(rolledItems) }

        // Phase 1 — a home fetch parked BEFORE the roll captures the pre-bump
        // generation.
        val preRollGate = CompletableDeferred<Result<HomeSectionsResult>>()
        coEvery { apiClient.getHomeSections(any(), any()) } coAnswers { preRollGate.await() }
        val beforeRoll = async { repository.getHomeSections(HomeSectionQuery(), force = false) }
        runCurrent() // parked on preRollGate, generation captured

        val roll = async { repository.rerollDiscoverRow(row) }
        runCurrent() // the roll parks inside the network op (pre-bump still)

        // Phase 2 — a home read landing INSIDE the operation window. It
        // JOINS phase 1's parked flight (single-flight: overlapping callers
        // share one network call), so it receives the same flight result and
        // issues no second fetch.
        val midRollFetch = async { repository.getHomeSections(HomeSectionQuery(), force = false) }
        runCurrent() // joined the parked flight — no new network call

        // Release the operation: it returns and the post-op bump lands (the
        // funnel's single bump + assembled-payload drop).
        rowGate.complete(Unit)
        assertTrue(roll.await().isSuccess)

        // Phase 1's write lands AFTER the bump: refused (captured pre-bump ≠
        // settled), still returned to its caller — and the joined phase-2
        // caller receives the flight's result.
        preRollGate.complete(homeResult("pre-roll"))
        assertTrue(beforeRoll.await().isSuccess, "the raced fetch still returns its result to its caller")
        assertTrue(midRollFetch.await().isSuccess, "the joined caller receives the flight's result")

        // The post-roll ordinary read refetches (the raced write did not
        // pin), captures the settled generation, and PINS; a further read is
        // then served from the cache with zero new calls — nothing bumped
        // after the operation returned.
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("post-roll")
        repository.getHomeSections(HomeSectionQuery(), force = false)
        repository.getHomeSections(HomeSectionQuery(), force = false)

        // Two network calls total — the parked flight's single fetch + the
        // post-roll read. The mid-roll caller joined the flight (no second
        // concurrent call): the double-fetch this fixes stays fixed.
        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `rerollDiscoverRow failure returns the failure and keeps the assembled payload dropped`() = runBlocking {
        // A failed roll skips the commit (inside the network op), but the
        // operation still DROPPED the row memo — the cached assembled payload
        // (carrying the pre-roll row) must not survive to be replayed: the
        // repo's post-operation bump runs on every completed attempt. The
        // next home fetch therefore re-queries the row rather than replaying
        // pre-roll items (the interface KDoc's failure contract).
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")
        coEvery { homeSectionsCachePort.rerollDiscoverRow(any()) } returns
            Result.failure(RuntimeException("flaky"))
        val row = DiscoverRowConfig(id = "dr_x", title = "Surprise Me")

        val result = repository.rerollDiscoverRow(row)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { homeSectionsCachePort.rerollDiscoverRow(row) }

        repository.getHomeSections(HomeSectionQuery(), force = false) // must refetch, not replay
        coVerify(exactly = 1) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `refreshHomeSection is forced all the way to the network layer`() = runBlocking {
        // The edge-pull refresh's freshness contract, first half: the force
        // flag must arrive at the network fetcher so its sub-call memos
        // (latest media / discover rows) are BYPASSED on read — a pull that
        // served a memoised row would not be a refresh.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        val section = homeSection("latest_lib1")
        coEvery { homeSectionsCachePort.refreshHomeSection(any(), any(), any(), any()) } returns
            Result.success(section)

        repository.refreshHomeSection(
            section,
            HomeSectionQuery(),
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )

        coVerify(exactly = 1) { homeSectionsCachePort.refreshHomeSection(section, any(), false, true) }
    }

    @Test
    fun `refreshHomeSection success drops the cached home payload - the next ordinary read refetches`() = runBlocking {
        // The freshness contract's second half (the dice-roll drop's twin):
        // the in-memory assembled payload still carries the PRE-pull sections,
        // and a non-forced periodic read landing inside its 60s TTL would
        // repaint the stale row over the fresh one. Success must evict it, so
        // the next ordinary read refetches (through the network layer, whose
        // sub-call memos the pull just refreshed). The SWR snapshot persist
        // stays untouched — verified structurally: the port call, not a
        // getHomeSections path, produced the refresh.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")
        coEvery { homeSectionsCachePort.refreshHomeSection(any(), any(), any(), any()) } returns
            Result.success(homeSection("latest_lib1_fresh"))

        repository.getHomeSections(HomeSectionQuery(), force = false) // populate the assembled cache
        repository.refreshHomeSection(
            homeSection("latest_lib1"),
            HomeSectionQuery(),
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )
        repository.getHomeSections(HomeSectionQuery(), force = false) // must refetch, not serve pre-pull payload

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `refreshHomeSection failure keeps the cached home payload`() = runBlocking {
        // A failed pull leaves every row it didn't touch exactly as they were —
        // the assembled payload is still accurate for them, so the next
        // ordinary read keeps serving it instead of paying a full refetch.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")
        coEvery { homeSectionsCachePort.refreshHomeSection(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("server down"))

        repository.getHomeSections(HomeSectionQuery(), force = false) // populate the assembled cache
        repository.refreshHomeSection(
            homeSection("latest_lib1"),
            HomeSectionQuery(),
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )
        repository.getHomeSections(HomeSectionQuery(), force = false) // still within TTL: cached

        coVerify(exactly = 1) { apiClient.getHomeSections(any(), any()) }
    }

    @Test
    fun `getHomeSections identity-keyed - user A cached result not served to user B`() = runBlocking {
        // The headline C9 test: a wrong identity must be a guaranteed miss by
        // construction, so the previous user's home payload can never surface
        // for the next user within the TTL window.
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery(), force = false) // populates user-A entry

        // Switch to user B on the same server.
        switchUser("user-B")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("B")

        repository.getHomeSections(HomeSectionQuery(), force = false)

        // Two distinct network fetches — user A's cached entry did NOT serve user B.
        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    /**
     * Lazy home staleness, the #157 contract (see MediaRepositoryImpl's
     * homeSectionsStale): a confirmed own-write (toggleFavorite / markPlayed /
     * markUnplayed / delivered STOP / outbox drain) announces through
     * [UserDataAnnouncer.notifyUserDataChanged], which does NOT eagerly clear
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

        repository.getHomeSections(HomeSectionQuery(), force = false)
        announcer.notifyUserDataChanged(listOf("item-1"))
        repository.getHomeSections(HomeSectionQuery(), force = false)

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

        repository.getHomeSections(HomeSectionQuery(), force = false) // populate the cache
        announcer.notifyUserDataChanged(listOf("item-1")) // arm the marker
        val failed = repository.getHomeSections(HomeSectionQuery(), force = false) // consumes, fetch fails
        assertTrue(failed.isFailure)
        repository.getHomeSections(HomeSectionQuery(), force = false) // re-armed: refetches

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

        repository.getHomeSections(HomeSectionQuery(), force = false) // populate, force = false
        announcer.notifyUserDataChanged(listOf("item-1")) // arm the marker
        repository.getHomeSections(HomeSectionQuery(), force = false) // consumes the marker

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
        assertEquals(listOf(false, true), forcedFlags)
    }

    @Test
    fun `a home read without an intervening announcement serves the cached result`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        val first = repository.getHomeSections(HomeSectionQuery(), force = false)
        val second = repository.getHomeSections(HomeSectionQuery(), force = false)

        coVerify(exactly = 1) { apiClient.getHomeSections(any(), any()) }
        assertEquals(first.getOrNull(), second.getOrNull())
    }

    @Test
    fun `the staleness marker is one-shot - a second read without a new announcement serves cache`() = runBlocking {
        val repository = buildRepository()
        signIn("server-1", "user-A")
        coEvery { apiClient.getHomeSections(any(), any()) } returns homeResult("A")

        repository.getHomeSections(HomeSectionQuery(), force = false)
        announcer.notifyUserDataChanged(listOf("item-1"))
        repository.getHomeSections(HomeSectionQuery(), force = false) // consumes the marker, refetches
        repository.getHomeSections(HomeSectionQuery(), force = false) // marker gone: cached

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

        repository.getHomeSections(HomeSectionQuery(), force = false) // populate, force = false
        announcer.notifyUserDataChanged(listOf("item-1")) // arm the marker
        switchUser("user-B")
        repository.getHomeSections(HomeSectionQuery(), force = false) // identity miss refetches anyway

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

        repository.getHomeSections(HomeSectionQuery(), force = false) // cached at t=1000
        fakeTimeSource.nowMs += HomeFreshness.REPO_MEMORY_TTL_MS + 1_000L // 61s later
        repository.getHomeSections(HomeSectionQuery(), force = false)

        coVerify(exactly = 2) { apiClient.getHomeSections(any(), any()) }
    }

    // (The Room SWR staleness-ceiling pins — null past 24h, payload when
    // fresh — moved to HomeSectionsSnapshotStoreTest: the ceiling check lives
    // in the extracted store now.)

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
