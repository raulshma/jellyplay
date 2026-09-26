package com.raulshma.jellyplay.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.rememberAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rememberJellyPlayUiEnvironment
import com.raulshma.jellyplay.core.ui.components.BackExitConfirmation
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.components.ScrollDirectionVisibility
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.resolve
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.navigation.visibleTopLevelRoutes
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvTypography
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.designsystem.theme.TvTypography
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import com.raulshma.jellyplay.feature.shell.navigation.ShellHostHooks
import com.raulshma.jellyplay.feature.shell.rememberShellUserMessages
import com.raulshma.jellyplay.navigation.playbackhost.ExternalPlayerHost
import com.raulshma.jellyplay.navigation.playbackhost.HostDecision
import com.raulshma.jellyplay.navigation.playbackhost.PlaybackHostRouter
import com.raulshma.jellyplay.shell.ShellInfra
import kotlinx.coroutines.launch

@Composable
internal fun MainContent(
    onLogout: (Boolean) -> Unit,
    model: MainShellModel,
    preferences: MainPreferences,
    infra: ShellInfra,
    audioPlaybackManager: AudioPlaybackManager,
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userMessageBus = LocalUserMessageBus.current
    // Shared (commonMain) bus — same instance the root provider supplies to
    // the migrated ViewModels; collected alongside the legacy bus below.
    val sharedUserMessageBus = com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus.current
    // One home for the external-player launch protocol (ExternalPlayerHost,
    // beside PlaybackHostRouter): resolve → report-start → stash → chooser →
    // failure-clears-stash+error, plus the result arm's position parse +
    // report-stop — the ordering between those steps is pinned there
    // (ExternalPlayerHostTest). The host owns the pending-launch stash, so it
    // is remembered (stateful, unlike the stateless NavRequestCollector
    // constructed inline below); the ActivityResultLauncher arrives as a
    // per-call seam because the shell constructs the host BEFORE the launcher
    // (the launcher's result callback feeds host.onResult). The bus rides a
    // rememberUpdatedState wrapper so the remembered host always posts to the
    // composition's current bus.
    val currentMessageBus by rememberUpdatedState(userMessageBus)
    val externalPlayerHost = remember {
        ExternalPlayerHost(
            buildLaunch = model::buildExternalPlayerLaunch,
            reportStart = model::reportExternalPlaybackStart,
            reportStopped = model::reportExternalPlaybackStopped,
            notifyNoPlayerFound = {
                currentMessageBus.error(
                    com.raulshma.jellyplay.core.ui.feedback.uiTextOf(
                        com.raulshma.jellyplay.shared.core.ui.R.string.msg_no_video_player_found,
                    ),
                )
            },
        )
    }
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result: ActivityResult ->
        externalPlayerHost.onResult(
            position = result.data?.extras?.get("position"),
            positionMs = result.data?.extras?.get("positionMs"),
        )
    }
    val navigator = Navigator(navigationState, navigateFilter = { route ->
        // Thin executing adapter for PlaybackHostRouter — the single owner of
        // the "which host mounts playback" decision. ExternalPlayer → the
        // host above (its returned position is credited via
        // reportExternalPlaybackStopped, so Continue Watching advances for
        // regular videos and Live TV channels); DedicatedActivity →
        // PlayerActivity (system PiP floats over this browse UI; back-stack
        // choreography: shared taskAffinity, singleTask). Both return false so
        // the route never enters an in-nav back stack. InNav/NotPlayback →
        // true, the Navigator pushes normally.
        when (val decision = PlaybackHostRouter.decide(route, preferences.preferredPlayer)) {
            is HostDecision.ExternalPlayer -> {
                scope.launch {
                    externalPlayerHost.launch(
                        itemId = decision.itemId,
                        mediaSourceId = decision.mediaSourceId,
                        startPositionTicks = decision.startPositionTicks,
                        startChooser = { chooser -> externalPlayerLauncher.launch(chooser) },
                    )
                }
                false
            }
            is HostDecision.DedicatedActivity -> {
                context.startActivity(decision.args.buildIntent(context))
                false
            }
            HostDecision.InNav, HostDecision.NotPlayback -> true
        }
    })
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
    // auto-filters to downloads (#147) — all stay visible.
    val offlineMode by model.offlineMode.collectAsStateWithLifecycle()
    val isGoingOnline by model.isGoingOnline.collectAsStateWithLifecycle()
    val downloadCount by model.activeDownloadCount.collectAsStateWithLifecycle()
    val isOffline = offlineMode != com.raulshma.jellyplay.core.model.OfflineMode.ONLINE

    // Memoize the route filter+reorder so it only re-runs when homeMode /
    // hiddenNavItems / navItemOrder / offline actually change. MainContent
    // recomposes frequently (it reads audioTitle/artist/... for the mini
    // player), so the previous eager `when{}` allocated a fresh LinkedHashMap +
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

    val onModeChange: (HomeMode) -> Unit = { mode ->
        model.setHomeMode(mode)
    }

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

    // One home for the shell's nav-request collectors (NavRequestCollector,
    // beside RemoteNavigationRouting): every collect-then-dispatch loop that
    // used to live inline in this body — the policy forks are pure and pinned
    // there; the effects below are one-line launchers. The controller is
    // stateless, so no remember: each effect captures the instance current
    // when it (re)launches, exactly as the former inline collectors captured
    // `navigator` (whose navigateFilter carries the playback-host decision).
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val navRequests = NavRequestCollector(
        topLevelKeys = ALL_TOP_LEVEL_ROUTE_KEYS,
        navigate = navigator::navigate,
        selectTopLevelTab = { route -> navigationState.topLevelRoute.value = route },
        backStacks = { navigationState.backStacks.values },
        consumePendingRoute = model::consumePendingRoute,
        presentSnackbar = { message ->
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        },
        goBack = { navigator.goBack() },
        dispatchKey = infra.keyDispatcher,
    )

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
    // jvmTest). The bridge resolves
    // INSIDE the effect body — LaunchedEffect runs after the frame applies,
    // so its Koin construction no longer runs during any composition pass.
    val contextMenuUnavailableMessage = stringResource(R.string.snackbar_context_menu_unavailable)
    LaunchedEffect(infra.remoteNavigationBridgeLazy) {
        navRequests.collectRemoteNavigation(
            targets = infra.remoteNavigationBridgeLazy.value.targets,
            contextMenuUnavailableMessage = contextMenuUnavailableMessage,
        )
    }

    // SyncPlay auto-open: a joined group started playing (or switched items)
    // while no player is on top of any back stack → open the video player.
    // When a player IS already open, its SyncPlayBridge drives the item load
    // in place — the player-open guard lives in the collector's pure fold.
    // The coordinator rides the infra bundle (lazy like its five peers;
    // resolved inside the effect body for the same post-frame reason as the
    // bridge above).
    LaunchedEffect(infra.syncPlayOpenCoordinatorLazy) {
        navRequests.collectSyncPlayOpens(infra.syncPlayOpenCoordinatorLazy.value.openRequests)
    }

    // Remote-control "now playing" snackbar; the title fallback + template
    // format live in the collector's pure fold. Resolved inside the
    // effect body for the same post-frame reason as the bridge above.
    val nowPlayingTemplate = stringResource(R.string.snackbar_now_playing)
    androidx.compose.runtime.LaunchedEffect(infra.remoteControlReceiverLazy) {
        navRequests.collectNowPlayingSnackbars(
            events = infra.remoteControlReceiverLazy.value.playEvents,
            messageTemplate = nowPlayingTemplate,
        )
    }

    val navBarColorState = remember { mutableStateOf<Color?>(null) }
    val animatedNavBarColor by animateColorAsState(
        targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navBarColor",
    )

    val isTv = context.isTv()

    // Press-and-hold "peek" preview (Instagram-style). Remembered once at the
    // root and provided via LocalMediaPreviewController; the overlay collects it
    // below. Purely ephemeral UI state, so no DI/ViewModel involvement. The
    // whole feature is dormant unless the user opts in under Experimental
    // settings (off by default).
    val peekEnabled = preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK)
    val mediaPreviewController = remember {
        com.raulshma.jellyplay.core.ui.preview.MediaPreviewController()
    }
    val mediaPreviewState by mediaPreviewController.state.collectAsStateWithLifecycle()
    // Blur the live content behind the peek overlay. Skipped on TV (no peek), in
    // performance mode (Modifier.blur over the full tree is GPU-costly), and when
    // the feature is disabled — in which case mediaPreviewState is always null.
    val previewBlur by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (peekEnabled && !isTv && !preferences.performanceMode && mediaPreviewState != null) 14f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "previewBackdropBlur",
    )
    val previewBlurModifier =
        if (previewBlur > 0.5f) Modifier.blur(previewBlur.dp) else Modifier

    // Single root collector pair for app-wide one-shot messages, behind the
    // shared rememberShellUserMessages seam: host construction and the
    // collector effects live in feature/shell now; this shell supplies only
    // the surface fork (the TV-Toast vs phone-Snackbar adapter, built by
    // shellUserMessagePresent in NavRequestCollector.kt) and, for the legacy
    // bus, the severity/resolver projection. Collection lives with this
    // composition and restarts only if a bus instance is swapped; a TV/phone
    // flip re-arms the surface adapter in place instead of cancelling and
    // relaunching the collectors (the former (bus, isTv) keys — the
    // deliberate delta documented on the seam).
    val presentUserMessage = shellUserMessagePresent(
        context = context,
        isTv = isTv,
        snackbarHostState = snackbarHostState,
    )

    // Legacy (:core:ui feedback) bus — its Android payload type stays outside
    // the seam via the adapted overload: the severity projection
    // (legacySeverityOf) and the legacy UiText.resolve(context) resolution are
    // supplied here, not the shared compose-resources resolver.
    rememberShellUserMessages(
        presentUserMessage,
        userMessageBus.messages,
        severityOf = ::legacySeverityOf,
        resolveText = { message -> message.text.resolve(context) },
    )

    // Shared (commonMain) bus the migrated ViewModels post through — its
    // payloads are already seam UserMessages, so the plain overload.
    rememberShellUserMessages(presentUserMessage, sharedUserMessageBus.messages)

    val adaptiveInfo = rememberAdaptiveInfo()
    val uiEnvironment = rememberJellyPlayUiEnvironment(
        adaptiveInfo = adaptiveInfo,
        isTv = isTv,
    )

    val tvTypography = if (isTv) TvTypography else null

    val bottomNavHeight = Dimensions.floatingNavHeight // Canonical floating nav-bar height
    val bottomNavHeightPx = with(LocalDensity.current) { bottomNavHeight.toPx() }
    val bottomNavOffsetHeightPx = remember { mutableFloatStateOf(0f) }
    // Hide-on-scroll policy for the floating nav bar: core/ui's shared
    // ScrollDirectionVisibility — the same policy the home dock feeds from its
    // LazyListState. The nav's mechanism adapter is the NestedScrollConnection
    // in PhoneContent below. There is deliberately NO at-top rule
    // (forceVisibleAtTop = false — the nav never force-shows on returning to a
    // list's top) and NO per-update canHide gate (canHide = null — its only
    // gate is the settings-off reset LaunchedEffect in PhoneContent), matching
    // the former inline state machine exactly. The module owns the visible
    // state: `visibleState` is what LocalFloatingNavVisibility provides, and
    // the offset animation below reads `visible` exactly as it read the former
    // bare MutableState.
    val bottomNavScrollVisibility = remember {
        ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)
    }
    var isBottomNavVisible by bottomNavScrollVisibility.visibleState

    val animatedBottomNavOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isBottomNavVisible) 0f else -bottomNavHeightPx * 2,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bottomNavOffset"
    )

    LaunchedEffect(animatedBottomNavOffset) {
        bottomNavOffsetHeightPx.floatValue = animatedBottomNavOffset
    }

    // Stable getter for the floating-nav offset. Reading
    // `bottomNavOffsetHeightPx.floatValue` here would force `MainContent` to
    // recompose on every animation frame of the nav-bar slide (which re-runs
    // the whole TV/Phone/FullScreen branch dispatch). Exposing a `() -> Float`
    // instead lets leaf consumers read the value inside Modifier.offset { … }
    // (layout phase) — no recomposition at all, just relayout.
    val floatingNavOffset: () -> Float = remember(bottomNavOffsetHeightPx) {
        { bottomNavOffsetHeightPx.floatValue }
    }

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
        LocalFloatingNavVisibility provides bottomNavScrollVisibility.visibleState,
        com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController provides mediaPreviewController,
        com.raulshma.jellyplay.core.ui.preview.LocalMediaPeekEnabled provides
            preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK),
        com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences provides com.raulshma.jellyplay.core.ui.components.CardDisplayPreferences(
            showUnwatchedBadge = preferences.showUnwatchedBadge,
            showWatchedCheckmark = preferences.showWatchedCheckmark,
            hideWatchedItems = preferences.hideWatchedItems,
        ),
    ) {
        val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope provides if (preferences.performanceMode) null else this,
                LocalNavigationBarColor provides navBarColorState,
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset provides (if (!isExpanded && !isFullScreenRoute) floatingNavOffset else ({ 0f })),
            ) {
            // Hoist the saveable-state holder above the isTv/isFullScreenRoute branches so that
            // navigation-entry saveable state (scroll position, form fields, etc.) survives
            // transitions between phone <-> TV <-> full-screen layouts. Previously each
            // MainNavDisplay call site created its own holder, so saveable state was lost on
            // every layout-branch switch (e.g. entering the player and back).
            val saveableStateHolder = rememberSaveableStateHolder()
            val entryDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>(saveableStateHolder)
            // Hoist the audio-mini-player navigation callbacks so all three layout branches
            // (TvContent / PhoneContent / FullScreenContent) can share identical instances
            // instead of allocating fresh lambdas per call site.
            val onNowPlayingClick: () -> Unit = {
                audioItemId?.let { itemId -> navigator.navigate(Route.AudioPlayer(itemId)) }
            }
            val onAmbientClick: () -> Unit = {
                navigator.navigate(
                    Route.Ambient(
                        imageUrl = audioPlaybackManager.albumArtUrl.value,
                        title = audioPlaybackManager.title.value,
                        artist = audioPlaybackManager.artist.value,
                    )
                )
            }

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
            // graph, built ONCE here — the single site where the model's
            // signals (admin gate, update check, surprise/search prefill) and
            // the Play On controller meet the shell seam. Remembered on the
            // same keys the former MainNavDisplay construction used, so the
            // graph rebuilds only when they change.
            val shellHost = remember(
                navigator,
                homeMode,
                onModeChange,
                onNowPlayingClick,
                onAmbientClick,
                onLogout,
                playOn,
            ) {
                ShellHostHooks(
                    homeMode = homeMode,
                    onHomeModeChange = onModeChange,
                    onNowPlayingClick = onNowPlayingClick,
                    onAmbientClick = onAmbientClick,
                    onLogout = onLogout,
                    onCheckForUpdates = { infra.updateCoordinatorLazy.value.manualCheckForUpdate() },
                    // Lazy .value reads — admin refreshes don't rebuild the graph.
                    isAdmin = { isAdminState.value },
                    isRefreshingAdmin = { isRefreshingAdminState.value },
                    onRefreshAdmin = { model.refreshAdminStatus() },
                    // The shared home module narrows the Play-On surface to its
                    // HomePlayOnRedirect seam (the concrete strategy is Android-
                    // bound); the probe + fling choreography lives on the controller
                    // (flingIfConnected), so the strategy never leaves it. Declared
                    // delta: the redirect is active on EVERY host now —
                    // it used to be phone-layout-only (the TV and full-screen
                    // MainNavDisplay calls passed no strategy). Only observable when
                    // a remote session is already connected, which itself can only
                    // be initiated from the phone layout's Play On device sheet.
                    playOnRedirect = HomePlayOnRedirect(playOn::flingIfConnected),
                    surpriseRequests = model.surpriseRequests,
                    pendingSearchQuery = model.pendingSearchQuery,
                    onConsumeSearchQuery = model::consumePendingSearchQuery,
                )
            }

            // The shell-wide parameter bundle (the HomeCallbacks idiom —
            // ShellNavParams in ShellLayouts.kt): everything drilled
            // identically into all three layout branches and MainNavDisplay.
            // Every field is a remembered/stable value or a Compose-memoized
            // lambda, so the @Immutable data class compares equal across
            // recompositions and the branches stay skippable.
            val shellParams = ShellNavParams(
                navigationState = navigationState,
                currentTopLevel = currentTopLevel,
                activeTopLevelRoutes = activeTopLevelRoutes,
                navigator = navigator,
                onLogout = onLogout,
                homeMode = homeMode,
                onModeChange = onModeChange,
                saveableStateHolder = saveableStateHolder,
                entryDecorator = entryDecorator,
                onNowPlayingClick = onNowPlayingClick,
                onAmbientClick = onAmbientClick,
                playOn = playOn,
                shellHost = shellHost,
            )

            Box(Modifier.fillMaxSize()) {
            // Wrap the live content (TV / Phone / FullScreen) in its own Box so
            // the peek overlay's backdrop blur applies to it only, leaving the
            // SnackbarHost and the overlay itself sharp.
            Box(Modifier.fillMaxSize().then(previewBlurModifier)) {
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
                    hiddenNavItems = preferences.hiddenNavItems,
                    navItemOrder = preferences.navItemOrder,
                    nowPlayingEnabled = audioItemId != null,
                    showMiniPlayer = showMiniPlayer,
                    audioPlaybackManager = audioPlaybackManager,
                    onDismissMiniPlayer = { isMiniPlayerDismissed = true },
                )
            } else {
                if (!isFullScreenRoute) {
                    // Wire the system/gesture back button to in-app navigation so back
                    // from a deep screen returns to the tab root. At a tab root, mirror
                    // the TV path: prompt with a toast and only exit on a second press
                    // inside the shared BackExitConfirmation window. The full-screen
                    // player is excluded — it owns its own BackHandler.
                    val backExitConfirmation = remember { BackExitConfirmation() }
                    var lastBackPressTime by remember { mutableLongStateOf(0L) }
                    BackHandler(enabled = true) {
                        when (val decision = backExitConfirmation.onBack(
                            nowMs = System.currentTimeMillis(),
                            lastAtMs = lastBackPressTime,
                            atExitPoint = navigator.isAtTabRoot(),
                        )) {
                            BackExitConfirmation.Decision.Pop -> navigator.goBack()
                            is BackExitConfirmation.Decision.Prompt -> {
                                lastBackPressTime = decision.nowMs
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.press_back_again_to_exit),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                            BackExitConfirmation.Decision.Exit -> {
                                lastBackPressTime = 0L
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            }
                        }
                    }
                    PhoneContent(
                        shellParams = shellParams,
                        isAudioPlayerScreen = isAudioPlayerScreen,
                        isExpanded = isExpanded,
                        bottomNavScrollVisibility = bottomNavScrollVisibility,
                        hideBottomNavOnScroll = preferences.hideBottomNavOnScroll,
                        bottomNavOffsetHeightPx = bottomNavOffsetHeightPx,
                        showMiniPlayer = showMiniPlayer,
                        audioPlaybackManager = audioPlaybackManager,
                        audioItemId = audioItemId,
                        onDismissMiniPlayer = { isMiniPlayerDismissed = true },
                        animatedNavBarColor = animatedNavBarColor,
                        showNavBarLabels = preferences.navBarShowLabels,
                        offlineMode = offlineMode,
                        isGoingOnline = isGoingOnline,
                        downloadCount = downloadCount,
                        onSurpriseClick = {
                            // Switch to Home first so the hero controller is
                            // composed, then fire the surprise signal it collects.
                            navigationState.topLevelRoute.value = Route.Home
                            model.requestSurprise()
                        },
                        onToggleOffline = { model.toggleOfflineMode() },
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
                    controller = mediaPreviewController,
                )
            }
            }
        }
    }
}
