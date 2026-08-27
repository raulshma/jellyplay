package com.raulshma.jellyplay.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalWebBackDispatcher
import com.raulshma.jellyplay.core.ui.components.WebBackDispatcher
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.events.Event

/**
 * Web-only route keys (wave 11B web-nav v1): apps/web keeps its OWN tiny
 * table instead of consuming the shared Route sealed class — no feature
 * module has a wasm target yet, so the shared leaves have nothing to render
 * into. [WebLanding] carries the connect/sign-in flow ([WebConnectFlow]),
 * [WebStatus] the connection-details level it pushes into.
 *
 * Deliberately NOT @Serializable and NOT persisted: the back stack is a
 * memory-only [SnapshotStateList] (no rememberNavBackStack/SavedState
 * configuration), so a page reload restarts on the landing pane — and a
 * surviving "#wp=N" address bar is rewritten down to "#wp=0" at boot via
 * history.replaceState (see the RELOAD bullet of [WebAppRoot]'s model notes
 * for the exact post-reload contract). There are no deep links;
 * browser-history integration below mirrors DEPTH only, not entry
 * identity/arguments.
 */
private data object WebLanding : NavKey

private data object WebStatus : NavKey

/**
 * Wave 13C diagnostics level ([WebDiagnosticsPane]): gated E2E surface for
 * the Coil artwork + HtmlVideoEngine browser passes. Same lifetime rules as
 * [WebStatus] — memory-only, no deep link.
 */
private data object WebDiag : NavKey

/** Location-hash prefix carrying the mirrored stack depth: "#wp=<index>". */
private const val HISTORY_HASH_PREFIX = "#wp="

/**
 * Parses a location hash in the "#wp=<index>" form into its stack
 * index. Returns null for anything that is not one of our entries (an empty
 * hash on the initial page load, or foreign fragments) — callers treat null
 * as depth 0.
 */
private fun historyHashToIndex(hash: String): Int? =
    if (hash.startsWith(HISTORY_HASH_PREFIX)) {
        hash.removePrefix(HISTORY_HASH_PREFIX).toIntOrNull()
    } else {
        null
    }

/**
 * Web nav root (wave 12C slice 2 over wave 11B's web-nav v1): NavDisplay from
 * the JB fork's navigation3-ui wasm klib over the shared core/ui primitives,
 * with the landing level grown from placeholder text into the real
 * connect/sign-in flow ([WebConnectFlow] driving [KtorWasmAuthApiClient]
 * through [WebConnectController] directly — there is no AuthRepository and no
 * core:data on wasm).
 *
 * Composition-local provisioning follows DesktopAppRoot's precedent:
 * [LocalNetworkStatus] maps `navigator.onLine` into [NetworkStatus] live via
 * the window 'online'/'offline' events; server health stays the static
 * Unknown StateFlow exactly like desktop (no real-server health pass exists).
 *
 * SERVER HEALTH: still static Unknown by design this slice — health probing
 * is not part of connect/auth browsing status.
 *
 * BROWSER-HISTORY MODEL (wave 12C — supersedes the "deferred" cut):
 * the snapshot list IS the single owner of truth; history MIRRORS it.
 *  - Push (`addEntry`): append locally, then pushState with the hash
 *    "#wp=<newTopIndex>". History entries carry NO state payload (empty
 *    object would need JS interop gymnastics; the hash encodes the same fact
 *    and survives reload-free navigation equally well).
 *  - Pop — the ACTIVE pop paths (the explicit Back button and
 *    NavDisplay.onBack) go through [requestPop]: mutate the list FIRST (UI
 *    stays correct even if the popstate event never arrives), then
 *    history.back() so the browser cursor follows. core/ui's
 *    JellyPlayBackHandler registrations DO land in [LocalWebBackDispatcher],
 *    but nothing invokes WebBackDispatcher.dispatchBack yet — v1 has zero
 *    registrants (screens keep explicit affordances), so no path can bypass
 *    the guard today; if dispatchBack ever gains callers they must route
 *    through the same trimming, never a raw history.back(). That enforcement
 *    is NOT wired — this doc states the gap instead of assuming it away.
 *  - RELOAD mid-stack: composition restarts on the landing pane whatever
 *    "#wp=N" survives in the address bar, and the boot effect below rewrites
 *    THAT CURRENT entry down to "#wp=0" via history.replaceState (no new
 *    history slot) so the mirror matches the restarted stack. Deeper
 *    pre-reload entries further along the session trail keep their stale
 *    hashes; surfacing one via Back/Forward simply re-runs reconciliation
 *    against the LIVE list, so an old hash can never talk the shell into a
 *    stack shape it did not choose itself.
 *  - Browser-initiated Back/Forward fires 'popstate'; the handler reconciles
 *    the list DOWNWARD to the hashed depth (a trim — dropping panes whose
 *    state was never persisted) and treats a missing/foreign hash as depth 0.
 *  - Forward onto a pruned level walks the cursor BACK to our top
 *    (history.go with a negative delta): panes dropped by an earlier local
 *    trim are never resurrected, so each Forward press past our real top
 *    permanently BURNS those ghost slots in this tab's session trail
 *    (accepted v1 walk-back contract).
 *
 * The root-refuse guard rides [requestPop] only, refusing at size <= 1 (an
 * emptied stack crashes NavDisplay), so the explicit Back button goes inert
 * at the root instead of popping the shell off the page.
 *
 * RUNTIME HONESTY (same rule as Main.kt/HtmlVideoEngine): compile-level proof
 * only (:apps:web:compileKotlinWasmJs). No headless browser lane exists in
 * this repo, so an actual click-through, the live 'online'/'offline' flips,
 * AND the pushState/popstate round-trips remain unverified until a
 * real-browser pass lands. Not click-tested; stated explicitly rather than
 * implied otherwise.
 */
