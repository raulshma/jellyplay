package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.DiscoveredServer
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_ssl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * Pins the [AddServerConnectPhase] / [AddServerDiscoveryPhase] machine that
 * replaced AddServerUiState's former flag triple (`isConnecting` /
 * `connectError` / `tlsTrustPromptAddress`) and its out-of-state
 * `pendingTrustRetry` closure:
 *
 * 1. Connect runs Idle → Connecting → {Idle | Failed | TlsTrustPending}, one
 *    atomic write per landing.
 * 2. TlsTrustPending CARRIES the failure's explanatory error — the former
 *    machine set the prompt and the error together and the screen renders
 *    both (dialog + field supporting text).
 * 3. [AddServerViewModel.clearError] cannot orphan a trust dialog: during
 *    Failed it returns to Idle; during TlsTrustPending it is a no-op
 *    (prompt-without-error is unrepresentable — the bug the fold killed).
 * 4. [AddServerViewModel.dismissTrustPrompt] returns to Failed with the SAME
 *    error (the message stays visible), the armed retry dying with the phase.
 * 5. [AddServerViewModel.confirmTrustServer] consumes the retry
 *    synchronously (the double-tap guard, formerly the hidden
 *    `pendingTrustRetry = null`), keeps the dialog up through the
 *    propagation await, then lands through Idle into the retry's outcome —
 *    including the DOCUMENTED propagation-timeout re-prompt loop, preserved
 *    deliberately (not fixed) in this pass.
 * 6. [AddServerViewModel.updateManualAddress] lands in Idle in ONE atomic
 *    write (no armed-retry survivor), and confirm afterwards is a full no-op.
 * 7. Connect and discovery are orthogonal BY INTENT: a connect never stops a
 *    Running scan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelPhaseTest {

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
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(NetworkOfflineSlice())
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

    // ── 1. the connect ladder ────────────────────────────────────────────

    @Test
    fun connect_passesThroughConnecting_thenLandsIdleOnSuccess() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        val gate = CompletableDeferred<Result<ServerInfo>>()
        coEvery { authRepository.addServer("http://server") } coAnswers { gate.await() }
        var received: Result<ServerInfo>? = null

        viewModel.connectToServer("http://server") { received = it }

        // The entry write is synchronous: Connecting is observable before any
        // launched coroutine runs.
        assertEquals(AddServerConnectPhase.Connecting, viewModel.uiState.value.connectPhase)
        assertNull(received)

        gate.complete(Result.success(info))
        advanceUntilIdle()

        assertEquals(AddServerConnectPhase.Idle, viewModel.uiState.value.connectPhase)
        assertTrue(received?.isSuccess == true)
    }

    @Test
    fun connect_genericFailure_landsInFailed_withTheClassifiedError() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(RuntimeException("boom"))

        viewModel.connectToServer("http://server") {}
        advanceUntilIdle()

        val phase = viewModel.uiState.value.connectPhase
        assertTrue(phase is AddServerConnectPhase.Failed, "a non-TLS failure lands in Failed")
        assertEquals(UiMessage.Raw("boom"), (phase as AddServerConnectPhase.Failed).error)
    }

    // ── 2. TlsTrustPending carries the error ─────────────────────────────

    @Test
    fun connect_tlsFailure_landsInTlsTrustPending_carryingErrorAndArmedRetry() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX path building failed"))

        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()

        val phase = viewModel.uiState.value.connectPhase
        assertTrue(phase is AddServerConnectPhase.TlsTrustPending, "the TLS failure must arm the trust dialog")
        val pending = phase as AddServerConnectPhase.TlsTrustPending
        // The canonical normalized entry — exactly the endpoint that failed.
        assertEquals("https://selfsigned.example.com", pending.address)
        // The phase carries the SAME failure's explanatory error (rendered
        // under the field while the dialog is up).
        assertEquals(UiMessage.Resource(Res.string.auth_error_ssl), pending.error)
        assertTrue(pending.retry != null, "the retry must be armed while the dialog is pending")
    }

    // ── 3. clearError cannot orphan a trust dialog ───────────────────────

    @Test
    fun clearError_duringFailed_returnsToIdle() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("http://server") } returns
            Result.failure(RuntimeException("boom"))
        viewModel.connectToServer("http://server") {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectPhase is AddServerConnectPhase.Failed)

        viewModel.clearError()

        assertEquals(AddServerConnectPhase.Idle, viewModel.uiState.value.connectPhase)
    }

    @Test
    fun clearError_duringTlsTrustPending_isANoop_theDialogKeepsItsError() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))
        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()
        val pendingBefore = viewModel.uiState.value.connectPhase as AddServerConnectPhase.TlsTrustPending

        // THE BUG-KILL PIN: the former machine let clearError drop
        // `connectError` while the trust dialog stayed up AND the retry stayed
        // armed — a dialog with no explanatory error. The error is part of
        // the phase now: clearing it alone is unrepresentable, so clearError
        // stands down.
        viewModel.clearError()

        val phase = viewModel.uiState.value.connectPhase
        assertTrue(phase is AddServerConnectPhase.TlsTrustPending, "the dialog phase must survive clearError")
        val pending = phase as AddServerConnectPhase.TlsTrustPending
        assertEquals(pendingBefore.address, pending.address)
        assertEquals(pendingBefore.error, pending.error)
        assertTrue(pending.retry != null, "the retry must stay armed with the dialog")
    }

    // ── 4. dismiss keeps the error visible ───────────────────────────────

    @Test
    fun dismissTrustPrompt_returnsToFailed_withTheSameError() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))
        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()
        val errorBefore = (viewModel.uiState.value.connectPhase as AddServerConnectPhase.TlsTrustPending).error

        viewModel.dismissTrustPrompt()

        // The former machine kept the error message visible after a decline —
        // preserved by landing in Failed with the SAME failure's error (the
        // armed retry dies with the phase).
        assertEquals(AddServerConnectPhase.Failed(errorBefore), viewModel.uiState.value.connectPhase)
    }

    // ── 5. confirm: consume guard, await, retry outcome ──────────────────

    @Test
    fun confirmTrustServer_keepsDialogUpDuringAwait_consumesRetryImmediately_retriesToIdle() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX")) andThen
            Result.success(info)
        val storeFlow = MutableStateFlow(NetworkOfflineSlice())
        every { networkOfflineStore.networkOffline } returns storeFlow

        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()

        viewModel.confirmTrustServer()
        // Edit done, propagation pending: the dialog stays up...
        runCurrent()
        val awaiting = viewModel.uiState.value.connectPhase
        assertTrue(
            awaiting is AddServerConnectPhase.TlsTrustPending,
            "the dialog must stay up during the propagation await",
        )
        // ...but the retry is already consumed — a double-tap confirm cannot
        // re-invoke it (the former code's synchronous `pendingTrustRetry =
        // null` guard, now a visible state field).
        assertNull((awaiting as AddServerConnectPhase.TlsTrustPending).retry)

        storeFlow.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://selfsigned.example.com"))
        advanceUntilIdle()

        assertEquals(AddServerConnectPhase.Idle, viewModel.uiState.value.connectPhase)
        coVerify(exactly = 1) { networkOfflineStore.addSelfSignedTrustHost("https://selfsigned.example.com") }
    }

    @Test
    fun confirmTrustServer_doubleTapWhileAwaiting_retriesOnlyOnce_writesGrantTwice() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX")) andThen
            Result.success(info)
        val storeFlow = MutableStateFlow(NetworkOfflineSlice())
        every { networkOfflineStore.networkOffline } returns storeFlow

        var attempts = 0
        viewModel.connectToServer("https://selfsigned.example.com") { attempts++ }
        advanceUntilIdle()
        assertEquals(1, attempts)

        viewModel.confirmTrustServer()
        // Second tap on confirm while the dialog is still up (propagation
        // pending): the grant write happens again, but the retry must fire
        // only once — parity with the former machine's double-confirm.
        viewModel.confirmTrustServer()
        storeFlow.value = NetworkOfflineSlice(selfSignedTrustHosts = setOf("https://selfsigned.example.com"))
        advanceUntilIdle()

        coVerify(exactly = 2) { networkOfflineStore.addSelfSignedTrustHost("https://selfsigned.example.com") }
        assertEquals(2, attempts, "the retry must fire exactly once despite the double confirm")
        assertEquals(AddServerConnectPhase.Idle, viewModel.uiState.value.connectPhase)
    }

    @Test
    fun confirmTrustServer_propagationTimeout_retryFailsTlsAgain_rePrompts() = runTest(testDispatcher) {
        // BOTH attempts fail TLS: the documented re-prompt loop is preserved
        // deliberately — on propagation timeout the retry proceeds anyway and
        // fails like an ungranted host, re-arming the dialog (do not "fix"
        // this loop in this pass; the pin exists so changing it is conscious).
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))
        // Propagation never lands: the bounded 5s wait expires (virtual time).
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(NetworkOfflineSlice())

        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()

        viewModel.confirmTrustServer()
        advanceUntilIdle()

        coVerify(exactly = 2) { authRepository.addServer("https://selfsigned.example.com") }
        val phase = viewModel.uiState.value.connectPhase
        assertTrue(
            phase is AddServerConnectPhase.TlsTrustPending,
            "the timeout re-prompts: the retried connect failed TLS again",
        )
        val pending = phase as AddServerConnectPhase.TlsTrustPending
        assertEquals("https://selfsigned.example.com", pending.address)
        assertTrue(pending.retry != null, "the re-prompt carries a FRESH armed retry")
    }

    // ── 6. updateManualAddress: one atomic write ─────────────────────────

    @Test
    fun updateManualAddress_fromTlsTrustPending_landsInIdle_inOneWrite_andConfirmNoOps() = runTest(testDispatcher) {
        coEvery { authRepository.addServer("https://selfsigned.example.com") } returns
            Result.failure(javax.net.ssl.SSLHandshakeException("PKIX"))
        viewModel.connectToServer("https://selfsigned.example.com") {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectPhase is AddServerConnectPhase.TlsTrustPending)

        // One atomic write: address text AND the phase reset land together —
        // no prompt-without-error intermediate, no armed-retry survivor (the
        // retry lived IN the phase and dies with the transition).
        viewModel.updateManualAddress("https://other.example.com")

        val state = viewModel.uiState.value
        assertEquals("https://other.example.com", state.manualAddress)
        assertEquals(AddServerConnectPhase.Idle, state.connectPhase)

        viewModel.confirmTrustServer()
        advanceUntilIdle()

        // The stale retry died with the phase: no grant write, no second
        // connect attempt.
        coVerify(exactly = 0) { networkOfflineStore.addSelfSignedTrustHost(any()) }
        coVerify(exactly = 1) { authRepository.addServer(any()) }
    }

    // ── 7. orthogonality: connect never stops discovery ──────────────────

    @Test
    fun connect_leavesDiscoveryRunning_thePhasesAreOrthogonal() = runTest(testDispatcher) {
        // Orthogonality BY INTENT: today's connect never stops a running scan
        // (the server-count row keeps its scanning spinner during a connect).
        // Pin it so a future mutual-exclusion change is a conscious one.
        val server = DiscoveredServer(id = "1", name = "one", address = "http://192.168.1.10:8096")
        val discoveryGate = CompletableDeferred<Unit>()
        every { serverDiscoveryRepository.discoverLocalServers() } returns flow {
            emit(server)
            discoveryGate.await()
        }
        viewModel.startDiscovery()
        advanceUntilIdle()
        assertEquals(AddServerDiscoveryPhase.Running, viewModel.uiState.value.discoveryPhase)

        val info = mockk<ServerInfo>()
        coEvery { authRepository.addServer(server.address) } returns Result.success(info)
        viewModel.connectToServer(server.address) {}
        advanceUntilIdle()

        assertEquals(AddServerConnectPhase.Idle, viewModel.uiState.value.connectPhase)
        assertEquals(
            AddServerDiscoveryPhase.Running,
            viewModel.uiState.value.discoveryPhase,
            "connect must not stop a running scan",
        )
        assertEquals(listOf(server), viewModel.uiState.value.discoveredServers)
    }
}
