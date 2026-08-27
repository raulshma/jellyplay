package com.raulshma.jellyplay.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.io.IOException

/**
 * Connect/sign-in orchestration for the web shell (wave 12C slice 2). The web
 * module has NO AuthRepository and no core:data on wasm (Room cut), so this
 * controller talks to [AuthApiClient] directly — the same client the desktop
 * pane drives through the repository, with the session publish/restore spine
 * (capture/adopt/try/publish/restore) living inside the client's atomicLogin.
 *
 * Call order mirrors `AuthRepositoryImpl`/`JellyfinApiEngine` semantics:
 *  - probe: [getServerInfo] — single-shot GET /System/Info/Public, NO session
 *    adoption and NO retry backoff (the retry-wrapped connectToServer would
 *    stall a dead/CORS-blocked host behind ~4s of backoff before surfacing a
 *    first error).
 *  - sign in: [authenticateUser] — adopts the probed server pre-auth,
 *    publishes the authenticated (server, user) pair atomically on success,
 *    restores the captured session on failure; on success capability
 *    declaration is fired via [declareCapabilitiesAfterSignIn] (login-time
 *    concern only — nothing is posted on logout, the repository logout path
 *    doesn't either).
 *
 * SIDE-EFFECT OWNERSHIP (the one non-obvious rule): publishing the session
 * swaps the signed-in card in immediately, which DISPOSES the pane coroutine
 * scope that made the call — anything still running there would be cancelled
 * mid-flight. Post-success work with real effects (the capabilities POST that
 * gates playback features server-side; the last-server-url DataStore write)
 * therefore runs on this controller's own [sideEffectScope], not any pane's,
 * and surfaces outcomes through [capabilityNote] instead of pane-local
 * message state.
 *  - logout: revokeServerSession best-effort (server-side token revocation),
 *    then disconnect() clears the local pair unconditionally — same shape as
 *    `AuthRepositoryImpl.revokeServerSession` minus the Room/identity-store
 *    writes that have no wasm counterpart yet.
 *
 * last-server-url persistence rides the shared "user_prefs" DataStore the web
 * module already resolves from datastoreCommonModule names
 * ([DatastoreQualifiers.userPreferencesDataStore]), under its own key. Every
 * read/write is try-caught so storage failures degrade silently to an empty
 * field (the wasm localStorage storage adapter already degrades internally;
 * these catches cover the DataStore plumbing itself).
 */
