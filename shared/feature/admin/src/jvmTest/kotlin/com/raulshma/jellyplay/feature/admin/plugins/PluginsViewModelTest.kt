package com.raulshma.jellyplay.feature.admin.plugins

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.model.PluginStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * Pins the plugins dashboard flow (`PluginsViewModel`):
 *
 *  - tab routing lazily loads and then caches the catalog (tab 1) and the
 *    repositories (tab 2); refresh re-fetches the installed list plus the
 *    active tab's data;
 *  - catalog filtering mirrors jellyfin-web: status chips partition by the
 *    installed GUIDs (default chip = Installed), category chips map the
 *    server taxonomy, and the free-text query matches
 *    name/description/category/owner case-insensitively;
 *  - install/uninstall/enable/disable route their arguments to the
 *    repository; a drained installation list re-fetches the installed
 *    plugins and stops the polling loop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    private val tv = PluginInfo(id = "guid-tv", name = "TV Head", version = "1.0", status = PluginStatus.ACTIVE)
    private val meta = PluginInfo(id = "guid-meta", name = "Metashark", version = "2.0", status = PluginStatus.DISABLED)

    private val pkgTv = PluginPackage(
        name = "TV Head", guid = "guid-tv", description = "dvr scheduling",
        owner = "jellyfin", category = "Live TV",
    )
    private val pkgTmdb = PluginPackage(
        name = "TMDb Box Sets", guid = "guid-tmdb", description = "auto collections",
        owner = "jellyfin", category = "Movies & Shows",
    )
    private val pkgAnime = PluginPackage(
        name = "AniDB", guid = "guid-anime", description = "anime metadata",
        owner = "community", category = "Anime",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk(relaxed = true)
        coEvery { adminRepository.getInstalledPlugins() } returns Result.success(listOf(tv, meta))
        // An empty installation list drains hasActiveInstalls and stops the
        // polling loop, which keeps advanceUntilIdle from spinning forever.
        coEvery { adminRepository.getPackageInstallations() } returns Result.success(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.pluginsViewModel(): PluginsViewModel =
        PluginsViewModel(adminRepository).also { advanceUntilIdle() }

    private fun TestScope.catalogViewModel(): PluginsViewModel {
        coEvery { adminRepository.getAvailablePackages() } returns
            Result.success(listOf(pkgAnime, pkgTv, pkgTmdb))
        val viewModel = pluginsViewModel()
        viewModel.selectTab(1)
        advanceUntilIdle()
        return viewModel
    }

    // ── tabs: installed / catalog / repositories ──

    @Test
    fun `init loads installed plugins sorted case-insensitively`() = runTest(mainDispatcher) {
        val viewModel = pluginsViewModel()

        assertEquals(listOf("Metashark", "TV Head"), viewModel.state.installedPlugins.map { it.name })
        assertFalse(viewModel.state.isLoading)
        assertNull(viewModel.state.error)
    }

    @Test
    fun `init failure surfaces the error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getInstalledPlugins() } returns
            Result.failure(RuntimeException("server down"))

        val viewModel = pluginsViewModel()

        assertEquals("server down", viewModel.state.error)
        assertTrue(viewModel.state.installedPlugins.isEmpty())
    }

    @Test
    fun `catalog tab loads sorted packages and caches them`() = runTest(mainDispatcher) {
        val viewModel = catalogViewModel()

        assertEquals(1, viewModel.state.selectedTabIndex)
        assertEquals(
            listOf("AniDB", "TMDb Box Sets", "TV Head"),
            viewModel.state.availablePackages.map { it.name },
        )
        assertFalse(viewModel.state.isCatalogLoading)

        // Re-entering the tab must not refetch the already-loaded catalog.
        viewModel.selectTab(1)
        viewModel.loadCatalog()
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.getAvailablePackages() }
    }

    @Test
    fun `repositories tab loads and caches`() = runTest(mainDispatcher) {
        val official = PluginRepository(name = "Official", url = "https://repo.jellyfin.org/")
        coEvery { adminRepository.getRepositories() } returns Result.success(listOf(official))

        val viewModel = pluginsViewModel()
        viewModel.selectTab(2)
        advanceUntilIdle()

        assertEquals(listOf(official), viewModel.state.repositories)
        assertFalse(viewModel.state.isReposLoading)

        viewModel.selectTab(2)
        viewModel.loadRepositories()
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.getRepositories() }
    }

    @Test
    fun `refresh re-fetches installed plus the active tab data`() = runTest(mainDispatcher) {
        val viewModel = catalogViewModel() // tab 1 → catalog fetched once

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { adminRepository.getAvailablePackages() }
        coVerify(atLeast = 2) { adminRepository.getInstalledPlugins() }
        assertFalse(viewModel.state.isRefreshing)
    }

    // ── catalog filters ──

    @Test
    fun `status filter partitions the catalog by installed guids`() = runTest(mainDispatcher) {
        val viewModel = catalogViewModel()

        // jellyfin-web default chip: Installed.
        assertEquals(PluginStatusFilter.INSTALLED, viewModel.state.catalogStatusFilter)
        assertEquals(listOf("TV Head"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateStatusFilter(PluginStatusFilter.AVAILABLE)
        assertEquals(listOf("AniDB", "TMDb Box Sets"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateStatusFilter(PluginStatusFilter.ALL)
        assertEquals(3, viewModel.state.filteredPackages.size)
    }

    @Test
    fun `category filter maps the server taxonomy`() = runTest(mainDispatcher) {
        val viewModel = catalogViewModel()
        viewModel.updateStatusFilter(PluginStatusFilter.ALL)

        viewModel.updateCategoryFilter(PluginCategory.LIVE_TV)
        assertEquals(listOf("TV Head"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateCategoryFilter(PluginCategory.MOVIES_AND_SHOWS)
        assertEquals(listOf("TMDb Box Sets"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateCategoryFilter(PluginCategory.ALL)
        assertEquals(3, viewModel.state.filteredPackages.size)
    }

    @Test
    fun `search matches name description category and owner case-insensitively`() = runTest(mainDispatcher) {
        val viewModel = catalogViewModel()
        viewModel.updateStatusFilter(PluginStatusFilter.ALL)

        viewModel.updateSearchQuery("tmdb")
        assertEquals(listOf("TMDb Box Sets"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateSearchQuery("COLLECTIONS") // description
        assertEquals(listOf("TMDb Box Sets"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateSearchQuery("community") // owner
        assertEquals(listOf("AniDB"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateSearchQuery("live tv") // category
        assertEquals(listOf("TV Head"), viewModel.state.filteredPackages.map { it.name })

        viewModel.updateSearchQuery("   ") // blank → no filtering
        assertEquals(3, viewModel.state.filteredPackages.size)
    }

    // ── install / uninstall / enable / disable routing ──

    @Test
    fun `installPackage routes its arguments and refreshes after draining`() = runTest(mainDispatcher) {
        coEvery { adminRepository.installPackage(any(), any(), any(), any()) } returns Result.success(Unit)
        val viewModel = pluginsViewModel()

        viewModel.installPackage(
            name = "TMDb Box Sets",
            assemblyGuid = "guid-tmdb",
            version = "1.2",
            repositoryUrl = "https://repo.jellyfin.org/",
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            adminRepository.installPackage(
                name = "TMDb Box Sets",
                assemblyGuid = "guid-tmdb",
                version = "1.2",
                repositoryUrl = "https://repo.jellyfin.org/",
            )
        }
        // The drained installation list re-fetches the installed plugins.
        coVerify(atLeast = 2) { adminRepository.getInstalledPlugins() }
        assertNull(viewModel.state.error)
    }

    @Test
    fun `install failure surfaces the error without polling installations`() = runTest(mainDispatcher) {
        coEvery { adminRepository.installPackage(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("repo offline"))
        val viewModel = pluginsViewModel()

        viewModel.installPackage(name = "TMDb Box Sets")
        advanceUntilIdle()

        assertEquals("repo offline", viewModel.state.error)
        coVerify(exactly = 0) { adminRepository.getPackageInstallations() }
    }

    @Test
    fun `uninstall routes to the repository and refreshes`() = runTest(mainDispatcher) {
        val viewModel = pluginsViewModel()

        viewModel.uninstallPlugin("guid-tv")
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.uninstallPlugin("guid-tv") }
        coVerify(atLeast = 2) { adminRepository.getInstalledPlugins() }
    }

    @Test
    fun `enable and disable route to setPluginEnabled`() = runTest(mainDispatcher) {
        val viewModel = pluginsViewModel()

        viewModel.enablePlugin(tv)
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.setPluginEnabled("guid-tv", "1.0", enabled = true) }

        viewModel.disablePlugin(tv)
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.setPluginEnabled("guid-tv", "1.0", enabled = false) }
    }

    // ── repository management ──

    @Test
    fun `addRepository appends and persists the new list`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getRepositories() } returns Result.success(emptyList())
        coEvery { adminRepository.setRepositories(any()) } returns Result.success(Unit)
        val viewModel = pluginsViewModel()
        viewModel.selectTab(2)
        advanceUntilIdle()

        viewModel.addRepository("Mine", "https://example.com/repo")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            adminRepository.setRepositories(
                listOf(PluginRepository(name = "Mine", url = "https://example.com/repo", isEnabled = true)),
            )
        }
        assertEquals(listOf("Mine"), viewModel.state.repositories.map { it.name })
        assertNull(viewModel.state.error)
    }

    @Test
    fun `addRepository failure keeps the old list and surfaces the error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getRepositories() } returns Result.success(emptyList())
        coEvery { adminRepository.setRepositories(any()) } returns
            Result.failure(RuntimeException("read only"))
        val viewModel = pluginsViewModel()
        viewModel.selectTab(2)
        advanceUntilIdle()

        viewModel.addRepository("Mine", "https://example.com/repo")
        advanceUntilIdle()

        assertEquals("read only", viewModel.state.error)
        assertTrue(viewModel.state.repositories.isEmpty())
    }

    @Test
    fun `removeRepository drops by index and out-of-range is a no-op`() = runTest(mainDispatcher) {
        val repos = listOf(
            PluginRepository(name = "First", url = "https://one"),
            PluginRepository(name = "Second", url = "https://two"),
        )
        coEvery { adminRepository.getRepositories() } returns Result.success(repos)
        coEvery { adminRepository.setRepositories(any()) } returns Result.success(Unit)
        val viewModel = pluginsViewModel()
        viewModel.selectTab(2)
        advanceUntilIdle()

        viewModel.removeRepository(9) // out of range → untouched
        advanceUntilIdle()
        coVerify(exactly = 0) { adminRepository.setRepositories(any()) }

        viewModel.removeRepository(0)
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.setRepositories(listOf(repos[1])) }
        assertEquals(listOf("Second"), viewModel.state.repositories.map { it.name })
    }

    @Test
    fun `toggleRepository flips isEnabled and persists`() = runTest(mainDispatcher) {
        val official = PluginRepository(name = "Official", url = "https://repo.jellyfin.org/")
        coEvery { adminRepository.getRepositories() } returns Result.success(listOf(official))
        coEvery { adminRepository.setRepositories(any()) } returns Result.success(Unit)
        val viewModel = pluginsViewModel()
        viewModel.selectTab(2)
        advanceUntilIdle()

        viewModel.toggleRepository(0, enabled = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.setRepositories(listOf(official.copy(isEnabled = false))) }
        assertFalse(viewModel.state.repositories.single().isEnabled)
    }

    // ── error surface ──

    @Test
    fun `clearError resets the error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getInstalledPlugins() } returns
            Result.failure(RuntimeException("boom"))
        val viewModel = pluginsViewModel()
        assertEquals("boom", viewModel.state.error)

        viewModel.clearError()
        assertNull(viewModel.state.error)
    }
}
