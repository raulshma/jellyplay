package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the engine's atomic session publish — the flow [HomeSession]
 * (core:data) classifies identity transitions from. The public write surface
 * is exactly two shapes: [JellyfinApiEngine.updateSession] (the mutex-held
 * multi-write) and [JellyfinApiEngine.adoptUser] (the single-side user
 * intent) — there is no public single-side server write, so the synthetic
 * `(newServer, oldUser)` pair two separate writes used to produce is
 * UNREPRESENTABLE from outside the module (StateManagementConventions §3:
 * impossible combinations unrepresentable, not merely discouraged).
 *
 * Harness mirrors [JellyfinApiEngineRetryTest]: a real engine over relaxed
 * mock collaborators, with `createApi` stubbed FULL-ARITY (partial matchers
 * send mockk's overload auto-hinting down a path that executes the real
 * final createApi — see AuthApiClientTest's note) because the public
 * multi-write rebuilds the client inside its critical section. The session
 * collector runs on `Dispatchers.Unconfined` so every `_session.value = …`
 * assignment is delivered synchronously and the recorded emission sequence
 * is deterministic.
 */
class JellyfinApiEngineSessionTest {

    private fun newEngine(): JellyfinApiEngine {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        every { jellyfin.createApi(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        // The router is a relaxed mock with a permanently-null activeAddress:
        // with a REAL router, updateSession's rebuild leaves `_api` non-null
        // and the engine's rebuild collector then races the test — its
        // mirrored republish (rebuildApiFor mirrors the active address into
        // the user) would surface a legitimate same-identity emission this
        // suite would misread as a mixed pair (the same trap the other
        // engine suites mock flat — see LibraryApiClientImplTest's setup
        // note). With the flow stuck at null the collector never fires.
        val router = mockk<ServerAddressRouter>(relaxed = true)
        every { router.activeAddress } returns MutableStateFlow(null)
        return JellyfinApiEngine(
            LazyProvider { jellyfin },
            LazyProvider { OkHttpClient() },
            DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            router,
        )
    }

    private fun server(id: String) = ServerInfo(id = id, name = id, address = "https://$id")

    private fun user(id: String) = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token-$id",
    )

    @Test
    fun `session starts null and collapses to null while either side is missing`() = runBlocking {
        val engine = newEngine()

        assertNull(engine.session.first())

        // (server, null) — the DESIGNED login-in-flight intermediate.
        engine.updateSession(server("s1"), null)
        assertNull(engine.session.first(), "server without user is no identity")

        engine.updateSession(null, null)
        engine.updateSession(null, user("u1"))
        assertNull(engine.session.first(), "user without server is no identity")
    }

    @Test
    fun `updateSession publishes both sides and the pair atomically`() = runBlocking {
        // PIN (a), sequential shape: a dispatched observer riding the session
        // flow sees only stable values across the multi-write.
        val engine = newEngine()
        val emissions = mutableListOf<ActiveSession?>()
        val collector = launch(Dispatchers.Unconfined) { engine.session.collect { emissions.add(it) } }
        try {
            engine.updateSession(server("s1"), user("u1"))
            assertEquals(ActiveSession(server("s1"), user("u1")), engine.session.first())

            // The login/switch shape: adopt the new server AND its user in
            // ONE critical-section step.
            engine.updateSession(server("s2"), user("u2"))

            assertEquals(ActiveSession(server("s2"), user("u2")), engine.session.first())
            assertEquals(
                listOf(null, ActiveSession(server("s1"), user("u1")), ActiveSession(server("s2"), user("u2"))),
                emissions,
                "no mixed (newServer, oldUser) intermediate may surface",
            )
            assertEquals(server("s2"), engine.currentServer.first())
            assertEquals(user("u2"), engine.currentUser.first())
        } finally {
            collector.cancel()
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun `concurrent multi-writes never surface a mixed pair to a dispatched observer`() = runBlocking {
        // PIN (a), racing shape: two identity switches interleaving from
        // separate dispatchers. Whatever the mutex interleaving, every
        // emission must be a STABLE session — null or a full (server, user)
        // pair — never the synthetic (newServer, oldUser) pair that
        // single-side writes used to surface.
        val engine = newEngine()
        val emissions = mutableListOf<ActiveSession?>()
        val collector = launch(Dispatchers.Unconfined) { engine.session.collect { emissions.add(it) } }
        try {
            val s1u1 = ActiveSession(server("s1"), user("u1"))
            val s2u2 = ActiveSession(server("s2"), user("u2"))

            val writer1 = launch(Dispatchers.IO) { repeat(50) { engine.updateSession(server("s1"), user("u1")) } }
            val writer2 = launch(Dispatchers.IO) { repeat(50) { engine.updateSession(server("s2"), user("u2")) } }
            writer1.join()
            writer2.join()

            val mixed = emissions.filter { it != null && it != s1u1 && it != s2u2 }
            assertTrue(mixed.isEmpty(), "mixed (newServer, oldUser) intermediates surfaced: $mixed")
            assertTrue(emissions.contains(s1u1) && emissions.contains(s2u2), "both stable pairs must have been observed")
        } finally {
            collector.cancel()
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun `the multi-write rebuilds the api client from the published pair in the same step`() = runBlocking {
        // PIN (b): `_api` must agree with the published server the moment the
        // multi-write returns — no stale window in the covered (full-pair)
        // path. A REAL Jellyfin so createApi answers with a real client whose
        // baseUrl/accessToken are observable.
        val jellyfin = createJellyfin {
            clientInfo = ClientInfo(name = "JellyPlayTest", version = "1.0.0")
            deviceInfo = DeviceInfo(id = "session-test", name = "SessionTest")
        }
        val engine = JellyfinApiEngine(
            LazyProvider { jellyfin },
            LazyProvider { OkHttpClient() },
            DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            ServerAddressRouter(),
        )
        val server = ServerInfo(id = "s1", name = "s1", address = "https://s1.example.com")
        val user = user("u1").copy(serverAddress = "https://s1.example.com")

        engine.updateSession(server, user)

        val api = engine.requireApi()
        assertEquals(server.address, api.baseUrl, "the client must speak the published server's address")
        assertEquals(user.accessToken, api.accessToken, "the client must speak the published user's token")
        assertEquals(ActiveSession(server, user), engine.session.first())
    }

    @Test
    fun `the designed login intermediate stays observable and does not touch the client`() = runBlocking {
        // PIN (c): (server, null) — the login-in-flight state — is still
        // EXPRESSIBLE through the multi-write, publishes the server side, and
        // deliberately leaves the client alone (minting one needs a token;
        // dropping one would kill in-flight requests while the user side is
        // momentarily missing — see updateSession's KDoc).
        val engine = newEngine()

        engine.updateSession(server("s1"), null)

        assertNull(engine.session.first(), "no identity until the user lands")
        assertEquals(server("s1"), engine.currentServer.first())
        assertNull(engine.currentUser.first())
        assertNull(engine.api, "the intermediate must not mint a client")

        // The user landing completes the identity — and the rebuild targets
        // the client at the pair (pin (b)'s covered path).
        engine.updateSession(server("s1"), user("u1"))
        assertEquals(ActiveSession(server("s1"), user("u1")), engine.session.first())
        assertTrue(engine.api != null, "the user landing rebuilt the client for the pair")
    }

    @Test
    fun `updateSession to null clears the pair and the client in one step`() = runBlocking {
        val engine = newEngine()
        val emissions = mutableListOf<ActiveSession?>()
        val collector = launch(Dispatchers.Unconfined) { engine.session.collect { emissions.add(it) } }
        try {
            engine.updateSession(server("s1"), user("u1"))
            engine.updateSession(null, null)

            assertNull(engine.session.first())
            assertNull(engine.currentServer.first())
            assertNull(engine.currentUser.first())
            assertNull(engine.api, "the fully-cleared publish drops the client (disconnect shape)")
            assertEquals(
                listOf(null, ActiveSession(server("s1"), user("u1")), null),
                emissions,
            )
        } finally {
            collector.cancel()
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun `requireUserId throws Not authenticated and currentUserId is null without a session`() {
        // requireUserId(): IllegalStateException, message "Not authenticated".
        val engine = newEngine()

        assertNull(engine.currentUserId())
        val error = assertFailsWith<IllegalStateException> { engine.requireUserId() }
        assertEquals("Not authenticated", error.message)
    }

    @Test
    fun `user id accessors read the atomic session, never the bare currentUser flow`() = runBlocking {
        // updateSession(null, user) leaves currentUser set while the atomic
        // session collapses to null ("user without server is no identity",
        // pinned above) — the accessors follow the session, so the
        // hand-rolled `currentUser.value?.id` guards they replace cannot
        // treat that partial state as an authenticated identity.
        val engine = newEngine()
        engine.updateSession(server("s1"), user("u1"))
        assertEquals("u1", engine.requireUserId())
        assertEquals("u1", engine.currentUserId())

        engine.updateSession(null, user("u1"))
        assertNull(engine.currentUserId())
        val error = assertFailsWith<IllegalStateException> { engine.requireUserId() }
        assertEquals("Not authenticated", error.message)
        Unit
    }
}
