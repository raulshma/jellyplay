package com.raulshma.jellyplay.feature.admin.plugins

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.PluginConfigPageContent
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginStatus
import com.raulshma.jellyplay.core.model.PluginVersionInfo
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
 * Pins the plugin detail flow (`PluginDetailViewModel`):
 *
 *  - initialize resolves the installed plugin by id and fan-outs the package
 *    (version) info and the config-page lookup; unknown ids and repository
 *    failures degrade to the route's id/name placeholder instead of a blank
 *    screen;
 *  - re-initializing the same id is a no-op (route change guard);
 *  - toggleEnabled routes by the plugin's current status (ACTIVE → disable,
 *    DISABLED → enable) and paints an optimistic isEnabledOverride before
 *    the repository call resolves;
 *  - uninstall routes to the repository and fires the completion callback
 *    only on success;
 *  - installVersion pins the optimistic version and routes the package
 *    name, GUID, version and repository URL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.initializedViewModel(
        status: PluginStatus = PluginStatus.ACTIVE,
    ): PluginDetailViewModel {
        val plugin = PluginInfo(id = "guid-1", name = "TV Head", version = "1.0", status = status)
        coEvery { adminRepository.getInstalledPlugins() } returns Result.success(listOf(plugin))
        // Real Results everywhere: a relaxed Result hands the VM a mocked
        // Throwable whose Log.e/message path dead-ends in kotlin-reflect.
        coEvery { adminRepository.setPluginEnabled(any(), any(), any()) } returns Result.success(Unit)
        coEvery { adminRepository.uninstallPlugin(any()) } returns Result.success(Unit)
        coEvery { adminRepository.getPackageInfo("TV Head", "guid-1") } returns
            Result.success(
                PluginPackage(
                    name = "TV Head",
                    guid = "guid-1",
                    versions = listOf(PluginVersionInfo(version = "1.0")),
                ),
            )
        coEvery { adminRepository.getPluginConfigPage("guid-1") } returns
            Result.success(PluginConfigPageContent(name = "Config", html = "<html/>"))
        val viewModel = PluginDetailViewModel(adminRepository)
        viewModel.initialize("guid-1", "TV Head")
        advanceUntilIdle()
        return viewModel
    }

    // ── load ──

    @Test
    fun `initialize resolves the plugin package info and config page`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()

        assertEquals("guid-1", viewModel.state.plugin?.id)
        assertFalse(viewModel.state.isLoading)
        assertTrue(viewModel.state.hasConfigPage)
        assertEquals("Config", viewModel.state.configPageName)
        assertEquals("TV Head", viewModel.state.pluginPackage?.name)
        assertNull(viewModel.state.error)
    }

    @Test
    fun `initialize with an unknown id falls back to the route placeholder`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getInstalledPlugins() } returns Result.success(emptyList())
        val viewModel = PluginDetailViewModel(adminRepository)

        viewModel.initialize("guid-x", "From Route")
        advanceUntilIdle()

        assertEquals("guid-x", viewModel.state.plugin?.id)
        assertEquals("From Route", viewModel.state.plugin?.name)
        assertFalse(viewModel.state.isLoading)
        assertFalse(viewModel.state.hasConfigPage)
        assertNull(viewModel.state.error)
    }

    @Test
    fun `initialize failure surfaces the error with the route placeholder`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getInstalledPlugins() } returns
            Result.failure(RuntimeException("server down"))
        val viewModel = PluginDetailViewModel(adminRepository)

        viewModel.initialize("guid-x", "From Route")
        advanceUntilIdle()

        assertEquals("server down", viewModel.state.error)
        assertEquals("guid-x", viewModel.state.plugin?.id)
        assertFalse(viewModel.state.isLoading)
    }

    @Test
    fun `re-initializing the same plugin does not refetch`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()

        viewModel.initialize("guid-1", "TV Head")
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.getInstalledPlugins() }
    }

    // ── enable / disable ──

    @Test
    fun `toggleEnabled on an active plugin routes to disable with optimistic override`() =
        runTest(mainDispatcher) {
            val viewModel = initializedViewModel(status = PluginStatus.ACTIVE)

            viewModel.toggleEnabled()
            // Optimistic override paints synchronously, before the repo call.
            assertTrue(viewModel.state.isToggling)
            assertEquals(false, viewModel.state.isEnabledOverride)

            advanceUntilIdle()

            coVerify(exactly = 1) { adminRepository.setPluginEnabled("guid-1", "1.0", enabled = false) }
            assertNull(viewModel.state.isEnabledOverride)
            assertFalse(viewModel.state.isToggling)
        }

    @Test
    fun `toggleEnabled on a disabled plugin routes to enable`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel(status = PluginStatus.DISABLED)

        viewModel.toggleEnabled()
        assertEquals(true, viewModel.state.isEnabledOverride)

        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.setPluginEnabled("guid-1", "1.0", enabled = true) }
        assertNull(viewModel.state.isEnabledOverride)
    }

    @Test
    fun `toggleEnabled failure keeps the override clear-free error`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel(status = PluginStatus.ACTIVE)
        coEvery { adminRepository.setPluginEnabled(any(), any(), any()) } returns
            Result.failure(RuntimeException("forbidden"))

        viewModel.toggleEnabled()
        advanceUntilIdle()

        assertEquals("forbidden", viewModel.state.error)
        assertFalse(viewModel.state.isToggling)
        assertNull(viewModel.state.isEnabledOverride)
        // Failure must not reload the plugin list.
        coVerify(exactly = 1) { adminRepository.getInstalledPlugins() }
    }

    @Test
    fun `toggleEnabled without a loaded plugin is a no-op`() = runTest(mainDispatcher) {
        val viewModel = PluginDetailViewModel(adminRepository)

        viewModel.toggleEnabled()
        advanceUntilIdle()

        coVerify(exactly = 0) { adminRepository.setPluginEnabled(any(), any(), any()) }
    }

    // ── uninstall ──

    @Test
    fun `uninstall routes to the repository and fires completion on success`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()
        var completed = false

        viewModel.uninstall { completed = true }
        assertTrue(viewModel.state.isUninstalling)
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.uninstallPlugin("guid-1") }
        assertTrue(completed)
    }

    @Test
    fun `uninstall failure surfaces the error and never completes`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()
        coEvery { adminRepository.uninstallPlugin(any()) } returns
            Result.failure(RuntimeException("uninstall failed"))
        var completed = false

        viewModel.uninstall { completed = true }
        advanceUntilIdle()

        assertEquals("uninstall failed", viewModel.state.error)
        assertFalse(viewModel.state.isUninstalling)
        assertFalse(completed)
    }

    // ── install a specific version ──

    @Test
    fun `installVersion routes package guid version and repository url`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()
        coEvery { adminRepository.installPackage(any(), any(), any(), any()) } returns Result.success(Unit)
        val older = PluginVersionInfo(version = "0.9", repositoryUrl = "https://old.example.com")

        viewModel.installVersion(older)
        assertEquals("0.9", viewModel.state.installingVersion) // optimistic pin

        advanceUntilIdle()

        coVerify(exactly = 1) {
            adminRepository.installPackage(
                name = "TV Head",
                assemblyGuid = "guid-1",
                version = "0.9",
                repositoryUrl = "https://old.example.com",
            )
        }
        assertNull(viewModel.state.installingVersion)
    }

    @Test
    fun `installVersion failure surfaces the error and clears the pin`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()
        coEvery { adminRepository.installPackage(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("download failed"))

        viewModel.installVersion(PluginVersionInfo(version = "0.9"))
        advanceUntilIdle()

        assertEquals("download failed", viewModel.state.error)
        assertNull(viewModel.state.installingVersion)
    }

    // ── error surface ──

    @Test
    fun `clearError resets the error`() = runTest(mainDispatcher) {
        val viewModel = initializedViewModel()
        coEvery { adminRepository.setPluginEnabled(any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))
        viewModel.toggleEnabled()
        advanceUntilIdle()
        assertEquals("boom", viewModel.state.error)

        viewModel.clearError()

        assertNull(viewModel.state.error)
    }
}
