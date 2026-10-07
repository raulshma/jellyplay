package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayDevicePush
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayPushRegistration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

/**
 * Exercises [JellyPushRepository]'s state machine against a fake distributor
 * seam, a real probe store (driven through the capabilities handshake) and a
 * real on-disk DataStore — the jvmTest repository-suite pattern. The
 * load-bearing invariants:
 *  - enable asks the distributor ONLY when one exists; without the seam
 *    (desktop) or an installed distributor app the machine parks on
 *    NoDistributor and nothing is sent anywhere;
 *  - onNewEndpoint is the rotation path: persist the endpoint locally (the
 *    reserved per-device pref) AND re-POST `registerDevice` WITH the push
 *    field (same device id, kind `generic`);
 *  - disable / onUnregistered both DETACH server-side (the explicit push-off
 *    half, `JellyPlayDevicePush.Detach`) — and disable additionally drops the
 *    distributor registration and the persisted endpoint;
 *  - onRegistrationFailed only unwinds a REGISTERING machine (never one that
 *    already holds an endpoint);
 *  - start() restores from the persisted endpoint by re-POSTing WITH push —
 *    the freshening repair after the events session's plain registration —
 *    and rides the gate: toggle off detaches, probe-without-push parks on
 *    ServerPushOff touching nothing.
 *
 * Scheduler discipline: every test builds its store and repository on
 * `runTest`'s own [kotlinx.coroutines.test.TestScope.backgroundScope] — one
 * scheduler per test, so DataStore reads/writes and the gate collector are all
 * virtual-time work `advanceUntilIdle()` drives deterministically.
 */
class JellyPushRepositoryTest {

    // ── fakes ───────────────────────────────────────────────────────────

    private class FakeDistributor : JellyPushDistributor {
        var hasDistributor = true
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()

        override fun hasDistributor(): Boolean = hasDistributor
        override fun register(instance: String) {
            registered.add(instance)
        }

        override fun unregister(instance: String) {
            unregistered.add(instance)
        }
    }

    private lateinit var api: JellyPlayPluginApiClient
    private lateinit var statusStore: JellyPlayPluginStatusStore
    private lateinit var dataStoreFile: okio.Path

