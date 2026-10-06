package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistory
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKey
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKeys
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sync screen's per-key diff behavior (the mockk idiom, the
 * [SeerrSettingsViewModelTest] pattern): tapping a history row fetches its
 * changed keys LAZILY, exactly once per seq, and the cached detail survives
 * collapse/re-expand. The degrade ladder is quiet by contract: a reset row's
 * empty diff reads "No key changes", a pre-wave 404 (null payload) and a
 * failed read both cache the "—" degrade, and a degraded gate never touches
 * the api at all.
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

    private lateinit var pluginApi: JellyPlayPluginApiClient
    private lateinit var statusStore: JellyPlayPluginStatusStore
    private lateinit var syncRepository: ProfileSyncRepository
    private val pluginStatus = MutableStateFlow(JellyPlayPluginStatus.UNAVAILABLE)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        pluginApi = mockk(relaxed = true)
        statusStore = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        every { statusStore.status } returns pluginStatus
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
        coEvery { pluginApi.getDevices() } returns Result.success(emptyList())
        coEvery { pluginApi.adminSyncOverview() } returns Result.success(null)
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

    private fun viewModel() = JellyPlaySyncViewModel(syncRepository, pluginApi, statusStore)

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
}
