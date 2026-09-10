package com.raulshma.jellyplay.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalWebBackDispatcher
import com.raulshma.jellyplay.core.ui.components.WebBackDispatcher
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.calendar.UpcomingCalendarScreen
import com.raulshma.jellyplay.feature.details.SeerrDetailScreen
import com.raulshma.jellyplay.feature.requests.RequestsScreen
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.events.Event

/**
 * Web-only route keys (web-nav v1): apps/web keeps its OWN tiny
 * table of web-only leaves alongside the SHARED Route sealed class. The web
 * entries render web-only panes ([WebLanding] carries the connect/sign-in
 * flow ([WebConnectFlow]), [WebStatus] the connection-details level,
 * [WebDiag] the diagnostics level); adds the first SHARED route —
 * `entry<Route.Requests>` renders the feature module's RequestsScreen —
 * which is exactly why the shared keys remain usable here without being
 * registered in this private table (the private objects exist because no
 * shared web pane exists for them).
 *
 * Deliberately NOT @Serializable and NOT persisted: the back stack is a
 * memory-only [SnapshotStateList] (no rememberNavBackStack/SavedState
 * configuration — NavKeySerializer's reflective persistence is the
 * Android/jvm saveable path; the web shell never saves), so a page reload
 * restarts on the landing pane — and a surviving "#wp=N" address bar is
 * rewritten down to "#wp=0" at boot via history.replaceState (see the
 * RELOAD bullet of [WebBackStackMirror]'s model notes for the exact
 * post-reload contract). There are no deep links; browser-history
 * integration below mirrors DEPTH only, not entry identity/arguments.
 */
private data object WebLanding : NavKey

private data object WebStatus : NavKey

/**
 *  diagnostics level ([WebDiagnosticsPane]): gated E2E surface for
 * the Coil artwork + HtmlVideoEngine browser passes. Same lifetime rules as
 * [WebStatus] — memory-only, no deep link.
 */
private data object WebDiag : NavKey

/**
 *  Seerr credentials level ([WebSeerrPane]): server URL + API key
 * entry/persist/test/disconnect — the pane that finally lets the requests
 * feature work on web (API-key mode is the only browser-viable Seerr auth).
 * Same lifetime rules as [WebStatus]/[WebDiag] — memory-only, no deep link;
 * the CREDENTIALS themselves persist (localStorage-backed secure store),
 * so a reload lands back on the landing pane but the Seerr config survives.
 */
private data object WebSeerr : NavKey

/**
 * Web nav root (slice 2 over the web-nav v1): NavDisplay from
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
 * BROWSER-HISTORY MODEL (supersedes the "deferred" cut): the
 * snapshot list IS the single owner of truth; history MIRRORS it. The whole
 * rule set — dispatch-first pops, root-refuse, reload hash normalization,
 * forward-onto-pruned walk-back — lives on [WebBackStackMirror] as a pure
 * decision core (its KDoc is the model notes; WebBackStackMirrorTest pins
 * it browser-free). THIS composable keeps only the two halves the pure core
 * cannot own: the memory-only [SnapshotStateList] (trimmed per the
 * reconcile decisions) and the window.history adapter translating
 * [WebHistoryCommand]s into pushState/replaceState/back/go calls. The
 * dispatch wiring rides [LocalWebBackDispatcher]'s [WebBackDispatcher]
 * (core/ui's seam): registrant count TODAY: zero — none of the
 * wasm-composed screens (Requests, UpcomingCalendar, SeerrDetail, the
 * web-only panes) calls JellyPlayBackHandler; they keep explicit
 * affordances. The wiring is thus exercised only through its no-op arm
 * (dispatchBack() returns false) and exists for the first shared screen
 * that registers (core/ui's modal sheet / preview overlay are the natural
 * first users).
 *
 * RUNTIME HONESTY (same rule as Main.kt/HtmlVideoEngine): the shell's own
 * panes are browser-verified by the headless-Edge CDP lane
 * (tools/e2e/web-verify.mjs — connect/sign-in, Connectivity flips are NOT
 * flipped in-lane, pushState/popstate round-trips are exercised only as far
 * as the lane's Back click).  extends the lane one level further:
 * after the diagnostics pane it pops back and opens the FIRST feature
 * screen, Route.Requests → shared RequestsScreen, asserting the filter bar
 * + the honest "Seerr not configured" error state with zero console errors
 * (honest at that lane point: no credentials saved yet — the 16B Seerr
 * pane opens later in the lane — and session-cookie auth stays
 * browser-impossible; see Main.kt). A later lane
 * extends it once more: back from Requests, open Route.UpcomingCalendar →
 * shared UpcomingCalendarScreen, asserting the honest feature-disabled pane
 * (the DIRECT_ARR_INTEGRATION flag boots off and no web settings UI can
 * flip it).
 */
