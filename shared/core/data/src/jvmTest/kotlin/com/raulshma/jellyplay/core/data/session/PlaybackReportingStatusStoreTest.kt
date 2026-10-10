package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [PlaybackReportingStatusStore] — the ONE owner of the
 * Playback Reporting plugin status (the flow `AdminStatisticsRepositoryImpl`
 * and `WatchHistoryRepositoryImpl` used to each hold privately). Pins:
 *  - the initial UNKNOWN (never checked);
 *  - refresh publishes the check's verdict (AVAILABLE / UNAVAILABLE), or the
 *    UNAVAILABLE fallback when the check itself fails;
 *  - the [SessionCacheRegistry] invalidation: user switch and sign-out reset
 *    the flow to UNKNOWN (the declared addition over the two former owners,
 *    which never cleared on identity change).
 *
 * The identity chain is REAL (HomeSession + SessionCacheRegistry over a
 * mocked `AuthApiClient.session` — the EpisodeCatalogueImplTest idiom),
 * so the invalidation is exercised end-to-end through the registry, not
 * stubbed.
 */
class PlaybackReportingStatusStoreTest {

    private val authApiClient: AuthApiClient = mockk()
    private val mediaInfoApiClient: MediaInfoApiClient = mockk()
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)

    private lateinit var homeSession: HomeSession
    private lateinit var store: PlaybackReportingStatusStore

    @BeforeTest
    fun setup() {
        every { authApiClient.session } returns sessionFlow
        homeSession = HomeSession(
            authApiClient,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val sessionCacheRegistry = SessionCacheRegistry(
            homeSession,
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        store = PlaybackReportingStatusStore(mediaInfoApiClient, sessionCacheRegistry)
    }

    private fun session(serverId: String, userId: String) = ActiveSession(
        ServerInfo(id = serverId, name = serverId, address = "https://$serverId"),
        UserInfo(id = userId, name = userId, serverAddress = "https://$serverId", accessToken = "tok"),
    )

    /** Polls (real dispatcher, the registry's collector runs on one) until the store reports [expected]. */
    private suspend fun awaitStatus(expected: PlaybackReportingStatus) {
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(2_000) {
                while (store.status.value != expected) delay(10)
            } ?: error("store never reached $expected (was ${store.status.value})")
        }
    }

    /**
     * Signs in [userId] on [serverId] and waits for the identity mirror — the
     * first session value produces a `SignedIn` transition, which the
     * registry deliberately SKIPS (no previous identity to drop), so the
     * refresh that follows cannot be cleared by it.
     */
    private suspend fun signIn(serverId: String, userId: String) {
        sessionFlow.value = session(serverId, userId)
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(2_000) {
                while (homeSession.currentIdentitySnapshot()?.userId != userId) delay(10)
            } ?: error("identity never reached $userId")
        }
    }

    /** A refreshed-AVAILABLE store, signed in as server-A/user-1. */
    private suspend fun refreshedAvailableStore() {
        signIn("server-A", "user-1")
        coEvery { mediaInfoApiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.AVAILABLE)
        store.refresh()
        assertEquals(PlaybackReportingStatus.AVAILABLE, store.status.value)
    }

    // ── initial value + refresh semantics ───────────────────────────────

    @Test
    fun `initial status is UNKNOWN before any refresh`() = runTest {
        assertEquals(PlaybackReportingStatus.UNKNOWN, store.status.value)
    }

    @Test
    fun `refresh publishes AVAILABLE when the plugin check succeeds`() = runTest {
        coEvery { mediaInfoApiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.AVAILABLE)

        store.refresh()

        assertEquals(PlaybackReportingStatus.AVAILABLE, store.status.value)
    }

    @Test
    fun `refresh publishes the check's UNAVAILABLE verdict verbatim`() = runTest {
        coEvery { mediaInfoApiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.UNAVAILABLE)

        store.refresh()

        assertEquals(PlaybackReportingStatus.UNAVAILABLE, store.status.value)
    }

    @Test
    fun `refresh falls back to UNAVAILABLE when the check fails`() = runTest {
        coEvery { mediaInfoApiClient.checkPlaybackReportingPlugin() } returns Result.failure(IllegalStateException("down"))

        store.refresh()

        assertEquals(PlaybackReportingStatus.UNAVAILABLE, store.status.value)
    }

    // ── session invalidation (the registry action) ──────────────────────

    @Test
    fun `a user switch resets a refreshed status to UNKNOWN`() = runTest {
        refreshedAvailableStore()

        sessionFlow.value = session("server-A", "user-2")
        awaitStatus(PlaybackReportingStatus.UNKNOWN)
    }

    @Test
    fun `a server switch resets a refreshed status to UNKNOWN`() = runTest {
        refreshedAvailableStore()

        sessionFlow.value = session("server-B", "user-1")
        awaitStatus(PlaybackReportingStatus.UNKNOWN)
    }

    @Test
    fun `sign-out resets a refreshed status to UNKNOWN`() = runTest {
        refreshedAvailableStore()

        sessionFlow.value = null
        awaitStatus(PlaybackReportingStatus.UNKNOWN)
    }

    @Test
    fun `refresh after invalidation republishes the new verdict`() = runTest {
        refreshedAvailableStore()

        // Sign out (SignedOut clears), sign back in (SignedIn — registry
        // skips it), refresh again: the NEW verdict must win.
        sessionFlow.value = null
        awaitStatus(PlaybackReportingStatus.UNKNOWN)
        signIn("server-A", "user-2")

        coEvery { mediaInfoApiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.UNAVAILABLE)
        store.refresh()

        assertEquals(PlaybackReportingStatus.UNAVAILABLE, store.status.value)
        assertEquals(PlaybackReportingStatus.UNAVAILABLE, store.status.first())
    }

    // ── the lifted plugin-gate fold (gatedWith) ─────────────────────────

    /**
     * The one table for the fold AdminStatisticsRepositoryImpl's `whenPlugin`
     * and WatchHistoryRepositoryImpl's two inline ladders were collapsed
     * into: AVAILABLE runs the call (success verbatim, failure → emptyList),
     * every other captured status (UNAVAILABLE, UNKNOWN) short-circuits to
     * emptyList WITHOUT calling.
     */
    @Test
    fun `gatedWith folds the plugin gate over call success and failure`() = runTest {
        data class Case(
            val captured: PlaybackReportingStatus,
            val callResult: Result<List<String>>,
            val expected: List<String>,
            val expectCall: Boolean,
        )

        listOf(
            Case(PlaybackReportingStatus.AVAILABLE, Result.success(listOf("a")), listOf("a"), expectCall = true),
            Case(PlaybackReportingStatus.AVAILABLE, Result.failure(IllegalStateException("down")), emptyList(), expectCall = true),
            Case(PlaybackReportingStatus.UNAVAILABLE, Result.success(listOf("a")), emptyList(), expectCall = false),
            Case(PlaybackReportingStatus.UNKNOWN, Result.failure(IllegalStateException("down")), emptyList(), expectCall = false),
        ).forEach { case ->
            var called = false
            val result = store.gatedWith(case.captured) {
                called = true
                case.callResult
            }
            assertEquals(case.expected, result, "captured=${case.captured} call=${case.callResult}")
            assertEquals(case.expectCall, called, "captured=${case.captured}")
        }
    }
}
