package com.raulshma.jellyplay.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.feature.settings.ConnectionProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource

/**
 * Seerr credentials orchestration for the web shell — the web
 * counterpart of `SeerrSettingsViewModel`, cut down to what a browser can
 * actually do: server URL + API key entry. The ViewModel's cookie login
 * paths (Jellyfin/LOCAL) are deliberately ABSENT, not merely hidden: a
 * browser tab cannot set the `Cookie` request header (fetch-forbidden) nor
 * read `Set-Cookie`, so cookie credentials can never function here (see
 * SeerrWireSupport's WASM BROWSER CAVEAT and Main.kt's SEERR-ON-WEB
 * HONESTY). API-key mode is the only web-viable auth, and since the
 * key persists across reloads via [LocalStorageSecureKeyValueStorage].
 *
 * STATE CORE: the probe status lives on the SHARED machine
 * (`shared/feature/settings` [ConnectionProbe] — the same board behind
 * `SeerrSettingsViewModel`, *arr settings, and the subtitle providers), not
 * on a pane-local state machine. One key ([WebSeerrProbeRequest]s all map to
 * [Unit] — a single connection), details = the server's version string. The
 * machine owns the status algebra (Idle → Testing → Connected/Error), the
 * refusal pre-flight (the former hand-rolled blank-credential guards are the
 * machine's `refused` lambda, mapping to the localized
 * `settings_probe_server_url_required` / `settings_probe_api_key_required`
 * fallbacks — the English literals they used to duplicate), and the failure
 * text policy (server messages render verbatim, null messages and crashes
 * degrade to the localized `settings_connection_failed` /
 * `settings_probe_unexpected_error` fallbacks).
 *
 * PINNED CALL ORDER (the contract [WebSeerrControllerTest] pins event by
 * event): persist FIRST (setServerUrl → setAuthMethod(API_KEY) →
 * setEnabled(true) → setApiKey — byte-identical to the former hand-rolled
 * `persist`, and the API-key slice of
 * `SeerrSettingsViewModel.testApiKeyConnection`), THEN
 * `seerrRepository.testApiKeyConnection()` — the repository re-reads both
 * stores on every call (hash-cached), so the just-persisted values are what
 * the test uses, no restart needed. The order lives in exactly one place:
 * the probe's [action][probeConnection]; the machine wraps everything
 * around it.
 *
 * SINGLE-FLIGHT, DECLARED: the probe is constructed with
 * [ConnectionProbe.SingleFlight.CALLER_GATED] — the pane's buttons-disabled
 * UX is the honest web call for a two-field pane, so the CALLER owns probe
 * concurrency and the machine neither cancels nor restarts an in-flight
 * test. That is the deliberate opposite arm of the ViewModel's
 * `launchTest` RESTART discipline (rapid re-taps must not queue restarts
 * into a pane whose Test button is disabled the whole time a test runs).
 * The machine's safety net still holds: only the currently registered job
 * may land an outcome, so even a caller bug cannot double-land.
 *
 * SIDE-EFFECT OWNERSHIP: same rule as [WebConnectController] — probe jobs,
 * save and disconnect all run on this controller's own page-lifetime scope
 * ([WebSideEffectScope], SupervisorJob + Dispatchers.Default) so a pane
 * navigation can never orphan an in-flight test or an in-flight
 * DataStore/localStorage write. Storage failures degrade silently to
 * session-only (the localStorage adapters already degrade internally; these
 * catches cover the store plumbing itself).
 */
