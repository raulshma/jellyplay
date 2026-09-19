package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository

/**
 * AdminRepository facade split: the twelve plugin-family members moved
 * verbatim from [AdminRepository] (the LiveTvRepository/NewsletterRepository
 * precedent) — the plugins screens (list, detail, config) are a single-family
 * consumer, so they inject this narrow seam instead of the admin union.
 * Every member is a stateless forward over [com.raulshma.jellyplay.core.network.api.PluginApiClient]
 * except the two compositions the family owns (config-page resolution and
 * the WebView bridge session), so this impl owns NO cache state.
 */
interface PluginAdminRepository {

    suspend fun getInstalledPlugins(): Result<List<PluginInfo>>

    suspend fun getAvailablePackages(): Result<List<PluginPackage>>

    suspend fun getPackageInfo(name: String, assemblyGuid: String? = null): Result<PluginPackage>

    suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>>

    suspend fun installPackage(
        name: String,
        assemblyGuid: String? = null,
        version: String? = null,
        repositoryUrl: String? = null,
    ): Result<Unit>

    suspend fun cancelPackageInstallation(packageId: String): Result<Unit>

    suspend fun setPluginEnabled(pluginId: String, version: String, enabled: Boolean): Result<Unit>

    suspend fun uninstallPlugin(pluginId: String): Result<Unit>

    suspend fun getRepositories(): Result<List<PluginRepository>>

    suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit>

    /**
     * Resolves a plugin's configuration page in one call: page lookup by
     * plugin id, then the page's HTML. Null when the plugin exposes no
     * configuration page.
     */
    suspend fun getPluginConfigPage(pluginId: String): Result<PluginConfigPageContent?>

    /**
     * WebView bridge session: the server address, user id, and access token
     * the plugin bridge script is parameterized with, plus the engine's
     * OkHttpClient for same-origin request interception.
     */
    val pluginWebViewSession: PluginWebViewSession
}
