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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
 *
 * Later top-up round: also pins the server/address operations the original
 * file predated — switchServer's callback + spinner contract (success AND
 * failure), the add/switch-address one-shot messages (including the
 * message-less-exception fallbacks), the banner dismissal, the active-server
 * removal failover to the first remaining server, and the grant path's
 * address normalization (trim + trailing slash + https scheme default).
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

    // ------------------------------------------- top-ups: switch + address ops

    @Test
    fun `switchServer calls the repository and settles the spinner on success`() = runTest(testDispatcher) {
        coEvery { authRepository.switchServer("s2") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()
        var completed = false

        vm.switchServer("s2") { completed = true }
        advanceUntilIdle()

        assertTrue(completed, "the success callback must fire")
        assertEquals(false, vm.isSwitching)
    }

    @Test
    fun `switchServer failure skips the callback and settles the spinner`() = runTest(testDispatcher) {
        coEvery { authRepository.switchServer("s3") } returns Result.failure(RuntimeException("unreachable"))
        val vm = viewModel()
        advanceUntilIdle()
        var completed = false

        vm.switchServer("s3") { completed = true }
        advanceUntilIdle()

        assertFalse(completed, "a failed switch must not navigate")
        assertEquals(false, vm.isSwitching)
    }

    @Test
    fun `addServerAddress success posts the confirmation and settles the spinner`() = runTest(testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()

        assertEquals("Address added", vm.addressOperationMessage)
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `addServerAddress failure posts the error message and settles the spinner`() = runTest(testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns
            Result.failure(RuntimeException("address already exists"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()

        assertEquals("address already exists", vm.addressOperationMessage)
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `addServerAddress failure without a message falls back to the generic wording`() = runTest(testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns
            Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()

        assertEquals("Failed to add address", vm.addressOperationMessage)
    }

    @Test
    fun `switchServerAddress success names the new address`() = runTest(testDispatcher) {
        coEvery { authRepository.switchServerAddress("s1", "https://mirror.example.com") } returns
            Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServerAddress("s1", "https://mirror.example.com")
        advanceUntilIdle()

        assertEquals("Switched to https://mirror.example.com", vm.addressOperationMessage)
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `switchServerAddress failure falls back to the generic wording`() = runTest(testDispatcher) {
        coEvery { authRepository.switchServerAddress("s1", "https://mirror.example.com") } returns
            Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServerAddress("s1", "https://mirror.example.com")
        advanceUntilIdle()

        assertEquals("Failed to switch address", vm.addressOperationMessage)
    }

    @Test
    fun `clearAddressOperationMessage dismisses the banner`() = runTest(testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()
        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()
        assertEquals("Address added", vm.addressOperationMessage)

        vm.clearAddressOperationMessage()

        assertNull(vm.addressOperationMessage)
    }

    @Test
    fun `removing the active server fails over to the first remaining one`() = runTest(testDispatcher) {
        serversState.value = listOf(
            ServerInfo(id = "s1", name = "Active", address = "https://a.example.com"),
            ServerInfo(id = "s2", name = "Backup", address = "https://b.example.com"),
        )
        every { serverIdentityStore.activeServerId } returns MutableStateFlow("s1")
        coEvery { authRepository.removeServer("s1") } returns Unit
        coEvery { authRepository.switchServer("s2") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.removeServer("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.removeServer("s1") }
        coVerify(exactly = 1) { authRepository.switchServer("s2") }
    }

    @Test
    fun `granting trust writes the normalized primary address`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSelfSignedTrust(
            ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920/"),
            granted = true,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.addSelfSignedTrustHost("https://media.example.com:8920") }
    }

    @Test
    fun `granting trust adds the https scheme to a schemeless address`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSelfSignedTrust(
            ServerInfo(id = "s1", name = "Media", address = " media.example.com "),
            granted = true,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.addSelfSignedTrustHost("https://media.example.com") }
    }
}