@Composable
fun WebAppRoot(
    sessionState: AtomicSessionState,
    authApiClient: AuthApiClient,
    userPrefs: DataStore<Preferences>,
) {
    val networkStatus = rememberBrowserConnectivityStatus()
    // Collected once here: the landing card's chip/lines must recompose on
    // 'online'/'offline' flips even though they live inside NavDisplay panes
    // that receive the raw value.
    val currentNetworkStatus by networkStatus.collectAsState()
    // Static provisioning exactly as desktop does it (DesktopNavScaffold):
    // nothing probes a real Jellyfin host for HEALTH this slice.
    val serverHealth = remember { MutableStateFlow(ServerHealth.Unknown) }
    val backStack = remember { mutableStateListOf<NavKey>(WebLanding) }

    val webBackDispatcher = remember { WebBackDispatcher() }
    val connectController = remember(sessionState, authApiClient, userPrefs) {
        WebConnectController(auth = authApiClient, userPrefs = userPrefs)
    }

    // Trims the stack to depth [depth] (keeping exactly depth+1 entries) when
    // it currently runs deeper — the downward reconcile of a browser-initiated
    // back. No-ops when already compliant, including at the root.
    fun trimToDepth(depth: Int) {
        while (backStack.size > depth + 1) backStack.removeAt(backStack.lastIndex)
    }

    // pushState's signature carries JS-interop types requiring the wasm
    // opt-in (same as HtmlVideoEngine's usage).
    @OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
    fun pushHistoryMirror() {
        window.history.pushState(null, "", "$HISTORY_HASH_PREFIX${backStack.lastIndex}")
    }

    // Reload mid-stack lands here with a stale "#wp=N" in the address bar
    // while the restarted stack holds only the landing pane — the address bar
    // must stop disagreeing (see the RELOAD bullet in the class KDoc). rewrite
    // the CURRENT entry only: replaceState, no new history slot. Stale hashes
    // on DEEPER pre-reload entries are left alone on purpose; every popstate
    // arrival is judged against the live list, never against stored hashes.
    @OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
    fun normalizeBootHashMirror() {
        val bootIndex = historyHashToIndex(window.location.hash)
        if (bootIndex != null && bootIndex != 0) {
            window.history.replaceState(null, "", "$HISTORY_HASH_PREFIX${backStack.lastIndex}")
        }
    }

    // THE pop path. Root-refuse guard first (see class KDoc); local trim
    // before the cursor move so the UI never waits on the async history turn.
    fun requestPop() {
        if (backStack.size <= 1) return
        backStack.removeAt(backStack.lastIndex)
        window.history.back()
    }

    // Browser-initiated Back/Forward reconciliation (see model above).
    fun onPopState(@Suppress("UNUSED_PARAMETER") event: Event) {
        val targetIndex = historyHashToIndex(window.location.hash)
        if (targetIndex == null || targetIndex <= 0) {
            trimToDepth(0)
            return
        }
        if (targetIndex < backStack.size) {
            trimToDepth(targetIndex)
        } else {
            // Forward onto a pruned level: walk the cursor back to our top.
            window.history.go(backStack.lastIndex - targetIndex)
        }
    }

    // Once-per-composition browser wiring: the 'popstate' listener rides a
    // DisposableEffect (DOM events fire on the single JS main thread), so the
    // hooks release correctly even if WebAppRoot ever gains a non-root caller.
    DisposableEffect(window) {
        normalizeBootHashMirror()
        val listener: (Event) -> Unit = ::onPopState
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }

    fun addEntry(key: NavKey) {
        backStack.add(key)
        pushHistoryMirror()
    }

    val entryProvider = remember(sessionState, authApiClient, userPrefs) {
        entryProvider<NavKey> {
            entry<WebLanding> { _ ->
                WebConnectFlow(
                    controller = connectController,
                    networkStatus = currentNetworkStatus,
                    onOpenConnectionDetails = { addEntry(WebStatus) },
                    onOpenDiagnostics = { addEntry(WebDiag) },
                )
            }
            entry<WebStatus> { _ ->
                WebStatusPane(onBack = ::requestPop)
            }
            entry<WebDiag> { _ ->
                WebDiagnosticsPane(onBack = ::requestPop)
            }
        }
    }

    CompositionLocalProvider(
        LocalNetworkStatus provides networkStatus,
        LocalServerHealth provides serverHealth,
        LocalWebBackDispatcher provides webBackDispatcher,
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = ::requestPop,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider,
        )
    }
}

