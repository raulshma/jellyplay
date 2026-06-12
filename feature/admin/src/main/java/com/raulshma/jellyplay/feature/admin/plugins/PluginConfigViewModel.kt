package com.raulshma.jellyplay.feature.admin.plugins

import android.util.Log
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class PluginConfigState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val configPageHtml: String? = null,
    val configPageName: String? = null,
    val serverAddress: String = "",
    val accessToken: String = "",
)

@HiltViewModel
class PluginConfigViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : JellyPlayViewModel() {

    private val _state = composeState(PluginConfigState())
    val state: PluginConfigState get() = _state.value

    private var _pluginId: String = ""

    fun initialize(pluginId: String, pluginName: String) {
        if (_pluginId == pluginId) return
        _pluginId = pluginId
        loadConfig(pluginId)
    }

    private fun loadConfig(pluginId: String) {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            apiClient.getConfigurationPages().onSuccess { pages ->
                val configPage = pages.find { it.pluginId == pluginId }
                if (configPage != null) {
                    _state.value = _state.value.copy(configPageName = configPage.name)
                    apiClient.getDashboardConfigurationPage(configPage.name).onSuccess { html ->
                        _state.value = _state.value.copy(configPageHtml = html)
                    }.onFailure { e ->
                        Log.e("PluginConfig", "Failed to load config page HTML", e)
                        _state.value = _state.value.copy(error = e.message)
                    }
                } else {
                    _state.value = _state.value.copy(error = "No configuration page found")
                }
            }.onFailure { e ->
                Log.e("PluginConfig", "Failed to load config pages", e)
                _state.value = _state.value.copy(error = e.message)
            }
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun refresh() {
        loadConfig(_pluginId)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
