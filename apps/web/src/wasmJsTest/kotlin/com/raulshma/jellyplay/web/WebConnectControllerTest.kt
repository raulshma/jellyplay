package com.raulshma.jellyplay.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

/**
 * Browser-free unit cover for the web connect controller's DECISION logic
 * ([WebConnectController] — the AuthRepository-delegation shape). The
 * establishment choreography itself lives in the shared repository seam now
 * (WasmAuthRepository — pinned source-side by the core:data mirror tests);
 * what is pinned HERE is the controller's own contract:
 *
 *  - BOOT CHOREOGRAPHY: construction fires restoreSession exactly once on
 *    the side-effect scope (the desktop-shell boot precedent), and the URL
 *    seed resolves there too — Room server rows first, else the one-time
 *    legacy `web_last_server_url` migration read, else "" (terminal value,
 *    never left unresolved), with the legacy key CONSUMED (removed) after
 *    the read either way.
 *  - DELEGATION: probe/sign-in/logout route to repository
 *    probeServer/login/revokeServerSession with the probed server's ADDRESS
 *    (login normalizes internally — the controller must not pre-normalize).
 *  - CAPABILITY DECLARATION: the failure note is the load-bearing copy the
 *    connected card renders; a fresh declaration resets it SYNCHRONOUSLY
 *    (the swap-out race the SIDE-EFFECT OWNERSHIP KDoc describes), success
 *    never sets a note, and outcomes land on the controller's own
 *    sideEffectScope (asserted here by awaiting on the event loop, never by
 *    blocking).
 *  - FAIL-CLOSED SEED: a broken servers flow (the second-tab OPFS reality)
 *    and a broken prefs store both degrade to "" / no crash.
 *  - FLOW CONTRACT: isAuthenticated/currentServer/currentUser expose the
 *    repository's own flows (assertSame — no recombination wrapper).
 *
 * The tests wrap their bodies in [runTest] (kotlin.test rejects `suspend`
 * test functions on wasmJs; runTest keeps the controller's sideEffectScope
 * jobs on Dispatchers.Default interleaving against real time, which the
 * polling helper awaits). No browser, no fetch: hand-rolled fakes for
 * [AuthRepository] and [DataStore].
 */
class WebConnectControllerTest {

    // ── fakes ──────────────────────────────────────────────────────────────

    /**
     * The repository fake: scriptable outcomes for the delegated members,
     * counters for the choreography calls, `throw` stubs for everything the
     * landing flow never routes through the controller. Open for the one
     * test that scripts a failing restoreSession.
     */
    private open class FakeAuthRepository : AuthRepository {
        val events = mutableListOf<String>()

        var serversFlow: Flow<List<ServerInfo>> = MutableStateFlow(emptyList())
        val currentServerFlow: MutableStateFlow<ServerInfo?> = MutableStateFlow(null)
        val currentUserFlow: MutableStateFlow<UserInfo?> = MutableStateFlow(null)
        override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(false)
        override val currentServerUsers: StateFlow<List<UserInfo>> = MutableStateFlow(emptyList())

        var restoreCount = 0
            private set
        var revokeCount = 0
            private set
        var loginAddress: String? = null
            private set
        var loginUsername: String? = null
            private set
        var loginPassword: String? = null
            private set
        var probeArg: String? = null
            private set

        var capabilitiesResult: Result<Unit> = Result.success(Unit)

        override val servers: Flow<List<ServerInfo>> get() = serversFlow
        override val currentServer: Flow<ServerInfo?> get() = currentServerFlow
        override val currentUser: Flow<UserInfo?> get() = currentUserFlow

        override suspend fun addServer(address: String): Result<ServerInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun probeServer(address: String): Result<ServerInfo> {
            probeArg = address
            events += "probe"
            return Result.success(probeResult)
        }

        var probeResult: ServerInfo = ServerInfo(id = "s1", name = "Media", address = "http://media.example.com")

        override suspend fun removeServer(serverId: String) =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun switchServer(serverId: String): Result<Unit> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun addServerAddress(serverId: String, address: String): Result<Unit> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun removeServerAddress(serverId: String, address: String): Result<Unit> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun switchServerAddress(serverId: String, address: String): Result<Unit> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun login(serverAddress: String, username: String, password: String): Result<UserInfo> {
            loginAddress = serverAddress
            loginUsername = username
            loginPassword = password
            events += "login"
            return loginResult
        }

        var loginResult: Result<UserInfo> = Result.success(
            UserInfo(id = "u1", name = "alice", serverAddress = "http://media.example.com", accessToken = "t"),
        )

