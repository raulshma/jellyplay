package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_cleartext
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_connection_failed
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_local_network_denied
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_resolve_address
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_server_address_required
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_ssl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NEW suite (downloads/syncplay conveyor precedent — the legacy module had no
 * AddServerViewModel tests): pins the connection-failure classifier 1:1 to
 * HEAD's branch table (message identity via [AuthMessage.Resource] /
 * [AuthMessage.Raw]), the blank-address guard, and the SSDP discovery flow's
 * dedupe/complete/failure behavior. The [LocalNetworkStatus] seam is faked
 * inline (hand-rolled lambda over the fun interface).
 *
 * Wave 21A extends the suite with the self-signed trust grant flow: TLS-trust
 * failures surface the trust dialog state, confirm persists the canonical
 * host through [NetworkOfflineStore] and retries the same connect. The store
 * is mockk'd (final DataStore-backed class — the pref→config mapping itself
 * is covered by OkHttpConfigProviderImplTest in core:data).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var serverDiscoveryRepository: ServerDiscoveryRepository
    private lateinit var networkOfflineStore: NetworkOfflineStore
    private var blamePermission: Boolean = false
    private lateinit var viewModel: AddServerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        serverDiscoveryRepository = mockk()
        networkOfflineStore = mockk(relaxed = true)
        viewModel = AddServerViewModel(
            authRepository = authRepository,
            serverDiscoveryRepository = serverDiscoveryRepository,
            localNetworkStatus = LocalNetworkStatus { _ -> blamePermission },
            networkOfflineStore = networkOfflineStore,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------ connect (manual)

    @Test
    fun connectToServer_blankAddress_setsRequiredError_withoutCallingRepository() = runTest(testDispatcher) {
        var resultInvoked = false
        viewModel.connectToServer("   ") { resultInvoked = true }

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_server_address_required),
            viewModel.uiState.value.connectError,
        )
        // HEAD guard shape: the blank address returns BEFORE launching, so
        // neither the repository nor the callback ever run.
        assertFalse(resultInvoked)
        coVerify(exactly = 0) { authRepository.addServer(any()) }
    }

    @Test
    fun connectToServer_unknownHost_resolvesToLocalResource() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(java.net.UnknownHostException("server"))

        viewModel.connectToServer("http://server") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_resolve_address),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_wrappedRootCause_classifiesInnermostCause() = runTest(testDispatcher) {
        // getRootCause walks the chain: the wrapper is a plain RuntimeError,
        // the SSLException at the bottom must win the classification.
        coEvery { authRepository.addServer("https://server") } returns
            Result.failure(RuntimeException("wrapper", javax.net.ssl.SSLException("handshake failed")))

        viewModel.connectToServer("https://server") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_ssl),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_cleartextMessageInRootCause_mapsToCleartextResource() = runTest(testDispatcher) {
        // Cleartext traffic permitted is surfaced by OkHttp inside a plain
        // IOException message, not an exception type.
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(java.io.IOException("Cleartext HTTP traffic to server not permitted"))

        viewModel.connectToServer("http://server") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_cleartext),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_permissionBlamedTimeout_mapsToLocalNetworkDenied() = runTest(testDispatcher) {
        blamePermission = true
        coEvery { authRepository.addServer("http://192.168.1.10:8096") } returns
            Result.failure(java.net.SocketTimeoutException("connect timed out"))

        viewModel.connectToServer("http://192.168.1.10:8096") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_local_network_denied),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_permissionBlameIgnored_forNonPermissionFailures() = runTest(testDispatcher) {
        // The blame only reclassifies timeout/connect/resolve failures; an
        // SSL failure against a LAN host keeps its specific message.
        blamePermission = true
        coEvery { authRepository.addServer("https://192.168.1.10:8920") } returns
            Result.failure(javax.net.ssl.SSLException("bad cert"))

        viewModel.connectToServer("https://192.168.1.10:8920") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_ssl),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_shortRawMessage_carriedRaw() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(RuntimeException("boom"))

        viewModel.connectToServer("http://server") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Raw("boom"),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_orgPrefixedMessage_fallsBackToGenericResource() = runTest(testDispatcher) {
        // HEAD filter: SDK exception messages starting with "org." are
        // treated as noise — the user gets the generic failure string.
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(RuntimeException("org.jellyfin.sdk.ApiException: something"))

        viewModel.connectToServer("http://server") {}

        advanceUntilIdle()
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_connection_failed),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_success_reportsResult_andClearsConnecting() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        coEvery { authRepository.addServer("http://server") } returns Result.success(info)
        var received: Result<ServerInfo>? = null

        viewModel.connectToServer("http://server") { received = it }

        advanceUntilIdle()
        assertTrue(received?.isSuccess == true)
        assertNull(viewModel.uiState.value.connectError)
        assertFalse(viewModel.uiState.value.isConnecting)
    }

    @Test
    fun updateManualAddress_clearsConnectError() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(RuntimeException("boom"))
        viewModel.connectToServer("http://server") {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectError != null)

        viewModel.updateManualAddress("http://other")

        assertEquals("http://other", viewModel.uiState.value.manualAddress)
        assertNull(viewModel.uiState.value.connectError)
    }

    // ------------------------------------------------------ self-signed trust

    @Test
    fun connectToServer_tlsFailure_onHttps_setsTrustPromptAddress() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX path building failed"))

        viewModel.connectToServer("https://selfsigned.example.com") {}

        advanceUntilIdle()
        assertEquals(
            "https://selfsigned.example.com",
            viewModel.uiState.value.tlsTrustPromptAddress,
        )
        // The SSL error message is still surfaced alongside the dialog.
        assertEquals(
            AuthMessage.Resource(Res.string.auth_error_ssl),
            viewModel.uiState.value.connectError,
        )
    }

    @Test
    fun connectToServer_tlsFailure_withoutScheme_promptsNormalizedHttpsAddress() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLPeerUnverifiedException("Certificate not trusted"))

        viewModel.connectToServer("selfsigned.example.com") {}

        advanceUntilIdle()
        // kotlin.test form (message-last) — the file's JUnit imports are
        // message-first and this assert needs the explanatory message.
        kotlin.test.assertEquals(
            "https://selfsigned.example.com",
            viewModel.uiState.value.tlsTrustPromptAddress,
            "the grant entry must be the normalized canonical form, not the raw input",
        )
    }

    @Test
    fun connectToServer_tlsFailure_onHttpAddress_neverPrompts() = runTest(testDispatcher) {
        // Cleartext addresses cannot present a certificate — belt-and-braces
        // guard even though an SSLException on http is not expected.
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(javax.net.ssl.SSLException("handshake"))

        viewModel.connectToServer("http://server") {}

        advanceUntilIdle()
        assertNull(viewModel.uiState.value.tlsTrustPromptAddress)
    }

    @Test
    fun connectToServer_nonTlsFailure_neverPrompts() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://server") } returns
            Result.failure(java.net.ConnectException("refused"))

        viewModel.connectToServer("https://server") {}

        advanceUntilIdle()
        assertNull(viewModel.uiState.value.tlsTrustPromptAddress)
    }

    @Test
    fun confirmTrustServer_persistsCanonicalHost_andRetriesConnect() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX")) andThen
            Result.success(info)

        var attempts = 0
        var finalResult: Result<ServerInfo>? = null
        viewModel.connectToServer("https://selfsigned.example.com") { attempts++; finalResult = it }
        advanceUntilIdle()
        assertEquals(1, attempts)

        viewModel.confirmTrustServer()
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.addSelfSignedTrustHost("https://selfsigned.example.com") }
        kotlin.test.assertEquals(2, attempts, "confirm must retry the same connect")
        assertTrue(finalResult?.isSuccess == true)
        assertNull(viewModel.uiState.value.tlsTrustPromptAddress)
    }

    @Test
    fun dismissTrustPrompt_clearsPrompt_withoutPersisting() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))
        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tlsTrustPromptAddress != null)

        viewModel.dismissTrustPrompt()

        assertNull(viewModel.uiState.value.tlsTrustPromptAddress)
        coVerify(exactly = 0) { networkOfflineStore.addSelfSignedTrustHost(any()) }
        coVerify(exactly = 1) { authRepository.addServer(any()) }
    }

    @Test
    fun updateManualAddress_clearsTrustPrompt() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))
        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tlsTrustPromptAddress != null)

        viewModel.updateManualAddress("https://other")

        assertNull(viewModel.uiState.value.tlsTrustPromptAddress)
    }

    @Test
    fun confirmTrustServer_withoutPrompt_isANoOp() = runTest(testDispatcher) {
        viewModel.confirmTrustServer()
        advanceUntilIdle()

        coVerify(exactly = 0) { networkOfflineStore.addSelfSignedTrustHost(any()) }
    }

    // ------------------------------------------------------------- discovery

    @Test
    fun startDiscovery_collectsServers_dedupes_byIdAndAddress_completes() = runTest(testDispatcher) {
        val a = DiscoveredServer(id = "1", name = "one", address = "http://192.168.1.10:8096")
        val sameAddress = DiscoveredServer(id = "2", name = "dupe", address = "http://192.168.1.10:8096")
        val b = DiscoveredServer(id = "3", name = "two", address = "http://192.168.1.11:8096")
        every { serverDiscoveryRepository.discoverLocalServers() } returns flowOf(a, sameAddress, b)

        viewModel.startDiscovery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // The address-dupe is dropped (HEAD dedupe predicate: id OR address).
        assertEquals(listOf(a, b), state.discoveredServers)
        assertFalse(state.isDiscovering)
        assertFalse(state.discoveryFailed)
    }

    @Test
    fun startDiscovery_failure_setsDiscoveryFailed() = runTest(testDispatcher) {
        every { serverDiscoveryRepository.discoverLocalServers() } returns
            flow<DiscoveredServer> { throw RuntimeException("multicast lock") }

        viewModel.startDiscovery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.discoveryFailed)
        assertFalse(state.isDiscovering)
    }

    @Test
    fun stopDiscovery_stopsScanning() = runTest(testDispatcher) {
        every { serverDiscoveryRepository.discoverLocalServers() } returns flowOf()
        viewModel.startDiscovery()

        viewModel.stopDiscovery()

        assertFalse(viewModel.uiState.value.isDiscovering)
    }
}
