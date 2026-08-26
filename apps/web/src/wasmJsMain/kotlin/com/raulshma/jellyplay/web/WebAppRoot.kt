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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.events.Event

/**
 * Web-only route keys (wave 11B web-nav v1): apps/web keeps its OWN tiny
 * table instead of consuming the shared Route sealed class — no feature
 * module has a wasm target yet, so the shared leaves have nothing to render
 * into. One push/pop level ([WebLanding] to [WebStatus]) suffices to prove
 * navigation + back end-to-end through the JB fork's wasm NavDisplay klib.
 *
 * Deliberately NOT @Serializable and NOT persisted: the back stack is a
 * memory-only [SnapshotStateList] (no rememberNavBackStack/SavedState
 * configuration), so a page reload restarts on the landing pane. Web-shell
 * v1 has no deep links; browser-history integration is deferred with the
 * JellyPlayBackHandler cut below.
 */
private data object WebLanding : NavKey

private data object WebStatus : NavKey

/**
 * Web nav root (wave 11B, spike w-10C §5): NavDisplay from the JB fork's
 * navigation3-ui wasm klib over the shared core/ui primitives — the first
 * real shared-UI composition in the browser shell. Mirrors the desktop
 * shell's shape (DesktopNavScaffold) minus rail/tabs/Koin-driven sections:
 * v1 carries exactly the two private panes above.
 *
 * Composition-local provisioning follows DesktopAppRoot's precedent, with
 * one upgrade on network status: instead of desktop's static Online flow,
 * [rememberBrowserConnectivityStatus] maps `navigator.onLine` into
 * [NetworkStatus] live via the window 'online'/'offline' events, so shared
 * screens' offline-banner reads become genuinely meaningful here. The
 * NetworkStatus.Local value stays unreachable through this provider for now
 * — navigator.onLine cannot distinguish LAN-only connectivity.
 *
 * Server health stays the static Unknown StateFlow exactly like desktop
 * (DesktopAppRoot provisions MutableStateFlow(ServerHealth.Unknown)): no
 * real-server pass exists yet, and no fake states go on screen.
 *
 * BACK/HISTORY honesty (spike w-10C §8, kept as documented cut): back works
 * via NavDisplay's own mechanisms once pushed routes exist — pop happens
 * when [NavDisplay]'s onBack fires and via the explicit Back affordance in
 * [WebStatusPane]. Deeper integration with browser history stays deferred:
 * the wasm actual of core/ui's JellyPlayBackHandler is a documented inert
 * no-op because honest pushState/popState bookkeeping per stack entry needs
 * its own pass; nothing on this pane tree registers a hardware/back callback
 * today, so no dead registration hangs off the shell either.
 *
 * RUNTIME HONESTY (same rule as Main.kt/HtmlVideoEngine): compile-level
 * proof only (:apps:web:compileKotlinWasmJs). No headless browser lane
 * exists in this repo, so an actual push/pop click-through and the live
 * 'online'/'offline' event flips remain unverified until a real-browser
 * pass lands.
 */
@Composable
fun WebAppRoot(sessionState: AtomicSessionState) {
    val networkStatus = rememberBrowserConnectivityStatus()
    // Static provisioning exactly as desktop does it (DesktopNavScaffold):
    // nothing probes a real Jellyfin host this wave.
    val serverHealth = remember { MutableStateFlow(ServerHealth.Unknown) }
    val backStack = remember { mutableStateListOf<NavKey>(WebLanding) }

    val entryProvider = remember(sessionState) {
        entryProvider<NavKey> {
            entry<WebLanding> { _ ->
                WebLandingPane(
                    sessionState = sessionState,
                    onOpenStatus = { backStack.add(WebStatus) },
                )
            }
            entry<WebStatus> { _ ->
                WebStatusPane(onBack = { backStack.removeLastOrNull() })
            }
        }
    }

    CompositionLocalProvider(
        LocalNetworkStatus provides networkStatus,
        LocalServerHealth provides serverHealth,
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
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
 * `(Event) -> Unit` lambda shape already used by HtmlVideoEngine. Listeners
 * are never removed — the web shell lives for the whole page lifetime, so
 * there is no disposal point to match.
 */
@Composable
private fun rememberBrowserConnectivityStatus() = remember {
    val status = MutableStateFlow(currentBrowserNetworkStatus())
    val onBrowserEvent: (Event) -> Unit = { status.value = currentBrowserNetworkStatus() }
    window.addEventListener("online", onBrowserEvent)
    window.addEventListener("offline", onBrowserEvent)
    status
}

/**
 * Landing pane: the Phase W placeholder content (session readout card over
 * the shared datastore/network stacks) plus the push affordance that opens
 * the status level.
 */
@Composable
private fun WebLandingPane(
    sessionState: AtomicSessionState,
    onOpenStatus: () -> Unit,
) {
    val server by sessionState.currentServer.collectAsState()
    val user by sessionState.currentUser.collectAsState()

    val sessionLine = when {
        server != null && user != null ->
            "Connected to ${server?.name} as ${user?.name}."
        server != null ->
            "Server ${server?.name}, not signed in."
        else ->
            "No server connected (auth UI is a later slice)."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "JellyPlay",
            style = MaterialTheme.typography.displayMedium,
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
                    text = "Network stack ready — $sessionLine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Preferences persist to localStorage via the shared datastore stack.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onOpenStatus, modifier = Modifier.padding(top = 16.dp)) {
            Text("Connection status")
        }
    }
}

/**
 * Status pane: consumes the app-level composition locals for real — the
 * connection line renders whatever the browser event wiring currently holds,
 * the health line renders the static Unknown provisioning. Explicit Back
 * pops the nav stack (see the root KDoc for why this button, not browser
 * history, owns back in v1).
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
        ServerHealth.Unknown -> "Not probed — no real-server pass yet."
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
