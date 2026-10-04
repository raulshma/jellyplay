package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.ComposeWindow
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.adaptive.applyOverride
import com.raulshma.jellyplay.core.ui.adaptive.rememberAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rememberJellyPlayUiEnvironment
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalPullToRefreshRegistry
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.PullToRefreshRegistry
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.navKey
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.navigation.visibleTopLevelRoutes
import com.raulshma.jellyplay.desktop.harness.DesktopSessionHarness
import com.raulshma.jellyplay.desktop.player.MpvSoftwareSurfaceSupport
import com.raulshma.jellyplay.feature.player.video.DesktopPlayerKeyBridge
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import com.raulshma.jellyplay.feature.shell.UserMessageDuration
import com.raulshma.jellyplay.feature.shell.navigation.ShellAdminHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellAudioSource
import com.raulshma.jellyplay.feature.shell.navigation.ShellHomeHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellSearchHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellSettingsHooks
import com.raulshma.jellyplay.feature.shell.navigation.shellEntryProvider
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellAdminGate
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellAudioClicks
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellHost
import com.raulshma.jellyplay.feature.shell.rememberShellUserMessages
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The nav scaffold: rail on the left, NavDisplay on the right, snackbar for
 * the dead-end guard. One back stack per top-level route (the phone app's
 * tab pattern), Esc / Alt+Left mapped to [Navigator.goBack].
 *
 * The scaffold's SERVICE WIRING lives in [rememberDesktopShellServices] —
 * the remembered [DesktopShellServices] holder owning construction +
 * collection of the session controller (ADR 0001), the About update check
 * (ADR desktop-auto-update), the dead-end guarded navigator + section
 * registry, the user-message source list, the remote-nav collector and the
 * idle-ambient controller; this composable reads them through the holder's
 * properties and keeps only the chrome: rail, NavDisplay, snackbar surface,
 * key handling, the idle overlay's rendering. ALL of this composition's
 * Koin reads ride the holder too — this composition carries no Koin read of
 * its own
 * (only DesktopAppRoot's pre-scaffold window does, per the
 * composition-order contract on [rememberDesktopShellServices]).
 *
 * The scaffold's DECISIONS live in extracted, test-pinned top-level
 * declarations (the DesktopUpdateCheckController idiom):
 *  - the dead-end guard adapter: [desktopGuardedNavigator] /
 *    [desktopDeadEndMessage] (constructed by [DesktopShellServices]);
 *  - the first-run onboarding gate's one-shot read:
 *    [runDesktopOnboardingGateOnce] (the effect stays HERE — a one-shot
 *    navigation choreography, not a service);
 *  - the UserMessageHost source assembly: [desktopUserMessageSources]
 *    (assembled by [DesktopShellServices]); the host call below supplies
 *    only the snackbar present adapter;
 *  - the remote MoveFocus direction mapping: [composeFocusDirection]
 *    (DesktopRemoteNavigation.kt);
 *  - the idle ambient seam: [DesktopIdleAmbientController] (held by
 *    [DesktopShellServices]) + [resolveIdleOverlayIdentity] (the overlay's
 *    identity fold, collected HERE beside the overlay's rendering);
 *  - the About update row: DesktopUpdateCheckController (held by
 *    [DesktopShellServices]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DesktopNavScaffold(
    menuRefreshRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    // The ComposeWindow handle (Main.kt's AWT ref) — the remote
    // navigation ladder's select synthesis posts AWT key events through it.
    windowRef: AtomicReference<ComposeWindow?>? = null,
    //  open-with: Main.kt's pending link-open queue — the argv seed,
    // the Ctrl+Shift+V clipboard accelerator and the second-instance forward
    // watcher all feed it; the drain effect below is its ONE consumer.
    linkOpens: DesktopLinkOpenQueue? = null,
) {
    val navigation = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = DESKTOP_TOP_LEVEL_ROUTES,
        savedStateConfiguration = desktopNavSavedStateConfiguration(),
    )
    val snackbarHostState = remember { SnackbarHostState() }

    // File→Refresh / Ctrl+R dispatch (see DesktopAppRoot KDoc): pull-to-refresh
    // screens self-register into this registry via PullToRefreshBox while they
    // are composed and enabled, so a menu refresh lands on the active screen's
    // own onRefresh — Library, Live TV, Music, Admin, Home, all of them — with
    // the same spinner/forced-fetch semantics as the gesture. No registration
    // (e.g. Settings is on top) → nothing to refresh; dropped silently.
    val refreshRegistry = remember { PullToRefreshRegistry() }
    LaunchedEffect(menuRefreshRequests, refreshRegistry) {
        menuRefreshRequests.collect { refreshRegistry.refreshActive() }
    }

    //  session harness: publish the live back stack (nav3 is the
    // source of truth) so DesktopSessionHarness can push the player route and
    // assert pops after Esc injection. Provider form reads the CURRENT tab's
    // stack; attaching here is a lambda store, no behavior for normal boots.
    remember(navigation) {
        DesktopSessionHarness.attachBackStackProvider {
            navigation.backStacks[navigation.topLevelRoute.value]
        }
        true
    }

    // Wire the software-surface prober before any route guard reads
    // it (the Route.VideoPlayer entry registration below asks it while the
    // entry provider graph is built; the guard derives the same predicate
    // from the graph's ledger). The probe itself is lazy and cached
    // inside MpvSoftwareSurfaceSupport — the first VideoPlayer-guard read pays
    // the one-time libmpv/sw-context smoke test; any failure degrades to
    // "unsupported", never crashes boot. This remember deliberately stays in
    // the scaffold (not inside DesktopShellServices): the graph build below
    // is what reads the probe, and keeping probe-then-graph adjacent in ONE
    // composition makes that ordering impossible to break by moving the
    // holder call.
    remember {
        DesktopVideoSurfaceBridge.registerSoftwareSurfaceProbe {
            MpvSoftwareSurfaceSupport.isSupported
        }
        true
    }

    // App-level composition locals the shared screens read. Server health
    // stays a static Unknown — deliberate: the desktop shell performs no
    // server health polling, and only StudioDetailScreen reads
    // LocalServerHealth today.
    val serverHealth = remember { MutableStateFlow(ServerHealth.Unknown) }

    // ── the shell services (DesktopShellServices) ───────────────────
    // Everything between here and the graph build that constructs, collects
    // or guards a shell SERVICE — the ADR 0001 session controller, the About
    // update check, the dead-end guarded navigator + its section registry,
    // the user-message source list (receiver DisplayMessages included), the
    // remote-nav collector and the idle-ambient controller — is owned by
    // the holder; the reads below are the states this chrome renders.
    // ORDER: after the probe above, before the graph build at the bottom —
    // the graph attaches into the holder's registry (see
    // rememberDesktopShellServices's contract).
    val services = rememberDesktopShellServices(
        navigation = navigation,
        snackbarHostState = snackbarHostState,
        windowRef = windowRef,
    )
    val sessionController = services.sessionController
    val guardedNavigator = services.guardedNavigator
    val idleAmbientController = services.idleAmbientController
    val homeMode by sessionController.homeMode.collectAsState()
    // Bottom-nav customization (#152): the same NavigationStore the phone
    // settings write through drives which items this rail shows and in what
    // order (see the rail composition below).
    val navigationStore = services.navigationStore

    // Network status is LIVE: the flow comes from :core:data's
    // DesktopNetworkMonitor (desktopDataModule single — NetworkInterface
    // probing with a 15 s re-probe and a synchronous construction-time seed),
    // so offline banners now reflect real connectivity. Read through the
    // holder like every other Koin collaborator of this composition.
    val networkMonitor = services.networkMonitor

    // Live desktop audio core — the Home music pane's Now Playing / Ambient
    // cards read the current item + metadata from it (same source the tray
    // and title bar observe; flows are read at click time, not collected).
    // Exposed by the holder (its idle controller reads the same single).
    val audioQueueManager: DesktopAudioQueueManager = services.audioQueueManager

    // First-run onboarding gate: the persisted `onboarding_completed` flag
    // is read ONCE per scaffold composition — i.e. once per authenticated
    // session entry — and a not-yet-onboarded session gets the shared wizard
    // pushed (the same Route.Onboarding the Shortcuts entry and the settings
    // "rerun setup" row open). The read + dispatch live in
    // [runDesktopOnboardingGateOnce] (extracted; pinned by
    // DesktopOnboardingGateTest) — the one-shot isOnboardingCompleted() read
    // (not the eagerly-shared state flow, whose seed is all-defaults before
    // the prefs file lands) is what keeps an already-onboarded user from
    // seeing the wizard at every boot. Completion flows back through the
    // shared OnboardingViewModel — same pref on both platforms — so the gate
    // never re-fires for a completer.
    val appRuntimeStateStore = services.appRuntimeStateStore
    LaunchedEffect(appRuntimeStateStore) {
        runDesktopOnboardingGateOnce(
            readOnboardingCompleted = appRuntimeStateStore::isOnboardingCompleted,
            navigate = guardedNavigator::navigate,
        )
    }

    //  open-with drain: the ONE consumer of Main.kt's pending link-open
    // queue. Parsed targets route through the guarded navigator (top-level
    // destinations switch the tab; details push on the current stack) and a
    // parsed-but-unroutable target (SyncPlayJoin — the Discord Rich Presence
    // join payload, see DesktopLinkOpenPolicy.targetRoute) or a clipboard
    // miss surfaces as this scaffold's own snackbar, so the accelerator never
    // feels dead. Events that arrived BEFORE this composition (the argv seed
    // waiting out session restore) are delivered on the first pass — the
    // channel's replay is the point; the signed-out branch drops its events
    // instead (see DesktopAppRoot), so nothing stale replays across a
    // sign-in.
    LaunchedEffect(linkOpens, guardedNavigator) {
        linkOpens?.pending?.collect { event ->
            when (event) {
                is DesktopOpenLinkEvent.Link -> {
                    val route = DesktopLinkOpenPolicy.targetRoute(event.target)
                    if (route != null) {
                        guardedNavigator.navigate(route)
                    } else {
                        snackbarHostState.showSnackbar(DesktopLinkOpenMessages.NO_DESKTOP_ROUTE)
                    }
                }
                DesktopOpenLinkEvent.NoLinkFound ->
                    snackbarHostState.showSnackbar(DesktopLinkOpenMessages.NO_LINK_IN_CLIPBOARD)
            }
        }
    }

    // User-message host (the shared seam): ONE collector behind every message
    // source this shell shows — the shared [UserMessageBus] flow, the
    // DesktopMusicMessageBus relay AND the remote-control receiver's
    // server-pushed DisplayMessages, assembled in [desktopUserMessageSources]
    // (extracted; pinned by DesktopUserMessagesTest; constructed by
    // [DesktopShellServices]). The present adapter — the snackbar below with
    // withDismissAction, matching the Android collector this seam replaced —
    // is this shell's share; the severity→duration policy stays in the shared
    // module.
    val sharedUserMessageBus = services.sharedUserMessageBus
    rememberShellUserMessages(
        { text, duration ->
            snackbarHostState.showSnackbar(
                message = text,
                withDismissAction = true,
                duration = when (duration) {
                    UserMessageDuration.Short -> SnackbarDuration.Short
                    UserMessageDuration.Long -> SnackbarDuration.Long
                },
            )
        },
        *services.userMessageSources.toTypedArray(),
    )

    val currentTopLevel by navigation.topLevelRoute
    val backStack = checkNotNull(navigation.backStacks[currentTopLevel]) {
        "no back stack for top-level route $currentTopLevel"
    }

    // ── idle "Ready to play" ambient reads ──────────────────────────
    // The controller (monitor + idle-gated active-remote-session count),
    // its start/stop effect and the input-hook wiring below; the overlay's
    // identity lines follow.
    val isIdle by idleAmbientController.isIdle.collectAsState()
    val activeRemoteSessions by idleAmbientController.activeRemoteSessionCount.collectAsState()

    // The overlay's identity lines: current server + user (the two-key
    // server match folds in resolveIdleOverlayIdentity, pinned by its test).
    // ONE owner: the holder's authRepository (the same instance the session
    // controller wraps) — no Koin read of its own in the scaffold.
    val currentUser by services.authRepository.currentUser.collectAsState(initial = null)
    val servers by services.authRepository.servers.collectAsState(initial = emptyList())
    val idleOverlayIdentity = remember(currentUser, servers) {
        resolveIdleOverlayIdentity(currentUser, servers)
    }

    // Search-prefill channel (ShellHostHooks.pendingSearchQuery): desktop has
    // no intent/shared-text source to arm it yet — the channel is wired
    // explicitly (never a silent local default), so a future source only
    // sets this flow.
    val pendingSearchQuery = remember { MutableStateFlow<String?>(null) }

    // Shell-supplied surface behind the shared section graph (ShellHostHooks),
    // built through the shared rememberShellHost factory — the ONE
    // construction site for the hooks (Android's MainContent feeds the same
    // five group bundles; the bundle-by-bundle wiring lives there). Every
    // factory parameter is a remember key, so each bundle below may be built
    // fresh per recomposition (the groups are data classes and compare
    // structurally) as long as its members are remembered/stable (the
    // discipline the factory's KDoc states): the audio bundle comes from the
    // shared rememberShellAudioClicks over this shell's ShellAudioSource
    // adapter (click-time reads of the desktop audio core —
    // DesktopAudioQueueManager — never collected values), and the session
    // bundles wrap the shared ShellSessionController the holder constructed —
    // the same values, same lazy reads the old inline entryProvider captured.
    // The graph below rebuilds only when these identities change (the
    // guarded navigator, homeMode, a DesktopShellServices rebuild re-issuing
    // them).
    val audioSource = remember(audioQueueManager) {
        DesktopQueueShellAudioSource(audioQueueManager)
    }
    val audioClicks = rememberShellAudioClicks(guardedNavigator, audioSource)
    val onCheckForUpdates: () -> Unit = remember(services) {
        services.updateCheckController::checkForUpdate
    }
    // The admin reads are the shared rememberShellAdminGate outputs — LAZY on
    // purpose ("Lazy reads — admin refreshes don't rebuild the graph"): the
    // read lambdas capture the collected State (the gate remembers on them),
    // so admin refreshes re-compose entries without rebuilding the hooks or
    // the section graph.
    val isAdminState = sessionController.isAdmin.collectAsState()
    val isRefreshingAdminState = sessionController.isRefreshingAdmin.collectAsState()
    val adminGate = rememberShellAdminGate(isAdminState, isRefreshingAdminState)
    val onHomeModeChange = remember(sessionController) { sessionController::setHomeMode }
    val onLogout: (Boolean) -> Unit = remember(sessionController) { sessionController::logout }
    val onRefreshAdmin: () -> Unit = remember(sessionController) {
        sessionController::refreshAdminStatusNow
    }
    val onConsumeSearchQuery: () -> Unit = remember(pendingSearchQuery) {
        { pendingSearchQuery.value = null }
    }
    val shellHost = rememberShellHost(
        navigator = guardedNavigator,
        home = ShellHomeHooks(
            homeMode = homeMode,
            onHomeModeChange = onHomeModeChange,
            // Android-only slots: no cast strategy and no shortcut-armed
            // "Surprise Me" flow on desktop; emptyFlow() is an
            // identity-stable singleton so the factory's remember keys
            // never churn.
            playOnRedirect = null,
            surpriseRequests = emptyFlow(),
        ),
        audio = audioClicks,
        settings = ShellSettingsHooks(
            onLogout = onLogout,
            onCheckForUpdates = onCheckForUpdates,
        ),
        admin = ShellAdminHooks(
            isAdmin = adminGate.isAdmin,
            isRefreshingAdmin = adminGate.isRefreshingAdmin,
            onRefreshAdmin = onRefreshAdmin,
        ),
        search = ShellSearchHooks(
            pendingSearchQuery = pendingSearchQuery,
            onConsumeSearchQuery = onConsumeSearchQuery,
        ),
    )

    // Remember the entry provider graph so the ~20 shared section builders
    // aren't re-invoked (allocating fresh lambdas + entry objects) on every
    // recomposition of this scaffold (same memoization the Android shell
    // applies). The graph — and with it the sectionRegistry the guard (in
    // [DesktopShellServices]) reads — is the shared appSections canonical
    // order (nav3 resolves by key, so the former per-shell ordering was never
    // routing behaviour) plus the one desktop-side registration below.
    val shellSections = remember(guardedNavigator, shellHost) {
        shellEntryProvider(
            navigator = guardedNavigator,
            host = shellHost,
            registry = services.sectionRegistry,
        ) {
            // …player-video, a conveyor — live where a surface story
            // exists: the commonMain VideoPlayerScreen renders the
            // software-render pane wherever its probe smoke-passed (primary —
            // the video sits inside the compose tree, so controls and clicks
            // work), falling back to the SwingPanel/HWND mpv surface, and the
            // per-session engine resolves through PlayerEngineFactory
            // (desktopPlayerModule). OSes with neither story keep the
            // dead-end guard above. The subtitle-tester overlay stays
            // Android-only: its push target dead-ends in the guard above.
            if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported ||
                DesktopVideoSurfaceBridge.isSoftwareVideoSurfaceSupported
            ) {
                entry<Route.VideoPlayer> { key ->
                    VideoPlayerScreen(
                        itemId = key.itemId,
                        mediaSourceId = key.mediaSourceId,
                        startPositionTicks = key.startPositionTicks,
                        subtitleStreamIndex = key.subtitleStreamIndex,
                        audioStreamIndex = key.audioStreamIndex,
                        onBack = { guardedNavigator.goBack() },
                    )
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            // Passive pointer observation — every pointer event of
            // any kind feeds the idle controller's debounce WITHOUT consuming
            // or transforming the event (the gesture layers below see it
            // unchanged).
            .pointerInput(idleAmbientController) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        idleAmbientController.onUserInput()
                    }
                }
            }
            // Back handling: Esc and Alt+Left pop the current stack when
            // there is anything to pop (nav3's predictive back is
            // Android-only; this is the whole desktop story).
            //
            // Deterministic media-key delivery: this preview is the
            // TOPMOST key-input chain, so it receives EVERY key with or
            // without any Compose focus owner (the null-focus fallback; ESC
            // has worked here since then). When the video player route is
            // current, every non-back key is offered to the player screen's
            // OWN handler through DesktopPlayerKeyBridge — the screen stays
            // the single interpreter of media-key semantics (this shell never
            // decodes a media key), and the sink declines when the focused
            // dispatch chain owns the key or a sheet is open, so a key is
            // interpreted exactly once either way. This closes the
            // flap gap: a SPACE/arrow/M/F/J/L pressed (or injected) while the
            // AWT/Compose focus shuffle left the player Box focus-less used
            // to die in this Row's fallback; now it reaches the player
            // deterministically.
            .onPreviewKeyEvent { event ->
                // Every key resets the idle debounce (and dismisses
                // a shown overlay) before anything else runs.
                idleAmbientController.onUserInput()
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // desktopBackKeyDecision folds the Esc/Alt+Left test AND the
                // root-refuse; the same fold runs in the signed-out shell's
                // shared SignedOutAuthHost frame.
                if (desktopBackKeyDecision(event.key, event.isAltPressed, backStack.size)) {
                    guardedNavigator.goBack()
                    true
                } else {
                    backStack.lastOrNull() is Route.VideoPlayer &&
                        DesktopPlayerKeyBridge.deliver(event)
                }
            },
    ) {
        // Fullscreen routes (the video player) take the whole content area:
        // hide the rail while one is on top; Esc/back pops out of it.
        //
        // DELIBERATE DELTA vs the Android shell's shared
        // isFullScreenRouteActive fold (navigation/FullScreenRoutePolicy.kt):
        // this shell reads the TOP entry only (desktopTopRouteIsFullscreen,
        // pinned by DesktopLayoutPolicyTest). The Android whole-stack scan
        // exists for hazards this shell doesn't have — one always-composed
        // NavDisplay never re-registers NavKeys against this read flipping,
        // and the subtitle tester is not registered here, so no full-screen
        // route can sit below the top. Recorded so the next reader doesn't
        // "fix" the mismatch in either direction.
        val topRouteIsFullscreen = desktopTopRouteIsFullscreen(backStack.lastOrNull())
        if (!topRouteIsFullscreen) {
            NavigationRail(
                // No header: branding lives in the custom title bar
                // (DesktopTitleBar) — a second "JellyPlay" here duplicated it.
                header = null,
            ) {
                // The rail holds 15 destinations (~1100dp of items) — far more
                // than the default 800dp window height. NavigationRail's own
                // column never scrolls, so without this scrollable wrapper the
                // lower destinations (Requests…Admin) are clipped and
                // unreachable. weight(1f) keeps the header pinned and scrolls
                // only the item list.
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                // Breathing room now that the rail header is gone (the rail's
                // own vertical padding is only 4dp) and under the last item
                // when the list is scrolled to the bottom.
                Spacer(Modifier.height(12.dp))
                // Rail items come from the shared composition policy (core/ui
                // VisibleTopLevelRoutes — by construction the same rules the
                // Android bar renders through): while offline the server-bound
                // LiveTv destination drops off, then the stored nav
                // customization (Appearance → Navigation) hides/reorders, the
                // rest keeping the default order. The BASE SET — the rail's
                // own full display order — stays this shell's policy (desktop
                // has no homeMode set-selection).
                // Group spacers are preserved between consecutive items whose
                // group changes, so a custom order can interleave groups.
                val navPrefs by navigationStore.navigation.collectAsState()
                val networkStatus by networkMonitor.networkStatus.collectAsState()
                val railDescriptors = remember(navPrefs, networkStatus) {
                    visibleTopLevelRoutes(
                        DESKTOP_RAIL_ITEMS,
                        { it.route.navKey },
                        hiddenNavItems = navPrefs.hiddenNavItems,
                        navItemOrder = navPrefs.navItemOrder,
                        isOffline = networkStatus.isOffline,
                    )
                }
                railDescriptors.forEachIndexed { index, descriptor ->
                    if (index > 0 && descriptor.railGroup != railDescriptors[index - 1].railGroup) {
                        Spacer(Modifier.height(12.dp))
                    }
                    DesktopRailItem(
                        descriptor.route,
                        descriptor.railLabel,
                        descriptor.icon,
                        currentTopLevel,
                        guardedNavigator,
                    )
                }
                Spacer(Modifier.height(12.dp))
                }
            }
        }

        // Adaptive shell wiring (issue #166): the shared screens previously
        // rendered against LocalAdaptiveInfo's Compact DEFAULT because this
        // scaffold never provided it — desktop was permanently phone-layout
        // regardless of window size. Provide the live measurement (the Compose
        // window frame) plus the stored layout override, and derive the same
        // DeviceClass/InputMode tokens Android's shell does. isTv=false — the
        // TV branch never runs on this shell.
        val appearanceStore = services.appearanceStore
        val layoutMode by appearanceStore.layoutMode.collectAsState()
        val desktopAdaptiveInfo = layoutMode.applyOverride(rememberAdaptiveInfo())
        val desktopUiEnvironment = rememberJellyPlayUiEnvironment(
            adaptiveInfo = desktopAdaptiveInfo,
            isTv = false,
        )
        CompositionLocalProvider(
            LocalNetworkStatus provides networkMonitor.networkStatus,
            LocalServerHealth provides serverHealth,
            LocalPullToRefreshRegistry provides refreshRegistry,
            LocalAdaptiveInfo provides desktopAdaptiveInfo,
            LocalJellyPlayUi provides desktopUiEnvironment,
            // The shared screens post one-shot messages through the commonMain
            // message.LocalUserMessageBus; provide the SAME bus instance the
            // UserMessageHost above collects, so those messages reach this
            // shell's snackbar (desktop's severity semantics are unchanged —
            // everything still flows through the shared host's policy).
            LocalUserMessageBus provides sharedUserMessageBus,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { guardedNavigator.goBack() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = shellSections.entryProvider,
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
                // The idle ambient overlay — cross-faded over the
                // whole content area with the SAME fade pair
                // NavTransitionPolicy gives ambient routes (defaultFade in,
                // fastFade out). NOT a route: an in-scaffold overlay keeps
                // the desktop-only surface out of the shared NavKey contract.
                androidx.compose.animation.AnimatedVisibility(
                    visible = isIdle,
                    enter = androidx.compose.animation.fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    DesktopIdleOverlay(
                        serverName = idleOverlayIdentity.serverName,
                        userName = idleOverlayIdentity.userName,
                        activeSessionCount = activeRemoteSessions,
                        onAnyInput = idleAmbientController::onUserInput,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopRailItem(
    route: Route,
    label: String,
    icon: ImageVector,
    currentTopLevel: NavKey,
    navigator: Navigator,
) {
    NavigationRailItem(
        selected = currentTopLevel == route,
        onClick = { navigator.navigate(route) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

/**
 * This shell's [ShellAudioSource] over [DesktopAudioQueueManager] — the
 * manager's four StateFlow members forwarded verbatim (the Android twin
 * adapts AudioPlaybackManager the same way beside MainContent). Remembered
 * on the manager at the call site: a fresh-per-recomposition adapter would
 * churn the rememberShellAudioClicks helper's remember keys (the discipline
 * its KDoc owns).
 */
private class DesktopQueueShellAudioSource(
    private val manager: DesktopAudioQueueManager,
) : ShellAudioSource {
    override val currentPlayingItemId: StateFlow<String?> get() = manager.currentPlayingItemId
    override val albumArtUrl: StateFlow<String> get() = manager.albumArtUrl
    override val title: StateFlow<String> get() = manager.title
    override val artist: StateFlow<String> get() = manager.artist
}