/** Read the current browser-reported connectivity at call time. */
private fun currentBrowserNetworkStatus(): NetworkStatus =
    if (window.navigator.onLine) NetworkStatus.Online else NetworkStatus.Offline

/**
 * MutableStateFlow-backed [LocalNetworkStatus] provider seeded from
 * `navigator.onLine` and updated by the window 'online'/'offline' events.
 *
 * API surface proven against the kotlinx-browser 0.5.0 wasm klib: Window
 * exposes `navigator: Navigator`, NavigatorOnLine carries `onLine: Boolean`
 * (org.w3c.dom package), and window/event-target addEventListener takes the
 * `(Event) -> Unit` lambda shape already used by HtmlVideoEngine. Listener
 * removal rides DisposableEffect (DOM events fire on the single JS main
 * thread; no isolate concern), so WebAppRoot stays correct if it ever gains
 * a non-root caller — today it is the immortal page root.
 */
@Composable
private fun rememberBrowserConnectivityStatus(): MutableStateFlow<NetworkStatus> {
    val status = remember { MutableStateFlow(currentBrowserNetworkStatus()) }
    DisposableEffect(window) {
        val onBrowserEvent: (Event) -> Unit = { status.value = currentBrowserNetworkStatus() }
        window.addEventListener("online", onBrowserEvent)
        window.addEventListener("offline", onBrowserEvent)
        onDispose {
            window.removeEventListener("online", onBrowserEvent)
            window.removeEventListener("offline", onBrowserEvent)
        }
    }
    return status
}

/**
 * Connection-details pane (wave 12C update of wave 11B's status pane):
 * connectivity line from the app-level composition locals over the static
 * Unknown health provisioning, unchanged from v1. Session/server facts live
 * in the landing card ([WebConnectFlow]); this level stays reachable via
 * "Connection details" only while connected, and its explicit Back routes
 * through the shared guarded pop path (list trim + history.back()).
 */
@Composable
private fun WebStatusPane(onBack: () -> Unit) {
    val networkStatusFlow = LocalNetworkStatus.current
    val networkStatus by networkStatusFlow.collectAsState()
    val serverHealthFlow = LocalServerHealth.current
    val health by serverHealthFlow.collectAsState()

    val connectionLine = when {
        networkStatus.isOnline -> "Online (browser reports connectivity)."
        networkStatus.isOffline -> "Offline (browser reports no connectivity)."
        else -> "LAN-only connectivity is not distinguishable via navigator.onLine."
    }
    // Local val capture: delegated State values do not smart-cast.
    val healthValue = health
    val healthLine = when (healthValue) {
        ServerHealth.Unknown -> "Not probed — no health pass yet."
        ServerHealth.Checking -> "Checking…"
        is ServerHealth.Healthy -> "Healthy (${healthValue.latencyMs} ms)." // unreachable v1
        ServerHealth.Unreachable -> "Unreachable." // unreachable v1
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connection status",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = connectionLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (networkStatus.hasNetwork) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = healthLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Shared core/ui primitive ride-along: the expressive wavy
                // indicator stands in place of the probe UI that does not
                // exist yet (health line above says exactly that).
                JellyPlayLinearProgressIndicator(Modifier.padding(top = 8.dp))
            }
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Back")
        }
    }
}
