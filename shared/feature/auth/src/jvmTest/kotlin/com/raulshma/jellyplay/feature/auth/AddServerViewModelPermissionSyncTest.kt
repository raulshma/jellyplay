package com.raulshma.jellyplay.feature.auth

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.DiscoveredServer
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The local-network permission choreography machine behind
 * [AddServerViewModel.onLocalNetworkAccessSynced] — the decisions extracted
 * out of AddServerScreen's former inline
 * `LaunchedEffect(localNetwork.isGranted)` (composition-only, untested):
 *
 * 1. The rationale-banner predicate (`enforced && !granted`) folding into
 *    [AddServerUiState.localNetworkRationale] — desktop's non-enforcing
 *    platform can never set it.
 * 2. Discovery auto-start: a granted report starts a scan (screen entry or
 *    post-grant transition); a denied report never starts one (a scan
 *    without the permission silently finds nothing — the trap the banner
 *    replaces).
 * 3. Parity with the former per-composition effect: a granted sync AFTER a
 *    scan already completed starts a FRESH scan (servers reset) — the screen
 *    fires the sync on every entry, exactly like the old effect did.
 * 4. The [AddServerViewModel.startDiscovery] isDiscovering guard dedupes a
 *    granted sync while a scan is still running (repository invoked once).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddServerViewModelPermissionSyncTest {

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

    /** A never-completing scan: keeps [AddServerUiState.isDiscovering] true. */
    private fun stubNeverEndingScan() {
        every { serverDiscoveryRepository.discoverLocalServers() } returns
            flow<DiscoveredServer> { awaitCancellation() }
    }    // ── rationale predicate ───────────────────────────────────────────────

    @Test
    fun desktopShape_enforcedFalse_grantedTrue_neverShowsRationale_andStartsDiscovery() = runTest(testDispatcher) {
        stubNeverEndingScan()

        viewModel.onLocalNetworkAccessSynced(enforced = false, granted = true)

        val state = viewModel.uiState.value
        assertFalse(state.localNetworkRationale)
        assertTrue(state.isDiscovering, "the desktop entry sync must auto-start the scan")
    }

    @Test
    fun androidDenied_showsRationale_withoutStartingDiscovery() = runTest(testDispatcher) {
        viewModel.onLocalNetworkAccessSynced(enforced = true, granted = false)

        val state = viewModel.uiState.value
        assertTrue(state.localNetworkRationale)
        assertFalse(state.isDiscovering)
        coVerify(exactly = 0) { serverDiscoveryRepository.discoverLocalServers() }
    }

    @Test
    fun enforcedAndGranted_showsNoRationale() = runTest(testDispatcher) {
        stubNeverEndingScan()

        viewModel.onLocalNetworkAccessSynced(enforced = true, granted = true)

        assertFalse(viewModel.uiState.value.localNetworkRationale)
    }

    // ── grant transition ─────────────────────────────────────────────────

    @Test
    fun grantArrivingAfterDenial_clearsRationale_andStartsDiscovery() = runTest(testDispatcher) {
        viewModel.onLocalNetworkAccessSynced(enforced = true, granted = false)
        assertTrue(viewModel.uiState.value.localNetworkRationale)
        coVerify(exactly = 0) { serverDiscoveryRepository.discoverLocalServers() }

        stubNeverEndingScan()
        viewModel.onLocalNetworkAccessSynced(enforced = true, granted = true)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.localNetworkRationale)
        assertTrue(state.isDiscovering, "the post-grant sync must start the scan")
        coVerify(exactly = 1) { serverDiscoveryRepository.discoverLocalServers() }
    }

    // ── re-entry + dedupe parity ─────────────────────────────────────────

    @Test
    fun grantedSyncAfterAScanCompleted_startsAFreshScan_andResetsServers() = runTest(testDispatcher) {
        // First entry: the scan completes with a server found.
        val server = DiscoveredServer(id = "1", name = "one", address = "http://192.168.1.10:8096")
        every { serverDiscoveryRepository.discoverLocalServers() } returns flowOf(server)
        viewModel.onLocalNetworkAccessSynced(enforced = false, granted = true)
        advanceUntilIdle()
        assertEquals(listOf(server), viewModel.uiState.value.discoveredServers)
        assertFalse(viewModel.uiState.value.isDiscovering)

        // Screen re-entry fires the sync again (the former composition effect
        // did the same) — a fresh scan resets the discovered list.
        every { serverDiscoveryRepository.discoverLocalServers() } returns flowOf()
        viewModel.onLocalNetworkAccessSynced(enforced = false, granted = true)
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.uiState.value.discoveredServers)
        coVerify(exactly = 2) { serverDiscoveryRepository.discoverLocalServers() }
    }

    @Test
    fun grantedSyncWhileScanRunning_isDedupedByTheDiscoveringGuard() = runTest(testDispatcher) {
        stubNeverEndingScan()
        viewModel.onLocalNetworkAccessSynced(enforced = false, granted = true)
        runCurrent()
        assertTrue(viewModel.uiState.value.isDiscovering)

        viewModel.onLocalNetworkAccessSynced(enforced = false, granted = true)

        coVerify(exactly = 1) { serverDiscoveryRepository.discoverLocalServers() }
        // Already-known servers survive the deduped sync (no state reset).
        assertTrue(viewModel.uiState.value.isDiscovering)
    }
}