@Composable
fun WebAppRoot(
    sessionState: AtomicSessionState,
    authApiClient: AuthApiClient,
    userPrefs: DataStore<Preferences>,
    seerrPreferencesStore: SeerrPreferencesStore,
    seerrSecureCredentialsStore: SeerrSecureCredentialsStore,
    seerrRepository: SeerrRepository,
    bootRoute: NavKey? = null,
    bootVariant: String? = null,
) {
    // GATED E2E INPUT PROBE (`?e2eRoute=inputprobe[&variant=scroll]`):
    // render ONLY the probe lattice and return — no NavDisplay, no session
    // gate, no browser-history wiring. The check sits BEFORE any remember{}
    // below so the probe pane composes in complete isolation from the shell
    // (that isolation is the experiment's point); bootRoute/bootVariant are
    // boot-URL constants, so the early return cannot deshape any state.
    // Humans can never reach this branch (no user surface sets the param).
    if (bootRoute === WebInputProbe) {
        WebInputProbePane(scrollable = bootVariant == "scroll")
        return
    }
    val networkStatus = rememberBrowserConnectivityStatus()
    // Collected once here: the landing card's chip/lines must recompose on
    // 'online'/'offline' flips even though they live inside NavDisplay panes
    // that receive the raw value.
    val currentNetworkStatus by networkStatus.collectAsState()
    // Static provisioning exactly as desktop does it (DesktopNavScaffold):
    // nothing probes a real Jellyfin host for HEALTH this slice.
    val serverHealth = remember { MutableStateFlow(ServerHealth.Unknown) }
    // GATED E2E BOOT ROUTE (desktop `jellyplay.harness.*` prop precedent):
    // [bootRoute] seeds the stack one level deep so the CDP lane can reach a
    // shared-feature route without depending on synthetic mouse-click
    // GEOMETRY. The clean-room probe (tools/e2e/input-probe.mjs +
    // docs/e2e/web-input-dead-region.md) found NO Compose input dead region:
    // synthetic clicks deliver everywhere inside the viewport (measured to
    // y=803.5 of an 805px viewport, at device scale 1 and 1.5). The earlier
    // "dead region below y≈600" report is attributed to a
    // SeerrDetailViewModel construction crash freezing composition after the
    // demo-button click LANDED (plus headless geometry: --window-size height
    // 900 is an 805px viewport, and below-fold boxes zero out at (0,0)).
    // The boot param STAYS as lane hygiene: a lane that never needs click
    // coordinates cannot regress with them. Humans use the demo button; the
    // lane boots straight into the route. Real navigation never sets the
    // query param, so the flag has no user-facing effect.
    val backStack = remember {
        mutableStateListOf<NavKey>(WebLanding).apply { bootRoute?.let { add(it) } }
    }

    val webBackDispatcher = remember { WebBackDispatcher() }
    val connectController = remember(sessionState, authApiClient, userPrefs) {
        WebConnectController(auth = authApiClient, userPrefs = userPrefs)
    }
    //THE SEERR CREDENTIALS CONTROLLER, BUILT EXACTLY LIKE
    // [WebConnectController] — plain class, Koin-resolved deps passed in from
    // Main.kt (SeerrPreferencesStore/SeerrSecureCredentialsStore are unnamed
    // singles in datastoreCommonModule/webDatastoreModule, SeerrRepository in
    // dataWasmModule), one page-lifetime instance.
    val seerrController = remember(seerrPreferencesStore, seerrSecureCredentialsStore, seerrRepository) {
        WebSeerrController(
            seerrPreferencesStore = seerrPreferencesStore,
            secureCredentialsStore = seerrSecureCredentialsStore,
            seerrRepository = seerrRepository,
        )
    }

    // ── Browser-history adapter over the pure WebBackStackMirror core ───────
    // The mirror's decision core and its model notes live in WebBackStackMirror;
    // these closures own the two halves it cannot: the SnapshotStateList and
    // window.history itself.

    // Trims the stack to depth [depth] (keeping exactly depth+1 entries) when
    // it currently runs deeper — the downward reconcile of a browser-initiated
    // back. No-ops when already compliant, including at the root.
    fun trimToDepth(depth: Int) {
        while (backStack.size > depth + 1) backStack.removeAt(backStack.lastIndex)
    }

    // The window.history half: the command vocabulary's ENTIRE DOM surface.
    // pushState/replaceState signatures carry JS-interop types requiring the
    // wasm opt-in (same as HtmlVideoEngine's usage).
    @OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
    fun applyCommand(command: WebHistoryCommand) {
        when (command) {
            is WebHistoryCommand.Push -> window.history.pushState(null, "", command.hash)
            is WebHistoryCommand.Rewrite -> window.history.replaceState(null, "", command.hash)
            WebHistoryCommand.NavigateBack -> window.history.back()
            is WebHistoryCommand.GoTo -> window.history.go(command.delta)
            WebHistoryCommand.None -> Unit
        }
    }

    // THE pop path (dispatch-first, then the root-refusing guarded
    // pop — WebBackStackMirror.requestPop owns the ordering). Local trim
    // before the cursor move so the UI never waits on the async history turn.
    fun requestPop() {
        val pressConsumed = webBackDispatcher.dispatchBack()
        WebBackStackMirror.requestPop(backStack.size, pressConsumed)?.let { command ->
            backStack.removeAt(backStack.lastIndex)
            applyCommand(command)
        }
    }

    // Browser-initiated Back/Forward reconciliation against the live list
    // (see WebBackStackMirror.reconcilePopState — the full rule set).
    fun onPopState(@Suppress("UNUSED_PARAMETER") event: Event) {
        val reconcile = WebBackStackMirror.reconcilePopState(window.location.hash, backStack.size)
        reconcile.trimToDepth?.let(::trimToDepth)
        applyCommand(reconcile.command)
    }

    // Once-per-composition browser wiring: the 'popstate' listener rides a
    // DisposableEffect (DOM events fire on the single JS main thread), so the
    // hooks release correctly even if WebAppRoot ever gains a non-root caller.
    DisposableEffect(window) {
        applyCommand(WebBackStackMirror.normalizeBootHash(window.location.hash, backStack.size))
        val listener: (Event) -> Unit = ::onPopState
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }

    fun addEntry(key: NavKey) {
        backStack.add(key)
        applyCommand(WebBackStackMirror.onEntryAdded(backStack.lastIndex))
    }

    val entryProvider = remember(sessionState, authApiClient, userPrefs, seerrPreferencesStore, seerrSecureCredentialsStore, seerrRepository) {
        entryProvider<NavKey> {
            entry<WebLanding> { _ ->
                WebConnectFlow(
                    controller = connectController,
                    networkStatus = currentNetworkStatus,
                    onOpenConnectionDetails = { addEntry(WebStatus) },
                    onOpenDiagnostics = { addEntry(WebDiag) },
                    //THE SHARED FEATURE ROUTE — PUSHED AS ITSELF,
                    // NOT as a web-only mirror key (see the route-keys KDoc).
                    onOpenRequests = { addEntry(Route.Requests) },
                    //THE SECOND SHARED FEATURE ROUTE, SAME SHAPE.
                    onOpenCalendar = { addEntry(Route.UpcomingCalendar) },
                    //THE SEERR CREDENTIALS PANE.
                    onOpenSeerr = { addEntry(WebSeerr) },
                )
            }
            entry<WebStatus> { _ ->
                WebStatusPane(onBack = ::requestPop)
            }
            entry<WebDiag> { _ ->
                WebDiagnosticsPane(
                    onBack = ::requestPop,
                    //  E2E surface: pushes the SeerrDetail screen for a
                    // FIXED demo key (tmdb 550, "movie") so the headless lane
                    // can drive the real shared screen without a Seerr server
                    // (the requests list is empty in the fixture — nothing is
                    // clickable there). See WebDiagnosticsPane's button KDoc.
                    onOpenSeerrDetailDemo = { addEntry(Route.SeerrDetail(550, "movie")) },
                )
            }
            entry<WebSeerr> { _ ->
                WebSeerrPane(onBack = ::requestPop, controller = seerrController)
            }
            entry<Route.Requests> { _ ->
                //THE FIRST SHARED FEATURE SCREEN ON WEB. THE SHELL
                // (Main.kt → ProvideWebShellViewModelOwners) provides the
                // ViewModelStoreOwner/LifecycleOwner koinViewModel() needs, so
                // the screen composes bare — there is deliberately no wrapper
                // here; requests' own ProvidePlatformLocalsFallback is
                // `internal` to that module (invisible from apps/web), which
                // structurally keeps ONE provisioning truth at the shell.
                //
                //THE SEERRDETAIL CUT STUB IS GONE —
                // onNavigateToDetail now pushes the real shared route, exactly
                // like requests' RequestsNavigation does on android/desktop
                // (`navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))`).
                // mediaType arrives verbatim from the Seerr wire model
                // ("movie"/"tv"; RequestDetailBottomSheet forwards
                // request.type) and SeerrDetailScreen compares
                // case-insensitively, so the pass-through needs no mapping.
                // Reachability in the fixture: only from a populated request
                // list — impossible without a Seerr server — so the lane
                // boots into the screen via the gated e2eRoute param instead
                // (see parseE2eBootRoute in Main.kt).
                //
                // onBack rides the SAME guarded pop path as every other pane
                // (requestPop: root-refusing list trim + history.back()).
                RequestsScreen(
                    onBack = ::requestPop,
                    onNavigateToDetail = { tmdbId, mediaType ->
                        addEntry(Route.SeerrDetail(tmdbId, mediaType))
                    },
                )
            }
            entry<Route.UpcomingCalendar> { _ ->
                //THE SECOND SHARED FEATURE SCREEN ON WEB — THE
                // shared UpcomingCalendarScreen (koinViewModel() against
                // calendarModule, registered in Main.kt this wave). The
                // feature-disabled pane is the honest v1 state in the browser
                // fixture: the DIRECT_ARR_INTEGRATION experimental flag boots
                // off and no settings UI exists on web to flip it, so the E2E
                // lane asserts the disabled pane (see web-verify.mjs).
                //
                // ARR-SETTINGS CUT (documented at the only site that could
                // navigate there): onOpenArrSettings would push Route
                // .ArrSettings — feature/settings has no wasmJs target
                // (documented), so the callback is a NO-OP stub. Reachability
                // of the stub: the Open-*arr-Settings button renders only in
                // the disabled pane; until a settings target lands the button
                // is inert on web. It becomes addEntry(Route.ArrSettings())
                // when settings gains the web target.
                //
                // onItemClick is REAL since Route.SeerrDetail landed
                // on web (coordinator merge): calendar rows forward
                // (tmdbId, mediaType) verbatim, same pass-through the
                // requests entry uses. Unreachable in the fixture (the flag
                // is off), but no longer a dead click by construction.
                //
                // onBack rides the SAME guarded pop path as every other pane
                // (requestPop: root-refusing list trim + history.back()).
                UpcomingCalendarScreen(
                    onBack = ::requestPop,
                    onOpenArrSettings = {
                        // No-op — settings has no wasm target (see the cut
                        // note above); nothing navigates.
                    },
                    onItemClick = { tmdbId, mediaType ->
                        addEntry(Route.SeerrDetail(tmdbId, mediaType))
                    },
                )
            }
            entry<Route.SeerrDetail> { key ->
                //THE SECOND SHARED FEATURE SCREEN ON WEB. SAME BARE
                // composition + shell-provided owners as the requests entry.
                //
                // - onBack rides the guarded pop path (requestPop).
                // - onNavigate is a NO-OP STUB by platform rule: the screen's
                //   cross-links target Route.MediaDetail (and friends), and
                //   the MediaDetail cluster has NO wasmJs target — it is
                //   jvmShared/off-web this wave (Room-blocked; see
                //   shared/feature/details/build.gradle.kts). Nothing on web
                //   can render it, so the callback deliberately does nothing
                //   rather than pushing an unroutable key. (No snackbar — the
                //   shell has no Scaffold host; window.alert is banned.)
                // - The shell wraps the screen in a Back row because the
                //   screen's own back affordance lives inside the loaded
                //   content; in the honest "Seerr not configured" error state
                //   (the only reachable one in the fixture) only ErrorScreen's
                //   Retry exists.
                // - LocalUriHandler is provisioned HERE (shell-owned platform
                //   locals, same pattern as LocalNetworkStatus below): the
                //   screen reads it for trailer embed-failure fallbacks, and
                //   the ComposeViewport root provisions no UriHandler.
                WebShellBackScaffold(onBack = ::requestPop) {
                    SeerrDetailScreen(
                        tmdbId = key.tmdbId,
                        mediaType = key.mediaType,
                        onBack = ::requestPop,
                        onNavigate = { _ ->
                            // MediaDetail cluster is off-web (no wasmJs
                            // target) — documented dead-click, see above.
                        },
                    )
                }
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
 * Shell-owned chrome for shared feature screens whose own back affordance can
 * disappear with their content state (SeerrDetail's lives inside the loaded
 * body; in the honest "Seerr not configured" error state only ErrorScreen's
 * Retry remains). The explicit Back routes through the guarded pop path.
 *
 * Also provisions [LocalUriHandler] with the browser implementation: the
 * ComposeViewport root provisions none, and the shared screen reads it
 * unconditionally (trailer embed-failure fallback → openUri). Shell-owned
 * platform locals are the same pattern as LocalNetworkStatus below; desktop's
 * Window does the equivalent provisioning internally.
 */
@Composable
private fun WebShellBackScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUriHandler provides remember { WebShellUriHandler() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = onBack,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            ) {
                Text("Back")
            }
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

/** Browser [UriHandler]: new tab (trailer links are external YouTube URLs). */
private class WebShellUriHandler : UriHandler {
    override fun openUri(uri: String) {
        window.open(uri, "_blank")
    }
}

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
 * Connection-details pane (an update of the status pane):
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
