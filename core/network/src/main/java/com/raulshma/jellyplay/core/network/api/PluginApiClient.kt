package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.PluginConfigPage
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository

interface PluginApiClient {
    suspend fun getInstalledPlugins(): Result<List<PluginInfo>>
    suspend fun enablePlugin(pluginId: String, version: String): Result<Unit>
    suspend fun disablePlugin(pluginId: String, version: String): Result<Unit>
    suspend fun uninstallPlugin(pluginId: String): Result<Unit>
    suspend fun getAvailablePackages(): Result<List<PluginPackage>>
    suspend fun getPackageInfo(name: String, assemblyGuid: String? = null): Result<PluginPackage>
    suspend fun installPackage(
        name: String,
        assemblyGuid: String? = null,
        version: String? = null,
        repositoryUrl: String? = null,
    ): Result<Unit>
    suspend fun cancelPackageInstallation(packageId: String): Result<Unit>
    suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>>
    suspend fun getRepositories(): Result<List<PluginRepository>>
    suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit>
    suspend fun getPluginConfiguration(pluginId: String): Result<String>
    suspend fun updatePluginConfiguration(pluginId: String, jsonBody: String): Result<Unit>
    suspend fun getConfigurationPages(): Result<List<PluginConfigPage>>
    suspend fun getDashboardConfigurationPage(name: String): Result<String>
}
