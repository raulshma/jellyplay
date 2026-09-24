package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import okhttp3.OkHttpClient

/**
 * MediaRepository facade split's admin twin (the LiveTvRepositoryImpl
 * precedent): every plugin member moved verbatim from [AdminRepositoryImpl]
 * — a stateless forward over the [PluginApiClient] family seam (not the
 * JellyfinApiClient union; the family single composes the same impl the
 * union delegates to, so wire behavior is unchanged). The WebView bridge
 * session reads through narrow ctor seams instead of the whole engine:
 * the active-address read (failover-correct — the engine's
 * activeServerAddress, never currentServer's primary, which may be the
 * dead endpoint after a failover), ONE atomic [ActiveSession] read for the
 * user identity (CONTEXT.md "Session identity": never re-derive identity
 * from two separate flows), and the constructor-injected OkHttpClient —
 * the same unqualified Koin single the engine exposes lazily (the
 * DlnaCastStrategy wiring precedent).
 */
class PluginAdminRepositoryImpl(
    private val pluginApiClient: PluginApiClient,
    private val activeServerAddress: () -> String?,
    private val session: () -> ActiveSession?,
    private val okHttpClient: OkHttpClient,
) : PluginAdminRepository {

    override val pluginWebViewSession: PluginWebViewSession
        get() {
            // ONE session read: user id and access token come from the same
            // atomic ActiveSession value — two separate flow reads could
            // observe a mixed (new server, old user) identity mid-transition.
            val session = session()
            return PluginWebViewSession(
                serverAddress = activeServerAddress().orEmpty(),
                userId = session?.user?.id.orEmpty(),
                accessToken = session?.user?.accessToken.orEmpty(),
                okHttpClient = okHttpClient,
            )
        }

    override suspend fun getInstalledPlugins(): Result<List<PluginInfo>> =
        pluginApiClient.getInstalledPlugins()

    override suspend fun getAvailablePackages(): Result<List<PluginPackage>> =
        pluginApiClient.getAvailablePackages()

    override suspend fun getPackageInfo(name: String, assemblyGuid: String?): Result<PluginPackage> =
        pluginApiClient.getPackageInfo(name, assemblyGuid)

    override suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>> =
        pluginApiClient.getPackageInstallations()

    override suspend fun installPackage(
        name: String,
        assemblyGuid: String?,
        version: String?,
        repositoryUrl: String?,
    ): Result<Unit> = pluginApiClient.installPackage(
        name = name,
        assemblyGuid = assemblyGuid,
        version = version,
        repositoryUrl = repositoryUrl,
    )

    override suspend fun cancelPackageInstallation(packageId: String): Result<Unit> =
        pluginApiClient.cancelPackageInstallation(packageId)

    override suspend fun setPluginEnabled(pluginId: String, version: String, enabled: Boolean): Result<Unit> =
        if (enabled) pluginApiClient.enablePlugin(pluginId, version) else pluginApiClient.disablePlugin(pluginId, version)

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> =
        pluginApiClient.uninstallPlugin(pluginId)

    override suspend fun getRepositories(): Result<List<PluginRepository>> =
        pluginApiClient.getRepositories()

    override suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit> =
        pluginApiClient.setRepositories(repositories)

    override suspend fun getPluginConfigPage(pluginId: String): Result<PluginConfigPageContent?> {
        val pages = pluginApiClient.getConfigurationPages().getOrElse { return Result.failure(it) }
        val page = pages.find { it.pluginId == pluginId } ?: return Result.success(null)
        return pluginApiClient.getDashboardConfigurationPage(page.name).map { html ->
            PluginConfigPageContent(name = page.name, html = html)
        }
    }
}
