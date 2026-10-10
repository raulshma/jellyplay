package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayDevice
import com.raulshma.jellyplay.core.network.api.JellyPlayDeviceRegistryRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistory
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKey
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKeys
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncNamespaceUsage
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sync screen's per-key diff behavior (the mockk idiom, the
 * [SeerrSettingsViewModelTest] pattern): tapping a history row fetches its
 * changed keys LAZILY, exactly once per seq, and the cached detail survives
 * collapse/re-expand. The degrade ladder is quiet by contract: a reset row's
 * empty diff reads "No key changes", a pre-wave 404 (null payload) and a
 * failed read both cache the "—" degrade, and a degraded gate never touches
 * the api at all. The Phase-2 faces ride the same refresh: the snapshot list
 * (null = the pre-wave hide), the namespace rows (server usage ∪ engine
 * namespaces, selective-sync flags folded in), and the device rows (model +
 * revoked carried, this-device first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlaySyncViewModelTest {

    /** Polls until [condition] holds, pumping the test scheduler between waits. */
    private suspend fun kotlinx.coroutines.test.TestScope.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        advanceUntilIdle()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, "condition not met within ${timeoutMs}ms")
            withContext(Dispatchers.Default) { delay(10) }
            advanceUntilIdle()
        }
    }

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var pluginApi: JellyPlaySettingsSyncRoutes
    private lateinit var deviceRegistry: JellyPlayDeviceRegistryRoutes
    private lateinit var statusStore: JellyPlayPluginStatusStore
    private lateinit var syncRepository: ProfileSyncRepository
    private val pluginStatus = MutableStateFlow(JellyPlayPluginStatus.UNAVAILABLE)

    /** Recorded route/engine calls — the awaitUntil-friendly observation seam. */
    private val toggleCalls = mutableListOf<Pair<String, Boolean>>()
    private val renameCalls = mutableListOf<Pair<String, String?>>()
    private val revokeCalls = mutableListOf<String>()
    private val createCalls = mutableListOf<Int>()
    private val resetCalls = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        pluginApi = mockk(relaxed = true)
        deviceRegistry = mockk(relaxed = true)
        statusStore = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        every { statusStore.status } returns pluginStatus
        every { syncRepository.activeNamespaces } returns listOf("prefs", "books")
        // The pending face's baseline: no pending keys anywhere.
        every { syncRepository.state } returns MutableStateFlow(ProfileSyncRepository.SyncState())
        coEvery { syncRepository.namespaceEnabled(any()) } returns true
        coEvery { syncRepository.setNamespaceEnabled(any(), any()) } coAnswers {
            toggleCalls += arg<String>(0) to arg<Boolean>(1)
        }
        // The status/history pull's baseline: an empty status, four ledger rows.
        coEvery { pluginApi.getSyncStatus() } returns Result.success(null)
        coEvery { pluginApi.getSyncHistory(any(), any()) } returns Result.success(
            JellyPlaySyncHistory(
                entries = listOf(
                    JellyPlaySyncHistoryEntry(seq = 1, op = "push"),
                    JellyPlaySyncHistoryEntry(seq = 2, op = "reset"),
                    JellyPlaySyncHistoryEntry(seq = 3, op = "pull"),
                    JellyPlaySyncHistoryEntry(seq = 4, op = "push"),
                ),
            ),
        )
        coEvery { deviceRegistry.getDevices() } returns Result.success(emptyList())
        coEvery { pluginApi.adminSyncOverview() } returns Result.success(null)
        coEvery { pluginApi.getSnapshots() } returns Result.success(null)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The probe AVAILABLE + `settings-sync` registry state the gate reads. */
    private fun gateOpen() {
        pluginStatus.value = JellyPlayPluginStatus.AVAILABLE
        every { statusStore.hasFeature(JellyPlayPluginFeatures.SettingsSync) } returns true
    }

    private fun viewModel() = JellyPlaySyncViewModel(syncRepository, pluginApi, deviceRegistry, statusStore)

    @Test
    fun setSyncEnabled_off_deArmsThePeriodicCatchUp() {
        val cancelCalls = mutableListOf<Int>()
        val scheduler = object : com.raulshma.jellyplay.core.data.worker.SettingsSyncScheduler {
            override fun enqueueNow() = Unit
            override fun enqueuePeriodicIfEnabled() = Unit
            override fun cancelPeriodic() {
                cancelCalls += 1
            }
        }

        JellyPlaySyncViewModel(syncRepository, pluginApi, deviceRegistry, statusStore, syncScheduler = scheduler)
            .setSyncEnabled(enabled = false)

        coVerify(exactly = 1) { syncRepository.setEnabled(false) }
        assertEquals(1, cancelCalls.size)
    }

    @Test
    fun setSyncEnabled_on_doesNotTouchThePeriodicArm() = runTest {
        val cancelCalls = mutableListOf<Int>()
        val scheduler = object : com.raulshma.jellyplay.core.data.worker.SettingsSyncScheduler {
            override fun enqueueNow() = Unit
            override fun enqueuePeriodicIfEnabled() = Unit
            override fun cancelPeriodic() {
                cancelCalls += 1
            }
        }

        JellyPlaySyncViewModel(syncRepository, pluginApi, deviceRegistry, statusStore, syncScheduler = scheduler)
            .setSyncEnabled(enabled = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { syncRepository.setEnabled(true) }
        assertTrue(cancelCalls.isEmpty())
    }

    @Test
    fun expand_fetchesKeysLazily_andCachesPerSeq() = runTest {
        gateOpen()
        coEvery { pluginApi.getSyncHistoryKeys(1, any()) } returns Result.success(
            JellyPlaySyncHistoryKeys(
                seq = 1,
                op = "push",
                keys = listOf(JellyPlaySyncHistoryKey("prefs", "theme", 1_700_000_000_000)),
            ),
        )
        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }
        assertTrue(viewModel.uiState.value.historyKeyDetails.isEmpty(), "diffs must not fetch before the first expand")

        viewModel.toggleHistoryEntryExpanded(1)
        awaitUntil { viewModel.uiState.value.historyKeyDetails.containsKey(1L) }

        assertEquals(1L, viewModel.uiState.value.expandedHistorySeq)
        assertEquals(
            listOf(JellyPlaySyncHistoryKey("prefs", "theme", 1_700_000_000_000)),
            viewModel.uiState.value.historyKeyDetails[1L]?.keys,
        )
        coVerify(exactly = 1) { pluginApi.getSyncHistoryKeys(1, any()) }

        // Collapse: no fetch, detail stays cached.
        viewModel.toggleHistoryEntryExpanded(1)
        assertNull(viewModel.uiState.value.expandedHistorySeq)

        // Re-expand: served from the cache, still exactly one fetch.
        viewModel.toggleHistoryEntryExpanded(1)
        assertEquals(1L, viewModel.uiState.value.expandedHistorySeq)
        coVerify(exactly = 1) { pluginApi.getSyncHistoryKeys(1, any()) }
    }

    @Test
    fun expand_resetRow_emptyKeys_otherSeq_404_nullKeys() = runTest {
        gateOpen()
        coEvery { pluginApi.getSyncHistoryKeys(2, any()) } returns Result.success(
            JellyPlaySyncHistoryKeys(seq = 2, op = "reset", keys = emptyList()),
        )
        // The pre-wave 404: the route answers null.
        coEvery { pluginApi.getSyncHistoryKeys(3, any()) } returns Result.success(null)
        // A failed read degrades the same quiet way.
        coEvery { pluginApi.getSyncHistoryKeys(4, any()) } returns Result.failure(IllegalStateException("500"))
        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        viewModel.toggleHistoryEntryExpanded(2)
        awaitUntil { viewModel.uiState.value.historyKeyDetails.containsKey(2L) }
        assertEquals(emptyList<JellyPlaySyncHistoryKey>(), viewModel.uiState.value.historyKeyDetails[2L]?.keys)

        viewModel.toggleHistoryEntryExpanded(3)
        awaitUntil { viewModel.uiState.value.historyKeyDetails.containsKey(3L) }
        assertNull(viewModel.uiState.value.historyKeyDetails[3L]?.keys, "404 must degrade to null keys, never an error")

        viewModel.toggleHistoryEntryExpanded(4)
        awaitUntil { viewModel.uiState.value.historyKeyDetails.containsKey(4L) }
        assertNull(viewModel.uiState.value.historyKeyDetails[4L]?.keys, "a failed read must degrade to null keys too")
    }

    @Test
    fun degradedGate_expandNeverTouchesTheApi() = runTest {
        // Probe UNAVAILABLE + registry empty (the relaxed defaults): the gate
        // is closed, the row may expand locally but no fetch may fire.
        val viewModel = viewModel()
        viewModel.toggleHistoryEntryExpanded(1)
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.expandedHistorySeq)
        assertTrue(viewModel.uiState.value.historyKeyDetails.isEmpty())
        coVerify(exactly = 0) { pluginApi.getSyncHistoryKeys(any(), any()) }
    }

    // ── the Phase-2 faces: snapshots, namespace rows, device rows, registry actions ──

    @Test
    fun refresh_loadsSnapshots_degradesQuietlyOnPreWave404() = runTest {
        gateOpen()
        coEvery { pluginApi.getSnapshots() } returns Result.success(
            listOf(
                com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot(
                    id = 1L, createdAt = 1_700_000_000_000, origin = "manual", keys = 4, bytes = 900,
                ),
            ),
        )
        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }
        assertEquals(1, viewModel.uiState.value.snapshots?.size)

        // The pre-wave 404 (null) hides the section, never errors.
        coEvery { pluginApi.getSnapshots() } returns Result.success(null)
        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.snapshots == null }
    }

    @Test
    fun refresh_namespaceRows_mergeServerUsageEngineNamespacesAndToggles() = runTest {
        gateOpen()
        coEvery { pluginApi.getSyncStatus() } returns Result.success(
            JellyPlaySyncStatus(
                namespaces = listOf(
                    JellyPlaySyncNamespaceUsage("prefs", keys = 10, bytes = 2048),
                    JellyPlaySyncNamespaceUsage("cw", keys = 2, bytes = 64),
                ),
            ),
        )
        coEvery { syncRepository.namespaceEnabled("prefs") } returns true
        coEvery { syncRepository.namespaceEnabled("books") } returns false
        every { syncRepository.state } returns MutableStateFlow(
            ProfileSyncRepository.SyncState(pendingByNamespace = mapOf("books" to 3)),
        )

        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.namespaceRows.isNotEmpty() }

        val rows = viewModel.uiState.value.namespaceRows.associateBy { it.ns }
        // Server-only namespace: usage without a toggle.
        assertEquals(JellyPlaySyncNamespaceRow("cw", 2, 64, 0, toggleable = false, enabled = true), rows["cw"])
        // Engine namespace: toggleable, flag from the persisted seam, pending folded in.
        assertEquals(JellyPlaySyncNamespaceRow("prefs", 10, 2048, 0, toggleable = true, enabled = true), rows["prefs"])
        assertEquals(JellyPlaySyncNamespaceRow("books", 0, 0, 3, toggleable = true, enabled = false), rows["books"])
    }

    @Test
    fun setNamespaceEnabled_flipsRowOptimistically_andDelegatesToTheEngine() = runTest {
        gateOpen()
        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.namespaceRows.isNotEmpty() }

        viewModel.setNamespaceEnabled("prefs", false)
        assertEquals(false, viewModel.uiState.value.namespaceRows.first { it.ns == "prefs" }.enabled)
        awaitUntil { toggleCalls.size == 1 }
        assertEquals("prefs" to false, toggleCalls.single())
    }

    @Test
    fun refresh_deviceRows_carryModelRevoked_thisDeviceFirst() = runTest {
        gateOpen()
        coEvery { syncRepository.currentDeviceId() } returns "device-a"
        coEvery { deviceRegistry.getDevices() } returns Result.success(
            listOf(
                JellyPlayDevice(deviceId = "device-b", name = "Living Room", platform = "tv", model = "Onn 4K", revoked = true),
                JellyPlayDevice(deviceId = "device-a", name = "Desk", platform = "desktop", model = null),
            ),
        )

        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.devices.isNotEmpty() }

        val rows = viewModel.uiState.value.devices
        assertEquals(listOf("device-a", "device-b"), rows.map { it.deviceId }, "this device first")
        val this_ = rows[0]
        val revoked = rows[1]
        assertTrue(this_.isThisDevice)
        assertNull(this_.model)
        assertTrue(revoked.revoked)
        assertEquals("Onn 4K", revoked.model)
    }

    @Test
    fun registryActions_revoke_and_rename_callTheRoutes_andRefresh() = runTest {
        gateOpen()
        coEvery { syncRepository.currentDeviceId() } returns "device-a"
        coEvery { deviceRegistry.renameDevice(any(), any(), any()) } coAnswers {
            renameCalls += arg<String>(0) to arg<String?>(1)
            Result.success(Unit)
        }
        coEvery { deviceRegistry.revokeDevice(any()) } coAnswers {
            revokeCalls += arg<String>(0)
            Result.success(Unit)
        }

        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { viewModel.uiState.value.thisDeviceId == "device-a" }

        viewModel.renameThisDevice("New name")
        awaitUntil { renameCalls.size == 1 }
        assertEquals("device-a" to "New name", renameCalls.single())

        viewModel.revokeDevice("device-b")
        awaitUntil { revokeCalls.size == 1 }
        assertEquals("device-b", revokeCalls.single())
    }

    @Test
    fun snapshotActions_createGuardsAndRefreshes() = runTest {
        gateOpen()
        coEvery { pluginApi.createSnapshot() } coAnswers {
            createCalls += 1
            Result.success(com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotCreated(9L))
        }

        val viewModel = viewModel()
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        viewModel.createSnapshot()
        awaitUntil { createCalls.size == 1 }
        assertFalse(viewModel.uiState.value.actionError)
    }

    @Test
    fun resetNamespace_capturesSafetySnapshotFirst_andWarnsOnMiss() = runTest {
        gateOpen()
        coEvery { pluginApi.resetNamespace(any(), any()) } coAnswers {
            resetCalls += arg<String>(0)
            Result.success(Unit)
        }
        // A failed capture must warn through the one-shot face — and never block the reset.
        coEvery { pluginApi.createSnapshot() } returns Result.failure(IllegalStateException("500"))

        val viewModel = viewModel()
        viewModel.resetNamespace()
        awaitUntil { resetCalls.size == 1 }

        assertTrue(viewModel.uiState.value.safetySnapshotMissed, "a missed capture must surface the warning face")
        assertEquals("prefs", resetCalls.single(), "the reset still ran — the miss never blocks")

        viewModel.clearSafetySnapshotMissed()
        assertFalse(viewModel.uiState.value.safetySnapshotMissed)
    }

    @Test
    fun resetNamespace_gateClosed_captureSucceedsQuietly() = runTest {
        // Probe UNAVAILABLE: the gate is closed, no api call may fire at all —
        // and the closed gate is NOT a capture failure.
        val viewModel = viewModel()
        viewModel.resetNamespace()
        advanceUntilIdle()

        assertEquals(0, resetCalls.size)
        assertFalse(viewModel.uiState.value.safetySnapshotMissed)
    }
}
