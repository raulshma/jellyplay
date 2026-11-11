package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrAuthenticator
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.network.api.JellyPlaySeerrRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySeerrStatus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.MutableComposeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Failure of one via-server bridge operation (status fetch / Quick Connect
 * link / logout). Mirrors the [ConnectionProbe.Failure] discipline: a
 * [SeerrBridgeFailure.Reported] carries transport/plugin-provided text and
 * renders verbatim; a [SeerrBridgeFailure.Declared] is this pane's own
 * fallback vocabulary, localized at render time by the screen (the
 * `jellyplay_seerr_error_*` resources). Declared here, not folded into the
 * shared probe enum — that stays the three direct-connection integrations'
 * vocabulary.
 */
public sealed interface SeerrBridgeFailure {
    /** Transport/plugin-provided text (e.g. the login route's 401 body error). */
    data class Reported(val message: String) : SeerrBridgeFailure

    /** A declared fallback; the screen resolves it to a localized string. */
    data class Declared(val text: Fallback) : SeerrBridgeFailure

    enum class Fallback {
        /** `seerrStatus` failed with no usable message. */
        StatusUnreachable,
        /** Jellyfin Quick Connect is disabled server-side. */
        QuickConnectDisabled,
        /** QC initiate/poll failed with no usable message. */
        QuickConnectFailed,
        /** The approval window elapsed without the user approving. */
        QuickConnectTimeout,
        /** `seerrLogin` failed with no usable message. */
        LoginFailed,
        /** `seerrLogout` failed with no usable message. */
        LogoutFailed,
    }
}

/**
 * The via-server bridge's operation/pairing lifecycle — ONE machine for the
 * whole "talk to the plugin's Seerr bridge" surface (status fetch, Quick
 * Connect pairing, logout), replacing the four loose flags + `Job` latch
 * whose combinations drifted (`code != null` with no flow live, a stale
 * failure beside a fresh operation, a timed-out pairing stranding its code
 * on screen and blocking the restart). Shape mirrors feature/auth's
 * `QuickConnectUiState` — same domain, one vocabulary.
 *
 * The linked/unlinked truth is deliberately NOT here: that is the server's
 * `bridgeStatus`, an orthogonal concern the machine only re-fetches.
 */
sealed interface SeerrBridgeQuickConnectPhase {
    /** No bridge operation in flight; no failure showing. */
    data object Idle : SeerrBridgeQuickConnectPhase

    /** A status fetch is in flight (the pane's "checking" row). */
    data object StatusFetch : SeerrBridgeQuickConnectPhase

    /** Quick Connect availability check + initiate are in flight (no code yet). */
    data object Starting : SeerrBridgeQuickConnectPhase

    /** The code is on screen and the approval poll is running. */
    data class WaitingForApproval(val code: String) : SeerrBridgeQuickConnectPhase

    /** Approved — the plugin login (finalize) and the status re-fetch are in flight. */
    data object Finalizing : SeerrBridgeQuickConnectPhase

    /** The server-side unlink (logout) is in flight. */
    data object LoggingOut : SeerrBridgeQuickConnectPhase

    /**
     * The last operation failed; [failure] renders in the pane's banner until
     * the next operation transitions out. Restarting the pairing from here is
     * allowed (the start guard's only non-[Idle] admission).
     */
    data class Failed(val failure: SeerrBridgeFailure) : SeerrBridgeQuickConnectPhase
}

