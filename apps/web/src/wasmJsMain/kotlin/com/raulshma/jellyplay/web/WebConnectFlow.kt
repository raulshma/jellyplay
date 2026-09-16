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
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Connect/sign-in orchestration for the web shell. The establishment
 * choreography — probe/adopt, authenticate, session persistence, boot
 * restore, revoke/logout — lives in the shared [AuthRepository] seam now
 * (`WasmAuthRepository` in core:data's wasmJs slice, bound by
 * `dataWasmModule`), so this controller is construction + capability notes
 * only: every establishment call delegates, and the landing UI observes the
 * repository's flows ([isAuthenticated]/[currentServer]/[currentUser]).
 * The hand-mirrored "call order mirrors AuthRepositoryImpl" body this file
 * used to carry is deleted — the mirror is the shared implementation.
 *
 * What remains HERE (and why):
 *  - PROBE DISCIPLINE: the landing probe rides [AuthRepository.probeServer]
 *    (single-shot `GET /System/Info/Public`, NO session adoption, NO retry
 *    backoff — `addServer`/`login`'s connectToServer paths adopt and would
 *    stall a dead/CORS-blocked host behind backoff before surfacing a first
 *    error).
 *  - SIDE-EFFECT OWNERSHIP (the one non-obvious rule): publishing the
 *    session swaps the signed-in card in immediately, which DISPOSES the
 *    pane coroutine scope that made the call — anything still running there
 *    would be cancelled mid-flight. Post-success work with real effects
 *    (the capabilities POST that gates playback features server-side; the
 *    boot-time restore; the URL-seed read) therefore runs on this
 *    controller's own [WebSideEffectScope], not any pane's, and surfaces
 *    outcomes through [capabilityNote] instead of pane-local message state.
 *  - BOOT RESTORE: `restoreSession()` fires once per page on construction
 *    (the desktop shell does the same at its root) — a persisted
 *    (server, user) pair in the OPFS Room database re-establishes across
 *    reloads; no identity / broken storage / second-tab OPFS lock degrades
 *    fail-closed to the sign-in pane (restore's stages are timeout-bounded
 *    inside the repository).
 *  - URL SEED: the sign-in field prefills from the repository's server rows
 *    (Room, most-recently-connected first). The pre-repository
 *    `web_last_server_url` DataStore key is a ONE-TIME migration source —
 *    read only when no server row exists yet, consumed (removed) after the
 *    read either way; nothing ever writes it again.
 *  - LOGOUT: [AuthRepository.revokeServerSession] verbatim — server-side
 *    revocation best-effort, then user-row removal, local disconnect and
 *    identity-store clear. (The old controller returned whether the revoke
 *    succeeded; the UI never rendered it — documented v1 gap, now gone with
 *    the delegation.)
 */