internal class WebConnectController(
    private val auth: AuthApiClient,
    private val userPrefs: DataStore<Preferences>,
) {
    private companion object {
        val LAST_SERVER_URL_KEY = stringPreferencesKey("web_last_server_url")
    }

    // Post-success work that must OUTLIVE the pane which started it (see
    // SIDE-EFFECT OWNERSHIP above): the connected card can replace the
    // sign-in form the instant the atomic session publishes, disposing the
    // pane's rememberCoroutineScope. Owned by this shell-level controller —
    // it lives as long as the page does, like the singleton API clients, and
    // is never cancelled explicitly. SupervisorJob keeps one failed job from
    // tearing down siblings; Dispatchers.Default is fine for a DataStore
    // write and one POST on wasm.
    private val sideEffectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Post-sign-in capability outcome for the connected card to render.
     * Non-null once the latest declaration attempt finished badly; reset to
     * null at each new sign-in's declaration start. Lives here rather than in
     * pane state precisely because the declaration outlives the swap — the
     * old "signed in, but capability registration failed" line was written to
     * sign-in-form state and could never be seen after the swap.
     */
    private val _capabilityNote = MutableStateFlow<String?>(null)
    val capabilityNote: StateFlow<String?> = _capabilityNote.asStateFlow()

    /**
     * The client's atomic session flow — the UI's single truth source for
     * signed-in-vs-not (context.md atomic rule: read the combined session,
     * never a combine of the two side flows).
     */
    val session: Flow<ActiveSession?> get() = auth.session

    /** Probes a server WITHOUT adopting anything into the session state. */
    suspend fun probeServer(address: String): Result<ServerInfo> = auth.getServerInfo(address)

    /**
     * Signs into [server]; success means the authenticated session is already
     * published by the time this Result succeeds.
     */
    suspend fun signIn(server: ServerInfo, username: String, password: String): Result<UserInfo> =
        auth.authenticateUser(serverInfo = server, username = username, password = password)

    /**
     * Fires capability declaration on [sideEffectScope] and returns
     * immediately — this MUST NOT be awaited from a pane scope, because the
     * ConnectedCard swap that follows sign-in success disposes that scope.
     * Failure is non-fatal information: it lands in [capabilityNote] for the
     * connected card to render, never gating the signed-in state.
     */
    fun declareCapabilitiesAfterSignIn() {
        _capabilityNote.value = null
        sideEffectScope.launch {
            val failed = try {
                auth.postCapabilities().isFailure
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Declaration work itself only fails as a Result; this catch
                // guards the plumbing around it (job races, transport throw).
                true
            }
            _capabilityNote.value = if (failed) {
                "Capability registration failed; some playback features may misbehave."
            } else {
                null
            }
        }
    }

    /**
     * Revokes the server-side session best-effort, then always clears the
     * local atomic session pair — same shape as
     * `AuthRepositoryImpl.revokeServerSession` minus the Room/identity-store
     * writes that have no wasm counterpart yet.
     *
     * Returns whether the server call succeeded. Deliberately unused by the UI
     * today: disconnect() unmounts the connected card either way, so there is
     * nothing left in place to render such a note into (documented v1 gap).
     */
    suspend fun logout(): Boolean {
        val revoked = try {
            auth.revokeServerSession().isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        auth.disconnect()
        return revoked
    }

    /** Last successfully-probed server URL; null when never saved/unavailable. */
    suspend fun lastServerUrl(): String? = try {
        userPrefs.data.first()[LAST_SERVER_URL_KEY]
    } catch (_: Exception) {
        null
    }

    /** Persists a just-probed URL on [sideEffectScope]; fire-and-forget. */
    fun rememberServerUrlLater(url: String) {
        sideEffectScope.launch { rememberServerUrl(url) }
    }

    /**
     * Persists a just-probed URL; failures degrade silently (session-only).
     * Private: callers must go through [rememberServerUrlLater] so the write
     * cannot be orphaned by pane disposal (same rule as capability
     * declaration above).
     */
    private suspend fun rememberServerUrl(url: String) {
        try {
            userPrefs.edit { prefs -> prefs[LAST_SERVER_URL_KEY] = url }
        } catch (_: Exception) {
            // Storage unavailable/quota/corruption: keep the field usable anyway.
        }
    }
}

/**
 * Landing-pane connect/auth flow (wave 12C): replaces the Phase W placeholder
 * readout when signed out — server probe → inline name result → username /
 * password sign-in — and collapses to a minimal connected card (server, user,
 * online/offline chip, logout) once the auth client's atomic session publishes.
 *
 * All feedback is plain inline Text on purpose: the shell has no Scaffold, so
 * there is no snackbar host, and window.alert is banned. This is connect/auth
 * browsing status ONLY — not a feature browser.
 *
 * RUNTIME HONESTY: verified in a real browser (2026-08-27, wave 13C) — the
 * headless-Edge CDP lane (tools/e2e/web-verify.mjs) clicked through this
 * exact flow against a live Jellyfin 10.11.11 server: URL typed via CDP
 * Input.insertText, probe (Connect) → sign-in fields, credentials entered,
 * Sign in → ConnectedCard, with zero console errors. Autoplay-muted
 * HtmlVideoEngine playback from the connected session was verified one level
 * deeper in WebDiagnosticsPane the same run.
 *
 * Cut from v1 (documented deltas vs DesktopSignInPane): QuickConnect,
 * remembered-user prefill, password visibility toggle (no Tabler icon set on
 * the web module), and a server Version line — /System/Info/Public carries
 * Version in real responses but the shared wire DTO subset reads only
 * Id/ServerName today, and extending shared DTOs is outside this slice.
 */
@Composable
internal fun WebConnectFlow(
    controller: WebConnectController,
    networkStatus: NetworkStatus,
    modifier: Modifier = Modifier,
    onOpenConnectionDetails: (() -> Unit)? = null,
    // Wave 13C: opens the gated E2E diagnostics pane (WebDiagnosticsPane)
    // — deliberately optional so nothing renders until the nav root wires it.
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    // initial = null is honest on wasm v1: nothing restores a session at
    // boot (no persisted identity), so the flow genuinely starts empty. If a
    // boot-time restore ever lands, swap the accessor for the StateFlow.
    val activeSession by controller.session.collectAsState(initial = null)
    val active: ActiveSession? = activeSession

    if (active != null) {
        ConnectedCard(
            controller = controller,
            session = active,
            networkStatus = networkStatus,
            onOpenConnectionDetails = onOpenConnectionDetails,
            onOpenDiagnostics = onOpenDiagnostics,
            modifier = modifier,
        )
    } else {
        SignInCard(controller = controller, modifier = modifier)
    }
}

/** Connected state: server + user facts, connectivity chip, logout. */
@Composable
private fun ConnectedCard(
    controller: WebConnectController,
    session: ActiveSession,
    networkStatus: NetworkStatus,
    onOpenConnectionDetails: (() -> Unit)?,
    onOpenDiagnostics: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var loggingOut by remember { mutableStateOf(false) }
    // Declaration result lives on the controller (it outlives this card's
    // composition); collected here so a failure is actually SEEABLE — the v1
    // line for this was written into the disposed sign-in form instead.
    val capabilityNote by controller.capabilityNote.collectAsState()
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier.width(480.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Connected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${session.server.name} — ${session.server.address}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Signed in as ${session.user.name}.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            capabilityNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Connectivity pill: Surface (not AssistChip) — a disabled m3 chip
            // renders at reduced opacity, which reads as broken rather than as
            // a status badge.
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (networkStatus.hasNetwork) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (networkStatus.hasNetwork) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                Text(
                    text = when {
                        networkStatus.isOnline -> "Online"
                        networkStatus.isOffline -> "Offline"
                        else -> "Local network"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loggingOut) CircularProgressIndicator(Modifier.height(20.dp))
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = !loggingOut,
                    onClick = {
                        loggingOut = true
                        scope.launch {
                            try {
                                // controller.logout() clears the atomic pair
                                // unconditionally after the best-effort revoke, so
                                // this card unmounts either way — a revoke-failure
                                // note rendered HERE could never actually be seen
                                // (v1 honesty gap, not surfaced anywhere).
                                controller.logout()
                            } finally {
                                loggingOut = false
                            }
                        }
                    },
                ) {
                    Text("Logout")
                }
            }
            if (onOpenConnectionDetails != null) {
                Button(onClick = onOpenConnectionDetails) {
                    Text("Connection details")
                }
            }
            // Wave 13C E2E hook: gated entry into WebDiagnosticsPane. An
            // OutlinedButton so it reads as secondary tooling next to the
            // primary actions — the pane is a verification surface, not a
            // user-facing feature.
            if (onOpenDiagnostics != null) {
                OutlinedButton(onClick = onOpenDiagnostics) {
                    Text("Diagnostics")
                }
            }
        }
    }
}