    @BeforeTest
    fun setup() {
        api = mockk(relaxUnitFun = true)
        coEvery { api.registerDevice(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        statusStore = JellyPlayPluginStatusStore(
            apiClient = api,
            sessionCacheRegistry = SessionCacheRegistry(FakeSessionIdentity(), CoroutineScope(Dispatchers.Default)),
        )
        // Unique per test run: AndroidX forbids two DataStore instances on one
        // file in the same process.
        val storeDir = File(File(System.getProperty("java.io.tmpdir"), "jellypush-test"), "run-${System.nanoTime()}")
            .apply { mkdirs() }
        dataStoreFile = File(storeDir, "push.preferences_pb").absolutePath.toPath()
    }

    @AfterTest
    fun teardown() {
        dataStoreFile.toFile().delete()
    }

    /** Drives the capability probe so the store reads AVAILABLE (with [features]). */
    private suspend fun makeAvailable(features: List<String> = listOf(JellyPlayPluginFeatures.Push)) {
        coEvery { api.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(contractVersion = 1, pluginVersion = "test", features = features),
        )
        statusStore.refresh()
    }

    private fun newDataStore(scope: CoroutineScope) = PreferenceDataStoreFactory.createWithPath(
        scope = scope,
        produceFile = { dataStoreFile },
    )

    private fun featureGate(gateFlow: MutableStateFlow<Boolean>) = mockk<JellyPlayFeatureGate> {
        every { isAvailable(JellyPlayPluginFeatures.Push) } returns gateFlow
        // The fresh one-shot read the receiver's late-endpoint guard takes —
        // pinned to the same flow so the two arms agree.
        coEvery { isAvailableNow(JellyPlayPluginFeatures.Push) } answers { gateFlow.value }
    }

    private fun repo(
        scope: CoroutineScope,
        distributor: JellyPushDistributor?,
        datastore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        gateFlow: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): JellyPushRepository =
        JellyPushRepository(
            apiClient = api,
            statusStore = statusStore,
            featureGate = featureGate(gateFlow),
            dataStore = datastore,
            scope = scope,
            deviceName = "Test device",
            devicePlatform = "desktop",
            appVersion = "test",
            deviceIdProvider = { "device-1" },
            distributor = distributor,
        )

    private suspend fun persistedEndpoint(
        datastore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    ): String? = datastore.data.first()[ENDPOINT_KEY]

    /**
     * Waits (real time — DataStore hops to Dispatchers.IO internally, which no
     * amount of virtual-time advancing can order against the assertions) until
     * the machine reaches [predicate]'s state, then returns it.
     */
    private suspend fun awaitState(
        repo: JellyPushRepository,
        timeoutMs: Long = 10_000,
        predicate: (JellyPushState) -> Boolean,
    ): JellyPushState {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!predicate(repo.state.value)) {
            if (System.nanoTime() >= deadline) break
            withContext(Dispatchers.IO) { Thread.sleep(10) }
        }
        return repo.state.value
    }

    // ── enable ──────────────────────────────────────────────────────────

    @Test
    fun `enable asks the distributor and parks on Registering`() = runTest {
        makeAvailable()
        val distributor = FakeDistributor()
        val repo = repo(backgroundScope, distributor, newDataStore(backgroundScope))

        repo.enable()

        assertEquals(JellyPushState.Registering, repo.state.value)
        assertEquals(listOf(JellyPushDistributor.INSTANCE_DEFAULT), distributor.registered)
        assertTrue(distributor.unregistered.isEmpty())
    }

    @Test
    fun `enable without an installed distributor parks on NoDistributor and asks nothing`() = runTest {
        makeAvailable()
        val distributor = FakeDistributor().apply { hasDistributor = false }
        val repo = repo(backgroundScope, distributor, newDataStore(backgroundScope))

        repo.enable()

        assertEquals(JellyPushState.NoDistributor, repo.state.value)
        assertTrue(distributor.registered.isEmpty())
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `enable on the desktop seam (no distributor at all) parks on NoDistributor`() = runTest {
        makeAvailable()
        val repo = repo(backgroundScope, distributor = null, datastore = newDataStore(backgroundScope))

        repo.enable()

        assertEquals(JellyPushState.NoDistributor, repo.state.value)
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `enable is a no-op once registered`() = runTest {
        makeAvailable()
        val distributor = FakeDistributor()
        val repo = repo(backgroundScope, distributor, newDataStore(backgroundScope))

        repo.onNewEndpoint("https://ntfy.example/endpoint-1")
        repo.enable()

        assertEquals(JellyPushState.Registered("https://ntfy.example/endpoint-1"), repo.state.value)
        // The machine holds an endpoint — the distributor is never asked again.
        assertTrue(distributor.registered.isEmpty())
    }

    // ── onNewEndpoint (the rotation path) ───────────────────────────────

    @Test
    fun `onNewEndpoint persists locally and re-posts the device registration with push`() = runTest {
        makeAvailable()
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, FakeDistributor(), datastore)

        repo.onNewEndpoint("https://ntfy.example/endpoint-1")

        assertEquals(JellyPushState.Registered("https://ntfy.example/endpoint-1"), repo.state.value)
        assertEquals("https://ntfy.example/endpoint-1", persistedEndpoint(datastore))
        coVerify(exactly = 1) {
            api.registerDevice(
                "device-1", "Test device", "desktop", "test",
                JellyPlayDevicePush.Attach(
                    JellyPlayPushRegistration(kind = "generic", endpoint = "https://ntfy.example/endpoint-1"),
                ),
                // The caps assertion rides every registration (the wire
                // REPLACES caps) — the silent-push opt-in is pinned here.
                eq(listOf("silent-push")),
            )
        }
    }

    @Test
    fun `a new endpoint rotates the registration and the persisted value`() = runTest {
        makeAvailable()
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, FakeDistributor(), datastore)

        repo.onNewEndpoint("https://ntfy.example/endpoint-1")
        repo.onNewEndpoint("https://ntfy.example/endpoint-2")

        assertEquals(JellyPushState.Registered("https://ntfy.example/endpoint-2"), repo.state.value)
        assertEquals("https://ntfy.example/endpoint-2", persistedEndpoint(datastore))
        coVerify(exactly = 1) {
            api.registerDevice(
                any(), any(), any(), any(),
                JellyPlayDevicePush.Attach(
                    JellyPlayPushRegistration(kind = "generic", endpoint = "https://ntfy.example/endpoint-2"),
                ),
                any(),
            )
        }
    }

    @Test
    fun `a late endpoint after an explicit disable does not re-arm the machine`() = runTest {
        makeAvailable()
        val gateFlow = MutableStateFlow(false) // the user toggled off mid-flight
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, FakeDistributor(), datastore, gateFlow = gateFlow)

        repo.onNewEndpoint("https://ntfy.example/late-endpoint")

        assertEquals(JellyPushState.Unregistered, repo.state.value)
        assertNull(persistedEndpoint(datastore))
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any(), any(), any()) }
    }

    // ── disable / onUnregistered (the detach paths) ─────────────────────

    @Test
    fun `disable detaches server-side drops the distributor and clears the endpoint`() = runTest {
        makeAvailable()
        val distributor = FakeDistributor()
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, distributor, datastore)
        repo.onNewEndpoint("https://ntfy.example/endpoint-1")

        repo.disable()

        assertEquals(JellyPushState.Unregistered, repo.state.value)
        assertNull(persistedEndpoint(datastore))
        assertEquals(listOf(JellyPushDistributor.INSTANCE_DEFAULT), distributor.unregistered)
        coVerify(exactly = 1) { api.registerDevice("device-1", "Test device", "desktop", "test", JellyPlayDevicePush.Detach, any()) }
    }

    @Test
    fun `onUnregistered detaches server-side and surfaces NoDistributor`() = runTest {
        makeAvailable()
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, FakeDistributor(), datastore)
        repo.onNewEndpoint("https://ntfy.example/endpoint-1")

        repo.onUnregistered()

        assertEquals(JellyPushState.NoDistributor, repo.state.value)
        assertNull(persistedEndpoint(datastore))
        coVerify(exactly = 1) { api.registerDevice("device-1", "Test device", "desktop", "test", JellyPlayDevicePush.Detach, any()) }
    }

    @Test
    fun `onUnregistered under a closed gate posts nothing and keeps the endpoint`() = runTest {
        // The ServerPushOff park: probe dropped the push key, so an old plugin
        // must never see a push field and the persisted endpoint stays put.
        makeAvailable(features = listOf("events"))
        val gateFlow = MutableStateFlow(false)
        val datastore = newDataStore(backgroundScope)
        datastore.edit { it[ENDPOINT_KEY] = "https://ntfy.example/persisted" }
        val repo = repo(backgroundScope, FakeDistributor(), datastore, gateFlow = gateFlow)

        repo.onUnregistered()

        assertEquals("https://ntfy.example/persisted", persistedEndpoint(datastore))
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any(), any(), any()) }
    }

    // ── onRegistrationFailed ────────────────────────────────────────────

    @Test
    fun `registration failed unwinds a Registering machine to Unregistered`() = runTest {
        makeAvailable()
        val repo = repo(backgroundScope, FakeDistributor(), newDataStore(backgroundScope))

        repo.enable()
        repo.onRegistrationFailed(reason = "NETWORK")

        assertEquals(JellyPushState.Unregistered, repo.state.value)
    }

    @Test
    fun `registration failed never unwinds a registered machine`() = runTest {
        makeAvailable()
        val repo = repo(backgroundScope, FakeDistributor(), newDataStore(backgroundScope))

        repo.onNewEndpoint("https://ntfy.example/endpoint-1")
        repo.onRegistrationFailed(reason = "NETWORK")

        assertEquals(JellyPushState.Registered("https://ntfy.example/endpoint-1"), repo.state.value)
    }

    // ── start(): the restore + gate rider ───────────────────────────────

    @Test
    fun `start restores a persisted endpoint by re-posting with push`() = runTest {
        makeAvailable()
        val datastore = newDataStore(backgroundScope)
        datastore.edit { it[ENDPOINT_KEY] = "https://ntfy.example/persisted" }
        val repo = repo(backgroundScope, FakeDistributor(), datastore)

        repo.start()
        val state = awaitState(repo) { it != JellyPushState.Unregistered }

        assertEquals(JellyPushState.Registered("https://ntfy.example/persisted"), state)
        coVerify(exactly = 1) {
            api.registerDevice(
                "device-1", "Test device", "desktop", "test",
                JellyPlayDevicePush.Attach(
                    JellyPlayPushRegistration(kind = "generic", endpoint = "https://ntfy.example/persisted"),
                ),
                any(),
            )
        }
        repo.stop()
    }

    @Test
    fun `start with the gate on and no persisted endpoint asks the distributor`() = runTest {
        makeAvailable()
        val distributor = FakeDistributor()
        val repo = repo(backgroundScope, distributor, newDataStore(backgroundScope))

        repo.start()
        val state = awaitState(repo) { it != JellyPushState.Unregistered }

        assertEquals(JellyPushState.Registering, state)
        assertEquals(1, distributor.registered.size)
        repo.stop()
    }

    @Test
    fun `start with the gate off skips the restore attach even where the probe exposes push`() = runTest {
        makeAvailable() // probe exposes push
        val gateFlow = MutableStateFlow(false) // ...but the user's toggle reads off
        val datastore = newDataStore(backgroundScope)
        datastore.edit { it[ENDPOINT_KEY] = "https://ntfy.example/persisted" }
        val repo = repo(backgroundScope, FakeDistributor(), datastore, gateFlow = gateFlow)

        repo.start()
        val state = awaitState(repo) { it == JellyPushState.Unregistered }

        // No transient attach: the restore waits for the whole gate (probe AND
        // toggle), and the collector's closed edge (toggle off, key present)
        // detaches instead of attaching-then-tearing-down.
        assertEquals(JellyPushState.Unregistered, state)
        coVerify(exactly = 0) {
            api.registerDevice(any(), any(), any(), any(), ofType<JellyPlayDevicePush.Attach>(), any())
        }
        repo.stop()
    }

    @Test
    fun `the gate closing because the toggle went off detaches push`() = runTest {
        makeAvailable()
        val gateFlow = MutableStateFlow(true)
        val datastore = newDataStore(backgroundScope)
        val distributor = FakeDistributor()
        val repo = repo(backgroundScope, distributor, datastore, gateFlow = gateFlow)
        repo.onNewEndpoint("https://ntfy.example/endpoint-1")

        repo.start()
        gateFlow.value = false // user toggle off — the probe still exposes push
        val state = awaitState(repo) { it == JellyPushState.Unregistered }

        assertEquals(JellyPushState.Unregistered, state)
        coVerify(exactly = 1) { api.registerDevice("device-1", "Test device", "desktop", "test", JellyPlayDevicePush.Detach, any()) }
        assertEquals(1, distributor.unregistered.size)
        repo.stop()
    }

    @Test
    fun `the probe dropping the push key parks on ServerPushOff touching nothing`() = runTest {
        makeAvailable(features = listOf("events")) // no push key — old plugin / admin-off
        val gateFlow = MutableStateFlow(false) // probe AND toggle → gate closed
        val datastore = newDataStore(backgroundScope)
        datastore.edit { it[ENDPOINT_KEY] = "https://ntfy.example/persisted" }
        val distributor = FakeDistributor()
        val repo = repo(backgroundScope, distributor, datastore, gateFlow = gateFlow)

        repo.start()
        val state = awaitState(repo) { it == JellyPushState.ServerPushOff }

        assertEquals(JellyPushState.ServerPushOff, state)
        // The persisted endpoint and the distributor registration are left alone;
        // nothing was sent to the server (an old plugin must not see a push field).
        assertEquals("https://ntfy.example/persisted", persistedEndpoint(datastore))
        assertTrue(distributor.unregistered.isEmpty())
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any(), any(), any()) }
        repo.stop()
    }

    // ── failed POSTs (the wire half can fail under every path) ──────────

    @Test
    fun `a failed attach POST parks on Unregistered and keeps the endpoint for restore`() = runTest {
        makeAvailable()
        coEvery { api.registerDevice(any(), any(), any(), any(), any(), any()) } returns Result.failure(java.io.IOException("503"))
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, FakeDistributor(), datastore)

        repo.onNewEndpoint("https://ntfy.example/endpoint-1")

        assertEquals(JellyPushState.Unregistered, repo.state.value)
        assertEquals("https://ntfy.example/endpoint-1", persistedEndpoint(datastore))
    }

    @Test
    fun `a failed detach leaves the machine registered for retry`() = runTest {
        makeAvailable()
        val distributor = FakeDistributor()
        val datastore = newDataStore(backgroundScope)
        val repo = repo(backgroundScope, distributor, datastore)
        repo.onNewEndpoint("https://ntfy.example/endpoint-1")

        coEvery { api.registerDevice(any(), any(), any(), any(), any(), any()) } returns Result.failure(java.io.IOException("offline"))
        repo.disable()

        // The server still holds the endpoint — the row must keep reading
        // Registered and nothing local drops until a detach is confirmed.
        assertEquals(JellyPushState.Registered("https://ntfy.example/endpoint-1"), repo.state.value)
        assertEquals("https://ntfy.example/endpoint-1", persistedEndpoint(datastore))
        assertTrue(distributor.unregistered.isEmpty())
    }

    @Test
    fun `a failed restore re-post stays unregistered for the next cycle`() = runTest {
        makeAvailable()
        var attachAttempts = 0
        coEvery { api.registerDevice(any(), any(), any(), any(), any(), any()) } answers {
            attachAttempts++
            Result.failure(java.io.IOException("offline"))
        }
        val datastore = newDataStore(backgroundScope)
        datastore.edit { it[ENDPOINT_KEY] = "https://ntfy.example/persisted" }
        val repo = repo(backgroundScope, FakeDistributor(), datastore)

        repo.start()
        // The failed restore parks back on Unregistered ("the last one failed");
        // the gate arm then re-asks the distributor — Registering proves the
        // restore's failure handler completed WITHOUT claiming Registered.
        val state = awaitState(repo) { attachAttempts > 0 && it == JellyPushState.Registering }

        assertEquals(JellyPushState.Registering, state)
        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any(), ofType<JellyPlayDevicePush.Attach>(), any()) }
        assertEquals("https://ntfy.example/persisted", persistedEndpoint(datastore))
        repo.stop()
    }

    // ── harness bits ────────────────────────────────────────────────────

    private class FakeSessionIdentity : com.raulshma.jellyplay.core.data.session.SessionIdentityProvider {
        override val transitions: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.session.HomeSessionTransition> =
            kotlinx.coroutines.flow.MutableSharedFlow()
        override suspend fun currentIdentity(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override fun currentIdentitySnapshot(): com.raulshma.jellyplay.core.data.session.SessionIdentity? = null
        override suspend fun cacheIdentity(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
        override fun cacheIdentitySnapshot(): com.raulshma.jellyplay.core.model.CacheIdentity =
            com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN
    }

    private companion object {
        val ENDPOINT_KEY = stringPreferencesKey("jpsync.device.push.endpoint")
    }
}
