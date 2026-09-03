package com.raulshma.jellyplay.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Browser-free unit cover for the web connect controller's DECISION logic
 * ([WebConnectController] — wave 12C slice 2). The controller is the web
 * shell's entire auth spine (no AuthRepository on wasm), and the behaviors
 * pinned here are exactly the ones whose mistakes only show up in a real
 * browser session:
 *
 *  - LOGOUT SHAPE: revoke is BEST-EFFORT (failure and throw both degrade to
 *    `false`, never an error), the local disconnect happens UNCONDITIONALLY
 *    and AFTER the revoke attempt (the connected card unmounts either way —
 *    the documented v1 gap), and a successful revoke reports true.
 *  - CAPABILITY DECLARATION: the failure note is the load-bearing copy the
 *    connected card renders; a fresh declaration resets it SYNCHRONOUSLY
 *    (the swap-out race the SIDE-EFFECT OWNERSHIP KDoc describes), success
 *    never sets a note, and outcomes land on the controller's own
 *    sideEffectScope (asserted here by awaiting on the event loop, never by
 *    blocking).
 *  - LAST-SERVER-URL PERSISTENCE: the exact storage key is a wire contract
 *    with the shared "user_prefs" DataStore; reads AND fire-and-forget
 *    writes degrade silently when the store is broken.
 *
 * The tests are suspend functions (kotlin.test runs them on the Kotlin Node
 * runner's single JS thread, so the controller's sideEffectScope jobs
 * interleave exactly like in the page). Honesty note (extends
 * WebShellPureHelpersTest's): the transport-failure classifier
 * (`isLikelyCorsOrTransport`) and the friendly-error mappers stay out of
 * reach — they are private to WebConnectFlow.kt and main sources are
 * off-limits for this wave. No browser, no fetch: hand-rolled fakes for
 * [AuthApiClient] and [DataStore].
 */
class WebConnectControllerTest {

    // ── fakes ──────────────────────────────────────────────────────────────

    /**
     * Records the revoke/disconnect call ORDER (the logout contract is
     * "revoke attempt, THEN unconditional disconnect") and lets each test
     * script the capabilities and revoke outcomes.
     */
    private class FakeAuthApiClient : AuthApiClient {
        val events = mutableListOf<String>()

        var capabilitiesResult: Result<Unit> = Result.success(Unit)
        var revokeResult: Result<Unit> = Result.success(Unit)
        var revokeThrows: Throwable? = null

        var disconnectCount = 0
            private set

        override val currentServer: Flow<ServerInfo?> = MutableStateFlow(null)
        override val currentUser: Flow<UserInfo?> = MutableStateFlow(null)
        override val session: Flow<ActiveSession?> = MutableStateFlow(null)

        override suspend fun connectToServer(address: String): Result<ServerInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun getServerInfo(address: String): Result<ServerInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun selectReachableAddress(): String? =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun authenticateUser(serverAddress: String, username: String, password: String): Result<UserInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun authenticateUser(serverInfo: ServerInfo, username: String, password: String): Result<UserInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun setServer(serverInfo: ServerInfo) =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun setUser(userInfo: UserInfo) =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun disconnect() {
            disconnectCount += 1
            events += "disconnect"
        }

        override suspend fun isQuickConnectEnabled(): Result<Boolean> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun getQuickConnectState(secret: String): Result<QuickConnectState> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun authenticateWithQuickConnect(serverInfo: ServerInfo, secret: String): Result<UserInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun authorizeQuickConnect(code: String): Result<Boolean> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun postCapabilities(): Result<Unit> {
            events += "capabilities"
            return capabilitiesResult
        }

        override suspend fun revokeServerSession(): Result<Unit> {
            events += "revoke"
            revokeThrows?.let { throw it }
            return revokeResult
        }

        override fun getServerUrl(): String? = null

        override fun getAccessToken(): String? = null
    }

    /** In-memory user_prefs store with per-test break switches for degradation. */
    private class FakePrefsDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        var failReads = false
        var failWrites = false

        val backing = MutableStateFlow(initial)

        override val data: Flow<Preferences>
            get() = if (failReads) flow { throw RuntimeException("storage broken") } else backing

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrites) throw RuntimeException("storage broken")
            val next = transform(backing.value)
            backing.value = next
            return next
        }
    }

    /** Fresh controller per test: the sideEffectScope is instance-owned, so tests never share jobs. */
    private fun controller(auth: FakeAuthApiClient = FakeAuthApiClient(), prefs: FakePrefsDataStore = FakePrefsDataStore()) =
        WebConnectController(auth, prefs)

    /** Polls [condition] on the event loop until true or [timeoutMs] elapses. */
    private suspend fun awaitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (!condition()) {
            if (deadline.hasPassedNow()) throw AssertionError("condition not met within ${timeoutMs}ms")
            delay(25)
        }
    }

    // ── logout: best-effort revoke + unconditional disconnect ──────────────

    @Test
    suspend fun `logout reports success and disconnects after the revoke`() {
        val auth = FakeAuthApiClient()
        val controller = controller(auth = auth)
        assertTrue(controller.logout(), "a successful revoke must report true")
        assertEquals(listOf("revoke", "disconnect"), auth.events, "disconnect must follow the revoke attempt")
        assertEquals(1, auth.disconnectCount, "exactly one disconnect — no double-clear of the session pair")
    }

    @Test
    suspend fun `logout degrades to false on a refused revoke but still disconnects`() {
        val auth = FakeAuthApiClient()
        val controller = controller(auth = auth)
        auth.revokeResult = Result.failure(RuntimeException("server refused"))
        assertFalse(controller.logout(), "a failed revoke must report false")
        assertEquals(listOf("revoke", "disconnect"), auth.events)
        assertEquals(1, auth.disconnectCount, "the local pair clears even when the server revocation failed")
    }

    @Test
    suspend fun `logout degrades to false on a throwing revoke but still disconnects`() {
        val auth = FakeAuthApiClient()
        val controller = controller(auth = auth)
        auth.revokeThrows = RuntimeException("transport exploded")
        assertFalse(controller.logout(), "a thrown revoke must degrade to false, never propagate")
        assertEquals(listOf("revoke", "disconnect"), auth.events)
        assertEquals(1, auth.disconnectCount)
    }

    // ── capability declaration (sideEffectScope outcomes) ──────────────────

    @Test
    suspend fun `failed capability declaration surfaces the exact connected-card note`() {
        val auth = FakeAuthApiClient()
        val controller = controller(auth = auth)
        auth.capabilitiesResult = Result.failure(RuntimeException("503"))
        controller.declareCapabilitiesAfterSignIn()
        awaitUntil { controller.capabilityNote.value != null }
        assertEquals(
            "Capability registration failed; some playback features may misbehave.",
            controller.capabilityNote.value,
            "the note copy is load-bearing UI text on the connected card",
        )
        assertEquals(listOf("capabilities"), auth.events)
    }

    @Test
    suspend fun `a new declaration resets the note synchronously and success keeps it null`() {
        val auth = FakeAuthApiClient()
        val controller = controller(auth = auth)
        auth.capabilitiesResult = Result.failure(RuntimeException("503"))
        controller.declareCapabilitiesAfterSignIn()
        awaitUntil { controller.capabilityNote.value != null }

        // Second sign-in with a healthy server: the reset happens on the
        // CALLER's thread (before the side-effect job launches) so a stale
        // failure note can never survive the swap into the connected card.
        auth.capabilitiesResult = Result.success(Unit)
        controller.declareCapabilitiesAfterSignIn()
        assertNull(controller.capabilityNote.value, "the reset must be synchronous")
        delay(300) // let the success job land: it must not write a note
        assertNull(controller.capabilityNote.value, "a successful declaration never sets a note")
    }

    // ── last-server-url persistence (shared user_prefs DataStore) ──────────

    @Test
    suspend fun `lastServerUrl reads the exact web_last_server_url storage key`() {
        val key = stringPreferencesKey("web_last_server_url")
        val prefs: Preferences = emptyPreferences().toMutablePreferences().apply {
            this[key] = "http://media.example.com"
        }.toPreferences()
        val controller = controller(prefs = FakePrefsDataStore(prefs))
        assertEquals("http://media.example.com", controller.lastServerUrl())
    }

    @Test
    suspend fun `lastServerUrl is null when nothing was persisted`() {
        val controller = controller()
        assertNull(controller.lastServerUrl())
    }

    @Test
    suspend fun `lastServerUrl degrades to null when the store read fails`() {
        val prefs = FakePrefsDataStore()
        prefs.failReads = true
        val controller = controller(prefs = prefs)
        assertNull(controller.lastServerUrl(), "a broken store must yield an empty field, not a crash")
    }

    @Test
    suspend fun `rememberServerUrlLater persists the probed URL on the side-effect scope`() {
        val key = stringPreferencesKey("web_last_server_url")
        val prefs = FakePrefsDataStore()
        val controller = controller(prefs = prefs)
        controller.rememberServerUrlLater("http://media.example.com")
        awaitUntil { prefs.backing.value[key] == "http://media.example.com" }
    }

    @Test
    suspend fun `rememberServerUrlLater swallows a broken store instead of crashing the scope`() {
        val key = stringPreferencesKey("web_last_server_url")
        val prefs = FakePrefsDataStore()
        prefs.failWrites = true
        val controller = controller(prefs = prefs)
        // Fire-and-forget: the write failure must be contained inside the
        // side-effect job (reaching the assertion IS the crash-freedom proof;
        // an uncaught throw would tear down the surrounding job tree).
        controller.rememberServerUrlLater("http://media.example.com")
        delay(300)
        assertNull(prefs.backing.value[key], "no half-write may land from a failing store")
    }

    // ── the session flow contract ───────────────────────────────────────────

    @Test
    fun `session exposes the client atomic session flow, not a recombination`() {
        val auth = FakeAuthApiClient()
        val controller = controller(auth = auth)
        assertSame(auth.session, controller.session, "the UI must read the client's combined atomic pair")
    }
}