        override suspend fun isQuickConnectEnabled(): Result<Boolean> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun initiateQuickConnect(): Result<QuickConnectInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun pollQuickConnect(secret: String): Result<QuickConnectState> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun loginWithQuickConnect(serverAddress: String, secret: String): Result<UserInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun authorizeQuickConnect(code: String): Result<Boolean> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun restoreSession(): Result<Unit> {
            restoreCount += 1
            events += "restore"
            return Result.success(Unit)
        }

        override suspend fun refreshCurrentUser(): Result<UserInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun logout() {
            events += "logout"
        }

        override suspend fun revokeServerSession() {
            revokeCount += 1
            events += "revoke"
        }

        override suspend fun switchUser(userId: String): Result<Unit> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun removeUser(userId: String) =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun getUsersForServer(serverId: String): List<UserInfo> =
            throw UnsupportedOperationException("unused in WebConnectControllerTest")

        override suspend fun postCapabilities(): Result<Unit> {
            events += "capabilities"
            return capabilitiesResult
        }
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

    /**
     * Fresh controller per test: the sideEffectScope is instance-owned and
     * construction fires the boot jobs (restore + seed), so tests never
     * share jobs — they await the outcomes through the polling helper.
     */
    private fun controller(
        auth: FakeAuthRepository = FakeAuthRepository(),
        prefs: FakePrefsDataStore = FakePrefsDataStore(),
    ) = WebConnectController(authRepository = auth, userPrefs = prefs)

    /** Polls [condition] on the event loop until true or [timeoutMs] elapses. */
    private suspend fun awaitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (!condition()) {
            if (deadline.hasPassedNow()) throw AssertionError("condition not met within ${timeoutMs}ms")
            delay(25)
        }
    }

    private val legacyKey = stringPreferencesKey("web_last_server_url")

    // ── boot choreography: restore fires once at construction ──────────────

    @Test
    fun `construction restores the persisted session exactly once`() = runTest {
        val auth = FakeAuthRepository()
        controller(auth = auth)
        awaitUntil { auth.restoreCount >= 1 }
        delay(200)
        assertEquals(1, auth.restoreCount, "restoreSession fires exactly once per page, on the side-effect scope")
    }

    // ── delegation: probe / sign-in / logout route to the repository ───────

    @Test
    fun `probeServer delegates to the repository probe verbatim`() = runTest {
        val auth = FakeAuthRepository()
        val controller = controller(auth = auth)
        val result = controller.probeServer("http://media.example.com")
        assertEquals("http://media.example.com", auth.probeArg, "the raw field text routes through untouched")
        assertTrue(result.isSuccess)
        assertEquals(auth.probeResult, result.getOrNull())
    }

