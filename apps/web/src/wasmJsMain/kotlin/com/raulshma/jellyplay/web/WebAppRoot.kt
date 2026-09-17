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
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalWebBackDispatcher
import com.raulshma.jellyplay.core.ui.components.WebBackDispatcher
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.arrqueue.ArrQueueScreen
import com.raulshma.jellyplay.feature.calendar.UpcomingCalendarScreen
import com.raulshma.jellyplay.feature.details.SeerrDetailScreen
import com.raulshma.jellyplay.feature.onboarding.OnboardingScreen
import com.raulshma.jellyplay.feature.requests.RequestsScreen
import com.raulshma.jellyplay.feature.settings.ArrSettingsScreen
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
 * connect/sign-in flow ([WebConnectFlow] driving the shared [AuthRepository]
 * — `WasmAuthRepository` via dataWasmModule — through
 * [WebConnectController]; the landing no longer drives the raw Ktor wasm
 * auth client itself).
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
    authRepository: AuthRepository,
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
    val connectController = remember(authRepository, userPrefs) {
        WebConnectController(authRepository = authRepository, userPrefs = userPrefs)
    }
    // The Seerr credentials controller, built exactly like
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

    val entryProvider = remember(authRepository, userPrefs, seerrPreferencesStore, seerrSecureCredentialsStore, seerrRepository) {
        // ── THE PANE TABLE ────────────────────────────────────────────────
        // The one list the shell's twin derivations walk (the web-local fold
        // of the hand-mirror class the desktop ShellSectionRegistry killed;
        // the shared appSections adoption stays a recorded deferral — NOT
        // this). [buildWebPanes] declares one row per level this nav root can
        // push AS A LANDING AFFORDANCE, in landing button order, and BOTH
        // consumers derive from those rows:
        //   - `entry<WebLanding>` projects each row through
        //     [WebPane.toLandingAffordance] (the buttons), and
        //   - [registerWebPanes] derives each row's entry registration,
        //     threading the guarded pop path in as every pane's `onBack`.
        // Adding a level = adding one row; button and registration cannot
        // drift (WebPaneTableTest pins the derived set).
        val webPanes = buildWebPanes(
            seerrController = { seerrController },
            addEntry = ::addEntry,
        )
        entryProvider<NavKey> {
            entry<WebLanding> { _ ->
                // The landing affordances DERIVE from [webPanes] — the rows
                // ARE the optionality contract: every level this nav root can
                // push appears as one button, in table order (e2e-verified by
                // tools/e2e/web-verify.mjs via accessible name). The shared
                // feature routes are pushed AS THEMSELVES, NOT as web-only
                // mirror keys (see the route-keys KDoc); Requests renders the
                // honest "Seerr not configured" error state until Seerr
                // credentials exist (Main.kt's SEERR-ON-WEB HONESTY note),
                // Calendar/ArrQueue render the honest feature-disabled panes
                // (DIRECT_ARR_INTEGRATION boots off), and completing
                // onboarding pops back here (no persisted first-run gate on
                // web).
                WebConnectFlow(
                    controller = connectController,
                    networkStatus = currentNetworkStatus,
                    affordances = webPanes.map { it.toLandingAffordance(onOpen = ::addEntry) },
                )
            }
            // One registration pass over the same rows; `onBack` is provided
            // HERE, once — every table pane receives the guarded pop path
            // (requestPop: dispatch-first, root-refusing list trim +
            // history.back()) through its render closure instead of
            // hand-writing `onBack = ::requestPop` per entry.
            registerWebPanes(scope = this, panes = webPanes, onBack = ::requestPop)
            entry<Route.ArrSettings> { _ ->
                // The FIFTH shared feature screen on web — the direct
                // *arr integration settings, the FIRST settings-family route
                // with a fully wasm-resolvable dependency closure
                // (ArrRepository + ArrPreferencesStore +
                // ArrSecureCredentialsStore — see Main.kt's settingsModule
                // note for everything that stays latent on web). Reachability
                // matches desktop: the Open-*arr-Settings buttons in the
                // calendar/arrqueue feature-disabled panes (both wired in the
                // pane table), NOT a settings-root row — web has no settings
                // root: the wasm AuthRepository binding EXISTS now
                // (dataWasmModule's WasmAuthRepository), but the settings
                // root's VM closure needs more than the repository
                // (SettingsBackupIo/AppMetaProvider/LogCollector have no
                // wasm actuals — see Main.kt's settingsModule note), so
                // Route.Settings stays unrouted and this entry is reached
                // by pushing the real key, not through any web-native
                // mirror. Bare composition +
                // shell-provided owners like every shared entry; onBack rides
                // the SAME guarded pop path as every other pane (requestPop:
                // root-refusing list trim + history.back()). The screen
                // assumes the DIRECT_ARR_INTEGRATION gate ran at the caller —
                // both caller panes render only while the flag is off, which
                // is exactly the desktop affordance.
                ArrSettingsScreen(onBack = ::requestPop)
            }
            entry<Route.SeerrDetail> { key ->
                // The SECOND shared feature screen on web. Same bare
                // composition + shell-provided owners as the requests entry.
                //
                // - onBack rides the guarded pop path (requestPop).
                // - onNavigate is a NO-OP STUB by platform rule: the screen's
                //   cross-links target Route.MediaDetail (and friends), and
                //   the MediaDetail cluster has NO wasmJs target — it is
                //   jvmShared/off-web for now (Room-blocked; see
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

/**
 * One row of the web shell's PANE TABLE — a level this nav root can push AS A
 * LANDING AFFORDANCE. The row is the whole declaration: the landing button's
 * [label] (load-bearing copy — tools/e2e/web-verify.mjs finds these buttons by
 * accessible name), the [isOutlined] flag marking secondary tooling, the
 * [key] pushed when the button opens the level, and the pane's [content]
 * render closure (receives the resolved key and the shell's guarded pop path
 * as `onBack`, provided once by [registerWebPanes]).
 *
 * Rows carry BOTH consumer halves of the former twin lists, so the
 * landing-vs-entry lockstep is structural, not a discipline: the landing pane
 * projects rows through [toLandingAffordance], and [registerWebPanes] derives
 * the `entry` registrations from the same rows (WebPaneTableTest pins the
 * derived set). Deliberately NOT rows: [WebLanding] itself (the root — it
 * RENDERS the table as buttons) and Route.ArrSettings / Route.SeerrDetail
 * (pushed programmatically from other panes, never landing buttons — their
 * entries stay hand-written in [WebAppRoot]).
 *
 * @param K the concrete NavKey type of the pane; registration derives the
 *   entry's KClass from [key] (`key::class`), so a data-object key registers
 *   exactly like the former reified `entry<WebX>` blocks did.
 */
internal class WebPane<K : NavKey>(
    val label: String,
    val isOutlined: Boolean = false,
    val key: K,
    val content: @Composable (key: K, onBack: () -> Unit) -> Unit,
)

/**
 * THE pane table — the single source the web shell's twin derivations walk
 * (the web-local fold of the hand-mirror class the desktop
 * ShellSectionRegistry killed; adopting the shared appSections machinery
 * stays a recorded deferral and is NOT this). One row per level this nav root
 * can push as a landing affordance, in landing button order:
 *
 *  - `entry<WebLanding>` projects each row through [WebPane.toLandingAffordance]
 *    — the rows ARE the optionality contract ("every level this nav root can
 *    push appears as one button"), e2e-verified via accessible name;
 *  - [registerWebPanes] derives each row's entry registration and threads the
 *    guarded pop path in as `onBack` once.
 *
 * Adding a level = adding one row here; button and registration cannot drift.
 *
 * [seerrController] is deferred to render time (the Seerr row's content
 * closure dereferences it only when [WebSeerrPane] actually composes) so the
 * registration test can build this table without a controller instance —
 * pane content is never invoked outside composition.
 */
internal fun buildWebPanes(
    seerrController: () -> WebSeerrController,
    addEntry: (NavKey) -> Unit,
): List<WebPane<*>> = listOf(
    WebPane("Connection details", key = WebStatus) { _, onBack ->
        WebStatusPane(onBack)
    },
    WebPane("Requests", key = Route.Requests) { _, onBack ->
        // The FIRST shared feature screen on web — bare composition +
        // shell-provided owners (Main.kt → ProvideWebShellViewModelOwners);
        // requests' own ProvidePlatformLocalsFallback is `internal` to that
        // module (invisible from apps/web), which structurally keeps ONE
        // provisioning truth at the shell. onNavigateToDetail pushes the REAL
        // shared route, exactly like requests' RequestsNavigation does on
        // android/desktop; mediaType arrives verbatim from the Seerr wire
        // model ("movie"/"tv"; RequestDetailBottomSheet forwards request.type)
        // and SeerrDetailScreen compares case-insensitively, so the
        // pass-through needs no mapping. Reachability of the detail push in
        // the fixture: only from a populated request list — impossible
        // without a Seerr server — so the lane boots into the screen via the
        // gated e2eRoute param instead (see parseE2eBootRoute in Main.kt).
        RequestsScreen(
            onBack = onBack,
            onNavigateToDetail = { tmdbId, mediaType -> addEntry(Route.SeerrDetail(tmdbId, mediaType)) },
        )
    },
    WebPane("Calendar", key = Route.UpcomingCalendar) { _, onBack ->
        // The SECOND shared feature screen on web. The feature-disabled pane
        // is the honest v1 state in the browser fixture: the
        // DIRECT_ARR_INTEGRATION experimental flag boots off and no web
        // surface can flip it (the E2E lane asserts the disabled pane — see
        // web-verify.mjs). ARR-SETTINGS, LIVE SINCE: onOpenArrSettings pushes
        // the REAL shared route (feature/settings' wasmJs target landed; the
        // screen resolves fully on web — see Main.kt's settingsModule note).
        // onItemClick is REAL since Route.SeerrDetail landed on web:
        // calendar rows forward (tmdbId, mediaType) verbatim, same
        // pass-through the requests row uses. Unreachable in the fixture (the
        // flag is off), but no longer a dead click by construction.
        UpcomingCalendarScreen(
            onBack = onBack,
            onOpenArrSettings = { addEntry(Route.ArrSettings()) },
            onItemClick = { tmdbId, mediaType -> addEntry(Route.SeerrDetail(tmdbId, mediaType)) },
        )
    },
    // The Seerr credentials pane — the entry that makes the requests feature
    // usable on web (API-key creds are the only browser-viable Seerr auth).
    WebPane("Seerr", key = WebSeerr) { _, onBack ->
        WebSeerrPane(onBack = onBack, controller = seerrController())
    },
    WebPane("Arr queue", key = Route.ArrQueue) { _, onBack ->
        // The THIRD shared feature screen on web — bare composition +
        // shell-provided owners like every shared row. onOpenArrSettings is
        // LIVE since (the settings wasmJs target landed): it pushes the real
        // Route.ArrSettings, same wiring as the calendar row — the button
        // renders in the feature-disabled pane, matching desktop
        // reachability.
        ArrQueueScreen(
            onBack = onBack,
            onOpenArrSettings = { addEntry(Route.ArrSettings()) },
        )
    },
    WebPane("Onboarding", key = Route.Onboarding) { _, onBack ->
        // The FOURTH shared feature screen on web — the onboarding wizard.
        // Web has NO persisted first-run gate (nothing boots into it; web
        // sessions start at the landing), so the wizard is reachable only
        // from the ConnectedCard button, and onComplete just POPS back to the
        // landing — there is no "done" destination to push. The wizard's
        // SeerrStep writes into the localStorage-backed Seerr credential
        // store (webDatastoreModule), consistent with the web honesty
        // carve-outs.
        OnboardingScreen(onComplete = onBack)
    },
    // GATED E2E hook into WebDiagnosticsPane — outlined so it reads as
    // secondary tooling next to the primary feature actions.
    WebPane("Diagnostics", isOutlined = true, key = WebDiag) { _, onBack ->
        // E2E surface: pushes the SeerrDetail screen for a FIXED demo key
        // (tmdb 550, "movie") so the headless lane can drive the real shared
        // screen without a Seerr server (the requests list is empty in the
        // fixture — nothing is clickable there). See WebDiagnosticsPane's
        // button KDoc.
        WebDiagnosticsPane(
            onBack = onBack,
            onOpenSeerrDetailDemo = { addEntry(Route.SeerrDetail(550, "movie")) },
        )
    },
)

/**
 * The landing half of the pane-table derivation: projects a row onto its
 * landing button ([WebLandingAffordance]) — label + outline verbatim, and an
 * `onOpen` that pushes the row's [WebPane.key] through [onOpen] (the shell's
 * addEntry). The button vocabulary cannot drift from the registrations because
 * both come from the same rows.
 */
internal fun WebPane<*>.toLandingAffordance(onOpen: (NavKey) -> Unit): WebLandingAffordance =
    WebLandingAffordance(
        label = label,
        isOutlined = isOutlined,
        onOpen = { onOpen(key) },
    )

/**
 * The registration half of the pane-table derivation: registers every row
 * into [scope], exactly where the former seven hand-written `entry<…>` blocks
 * stood. Uses the scope's class-keyed [EntryProviderScope.addEntryProvider]
 * overload — the non-reified form the reified `entry<WebX>` DSL delegates to —
 * so resolution semantics (clazzProviders lookup keyed on `key::class`,
 * default `contentKey = key.toString()`) are identical to the hand-written
 * entries; no reflection.
 *
 * [onBack] is the shell's guarded pop path (requestPop), provided ONCE here
 * and threaded into every row's render closure — no pane hand-writes it.
 */
internal fun registerWebPanes(
    scope: EntryProviderScope<NavKey>,
    panes: List<WebPane<*>>,
    onBack: () -> Unit,
) {
    panes.forEach { pane -> registerWebPane(scope, pane, onBack) }
}

/** One row's registration ([registerWebPanes] per-row body; generic so the
 *  key class and the render closure's key type stay aligned without casts). */
private fun <K : NavKey> registerWebPane(
    scope: EntryProviderScope<NavKey>,
    pane: WebPane<K>,
    onBack: () -> Unit,
) {
    scope.addEntryProvider(
        clazz = pane.key::class,
        content = { key: K -> pane.content(key, onBack) },
    )
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
