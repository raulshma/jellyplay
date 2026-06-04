package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SeerrSettingsViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val seerrPreferencesStore: SeerrPreferencesStore,
) : JellyPlayViewModel() {

    val preferences = seerrPreferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrPreferences())

    private val _serverUrl = composeState("")
    val serverUrl: String get() = _serverUrl.value

    private val _apiKey = composeState("")
    val apiKey: String get() = _apiKey.value

    private val _connectionStatus = composeState<ConnectionStatus>(ConnectionStatus.Idle)
    val connectionStatus: ConnectionStatus get() = _connectionStatus.value

    private val _isTesting = composeState(false)
    val isTesting: Boolean get() = _isTesting.value

    private val _isChecked = composeState(false)
    val isChecked: Boolean get() = _isChecked.value

    init {
        launch {
            val prefs = seerrPreferencesStore.preferences.first()
            _serverUrl.value = prefs.serverUrl
            _apiKey.value = prefs.apiKey
            _isChecked.value = prefs.serverUrl.isNotBlank()
            if (prefs.serverUrl.isNotBlank() && prefs.apiKey.isNotBlank()) {
                _connectionStatus.value = ConnectionStatus.Connected("", true)
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _serverUrl.value = url
        if (connectionStatus is ConnectionStatus.Connected) {
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    fun onApiKeyChanged(key: String) {
        _apiKey.value = key
        if (connectionStatus is ConnectionStatus.Connected) {
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    fun testConnection() {
        if (serverUrl.isBlank() || apiKey.isBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("Server URL and API key are required")
            return
        }
        launch {
            _isTesting.value = true
            try {
                seerrPreferencesStore.setServerUrl(serverUrl)
                seerrPreferencesStore.setApiKey(apiKey)

                seerrRepository.testConnection()
                    .onSuccess { response ->
                        _connectionStatus.value = ConnectionStatus.Connected(response.version, true)
                    }
                    .onFailure { error ->
                        _connectionStatus.value = ConnectionStatus.Error(
                            error.message ?: "Connection failed"
                        )
                    }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(
                    e.message ?: "Unexpected error occurred"
                )
            }
            _isTesting.value = false
        }
    }

    fun setEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setEnabled(enabled) }
    }

    fun setSearchEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setSearchEnabled(enabled) }
    }

    fun setRecommendationsEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setRecommendationsEnabled(enabled) }
    }

    fun setDiscoverEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverEnabled(enabled) }
    }

    fun setDiscoverTrending(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverTrending(enabled) }
    }

    fun setDiscoverPopularMovies(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverPopularMovies(enabled) }
    }

    fun setDiscoverPopularTv(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverPopularTv(enabled) }
    }

    fun setDiscoverUpcomingMovies(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverUpcomingMovies(enabled) }
    }

    fun setDiscoverUpcomingTv(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverUpcomingTv(enabled) }
    }

    fun setStreamingRegion(region: String) {
        launch { seerrPreferencesStore.setStreamingRegion(region) }
    }

    fun setDiscoverRegion(region: String) {
        launch { seerrPreferencesStore.setDiscoverRegion(region) }
    }

    fun disconnect() {
        launch {
            seerrPreferencesStore.disconnect()
            _serverUrl.value = ""
            _apiKey.value = ""
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    sealed class ConnectionStatus {
        data object Idle : ConnectionStatus()
        data class Connected(val version: String, val isValid: Boolean) : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }
}
