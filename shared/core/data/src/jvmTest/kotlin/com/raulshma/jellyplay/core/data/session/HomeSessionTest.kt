package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Classification coverage for [HomeSession] — the single owner of identity
 * transitions that replaced the three per-consumer mirrors
 * (MediaRepositoryImpl, EpisodeCatalogueImpl, HomeViewModel).
 *
 * The harness drives [HomeSession] through a mockk [JellyfinApiClient] whose
 * [JellyfinApiClient.session] is a real [MutableStateFlow], with the
 * collector on the test scheduler (runTest's backgroundScope) so
 * `runCurrent()` deterministically advances both the classifier and the
 * transitions subscriber. The ATOMICITY of the flow itself — that a
 * cross-server switch never publishes a mixed `(newServer, oldUser)` pair —
 * is the engine's contract and is pinned by JellyfinApiEngineSessionTest in
 * core:network (this module's test classpath cannot construct the engine,
 * whose constructor exposes org.jellyfin.sdk types not exported to
 * core:data).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeSessionTest {

    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)

    @BeforeTest
    fun setup() {
        every { apiClient.session } returns sessionFlow
    }

    private fun server(id: String) = ServerInfo(id = id, name = id, address = "https://$id")

    private fun user(id: String) = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token-$id",
    )

    /**
     * Subscribes before any emission so the replay-0 flow cannot lose one.
     * The collector runs in runTest's backgroundScope (shares the test
     * scheduler, auto-cancelled at test end) so the infinite collect doesn't
     * trip runTest's uncompleted-coroutines check.
     */
    private fun kotlinx.coroutines.test.TestScope.subscribe(
        session: HomeSession,
    ): MutableList<HomeSessionTransition> =
        mutableListOf<HomeSessionTransition>().also { received ->
            backgroundScope.launch { session.transitions.collect { received.add(it) } }
        }

    @Test
    fun `sign in emits SignedIn`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        val transitions = subscribe(session)
        runCurrent()

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()

        assertEquals(listOf<HomeSessionTransition>(HomeSessionTransition.SignedIn), transitions)
    }

    @Test
    fun `same-server user change emits UserSwitched with the previous identity`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        val transitions = subscribe(session)
        runCurrent()

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        sessionFlow.value = ActiveSession(server("s1"), user("u2"))
        runCurrent()

        assertEquals(
            listOf(
                HomeSessionTransition.SignedIn,
                HomeSessionTransition.UserSwitched(SessionIdentity("s1", "u1")),
            ),
            transitions,
        )
    }

    @Test
    fun `server change emits ServerSwitched`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        val transitions = subscribe(session)
        runCurrent()

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        sessionFlow.value = ActiveSession(server("s2"), user("u2"))
        runCurrent()

        assertEquals(
            listOf(
                HomeSessionTransition.SignedIn,
                HomeSessionTransition.ServerSwitched(SessionIdentity("s1", "u1")),
            ),
            transitions,
        )
    }

    @Test
    fun `logout emits SignedOut carrying the cleared identity`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        val transitions = subscribe(session)
        runCurrent()

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        sessionFlow.value = null
        runCurrent()

        assertEquals(
            listOf(
                HomeSessionTransition.SignedIn,
                HomeSessionTransition.SignedOut(SessionIdentity("s1", "u1")),
            ),
            transitions,
        )
    }

    @Test
    fun `re-emission of the same identity produces no transition`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        val transitions = subscribe(session)
        runCurrent()

        // Token refresh / address failover shape: a NEW pair object with the
        // same (serverId, userId) must not reclassify.
        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        sessionFlow.value = ActiveSession(
            server("s1").copy(name = "renamed"),
            user("u1").copy(accessToken = "refreshed"),
        )
        runCurrent()

        assertEquals(listOf<HomeSessionTransition>(HomeSessionTransition.SignedIn), transitions)
    }

    @Test
    fun `atomic cross-server switch produces exactly one transition`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        val transitions = subscribe(session)
        runCurrent()

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        sessionFlow.value = ActiveSession(server("s2"), user("u2"))
        runCurrent()

        // The mixed (s2, u1) intermediate the old two-step publish produced
        // cannot even be expressed by the atomic session flow — assert the
        // single-transition contract HomeSession guarantees its subscribers.
        assertEquals(
            listOf(
                HomeSessionTransition.SignedIn,
                HomeSessionTransition.ServerSwitched(SessionIdentity("s1", "u1")),
            ),
            transitions,
        )
    }

    @Test
    fun `currentIdentity reads the source flow and snapshot tracks the mirror`() = runTest {
        val session = HomeSession(apiClient, backgroundScope)
        backgroundScope.launch { session.transitions.collect { } }
        runCurrent()

        assertNull(session.currentIdentity())
        assertNull(session.currentIdentitySnapshot())

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()

        assertEquals(SessionIdentity("s1", "u1"), session.currentIdentity())
        assertEquals(SessionIdentity("s1", "u1"), session.currentIdentitySnapshot())
    }
}
