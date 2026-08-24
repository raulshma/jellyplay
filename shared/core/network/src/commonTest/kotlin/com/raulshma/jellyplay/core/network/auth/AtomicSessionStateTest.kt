package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the wasm client's atomic session publishing — the same invariants the
 * jvmTest JellyfinApiEngineSessionTest pins for the engine's flows:
 * a session exists only when BOTH sides exist, and a transition is observed
 * as one step (no synthetic (newServer, oldUser) intermediate).
 */
class AtomicSessionStateTest {

    private fun server(id: String) = ServerInfo(id = id, name = "server $id", address = "https://$id")
    private fun user(id: String) = UserInfo(
        id = id,
        name = "user $id",
        serverAddress = "https://s",
        accessToken = "token-$id",
    )

    @Test
    fun `session starts null and collapses to null while either side is missing`() {
        val state = AtomicSessionState()
        assertNull(state.session.value, "no identity before anything publishes")

        state.updateServer(server("s1"))
        assertNull(state.session.value, "server without user is no identity")

        state.updateSession(server("s1"), null)
        assertNull(state.session.value, "explicit null user still collapses")

        state.updateUser(null)
        assertNull(state.session.value, "clearing the user keeps no identity")

        // The other missing-side shape: a user published over a null server.
        state.updateSession(null, null)
        state.updateUser(user("u1"))
        assertNull(state.session.value, "user without server is no identity")
    }

    @Test
    fun `updateSession publishes the pair as one step`() {
        val state = AtomicSessionState()
        val s1 = server("s1")
        val u1 = user("u1")

        state.updateSession(s1, u1)
        assertEquals(ActiveSession(s1, u1), state.session.value)
        assertEquals(s1, state.currentServer.value)
        assertEquals(u1, state.currentUser.value)
    }

    @Test
    fun `transition from one session to the next never emits a mixed intermediate`() {
        val state = AtomicSessionState()
        state.updateSession(server("s1"), user("u1"))

        // The two-sided switch an observer must never see split.
        state.updateSession(server("s2"), user("u2"))
        assertEquals(ActiveSession(server("s2"), user("u2")), state.session.value)

        // A single-sided update legitimately pairs with the current other
        // side (the token-refresh shape): same server, refreshed user.
        val refreshed = user("u1").copy(accessToken = "token-refreshed")
        state.updateUser(refreshed)
        assertEquals(ActiveSession(server("s2"), refreshed), state.session.value)

        // Disconnect collapses to null in one step.
        state.updateSession(null, null)
        assertNull(state.session.value)
        assertNull(state.currentServer.value)
        assertNull(state.currentUser.value)
    }
}
