package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the engine's atomic session publish — the flow [HomeSession]
 * (core:data) classifies identity transitions from. A cross-server switch
 * performed via [JellyfinApiEngine.updateSession] must never surface the
 * synthetic `(newServer, oldUser)` pair that two separate
 * `updateServer`/`updateUser` calls produce (the pre-HomeSession observers
 * combined the two separate flows and misclassified that intermediate as a
 * real identity).
 *
 * Harness mirrors [JellyfinApiEngineRetryTest]: a real engine over relaxed
 * mock collaborators. The session collector runs on `Dispatchers.Unconfined`
 * so every `_session.value = …` assignment is delivered synchronously and
 * the recorded emission sequence is deterministic.
 */
class JellyfinApiEngineSessionTest {

    private fun newEngine() = JellyfinApiEngine(
        mockk(relaxed = true),
        dagger.Lazy { mockk<Jellyfin>(relaxed = true) },
        dagger.Lazy { OkHttpClient() },
        DeviceProfileProvider(DeviceCodecCapabilities()),
        ServerAddressRouter(),
    )

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

        engine.updateServer(server("s1"))
        assertNull("server without user is no identity", engine.session.first())

        engine.updateServer(null)
        engine.updateUser(user("u1"))
        assertNull("user without server is no identity", engine.session.first())
    }

    @Test
    fun `updateSession publishes both sides and the pair atomically`() = runBlocking {
        val engine = newEngine()
        val emissions = mutableListOf<ActiveSession?>()
        val collector = launch(Dispatchers.Unconfined) { engine.session.collect { emissions.add(it) } }
        try {
            engine.updateServer(server("s1"))
            engine.updateUser(user("u1"))
            assertEquals(ActiveSession(server("s1"), user("u1")), engine.session.first())

            // The login/switch shape: adopt the new server AND its user in
            // ONE critical-section step.
            engine.updateSession(server("s2"), user("u2"))

            assertEquals(ActiveSession(server("s2"), user("u2")), engine.session.first())
            assertEquals(
                "no mixed (newServer, oldUser) intermediate may surface",
                listOf(null, ActiveSession(server("s1"), user("u1")), ActiveSession(server("s2"), user("u2"))),
                emissions,
            )
            assertEquals(server("s2"), engine.currentServer.first())
            assertEquals(user("u2"), engine.currentUser.first())
        } finally {
            collector.cancel()
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun `two separate single-side updates DO produce the mixed intermediate`() = runBlocking {
        // Documents why the switch paths must use updateSession: pairing a
        // single-side update with the current other side surfaces
        // (newServer, oldUser) — legitimate for same-identity refreshes
        // (address failover, token refresh) but wrong for session switches.
        val engine = newEngine()
        val emissions = mutableListOf<ActiveSession?>()
        val collector = launch(Dispatchers.Unconfined) { engine.session.collect { emissions.add(it) } }
        try {
            engine.updateServer(server("s1"))
            engine.updateUser(user("u1"))
            engine.updateServer(server("s2"))
            engine.updateUser(user("u2"))

            assertEquals(
                listOf(null, ActiveSession(server("s1"), user("u1")), ActiveSession(server("s2"), user("u1")), ActiveSession(server("s2"), user("u2"))),
                emissions,
            )
        } finally {
            collector.cancel()
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun `updateSession to null clears the pair in one step`() = runBlocking {
        val engine = newEngine()
        val emissions = mutableListOf<ActiveSession?>()
        val collector = launch(Dispatchers.Unconfined) { engine.session.collect { emissions.add(it) } }
        try {
            engine.updateSession(server("s1"), user("u1"))
            engine.updateSession(null, null)

            assertNull(engine.session.first())
            assertNull(engine.currentServer.first())
            assertNull(engine.currentUser.first())
            assertEquals(
                listOf(null, ActiveSession(server("s1"), user("u1")), null),
                emissions,
            )
        } finally {
            collector.cancel()
            coroutineContext.cancelChildren()
        }
    }
}
