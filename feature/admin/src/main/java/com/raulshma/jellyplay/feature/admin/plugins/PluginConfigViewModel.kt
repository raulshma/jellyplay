package com.raulshma.jellyplay.feature.admin.plugins

import android.content.Context
import android.util.Log
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

data class PluginConfigState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val configPageHtml: String? = null,
    val configPageName: String? = null,
    val serverAddress: String = "",
    val accessToken: String = "",
    /** Parameterized bridge script (window.ApiClient/Dashboard globals) injected into the WebView. */
    val bridgeScript: String = "",
)

@HiltViewModel
class PluginConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(PluginConfigState())
    val state: PluginConfigState get() = _state.value

    private var _pluginId: String = ""

    fun initialize(pluginId: String, pluginName: String) {
        if (_pluginId == pluginId) return
        _pluginId = pluginId
        loadConfig(pluginId)
    }

    /** The OkHttpClient used by [PluginConfigScreen] to auth same-origin WebView requests. */
    val okHttpClient: OkHttpClient get() = adminRepository.pluginWebViewSession.okHttpClient

    private fun loadConfig(pluginId: String) {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            prepareBridgeScript()
            adminRepository.getPluginConfigPage(pluginId).onSuccess { page ->
                if (page != null) {
                    _state.value = _state.value.copy(configPageName = page.name, configPageHtml = page.html)
                } else {
                    _state.value = _state.value.copy(error = context.getString(R.string.admin_no_config_page))
                }
            }.onFailure { e ->
                Log.e("PluginConfig", "Failed to load config page", e)
                _state.value = _state.value.copy(error = e.message)
            }
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Builds the parameterized `pluginBridge.js` once per load, substituting the
     * current server address, user id, and access token. The token is injected
     * so write requests (config save) self-authenticate; the host additionally
     * authenticates same-origin GETs via shouldInterceptRequest.
     */
    private suspend fun prepareBridgeScript() {
        val session = adminRepository.pluginWebViewSession
        val rawJs = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.assets.open("pluginBridge.js").bufferedReader().use { it.readText() }
            }.getOrElse {
                Log.e("PluginConfig", "pluginBridge.js asset not found", it)
                ""
            }
        }
        val script = buildBridgeScript(rawJs, session.serverAddress, session.userId, session.accessToken)
        _state.value = _state.value.copy(
            serverAddress = session.serverAddress,
            accessToken = session.accessToken,
            bridgeScript = script,
        )
    }

    fun refresh() {
        loadConfig(_pluginId)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