    @Test
    fun `signIn delegates to repository login with the probed server address`() = runTest {
        val auth = FakeAuthRepository()
        val controller = controller(auth = auth)
        val probed = ServerInfo(id = "s1", name = "Media", address = "http://media.example.com")
        val result = controller.signIn(probed, username = "alice", password = "pw")
        assertEquals("http://media.example.com", auth.loginAddress, "login receives the probed server's address")
        assertEquals("alice", auth.loginUsername)
        assertEquals("pw", auth.loginPassword)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `logout delegates to repository revokeServerSession`() = runTest {
        val auth = FakeAuthRepository()
        val controller = controller(auth = auth)
        controller.logout()
        assertEquals(1, auth.revokeCount, "the full revoke/remove/disconnect/clear choreography is the repository's")
        assertEquals(listOf("revoke"), auth.events.filter { it == "revoke" })
    }

    // ── capability declaration (sideEffectScope outcomes) ──────────────────

    @Test
    fun `failed capability declaration surfaces the exact connected-card note`() = runTest {
        val auth = FakeAuthRepository()
        val controller = controller(auth = auth)
        auth.capabilitiesResult = Result.failure(RuntimeException("503"))
        controller.declareCapabilitiesAfterSignIn()
        awaitUntil { controller.capabilityNote.value != null }
        assertEquals(
            "Capability registration failed; some playback features may misbehave.",
            controller.capabilityNote.value,
            "the note copy is load-bearing UI text on the connected card",
        )
        assertEquals(listOf("capabilities"), auth.events.filter { it == "capabilities" })
    }

    @Test
    fun `a new declaration resets the note synchronously and success keeps it null`() = runTest {
        val auth = FakeAuthRepository()
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

    // ── URL seed: Room rows first, legacy migration second, terminal "" ────

    @Test
    fun `serverUrlSeed takes the first server row the repository reports`() = runTest {
        val auth = FakeAuthRepository()
        // The servers flow arrives in the DAO's lastConnected-DESC order (the
        // repository never re-sorts) — the seed takes that FIRST row.
        auth.serversFlow = MutableStateFlow(
            listOf(
                ServerInfo(id = "s2", name = "Recent", address = "http://recent.example.com"),
                ServerInfo(id = "s1", name = "Old", address = "http://old.example.com"),
            ),
        )
        val prefs = FakePrefsDataStore(
            emptyPreferences().toMutablePreferences().apply { this[legacyKey] = "http://legacy.example.com" }.toPreferences(),
        )
        val controller = controller(auth = auth, prefs = prefs)
        awaitUntil { controller.serverUrlSeed.value != null }
        assertEquals("http://recent.example.com", controller.serverUrlSeed.value, "the first row of the lastConnected-DESC flow wins")
        awaitUntil { prefs.backing.value[legacyKey] == null }
        assertNull(prefs.backing.value[legacyKey], "the legacy key is consumed even when a Room row won")
    }

    @Test
    fun `serverUrlSeed migrates the legacy web_last_server_url once, then consumes it`() = runTest {
        val prefs = FakePrefsDataStore(
            emptyPreferences().toMutablePreferences().apply { this[legacyKey] = "http://media.example.com" }.toPreferences(),
        )
        val controller = controller(prefs = prefs)
        awaitUntil { controller.serverUrlSeed.value != null }
        assertEquals("http://media.example.com", controller.serverUrlSeed.value)
        awaitUntil { prefs.backing.value[legacyKey] == null }
        assertNull(prefs.backing.value[legacyKey], "the migration read is one-time — the key must be gone")
    }

    @Test
    fun `serverUrlSeed resolves terminal empty when nothing is persisted`() = runTest {
        val controller = controller()
        awaitUntil { controller.serverUrlSeed.value != null }
        assertEquals("", controller.serverUrlSeed.value, "terminal emission — the one-shot collector must never park")
    }

    @Test
    fun `serverUrlSeed degrades to empty when the Room read fails`() = runTest {
        val auth = FakeAuthRepository()
        auth.serversFlow = flow { throw RuntimeException("OPFS pool locked (second tab)") }
        val controller = controller(auth = auth)
        awaitUntil { controller.serverUrlSeed.value != null }
        assertEquals("", controller.serverUrlSeed.value, "a broken DB read must yield an empty field, not a crash")
    }

    @Test
    fun `serverUrlSeed degrades to empty when the prefs read fails`() = runTest {
        val prefs = FakePrefsDataStore()
        prefs.failReads = true
        val controller = controller(prefs = prefs)
        awaitUntil { controller.serverUrlSeed.value != null }
        assertEquals("", controller.serverUrlSeed.value, "a broken store must yield an empty field, not a crash")
    }

    @Test
    fun `consuming the legacy key swallows a broken store instead of crashing the scope`() = runTest {
        val prefs = FakePrefsDataStore(
            emptyPreferences().toMutablePreferences().apply { this[legacyKey] = "http://media.example.com" }.toPreferences(),
        )
        prefs.failWrites = true
        val controller = controller(prefs = prefs)
        // Fire-and-forget: the consume-write failure must be contained inside
        // the side-effect job (reaching the assertion IS the crash-freedom
        // proof; an uncaught throw would tear down the surrounding job tree).
        awaitUntil { controller.serverUrlSeed.value != null }
        assertEquals("http://media.example.com", controller.serverUrlSeed.value, "the seed still lands from the read")
        delay(300)
        assertEquals(
            "http://media.example.com",
            prefs.backing.value[legacyKey],
            "no half-write may land from a failing store — the key simply stays for the next boot",
        )
    }

    // ── the flow contract ───────────────────────────────────────────────────

    @Test
    fun `observability flows expose the repository's own flows, not recombinations`() {
        val auth = FakeAuthRepository()
        val controller = controller(auth = auth)
        assertSame(auth.isAuthenticated, controller.isAuthenticated, "the gate is the repository's atomic-derived StateFlow")
        assertSame(auth.currentServer, controller.currentServer)
        assertSame(auth.currentUser, controller.currentUser)
    }

    @Test
    fun `construction is safe when restore throws`() = runTest {
        val auth = object : FakeAuthRepository() {
            override suspend fun restoreSession(): Result<Unit> = Result.failure(RuntimeException("no identity"))
        }
        val controller = controller(auth = auth)
        awaitUntil { controller.serverUrlSeed.value != null }
        assertFalse(controller.isAuthenticated.value, "a failed restore degrades fail-closed to the sign-in pane")
    }
}
