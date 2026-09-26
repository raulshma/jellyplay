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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalPullToRefreshRegistry
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch
import com.raulshma.jellyplay.core.ui.components.PullToRefreshRegistry
import com.raulshma.jellyplay.core.ui.components.SurpriseLaunchController
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.navKey
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.navigation.visibleTopLevelRoutes
import com.raulshma.jellyplay.desktop.harness.DesktopSessionHarness
import com.raulshma.jellyplay.desktop.player.MpvSoftwareSurfaceSupport
import com.raulshma.jellyplay.desktop.update.DesktopUpdateCheckController
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.player.video.DesktopPlayerKeyBridge
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import com.raulshma.jellyplay.feature.shell.ShellSessionController
import com.raulshma.jellyplay.feature.shell.UserMessageDuration
import com.raulshma.jellyplay.feature.shell.navigation.ShellHostHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellSectionRegistry
import com.raulshma.jellyplay.feature.shell.navigation.shellEntryProvider
import com.raulshma.jellyplay.feature.shell.rememberShellUserMessages
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * The nav scaffold: rail on the left, NavDisplay on the right, snackbar for
 * the dead-end guard. One back stack per top-level route (the phone app's
 * tab pattern), Esc / Alt+Left mapped to [Navigator.goBack].
 *
 * The scaffold's DECISIONS live in extracted, test-pinned top-level
 * declarations (the DesktopUpdateCheckController idiom) — the composable
 * keeps only `remember {}` construction + `LaunchedEffect` collects:
 *  - the dead-end guard adapter: [desktopGuardedNavigator] /
 *    [desktopDeadEndMessage];
 *  - the first-run onboarding gate's one-shot read:
 *    [runDesktopOnboardingGateOnce];
 *  - the UserMessageHost source assembly: [desktopUserMessageSources];
 *  - the remote MoveFocus direction mapping: [composeFocusDirection];
 *  - the idle ambient seam: [DesktopIdleAmbientController] +
 *    [resolveIdleOverlayIdentity];
 *  - the About update row: [DesktopUpdateCheckController].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DesktopNavScaffold(
    menuRefreshRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    // The ComposeWindow handle (Main.kt's AWT ref) — the remote
    // navigation ladder's select synthesis posts AWT key events through it.
    windowRef: AtomicReference<ComposeWindow?>? = null,
) {
    val navigation = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = DESKTOP_TOP_LEVEL_ROUTES,
        savedStateConfiguration = desktopNavSavedStateConfiguration(),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
    // "unsupported", never crashes boot.
    remember {
        DesktopVideoSurfaceBridge.registerSoftwareSurfaceProbe {
            MpvSoftwareSurfaceSupport.isSupported
        }
        true
    }

    // App-level composition locals the shared screens read. Network status is
    // LIVE: the flow comes from :core:data's DesktopNetworkMonitor
    // (desktopDataModule single — NetworkInterface probing with a 15 s
    // re-probe and a synchronous construction-time seed), so offline banners
    // now reflect real connectivity. Server health stays a static Unknown —
    // deliberate: the desktop shell performs no server health polling, and
    // only StudioDetailScreen reads LocalServerHealth today.
    val networkMonitor: NetworkMonitor = koinInject()
    val serverHealth = remember { MutableStateFlow(ServerHealth.Unknown) }

    // Shell session policy (ADR 0001): the wiring this scaffold used to carry
    // inline ("the Android shell's MainViewModel duties, inlined for desktop")
    // now lives in the shared ShellSessionController beside AdminRefreshGate —
    // admin-status state + the 30 s refresh arbitration, homeMode
    // collect/persist, and the revoke/plain logout fork. Constructed directly
    // over this shell's own stores (no Koin binding, the same direct
    // construction MainViewModel performs on Android) on this composition's
    // scope, so every job dies with the scaffold exactly as the inlined
    // copies did.
    val authRepository: AuthRepository = koinInject()
    val homeDiscoveryStore: HomeDiscoveryStore = koinInject()
    val sessionController = remember(authRepository, homeDiscoveryStore) {
        ShellSessionController(
            scope = scope,
            nowMs = { System.currentTimeMillis() },
            currentUser = authRepository.currentUser,
            refreshCurrentUser = { authRepository.refreshCurrentUser() },
            persistHomeMode = { mode -> homeDiscoveryStore.setHomeMode(mode) },
            homeModeChanges = homeDiscoveryStore.homeDiscovery.map { it.homeMode },
            signOut = { revoke ->
                if (revoke) authRepository.revokeServerSession() else authRepository.logout()
            },
        )
    }
    val homeMode by sessionController.homeMode.collectAsState()
    val isAdmin by sessionController.isAdmin.collectAsState()
    val isRefreshingAdmin by sessionController.isRefreshingAdmin.collectAsState()
    // Bottom-nav customization (#152): the same NavigationStore the phone
    // settings write through drives which items this rail shows and in what
    // order (see the rail composition below).
    val navigationStore: NavigationStore = koinInject()

    // Live desktop audio core — the Home music pane's Now Playing / Ambient
    // cards read the current item + metadata from it (same source the tray
    // and title bar observe; flows are read at click time, not collected).
    val audioQueueManager: DesktopAudioQueueManager = koinInject()

    // AppUpdate split + desktop auto-update (ADR
    // desktop-auto-update): the About screen's "Check for updates" row —
    // this shell's OWN update surface. The check→message mapping, the
    // browser handoff and the snackbar wording live in
    // DesktopUpdateCheckController (extracted; pinned by its test — never a
    // silent install, browser handoff only). The repository binding was
    // REPLACED by desktopAppUpdateModule (DesktopKoinModules) with the
    // real-version actual: a packaged release-lane build reports genuine
    // newer releases from the GitHub feed, a dev build stays "up to date" by
    // construction. The row itself is pref-gated by selfUpdateCheckEnabled
    // (default on).
    val appUpdateRepository: AppUpdateRepository = koinInject()
    val updateCheckController = remember(scope, appUpdateRepository, snackbarHostState) {
        DesktopUpdateCheckController(
            scope = scope,
            repository = appUpdateRepository,
            showMessage = { snackbarHostState.showSnackbar(it) },
        )
    }
    val onCheckForUpdates: () -> Unit = updateCheckController::checkForUpdate

    // Dead-end guard (runtime safety, not polish): NavDisplay with an
    // unregistered top-of-stack entry is a crash hazard, and the shared
    // screens freely push routes that have no desktop section (see
    // DesktopAppRoot KDoc). The adapter + snackbar wording live in
    // [desktopGuardedNavigator] / [desktopDeadEndMessage] (extracted; pinned
    // by DesktopNavGuardTest): sectionRegistry is the shell-owned ledger the
    // entry provider built below re-attaches on every rebuild, so a route is
    // a dead end exactly when no shellEntryProvider section registered it —
    // LiveTvChannelPlayer/SubtitleTester because their builders are
    // Android-only, VideoPlayer wherever the surface probe fails (its
    // registration flows through the same extraSections slot). The former
    // hand-kept three-route mirror is gone. Unregistered routes surface as a
    // snackbar — the desktop twin of the Android shell's
    // PlaybackHostRouter navigateFilter.
    val sectionRegistry = remember { ShellSectionRegistry() }
    val guardedNavigator = remember(navigation, sectionRegistry) {
        desktopGuardedNavigator(
            navigation = navigation,
            isRegistered = sectionRegistry::isRegistered,
            scope = scope,
            showMessage = { snackbarHostState.showSnackbar(it) },
        )
    }

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
    val appRuntimeStateStore: AppRuntimeStateStore = koinInject()
    LaunchedEffect(appRuntimeStateStore) {
        runDesktopOnboardingGateOnce(
            readOnboardingCompleted = appRuntimeStateStore::isOnboardingCompleted,
            navigate = guardedNavigator::navigate,
        )
    }

    // User-message host (the shared seam): ONE collector behind every message
    // source this shell shows. The source assembly — the shared
    // [UserMessageBus] flow plus the DesktopMusicMessageBus relay mapped onto
    // UserMessage.Error — lives in [desktopUserMessageSources] (extracted;
    // pinned by DesktopUserMessagesTest) and feeds the shared
    // rememberShellUserMessages seam: host construction + the collector
    // effect are the seam's now; the snackbar below is this shell's present
    // adapter (withDismissAction, matching the Android collector this seam
    // replaced), and the severity→duration policy stays in the shared module.
    val sharedUserMessageBus: UserMessageBus = koinInject()
    val musicMessageBus: MusicMessageBus = koinInject()
    val userMessageSources = remember(musicMessageBus) {
        desktopUserMessageSources(sharedUserMessageBus, musicMessageBus)
    }
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
        *userMessageSources.toTypedArray(),
    )

    val currentTopLevel by navigation.topLevelRoute
    val backStack = checkNotNull(navigation.backStacks[currentTopLevel]) {
        "no back stack for top-level route $currentTopLevel"
    }

    // ── remote navigation bridge collector ─────────────────────────
    // Desktop ignored RemoteNavigationBridge entirely before the receiver
    // port — remote Play → desktop, SyncPlay-driven opens and ClosePlayer
    // all dead-ended. The folds are DesktopRemoteNavigation's pure halves;
    // this effect only dispatches: pushes through the guarded navigator
    // (dead-end routes surface the guard's snackbar), ClosePlayer pops
    // player routes off every tab's stack, GoBack pops one entry, MoveFocus
    // drives the Compose FocusManager (through [composeFocusDirection], the
    // extracted four-branch mapping), and InvokeSelect synthesizes an AWT
    // Enter pair posted through the system event queue — the exact route
    // every real keystroke takes into the Compose preview-key chain (the
    // player's media-key bridge included when a player route is on top).
    val remoteNavigationBridge: com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge = koinInject()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val desktopRemoteNavCollector = remember(
        guardedNavigator,
        focusManager,
        snackbarHostState,
        windowRef,
    ) {
        DesktopRemoteNavCollector(
            navigate = guardedNavigator::navigate,
            goBack = { guardedNavigator.goBack() },
            backStacks = { navigation.backStacks.values },
            moveFocus = { direction ->
                focusManager.moveFocus(composeFocusDirection(direction))
            },
            invokeSelect = { DesktopKeySynthesizer.postEnterKey(windowRef?.get()) },
            presentMessage = { message -> snackbarHostState.showSnackbar(message) },
        )
    }
    LaunchedEffect(desktopRemoteNavCollector) {
        desktopRemoteNavCollector.collect(remoteNavigationBridge.targets)
    }

    // ── idle "Ready to play" ambient screen ───────────────────────
    // The whole seam (idle monitor + the idle-gated active-remote-session
    // count off the receiver socket's Sessions push + the overlay's identity
    // fold) lives in DesktopIdleAmbientController — the same
    // extracted-controller idiom as DesktopUpdateCheckController. The
    // scaffold keeps only the collect, the start/stop effect and the overlay
    // call; input resets run through the controller's hook at the Row
    // modifiers below. Idle definition unchanged: nothing playing (audio
    // queue + engine registry), window active, debounced timeout from the
    // screensaver store's idle-ambient settings (0 = off); playback start
    // clears the overlay on the next 1 s tick.
    val screensaverStore: com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore = koinInject()
    val activePlayerRegistry: com.raulshma.jellyplay.core.data.remote.ActivePlayerController = koinInject()
    val webSocketClient: com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient = koinInject()
    val idleAmbientController = remember(audioQueueManager, activePlayerRegistry, screensaverStore, webSocketClient, windowRef) {
        DesktopIdleAmbientController(
            settings = {
                val slice = screensaverStore.screensaver.value
                IdleAmbientSettings(
                    enabled = slice.idleAmbientEnabled,
                    timeoutMin = slice.idleAmbientTimeoutMin,
                )
            },
            isAudioPlaying = { audioQueueManager.currentPlayingItemId.value != null },
            isVideoActive = { activePlayerRegistry.engine != null },
            isWindowActive = { windowRef?.get()?.let { it.isShowing && it.isActive } ?: false },
            sessionsEvents = webSocketClient.events,
        )
    }
    DisposableEffect(idleAmbientController) {
        idleAmbientController.start(scope)
        onDispose { idleAmbientController.stop() }
    }
    val isIdle by idleAmbientController.isIdle.collectAsState()
    val activeRemoteSessions by idleAmbientController.activeRemoteSessionCount.collectAsState()

    // The overlay's identity lines: current server + user (the two-key
    // server match folds in resolveIdleOverlayIdentity, pinned by its test).
    val currentUser by authRepository.currentUser.collectAsState(initial = null)
    val servers by authRepository.servers.collectAsState(initial = emptyList())
    val idleOverlayIdentity = remember(currentUser, servers) {
        resolveIdleOverlayIdentity(currentUser, servers)
    }

    // Search-prefill channel (ShellHostHooks.pendingSearchQuery): desktop has
    // no intent/shared-text source to arm it yet — the channel is wired
    // explicitly (never a silent local default), so a future source only
    // sets this flow.
    val pendingSearchQuery = remember { MutableStateFlow<String?>(null) }

    // Shell-supplied surface behind the shared section graph (ShellHostHooks):
    // the now-playing/ambient lambdas read the desktop audio core
    // (DesktopAudioQueueManager) at click time, and the session seams wrap the
    // shared ShellSessionController above — the same values, same lazy reads
    // the old inline entryProvider captured. Remembered on the values the
    // hooks capture, so the graph rebuilds only when they change.
    val shellHost = remember(guardedNavigator, homeMode) {
        ShellHostHooks(
            homeMode = homeMode,
            onHomeModeChange = sessionController::setHomeMode,
            onNowPlayingClick = {
                audioQueueManager.currentPlayingItemId.value?.let { itemId ->
                    guardedNavigator.navigate(Route.AudioPlayer(itemId))
                }
            },
            onAmbientClick = {
                guardedNavigator.navigate(
                    Route.Ambient(
                        imageUrl = audioQueueManager.albumArtUrl.value.ifEmpty { null },
                        title = audioQueueManager.title.value,
                        artist = audioQueueManager.artist.value,
                    ),
                )
            },
            onLogout = sessionController::logout,
            onCheckForUpdates = onCheckForUpdates,
            // Lazy reads — admin refreshes don't rebuild the graph.
            isAdmin = { isAdmin },
            isRefreshingAdmin = { isRefreshingAdmin },
            onRefreshAdmin = sessionController::refreshAdminStatusNow,
            pendingSearchQuery = pendingSearchQuery,
            onConsumeSearchQuery = { pendingSearchQuery.value = null },
        )
    }

    // Remember the entry provider graph so the ~20 shared section builders
    // aren't re-invoked (allocating fresh lambdas + entry objects) on every
    // recomposition of this scaffold (same memoization the Android shell
    // applies). The graph — and with it the sectionRegistry the guard above
    // reads — is the shared appSections canonical order (nav3 resolves by
    // key, so the former per-shell ordering was never routing behaviour)
    // plus the one desktop-side registration below.
    val shellSections = remember(guardedNavigator, shellHost) {
        shellEntryProvider(
            navigator = guardedNavigator,
            host = shellHost,
            registry = sectionRegistry,
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
        // nav3 keys are the base type; only our Route subclasses carry isFullScreen.
        val topRouteIsFullscreen = (backStack.lastOrNull() as? Route)?.isFullScreen == true
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

        // The shared HomeHeroController reads LocalSurpriseOnLaunch
        // unconditionally; Android arms it from the launcher-shortcut
        // ("Surprise Me") intent, a seam desktop has no equivalent of.
        // Provide a never-armed controller so the read resolves — desktop's
        // in-app "Surprise Me" path is the surpriseRequests flow parameter,
        // which never touches this local.
        val surpriseController = remember {
            SurpriseLaunchController(
                armed = MutableStateFlow(false),
                consume = {},
            )
        }
        CompositionLocalProvider(
            LocalNetworkStatus provides networkMonitor.networkStatus,
            LocalServerHealth provides serverHealth,
            LocalSurpriseOnLaunch provides surpriseController,
            LocalPullToRefreshRegistry provides refreshRegistry,
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
