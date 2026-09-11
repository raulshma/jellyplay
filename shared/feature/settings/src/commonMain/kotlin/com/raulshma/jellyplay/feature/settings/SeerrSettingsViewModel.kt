package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.MutableComposeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class SeerrSettingsViewModel(
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

    /**
     * Connection status via the shared [ConnectionProbe] board (single key —
     * one connection): the machine owns single-flight RESTART, the
     * cancellation-never-lands-as-Error rule, and the localized fallback texts
     * that replaced this VM's former English literals.
     *
     * Behavior changes vs the former hand-rolled status:
     *  - `ConnectionStatus.isValid` died (every construction passed `true`
     *    and no reader ever consumed it).
     *  - a refused request (blank field) now SUPERSEDES an in-flight test —
     *    the old flow let the in-flight result overwrite the fresh
     *    validation error.
     *  - superseding a test no longer flickers the spinner off/on between
     *    probes (the screen's Testing flag derives from the status).
     *  - an unexpected crash degrades to the localized UnexpectedError
     *    fallback (same user-visible text as the old catch-all, now declared
     *    once in the machine).
     */
    private val connectionProbe = ConnectionProbe(
        scope = scope,
        keyOf = { _: SeerrProbeRequest -> Unit },
        refused = { it.refusal() },
        action = ::probeConnection,
    )

    val connectionStatus: StateFlow<ConnectionProbe.Status<SeerrConnectionDetails>> =
        connectionProbe.status
            .map { it[Unit] ?: ConnectionProbe.Status.Idle }
            .stateIn(scope, SharingStarted.Eagerly, ConnectionProbe.Status.Idle)

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
                    connectionProbe.restoreConnected(Unit, SeerrConnectionDetails(version = ""))
                }
            }
        }
    }

    /**
     * Any edit to the connection's inputs invalidates a standing Connected
     * verdict — the probe board resets to idle until the user re-tests.
     * Reads the board's authoritative status, not the [connectionStatus]
     * mirror (a stateIn copy can lag the board write by a dispatch).
     */
    private fun resetProbeIfConnected() {
        if (connectionProbe.statusOf(Unit) is ConnectionProbe.Status.Connected) {
            connectionProbe.reset(Unit)
        }
    }

    /** Writes a connection input, then re-arms the probe board ([resetProbeIfConnected]). */
    private fun <T> MutableComposeState<T>.setInvalidating(value: T) {
        this.value = value
        resetProbeIfConnected()
    }

    fun onServerUrlChanged(url: String) {
        _serverUrl.setInvalidating(url)
    }

    fun onApiKeyChanged(key: String) {
        _apiKey.setInvalidating(key)
    }

    fun onUsernameChanged(value: String) {
        _username.setInvalidating(value)
    }

    fun onEmailChanged(value: String) {
        _email.setInvalidating(value)
    }

    fun onPasswordChanged(value: String) {
        _password.setInvalidating(value)
    }

    fun onAuthMethodChanged(method: SeerrAuthMethod) {
        _authMethod.setInvalidating(method)
    }

    /**
     * Builds the probe request from the live form fields and hands it to the
     * board. Blank-field refusals settle synchronously inside
     * [ConnectionProbe.probe] (no Testing frame, no repository call).
     */
    fun testConnection() {
        connectionProbe.probe(
            when (authMethod) {
                SeerrAuthMethod.API_KEY -> SeerrProbeRequest.ApiKey(serverUrl, apiKey)
                SeerrAuthMethod.JELLYFIN -> SeerrProbeRequest.Jellyfin(serverUrl, username, password)
                SeerrAuthMethod.LOCAL -> SeerrProbeRequest.Local(serverUrl, email, password)
            }
        )
    }

    /**
     * The probe action per request variant. Declared persistence order is
     * preserved from the former hand-rolled paths: the server URL is written
     * up-front for every variant (the repository resolves it from saved
     * preferences when making the call), the API-key path persists its
     * credential before the call, and the login paths defer credential writes
     * to success so failed attempts don't leave bad credentials saved.
     */
    private suspend fun probeConnection(request: SeerrProbeRequest): ConnectionProbe.Outcome<SeerrConnectionDetails> {
        seerrPreferencesStore.setServerUrl(request.serverUrl)
        return when (request) {
            is SeerrProbeRequest.ApiKey -> {
                seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.API_KEY)
                secureCredentialsStore.setApiKey(request.apiKey)
                seerrRepository.testApiKeyConnection().fold(
                    onSuccess = { ConnectionProbe.Outcome.Reachable(SeerrConnectionDetails(it.version)) },
                    onFailure = { ConnectionProbe.unreachable(it.message) },
                )
            }
            is SeerrProbeRequest.Jellyfin -> probeLogin(
                method = SeerrAuthMethod.JELLYFIN,
                login = { seerrRepository.loginJellyfin(request.username, request.password) },
                persistCredentials = {
                    seerrPreferencesStore.setUsername(request.username)
                    secureCredentialsStore.setPassword(request.password)
                },
            )
            is SeerrProbeRequest.Local -> probeLogin(
                method = SeerrAuthMethod.LOCAL,
                login = { seerrRepository.loginLocal(request.email, request.password) },
                persistCredentials = {
                    seerrPreferencesStore.setEmail(request.email)
                    secureCredentialsStore.setPassword(request.password)
                },
            )
        }
    }

    /**
     * The shared login-path fold: on success, persist the auth method, then
     * the variant's credentials ([persistCredentials], declared order — after
     * the method write, before the outcome lands), then report Reachable;
     * on failure report unreachable with the LoginFailed fallback. Login
     * paths defer credential writes to success so failed attempts don't
     * leave bad credentials saved.
     */
    private suspend fun probeLogin(
        method: SeerrAuthMethod,
        login: suspend () -> Result<SeerrStatusResponse>,
        persistCredentials: suspend () -> Unit,
    ): ConnectionProbe.Outcome<SeerrConnectionDetails> =
        login().fold(
            onSuccess = { response ->
                seerrPreferencesStore.setAuthMethod(method)
                persistCredentials()
                ConnectionProbe.Outcome.Reachable(SeerrConnectionDetails(response.version))
            },
            onFailure = { ConnectionProbe.unreachable(it.message, ConnectionProbe.FallbackText.LoginFailed) },
        )

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
            connectionProbe.reset(Unit)
        }
    }

    /**
     * Connected details: the server's reported version ("" when restored from
     * the store at init without a probe).
     */
    data class SeerrConnectionDetails(val version: String)

    /**
     * One connection-test request, built from the live form fields at tap
     * time; the variant selects the probe path. The shared pre-flight lives
     * here once (a blank server URL refuses every variant), and each variant
     * declares only its own blank-field refusal — checked synchronously by
     * the board before any network call.
     */
    private sealed class SeerrProbeRequest {
        abstract val serverUrl: String

        fun refusal(): ConnectionProbe.FallbackText? =
            if (serverUrl.isBlank()) {
                ConnectionProbe.FallbackText.ServerUrlRequired
            } else {
                fieldRefusal()
            }

        /** The variant's own blank-field refusal, after the shared server-url check. */
        protected abstract fun fieldRefusal(): ConnectionProbe.FallbackText?

        data class ApiKey(override val serverUrl: String, val apiKey: String) : SeerrProbeRequest() {
            override fun fieldRefusal(): ConnectionProbe.FallbackText? =
                if (apiKey.isBlank()) ConnectionProbe.FallbackText.ApiKeyRequired else null
        }

        data class Jellyfin(
            override val serverUrl: String,
            val username: String,
            val password: String,
        ) : SeerrProbeRequest() {
            override fun fieldRefusal(): ConnectionProbe.FallbackText? =
                if (username.isBlank() || password.isBlank()) {
                    ConnectionProbe.FallbackText.UsernamePasswordRequired
                } else {
                    null
                }
        }

        data class Local(
            override val serverUrl: String,
            val email: String,
            val password: String,
        ) : SeerrProbeRequest() {
            override fun fieldRefusal(): ConnectionProbe.FallbackText? =
                if (email.isBlank() || password.isBlank()) {
                    ConnectionProbe.FallbackText.EmailPasswordRequired
                } else {
                    null
                }
        }
    }
}
