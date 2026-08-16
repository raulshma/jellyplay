package com.raulshma.jellyplay.feature.admin.plugins

import android.util.Log
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.data.repository.AdminRepository
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
    /** Catalog status filter, mirroring jellyfin-web's status chips. */
    val catalogStatusFilter: PluginStatusFilter = PluginStatusFilter.INSTALLED,
    /** Catalog category filter, mirroring jellyfin-web's category chips. */
    val catalogCategoryFilter: PluginCategory = PluginCategory.ALL,
) {
    /** GUIDs of installed plugins, used by the Available/Installed status filter. */
    val installedGuids: Set<String> get() = installedPlugins.map { it.id }.toSet()

    val filteredPackages: List<PluginPackage>
        get() {
            var result: Iterable<PluginPackage> = availablePackages

            // Status filter: All / Installed / Available (jellyfin-web default = Installed).
            result = when (catalogStatusFilter) {
                PluginStatusFilter.ALL -> result
                PluginStatusFilter.INSTALLED -> result.filter { it.guid in installedGuids }
                PluginStatusFilter.AVAILABLE -> result.filter { it.guid !in installedGuids }
            }

            // Category filter.
            if (catalogCategoryFilter != PluginCategory.ALL) {
                result = result.filter {
                    PluginCategory.fromServer(it.category) == catalogCategoryFilter
                }
            }

            // Free-text search over name/description/category/owner.
            if (catalogSearchQuery.isNotBlank()) {
                val query = catalogSearchQuery.lowercase()
                result = result.filter { pkg ->
                    pkg.name.lowercase().contains(query) ||
                        pkg.description.lowercase().contains(query) ||
                        pkg.category.lowercase().contains(query) ||
                        pkg.owner.lowercase().contains(query)
                }
            }
            return result.toList()
        }
}

/** Catalog status filter options (mirror `pluginStatusOption.ts`). */
enum class PluginStatusFilter(val displayName: String) {
    ALL("All"),
    INSTALLED("Installed"),
    AVAILABLE("Available"),
}

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
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
            adminRepository.getInstalledPlugins().onSuccess { plugins ->
                _state.value = _state.value.copy(installedPlugins = plugins.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
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
            adminRepository.getAvailablePackages().onSuccess { packages ->
                _state.value = _state.value.copy(
                    availablePackages = packages.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
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
            adminRepository.getRepositories().onSuccess { repos ->
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
            adminRepository.setPluginEnabled(plugin.id, plugin.version, enabled = true)
            delay(500)
            fetchInstalledPlugins()
        }
    }

    fun disablePlugin(plugin: PluginInfo) {
        launch {
            adminRepository.setPluginEnabled(plugin.id, plugin.version, enabled = false)
            delay(500)
            fetchInstalledPlugins()
        }
    }

    fun uninstallPlugin(pluginId: String) {
        launch {
            adminRepository.uninstallPlugin(pluginId)
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
            adminRepository.installPackage(
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
            adminRepository.cancelPackageInstallation(packageId)
            delay(500)
            fetchActiveInstallations()
        }
    }

    private fun fetchActiveInstallations() {
        launch {
            adminRepository.getPackageInstallations().onSuccess { installations ->
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
                    delay(5000)
                    fetchActiveInstallations()
                }
            }
        }
    }

    fun addRepository(name: String, url: String) {
        launch {
            val current = _state.value.repositories.toMutableList()
            current.add(PluginRepository(name = name, url = url, isEnabled = true))
            adminRepository.setRepositories(current).onSuccess {
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
                adminRepository.setRepositories(current).onSuccess {
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
                adminRepository.setRepositories(current).onSuccess {
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

    fun updateStatusFilter(filter: PluginStatusFilter) {
        _state.value = _state.value.copy(catalogStatusFilter = filter)
    }

    fun updateCategoryFilter(category: PluginCategory) {
        _state.value = _state.value.copy(catalogCategoryFilter = category)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
