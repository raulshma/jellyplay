package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import kotlin.test.assertTrue

/**
 * Pins the *arr settings wiring (LibraryLayout jvmTest pattern: mockk
 * collaborators + real Result/[MutableStateFlow] stubs + inlined
 * Main-dispatcher rule). Manual-server mutations READ-MODIFY-WRITE through the
 * authoritative [ArrSecureCredentialsStore] (never the seeding StateFlow —
 * the cold-start race documented on the ViewModel), writes invalidate the
 * repository's resolved-server cache so the UI re-resolves immediately, and
 * resolved servers are auto-probed with the per-server Result folded into
 * [ArrSettingsViewModel.ServerConnectionStatus].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var arrRepository: ArrRepository
    private lateinit var arrPreferencesStore: ArrPreferencesStore
    private lateinit var secureCredentialsStore: ArrSecureCredentialsStore
    private val preferencesState = MutableStateFlow(ArrPreferences())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        arrRepository = mockk(relaxed = true)
        arrPreferencesStore = mockk(relaxed = true)
        secureCredentialsStore = mockk(relaxed = true)
        every { arrPreferencesStore.preferences } returns preferencesState
        every { arrPreferencesStore.setManualServers(any()) } returns Unit
        every { arrRepository.invalidateServers() } returns Unit
        every { secureCredentialsStore.getManualServers() } returns emptyList()
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ArrSettingsViewModel(arrRepository, arrPreferencesStore, secureCredentialsStore)

    @Test
    fun `refreshServers loads the resolved summary and settles the spinner`() = runTest {
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(ArrServiceSummary(), viewModel.servers.value)
        assertEquals(false, viewModel.isRefreshing.value)
        // Empty summary → nothing to auto-probe.
        coVerify(exactly = 0) { arrRepository.testServer(any()) }
    }

    @Test
    fun `resolve failure degrades to an empty summary`() = runTest {
        coEvery { arrRepository.resolveServers() } returns Result.failure(RuntimeException("down"))
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(ArrServiceSummary(), viewModel.servers.value)
        assertEquals(false, viewModel.isRefreshing.value)
    }

    @Test
    fun `resolved servers are auto-probed and report connectivity`() = runTest {
        val radarr = ArrServerConfig(
            id = "radarr-1", baseUrl = "https://radarr.example", apiKey = "k",
            name = "Radarr", kind = ArrServiceKind.RADARR,
        )
        val sonarr = ArrServerConfig(
            id = "sonarr-1", baseUrl = "https://sonarr.example", apiKey = "k",
            name = "Sonarr", kind = ArrServiceKind.SONARR,
        )
        coEvery { arrRepository.resolveServers() } returns
            Result.success(ArrServiceSummary(radarrServers = listOf(radarr), sonarrServers = listOf(sonarr)))
        coEvery { arrRepository.testServer(radarr) } returns Result.success(Unit)
        coEvery { arrRepository.testServer(sonarr) } returns Result.success(Unit)
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(
            ArrSettingsViewModel.ServerConnectionStatus.Connected,
            viewModel.serverStatus.value["radarr-1"],
        )
        assertEquals(
            ArrSettingsViewModel.ServerConnectionStatus.Connected,
            viewModel.serverStatus.value["sonarr-1"],
        )
    }

    @Test
    fun `a failed probe surfaces the error message`() = runTest {
        val radarr = ArrServerConfig(
            id = "radarr-1", baseUrl = "https://radarr.example", apiKey = "k",
            name = "Radarr", kind = ArrServiceKind.RADARR,
        )
        coEvery { arrRepository.resolveServers() } returns
            Result.success(ArrServiceSummary(radarrServers = listOf(radarr)))
        coEvery { arrRepository.testServer(radarr) } returns Result.failure(RuntimeException("401"))
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(
            ArrSettingsViewModel.ServerConnectionStatus.Error("401"),
            viewModel.serverStatus.value["radarr-1"],
        )
    }

    @Test
    fun `addManualServer appends to the persisted manual list and invalidates the cache`() = runTest {
        val existing = ArrServerConfig(
            id = "manual-radarr-old", baseUrl = "https://old.example", apiKey = "k0",
            name = "Old", kind = ArrServiceKind.RADARR, isManual = true,
        )
        every { secureCredentialsStore.getManualServers() } returns listOf(existing)
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())
        val viewModel = viewModel()
        advanceUntilIdle()
        val written = slot<List<ArrServerConfig>>()

        viewModel.addManualServer(" New ", "https://new.example/", " k1 ", ArrServiceKind.RADARR)
        advanceUntilIdle()

        verify(exactly = 1) { arrPreferencesStore.setManualServers(capture(written)) }
        assertEquals(2, written.captured.size)
        val added = written.captured.last()
        assertEquals("https://new.example", added.baseUrl)
        assertEquals("New", added.name)
        assertEquals("k1", added.apiKey)
        assertTrue(added.isManual)
        // Cache invalidation is what makes the new server show up immediately.
        verify(exactly = 1) { arrRepository.invalidateServers() }
    }

    @Test
    fun `addManualServer ignores blank input`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addManualServer("  ", "https://new.example", "k1", ArrServiceKind.RADARR)
        advanceUntilIdle()

        verify(exactly = 0) { arrPreferencesStore.setManualServers(any()) }
        verify(exactly = 0) { arrRepository.invalidateServers() }
    }

    @Test
    fun `removeManualServer writes back the remaining servers`() = runTest {
        val kept = ArrServerConfig(
            id = "manual-radarr-a", baseUrl = "https://a.example", apiKey = "k",
            name = "A", kind = ArrServiceKind.RADARR, isManual = true,
        )
        val removed = ArrServerConfig(
            id = "manual-radarr-b", baseUrl = "https://b.example", apiKey = "k",
            name = "B", kind = ArrServiceKind.RADARR, isManual = true,
        )
        every { secureCredentialsStore.getManualServers() } returns listOf(kept, removed)
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())
        val viewModel = viewModel()
        advanceUntilIdle()
        val written = slot<List<ArrServerConfig>>()

        viewModel.removeManualServer(removed)
        advanceUntilIdle()

        verify(exactly = 1) { arrPreferencesStore.setManualServers(capture(written)) }
        assertEquals(listOf(kept), written.captured)
    }

    @Test
    fun `discovered servers are not removable`() = runTest {
        val discovered = ArrServerConfig(
            id = "radarr-3", baseUrl = "https://radarr.example", apiKey = "k",
            name = "Radarr", kind = ArrServiceKind.RADARR, isManual = false,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.removeManualServer(discovered)
        advanceUntilIdle()

        verify(exactly = 0) { arrPreferencesStore.setManualServers(any()) }
    }

    @Test
    fun `discovery toggle persists then re-resolves`() = runTest {
        coEvery { arrPreferencesStore.setUseSeerrDiscovery(false) } returns Unit
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setUseSeerrDiscovery(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { arrPreferencesStore.setUseSeerrDiscovery(false) }
        verify(atLeast = 1) { arrRepository.invalidateServers() }
    }
}
