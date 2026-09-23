package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.model.ConnectionCredentials
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the shared authenticated-edge realtime choreography both shells used
 * to duplicate (the Android SessionCoordinator / desktop
 * DesktopSessionCoordinator twins): connect + receiver start on the auth true
 * edge, disconnect + receiver stop on the false edge, endpoint selection, and
 * the capabilities re-arm on the first connect and every true reconnect —
 * gated on the live auth state so a post never fires while signed out. Fake
 * clock-free, plain lambdas for the transport seams (the
 * ShellSessionControllerTest pattern; the repositories are plain lambdas).
 * The Android fan-out seam ([RealtimeSessionController.onConnected]) is
 * pinned to the exact same arm the credentials ride: same auth-true edge,
 * same resolved pair, same pair-null never-retry skip, and no re-fire on
 * socket reconnects (only a fresh auth edge re-fires it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSessionControllerTest {

    /** Everything the controller reaches through, recorded. */
    private class FakeRealtime {
        val isAuth = MutableStateFlow(false)
        val server = MutableStateFlow<ServerInfo?>(testServer())
        val user = MutableStateFlow<UserInfo?>(testUser())

        /** The transport's active endpoint — `null` = fall back to the address. */
        var activeUrl: String? = null
        val isConnected = MutableStateFlow(false)
        val reconnects = MutableSharedFlow<Unit>()

        val connects = mutableListOf<ConnectionCredentials>()
        val disconnects = mutableListOf<Unit>()
        val receiverStarts = mutableListOf<Unit>()
        val receiverStops = mutableListOf<Unit>()
        var deviceIdCalls = 0
        val capabilityPosts = mutableListOf<Unit>()

        /** The (server, user) pairs delivered to `onConnected`, in order. */
        val connectedPairs = mutableListOf<Pair<ServerInfo, UserInfo>>()

        /**
         * Shared fire-order log (`startReceiver` vs `onConnected`) so tests
         * can pin the fan-out's exact position in the arm.
         */
        val fireOrder = mutableListOf<String>()

        fun controller(
            scope: CoroutineScope,
            clientName: String = "JellyPlay",
            onReconnect: suspend () -> Unit = { capabilityPosts.add(Unit) },
            onConnected: suspend (ServerInfo, UserInfo) -> Unit = { server, user ->
                connectedPairs.add(server to user)
                fireOrder.add("onConnected")
            },
        ) = RealtimeSessionController(
            scope = scope,
            isAuthenticated = isAuth,
            currentServer = server,
            currentUser = user,
            clientName = clientName,
            serverUrl = { activeUrl },
            isConnected = isConnected,
            reconnects = reconnects,
            connect = { credentials ->
                connects.add(credentials)
                // A real connect flips the handshake state, arming the
                // watcher's first-connect wait.
                isConnected.value = true
            },
            disconnect = {
                disconnects.add(Unit)
                // A real disconnect drops the handshake state.
                isConnected.value = false
            },
            startReceiver = {
                receiverStarts.add(Unit)
                fireOrder.add("startReceiver")
            },
            stopReceiver = { receiverStops.add(Unit) },
            ensureDeviceId = {
                deviceIdCalls++
                "device-1"
            },
            onReconnect = onReconnect,
            onConnected = onConnected,
        )
    }

    @Test
    fun `auth true connects and starts the receiver with the host clientName`() = runTest {
        val realtime = FakeRealtime()
        realtime.controller(scope = testScope())

        // Startup signed-out pass: the false edge disconnects + stops (the
        // hosts' pre-fold behavior at composition/start).
        advanceUntilIdle()
        assertEquals(listOf(Unit), realtime.disconnects, "the initial false edge must tear down")
        assertEquals(listOf(Unit), realtime.receiverStops)

        realtime.isAuth.value = true
        advanceUntilIdle()

        assertEquals(1, realtime.connects.size)
        val credentials = realtime.connects.single()
        assertEquals("JellyPlay", credentials.clientName)
        assertEquals("token-1", credentials.accessToken)
        assertEquals("device-1", credentials.deviceId)
        assertEquals(
            ConnectionCredentials.deviceNameFor("User"),
            credentials.deviceName,
            "device name must ride ConnectionCredentials.deviceNameFor, as both shells did",
        )
        // No active transport endpoint selected yet → the server's address.
        assertEquals("https://server.example", credentials.serverAddress)
        assertEquals(listOf(Unit), realtime.receiverStarts)
        assertEquals(1, realtime.deviceIdCalls)
    }

    @Test
    fun `endpoint selection prefers the transport's active url over the server address`() = runTest {
        val realtime = FakeRealtime()
        realtime.activeUrl = "https://alternate.example"
        realtime.controller(scope = testScope())

        realtime.isAuth.value = true
        advanceUntilIdle()

        assertEquals(
            "https://alternate.example",
            realtime.connects.single().serverAddress,
            "the socket must target the ACTIVE endpoint, not the possibly-dead primary",
        )
    }

    @Test
    fun `auth false disconnects and stops the receiver`() = runTest {
        val realtime = FakeRealtime()
        realtime.controller(scope = testScope())
        realtime.isAuth.value = true
        advanceUntilIdle()
        assertTrue(realtime.receiverStarts.isNotEmpty(), "precondition: started on the true edge")
        realtime.disconnects.clear()
        realtime.receiverStops.clear()

        realtime.isAuth.value = false
        advanceUntilIdle()

        assertEquals(listOf(Unit), realtime.disconnects)
        assertEquals(listOf(Unit), realtime.receiverStops)
        // And signing back in reconnects.
        realtime.isAuth.value = true
        advanceUntilIdle()
        assertEquals(2, realtime.connects.size)
        assertEquals(2, realtime.receiverStarts.size)
    }

    @Test
    fun `capabilities re-arm on the first connect and every reconnect, never while signed out`() = runTest {
        val realtime = FakeRealtime()
        realtime.controller(scope = testScope())
        advanceUntilIdle()
        assertTrue(realtime.capabilityPosts.isEmpty(), "no arms while signed out")

        // Sign in: the connect flips the handshake state, waking the watcher's
        // one-shot first-connect wait — the immediate arm (reconnects carries
        // no replay, so this is the only first-connect arm).
        realtime.isAuth.value = true
        advanceUntilIdle()
        assertEquals(1, realtime.capabilityPosts.size, "the first connect must arm capabilities")

        realtime.reconnects.emit(Unit)
        realtime.reconnects.emit(Unit)
        advanceUntilIdle()
        assertEquals(3, realtime.capabilityPosts.size, "every true reconnect re-arms")

        // Sign out: the false edge disconnects; a reconnect emission while
        // signed out must stay gated off the live auth state.
        realtime.isAuth.value = false
        advanceUntilIdle()
        realtime.reconnects.emit(Unit)
        advanceUntilIdle()
        assertEquals(3, realtime.capabilityPosts.size, "signed-out arms must stay gated")
    }

    @Test
    fun `a failed capability post does not kill the reconnect watcher`() = runTest {
        val realtime = FakeRealtime()
        var fail = true
        realtime.controller(
            scope = testScope(),
            onReconnect = {
                if (fail) throw IllegalStateException("offline")
                realtime.capabilityPosts.add(Unit)
            },
        )
        realtime.isAuth.value = true
        advanceUntilIdle()
        assertTrue(realtime.capabilityPosts.isEmpty(), "the failed first post was swallowed")

        fail = false
        realtime.reconnects.emit(Unit)
        advanceUntilIdle()
        assertEquals(1, realtime.capabilityPosts.size, "the watcher must survive a failed post")
    }

    @Test
    fun `stop cancels the collectors and stops the receiver`() = runTest {
        val realtime = FakeRealtime()
        val gate = CompletableDeferred<Unit>()
        val scope = testScope()
        val controller = realtime.controller(
            scope = scope,
            onReconnect = {
                gate.await()
                realtime.capabilityPosts.add(Unit)
            },
        )
        advanceUntilIdle() // the initial signed-out pass: disconnect + stop
        assertTrue(realtime.disconnects.size == 1, "precondition: the false edge ran")
        realtime.isAuth.value = true
        advanceUntilIdle() // suspends inside onReconnect's gate
        controller.stop()
        assertTrue(realtime.receiverStops.isNotEmpty(), "stop must stop the receiver")

        // The parked arm belongs to the cancelled collector tree: releasing
        // the gate must not add a post.
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(realtime.capabilityPosts.isEmpty(), "stop must cancel the in-flight arm")

        // And the auth collector is gone: a later edge flip is a no-op.
        realtime.isAuth.value = false
        advanceUntilIdle()
        assertEquals(1, realtime.disconnects.size, "stop must cancel the auth-edge collector")
    }

    @Test
    fun `onConnected fires after startReceiver with the resolved pair`() = runTest {
        val realtime = FakeRealtime()
        // An ACTIVE transport endpoint is selected: the fan-out must still
        // receive the FLOW's ServerInfo (possibly-dead primary address),
        // not the endpoint-selected URL the credentials prefer.
        realtime.activeUrl = "https://alternate.example"
        realtime.controller(scope = testScope())

        realtime.isAuth.value = true
        advanceUntilIdle()

        val (server, user) = realtime.connectedPairs.single()
        assertEquals("server-1", server.id)
        assertEquals("https://server.example", server.address)
        assertEquals("user-1", user.id)
        assertEquals("token-1", user.accessToken)
        // Same arm, exact position: the receiver start, THEN the fan-out.
        assertEquals(listOf("startReceiver", "onConnected"), realtime.fireOrder)
        assertEquals(1, realtime.receiverStarts.size)
    }

    @Test
    fun `auth true before server and user populate skips onConnected exactly like the credentials`() = runTest {
        val realtime = FakeRealtime()
        realtime.server.value = null
        realtime.user.value = null
        realtime.controller(scope = testScope())

        realtime.isAuth.value = true
        advanceUntilIdle()

        // The never-retry no-op: the pair-null arm skips BOTH the
        // credentials and the fan-out.
        assertTrue(realtime.connects.isEmpty(), "the no-op must skip the connect")
        assertTrue(realtime.receiverStarts.isEmpty(), "the no-op must skip the receiver")
        assertTrue(realtime.connectedPairs.isEmpty(), "the no-op must skip the fan-out")

        // The edge is spent (auth will not re-emit): populating the flows
        // afterwards must not retro-fire anything.
        realtime.server.value = testServer()
        realtime.user.value = testUser()
        advanceUntilIdle()
        assertTrue(realtime.connectedPairs.isEmpty(), "the spent edge must not re-fire")
        assertTrue(realtime.connects.isEmpty())
    }

    @Test
    fun `onConnected never re-fires on reconnects and re-fires on a fresh auth edge`() = runTest {
        val realtime = FakeRealtime()
        realtime.controller(scope = testScope())
        realtime.isAuth.value = true
        advanceUntilIdle()
        assertEquals(1, realtime.connectedPairs.size, "fired once on the sign-in edge")

        // Socket drop + reconnects (capabilities re-post territory): the
        // fan-out is AUTH-edge work, not per-(re)connect work — no re-fire,
        // exactly like the host-side auth collector it replaced.
        realtime.reconnects.emit(Unit)
        realtime.reconnects.emit(Unit)
        advanceUntilIdle()
        assertEquals(1, realtime.connectedPairs.size)
        assertEquals(3, realtime.capabilityPosts.size, "the reconnects still re-armed capabilities")

        // Sign out, sign back in: a NEW auth-true edge re-fires with the
        // freshly resolved pair.
        realtime.isAuth.value = false
        advanceUntilIdle()
        realtime.isAuth.value = true
        advanceUntilIdle()
        assertEquals(2, realtime.connectedPairs.size)
        assertEquals(realtime.server.value, realtime.connectedPairs.last().first)
        assertEquals(realtime.user.value, realtime.connectedPairs.last().second)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /**
     * The host scope the tests hand the controller (the
     * ShellSessionControllerTest pattern): an UNPARENTED job on the test
     * scheduler — [advanceUntilIdle] drives its launches, and runTest's leak
     * check never waits on it because it is not a child of the test job.
     */
    private fun kotlinx.coroutines.test.TestScope.testScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())
}

/** Top level so the non-inner [RealtimeSessionControllerTest.FakeRealtime] can reach them. */
private fun testServer() = ServerInfo(
    id = "server-1",
    name = "Server",
    address = "https://server.example",
)

private fun testUser() = UserInfo(
    id = "user-1",
    name = "User",
    serverAddress = "https://server.example",
    accessToken = "token-1",
)
