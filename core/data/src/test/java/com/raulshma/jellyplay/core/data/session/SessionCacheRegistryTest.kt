package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reaction coverage for [SessionCacheRegistry] — the single subscriber that
 * turns [HomeSession] transitions into cache drops. The harness drives a real
 * [HomeSession] through a mockk [JellyfinApiClient] whose
 * [JellyfinApiClient.session] is a real [MutableStateFlow], with BOTH the
 * classifier and the registry's collector on the test scheduler
 * (`backgroundScope`), so `runCurrent()` deterministically advances
 * session emission → classification → reaction. This mirrors the seam
 * [HomeSessionTest] established for the classifier half.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionCacheRegistryTest {

    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)

    @Before
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

    private fun TestScope.buildRegistry(): Pair<HomeSession, SessionCacheRegistry> {
        val homeSession = HomeSession(apiClient, backgroundScope)
        val registry = SessionCacheRegistry(homeSession, backgroundScope)
        runCurrent()
        return homeSession to registry
    }

    @Test
    fun `SignedIn does not clear caches or run actions`() = runTest {
        val (_, registry) = buildRegistry()
        val cache = TtlCache<String>(ttlMs = 60_000L)
        cache.put("k", "v")
        var actionRan = false
        registry.registerCaches("owner", cache)
        registry.registerAction("owner") { actionRan = true }

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()

        assertEquals("SignedIn must not clear caches (restore / first login)", "v", cache.get("k"))
        assertTrue("SignedIn must not run actions", !actionRan)
    }

    @Test
    fun `UserSwitched ServerSwitched and SignedOut clear caches and run actions with the transition`() = runTest {
        val (_, registry) = buildRegistry()
        val cache = TtlCache<String>(ttlMs = 60_000L)
        val received = mutableListOf<HomeSessionTransition>()
        registry.registerCaches("owner", cache)
        registry.registerAction("owner") { transition -> received.add(transition) }

        // Sign in first so later steps classify as switches, not restore.
        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        assertTrue("SignedIn must not run actions", received.isEmpty())
        cache.put("k", "v")

        // Same server, different user → UserSwitched carrying the previous identity.
        sessionFlow.value = ActiveSession(server("s1"), user("u2"))
        runCurrent()
        assertEquals(HomeSessionTransition.UserSwitched(SessionIdentity("s1", "u1")), received.single())
        assertNull("UserSwitched must clear registered caches", cache.get("k"))

        // Different server → ServerSwitched carrying the previous identity.
        sessionFlow.value = ActiveSession(server("s2"), user("u3"))
        runCurrent()
        assertEquals(
            HomeSessionTransition.ServerSwitched(SessionIdentity("s1", "u2")),
            received.last(),
        )

        // Logout → SignedOut carrying the cleared identity.
        sessionFlow.value = null
        runCurrent()
        assertEquals(
            HomeSessionTransition.SignedOut(SessionIdentity("s2", "u3")),
            received.last(),
        )
        assertEquals(
            "every non-SignedIn transition reached the action, in order",
            listOf(
                HomeSessionTransition.UserSwitched(SessionIdentity("s1", "u1")),
                HomeSessionTransition.ServerSwitched(SessionIdentity("s1", "u2")),
                HomeSessionTransition.SignedOut(SessionIdentity("s2", "u3")),
            ),
            received,
        )
    }

    @Test
    fun `a throwing action does not prevent other owners or later transitions`() = runTest {
        val (_, registry) = buildRegistry()
        val survivingCache = TtlCache<String>(ttlMs = 60_000L)
        var survivorRuns = 0
        registry.registerAction("bad") { throw IllegalStateException("bad owner") }
        registry.registerCaches("survivor", survivingCache)
        registry.registerAction("survivor") { survivorRuns++ }

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        survivingCache.put("k", "v")

        sessionFlow.value = null
        runCurrent()

        assertEquals("the healthy owner's action ran despite the throwing one", 1, survivorRuns)
        assertNull("the healthy owner's cache was cleared despite the throwing one", survivingCache.get("k"))
    }

    @Test
    fun `re-registration replaces the previous registration`() = runTest {
        val (_, registry) = buildRegistry()
        val firstCache = TtlCache<String>(ttlMs = 60_000L)
        val secondCache = TtlCache<String>(ttlMs = 60_000L)
        var firstActionRan = false
        var secondActionRan = false

        registry.registerCaches("owner", firstCache)
        registry.registerAction("owner") { firstActionRan = true }
        // Re-register under the same owner: replaces, does not stack.
        registry.registerCaches("owner", secondCache)
        registry.registerAction("owner") { secondActionRan = true }

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        firstCache.put("k", "v")
        secondCache.put("k", "v")

        sessionFlow.value = ActiveSession(server("s2"), user("u2"))
        runCurrent()

        assertTrue("the replaced action must not run", !firstActionRan)
        assertTrue("the replacement action must run", secondActionRan)
        assertEquals("the replaced cache must not be cleared", "v", firstCache.get("k"))
        assertNull("the replacement cache must be cleared", secondCache.get("k"))
    }

    @Test
    fun `owners run in registration order`() = runTest {
        val (_, registry) = buildRegistry()
        val order = mutableListOf<String>()
        registry.registerAction("first") { order.add("first") }
        registry.registerAction("second") { order.add("second") }
        registry.registerAction("third") { order.add("third") }

        sessionFlow.value = ActiveSession(server("s1"), user("u1"))
        runCurrent()
        assertTrue("SignedIn must not run actions", order.isEmpty())

        sessionFlow.value = null
        runCurrent()

        assertEquals(
            "actions run in registration order, not hash-bucket order",
            listOf("first", "second", "third"),
            order,
        )
    }
}
