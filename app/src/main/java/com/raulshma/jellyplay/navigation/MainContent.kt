package com.raulshma.jellyplay.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.ShellAudioSurface
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.navigation.visibleTopLevelRoutes
import com.raulshma.jellyplay.feature.shell.rememberShellUserMessages
import com.raulshma.jellyplay.shell.ShellInfra

/**
 * The main shell's ORCHESTRATOR: one composable body that orders the shell's
 * holders and dispatches the TV / phone / full-screen layout branches. The
 * bodies live beside it, one cluster per holder file:
 *
 *  - [rememberShellEnvironmentHost] (ShellEnvironmentHost.kt) — the shell
 *    environment: TV detection, the nav-bar color animation, the
 *    adaptive-info + UI environment pair, the TV typography, the floating
 *    nav-bar scroll state, and the ~12 app-wide CompositionLocals published
 *    through its [ShellEnvironmentHost.Provide];
 *  - [rememberPeekPreviewState] (PeekPreviewState.kt) — the press-and-hold
 *    peek preview state;
 *  - [rememberShellUserMessages] (feature/shell) — the app-wide one-shot
 *    message collector over this shell's present adapter;
 *  - [rememberNavRequestController] (NavRequestController.kt) — the
 *    request-dispatch half (the identity-stable navigator + collector);
 *  - [rememberShellHostArms] (ShellHostArms.kt) — the shell-HOST arms: the
 *    Play On controller, the admin states, and the shared
 *    rememberShellComposition call that builds the audio clicks / admin gate
 *    / ShortcutActionSurface / ShellHostHooks from this shell's
 *    ShellCompositionArms (ADR-0001: wiring shared, arms per shell), plus the
 *    ShellNavParams bundle;
 *  - [rememberShortcutSurfaces] / [BackExitHost] / [SessionRestorePromptHost]
 *    — their own small holders, called at their branch sites below.
 *
 * Nothing here owns state construction anymore beyond the truly
 * ordering-local pieces (the navigation state, the mini-player dismissed
 * flag, the drawer states); behavior is unchanged — each holder carries its
 * block's remember-key discipline comments.
 */