internal class WebSeerrController(
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val secureCredentialsStore: SeerrSecureCredentialsStore,
    private val seerrRepository: SeerrRepository,
) {
    // Post-click work that must OUTLIVE the pane (see SIDE-EFFECT OWNERSHIP).
    // Same lifetime discipline as WebConnectController.sideEffectScope — the
    // shared shape lives in [WebSideEffectScope]. The probe board runs its
    // jobs here too: an in-flight test survives pane navigation exactly like
    // a save write does.
    private val sideEffectScope = WebSideEffectScope()

    /** Field-seeding snapshot read by [WebSeerrPane]'s hydration effect. */
    data class CredsState(val serverUrl: String, val apiKey: String)

    /** Connected details: the Overseerr/Jellyseerr version string (may be blank). */
    data class ConnectionDetails(val version: String)

    /**
     * The one probe request the web pane can issue: API-key credentials. The
     * former hand-rolled blank guards are the machine's synchronous refusal
     * pre-flight, in the same order the old guards ran (URL before key) and
     * backed by the SAME localized texts the Seerr ViewModel's refusals use —
     * no web-local probe-taxonomy strings.
     */
    private data class WebSeerrProbeRequest(val serverUrl: String, val apiKey: String) {
        fun refusal(): ConnectionProbe.FallbackText? = when {
            serverUrl.isBlank() -> ConnectionProbe.FallbackText.ServerUrlRequired
            apiKey.isBlank() -> ConnectionProbe.FallbackText.ApiKeyRequired
            else -> null
        }
    }

    /**
     * The shared status board, reduced to the pane's single key (the
     * `SeerrSettingsViewModel.connectionStatus` derivation). Status writes
     * land on the page-lifetime scope; refusals settle synchronously inside
     * [testConnection].
     */
    private val connectionProbe = ConnectionProbe<WebSeerrProbeRequest, Unit, ConnectionDetails>(
        scope = sideEffectScope.coroutineScope,
        keyOf = { _ -> Unit },
        refused = { it.refusal() },
        singleFlight = ConnectionProbe.SingleFlight.CALLER_GATED,
        action = ::probeConnection,
    )

    /** The pane's status seal: Idle → Testing → Connected([ConnectionDetails]) / Error. */
    val connectionStatus: StateFlow<ConnectionProbe.Status<ConnectionDetails>> =
        connectionProbe.status
            .map { it[Unit] ?: ConnectionProbe.Status.Idle }
            .stateIn(sideEffectScope.coroutineScope, SharingStarted.Eagerly, ConnectionProbe.Status.Idle)

    /**
     * Reads the persisted server URL + API key for field seeding. Every read
     * is degraded individually so a broken store yields an empty field, not
     * a crash.
     */
    suspend fun hydrate(): CredsState {
        val serverUrl = try {
            seerrPreferencesStore.preferences.first().serverUrl
        } catch (_: Exception) {
            ""
        }
        val apiKey = try {
            secureCredentialsStore.getApiKey()
        } catch (_: Exception) {
            ""
        }
        return CredsState(serverUrl = serverUrl, apiKey = apiKey)
    }

    /**
     * Persists the credential pair on the page-lifetime scope; fire-and-forget.
     * Failures degrade silently (session-only persistence).
     */
    fun saveLater(serverUrl: String, apiKey: String) {
        sideEffectScope.launchDegrading { persist(serverUrl, apiKey) }
    }

    /**
     * Test-connection, the probe-machine way: fire-and-forget. Refusals
     * (blank credentials) settle synchronously before this returns — no
     * Testing frame, no repository call, no store write. An accepted request
     * lands its outcome on [connectionStatus] (persist-then-test, see the
     * class KDoc for the pinned order).
     */
    fun testConnection(serverUrl: String, apiKey: String) {
        connectionProbe.probe(WebSeerrProbeRequest(serverUrl, apiKey))
    }

    /**
     * Drops the probe status (back to Idle) and cancels any in-flight probe —
     * the pane's field-edit clear (the former `statusLine = null`) and the
     * Save/Disconnect line replacement.
     */
    fun resetProbeStatus() {
        connectionProbe.reset(Unit)
    }

    /**
     * The probe action: persist-then-test, the ONE home of the pinned order.
     * The write sequence is byte-identical to the former hand-rolled
     * [persist]; the repository fold maps verbatim server messages to
     * [ConnectionProbe.Failure.Reported] and null messages to the localized
     * ConnectionFailed fallback (the former `error.message ?: "Connection
     * failed"` literal, now the machine's). A wrapped [CancellationException]
     * still propagates (never lands as Error); a thrown non-cancellation
     * crash degrades to the localized UnexpectedError fallback by the
     * machine's declared policy — the alignment the Seerr ViewModel accepted
     * in the same migration (the old web catch surfaced `e.message`).
     */
    private suspend fun probeConnection(
        request: WebSeerrProbeRequest,
    ): ConnectionProbe.Outcome<ConnectionDetails> {
        persist(request.serverUrl, request.apiKey)
        return seerrRepository.testApiKeyConnection().fold(
            onSuccess = { ConnectionProbe.Outcome.Reachable(ConnectionDetails(it.version)) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                ConnectionProbe.unreachable(error.message)
            },
        )
    }

    /**
     * Clears Seerr configuration + credentials on the page-lifetime scope via
     * the preference store's own [SeerrPreferencesStore.disconnect] — which
     * also empties the secure store (`clearAll`) — mirroring
     * `SeerrSettingsViewModel.disconnect`'s store-level reset. Fire-and-forget.
     */
    fun disconnectLater() {
        sideEffectScope.launchDegrading {
            try {
                seerrPreferencesStore.disconnect()
            } catch (_: Exception) {
                // Degrade: fields already cleared in the pane; retry on next edit.
            }
        }
    }

    /** The awaited write behind [saveLater]/[probeConnection]; degrade on failure. */
    private suspend fun persist(serverUrl: String, apiKey: String) {
        try {
            seerrPreferencesStore.setServerUrl(serverUrl)
            seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.API_KEY)
            seerrPreferencesStore.setEnabled(true)
            secureCredentialsStore.setApiKey(apiKey)
        } catch (_: Exception) {
            // Storage unavailable/quota: keep the UI usable; persistence lost.
        }
    }
}

