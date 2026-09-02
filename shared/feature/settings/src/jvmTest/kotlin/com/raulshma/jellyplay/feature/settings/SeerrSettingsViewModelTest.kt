package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the Seerr settings wiring (LibraryLayout jvmTest pattern: mockk
 * collaborators + real Result/[MutableStateFlow] stubs + inlined
 * Main-dispatcher rule). The init block seeds the form + connection status
 * from the preferences and secure-credential stores (cookie-backed auth
 * included), connection tests persist before probing and route the Result
 * into [SeerrSettingsViewModel.ConnectionStatus], blank input fails fast
 * without touching the repository, and toggles/disconnect route to the
 * preferences store.
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

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var secureCredentialsStore: SeerrSecureCredentialsStore
    private val preferencesState = MutableStateFlow(SeerrPreferences())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        seerrRepository = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        secureCredentialsStore = mockk(relaxed = true)
        every { seerrPreferencesStore.preferences } returns preferencesState
        every { secureCredentialsStore.getApiKey() } returns ""
        every { secureCredentialsStore.getPassword() } returns ""
        every { secureCredentialsStore.getSessionCookie() } returns ""
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SeerrSettingsViewModel(seerrRepository, seerrPreferencesStore, secureCredentialsStore)

    private fun seedPreferences(prefs: SeerrPreferences) {
        preferencesState.value = prefs
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
        assertTrue(viewModel.connectionStatus is SeerrSettingsViewModel.ConnectionStatus.Connected)
    }

    @Test
    fun `init reports connected from a stored session cookie for cookie auth`() = runTest {
        seedPreferences(
            SeerrPreferences(serverUrl = "https://seerr.example", authMethod = SeerrAuthMethod.JELLYFIN)
        )
        every { secureCredentialsStore.getSessionCookie() } returns "cookie"
        val viewModel = viewModel()

        awaitUntil { viewModel.connectionStatus is SeerrSettingsViewModel.ConnectionStatus.Connected }

        assertEquals(SeerrAuthMethod.JELLYFIN, viewModel.authMethod)
    }

    @Test
    fun `testConnection without a url fails fast without touching the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(
            SeerrSettingsViewModel.ConnectionStatus.Error("Server URL is required"),
            viewModel.connectionStatus,
        )
        coVerify(exactly = 0) { seerrRepository.testApiKeyConnection() }
    }

    @Test
    fun `an api-key test persists credentials then reports the connected version`() = runTest {
        seedPreferences(SeerrPreferences(serverUrl = "https://seerr.example"))
        every { secureCredentialsStore.getApiKey() } returns "stored-key"
        coEvery { seerrRepository.testApiKeyConnection() } returns
            Result.success(SeerrStatusResponse(version = "2.0"))
        val viewModel = viewModel()
        awaitUntil { viewModel.serverUrl == "https://seerr.example" && viewModel.apiKey == "stored-key" }

        viewModel.testConnection()

        awaitUntil {
            viewModel.connectionStatus == SeerrSettingsViewModel.ConnectionStatus.Connected("2.0", true)
        }
        coVerify(exactly = 1) { seerrPreferencesStore.setServerUrl("https://seerr.example") }
        coVerify(exactly = 1) { seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.API_KEY) }
        coVerify(exactly = 1) { secureCredentialsStore.setApiKey("stored-key") }
        coVerify(exactly = 1) { seerrRepository.testApiKeyConnection() }
    }

    @Test
    fun `a failed connection test surfaces the error and clears the spinner`() = runTest {
        seedPreferences(SeerrPreferences(serverUrl = "https://seerr.example"))
        every { secureCredentialsStore.getApiKey() } returns "stored-key"
        coEvery { seerrRepository.testApiKeyConnection() } returns
            Result.failure(RuntimeException("refused"))
        val viewModel = viewModel()
        awaitUntil { viewModel.serverUrl == "https://seerr.example" && viewModel.apiKey == "stored-key" }

        viewModel.testConnection()

        awaitUntil {
            viewModel.connectionStatus == SeerrSettingsViewModel.ConnectionStatus.Error("refused")
        }
        assertFalse(viewModel.isTesting)
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
        assertEquals(
            SeerrSettingsViewModel.ConnectionStatus.Idle,
            viewModel.connectionStatus,
        )
    }
}
