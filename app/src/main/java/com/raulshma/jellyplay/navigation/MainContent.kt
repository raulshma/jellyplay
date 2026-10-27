package com.raulshma.jellyplay.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.ShellAudioSurface
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.applyOverride
import com.raulshma.jellyplay.core.ui.adaptive.rememberAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rememberJellyPlayUiEnvironment
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.navigation.visibleTopLevelRoutes
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvTypography
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.core.designsystem.theme.TvTypography
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import com.raulshma.jellyplay.feature.shell.navigation.ShellAdminHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellHomeHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellSearchHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellSettingsHooks
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellAdminGate
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellAudioClicks
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellHost
import com.raulshma.jellyplay.feature.shell.rememberShellUserMessages
import com.raulshma.jellyplay.shell.ShellInfra

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
    // recomposition.
    val activeTopLevelRoutes: LinkedHashMap<Route, String> by remember(
        homeMode,
        preferences.hiddenNavItems,
        preferences.navItemOrder,
        isOffline,
    ) {
        derivedStateOf {
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
    }

    // Stable on purpose: this is a key of the shellHost construction below
    // (the rememberShellHost factory keys on it) and a ShellNavParams field,
    // so a fresh lambda per recomposition would make both compare unequal
    // (a fresh allocation makes the section-graph memoization always
    // miss). Remembered on the model, whose activity scope is the lambda's
    // only capture.
    val onModeChange: (HomeMode) -> Unit = remember(model) { model::setHomeMode }

    val audioItemId by audioPlaybackManager.currentPlayingItemId.collectAsStateWithLifecycle()
    val libraryFolders by infra.sessionCoordinatorLazy.value.libraryFolders.collectAsStateWithLifecycle()
    var isMiniPlayerDismissed by remember { mutableStateOf(false) }
    val showMiniPlayer by remember {
        derivedStateOf { audioItemId != null && !isFullScreenRoute && !isMiniPlayerDismissed }
    }

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

    val navBarColorState = remember { mutableStateOf<Color?>(null) }
    val animatedNavBarColor by animateColorAsState(
        targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navBarColor",
    )

    val isTv = context.isTv()

    // Press-and-hold "peek" preview (Instagram-style): the controller +
    // backdrop-blur pair live in their own holder (PeekPreviewState.kt);
    // purely ephemeral UI state, so no DI/ViewModel involvement. The whole
    // feature is dormant unless the user opts in under Experimental settings
    // (off by default).
    val peekEnabled = preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK)
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

    // The manual layout override clamps the measured width class
    // BEFORE anything consumes it: the shell's isExpanded fork below, the TV/
    // phone DeviceClass tokens, and every downstream LocalAdaptiveInfo reader
    // (rail vs bottom bar, two-pane details, grids) all follow the override
    // from this single site.
    val adaptiveInfo = preferences.layoutMode.applyOverride(rememberAdaptiveInfo())
    val uiEnvironment = rememberJellyPlayUiEnvironment(
        adaptiveInfo = adaptiveInfo,
        isTv = isTv,
    )

    val tvTypography = if (isTv) TvTypography else null

    // The floating nav-bar's hide-on-scroll state (ScrollDirectionVisibility
    // policy + the animated px offset + its stable getter) — constructed by
    // its own holder (BottomNavScrollState.kt); the phone layout's
    // NestedScrollConnection and LocalFloatingNavVisibility/Offset consume it
    // below.
    val bottomNav = rememberBottomNavScrollState()

    // Stable lambdas for CompositionLocalProvider — `provides` compares by ==,
    // so a fresh lambda per recomposition forces downstream invalidation even
    // when the captured state hasn't changed. MainContent recomposes often
    // (audio metadata, nav color, mini-player), so hoist these out.

    CompositionLocalProvider(
        LocalTvMode provides isTv,
        LocalAdaptiveInfo provides adaptiveInfo,
        LocalJellyPlayUi provides uiEnvironment,
        LocalTvTypography provides tvTypography,
        LocalPerformanceMode provides preferences.performanceMode,
        com.raulshma.jellyplay.core.ui.feedback.LocalHapticsEnabled provides preferences.hapticsEnabled,
        LocalFloatingNavVisibility provides bottomNav.scrollVisibility.visibleState,
        com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController provides peekPreview.controller,
        com.raulshma.jellyplay.core.ui.preview.LocalMediaPeekEnabled provides
            preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK),
        com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences provides com.raulshma.jellyplay.core.ui.components.CardDisplayPreferences(
            showUnwatchedBadge = preferences.showUnwatchedBadge,
            showWatchedCheckmark = preferences.showWatchedCheckmark,
            hideWatchedItems = preferences.hideWatchedItems,
            showQualityBadges = preferences.showQualityBadges,
            showNewBadges = preferences.showNewBadges,
            antiSpoilerHome = preferences.antiSpoilerHome,
        ),
    ) {
        val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope provides if (preferences.performanceMode) null else this,
                LocalNavigationBarColor provides navBarColorState,
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset provides (if (!isExpanded && !isFullScreenRoute) bottomNav.floatingNavOffset else ({ 0f })),
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
            // Hoist the audio-mini-player navigation callbacks so all three layout branches
            // (TvContent / PhoneContent / FullScreenContent) share identical instances
            // instead of allocating fresh lambdas per call site. Built through the shared
            // rememberShellAudioClicks — the ONE construction site for the pair (its KDoc
            // owns the remember-key discipline and the blank-art→null normalization):
            // this shell's audio core (AudioPlaybackManager) IS the shared
            // NowPlayingSurface — passed straight in, and the helper reads the
            // manager's flows at
            // CLICK time — the desktop hooks' pattern. Capturing the composed
            // `audioItemId` instead would either go stale (keyed on the navigator
            // alone) or rebuild the shellHost graph on every song change (keyed on
            // the item). Equivalent while resumed, which is the only time a click can land.
            val audioClicks = rememberShellAudioClicks(navigator, audioPlaybackManager)

            // Play On (cast-to-Jellyfin-session) controller — the ONE
            // construction site for the whole Play On surface family, hoisted
            // above the TV / phone / full-screen branches for the same reason
            // as the saveable state holder: a runtime TV-mode flip (or
            // entering/leaving a full-screen route) re-dispatches the branch,
            // and the mini bar's companion screen must keep rendering the
            // same controller instead of re-resolving per branch. Activity-
            // scoped through the ViewModelStore (Koin def in AppKoinModule),
            // so the instance also survives config changes. Threaded to every
            // host below as an explicit parameter — no Play On surface
            // resolves it itself.
            val playOn: com.raulshma.jellyplay.PlayOnViewModel =
                org.koin.compose.viewmodel.koinViewModel()

            // Admin access-control state, collected once here (a @Composable
            // context) and threaded into the hooks as read lambdas so the
            // navigation entries — which are composed lazily — observe the
            // latest value without re-building the entry graph. The flows
            // arrive through the [MainShellModel] seam; nothing below this
            // point reaches past it.
            val isAdminState = model.isAdmin.collectAsStateWithLifecycle()
            val isRefreshingAdminState = model.isRefreshingAdmin.collectAsStateWithLifecycle()

            // The shell-host hooks (ShellHostHooks) behind the shared section
            // graph, built through the shared rememberShellHost factory —
            // the ONE construction site for the hooks (desktop's
            // DesktopNavScaffold feeds the same five group bundles; the
            // bundle-by-bundle wiring lives in that factory file). The model's
            // signals (admin gate, update check, surprise/search prefill)
            // and the Play On controller meet the shell seam HERE — this
            // call site is the value wiring. Every factory parameter is a
            // remember key, so each bundle below may be built fresh per
            // recomposition (the groups are data classes and compare
            // structurally) as long as its members are remembered/stable
            // (the discipline the factory's KDoc states) — the graph
            // rebuilds only when these identities change.
            // The admin reads are the shared rememberShellAdminGate outputs —
            // lazy .value reads, so admin refreshes don't rebuild the graph.
            val adminGate = rememberShellAdminGate(isAdminState, isRefreshingAdminState)
            val onRefreshAdmin: () -> Unit = remember(model) { { model.refreshAdminStatus() } }
            val onCheckForUpdates: () -> Unit = remember(infra) {
                { infra.updateCoordinatorLazy.value.manualCheckForUpdate() }
            }
            val onConsumeSearchQuery: () -> Unit = remember(model) {
                model::consumePendingSearchQuery
            }
            // The shared home module narrows the Play-On surface to its
            // HomePlayOnRedirect seam (the concrete strategy is Android-
            // bound); the probe + fling choreography lives on the controller
            // (flingIfConnected), so the strategy never leaves it. The
            // redirect is active on EVERY host — TV and full-screen
            // MainNavDisplay calls included. Only observable when
            // a remote session is already connected, which itself can only
            // be initiated from the phone layout's Play On device sheet.
            // Remembered on the controller, keyed on playOn.
            val playOnRedirect = remember(playOn) { HomePlayOnRedirect(playOn::flingIfConnected) }
            // The Play On device sheet's state lives in PhoneContent; header
            // shortcuts request it through this counter (the sheet-owning
            // scope observes increments).
            val playOnOpenRequests = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            // Header-shortcut shell actions — the ONE execution surface behind
            // every action pill (home dock's header rows, the overflow menu's
            // action fold): the shared ShortcutActionSurface owns the
            // capability verdict and the navigate-home-before-arm-surprise
            // ordering. Android arms all three actions (MainViewModel
            // + the Play On sheet counter), so the unsupported arm is a
            // defensive error — it is unreachable from this shell.
            val shortcutActions = remember(model, navigationState, playOnOpenRequests) {
                com.raulshma.jellyplay.feature.shell.navigation.ShortcutActionSurface(
                    focusHome = { navigationState.topLevelRoute.value = Route.Home },
                    armSurprise = model::requestSurprise,
                    requestPlayOn = { playOnOpenRequests.value += 1 },
                    toggleOffline = model::toggleOfflineMode,
                    onUnsupported = { action ->
                        error("ShortcutActionSurface: the Android shell arms every action; $action cannot be unsupported")
                    },
                )
            }
            val shellHost = rememberShellHost(
                navigator = navigator,
                home = ShellHomeHooks(
                    homeMode = homeMode,
                    onHomeModeChange = onModeChange,
                    playOnRedirect = playOnRedirect,
                    surpriseRequests = model.surpriseRequests,
                    shortcutActions = shortcutActions,
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
                    pendingSearchQuery = model.pendingSearchQuery,
                    onConsumeSearchQuery = onConsumeSearchQuery,
                ),
            )

            // The shell-wide parameter bundle (the HomeCallbacks idiom —
            // ShellNavParams in ShellLayouts.kt): everything drilled
            // identically into all three layout branches and MainNavDisplay.
            // Every field is a remembered/stable value or a Compose-memoized
            // lambda, so the @Immutable data class compares equal across
            // recompositions and the branches stay skippable. The per-section
            // hook values ride shellHost's groups — no duplicate scalars.
            val shellParams = ShellNavParams(
                navigationState = navigationState,
                currentTopLevel = currentTopLevel,
                activeTopLevelRoutes = activeTopLevelRoutes,
                navigator = navigator,
                saveableStateHolder = saveableStateHolder,
                entryDecorator = entryDecorator,
                playOn = playOn,
                shellHost = shellHost,
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
                    shellParams = shellParams,
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
                    // while unconfigured) — resolved once here with the
                    // admin/offline/settings-reachability inputs, so the menu
                    // chrome in PhoneContent only clicks and decorates.
                    val shortcutsStore: com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore =
                        org.koin.compose.koinInject()
                    val shortcutCatalog: com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalog =
                        org.koin.compose.koinInject()
                    val shortcutsSlice by shortcutsStore.shortcuts.collectAsStateWithLifecycle()
                    val isAdminForShortcuts = isAdminState.value
                    val overflowShortcuts = androidx.compose.runtime.remember(
                        shortcutsSlice, isAdminForShortcuts, isOffline, activeTopLevelRoutes,
                    ) {
                        com.raulshma.jellyplay.core.ui.navigation.resolveShortcuts(
                            definitions = shortcutsSlice.definitions,
                            headerOrder = shortcutsSlice.headerOrder,
                            overflowOrder = shortcutsSlice.overflowOrder,
                            catalog = shortcutCatalog.entries,
                            surface = com.raulshma.jellyplay.core.model.ShortcutSurface.OVERFLOW,
                            isAdmin = isAdminForShortcuts,
                            isOffline = isOffline,
                            settingsReachable = Route.Settings in activeTopLevelRoutes,
                            maxSlots = com.raulshma.jellyplay.core.model.ShortcutLimits.OVERFLOW_MAX,
                        )
                    }
                    PhoneContent(
                        shellParams = shellParams,
                        isAudioPlayerScreen = isAudioPlayerScreen,
                        isExpanded = isExpanded,
                        bottomNavScrollVisibility = bottomNav.scrollVisibility,
                        hideBottomNavOnScroll = preferences.hideBottomNavOnScroll,
                        bottomNavOffsetHeightPx = bottomNav.offsetHeightPx,
                        showMiniPlayer = showMiniPlayer,
                        audioPlaybackManager = audioPlaybackManager,
                        audioItemId = audioItemId,
                        onDismissMiniPlayer = { isMiniPlayerDismissed = true },
                        animatedNavBarColor = animatedNavBarColor,
                        showNavBarLabels = preferences.navBarShowLabels,
                        offlineMode = offlineMode,
                        isGoingOnline = isGoingOnline,
                        downloadCount = downloadCount,
                        overflowShortcuts = overflowShortcuts,
                        playOnOpenRequests = playOnOpenRequests.value,
                    )
                } else {
                    FullScreenContent(shellParams = shellParams)
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
            // and renders the shared ConfirmDialog on an Ask. Composed INSIDE
            // the providers above (ConfirmDialog's TV-focus policy reads
            // LocalJellyPlayUi). The explicit-launch fact rides the same
            // bundle (classified in onCreate, before this composed) and is
            // remember-frozen in the host.
            SessionRestorePromptHost(
                controller = infra.sessionRestorationControllerLazy.value,
                launchedWithExplicitDestination = remember { infra.launchedWithExplicitDestination },
                navigator = navigator,
            )
            }
        }
    }
}