/**
 * Seerr credentials pane: the first place web users can make the
 * requests feature work — server URL + API key, persist + test + disconnect.
 * All feedback is plain inline Text (no Scaffold/snackbar host; window.alert
 * is banned — same rules as WebConnectFlow), and every control is
 * AX-visible (Text/Button/textbox roles) because the E2E lane
 * (tools/e2e/web-verify.mjs) drives the app through the accessibility tree.
 * The visible "Server URL"/"API Key" header Texts above the fields are
 * deliberate: Compose does not expose OutlinedTextField labels in the AX
 * tree, so the lane anchors on these StaticTexts + field geometry.
 *
 * STATUS LINE: derives from the controller's shared probe board — the
 * spinner + disabled controls are [ConnectionProbe.Status.Testing], and the
 * line itself is the board's verdict. The "Test failed: " prefix is the
 * web-local presentation wrapper, kept byte-stable because the E2E lane
 * anchors its honest-failure assertion on it; the text AFTER it resolves
 * through the machine's taxonomy (a reported server message verbatim, a
 * declared fallback localized via the settings module's settings_probe_*
 * resources). "Saved"/"Disconnected" stay web-local (a local `note`, shown
 * only while the board is Idle) and are NOT probe-taxonomy strings.
 *
 * Layout level matches WebStatusPane (centered column, one surfaceVariant
 * card, explicit Back button through the shell's guarded pop path).
 */
@Composable
internal fun WebSeerrPane(
    onBack: () -> Unit,
    controller: WebSeerrController,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    // Web-local note for the non-probe actions (Save/Disconnect). Anything
    // the shared board has a verdict for renders from the board instead.
    var note by remember { mutableStateOf<String?>(null) }
    val probeStatus by controller.connectionStatus.collectAsState()
    val testing = probeStatus is ConnectionProbe.Status.Testing

    // The probe-board half of the status line: Connected (web-local label +
    // the version detail) or Error ("Test failed: " + taxonomy-resolved
    // text). Testing/Idle render no line.
    val probeLine = when (val status = probeStatus) {
        is ConnectionProbe.Status.Connected ->
            // Web-local label: the settings module's localized "Connected"
            // (settings_connected) is NOT part of the probe failure taxonomy
            // this migration targets, and its generated Res object is
            // internal to the settings module — unreachable from here. The
            // word is deliberately NOT one of the localized fallback texts.
            if (status.details.version.isNotBlank()) {
                "Connected v${status.details.version}"
            } else {
                "Connected."
            }
        is ConnectionProbe.Status.Error ->
            "Test failed: " + when (val failure = status.failure) {
                is ConnectionProbe.Failure.Reported -> failure.message
                is ConnectionProbe.Failure.Declared -> stringResource(failure.text.resource())
            }
        ConnectionProbe.Status.Idle, ConnectionProbe.Status.Testing -> null
    }
    val statusLine = probeLine ?: note
    val statusIsError = probeStatus is ConnectionProbe.Status.Error

    // Seed the fields from the persisted stores once (reload rehydration —
    // the point of the localStorage-backed secure store).
    LaunchedEffect(controller) {
        val state = controller.hydrate()
        serverUrl = state.serverUrl
        apiKey = state.apiKey
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Seerr settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.padding(top = 16.dp).width(480.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Overseerr / Jellyseerr credentials",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Cookie sign-in cannot work in a browser; the API key is the only " +
                        "usable Seerr credential here. It is saved in this browser's local " +
                        "storage and survives reloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Server URL",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        controller.resetProbeStatus()
                        note = null
                    },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://localhost:5055") },
                    singleLine = true,
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "API Key",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        controller.resetProbeStatus()
                        note = null
                    },
                    label = { Text("API Key") },
                    singleLine = true,
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                )
                statusLine?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (testing) CircularProgressIndicator(Modifier.height(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Button(
                        enabled = !testing,
                        onClick = {
                            note = null
                            controller.testConnection(serverUrl, apiKey)
                        },
                    ) {
                        Text("Test connection")
                    }
                    Button(
                        enabled = !testing,
                        onClick = {
                            controller.saveLater(serverUrl, apiKey)
                            controller.resetProbeStatus()
                            note = "Saved"
                        },
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        enabled = !testing,
                        onClick = {
                            controller.disconnectLater()
                            serverUrl = ""
                            apiKey = ""
                            controller.resetProbeStatus()
                            note = "Disconnected"
                        },
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Back")
        }
    }
}
