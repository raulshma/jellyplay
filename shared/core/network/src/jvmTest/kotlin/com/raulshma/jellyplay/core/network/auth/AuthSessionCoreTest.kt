package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [AuthSessionCore] spine — the capture/adopt/try/publish/restore
 * discipline both auth API clients delegate to (the jvmShared
 * AuthApiClientImpl with the engine's ApiClient swap as its side-effects
 * port, the wasm KtorWasmAuthApiClient with the inert one). The platform
 * files keep only their wire legs; every invariant below is therefore
 * platform-free by construction.
 *
 * Harness: a recording state store over a REAL [AtomicSessionState] plus a
 * recording side-effects port (S = String), both logging
 * `event(locked=…)` lines — so every assertion checks call ordering AND
 * that the hook ran inside the injected mutex's critical section (the
 * failover-ordering invariants the JVM port depends on).
 */
class AuthSessionCoreTest {

    private val mutex = Mutex()
    private val events = mutableListOf<String>()

    private val store = RecordingStateStore(mutex, events)
    private val sideEffects = RecordingSideEffects(mutex, events)
    private val core = AuthSessionCore(mutex = mutex, state = store, sideEffects = sideEffects)

    private fun server(id: String) = ServerInfo(id = id, name = id, address = "https://$id")

    private fun user(id: String) = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token-$id",
    )

    /** State store over a real [AtomicSessionState]; logs each write with the mutex state it ran under. */
    private class RecordingStateStore(
        private val mutex: Mutex,
        private val events: MutableList<String>,
    ) : AuthSessionStateStore {
        private val state = AtomicSessionState()
        val currentServer: ServerInfo? get() = state.currentServer.value
        val currentUser: UserInfo? get() = state.currentUser.value
        val session: ActiveSession? get() = state.session.value
        override fun currentSession(): ActiveSession? = state.session.value
        override fun updateServer(server: ServerInfo?) {
            events.add("updateServer(${server?.id},locked=${mutex.isLocked})")
            state.updateServer(server)
        }
        override fun updateSession(server: ServerInfo?, user: UserInfo?) {
            events.add("updateSession(${server?.id},${user?.id},locked=${mutex.isLocked})")
            state.updateSession(server, user)
        }
    }

    /** Side-effects port with observable state (S = String); logs each hook with the mutex state it ran under. */
    private class RecordingSideEffects(
        private val mutex: Mutex,
        private val events: MutableList<String>,
    ) : AuthSessionSideEffects<String> {
        var current: String = "side-initial"
            private set
        override fun captureSide(): String {
            events.add("captureSide($current,locked=${mutex.isLocked})")
            return current
        }
        override fun publishSide(serverInfo: ServerInfo, userInfo: UserInfo) {
            events.add("publishSide(${userInfo.id},locked=${mutex.isLocked})")
            current = "side-published-${userInfo.id}"
        }
        override fun restoreSide(previous: String) {
            events.add("restoreSide($previous,locked=${mutex.isLocked})")
            current = previous
        }
        override fun clearSide() {
            events.add("clearSide(locked=${mutex.isLocked})")
            current = "side-cleared"
        }
    }

    @Test
    fun `successful login publishes the authenticated pair atomically with the side effect inside the lock`() = runTest {
        val target = server("s2")
        val userInfo = user("u2")

        val result = core.atomicLogin(target) { RawLoginOutcome(accessToken = "token-u2", userInfo = userInfo) }

        assertEquals(userInfo, result)
        assertEquals(
            ActiveSession(
                target.copy(userId = "u2", accessToken = "token-u2", isConnected = true),
                userInfo,
            ),
            store.session,
            "the publish must carry server.copy(userId/token/isConnected=true) plus the user",
        )
        assertEquals(
            listOf(
                "captureSide(side-initial,locked=true)",
                // the signed-out adopt, inside the capture critical section
                "updateSession(s2,null,locked=true)",
                "publishSide(u2,locked=true)", // the side effect BEFORE the state write…
                "updateSession(s2,u2,locked=true)", // …and both under the lock
            ),
            events,
        )
        assertEquals("side-published-u2", sideEffects.current)
    }

    @Test
    fun `the pre-auth adopt runs only when no session is established`() = runTest {
        // Signed-out: the adopt points currentServer at the target while the
        // identity stays null (nothing to wipe), visible to the round-trip.
        core.atomicLogin(server("s1")) {
            assertEquals(server("s1"), store.currentServer, "the adopt must precede the round-trip")
            assertNull(store.session, "a server without a user is no identity")
            RawLoginOutcome(accessToken = "token-u1", userInfo = user("u1"))
        }
        assertEquals(
            ActiveSession(
                server("s1").copy(userId = "u1", accessToken = "token-u1", isConnected = true),
                user("u1"),
            ),
            store.session,
        )

        // Signed-in: NO adopt — re-adopting would publish SignedOut(previous)
        // mid-round-trip (destructive for identity observers); the working
        // server/user must survive the whole attempt untouched.
        val established = store.session!!
        events.clear()
        val seenByRoundTrip = mutableListOf<ServerInfo?>()
        assertFailsWith<Exception> {
            core.atomicLogin(server("s2")) {
                seenByRoundTrip.add(store.currentServer)
                throw IllegalStateException("wrong password")
            }
        }
        assertEquals(listOf<ServerInfo?>(established.server), seenByRoundTrip, "no re-adopt over the working session")
        assertEquals(established, store.session, "the failed attempt must leave the captured pair published")
        assertEquals(
            listOf(
                "captureSide(side-published-u1,locked=true)",
                // NO adopt updateSession here — that is the point
                "restoreSide(side-published-u1,locked=true)",
                "updateSession(s1,u1,locked=true)",
            ),
            events,
        )
    }

    @Test
    fun `a concurrent publish between capture and restore wins over the restore`() = runTest {
        store.updateSession(server("s1"), user("u1"))
        events.clear()

        assertFailsWith<Exception> {
            core.atomicLogin(server("s2")) {
                // The round-trip runs UNLOCKED: a concurrent auth flow may
                // publish its own (server, user) pair in the meantime…
                mutex.withLock { store.updateSession(server("s3"), user("u3")) }
                throw IllegalStateException("round-trip failed after the concurrent publish")
            }
        }

        // …and that newer publish WINS: the restore re-checks under the mutex
        // and leaves BOTH the session and the side state untouched.
        assertEquals(ActiveSession(server("s3"), user("u3")), store.session)
        assertEquals("side-initial", sideEffects.current)
        assertEquals(
            listOf(
                "captureSide(side-initial,locked=true)",
                "updateSession(s3,u3,locked=true)",
                // no restoreSide, no restore updateSession — nothing to undo
            ),
            events,
        )
    }

    @Test
    fun `both validation failures restore the captured session`() = runTest {
        store.updateSession(server("s1"), user("u1"))

        val noToken = assertFailsWith<Exception> {
            core.atomicLogin(server("s2")) {
                RawLoginOutcome(accessToken = null, userInfo = user("u2"))
            }
        }
        assertEquals("No access token", noToken.message)

        val noUser = assertFailsWith<Exception> {
            core.atomicLogin(server("s2")) {
                RawLoginOutcome(accessToken = "token-u2", userInfo = null)
            }
        }
        assertEquals("Authentication failed", noUser.message)

        // Both attempts restored the captured pair (and captured side).
        assertEquals(ActiveSession(server("s1"), user("u1")), store.session)
        assertEquals("side-initial", sideEffects.current)
    }

    @Test
    fun `the failure restore survives caller cancellation`() = runTest {
        store.updateSession(server("s1"), user("u1"))
        events.clear()

        val job = launch {
            // The CancellationException is rethrown AFTER the NonCancellable
            // restore, ending this child cancelled — not failed.
            core.atomicLogin(server("s2")) {
                currentCoroutineContext().cancel()
                throw CancellationException("caller cancelled mid-round-trip")
            }
        }
        job.join()
        assertTrue(job.isCancelled, "the login coroutine itself was cancelled")

        // …yet the restore ran: withContext(NonCancellable) carried it past
        // the cancellation, re-pointing both the session and the side state.
        assertEquals(ActiveSession(server("s1"), user("u1")), store.session)
        assertEquals("side-initial", sideEffects.current)
        assertEquals(
            listOf(
                "captureSide(side-initial,locked=true)",
                "restoreSide(side-initial,locked=true)",
                "updateSession(s1,u1,locked=true)",
            ),
            events,
        )
    }

    @Test
    fun `disconnect clears the side state and the pair inside one lock`() = runTest {
        store.updateSession(server("s1"), user("u1"))
        events.clear()

        core.disconnect()

        assertNull(store.session)
        assertNull(store.currentServer)
        assertNull(store.currentUser)
        assertEquals("side-cleared", sideEffects.current)
        assertEquals(
            listOf(
                "clearSide(locked=true)", // side state dropped first…
                "updateSession(null,null,locked=true)", // …then the pair, atomically
            ),
            events,
        )
    }

    @Test
    fun `setServer and adoptServer write under the same mutex`() = runTest {
        core.setServer(server("s1"))
        assertEquals(server("s1"), store.currentServer)
        assertNull(store.session, "a server without a user is no identity")
        assertEquals(listOf("updateServer(s1,locked=true)"), events)

        events.clear()
        core.adoptServer(server("s2"))
        assertEquals(server("s2"), store.currentServer, "the adopt replaces the server")
        assertNull(store.currentUser, "the adopt drops any signed-in user")
        assertNull(store.session, "no synthetic (newServer, oldUser) intermediate")
        assertEquals(listOf("updateSession(s2,null,locked=true)"), events)
    }
}
