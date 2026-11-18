package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ClientCertificateRepository
import com.raulshma.jellyplay.core.data.repository.SelfSignedTrustRepositoryImpl
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.ClientCertificateImport
import com.raulshma.jellyplay.core.model.ClientCertificateStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_switch_failed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * Pins the Server Management trust-toggle semantics through the real
 * core:data SelfSignedTrustRepository seam (backed by the SAME matcher the
 * handshake layer uses — display drift: a portless grant honors any port, so
 * exact string membership showed the toggle OFF for a grant every TLS
 * handshake accepted), and the orphan-grant cleanup on address/server removal
 * (a grant covering no known address is dropped; one still covering another
 * address survives).
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
 *
 * Phase-machine round: the address operations' former parked-String +
 * in-progress boolean pair is now the [AddressOperationPhase] machine
 * (Idle/Running(verb)/Completed(message), the SnapshotActionPhase shape) —
 * the message pins above read the phase's Completed face unchanged, and the
 * machine pins below add the Running observability (verb carried, derived
 * `isAddressOperationInProgress` true mid-flight), the entry-clear of a
 * parked outcome, and the preserved guard-free re-entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var serverIdentityStore: ServerIdentityStore
    private lateinit var networkOfflineStore: NetworkOfflineStore
    private val serversState = MutableStateFlow<List<ServerInfo>>(emptyList())
    private val trustState = MutableStateFlow(NetworkOfflineSlice())

    @BeforeTest
    fun setUp() {
        authRepository = mockk(relaxed = true)
        serverIdentityStore = mockk(relaxed = true)
        networkOfflineStore = mockk(relaxed = true)
        every { authRepository.servers } returns serversState
        every { serverIdentityStore.activeServerId } returns MutableStateFlow(null)
        every { networkOfflineStore.networkOffline } returns trustState
    }

    private fun viewModel(): ServerManagementViewModel = ServerManagementViewModel(
        authRepository = authRepository,
        serverIdentityStore = serverIdentityStore,
        networkOfflineStore = networkOfflineStore,
        // The REAL decision seam (stateless; core:data's view of the network
        // matcher) so these tests keep pinning genuine matching semantics
        // against the mocked store state — not a stubbed answer.
        selfSignedTrustRepository = SelfSignedTrustRepositoryImpl(),
        clientCertificate = certificateFacade,
    )

    /** In-memory seam double: status + recorded calls, no file IO. */
    private class FakeClientCertificateRepository : ClientCertificateRepository {
        val statusState = MutableStateFlow(ClientCertificateStatus())
        override val status: StateFlow<ClientCertificateStatus> = statusState
        var lastImport: ClientCertificateImport? = null
        var importResult: Result<ClientCertificateStatus> = Result.success(
            ClientCertificateStatus(enabled = true, materialPresent = true, subject = "CN=test"),
        )
        var enabledWrites: List<Boolean> = emptyList()
        var removed = false

        override suspend fun import(input: ClientCertificateImport): Result<ClientCertificateStatus> {
            lastImport = input
            return importResult
        }

        override fun setEnabled(enabled: Boolean) {
            enabledWrites += enabled
        }

        override fun remove() {
            removed = true
        }
    }

    private val certificateFacade = FakeClientCertificateRepository()

    // ------------------------------------------------------- toggle semantics

    @Test
    fun `portless grant shows toggle ON for ported primary`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `exact-address grant still shows ON and foreign grants show OFF`() = runTest(mainDispatcherRule.testDispatcher) {
        trustState.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://media.example.com:8920"))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.isSelfSignedTrustGranted(ServerInfo(id = "s1", name = "Media", address = "https://media.example.com:8920")))
        assertFalse(vm.isSelfSignedTrustGranted(ServerInfo(id = "s2", name = "Other", address = "https://other.example.com")))
        assertFalse(vm.isSelfSignedTrustGranted(ServerInfo(id = "s3", name = "Media", address = "https://media.example.com:8921")))
    }

    @Test
    fun `revoke drops a portless grant covering a ported primary`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `orphaned grant removed after address removal`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `grant still covering another address survives removal`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `removing a server drops its grant but keeps another server's grant`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `switchServer calls the repository and settles the spinner on success`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `switchServer failure skips the callback and settles the spinner`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `switchServer failure parks a message for the bus and it dismisses`() = runTest(mainDispatcherRule.testDispatcher) {
        // DECLARED FIX pin: a failed switch used to settle the spinner with no
        // feedback — the cards just re-enabled and nothing said the active
        // server had NOT changed. The VM now parks a UiMessage the screen
        // posts to the UserMessageBus exactly once.
        coEvery { authRepository.switchServer("s3") } returns Result.failure(RuntimeException("unreachable"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServer("s3") {}
        advanceUntilIdle()

        assertEquals(UiMessage.Raw("unreachable"), vm.switchServerFailure)

        vm.clearSwitchServerFailure()
        assertNull(vm.switchServerFailure)
    }

    @Test
    fun `switchServer failure without a message falls back to the localized resource`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.switchServer("s3") } returns Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServer("s3") {}
        advanceUntilIdle()

        assertEquals(
            UiMessage.Resource(Res.string.settings_server_switch_failed),
            vm.switchServerFailure,
        )
    }

    @Test
    fun `a new switch attempt clears the parked failure`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.switchServer("s3") } returns Result.failure(RuntimeException("down"))
        coEvery { authRepository.switchServer("s2") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServer("s3") {}
        advanceUntilIdle()
        assertEquals(UiMessage.Raw("down"), vm.switchServerFailure)

        vm.switchServer("s2") {}
        advanceUntilIdle()

        assertNull(vm.switchServerFailure)
        assertEquals(false, vm.isSwitching)
    }

    @Test
    fun `switchServer re-entry while switching is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { authRepository.switchServer("s2") } coAnswers {
            gate.await()
            Result.success(Unit)
        }
        coEvery { authRepository.switchServer("s3") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServer("s2") {}
        advanceUntilIdle()
        assertTrue(vm.isSwitching, "the first switch must be in flight")

        vm.switchServer("s3") {}
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.switchServer("s3") }
        assertEquals(false, vm.isSwitching)
    }

    @Test
    fun `addServerAddress success parks the confirmation face and settles the spinner`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()

        assertEquals(AddressOperationPhase.Completed("Address added"), vm.addressOperationPhase)
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `addServerAddress failure parks the error face and settles the spinner`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns
            Result.failure(RuntimeException("address already exists"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()

        assertEquals(
            AddressOperationPhase.Completed("address already exists"),
            vm.addressOperationPhase,
        )
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `addServerAddress failure without a message falls back to the generic wording`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns
            Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()

        assertEquals(
            AddressOperationPhase.Completed("Failed to add address"),
            vm.addressOperationPhase,
        )
    }

    @Test
    fun `switchServerAddress success names the new address`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.switchServerAddress("s1", "https://mirror.example.com") } returns
            Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServerAddress("s1", "https://mirror.example.com")
        advanceUntilIdle()

        assertEquals(
            AddressOperationPhase.Completed("Switched to https://mirror.example.com"),
            vm.addressOperationPhase,
        )
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `switchServerAddress failure falls back to the generic wording`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.switchServerAddress("s1", "https://mirror.example.com") } returns
            Result.failure(IllegalStateException())
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServerAddress("s1", "https://mirror.example.com")
        advanceUntilIdle()

        assertEquals(
            AddressOperationPhase.Completed("Failed to switch address"),
            vm.addressOperationPhase,
        )
    }

    @Test
    fun `clearAddressOperationMessage dismisses the banner`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()
        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()
        assertEquals(AddressOperationPhase.Completed("Address added"), vm.addressOperationPhase)

        vm.clearAddressOperationMessage()

        assertEquals(AddressOperationPhase.Idle, vm.addressOperationPhase)
    }

    // --------------------------------- address operation phase (the machine)

    @Test
    fun `addServerAddress runs the phase observable - Running carries the Add verb and the derived spinner`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } coAnswers {
                gate.await()
                Result.success(Unit)
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.addServerAddress("s1", "https://alt.example.com")
            advanceUntilIdle()

            assertEquals(AddressOperationPhase.Running(AddressOperationVerb.Add), vm.addressOperationPhase)
            assertTrue(vm.isAddressOperationInProgress, "Running must drive the derived spinner projection")

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AddressOperationPhase.Completed("Address added"), vm.addressOperationPhase)
            assertEquals(false, vm.isAddressOperationInProgress)
        }

    @Test
    fun `switchServerAddress runs the phase with the Switch verb`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { authRepository.switchServerAddress("s1", "https://mirror.example.com") } coAnswers {
            gate.await()
            Result.success(Unit)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchServerAddress("s1", "https://mirror.example.com")
        advanceUntilIdle()

        assertEquals(AddressOperationPhase.Running(AddressOperationVerb.Switch), vm.addressOperationPhase)
        assertTrue(vm.isAddressOperationInProgress)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            AddressOperationPhase.Completed("Switched to https://mirror.example.com"),
            vm.addressOperationPhase,
        )
        assertEquals(false, vm.isAddressOperationInProgress)
    }

    @Test
    fun `starting the next address operation clears a parked outcome at entry`() = runTest(mainDispatcherRule.testDispatcher) {
        // The analog of the switch half's "a new attempt clears the parked
        // failure": the parked Completed face is gone the moment the next
        // operation's Running lands (the former clear-at-start write).
        val gate = CompletableDeferred<Unit>()
        coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } returns Result.success(Unit)
        coEvery { authRepository.addServerAddress("s1", "https://other.example.com") } coAnswers {
            gate.await()
            Result.success(Unit)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.addServerAddress("s1", "https://alt.example.com")
        advanceUntilIdle()
        assertEquals(AddressOperationPhase.Completed("Address added"), vm.addressOperationPhase)

        vm.addServerAddress("s1", "https://other.example.com")
        advanceUntilIdle()

        assertEquals(AddressOperationPhase.Running(AddressOperationVerb.Add), vm.addressOperationPhase)
        assertTrue(
            vm.addressOperationPhase is AddressOperationPhase.Running,
            "Running carries no outcome text — the parked face is cleared at entry",
        )

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AddressOperationPhase.Completed("Address added"), vm.addressOperationPhase)
    }

    @Test
    fun `refiring from a parked outcome is allowed - the address ops keep no re-entry guard`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // PRESERVED VERBATIM (flagged in AddressOperationPhase's KDoc, not
            // a declared fix): unlike the switch half, the address operations
            // have no re-entry guard — from a parked Completed (or even mid-
            // Running) a second operation starts and each settle writes the
            // shared machine, exactly as the former per-op flag/message
            // writes interleaved.
            val gate = CompletableDeferred<Unit>()
            coEvery { authRepository.addServerAddress("s1", "https://alt.example.com") } coAnswers {
                gate.await()
                Result.success(Unit)
            }
            coEvery { authRepository.addServerAddress("s1", "https://other.example.com") } returns Result.success(Unit)
            val vm = viewModel()
            advanceUntilIdle()

            vm.addServerAddress("s1", "https://alt.example.com")
            advanceUntilIdle()
            assertTrue(vm.isAddressOperationInProgress)

            vm.addServerAddress("s1", "https://other.example.com")
            advanceUntilIdle()

            coVerify(exactly = 1) { authRepository.addServerAddress("s1", "https://other.example.com") }
            // The first op settles the shared machine (Running → Completed)
            // while the second is still in flight — the former shape's
            // per-op `inProgress=false` write, preserved.
            assertEquals(AddressOperationPhase.Completed("Address added"), vm.addressOperationPhase)
            assertEquals(false, vm.isAddressOperationInProgress)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                AddressOperationPhase.Completed("Address added"),
                vm.addressOperationPhase,
                "the second op's success text is byte-identical here",
            )
        }

    @Test
    fun `removing the active server fails over to the first remaining one`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `granting trust writes the normalized primary address`() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun `granting trust adds the https scheme to a schemeless address`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSelfSignedTrust(
            ServerInfo(id = "s1", name = "Media", address = " media.example.com "),
            granted = true,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { networkOfflineStore.addSelfSignedTrustHost("https://media.example.com") }
    }

    // ---------------------------------------------- client certificate

    @Test
    fun `certificate status flows from the facade and import success surfaces a message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.importClientCertificate(
                ClientCertificateImport(pkcs12Bytes = byteArrayOf(1, 2, 3), passphrase = "secret".toCharArray()),
            )
            advanceUntilIdle()

            val import = assertIs<ClientCertificateImport>(certificateFacade.lastImport)
            assertEquals(3, import.pkcs12Bytes?.size)
            assertIs<CertificateUserMessage.Imported>(vm.certificateMessage)
            assertFalse(vm.isCertificateOperationInProgress)
        }

    @Test
    fun `import failure surfaces the facade's user-presentable message`() = runTest(mainDispatcherRule.testDispatcher) {
        certificateFacade.importResult = Result.failure(IllegalArgumentException("not a readable PKCS#12 file"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.importClientCertificate(ClientCertificateImport(pkcs12Bytes = byteArrayOf(9)))
        advanceUntilIdle()

        assertEquals("not a readable PKCS#12 file", assertIs<CertificateUserMessage.ImportFailed>(vm.certificateMessage).text)
        assertFalse(vm.isCertificateOperationInProgress)
    }

    @Test
    fun `toggle and remove route to the facade`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setClientCertificateEnabled(false)
        vm.setClientCertificateEnabled(true)
        vm.removeClientCertificate()
        advanceUntilIdle()

        assertEquals(listOf(false, true), certificateFacade.enabledWrites)
        assertTrue(certificateFacade.removed)
    }
}
