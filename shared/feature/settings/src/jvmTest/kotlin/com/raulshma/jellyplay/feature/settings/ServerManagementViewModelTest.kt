package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ServerInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave-21 review round: pins the Server Management trust-toggle semantics
 * against the SAME matcher the handshake layer uses (display drift — a
 * portless grant honors any port, so exact string membership showed the
 * toggle OFF for a grant every TLS handshake accepted), and the orphan-grant
 * cleanup on address/server removal (a grant covering no known address is
 * dropped; one still covering another address survives).
 *
 * Stores are mockk'd (final DataStore-backed classes); the granted set is a
 * plain [MutableStateFlow] the test controls. Main-dispatcher rule inlined
 * (StandardTestDispatcher + setMain/resetMain — LibraryLayout jvmTest
 * pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var serverIdentityStore: ServerIdentityStore
    private lateinit var networkOfflineStore: NetworkOfflineStore
    private val serversState = MutableStateFlow<List<ServerInfo>>(emptyList())
    private val trustState = MutableStateFlow(NetworkOfflineSlice())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        serverIdentityStore = mockk(relaxed = true)
        networkOfflineStore = mockk(relaxed = true)
        every { authRepository.servers } returns serversState
        every { serverIdentityStore.activeServerId } returns MutableStateFlow(null)
        every { networkOfflineStore.networkOffline } returns trustState
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ServerManagementViewModel = ServerManagementViewModel(
        authRepository = authRepository,
        serverIdentityStore = serverIdentityStore,
        networkOfflineStore = networkOfflineStore,
    )

    // ------------------------------------------------------- toggle semantics

    @Test
    fun `portless grant shows toggle ON for ported primary`() = runTest(testDispatcher) {
        trustState.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://media.example.com"))
        val server = ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920")
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(
            vm.isSelfSignedTrustGranted(server),
            "a portless grant is honored by every handshake (any port) — the toggle must show ON",
        )
    }

    @Test
    fun `exact-address grant still shows ON and foreign grants show OFF`() = runTest(testDispatcher) {
        trustState.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://media.example.com:8920"))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.isSelfSignedTrustGranted(ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920")))
        assertFalse(vm.isSelfSignedTrustGranted(ServerInfo(id = "s2", name = "Other", address = "https://other.example.com")))
        assertFalse(vm.isSelfSignedTrustGranted(ServerInfo(id = "s3", name = "Media", address = "https://media.example.com:8921")))
    }

    @Test
    fun `revoke drops a portless grant covering a ported primary`() = runTest(testDispatcher) {
        trustState.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://media.example.com"))
        val server = ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920")
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSelfSignedTrust(server, granted = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.removeSelfSignedTrustHost("https://media.example.com") }
    }

    // ------------------------------------------------------- orphan cleanup

    @Test
    fun `orphaned grant removed after address removal`() = runTest(testDispatcher) {
        serversState.value = listOf(
            ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920"),
        )
        trustState.value = NetworkOfflineSlice(
            selfSignedTrustHosts = setOf("https://media.example.com:8920", "https://orphan.example.com"),
        )
        val vm = viewModel()
        advanceUntilIdle()
        // Repository removal already applied upstream of the prune read.
        coEvery { authRepository.removeServerAddress("s1", "https://orphan.example.com") } returns Result.success(Unit)

        vm.removeServerAddress("s1", "https://orphan.example.com")
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.removeSelfSignedTrustHost("https://orphan.example.com") }
        coVerify(exactly = 0) { networkOfflineStore.removeSelfSignedTrustHost("https://media.example.com:8920") }
    }

    @Test
    fun `grant still covering another address survives removal`() = runTest(testDispatcher) {
        // Portless grant covers BOTH the remaining primary (:8920) and the
        // removed alternate (:8921) — it must survive the alternate's removal.
        serversState.value = listOf(
            ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920"),
        )
        trustState.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://media.example.com"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.removeServerAddress("s1", "https://media.example.com:8921")
        advanceUntilIdle()

        coVerify(exactly = 0) { networkOfflineStore.removeSelfSignedTrustHost(any()) }
    }

    @Test
    fun `removing a server drops its grant but keeps another server's grant`() = runTest(testDispatcher) {
        serversState.value = listOf(
            ServerInfo(id = "s2", name = "Keep", address = "https://keep.example.com"),
        )
        trustState.value = NetworkOfflineSlice(
            selfSignedTrustHosts = setOf("https://gone.example.com", "https://keep.example.com"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.removeServer("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.removeSelfSignedTrustHost("https://gone.example.com") }
        coVerify(exactly = 0) { networkOfflineStore.removeSelfSignedTrustHost("https://keep.example.com") }
    }
}
