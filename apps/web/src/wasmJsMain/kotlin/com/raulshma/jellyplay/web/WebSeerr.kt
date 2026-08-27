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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Seerr credentials orchestration for the web shell (wave 16B) — the web
 * counterpart of `SeerrSettingsViewModel`, cut down to what a browser can
 * actually do: server URL + API key entry. The ViewModel's cookie login
 * paths (Jellyfin/LOCAL) are deliberately ABSENT, not merely hidden: a
 * browser tab cannot set the `Cookie` request header (fetch-forbidden) nor
 * read `Set-Cookie`, so cookie credentials can never function here (see
 * SeerrWireSupport's WASM BROWSER CAVEAT and Main.kt's SEERR-ON-WEB
 * HONESTY). API-key mode is the only web-viable auth, and since wave 16B the
 * key persists across reloads via [LocalStorageSecureKeyValueStorage].
 *
 * Call order mirrors `SeerrSettingsViewModel.testApiKeyConnection` exactly:
 * PERSIST FIRST (setServerUrl → setAuthMethod(API_KEY) → setApiKey; the web
 * pane additionally setEnabled(true) so saving is enough to arm the
 * requests feature), THEN call `seerrRepository.testApiKeyConnection()` —
 * the repository re-reads both stores on every call (hash-cached), so the
 * just-persisted values are what the test uses, no restart needed.
 *
 * SIDE-EFFECT OWNERSHIP: same rule as [WebConnectController] — save and
 * disconnect run on this controller's own [sideEffectScope] (SupervisorJob +
 * Dispatchers.Default, page lifetime) so a pane navigation can never orphan
 * an in-flight DataStore/localStorage write. Storage failures degrade
 * silently to session-only (the localStorage adapters already degrade
 * internally; these catches cover the store plumbing itself).
 *
 * DELIBERATE DEVIATION from the desktop mirror: there is no test-in-flight
 * job-cancels-previous machinery ([SeerrSettingsViewModel.launchTest]) —
 * the pane disables its buttons while a test runs, which is sufficient for
 * a single-field pane and keeps the web shell's controller plain.
 */
internal class WebSeerrController(
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val secureCredentialsStore: SeerrSecureCredentialsStore,
    private val seerrRepository: SeerrRepository,
) {
    // Post-click work that must OUTLIVE the pane (see SIDE-EFFECT OWNERSHIP).
    // Same lifetime discipline as WebConnectController.sideEffectScope.
    private val sideEffectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Field-seeding snapshot read by [WebSeerrPane]'s hydration effect. */
    data class CredsState(val serverUrl: String, val apiKey: String)

    /** Outcome of a connection test, for the pane's status line. */
    sealed interface TestOutcome {
        /** Success; [version] is the Overseerr/Jellyseerr version string (may be blank). */
        data class Connected(val version: String) : TestOutcome

        /** Failure; [message] is the repository/client error text. */
        data class Failed(val message: String) : TestOutcome
    }

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
     * Persists the credential pair on [sideEffectScope]; fire-and-forget.
     * Failures degrade silently (session-only persistence).
     */
    fun saveLater(serverUrl: String, apiKey: String) {
        sideEffectScope.launch { persist(serverUrl, apiKey) }
    }

    /**
     * Persist-then-test (order mirrors `SeerrSettingsViewModel.testApiKeyConnection`):
     * the repository resolves URL + key from the stores at call time, so
     * they must be written before `testApiKeyConnection` runs. Returns
     * [TestOutcome.Connected] with the server's version string, or
     * [TestOutcome.Failed] with the error message.
     */
    suspend fun testConnection(serverUrl: String, apiKey: String): TestOutcome {
        if (serverUrl.isBlank()) return TestOutcome.Failed("Server URL is required")
        if (apiKey.isBlank()) return TestOutcome.Failed("API key is required")
        persist(serverUrl, apiKey)
        return try {
            seerrRepository.testApiKeyConnection().fold(
                onSuccess = { TestOutcome.Connected(it.version) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    TestOutcome.Failed(error.message ?: "Connection failed")
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TestOutcome.Failed(e.message ?: "Unexpected error occurred")
        }
    }

    /**
     * Clears Seerr configuration + credentials on [sideEffectScope] via the
     * preference store's own [SeerrPreferencesStore.disconnect] — which also
     * empties the secure store (`clearAll`) — mirroring
     * `SeerrSettingsViewModel.disconnect`'s store-level reset. Fire-and-forget.
     */
    fun disconnectLater() {
        sideEffectScope.launch {
            try {
                seerrPreferencesStore.disconnect()
            } catch (_: Exception) {
                // Degrade: fields already cleared in the pane; retry on next edit.
            }
        }
    }

    /** The awaited write behind [saveLater]/[testConnection]; degrade on failure. */
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
 * Seerr credentials pane (wave 16B): the first place web users can make the
 * requests feature work — server URL + API key, persist + test + disconnect.
 * All feedback is plain inline Text (no Scaffold/snackbar host; window.alert
 * is banned — same rules as WebConnectFlow), and every control is
 * AX-visible (Text/Button/textbox roles) because the E2E lane
 * (tools/e2e/web-verify.mjs) drives the app through the accessibility tree.
 * The visible "Server URL"/"API Key" header Texts above the fields are
 * deliberate: Compose does not expose OutlinedTextField labels in the AX
 * tree, so the lane anchors on these StaticTexts + field geometry.
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
    var statusLine by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                        statusLine = null
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
                        statusLine = null
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
                            testing = true
                            statusLine = null
                            scope.launch {
                                val outcome = controller.testConnection(serverUrl, apiKey)
                                testing = false
                                when (outcome) {
                                    is WebSeerrController.TestOutcome.Connected -> {
                                        statusLine = if (outcome.version.isNotBlank()) {
                                            "Connected v${outcome.version}"
                                        } else {
                                            "Connected."
                                        }
                                        statusIsError = false
                                    }
                                    is WebSeerrController.TestOutcome.Failed -> {
                                        statusLine = "Test failed: ${outcome.message}"
                                        statusIsError = true
                                    }
                                }
                            }
                        },
                    ) {
                        Text("Test connection")
                    }
                    Button(
                        enabled = !testing,
                        onClick = {
                            controller.saveLater(serverUrl, apiKey)
                            statusLine = "Saved"
                            statusIsError = false
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
                            statusLine = "Disconnected"
                            statusIsError = false
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
