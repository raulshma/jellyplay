package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_server_address_required
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary gaps in [AddServerViewModel] NOT pinned by [AddServerViewModelTest]:
 *
 * 1. [AddServerViewModel.clearError] clears the connect-error banner (the
 *    existing suite only covers the address-edit clear path).
 * 2. The blank-address guard fires for a whitespace-only address with the
 *    `auth_error_server_address_required` resource (the existing suite covers
 *    the empty-string case; the guard is `isBlank`).
 * 3. [AddServerViewModel.connectToServer] trims the address BEFORE the
 *    repository call and reports the repository's result 1:1 — the probe
 *    endpoint is the trimmed form, not the padded input.
 * 4. [AddServerViewModel.startDiscovery] re-entrant guard: a second call while
 *    a scan is already running is a no-op (repository invoked once, already
 *    discovered servers kept, no state reset).
 * 5. Editing the manual address after a TLS-trust failure forgets the pending
 *    retry — [AddServerViewModel.confirmTrustServer] then no-ops (no store
 *    write, no second connect attempt), while the error banner itself stays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelGapsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var serverDiscoveryRepository: ServerDiscoveryRepository
    private lateinit var networkOfflineStore: NetworkOfflineStore
    private lateinit var viewModel: AddServerViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        serverDiscoveryRepository = mockk()
        networkOfflineStore = mockk(relaxed = true)
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )
        viewModel = AddServerViewModel(
            authRepository = authRepository,
            serverDiscoveryRepository = serverDiscoveryRepository,
            localNetworkStatus = LocalNetworkStatus { false },
            networkOfflineStore = networkOfflineStore,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clearError_clearsTheConnectBanner() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(RuntimeException("boom"))
        viewModel.connectToServer("http://server") {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectError != null)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.connectError)
    }

    @Test
    fun connectToServer_whitespaceOnlyAddress_setsTheRequiredError() = runTest(testDispatcher) {
        var resultInvoked = false
        viewModel.connectToServer(" \t ") { resultInvoked = true }
        advanceUntilIdle()

        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_server_address_required),
            viewModel.uiState.value.connectError,
        )
        assertFalse(resultInvoked)
        coVerify(exactly = 0) { authRepository.addServer(any()) }
    }

    @Test
    fun connectToServer_trimsTheAddressBeforeTheRepositoryCall() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        coEvery { authRepository.addServer("http://server") } returns Result.success(info)
        var received: Result<ServerInfo>? = null

        // Padded input — the repository must see the trimmed form.
        viewModel.connectToServer("  http://server  ") { received = it }
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.addServer("http://server") }
        assertTrue(received?.isSuccess == true)
        assertFalse(viewModel.uiState.value.isConnecting)
    }

    @Test
    fun startDiscovery_whileAlreadyDiscovering_isANoop() = runTest(testDispatcher) {
        val first = DiscoveredServer(id = "1", name = "one", address = "http://192.168.1.10:8096")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        every { serverDiscoveryRepository.discoverLocalServers() } returns flow {
            emit(first)
            gate.await()
        }

        viewModel.startDiscovery()
        advanceUntilIdle()
        assertEquals(listOf(first), viewModel.uiState.value.discoveredServers)
        assertTrue(viewModel.uiState.value.isDiscovering)

        // Re-entrant call: the scan must not restart (no second repository
        // call, no discovered-list wipe, no reset of the running flag).
        viewModel.startDiscovery()
        advanceUntilIdle()

        assertEquals(listOf(first), viewModel.uiState.value.discoveredServers)
        assertTrue(viewModel.uiState.value.isDiscovering)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDiscovering)
        // One scan total.
        verify(exactly = 1) { serverDiscoveryRepository.discoverLocalServers() }
    }

    @Test
    fun updateManualAddress_afterTlsFailure_forgetsThePendingRetry() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))

        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()
        assertEquals(
            "https://selfsigned.example.com",
            viewModel.uiState.value.tlsTrustPromptAddress,
        )

        // Editing the address field drops the prompt AND the remembered retry.
        viewModel.updateManualAddress("https://other.example.com")
        assertNull(viewModel.uiState.value.tlsTrustPromptAddress)

        viewModel.confirmTrustServer()
        advanceUntilIdle()

        // No grant persisted, no second connect attempt — the stale retry was
        // forgotten with the prompt.
        coVerify(exactly = 0) { networkOfflineStore.addSelfSignedTrustHost(any()) }
        coVerify(exactly = 1) { authRepository.addServer(any()) }
    }
}