@Composable
internal fun MainContent(
    onLogout: (Boolean) -> Unit,
    model: MainShellModel,
    preferences: MainPreferences,
    infra: ShellInfra,
    audioPlaybackManager: ShellAudioSurface,
) {
    val homeMode = preferences.homeMode
    val isSoothing = com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme.current
    val isMonochrome = com.raulshma.jellyplay.core.designsystem.theme.LocalIsMonochromeTheme.current

    // `true` once per fresh ViewModel (state-loss restore): process death,
    // "Don't keep activities", or low-memory eviction — every case where the
    // player's in-memory state is gone but the saveable nav back stack
    // survives. Config-change recreate reuses the VM, so this reads false and
    // rotation/locale keep the player. Captured in `remember` so it is stable
    // for this Activity's lifetime; rememberNavigationState runs the strip at
    // most once.
    val stripPlayerRoutesOnRestore = remember { model.consumeStateLossRestore() }

    val navigationState = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = ALL_TOP_LEVEL_ROUTE_KEYS,
        stripPlayerRoutesOnRestore = stripPlayerRoutesOnRestore,
    )
    val context = LocalContext.current
    // The shell's snackbar surface — ONE host state shared by the
    // request-dispatch holder below (the collector's presentSnackbar seam),
    // the layout's JellyPlaySnackbarHost and the shellUserMessagePresent
    // adapter, so all three present into the same queue.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    // The external-player launch protocol pair (ExternalPlayerHost + its
    // ActivityResultLauncher, plus the no-player-found message path) —
    // constructed by its own holder (ExternalPlayerLauncherHost.kt); the
    // remembered NavRequestController below takes both members as seams.
    // The reporting contract resolves through the ShellInfra bundle — the
    // authenticated branch only, like every other lazy provider here.
    val externalPlayer = rememberExternalPlayerLauncherHost(infra.externalPlayerReportsLazy.value)

    // The request-dispatch half of this composable (NavRequestController): the
    // playback-host navigateFilter + its ONE identity-stable Navigator, the
    // stateless NavRequestCollector over its seams, and the per-loop collect
    // members the keyed effects below drive. Constructed on the holder's
    // stability-contract keys — a fresh Navigator per recomposition here used
    // to make the shellHost remember below and MainNavDisplay's
    // remember(navigator, shellHost) section-graph memoization always miss,
    // re-invoking the ~25 section builders on every tab switch, preference
    // write or download-count emission. The preferred-player preference rides
    // a rememberUpdatedState getter inside the holder, so an engine flip
    // re-decides at the next navigate without rebuilding anything.
    val navRequests = rememberNavRequestController(
        navigationState = navigationState,
        model = model,
        infra = infra,
        snackbarHostState = snackbarHostState,
        externalPlayerHost = externalPlayer.host,
        externalPlayerLauncher = externalPlayer.launcher,
        preferredPlayer = preferences.preferredPlayer,
    )
    val navigator = navRequests.navigator
    val currentTopLevel by navigationState.topLevelRoute
    val currentRoute = navigator.currentRoute()

    val isPlayerScreen = currentRoute is Route.VideoPlayer ||
            currentRoute is Route.LiveTvChannelPlayer

    val isAudioPlayerScreen = currentRoute is Route.AudioPlayer

    // A full-screen route may sit below the top of the back stack (e.g. the
    // video player with the subtitle tester pushed on top of it). Keep the
    // full-screen layout branch active while *any* full-screen route is on the
    // current stack: switching the branch mid-round-trip re-registers the
    // player's NavKey in a second NavDisplay subtree against the shared
    // SaveableStateHolder, crashing with "Key VideoPlayer(...) was used
    // multiple times" on the back-pop. isPlayerScreen/isAudioPlayerScreen stay
    // top-only since they only drive chrome styling. The scan itself is the
    // pure isFullScreenRouteActive fold (FullScreenRoutePolicy.kt), pinned by
    // FullScreenRoutePolicyTest.
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value]
    val isFullScreenRoute = isFullScreenRouteActive(currentBackStack)

    // App-wide offline state. Live TV has no offline fallback (live streams
    // are always server-bound and degrade to a dead-end ErrorScreen), so while
    // offline it is hidden from the floating nav. Home is the offline hub,
    // Search has an offline-results path, Shortcuts are device-local,
    // MusicBrowse's home surfaces the downloaded music library, and Library
    // auto-filters to downloads — all stay visible.
    val offlineMode by model.offlineMode.collectAsStateWithLifecycle()
    val isGoingOnline by model.isGoingOnline.collectAsStateWithLifecycle()
    val downloadCount by model.activeDownloadCount.collectAsStateWithLifecycle()
    val isOffline = offlineMode != com.raulshma.jellyplay.core.model.OfflineMode.ONLINE

    // Memoize the route filter+reorder so it only re-runs when homeMode /
    // hiddenNavItems / navItemOrder / offline actually change. MainContent
    // recomposes frequently (it reads audioTitle/artist/... for the mini
    // player), so an eager `when{}` would allocate a fresh LinkedHashMap +
    // intermediate entry lists + KClass.simpleName lookups on every
    // recomposition. A plain `remember(keys) { fold }` — not a derivedStateOf:
    // every captured input is already a remember key and no State is read
    // inside, so the derived lambda would only add a layer.
    val activeTopLevelRoutes: LinkedHashMap<Route, String> = remember(
        homeMode,
        preferences.hiddenNavItems,
        preferences.navItemOrder,
        isOffline,
    ) {
        // Pure fold (core/ui VisibleTopLevelRoutes): the homeMode base
        // set + the offline hide-set (LiveTv) + nav customization
        // composition — the same policy the desktop rail renders through.
        visibleTopLevelRoutes(
            when (homeMode) {
                HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
                HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
            },
            hiddenNavItems = preferences.hiddenNavItems,
            navItemOrder = preferences.navItemOrder,
            isOffline = isOffline,
        )
    }

    // The shell environment holder: TV detection, the nav-bar color
    // animation, the adaptive-info + UI-environment pair (the manual layout
    // override clamped before anything consumes it), the TV typography and the
    // floating nav-bar scroll state. Constructed BEFORE the peek preview,
    // whose factory needs the holder's isTv; its [ShellEnvironmentHost.Provide]
    // publishes the app-wide locals below, after the peek holder exists.
    val environment = rememberShellEnvironmentHost(preferences)
    val isTv = environment.isTv

    // Press-and-hold "peek" preview (Instagram-style): the controller +
    // backdrop-blur pair live in their own holder (PeekPreviewState.kt);
    // purely ephemeral UI state, so no DI/ViewModel involvement. The whole
    // feature is dormant unless the user opts in under Experimental settings
    // (off by default).
    val peekEnabled = environment.peekEnabled
    val peekPreview = rememberPeekPreviewState(
        peekEnabled = peekEnabled,
        isTv = isTv,
        performanceMode = preferences.performanceMode,
    )

    // Single root collector for app-wide one-shot messages, behind the
    // shared rememberShellUserMessages seam: host construction and the
    // collector effect live in feature/shell now; this shell supplies only
    // the surface fork (the TV-Toast vs phone-Snackbar adapter, built by
    // shellUserMessagePresent in NavRequestCollector.kt). The ONE
    // (commonMain) bus's payloads are already seam UserMessages, so the
    // plain overload. Collection lives with this composition and restarts
    // only if the bus instance is swapped; a TV/phone flip re-arms the
    // surface adapter in place instead of cancelling and relaunching the
    // collector.
    val userMessageBus = LocalUserMessageBus.current
    val presentUserMessage = shellUserMessagePresent(
        context = context,
        isTv = isTv,
        snackbarHostState = snackbarHostState,
    )

    rememberShellUserMessages(presentUserMessage, userMessageBus.messages)

    val audioItemId by audioPlaybackManager.currentPlayingItemId.collectAsStateWithLifecycle()
    val libraryFolders by infra.sessionCoordinatorLazy.value.libraryFolders.collectAsStateWithLifecycle()
    var isMiniPlayerDismissed by remember { mutableStateOf(false) }
    // A plain composition val, NOT a `remember { derivedStateOf { ... } }`: the
    // derived lambda had no keys, so it captured the plain `isFullScreenRoute`
    // Boolean BY VALUE from the first composition forever — cold-starting on a
    // full-screen route froze showMiniPlayer=false for the process lifetime
    // (the mini player never returned after backing out). Three comparisons buy
    // nothing from derivation, and the delegated state reads below recompose
    // this scope naturally.
    val showMiniPlayer = audioItemId != null && !isFullScreenRoute && !isMiniPlayerDismissed

    LaunchedEffect(audioItemId) {
        if (audioItemId != null) {
            isMiniPlayerDismissed = false
        }
    }

    // The request-dispatch effects (NavRequestCollector's loops, driven
    // through the remembered holder above). The value-keyed pending-route
    // effect re-launches — and re-captures the holder's collector — on every
    // new route, while the flow-keyed collectors hold their launch-time
    // instance (NavRequestCollector's documented capture semantics; the
    // holder's remember keys are the collector's captured seams, so the only
    // way those captures go stale is a holder rebuild, which any re-keying
    // effect picks up). The policy forks are pure and pinned in
    // NavRequestCollector's suite; the effects are one-line launchers.

    // Deep links / launcher shortcuts / shared-text targets
    // (MainViewModel.pendingRoute). Value-keyed over the lifecycle-aware
    // state, so dispatch + the consume-once ack land in the frame the state
    // is seen — the tab-vs-nested fork lives in the collector's pure fold.
    val pendingRoute by model.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        navRequests.dispatchPendingRoute(pendingRoute)
    }

    // Remote "Play" / "Playstate" / "GeneralCommand" navigation requests
    // emitted by the WebSocket receiver; the target→route mapping and the
    // multi-back-stack player pop are shared/feature/shell's pure folds
    // (feature.shell.navigation.RemoteNavigationRouting, pinned by its
    // jvmTest). The bridge resolves INSIDE the holder member — LaunchedEffect
    // runs after the frame applies, so its Koin construction never runs
    // during any composition pass.
    val contextMenuUnavailableMessage = stringResource(R.string.snackbar_context_menu_unavailable)
    LaunchedEffect(infra.remoteNavigationBridgeLazy) {
        navRequests.collectRemoteNavigation(contextMenuUnavailableMessage)
    }

    // SyncPlay auto-open: a joined group started playing (or switched items)
    // while no player is on top of any back stack → open the video player.
    // When a player IS already open, its SyncPlayBridge drives the item load
    // in place — the player-open guard lives in the shared pure fold. The
    // coordinator rides the infra bundle (lazy like its peers; resolved
    // inside the holder member for the same post-frame reason as the bridge
    // above).
    LaunchedEffect(infra.syncPlayOpenCoordinatorLazy) {
        navRequests.collectSyncPlayOpens()
    }

    // Remote-control "now playing" snackbar; the title fallback + template
    // format live in the shared pure fold. Resolved inside the holder member
    // for the same post-frame reason as the bridge above.
    val nowPlayingTemplate = stringResource(R.string.snackbar_now_playing)
    androidx.compose.runtime.LaunchedEffect(infra.remoteControlReceiverLazy) {
        navRequests.collectNowPlayingSnackbars(nowPlayingTemplate)
    }

    environment.Provide(peekPreview) {
        val isExpanded = environment.adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope provides if (preferences.performanceMode) null else this,
                com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor provides environment.navBarColorState,
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset provides (if (!isExpanded && !isFullScreenRoute) environment.bottomNav.floatingNavOffset else ({ 0f })),
                // The floating bottom nav paints ONLY in the compact phone branch
                // (PhoneContent's `if (!isExpanded)` bar; TV composes TvContent,
                // full-screen routes FullScreenContent, expanded widths a rail).
                // Bottom-floating elements read this to skip the nav clearance
                // wherever no bar is painted — the same fork as the offset above
                // plus the !isTv guard.
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavPresent
                    provides (!isTv && !isExpanded && !isFullScreenRoute),
            ) {
            // Hoist the saveable-state holder above the isTv/isFullScreenRoute branches so that
            // navigation-entry saveable state (scroll position, form fields, etc.) survives
            // transitions between phone <-> TV <-> full-screen layouts. A holder
            // created per MainNavDisplay call site would lose saveable state on
            // every layout-branch switch (e.g. entering the player and back).
            val saveableStateHolder = rememberSaveableStateHolder()
            val entryDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>(saveableStateHolder)
            // The shell-host arms holder (ShellHostArms.kt): the Play On
            // controller, the admin states, the shared
            // rememberShellComposition construction (audio clicks, admin
            // gate's lazy reads, ShortcutActionSurface, the ShellHostHooks
            // behind the shared section graph) and the ShellNavParams bundle
            // — the ONE value-wiring site where the model's signals meet the
            // shell seam. The arms holder carries the audio-clicks and
            // Play-On remember-key discipline comments; the graph below
            // (MainNavDisplay's remember(navigator, shellHost)) rebuilds only
            // when the arms' identities change.
            val arms = rememberShellHostArms(
                navigator = navigator,
                navigationState = navigationState,
                model = model,
                onLogout = onLogout,
                infra = infra,
                audioPlaybackManager = audioPlaybackManager,
                homeMode = homeMode,
                currentTopLevel = currentTopLevel,
                activeTopLevelRoutes = activeTopLevelRoutes,
                saveableStateHolder = saveableStateHolder,
                entryDecorator = entryDecorator,
            )

            Box(Modifier.fillMaxSize()) {
            // Wrap the live content (TV / Phone / FullScreen) in its own Box so
            // the peek overlay's backdrop blur applies to it only, leaving the
            // SnackbarHost and the overlay itself sharp.
            Box(Modifier.fillMaxSize().then(peekPreview.blurModifier)) {
            // Hoist the TV drawer state above the isFullScreenRoute branch so it survives visiting a
            // full-screen route (e.g. the player) and back, instead of being recreated when
            // TvNavigationDrawer leaves and re-enters composition. Fully-qualified to keep the TV
            // DrawerState type distinct from any mobile-material3 names.
            val tvDrawerState = androidx.tv.material3.rememberDrawerState(androidx.tv.material3.DrawerValue.Closed)
            val tvDrawerListState = androidx.compose.foundation.lazy.rememberLazyListState()
            // Returning from a full-screen route with a saved-Open drawer state would make the
            // drawer rail re-grab focus on re-entry and land expanded; snap it closed so the
            // restored screen owns focus. Keyed only on the route flag — reading currentValue
            // here would re-run when the user opens the drawer intentionally and fight it.
            LaunchedEffect(isTv, isFullScreenRoute) {
                if (isTv && !isFullScreenRoute &&
                    tvDrawerState.currentValue == androidx.tv.material3.DrawerValue.Open
                ) {
                    tvDrawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                }
            }
            if (isTv && !isFullScreenRoute) {
                TvContent(
                    shellParams = arms.shellParams,
                    tvDrawerState = tvDrawerState,
                    tvDrawerListState = tvDrawerListState,
                    libraryFolders = libraryFolders,
                    tvOverscan = preferences.tvOverscan,
                    hiddenNavItems = preferences.hiddenNavItems,
                    navItemOrder = preferences.navItemOrder,
                    nowPlayingEnabled = audioItemId != null,
                    showMiniPlayer = showMiniPlayer,
                    audioPlaybackManager = audioPlaybackManager,
                    onDismissMiniPlayer = { isMiniPlayerDismissed = true },
                )
            } else {
                if (!isFullScreenRoute) {
                    // Wire the system/gesture back button to in-app navigation
                    // (BackExitHost.kt): back from a deep screen returns to the
                    // tab root; at a tab root it prompts and only exits on a
                    // second press. The full-screen player is excluded — it
                    // owns its own BackHandler.
                    BackExitHost(navigator = navigator)
                    // The user's customized overflow menu (shipped defaults
                    // while unconfigured) — resolved by the shared
                    // ShortcutSurfaceResolver behind rememberShortcutSurfaces
                    // (the store/catalog Koin resolution lives there, not in
                    // this layout branch), so the menu chrome in PhoneContent
                    // only clicks and decorates.
                    val shortcutSurfaces = rememberShortcutSurfaces(
                        isAdmin = arms.isAdminState.value,
                        isOffline = isOffline,
                        activeTopLevelRoutes = activeTopLevelRoutes.keys,
                    )
                    PhoneContent(
                        shellParams = arms.shellParams,
                        isAudioPlayerScreen = isAudioPlayerScreen,
                        isExpanded = isExpanded,
                        bottomNavScrollVisibility = environment.bottomNav.scrollVisibility,
                        hideBottomNavOnScroll = preferences.hideBottomNavOnScroll,
                        bottomNavOffsetHeightPx = environment.bottomNav.offsetHeightPx,
                        showMiniPlayer = showMiniPlayer,
                        audioPlaybackManager = audioPlaybackManager,
                        audioItemId = audioItemId,
                        onDismissMiniPlayer = { isMiniPlayerDismissed = true },
                        animatedNavBarColor = environment.animatedNavBarColor,
                        showNavBarLabels = preferences.navBarShowLabels,
                        offlineMode = offlineMode,
                        isGoingOnline = isGoingOnline,
                        downloadCount = downloadCount,
                        overflowShortcuts = shortcutSurfaces.overflow,
                        playOnOpenRequests = arms.playOnOpenRequests.value,
                    )
                } else {
                    FullScreenContent(shellParams = arms.shellParams)
                }
                }
            } // end inner blur Box
                com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = if (isFullScreenRoute) 16.dp else 96.dp),
                )
            }
            // Press-and-hold peek overlay — topmost. Only composed when the user
            // has enabled the experimental feature and on phone; on TV the
            // controller is never triggered, so this would render nothing anyway.
            if (peekEnabled && !isTv) {
                com.raulshma.jellyplay.core.ui.preview.MediaPreviewOverlay(
                    controller = peekPreview.controller,
                )
            }

            // Cold-start session restoration ("resume last watched?"): ONE
            // evaluation per process, applied shell-side — the prompt host
            // resolves the controller single through the infra bundle
            // (authenticated branch only, like every other lazy provider here)
            // and renders the shared SessionRestorePromptDialog on an Ask
            // (the shared applier owns the ladder, the arms and the dismissal
            // scope). Composed INSIDE the providers above (ConfirmDialog's
            // TV-focus policy reads LocalJellyPlayUi). The explicit-launch
            // fact rides the same bundle (classified in onCreate, before this
            // composed) and is remember-frozen in the applier.
            SessionRestorePromptHost(
                controller = infra.sessionRestorationControllerLazy.value,
                launchedWithExplicitDestination = remember { infra.launchedWithExplicitDestination },
                navigator = navigator,
            )
            }
        }
    }
}