/** Signed-out state: probe form (+sign-in section once probed). */
@Composable
private fun SignInCard(
    controller: WebConnectController,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var probing by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var probedServer by remember { mutableStateOf<ServerInfo?>(null) }
    var probeLine by remember { mutableStateOf<String?>(null) }
    var probeIsError by remember { mutableStateOf(false) }
    var corsHintVisible by remember { mutableStateOf(false) }
    var signInLine by remember { mutableStateOf<String?>(null) }

    // Seed from the persisted last URL once; leaves the field fully editable.
    LaunchedEffect(controller) {
        if (serverUrl.isBlank()) {
            controller.lastServerUrl()?.let { serverUrl = it }
        }
    }
    val scope = rememberCoroutineScope()

    fun clearTransient() {
        probeLine = null
        probeIsError = false
        corsHintVisible = false
        signInLine = null
    }

    Card(
        modifier = modifier.width(480.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Connect to your Jellyfin server",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    probedServer = null
                    clearTransient()
                },
                label = { Text("Server URL") },
                supportingText = { Text("e.g. media.example.com — https:// is added automatically") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !probing && !signingIn,
                modifier = Modifier.fillMaxWidth(),
            )
            probeLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (probeIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (corsHintVisible) {
                Text(
                    text = "If the address is correct, your server or reverse proxy may be blocking " +
                        "browser requests (CORS). See docs/jellyfin-cors.md in the project repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (probing) CircularProgressIndicator(Modifier.height(20.dp))
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = !probing && !signingIn && serverUrl.isNotBlank(),
                    onClick = {
                        probing = true
                        clearTransient()
                        scope.launch {
                            val result = controller.probeServer(serverUrl)
                            probing = false
                            result
                                .onSuccess { info ->
                                    probedServer = info
                                    probeLine = "Found \"${info.name}\" at ${info.address}."
                                    probeIsError = false
                                    controller.rememberServerUrlLater(info.address)
                                }
                                .onFailure { failure ->
                                    if (failure is CancellationException) return@onFailure
                                    probeLine = friendlyProbeFailure(failure, window.navigator.onLine)
                                    probeIsError = true
                                    corsHintVisible = isLikelyCorsOrTransport(failure) && window.navigator.onLine
                                }
                        }
                    },
                ) {
                    Text("Connect")
                }
            }

            // Sign-in section appears only after a successful probe.
            if (probedServer != null) {
                Text(
                    text = "Sign in to ${probedServer?.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        signInLine = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !signingIn && !probing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        signInLine = null
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !signingIn && !probing,
                    modifier = Modifier.fillMaxWidth(),
                )
                signInLine?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (signingIn) CircularProgressIndicator(Modifier.height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = !signingIn && !probing &&
                            username.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            val target = probedServer ?: return@Button
                            signingIn = true
                            signInLine = null
                            scope.launch {
                                val result = controller.signIn(target, username.trim(), password)
                                signingIn = false
                                result
                                    .onSuccess { _ ->
                                        // Session already published by the client's
                                        // atomicLogin. Declaration fires on the
                                        // CONTROLLER's scope, deliberately not this
                                        // pane's: the ConnectedCard swap that this
                                        // success triggers disposes the pane scope and
                                        // would kill an in-flight capabilities POST.
                                        controller.declareCapabilitiesAfterSignIn()
                                        // Swap-out happens via the session flow; a failed
                                        // declaration is surfaced IN the connected card.
                                    }
                                    .onFailure { failure ->
                                        if (failure is CancellationException) return@onFailure
                                        signInLine = friendlySignInFailure(failure)
                                    }
                            }
                        },
                    ) {
                        Text("Sign in")
                    }
                }
            }
        }
    }
}

