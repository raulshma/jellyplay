package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [PluginAdminRepositoryImpl]'s stateless forwards over the
 * [PluginApiClient] family seam (moved verbatim from AdminRepositoryImpl at
 * the admin facade split — the LiveTvRepositoryImpl pattern; the family
 * single composes the same impl the JellyfinApiClient union delegates to,
 * so wire behavior is unchanged). The two compositions live in the jvmTest
 * twin.
 */
class PluginAdminRepositoryImplTest {

    private val pluginApiClient: PluginApiClient = mockk(relaxed = true)
    private val engine: JellyfinApiEngine = mockk(relaxed = true)
    private val repository = PluginAdminRepositoryImpl(pluginApiClient, engine)

    @Test
    fun `plugin operations delegate to the family client`() = runTest {
        val plugin = PluginInfo(id = "p1", name = "Webhooks", version = "1.0")
        coEvery { pluginApiClient.getInstalledPlugins() } returns Result.success(listOf(plugin))
        coEvery { pluginApiClient.enablePlugin("p1", "1.0") } returns Result.success(Unit)
        coEvery { pluginApiClient.disablePlugin("p1", "1.0") } returns Result.success(Unit)
        coEvery { pluginApiClient.uninstallPlugin("p1") } returns Result.success(Unit)
        coEvery { pluginApiClient.getPackageInstallations() } returns Result.success(emptyList())
        coEvery { pluginApiClient.getRepositories() } returns Result.success(emptyList())
        val repos = listOf(PluginRepository(name = "Official", url = "https://repo", isEnabled = true))
        coEvery { pluginApiClient.setRepositories(repos) } returns Result.success(Unit)

        repository.getInstalledPlugins()
        repository.setPluginEnabled("p1", "1.0", enabled = true)
        repository.setPluginEnabled("p1", "1.0", enabled = false)
        repository.uninstallPlugin("p1")
        repository.getPackageInstallations()
        repository.getRepositories()
        repository.setRepositories(repos)

        coVerify(exactly = 1) { pluginApiClient.getInstalledPlugins() }
        coVerify(exactly = 1) { pluginApiClient.enablePlugin("p1", "1.0") }
        coVerify(exactly = 1) { pluginApiClient.disablePlugin("p1", "1.0") }
        coVerify(exactly = 1) { pluginApiClient.uninstallPlugin("p1") }
        coVerify(exactly = 1) { pluginApiClient.getPackageInstallations() }
        coVerify(exactly = 1) { pluginApiClient.getRepositories() }
        coVerify(exactly = 1) { pluginApiClient.setRepositories(repos) }
    }

    @Test
    fun `getPackageInfo and catalog installs delegate`() = runTest {
        coEvery { pluginApiClient.getAvailablePackages() } returns Result.success(emptyList())
        coEvery { pluginApiClient.getPackageInfo("Webhooks", "guid-1") } returns
            Result.success(PluginPackage(name = "Webhooks", guid = "guid-1"))
        coEvery { pluginApiClient.installPackage(name = "Webhooks", assemblyGuid = null, version = "2.0", repositoryUrl = null) } returns
            Result.success(Unit)
        coEvery { pluginApiClient.cancelPackageInstallation("inst-1") } returns Result.success(Unit)

        repository.getAvailablePackages()
        val pkg = repository.getPackageInfo("Webhooks", "guid-1").getOrNull()!!
        repository.installPackage(name = "Webhooks", assemblyGuid = null, version = "2.0", repositoryUrl = null)
        repository.cancelPackageInstallation("inst-1")

        assertEquals("Webhooks", pkg.name)
        coVerify(exactly = 1) { pluginApiClient.getAvailablePackages() }
        coVerify(exactly = 1) { pluginApiClient.getPackageInfo("Webhooks", "guid-1") }
        coVerify(exactly = 1) { pluginApiClient.installPackage(name = "Webhooks", assemblyGuid = null, version = "2.0", repositoryUrl = null) }
        coVerify(exactly = 1) { pluginApiClient.cancelPackageInstallation("inst-1") }
    }
}
