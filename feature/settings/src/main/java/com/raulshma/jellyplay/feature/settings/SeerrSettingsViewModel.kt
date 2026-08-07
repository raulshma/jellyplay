package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SeerrSettingsViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val secureCredentialsStore: SeerrSecureCredentialsStore,
) : JellyPlayViewModel() {

    val preferences = seerrPreferencesStore.preferences

    private val _serverUrl = composeState("")
    val serverUrl: String get() = _serverUrl.value

    private val _apiKey = composeState("")
    val apiKey: String get() = _apiKey.value

    private val _username = composeState("")
    val username: String get() = _username.value

    private val _email = composeState("")
    val email: String get() = _email.value

    private val _password = composeState("")
    val password: String get() = _password.value

    private val _authMethod = composeState(SeerrAuthMethod.API_KEY)
    val authMethod: SeerrAuthMethod get() = _authMethod.value

    private val _connectionStatus = composeState<ConnectionStatus>(ConnectionStatus.Idle)
    val connectionStatus: ConnectionStatus get() = _connectionStatus.value

    private val _isTesting = composeState(false)
    val isTesting: Boolean get() = _isTesting.value

    // Tracks the in-flight connection test so a rapid second tap cancels the
    // first instead of letting two logins race on _connectionStatus / _isTesting.
    private var testJob: Job? = null

    private fun launchTest(block: suspend CoroutineScope.() -> Unit) {
        testJob?.cancel()
        testJob = launch {
            _isTesting.value = true
            try {
                block()
            } finally {
                // Reset in `finally` so cancellation (a rapid second tap via
                // [launchTest], or VM clearing) still clears the spinner. A plain
                // statement after the body would be skipped on the
                // CancellationException thrown at the next suspension point,
                // leaving the UI stuck on "testing".
                _isTesting.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel the in-flight test so its `finally` resets _isTesting before
        // the VM goes away. viewModelScope cancellation would eventually do this,
        // but matching [ArrSettingsViewModel.onCleared] keeps the two test-tracked
        // VMs consistent and makes the cancellation deterministic.
        testJob?.cancel()
    }

    init {
        launch {
            val prefs = seerrPreferencesStore.preferences.first()
            // EncryptedSharedPreferences / Keystore-backed reads are crypto +
            // disk work; push them off the Main dispatcher.
            val (apiKey, password, sessionCookie) = withContext(Dispatchers.IO) {
                Triple(
                    secureCredentialsStore.getApiKey(),
                    secureCredentialsStore.getPassword(),
                    if (prefs.serverUrl.isNotBlank()) secureCredentialsStore.getSessionCookie() else "",
                )
            }
            _serverUrl.value = prefs.serverUrl
            _authMethod.value = prefs.authMethod
            _username.value = prefs.username
            _email.value = prefs.email
            _apiKey.value = apiKey
            _password.value = password
            if (prefs.serverUrl.isNotBlank()) {
                val hasCreds = when (prefs.authMethod) {
                    SeerrAuthMethod.API_KEY -> apiKey.isNotBlank()
                    SeerrAuthMethod.JELLYFIN,
                    SeerrAuthMethod.LOCAL -> sessionCookie.isNotBlank()
                }
                if (hasCreds) {
                    _connectionStatus.value = ConnectionStatus.Connected("", true)
                }
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

    fun onUsernameChanged(value: String) {
        _username.value = value
        if (connectionStatus is ConnectionStatus.Connected) {
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    fun onEmailChanged(value: String) {
        _email.value = value
        if (connectionStatus is ConnectionStatus.Connected) {
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
        if (connectionStatus is ConnectionStatus.Connected) {
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    fun onAuthMethodChanged(method: SeerrAuthMethod) {
        _authMethod.value = method
        if (connectionStatus is ConnectionStatus.Connected) {
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    fun testConnection() {
        if (serverUrl.isBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("Server URL is required")
            return
        }
        when (authMethod) {
            SeerrAuthMethod.API_KEY -> testApiKeyConnection()
            SeerrAuthMethod.JELLYFIN -> loginJellyfin()
            SeerrAuthMethod.LOCAL -> loginLocal()
        }
    }

    private fun testApiKeyConnection() {
        if (apiKey.isBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("API key is required")
            return
        }
        launchTest {
            try {
                seerrPreferencesStore.setServerUrl(serverUrl)
                seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.API_KEY)
                secureCredentialsStore.setApiKey(apiKey)

                seerrRepository.testApiKeyConnection()
                    .onSuccess { response ->
                        _connectionStatus.value = ConnectionStatus.Connected(response.version, true)
                    }
                    .onFailure { error ->
                        _connectionStatus.value = ConnectionStatus.Error(
                            error.message ?: "Connection failed"
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(
                    e.message ?: "Unexpected error occurred"
                )
            }
        }
    }

    private fun loginJellyfin() {
        if (username.isBlank() || password.isBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("Username and password are required")
            return
        }
        launchTest {
            try {
                // Persist the server URL up-front: the repository resolves it from
                // the saved preferences when making the login request, so it must be
                // written before the call. Credentials stay deferred to success so
                // failed attempts don't leave bad credentials saved.
                seerrPreferencesStore.setServerUrl(serverUrl)
                seerrRepository.loginJellyfin(username, password)
                    .onSuccess { response ->
                        seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.JELLYFIN)
                        seerrPreferencesStore.setUsername(username)
                        secureCredentialsStore.setPassword(password)
                        _connectionStatus.value = ConnectionStatus.Connected(response.version, true)
                    }
                    .onFailure { error ->
                        _connectionStatus.value = ConnectionStatus.Error(
                            error.message ?: "Login failed"
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(
                    e.message ?: "Unexpected error occurred"
                )
            }
        }
    }

    private fun loginLocal() {
        if (email.isBlank() || password.isBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("Email and password are required")
            return
        }
        launchTest {
            try {
                // Persist the server URL up-front: the repository resolves it from
                // the saved preferences when making the login request, so it must be
                // written before the call. Credentials stay deferred to success so
                // failed attempts don't leave bad credentials saved.
                seerrPreferencesStore.setServerUrl(serverUrl)
                seerrRepository.loginLocal(email, password)
                    .onSuccess { response ->
                        seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.LOCAL)
                        seerrPreferencesStore.setEmail(email)
                        secureCredentialsStore.setPassword(password)
                        _connectionStatus.value = ConnectionStatus.Connected(response.version, true)
                    }
                    .onFailure { error ->
                        _connectionStatus.value = ConnectionStatus.Error(
                            error.message ?: "Login failed"
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(
                    e.message ?: "Unexpected error occurred"
                )
            }
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
            _username.value = ""
            _email.value = ""
            _password.value = ""
            _authMethod.value = SeerrAuthMethod.API_KEY
            _connectionStatus.value = ConnectionStatus.Idle
        }
    }

    sealed class ConnectionStatus {
        data object Idle : ConnectionStatus()
        data class Connected(val version: String, val isValid: Boolean) : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }
}
