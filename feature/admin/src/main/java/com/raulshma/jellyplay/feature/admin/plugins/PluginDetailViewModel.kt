package com.raulshma.jellyplay.feature.admin.plugins

import android.util.Log
import com.raulshma.jellyplay.core.model.PluginConfigPage
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginStatus
import com.raulshma.jellyplay.core.model.PluginVersionInfo
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

data class PluginDetailState(
    val isLoading: Boolean = true,
    val isLoadingVersions: Boolean = false,
    val error: String? = null,
    val plugin: PluginInfo? = null,
    val pluginPackage: PluginPackage? = null,
    val hasConfigPage: Boolean = false,
    val configPageName: String? = null,
    val isToggling: Boolean = false,
    val isUninstalling: Boolean = false,
    val installingVersion: String? = null,
    /** Optimistic enable state set immediately while toggleEnabled() is in flight
     *  (mirrors jellyfin-web's isEnabledOverride). Null = no override. */
    val isEnabledOverride: Boolean? = null,
)

@HiltViewModel
class PluginDetailViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(PluginDetailState())
    val state: PluginDetailState get() = _state.value

    private var _pluginId: String = ""

    fun initialize(pluginId: String, pluginName: String) {
        if (_pluginId == pluginId) return
        _pluginId = pluginId
        loadPlugin(pluginId, pluginName)
    }

    private fun loadPlugin(pluginId: String, pluginName: String) {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null, isEnabledOverride = null)
            adminRepository.getInstalledPlugins().onSuccess { plugins ->
                val plugin = plugins.find { it.id == pluginId }
                if (plugin != null) {
                    _state.value = _state.value.copy(
                        plugin = plugin,
                        isLoading = false,
                    )
                    loadPackageInfoAsync(plugin.name, plugin.id)
                    checkConfigPage(pluginId)
                } else {
                    _state.value = _state.value.copy(
                        plugin = PluginInfo(id = pluginId, name = pluginName),
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                Log.e("PluginDetail", "Failed to load plugin", e)
                _state.value = _state.value.copy(
                    error = e.message,
                    plugin = PluginInfo(id = pluginId, name = pluginName),
                    isLoading = false,
                )
            }
        }
    }

    private fun loadPackageInfoAsync(name: String, assemblyGuid: String) {
        launch {
            _state.value = _state.value.copy(isLoadingVersions = true)
            adminRepository.getPackageInfo(name, assemblyGuid).onSuccess { pkg ->
                _state.value = _state.value.copy(
                    pluginPackage = pkg,
                    isLoadingVersions = false,
                )
            }.onFailure { e ->
                Log.e("PluginDetail", "Failed to load package info", e)
                _state.value = _state.value.copy(isLoadingVersions = false)
            }
        }
    }

    private fun checkConfigPage(pluginId: String) {
        launch {
            adminRepository.getPluginConfigPage(pluginId).onSuccess { page ->
                if (page != null) {
                    _state.value = _state.value.copy(
                        hasConfigPage = true,
                        configPageName = page.name,
                    )
                }
            }.onFailure { e ->
                Log.e("PluginDetail", "Failed to check config pages", e)
            }
        }
    }

    fun toggleEnabled() {
        val plugin = _state.value.plugin ?: return
        val isEnabled = plugin.status == PluginStatus.ACTIVE || plugin.status == PluginStatus.RESTART
        // Optimistically flip the UI immediately (jellyfin-web isEnabledOverride).
        _state.value = _state.value.copy(isToggling = true, isEnabledOverride = !isEnabled)
        launch {
            val result = if (isEnabled) {
                adminRepository.setPluginEnabled(plugin.id, plugin.version, enabled = false)
            } else {
                adminRepository.setPluginEnabled(plugin.id, plugin.version, enabled = true)
            }
            result.onSuccess {
                delay(500)
                loadPlugin(plugin.id, plugin.name)
            }.onFailure { e ->
                Log.e("PluginDetail", "Failed to toggle plugin", e)
                _state.value = _state.value.copy(error = e.message)
            }
            _state.value = _state.value.copy(isToggling = false, isEnabledOverride = null)
        }
    }

    fun uninstall(onComplete: () -> Unit) {
        val plugin = _state.value.plugin ?: return
        _state.value = _state.value.copy(isUninstalling = true)
        launch {
            adminRepository.uninstallPlugin(plugin.id).onSuccess {
                delay(500)
                onComplete()
            }.onFailure { e ->
                Log.e("PluginDetail", "Failed to uninstall plugin", e)
                _state.value = _state.value.copy(
                    error = e.message,
                    isUninstalling = false,
                )
            }
        }
    }

    fun installVersion(version: PluginVersionInfo) {
        val pkg = _state.value.pluginPackage ?: return
        _state.value = _state.value.copy(installingVersion = version.version)
        launch {
            adminRepository.installPackage(
                name = pkg.name,
                assemblyGuid = pkg.guid,
                version = version.version,
                repositoryUrl = version.repositoryUrl,
            ).onSuccess {
                delay(1000)
                val plugin = _state.value.plugin
                if (plugin != null) {
                    loadPlugin(plugin.id, plugin.name)
                }
            }.onFailure { e ->
                Log.e("PluginDetail", "Failed to install version", e)
                _state.value = _state.value.copy(error = e.message)
            }
            _state.value = _state.value.copy(installingVersion = null)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
