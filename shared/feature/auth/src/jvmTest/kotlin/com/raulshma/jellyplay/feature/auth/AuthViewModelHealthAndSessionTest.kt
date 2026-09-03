package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_auth
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_initiate
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_error_polling
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gaps in [AuthViewModel] coverage NOT pinned by [AuthViewModelTest] (which
 * exercises only the QuickConnect happy/sad branches):
 *
 * 1. [AuthViewModel.checkServersHealth] — the whole function was untested:
 *    - every server is synchronously published as [ServerHealth.Checking]
 *      before any probe runs (the UI's immediate dot),
 *    - a reachable primary address lands [ServerHealth.Healthy] (latency > 0),
 *    - an all-addresses-fail server lands [ServerHealth.Unreachable],
 *    - the alternate-address failover: a failed primary + a succeeding
 *      alternate still counts the SERVER as healthy (one probe per address,
 *      short-circuiting `any`),
 *    - an empty list resets the health map to empty,
 *    - a re-entrant call cancels the in-flight batch so only the newest
 *      batch's results land.
 * 2. Session delegation: [AuthViewModel.addServer] toggles [AuthViewModel.isLoading]
 *    around the repository call and reports the result through the callback;
 *    [AuthViewModel.login], [AuthViewModel.switchUser],
 *    [AuthViewModel.removeUser], [AuthViewModel.removeServer] and
 *    [AuthViewModel.getUsersForServer] forward 1:1 to the repository.
 * 3. QuickConnect branches the legacy suite missed: the
 *    [QuickConnectUiState.WaitingForApproval] state carries code + secret;
 *    initiate/poll failures WITHOUT an exception message fall back to their
 *    localized resources; `loginWithQuickConnect` failure surfaces
 *    `auth_qc_error_auth` (raw-message-wins semantics); a fresh
 *    [AuthViewModel.startQuickConnect] cancels the previous polling job (the
 *    stale poller must not overwrite the new session's state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelHealthAndSessionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        every { authRepository.servers } returns MutableStateFlow(emptyList())
        every { authRepository.currentServerUsers } returns MutableStateFlow(emptyList())
        viewModel = AuthViewModel(authRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun server(id: String, address: String, vararg alternates: String) = ServerInfo(
        id = id,
        name = "Server $id",
        address = address,
        alternateAddresses = alternates.toList(),
    )

    // ── checkServersHealth ────────────────────────────────────────────────

    @Test
    fun checkServersHealth_publishesCheckingSynchronously_beforeAnyProbe() = runTest(testDispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { authRepository.probeServer(any()) } coAnswers { gate.await(); Result.success(mockk()) }

        viewModel.checkServersHealth(listOf(server("a", "http://a")))

        // No virtual time advanced: the Checking prefetch must already be
        // visible (the list renders the dot before the first probe resolves).
        assertEquals(
            mapOf("http://a" to ServerHealth.Checking),
            viewModel.serverHealth.value,
        )
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun checkServersHealth_reachablePrimary_landsHealthyWithLatency() = runTest(testDispatcher) {
        coEvery { authRepository.probeServer("http://a") } returns Result.success(mockk())

        viewModel.checkServersHealth(listOf(server("a", "http://a")))
        advanceUntilIdle()

        val health = viewModel.serverHealth.value["http://a"]
        assertTrue(health is ServerHealth.Healthy, "expected Healthy, was $health")
        assertTrue((health as ServerHealth.Healthy).latencyMs >= 0L)
    }

    @Test
    fun checkServersHealth_allAddressesFail_landsUnreachable() = runTest(testDispatcher) {
        coEvery { authRepository.probeServer("http://a") } returns Result.failure(RuntimeException("down"))
        coEvery { authRepository.probeServer("http://a-alt") } returns Result.failure(RuntimeException("down"))

        viewModel.checkServersHealth(listOf(server("a", "http://a", "http://a-alt")))
        advanceUntilIdle()

        assertEquals(ServerHealth.Unreachable, viewModel.serverHealth.value["http://a"])
        // Both addresses were tried before giving up.
        coVerify(exactly = 1) { authRepository.probeServer("http://a") }
        coVerify(exactly = 1) { authRepository.probeServer("http://a-alt") }
    }

    @Test
    fun checkServersHealth_primaryFailsAlternateSucceeds_serverIsHealthy() = runTest(testDispatcher) {
        coEvery { authRepository.probeServer("http://a") } returns Result.failure(RuntimeException("down"))
        coEvery { authRepository.probeServer("http://a-alt") } returns Result.success(mockk())

        viewModel.checkServersHealth(listOf(server("a", "http://a", "http://a-alt")))
        advanceUntilIdle()

        // The address failover: ANY reachable address makes the server healthy.
        assertTrue(viewModel.serverHealth.value["http://a"] is ServerHealth.Healthy)
        // `any` short-circuits: the alternate answered, so exactly 2 probes total
        // (failed primary + succeeding alternate).
        coVerify(exactly = 2) { authRepository.probeServer(any()) }
    }

    @Test
    fun checkServersHealth_emptyList_resetsTheHealthMap() = runTest(testDispatcher) {
        coEvery { authRepository.probeServer("http://a") } returns Result.success(mockk())
        viewModel.checkServersHealth(listOf(server("a", "http://a")))
        advanceUntilIdle()
        assertTrue(viewModel.serverHealth.value.isNotEmpty())

        viewModel.checkServersHealth(emptyList())
        advanceUntilIdle()

        assertEquals(emptyMap(), viewModel.serverHealth.value)
    }

    @Test
    fun checkServersHealth_reentrantCall_cancelsTheInFlightBatch() = runTest(testDispatcher) {
        // First batch's probe never completes until the gate opens — its batch
        // coroutine gets cancelled by the re-entrant call, so its result must
        // never land.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { authRepository.probeServer("http://a") } coAnswers { gate.await(); Result.success(mockk()) }
        coEvery { authRepository.probeServer("http://b") } returns Result.success(mockk())

        viewModel.checkServersHealth(listOf(server("a", "http://a")))
        advanceUntilIdle()
        assertEquals(mapOf("http://a" to ServerHealth.Checking), viewModel.serverHealth.value)

        viewModel.checkServersHealth(listOf(server("b", "http://b")))
        advanceUntilIdle()

        // Batch 2 completed; batch 1 was cancelled so "a" never resolved to
        // Healthy (whether the re-entrant batch replaces or merges the map,
        // the cancelled probe's result must never land).
        // (Healthy carries a measured latencyMs, so assert the variant, not a value.)
        assertTrue(viewModel.serverHealth.value["http://b"] is ServerHealth.Healthy)
        assertTrue(viewModel.serverHealth.value["http://a"] !is ServerHealth.Healthy)

        // Opening the gate changes nothing: batch 1's continuation is dead.
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.serverHealth.value["http://b"] is ServerHealth.Healthy)
        assertTrue(viewModel.serverHealth.value["http://a"] !is ServerHealth.Healthy)
    }

    // ── session delegation ────────────────────────────────────────────────

    @Test
    fun addServer_togglesIsLoading_andReportsTheResult() = runTest(testDispatcher) {
        val info = mockk<ServerInfo>()
        val gate = kotlinx.coroutines.CompletableDeferred<Result<ServerInfo>>()
        coEvery { authRepository.addServer("http://a") } coAnswers { gate.await() }

        var received: Result<ServerInfo>? = null
        viewModel.addServer("http://a") { received = it }
        advanceUntilIdle()

        // In-flight: the spinner flag is up and the callback has not run yet.
        assertTrue(viewModel.isLoading.value)
        kotlin.test.assertNull(received)

        gate.complete(Result.success(info))
        advanceUntilIdle()

        kotlin.test.assertFalse(viewModel.isLoading.value)
        assertTrue(received?.isSuccess == true)
    }

    @Test
    fun login_forwardsAddressUsernamePassword_andReportsTheResult() = runTest(testDispatcher) {
        coEvery { authRepository.login("http://a", "user", "pass") } returns
            Result.success(mockk<UserInfo>())

        var received: Result<Unit>? = null
        viewModel.login("http://a", "user", "pass") { received = it }
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.login("http://a", "user", "pass") }
        assertTrue(received?.isSuccess == true)
    }

    @Test
    fun switchUser_and_removeUser_forwardToTheRepository() = runTest(testDispatcher) {
        coEvery { authRepository.switchUser("u1") } returns Result.success(Unit)
        coEvery { authRepository.removeUser(any()) } just Runs

        var received: Result<Unit>? = null
        viewModel.switchUser("u1") { received = it }
        advanceUntilIdle()
        viewModel.removeUser("u2")
        advanceUntilIdle()

        assertTrue(received?.isSuccess == true)
        coVerify(exactly = 1) { authRepository.switchUser("u1") }
        coVerify(exactly = 1) { authRepository.removeUser("u2") }
    }

    @Test
    fun removeServer_and_getUsersForServer_forwardToTheRepository() = runTest(testDispatcher) {
        coEvery { authRepository.removeServer("srv-1") } just Runs
        coEvery { authRepository.getUsersForServer("srv-1") } returns listOf(mockk(), mockk())

        var users: List<UserInfo>? = null
        viewModel.getUsersForServer("srv-1") { users = it }
        advanceUntilIdle()
        viewModel.removeServer("srv-1")
        advanceUntilIdle()

        assertEquals(2, users?.size)
        coVerify(exactly = 1) { authRepository.getUsersForServer("srv-1") }
        coVerify(exactly = 1) { authRepository.removeServer("srv-1") }
    }

    // ── QuickConnect branches the legacy suite missed ─────────────────────

    @Test
    fun quickConnect_initiate_landsWaitingForApprovalWithCodeAndSecret() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "sec-1", code = "ABCD")
        )
        // Never authenticates.
        coEvery { authRepository.pollQuickConnect("sec-1") } returns Result.success(
            QuickConnectState(authenticated = false, secret = "sec-1")
        )

        viewModel.startQuickConnect("http://server")
        // Advance into the FIRST poll cycle only: advanceUntilIdle would burn
        // all 40 × 3s virtual attempts and land the timeout error instead.
        advanceTimeBy(3_000)
        runCurrent()

        val state = viewModel.quickConnectState.value
        assertTrue(state is QuickConnectUiState.WaitingForApproval, "was $state")
        assertEquals("ABCD", state.code)
        assertEquals("sec-1", state.secret)
    }

    @Test
    fun quickConnect_initiateFails_withoutMessage_fallsBackToResource() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.failure(RuntimeException())

        viewModel.startQuickConnect("http://server")
        advanceUntilIdle()

        val state = viewModel.quickConnectState.value
        assertEquals(
            AuthMessage.Resource(Res.string.auth_qc_error_initiate),
            (state as QuickConnectUiState.Error).message,
        )
    }

    @Test
    fun quickConnect_pollFails_withoutMessage_fallsBackToPollingResource() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "sec-1", code = "ABCD")
        )
        coEvery { authRepository.pollQuickConnect("sec-1") } returns Result.failure(RuntimeException())

        viewModel.startQuickConnect("http://server")
        advanceUntilIdle()

        val state = viewModel.quickConnectState.value
        assertEquals(
            AuthMessage.Resource(Res.string.auth_qc_error_polling),
            (state as QuickConnectUiState.Error).message,
        )
    }

    @Test
    fun quickConnect_loginWithQuickConnectFails_surfacesAuthErrorResource() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        coEvery { authRepository.initiateQuickConnect() } returns Result.success(
            QuickConnectInfo(secret = "sec-1", code = "ABCD")
        )
        coEvery { authRepository.pollQuickConnect("sec-1") } returns Result.success(
            QuickConnectState(authenticated = true, secret = "sec-1")
        )
        coEvery { authRepository.loginWithQuickConnect("http://server", "sec-1") } returns
            Result.failure(RuntimeException())

        viewModel.startQuickConnect("http://server")
        advanceUntilIdle()

        val state = viewModel.quickConnectState.value
        assertEquals(
            AuthMessage.Resource(Res.string.auth_qc_error_auth),
            (state as QuickConnectUiState.Error).message,
        )
        coVerify(exactly = 1) { authRepository.loginWithQuickConnect("http://server", "sec-1") }
    }

    @Test
    fun quickConnect_restart_cancelsThePreviousPoller() = runTest(testDispatcher) {
        coEvery { authRepository.isQuickConnectEnabled() } returns Result.success(true)
        // Session 1 initiates with sec-1, the restarted session with sec-2.
        coEvery { authRepository.initiateQuickConnect() } returnsMany listOf(
            Result.success(QuickConnectInfo(secret = "sec-1", code = "ABCD")),
            Result.success(QuickConnectInfo(secret = "sec-2", code = "EFGH")),
        )
        // First session's poll never authenticates; the restarted session's
        // poll authenticates immediately.
        coEvery { authRepository.pollQuickConnect("sec-1") } returns Result.success(
            QuickConnectState(authenticated = false, secret = "sec-1")
        )
        coEvery { authRepository.pollQuickConnect("sec-2") } returns Result.success(
            QuickConnectState(authenticated = true, secret = "sec-2")
        )
        coEvery { authRepository.loginWithQuickConnect(any(), any()) } returns Result.success(mockk<UserInfo>())

        viewModel.startQuickConnect("http://server")
        // Advance INTO the first poll cycle without draining it to the 40-attempt
        // timeout (advanceUntilIdle would burn all 40 × 3s virtual delays): one
        // delay elapses, the first (unauthenticated) poll runs, and the poller
        // parks on its next 3s delay.
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(viewModel.quickConnectState.value is QuickConnectUiState.WaitingForApproval)

        // Restart: the new session completes even though the first poller was
        // still waiting on its 3s delay — it must have been cancelled, or its
        // eventual polls would keep racing the new session's state toward the
        // timeout error.
        viewModel.startQuickConnect("http://server")
        advanceUntilIdle()

        assertTrue(
            viewModel.quickConnectState.value is QuickConnectUiState.Success,
            "was ${viewModel.quickConnectState.value}",
        )
    }
}