/**
 * True when [failure] looks like a transport-layer refusal rather than a
 * server verdict. Two signals, either suffices:
 *  - TYPE: Ktor Js/fetch IO errors (which CORS blocks surface as) or the
 *    timeout plugin. The assumption that the Js engine wraps fetch failures
 *    in [IOException] is statically unverifiable from this repo's lanes.
 *  - MESSAGE: the raw browser rejection strings ("Failed to fetch" on
 *    Chromium, "NetworkError" on Firefox, "Load failed" on WebKit) matched
 *    case-insensitively down the cause chain, so the friendly line + CORS
 *    hint survive an engine whose wrapping differs; the coordinator's
 *    real-server browser pass will confirm the actual taxonomy.
 *
 * Used only to decide whether the CORS doc pointer shows alongside the error
 * line — never to replace the typed message itself.
 */
private fun isLikelyCorsOrTransport(failure: Throwable): Boolean {
    var cause: Throwable? = failure
    while (cause != null) {
        if (cause is HttpRequestTimeoutException || cause is IOException) return true
        val message = cause.message?.lowercase() ?: ""
        if (
            "failed to fetch" in message ||
            "networkerror" in message ||
            "load failed" in message
        ) {
            return true
        }
        cause = cause.cause
    }
    return false
}

/**
 * Probe-stage error mapping: transport refusals get a diagnosable line (with
 * the CORS doc hint added separately when the browser still reports
 * connectivity); anything else falls back to whatever the failure carries.
 */
private fun friendlyProbeFailure(failure: Throwable, browserOnline: Boolean): String {
    if (isLikelyCorsOrTransport(failure)) {
        return if (browserOnline) {
            "Could not reach the server (request refused or timed out)."
        } else {
            "The browser reports no connectivity."
        }
    }
    return failure.message ?: "Could not reach the server."
}

/**
 * Sign-in-stage error mapping, invalid-credentials vs unreachable kept
 * distinct: Jellyfin answers wrong credentials with HTTP 401, which the
 * client surfaces as an access-denied ApiException; transport failures ride
 * the client's classified retryable messages ("Connection timed out…", etc.).
 */
private fun friendlySignInFailure(failure: Throwable): String = when {
    failure is ApiException && failure.httpCode == 401 -> "Incorrect username or password."
    failure.message != null -> failure.message!!
    else -> "Sign-in failed."
}
