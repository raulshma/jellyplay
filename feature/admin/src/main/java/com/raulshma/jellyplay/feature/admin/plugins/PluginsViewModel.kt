package com.raulshma.jellyplay.feature.admin.plugins

import android.util.Log
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class PluginsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val selectedTabIndex: Int = 0,
    val installedPlugins: List<PluginInfo> = emptyList(),
    val availablePackages: List<PluginPackage> = emptyList(),
    val repositories: List<PluginRepository> = emptyList(),
    val activeInstallations: List<PluginInstallationInfo> = emptyList(),
    val catalogSearchQuery: String = "",
    val isCatalogLoading: Boolean = false,
    val isReposLoading: Boolean = false,
) {
    val filteredPackages: List<PluginPackage>
        get() {
            if (catalogSearchQuery.isBlank()) return availablePackages
            val query = catalogSearchQuery.lowercase()
            return availablePackages.filter { pkg ->
                pkg.name.lowercase().contains(query) ||
                    pkg.description.lowercase().contains(query) ||
                    pkg.category.lowercase().contains(query) ||
                    pkg.owner.lowercase().contains(query)
            }
        }
}

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : JellyPlayViewModel() {

    private val _state = composeState(PluginsState())
    val state: PluginsState get() = _state.value

    private val hasActiveInstalls = MutableStateFlow(false)

    init {
        loadInstalledPlugins()
        startInstallationPolling()
    }

    fun loadInstalledPlugins() {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            fetchInstalledPlugins()
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun refresh() {
        launch {
            _state.value = _state.value.copy(isRefreshing = true)
            fetchInstalledPlugins()
            when (_state.value.selectedTabIndex) {
                1 -> fetchCatalog()
                2 -> fetchRepositories()
            }
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    private fun fetchInstalledPlugins() {
        launch {
            apiClient.getInstalledPlugins().onSuccess { plugins ->
                _state.value = _state.value.copy(installedPlugins = plugins.sortedBy { it.name.lowercase() })
            }.onFailure { e ->
                Log.e("Plugins", "Failed to fetch plugins", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun loadCatalog() {
        if (_state.value.availablePackages.isNotEmpty()) return
        fetchCatalog()
    }

    private fun fetchCatalog() {
        launch {
            _state.value = _state.value.copy(isCatalogLoading = true)
            apiClient.getAvailablePackages().onSuccess { packages ->
                _state.value = _state.value.copy(
                    availablePackages = packages.sortedBy { it.name.lowercase() },
                    isCatalogLoading = false,
                )
            }.onFailure { e ->
                Log.e("Plugins", "Failed to fetch catalog", e)
                _state.value = _state.value.copy(
                    error = e.message,
                    isCatalogLoading = false,
                )
            }
        }
    }

    fun loadRepositories() {
        if (_state.value.repositories.isNotEmpty()) return
        fetchRepositories()
    }

    private fun fetchRepositories() {
        launch {
            _state.value = _state.value.copy(isReposLoading = true)
            apiClient.getRepositories().onSuccess { repos ->
                _state.value = _state.value.copy(
                    repositories = repos,
                    isReposLoading = false,
                )
            }.onFailure { e ->
                Log.e("Plugins", "Failed to fetch repositories", e)
                _state.value = _state.value.copy(
                    error = e.message,
                    isReposLoading = false,
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTabIndex = index)
        when (index) {
            1 -> loadCatalog()
            2 -> loadRepositories()
        }
    }

    fun enablePlugin(plugin: PluginInfo) {
        launch {
            apiClient.enablePlugin(plugin.id, plugin.version)
            delay(500)
            fetchInstalledPlugins()
        }
    }

    fun disablePlugin(plugin: PluginInfo) {
        launch {
            apiClient.disablePlugin(plugin.id, plugin.version)
            delay(500)
            fetchInstalledPlugins()
        }
    }

    fun uninstallPlugin(pluginId: String) {
        launch {
            apiClient.uninstallPlugin(pluginId)
            delay(500)
            fetchInstalledPlugins()
        }
    }

    fun installPackage(
        name: String,
        assemblyGuid: String? = null,
        version: String? = null,
        repositoryUrl: String? = null,
    ) {
        launch {
            apiClient.installPackage(
                name = name,
                assemblyGuid = assemblyGuid,
                version = version,
                repositoryUrl = repositoryUrl,
            ).onSuccess {
                hasActiveInstalls.value = true
                delay(1000)
                fetchActiveInstallations()
            }.onFailure { e ->
                Log.e("Plugins", "Failed to install package", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun cancelInstallation(packageId: String) {
        launch {
            apiClient.cancelPackageInstallation(packageId)
            delay(500)
            fetchActiveInstallations()
        }
    }

    private fun fetchActiveInstallations() {
        launch {
            apiClient.getPackageInstallations().onSuccess { installations ->
                _state.value = _state.value.copy(activeInstallations = installations)
                hasActiveInstalls.value = installations.isNotEmpty()
                if (installations.isEmpty()) {
                    fetchInstalledPlugins()
                }
            }
        }
    }

    private fun startInstallationPolling() {
        launch {
            hasActiveInstalls.collect { active ->
                while (active) {
                    delay(3000)
                    fetchActiveInstallations()
                }
            }
        }
    }

    fun addRepository(name: String, url: String) {
        launch {
            val current = _state.value.repositories.toMutableList()
            current.add(PluginRepository(name = name, url = url, isEnabled = true))
            apiClient.setRepositories(current).onSuccess {
                _state.value = _state.value.copy(repositories = current)
            }.onFailure { e ->
                Log.e("Plugins", "Failed to add repository", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun removeRepository(index: Int) {
        launch {
            val current = _state.value.repositories.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                apiClient.setRepositories(current).onSuccess {
                    _state.value = _state.value.copy(repositories = current)
                }.onFailure { e ->
                    Log.e("Plugins", "Failed to remove repository", e)
                    _state.value = _state.value.copy(error = e.message)
                }
            }
        }
    }

    fun toggleRepository(index: Int, enabled: Boolean) {
        launch {
            val current = _state.value.repositories.toMutableList()
            if (index in current.indices) {
                current[index] = current[index].copy(isEnabled = enabled)
                apiClient.setRepositories(current).onSuccess {
                    _state.value = _state.value.copy(repositories = current)
                }.onFailure { e ->
                    Log.e("Plugins", "Failed to toggle repository", e)
                    _state.value = _state.value.copy(error = e.message)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(catalogSearchQuery = query)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
