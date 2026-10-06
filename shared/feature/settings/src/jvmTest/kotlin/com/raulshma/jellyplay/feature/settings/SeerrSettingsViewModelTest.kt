package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrAuthenticator
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlaySeerrStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the Seerr settings wiring (LibraryLayout jvmTest pattern: mockk
 * collaborators + real Result/[MutableStateFlow] stubs + inlined
 * Main-dispatcher rule). The init block seeds the form + connection status
 * from the preferences and secure-credential stores (cookie-backed auth
 * included), connection tests persist before probing and route the Result
 * into the shared [ConnectionProbe.Status] board, blank input fails fast
 * without touching the repository, and toggles/disconnect route to the
 * preferences store.
 *
 * The via-server bridge half (ADR 0010) pins the mode persistence +
 * plugin-brokered link lifecycle: entering the mode persists the flag and
 * fetches `seerrStatus`, the Quick Connect flow authorizes through the
 * Jellyfin QC primitives and finalizes with the plugin's `seerrLogin`,
 * logout unlinks and re-reads the status, and failures land in
 * [SeerrSettingsViewModel.bridgeFailure] (reported verbatim or declared).
 *
 * The init block hops to `Dispatchers.IO` for the credential reads, so
 * assertions that depend on it use [awaitUntil] instead of a bare
 * `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeerrSettingsViewModelTest {

    /** Polls until [condition] holds, pumping the test scheduler between waits. */
    private suspend fun TestScope.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        advanceUntilIdle()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, "condition not met within ${timeoutMs}ms")
            withContext(Dispatchers.Default) { delay(10) }
            advanceUntilIdle()
        }
    }

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var seerrAuthenticator: SeerrAuthenticator
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var secureCredentialsStore: SeerrSecureCredentialsStore
    private lateinit var pluginApiClient: JellyPlayPluginApiClient
    private lateinit var pluginStatusStore: JellyPlayPluginStatusStore
    private lateinit var authRepository: AuthRepository
    private val preferencesState = MutableStateFlow(SeerrPreferences())
    private val pluginStatusState = MutableStateFlow(JellyPlayPluginStatus.UNAVAILABLE)
    private val pluginFeaturesState = MutableStateFlow<Set<String>>(emptySet())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        seerrAuthenticator = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        secureCredentialsStore = mockk(relaxed = true)
        pluginApiClient = mockk(relaxed = true)
        pluginStatusStore = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        every { seerrPreferencesStore.preferences } returns preferencesState
        every { pluginStatusStore.status } returns pluginStatusState
        every { pluginStatusStore.features } returns pluginFeaturesState
        every { secureCredentialsStore.getApiKey() } returns ""
        every { secureCredentialsStore.getPassword() } returns ""
        every { secureCredentialsStore.getSessionCookie() } returns ""
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * [withBridge] opts the VM into the plugin seams (the Koin factory always
     * passes them; the direct-construction default keeps them null — the
     * nullable-with-default pattern under test).
     */
    private fun viewModel(withBridge: Boolean = false) = SeerrSettingsViewModel(
        seerrAuthenticator,
        seerrPreferencesStore,
        secureCredentialsStore,
        pluginApiClient = pluginApiClient.takeIf { withBridge },
        pluginStatusStore = pluginStatusStore.takeIf { withBridge },
        authRepository = authRepository.takeIf { withBridge },
    )

    private fun seedPreferences(prefs: SeerrPreferences) {
        preferencesState.value = prefs
    }

    private fun stubBridgeStatus(linked: Boolean) {
        coEvery { pluginApiClient.seerrStatus() } returns Result.success(
            JellyPlaySeerrStatus(
                configured = true,
                serverUrl = "https://seerr.example",
                linked = linked,
            )
        )
    }

    @Test
    fun `init seeds the form and connection state from the stores`() = runTest {
        seedPreferences(
            SeerrPreferences(
                serverUrl = "https://seerr.example",
                username = "user",
                email = "user@example.com",
                authMethod = SeerrAuthMethod.API_KEY,
            )
        )
        every { secureCredentialsStore.getApiKey() } returns "stored-key"
        val viewModel = viewModel()

        awaitUntil { viewModel.serverUrl == "https://seerr.example" && viewModel.apiKey == "stored-key" }

        assertEquals("user", viewModel.username)
        assertEquals("user@example.com", viewModel.email)
        assertEquals(SeerrAuthMethod.API_KEY, viewModel.authMethod)
        // A stored key for an API_KEY server means "connected".
        assertTrue(viewModel.connectionStatus.value is ConnectionProbe.Status.Connected)
    }

    @Test
    fun `init reports connected from a stored session cookie for cookie auth`() = runTest {
        seedPreferences(
            SeerrPreferences(serverUrl = "https://seerr.example", authMethod = SeerrAuthMethod.JELLYFIN)
        )
        every { secureCredentialsStore.getSessionCookie() } returns "cookie"
        val viewModel = viewModel()

        awaitUntil { viewModel.connectionStatus.value is ConnectionProbe.Status.Connected }

        assertEquals(SeerrAuthMethod.JELLYFIN, viewModel.authMethod)
    }

    @Test
    fun `testConnection without a url fails fast without touching the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(
            ConnectionProbe.Status.Error(
                ConnectionProbe.Failure.Declared(ConnectionProbe.FallbackText.ServerUrlRequired)
            ),
            viewModel.connectionStatus.value,
        )
        coVerify(exactly = 0) { seerrAuthenticator.testApiKeyConnection() }
    }

    @Test
    fun `an api-key test persists credentials then reports the connected version`() = runTest {
        seedPreferences(SeerrPreferences(serverUrl = "https://seerr.example"))
        every { secureCredentialsStore.getApiKey() } returns "stored-key"
        coEvery { seerrAuthenticator.testApiKeyConnection() } returns
            Result.success(SeerrStatusResponse(version = "2.0"))
        val viewModel = viewModel()
        awaitUntil { viewModel.serverUrl == "https://seerr.example" && viewModel.apiKey == "stored-key" }

        viewModel.testConnection()

        awaitUntil {
            viewModel.connectionStatus.value ==
                ConnectionProbe.Status.Connected(SeerrSettingsViewModel.SeerrConnectionDetails("2.0"))
        }
        coVerify(exactly = 1) { seerrPreferencesStore.setServerUrl("https://seerr.example") }
        coVerify(exactly = 1) { seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.API_KEY) }
        coVerify(exactly = 1) { secureCredentialsStore.setApiKey("stored-key") }
        coVerify(exactly = 1) { seerrAuthenticator.testApiKeyConnection() }
    }

    @Test
    fun `a failed connection test surfaces the error and clears the spinner`() = runTest {
        seedPreferences(SeerrPreferences(serverUrl = "https://seerr.example"))
        every { secureCredentialsStore.getApiKey() } returns "stored-key"
        coEvery { seerrAuthenticator.testApiKeyConnection() } returns
            Result.failure(RuntimeException("refused"))
        val viewModel = viewModel()
        awaitUntil { viewModel.serverUrl == "https://seerr.example" && viewModel.apiKey == "stored-key" }

        viewModel.testConnection()

        awaitUntil {
            viewModel.connectionStatus.value ==
                ConnectionProbe.Status.Error(ConnectionProbe.Failure.Reported("refused"))
        }
    }

    @Test
    fun `toggles route to the store and disconnect resets the form`() = runTest {
        seedPreferences(SeerrPreferences(serverUrl = "https://seerr.example"))
        every { secureCredentialsStore.getApiKey() } returns "stored-key"
        val viewModel = viewModel()
        awaitUntil { viewModel.serverUrl == "https://seerr.example" }

        viewModel.setEnabled(true)
        viewModel.setSearchEnabled(true)
        viewModel.disconnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrPreferencesStore.setEnabled(true) }
        coVerify(exactly = 1) { seerrPreferencesStore.setSearchEnabled(true) }
        coVerify(exactly = 1) { seerrPreferencesStore.disconnect() }
        assertEquals("", viewModel.serverUrl)
        assertEquals("", viewModel.apiKey)
        assertEquals(ConnectionProbe.Status.Idle, viewModel.connectionStatus.value)
    }

    // ── via-server bridge mode (ADR 0010) ──

    @Test
    fun `entering via-server mode persists the flag and fetches the bridge status`() = runTest {
        stubBridgeStatus(linked = false)
        val viewModel = viewModel(withBridge = true)

        viewModel.setUseServerBridge(true)
        advanceUntilIdle()

        assertTrue(viewModel.useServerBridge)
        coVerify(exactly = 1) { seerrPreferencesStore.setUseServerBridge(true) }
        coVerify(exactly = 1) { pluginApiClient.seerrStatus() }
        assertEquals(false, viewModel.bridgeStatus?.linked)
        assertEquals(false, viewModel.bridgeBusy)
        assertNull(viewModel.bridgeFailure)
    }

    @Test
    fun `init restores a saved via-server mode and fetches the linked status`() = runTest {
        seedPreferences(SeerrPreferences(useServerBridge = true))
        stubBridgeStatus(linked = true)
        val viewModel = viewModel(withBridge = true)

        awaitUntil { viewModel.bridgeStatus?.linked == true }

        assertTrue(viewModel.useServerBridge)
        assertEquals("https://seerr.example", viewModel.bridgeStatus?.serverUrl)
        coVerify(exactly = 1) { pluginApiClient.seerrStatus() }
    }

    @Test
    fun `quick connect authorizes via jellyfin and finalizes with the plugin login`() = runTest {
        stubBridgeStatus(linked = false)
        val viewModel = viewModel(withBridge = true)
        viewModel.setUseServerBridge(true)
        advanceUntilIdle()

        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns
            Result.success(QuickConnectInfo(secret = "qc-secret", code = "AB12"))
        coEvery { authRepository.pollQuickConnect("qc-secret") } returns
            Result.success(QuickConnectState(authenticated = true, secret = "qc-secret"))
        coEvery {
            pluginApiClient.seerrLogin("quickconnect", null, null, "qc-secret")
        } returns Result.success(Unit)
        stubBridgeStatus(linked = true)

        viewModel.startBridgeQuickConnect()

        awaitUntil { viewModel.bridgeStatus?.linked == true }

        coVerify(exactly = 1) { authRepository.initiateQuickConnect() }
        coVerify(exactly = 1) { pluginApiClient.seerrLogin("quickconnect", null, null, "qc-secret") }
        assertNull(viewModel.bridgeQcCode)
        assertEquals(false, viewModel.bridgeBusy)
    }

    @Test
    fun `a rejected plugin login surfaces the reported failure and clears the code`() = runTest {
        stubBridgeStatus(linked = false)
        val viewModel = viewModel(withBridge = true)
        viewModel.setUseServerBridge(true)
        advanceUntilIdle()

        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns
            Result.success(QuickConnectInfo(secret = "qc-secret", code = "AB12"))
        coEvery { authRepository.pollQuickConnect("qc-secret") } returns
            Result.success(QuickConnectState(authenticated = true, secret = "qc-secret"))
        coEvery {
            pluginApiClient.seerrLogin("quickconnect", null, null, "qc-secret")
        } returns Result.failure(RuntimeException("quickconnect-pending"))

        viewModel.startBridgeQuickConnect()

        awaitUntil {
            viewModel.bridgeFailure == SeerrBridgeFailure.Reported("quickconnect-pending")
        }

        assertNull(viewModel.bridgeQcCode)
        assertEquals(false, viewModel.bridgeStatus?.linked)
    }

    @Test
    fun `quick connect disabled on the server surfaces the declared failure without a login`() = runTest {
        stubBridgeStatus(linked = false)
        val viewModel = viewModel(withBridge = true)
        viewModel.setUseServerBridge(true)
        advanceUntilIdle()

        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(false)

        viewModel.startBridgeQuickConnect()
        advanceUntilIdle()

        assertEquals(
            SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.QuickConnectDisabled),
            viewModel.bridgeFailure,
        )
        coVerify(exactly = 0) { authRepository.initiateQuickConnect() }
        coVerify(exactly = 0) { pluginApiClient.seerrLogin(any(), any(), any(), any()) }
    }

    @Test
    fun `logout unlinks via the plugin and refreshes the status`() = runTest {
        stubBridgeStatus(linked = true)
        val viewModel = viewModel(withBridge = true)
        viewModel.setUseServerBridge(true)
        advanceUntilIdle()
        assertEquals(true, viewModel.bridgeStatus?.linked)

        coEvery { pluginApiClient.seerrLogout() } returns Result.success(Unit)
        stubBridgeStatus(linked = false)

        viewModel.bridgeLogout()
        advanceUntilIdle()

        coVerify(exactly = 1) { pluginApiClient.seerrLogout() }
        assertEquals(false, viewModel.bridgeStatus?.linked)
        assertNull(viewModel.bridgeFailure)
    }

    @Test
    fun `a failed logout surfaces the reported failure and keeps the link`() = runTest {
        stubBridgeStatus(linked = true)
        val viewModel = viewModel(withBridge = true)
        viewModel.setUseServerBridge(true)
        advanceUntilIdle()

        coEvery { pluginApiClient.seerrLogout() } returns Result.failure(RuntimeException("JellyPlay Seerr logout failed: 500"))

        viewModel.bridgeLogout()
        advanceUntilIdle()

        assertEquals(
            SeerrBridgeFailure.Reported("JellyPlay Seerr logout failed: 500"),
            viewModel.bridgeFailure,
        )
        assertEquals(true, viewModel.bridgeStatus?.linked)
    }

    @Test
    fun `leaving via-server mode cancels an in-flight quick connect`() = runTest {
        stubBridgeStatus(linked = false)
        val viewModel = viewModel(withBridge = true)
        viewModel.setUseServerBridge(true)
        advanceUntilIdle()

        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns
            Result.success(QuickConnectInfo(secret = "qc-secret", code = "AB12"))
        // Park the poll mid-flight so the loop is genuinely in-flight when the
        // mode flips (virtual time would otherwise run the whole window).
        coEvery { authRepository.pollQuickConnect("qc-secret") } coAnswers { awaitCancellation() }

        viewModel.startBridgeQuickConnect()
        awaitUntil { viewModel.bridgeQcCode == "AB12" }

        viewModel.setUseServerBridge(false)
        advanceUntilIdle()

        assertNull(viewModel.bridgeQcCode)
        assertEquals(false, viewModel.useServerBridge)
        assertNull(viewModel.bridgeFailure)
        coVerify(exactly = 0) { pluginApiClient.seerrLogin(any(), any(), any(), any()) }
    }
}