class SeerrSettingsViewModel(
    private val seerrAuthenticator: SeerrAuthenticator,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val secureCredentialsStore: SeerrSecureCredentialsStore,
    /**
     * The jellyfin-plugin-jellyplay "via server" seams.
     * Nullable-with-default (the [SettingsViewModel] plugin-dep pattern):
     * this VM is directly constructed in tests; the Koin factory passes the
     * real singles and the screen renders the mode selector only when the
     * probe reports AVAILABLE with the SeerrBridge feature key.
     */
    private val pluginApiClient: JellyPlaySeerrRoutes? = null,
    private val pluginStatusStore: JellyPlayPluginStatusStore? = null,
    /**
     * The per-feature gate seam (probe AND the user's toggle) over the store
     * above. Nullable-with-default; unwired, every bridge path fails CLOSED
     * (production always receives it from Koin; only direct test
     * construction runs without it).
     */
    private val jellyPlayFeatureGate: com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate? = null,
    /** Jellyfin Quick Connect source for the bridge link flow (the plugin authorizes a Jellyfin QC secret, then SSOs into Seerr). */
    private val authRepository: AuthRepository? = null,
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

    // ── via-server bridge mode (jellyfin-plugin-jellyplay Seerr bridge) ──

    /** Companion-plugin availability (UNKNOWN until [refreshJellyPlayPluginStatus] probes). */
    val jellyPlayPluginStatus: StateFlow<JellyPlayPluginStatus> =
        pluginStatusStore?.status ?: MutableStateFlow(JellyPlayPluginStatus.UNAVAILABLE)

    /** The plugin's live feature set; the mode selector gates on [JellyPlayPluginFeatures.SeerrBridge]. */
    val jellyPlayPluginFeatures: StateFlow<Set<String>> =
        pluginStatusStore?.features ?: MutableStateFlow(emptySet())

    /**
     * The per-feature USER toggles (the gate seam's switch states) — the
     * screen ANDs this with the registry above so the mode selector is
     * superseded while the `seerr-bridge` toggle is off.
     */
    val jellyPlayFeatureToggles: StateFlow<Set<String>> =
        jellyPlayFeatureGate?.enabledFeatures
            ?: MutableStateFlow(
                com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate.TOGGLEABLE_FEATURES.toSet(),
            )

    /** The saved connection MODE (false = direct connection, the pre-bridge default). */
    private val _useServerBridge = composeState(false)
    val useServerBridge: Boolean get() = _useServerBridge.value

    /** Set once the user (or a restore) expresses a mode choice this session; the init seed then defers. */
    private var userModeChanged = false

    /** The plugin's last-reported Seerr bridge status (null before the first successful fetch). */
    private val _bridgeStatus = composeState<JellyPlaySeerrStatus?>(null)
    val bridgeStatus: JellyPlaySeerrStatus? get() = _bridgeStatus.value

    /**
     * The bridge's operation/pairing machine ([SeerrBridgeQuickConnectPhase]).
     * The four legacy flags below are its DERIVED projections, kept so the
     * screen and the pins read the same surface as before.
     */
    private val _bridgePhase =
        composeState<SeerrBridgeQuickConnectPhase>(SeerrBridgeQuickConnectPhase.Idle)

    /**
     * A bridge operation (status fetch, link finalize, logout) is in flight.
     * Deliberately the LEGACY semantics — the pairing's Starting/Waiting arms
     * are NOT busy (the login/logout buttons stay enabled-but-guarded during
     * them, exactly as before the machine landed).
     */
    val bridgeBusy: Boolean
        get() = when (_bridgePhase.value) {
            is SeerrBridgeQuickConnectPhase.StatusFetch,
            is SeerrBridgeQuickConnectPhase.Finalizing,
            is SeerrBridgeQuickConnectPhase.LoggingOut,
                -> true

            else -> false
        }

    /** Quick Connect is starting (availability check + initiate in flight). */
    val bridgeQcStarting: Boolean
        get() = _bridgePhase.value is SeerrBridgeQuickConnectPhase.Starting

    /** Non-null while the bridge link flow waits for the Quick Connect approval (the code to display). */
    val bridgeQcCode: String?
        get() = (_bridgePhase.value as? SeerrBridgeQuickConnectPhase.WaitingForApproval)?.code

    /** The last bridge operation's failure; cleared by the next operation. */
    val bridgeFailure: SeerrBridgeFailure?
        get() = (_bridgePhase.value as? SeerrBridgeQuickConnectPhase.Failed)?.failure

    /** The QC poll loop ([startBridgeQuickConnect]); cancelled on cancel/mode-exit/restart. */
    private var bridgeQuickConnectJob: Job? = null

    /**
     * Connection status via the shared [ConnectionProbe] board (single key —
     * one connection): the machine owns single-flight RESTART, the
     * cancellation-never-lands-as-Error rule, and the localized fallback texts.
     *
     * Behavior:
     *  - a refused request (blank field) SUPERSEDES an in-flight test, so a
     *    stale in-flight result cannot overwrite the fresh validation error.
     *  - superseding a test does not flicker the spinner between probes (the
     *    screen's Testing flag derives from the status).
     *  - an unexpected crash degrades to the localized UnexpectedError fallback.
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
            val (apiKey, password, sessionCookie) = withContext(settingsIoDispatcher) {
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
            // The saved connection MODE (direct vs via-server) seeds the pane;
            // a user who re-enters in via-server mode gets a fresh bridge
            // status so the linked/unlinked state is current. The seed must
            // NOT clobber an explicit user change that lands while this
            // coroutine is parked on the IO dispatcher (the store read above):
            // once the user has expressed a choice this session, it wins.
            if (!userModeChanged) {
                _useServerBridge.value = prefs.useServerBridge
            }
            if (prefs.useServerBridge && pluginApiClient != null && bridgeGateOpen()) {
                refreshBridgeStatus()
            }
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
     * The probe action per request variant. The server URL is written
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
                seerrAuthenticator.testApiKeyConnection().fold(
                    onSuccess = { ConnectionProbe.Outcome.Reachable(SeerrConnectionDetails(it.version)) },
                    onFailure = { ConnectionProbe.unreachable(it.message) },
                )
            }
            is SeerrProbeRequest.Jellyfin -> probeLogin(
                method = SeerrAuthMethod.JELLYFIN,
                login = { seerrAuthenticator.loginJellyfin(request.username, request.password) },
                persistCredentials = {
                    seerrPreferencesStore.setUsername(request.username)
                    secureCredentialsStore.setPassword(request.password)
                },
            )
            is SeerrProbeRequest.Local -> probeLogin(
                method = SeerrAuthMethod.LOCAL,
                login = { seerrAuthenticator.loginLocal(request.email, request.password) },
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

    // ── via-server bridge actions ──
    // The pane-level lifecycle of the plugin-brokered Seerr link:
    // mode persist + status fetch + Quick Connect link + logout. The direct
    // probe board above is untouched — the bridge has its own state block.

    /**
     * One capabilities probe (the sync section's discipline: called when the
     * mode selector's section becomes visible; UNKNOWN → one refresh, the
     * store's identity reset re-arms the next visit). After an AVAILABLE
     * probe, a user already in via-server mode gets the bridge status fetched
     * so the pane never sits on a stale/unfetched link state.
     */
    fun refreshJellyPlayPluginStatus() {
        val store = pluginStatusStore ?: return
        launch {
            store.refresh()
            if (store.status.value == JellyPlayPluginStatus.AVAILABLE &&
                _useServerBridge.value &&
                _bridgeStatus.value == null &&
                bridgeGateOpen()
            ) {
                fetchBridgeStatus()
            }
        }
    }

    /**
     * The bridge surfaces' gate arm (probe AND the user's `seerr-bridge`
     * toggle). Fail-CLOSED on the unwired gate seam: the registry is the only
     * gating mechanism. The saved via-server MODE pref stays put
     * while off — the screen just renders the direct pane and the repository
     * stays in direct mode until the toggle (or the pref) comes back.
     */
    private suspend fun bridgeGateOpen(): Boolean =
        jellyPlayFeatureGate?.isAvailableNow(JellyPlayPluginFeatures.SeerrBridge) ?: false

    /**
     * Persists the connection MODE and drives the pane transition: entering
     * via-server fetches the plugin's Seerr status (the pane's linked/unlinked
     * truth); leaving cancels any in-flight Quick Connect. Same-value calls
     * are no-ops (the segmented row fires on every tap).
     */
    fun setUseServerBridge(enabled: Boolean) {
        if (enabled == _useServerBridge.value) return
        userModeChanged = true
        _useServerBridge.value = enabled
        launch { seerrPreferencesStore.setUseServerBridge(enabled) }
        if (enabled) {
            cancelBridgeQuickConnect()
            // A fresh mode entry clears a Failed banner too (the legacy
            // `_bridgeFailure = null`).
            _bridgePhase.value = SeerrBridgeQuickConnectPhase.Idle
            refreshBridgeStatus()
        } else {
            cancelBridgeQuickConnect()
        }
    }

    /** Public re-fetch of the bridge status (the pane's retry affordance). */
    fun refreshBridgeStatus() {
        if (pluginApiClient == null) return
        launch { fetchBridgeStatus() }
    }

    /**
     * The status fetch: the pane's linked/unlinked/configured truth comes from
     * the plugin (per-user session state), NOT from any client-side credential
     * store — the bridge session lives server-side. A failure carries the
     * transport text verbatim ([SeerrBridgeFailure.Reported]) or the declared
     * [SeerrBridgeFailure.Fallback.StatusUnreachable] fallback; the previous
     * status stays (a stale link state beats a blank pane) and the failure
     * renders in the pane's banner. Phase exit is [Idle] on success and
     * [SeerrBridgeQuickConnectPhase.Failed] on failure — the finalize path
     * relies on that landing [Idle] (it skips its own terminal write).
     */
    private suspend fun fetchBridgeStatus() {
        val client = pluginApiClient ?: return
        _bridgePhase.value = SeerrBridgeQuickConnectPhase.StatusFetch
        client.seerrStatus().fold(
            onSuccess = { status ->
                _bridgeStatus.value = status
                _bridgePhase.value = SeerrBridgeQuickConnectPhase.Idle
            },
            onFailure = { failure ->
                _bridgePhase.value = SeerrBridgeQuickConnectPhase.Failed(
                    failure.message?.takeIf { it.isNotBlank() }
                        ?.let(SeerrBridgeFailure::Reported)
                        ?: SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.StatusUnreachable),
                )
            },
        )
    }

    /**
     * The Quick Connect link flow — the AuthViewModel discipline (availability
     * check → initiate → code display → poll → finalize) with the plugin's
     * `JellyPlaySeerrRoutes.seerrLogin` as the finalize step: the plugin
     * authorizes the JELLYFIN Quick Connect secret server-side and SSOs into
     * Seerr with it, so the client never handles a Seerr credential. Failures
     * land as [SeerrBridgeQuickConnectPhase.Failed]; success re-fetches the
     * status (linked). The guard admits [Idle] and Failed only — a restart
     * after a failure is the retry path; any live phase refuses.
     */
    fun startBridgeQuickConnect() {
        val client = pluginApiClient ?: return
        val repo = authRepository ?: return
        when (_bridgePhase.value) {
            is SeerrBridgeQuickConnectPhase.Idle,
            is SeerrBridgeQuickConnectPhase.Failed,
                -> Unit

            else -> return
        }
        cancelBridgeQuickConnect()
        bridgeQuickConnectJob = launch {
            _bridgePhase.value = SeerrBridgeQuickConnectPhase.Starting
            val qcEnabled = repo.isQuickConnectEnabled().fold(
                onSuccess = { it },
                onFailure = { null },
            )
            if (qcEnabled != true) {
                _bridgePhase.value =
                    SeerrBridgeQuickConnectPhase.Failed(SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.QuickConnectDisabled))
                return@launch
            }
            val qcInfo = repo.initiateQuickConnect().fold(
                onSuccess = { it },
                onFailure = { failure ->
                    _bridgePhase.value = SeerrBridgeQuickConnectPhase.Failed(
                        failure.message?.takeIf { it.isNotBlank() }
                            ?.let(SeerrBridgeFailure::Reported)
                            ?: SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.QuickConnectFailed),
                    )
                    null
                },
            ) ?: return@launch
            _bridgePhase.value = SeerrBridgeQuickConnectPhase.WaitingForApproval(qcInfo.code)
            pollBridgeQuickConnect(client, repo, qcInfo.code, qcInfo.secret)
        }
    }

    /**
     * The approval poll (the AuthViewModel cadence: one poll per
     * [QC_POLL_INTERVAL_MS], [QC_MAX_ATTEMPTS] total). Each tick re-arms
     * [WaitingForApproval] first — a status fetch landing mid-poll (the
     * retry button has no pairing guard) writes a terminal phase and would
     * otherwise strand the machine out of the pairing while the loop runs.
     * On approval the finalize runs (`JellyPlaySeerrRoutes.seerrLogin` with
     * the secret) and lands Idle (via the nested status fetch) or Failed; on
     * exhaustion the declared timeout fallback lands as Failed — which also
     * clears the stranded code (declared v0.11.10 fix: the legacy write left
     * `bridgeQcCode` standing, so the timed-out code stayed on screen and the
     * start guard blocked a restart until the user found the cancel button).
     */
    private suspend fun pollBridgeQuickConnect(
        client: JellyPlaySeerrRoutes,
        repo: AuthRepository,
        code: String,
        secret: String,
    ) {
        repeat(QC_MAX_ATTEMPTS) {
            delay(QC_POLL_INTERVAL_MS)
            if (_bridgePhase.value !is SeerrBridgeQuickConnectPhase.WaitingForApproval) {
                _bridgePhase.value = SeerrBridgeQuickConnectPhase.WaitingForApproval(code)
            }
            val authenticated = repo.pollQuickConnect(secret).fold(
                onSuccess = { it.authenticated },
                onFailure = { failure ->
                    _bridgePhase.value = SeerrBridgeQuickConnectPhase.Failed(
                        failure.message?.takeIf { it.isNotBlank() }
                            ?.let(SeerrBridgeFailure::Reported)
                            ?: SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.QuickConnectFailed),
                    )
                    return
                },
            )
            if (authenticated) {
                _bridgePhase.value = SeerrBridgeQuickConnectPhase.Finalizing
                client.seerrLogin(
                    authType = QC_AUTH_TYPE,
                    username = null,
                    password = null,
                    quickConnectSecret = secret,
                ).fold(
                    onSuccess = { fetchBridgeStatus() },
                    onFailure = { failure ->
                        _bridgePhase.value = SeerrBridgeQuickConnectPhase.Failed(
                            failure.message?.takeIf { it.isNotBlank() }
                                ?.let(SeerrBridgeFailure::Reported)
                                ?: SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.LoginFailed),
                        )
                    },
                )
                return
            }
        }
        _bridgePhase.value =
            SeerrBridgeQuickConnectPhase.Failed(SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.QuickConnectTimeout))
    }

    /**
     * Cancels an in-flight Quick Connect flow (cancel button, mode exit,
     * restart). The server-side QC request simply expires; nothing to revoke.
     * Every phase whose writer was the just-cancelled job returns to [Idle]
     * (including Finalizing — a dead finalize must not leave the legacy
     * stuck-busy); a [SeerrBridgeQuickConnectPhase.Failed] banner is kept
     * (cancel is not the failure's dismissal).
     */
    fun cancelBridgeQuickConnect() {
        bridgeQuickConnectJob?.cancel()
        bridgeQuickConnectJob = null
        when (_bridgePhase.value) {
            is SeerrBridgeQuickConnectPhase.Starting,
            is SeerrBridgeQuickConnectPhase.WaitingForApproval,
            is SeerrBridgeQuickConnectPhase.Finalizing,
                -> _bridgePhase.value = SeerrBridgeQuickConnectPhase.Idle

            else -> Unit
        }
    }

    /**
     * Unlinks the server-side Seerr session (the plugin deletes the per-user
     * cookies — the client holds no session to clear) and re-fetches the
     * status so the pane flips to unlinked. Failures surface as
     * [SeerrBridgeQuickConnectPhase.Failed]; the status re-fetch only runs on
     * success (it lands the terminal [Idle]).
     */
    fun bridgeLogout() {
        val client = pluginApiClient ?: return
        when (_bridgePhase.value) {
            is SeerrBridgeQuickConnectPhase.Idle,
            is SeerrBridgeQuickConnectPhase.Failed,
                -> Unit

            else -> return
        }
        launch {
            _bridgePhase.value = SeerrBridgeQuickConnectPhase.LoggingOut
            client.seerrLogout().fold(
                onSuccess = { fetchBridgeStatus() },
                onFailure = { failure ->
                    _bridgePhase.value = SeerrBridgeQuickConnectPhase.Failed(
                        failure.message?.takeIf { it.isNotBlank() }
                            ?.let(SeerrBridgeFailure::Reported)
                            ?: SeerrBridgeFailure.Declared(SeerrBridgeFailure.Fallback.LogoutFailed),
                    )
                },
            )
        }
    }

    /**
     * Connected details: the server's reported version ("" when restored from
     * the store at init without a probe).
     */
    data class SeerrConnectionDetails(val version: String)

    private companion object {
        /** The plugin login route's quick-connect authType (the wire contract's vocabulary). */
        const val QC_AUTH_TYPE = "quickconnect"

        /** The approval poll cadence (the AuthViewModel Quick Connect discipline). */
        const val QC_POLL_INTERVAL_MS = 3_000L

        /** Poll attempts before the declared timeout (~2 minutes of approval window). */
        const val QC_MAX_ATTEMPTS = 40
    }

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
