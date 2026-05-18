package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeerrSettingsViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val seerrPreferencesStore: SeerrPreferencesStore,
) : ViewModel() {

    val preferences = seerrPreferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeerrPreferences())

    var serverUrl by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
        private set
    var connectionStatus by mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle)
        private set
    var isTesting by mutableStateOf(false)
        private set
    var isChecked by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val prefs = seerrPreferencesStore.preferences.first()
            serverUrl = prefs.serverUrl
            apiKey = prefs.apiKey
            isChecked = prefs.serverUrl.isNotBlank()
            if (prefs.serverUrl.isNotBlank() && prefs.apiKey.isNotBlank()) {
                connectionStatus = ConnectionStatus.Connected("", true)
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        serverUrl = url
        if (connectionStatus is ConnectionStatus.Connected) {
            connectionStatus = ConnectionStatus.Idle
        }
    }

    fun onApiKeyChanged(key: String) {
        apiKey = key
        if (connectionStatus is ConnectionStatus.Connected) {
            connectionStatus = ConnectionStatus.Idle
        }
    }

    fun testConnection() {
        if (serverUrl.isBlank() || apiKey.isBlank()) {
            connectionStatus = ConnectionStatus.Error("Server URL and API key are required")
            return
        }
        viewModelScope.launch {
            isTesting = true
            try {
                // Save credentials first so testConnection can use them
                seerrPreferencesStore.setServerUrl(serverUrl)
                seerrPreferencesStore.setApiKey(apiKey)

                seerrRepository.testConnection()
                    .onSuccess { response ->
                        connectionStatus = ConnectionStatus.Connected(response.version, true)
                    }
                    .onFailure { error ->
                        connectionStatus = ConnectionStatus.Error(
                            error.message ?: "Connection failed"
                        )
                    }
            } catch (e: Exception) {
                connectionStatus = ConnectionStatus.Error(
                    e.message ?: "Unexpected error occurred"
                )
            }
            isTesting = false
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setEnabled(enabled) }
    }

    fun setSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setSearchEnabled(enabled) }
    }

    fun setRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setRecommendationsEnabled(enabled) }
    }

    fun setDiscoverEnabled(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverEnabled(enabled) }
    }

    fun setDiscoverTrending(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverTrending(enabled) }
    }

    fun setDiscoverPopularMovies(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverPopularMovies(enabled) }
    }

    fun setDiscoverPopularTv(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverPopularTv(enabled) }
    }

    fun setDiscoverUpcomingMovies(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverUpcomingMovies(enabled) }
    }

    fun setDiscoverUpcomingTv(enabled: Boolean) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverUpcomingTv(enabled) }
    }

    fun setStreamingRegion(region: String) {
        viewModelScope.launch { seerrPreferencesStore.setStreamingRegion(region) }
    }

    fun setDiscoverRegion(region: String) {
        viewModelScope.launch { seerrPreferencesStore.setDiscoverRegion(region) }
    }

    fun disconnect() {
        viewModelScope.launch {
            seerrPreferencesStore.disconnect()
            serverUrl = ""
            apiKey = ""
            connectionStatus = ConnectionStatus.Idle
        }
    }

    sealed class ConnectionStatus {
        data object Idle : ConnectionStatus()
        data class Connected(val version: String, val isValid: Boolean) : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }
}