internal class WebConnectController(
    private val authRepository: AuthRepository,
    private val userPrefs: DataStore<Preferences>,
) {
    private companion object {
        /**
         * The pre-repository persistence key in the shared "user_prefs"
         * DataStore — read ONCE for migration (only when no Room server row
         * exists), then consumed. Never written again: server rows own the
         * memory now.
         */
        val LEGACY_LAST_SERVER_URL_KEY = stringPreferencesKey("web_last_server_url")
    }

    // Post-success work that must OUTLIVE the pane which started it (see
    // SIDE-EFFECT OWNERSHIP above): the connected card can replace the
    // sign-in form the instant the atomic session publishes, disposing the
    // pane's rememberCoroutineScope. Owned by this shell-level controller;
    // the scope lifetime + degrade shape live in [WebSideEffectScope]
    // (shared with WebSeerrController).
    private val sideEffectScope = WebSideEffectScope()

    /**
     * Post-sign-in capability outcome for the connected card to render.
     * Non-null once the latest declaration attempt finished badly; reset to
     * null at each new sign-in's declaration start. Lives here rather than in
     * pane state precisely because the declaration outlives the swap.
     */
    private val _capabilityNote = MutableStateFlow<String?>(null)
    val capabilityNote: StateFlow<String?> = _capabilityNote.asStateFlow()

    /**
     * The URL-field seed for the sign-in card — resolves once per page on
     * [sideEffectScope]: the most-recently-connected Room server row's
     * address, else the one-time legacy-key migration read, else "" (no
     * seed). Terminal value guaranteed non-null so the card's one-shot
     * collector never parks forever.
     */
    private val _serverUrlSeed = MutableStateFlow<String?>(null)
    val serverUrlSeed: StateFlow<String?> = _serverUrlSeed.asStateFlow()

    /** The repository's atomic-session-derived gate (never a side-flow combine). */
    val isAuthenticated: StateFlow<Boolean> get() = authRepository.isAuthenticated

    /** The repository's session fact flows, for the connected card's lines. */
    val currentServer: Flow<ServerInfo?> get() = authRepository.currentServer
    val currentUser: Flow<UserInfo?> get() = authRepository.currentUser

    init {
        // Boot choreography (desktop-shell precedent: restore fires once at
        // the root, failures degrade fail-closed to the sign-in pane).
        sideEffectScope.launchDegrading { authRepository.restoreSession() }
        sideEffectScope.launchDegrading { resolveServerUrlSeed() }
    }

    /** Probes a server WITHOUT adopting anything into the session state. */
    suspend fun probeServer(address: String): Result<ServerInfo> =
        authRepository.probeServer(address)

    /**
     * Signs into the probed [server] through [AuthRepository.login]. First
     * sign-in against a server with no Room row takes login's raw-address
     * path (the client connect+authenticates in one round — one extra probe
     * vs the desktop addServer-then-login two-screen flow); success persists
     * the server + user rows and the active-session identity exactly as on
     * android/desktop, which is also what makes the next boot's restore work.
     */
    suspend fun signIn(server: ServerInfo, username: String, password: String): Result<UserInfo> =
        authRepository.login(serverAddress = server.address, username = username, password = password)

    /**
     * Fires capability declaration on [sideEffectScope] and returns
     * immediately — this MUST NOT be awaited from a pane scope, because the
     * ConnectedCard swap that follows sign-in success disposes that scope.
     * Failure is non-fatal information: it lands in [capabilityNote] for the
     * connected card to render, never gating the signed-in state.
     */
    fun declareCapabilitiesAfterSignIn() {
        _capabilityNote.value = null
        sideEffectScope.launchDegrading {
            val failed = try {
                authRepository.postCapabilities().isFailure
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
     * Full repository logout: best-effort server-side revocation, user-row
     * removal, atomic local disconnect, identity-store clear (the same
     * choreography android/desktop run).
     */
    suspend fun logout() {
        authRepository.revokeServerSession()
    }

    /**
     * Resolves [serverUrlSeed] once per page. Room read failures (e.g. the
     * second tab's OPFS pool lock — web ships single-tab) and DataStore
     * failures both degrade to "" (empty field), never a crash: this runs on
     * the side-effect scope precisely so a broken store cannot take the
     * landing pane down.
     */
    private suspend fun resolveServerUrlSeed() {
        val fromDb = runCatchingRethrowingCancellation {
            // servers orders by lastConnected DESC (the DAO query); first
            // row = most recently connected server.
            authRepository.servers.first().firstOrNull()?.address
        }.getOrNull()
        if (fromDb != null) {
            _serverUrlSeed.value = fromDb
            consumeLegacySeedKey()
            return
        }
        val legacy = runCatchingRethrowingCancellation {
            userPrefs.data.first()[LEGACY_LAST_SERVER_URL_KEY]
        }.getOrNull()
        _serverUrlSeed.value = legacy ?: ""
        if (legacy != null) consumeLegacySeedKey()
    }

    /** One-time migration close-out: remove the legacy key, degrade silently. */
    private suspend fun consumeLegacySeedKey() {
        runCatchingRethrowingCancellation {
            userPrefs.edit { prefs -> prefs.remove(LEGACY_LAST_SERVER_URL_KEY) }
        }
    }
}

/**
 * One optional landing affordance rendered by [ConnectedCard] after the
 * logout row — a button that opens a shell level (connection details, a
 * shared feature screen, the diagnostics tooling).
 *
 * The list IS the optionality contract (web-nav v1): [WebConnectFlow] stays
 * renderable without a nav root behind it — an empty list renders no buttons,
 * exactly like the old nullable per-hook lambdas. The nav root
 * ([WebAppRoot]'s `entry<WebLanding>`) builds the list with the entries it
 * can push; label and order are load-bearing (tools/e2e/web-verify.mjs finds
 * these buttons by accessible name), and [isOutlined] marks the secondary
 * tooling entry (Diagnostics) so it reads as outlined next to the primary
 * feature buttons.
 */
internal data class WebLandingAffordance(
    val label: String,
    val isOutlined: Boolean = false,
    val onOpen: () -> Unit,
)

/**
 * Landing-pane connect/auth flow: replaces the placeholder
 * readout when signed out — server probe → inline name result → username /
 * password sign-in — and collapses to a minimal connected card (server, user,
 * online/offline chip, logout) once the repository's session publishes.
 *
 * All feedback is plain inline Text on purpose: the shell has no Scaffold, so
 * there is no snackbar host, and window.alert is banned. This is connect/auth
 * browsing status ONLY — not a feature browser.
 *
 * GATE DISCIPLINE: the signed-in swap is keyed on the repository's
 * [WebConnectController.isAuthenticated] — the atomic-session-derived
 * StateFlow — never a `combine` of the server/user side flows (the
 * synthetic `(newServer, oldUser)` intermediate rule). The server/user fact
 * collectors additionally null-guard so the card can never render a
 * half-published pair even for one frame.
 *
 * RUNTIME HONESTY: verified in a real browser (2026-08-27) — the
 * headless-Edge CDP lane (tools/e2e/web-verify.mjs) clicked through this
 * exact flow against a live Jellyfin 10.11.11 server: URL typed via CDP
 * (Input.insertText, with a per-char key-event fallback in the driver),
 * probe (Connect) → sign-in fields, credentials entered,
 * Sign in → ConnectedCard, with zero console errors. Autoplay-muted
 * HtmlVideoEngine playback from the connected session was verified one level
 * deeper in WebDiagnosticsPane the same run. (That pass predates the
 * AuthRepository delegation; the texts/order pinned below are unchanged.)
 *
 * Cut from v1 (documented deltas vs the shared auth screens the desktop shell
 * hosts since then): QuickConnect,
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
    affordances: List<WebLandingAffordance> = emptyList(),
) {
    // isAuthenticated is a StateFlow (no initial param needed); the fact
    // flows are cold, hence the explicit initial null.
    val authenticated by controller.isAuthenticated.collectAsState()
    val server by controller.currentServer.collectAsState(initial = null)
    val user by controller.currentUser.collectAsState(initial = null)
    // Local captures: delegated State values do not smart-cast (the same
    // rule WebStatusPane's healthValue follows).
    val serverInfo = server
    val userInfo = user

    if (authenticated && serverInfo != null && userInfo != null) {
        ConnectedCard(
            controller = controller,
            server = serverInfo,
            user = userInfo,
            networkStatus = networkStatus,
            affordances = affordances,
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
    server: ServerInfo,
    user: UserInfo,
    networkStatus: NetworkStatus,
    affordances: List<WebLandingAffordance>,
    modifier: Modifier,
) {
    var loggingOut by remember { mutableStateOf(false) }
    // Declaration result lives on the controller (it outlives this card's
    // composition); collected here so a failure is actually SEEABLE.
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
                text = "${server.name} — ${server.address}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Signed in as ${user.name}.",
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
            // renders at reduced opacity, which reads as broken rather than as a
            // status badge.
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
                                // The repository's revokeServerSession clears
                                // the atomic pair unconditionally after the
                                // best-effort revoke, so this card unmounts
                                // either way.
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
            // The shell-supplied affordances ([WebLandingAffordance]): one
            // button per shell level, in the order the list carries — the
            // shared feature screens first (primary Buttons), the gated
            // diagnostics tooling last as an OutlinedButton so it reads as
            // secondary. Empty list (no nav root) renders nothing.
            affordances.forEach { affordance ->
                if (affordance.isOutlined) {
                    OutlinedButton(onClick = affordance.onOpen) {
                        Text(affordance.label)
                    }
                } else {
                    Button(onClick = affordance.onOpen) {
                        Text(affordance.label)
                    }
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

    // One-shot seed fill: waits for the controller's terminal seed emission
    // (Room row address / legacy migration / ""), then fills a blank field.
    // Leaves the field fully editable either way. The non-null terminal
    // value is a controller contract; the elvis is the fail-closed escape.
    LaunchedEffect(controller) {
        val seed = controller.serverUrlSeed.first { it != null } ?: return@LaunchedEffect
        if (seed.isNotBlank() && serverUrl.isBlank()) serverUrl = seed
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
                                    // No persistence at probe time: the probe
                                    // is pure by contract; server rows are
                                    // written by sign-in (AuthRepository.login).
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
                                        // Session already published by the
                                        // client's atomicLogin inside
                                        // AuthRepository.login. Declaration
                                        // fires on the CONTROLLER's scope,
                                        // deliberately not this pane's: the
                                        // ConnectedCard swap that this success
                                        // triggers disposes the pane scope and
                                        // would kill an in-flight POST.
                                        controller.declareCapabilitiesAfterSignIn()
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

// The probe/sign-in failure taxonomy (isLikelyCorsOrTransport +
// friendlyProbeFailure/friendlySignInFailure) lives in WebConnectFailurePolicy.kt
// — same package, internal visibility, pinned by WebConnectFailurePolicyTest.
