package com.raulshma.jellyplay.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.PlayOnViewModel
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.designsystem.theme.backgroundBrush
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.components.ScrollDirectionVisibility
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.SHORTCUTS_NAV_KEY
import com.raulshma.jellyplay.core.ui.navigation.navIcon
import com.raulshma.jellyplay.core.ui.tv.TvScaffold
import com.raulshma.jellyplay.feature.shell.navigation.ShellHostHooks
import com.raulshma.jellyplay.navigation.components.ExpressiveFloatingNavigationBar
import com.raulshma.jellyplay.navigation.components.MoreToggleIcon
import kotlin.math.roundToInt
import androidx.tv.material3.MaterialTheme as TvMaterial3Theme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

/**
 * The shell-wide parameter bundle (the HomeCallbacks / MusicNavActions
 * idiom): the nav plumbing MainContent drills identically into all three
 * layout branches — [TvContent], [PhoneContent], [FullScreenContent] — and
 * that every branch forwards to [MainNavDisplay]. Extracted from the former
 * ~20-parameter composables so each branch takes this one @Immutable value
 * plus its genuinely layout-specific state.
 *
 * A constructor-arg bundle, not an interface: MainContent builds it inline
 * from locals it already holds, without any member-name shadowing. Every
 * field is a remembered/stable value or a Compose-memoized lambda, so the
 * data class compares equal across recompositions and the branches stay
 * skippable. ADR 0001 note: this carries UI wiring only — the session
 * policy it participates in (onLogout → ShellSessionController) stays
 * per-shell, exactly as before.
 */
@Immutable
data class ShellNavParams(
    val navigationState: NavigationState,
    /** The selected top-level tab, read once per MainContent recomposition. */
    val currentTopLevel: NavKey,
    /** Pure fold (core/ui VisibleTopLevelRoutes): the active route→label map. */
    val activeTopLevelRoutes: LinkedHashMap<Route, String>,
    val navigator: Navigator,
    /** ADR 0001: the revoke/plain sign-out fork into the session controller. */
    val onLogout: (Boolean) -> Unit,
    val homeMode: HomeMode,
    val onModeChange: (HomeMode) -> Unit,
    /** Hoisted in MainContent so saveable state survives layout-branch switches. */
    val saveableStateHolder: SaveableStateHolder,
    val entryDecorator: NavEntryDecorator<NavKey>,
    val onNowPlayingClick: () -> Unit,
    val onAmbientClick: () -> Unit,
    /** Play On controller — MainContent's single construction site. */
    val playOn: PlayOnViewModel,
    /**
     * The shell-host hooks behind the shared section graph — built once in
     * MainContent from the activity-scoped MainViewModel, so no type below
     * this bundle names that ViewModel.
     */
    val shellHost: ShellHostHooks,
)

/**
 * TV form-factor layout:_TV Material3 theme + [TvNavigationDrawer] host wrapping a single
 * [MainNavDisplay]. Extracted from [MainContent] so the per-form-factor scaffolding stays
 * isolated and the orchestrator stays a clean when-branch picker.
 */
@Composable
internal fun TvContent(
    shellParams: ShellNavParams,
    tvDrawerState: androidx.tv.material3.DrawerState,
    tvDrawerListState: androidx.compose.foundation.lazy.LazyListState,
    libraryFolders: List<com.raulshma.jellyplay.core.model.LibraryFolder>,
    hiddenNavItems: Set<String> = emptySet(),
    navItemOrder: List<String> = emptyList(),
    nowPlayingEnabled: Boolean,
    showMiniPlayer: Boolean,
    audioPlaybackManager: AudioPlaybackManager,
    onDismissMiniPlayer: () -> Unit,
) {
    val navigationState = shellParams.navigationState
    val currentTopLevel = shellParams.currentTopLevel
    val activeTopLevelRoutes = shellParams.activeTopLevelRoutes
    val navigator = shellParams.navigator
    val onNowPlayingClick = shellParams.onNowPlayingClick
    val audioTitle by audioPlaybackManager.title.collectAsStateWithLifecycle()
    val nowPlayingTitle = audioTitle.takeIf { nowPlayingEnabled }
    TvMaterial3Theme(
        colorScheme = tvDarkColorScheme(
            background = MaterialTheme.colorScheme.background,
            surface = MaterialTheme.colorScheme.surfaceContainer,
            onBackground = MaterialTheme.colorScheme.onSurface,
            onSurface = MaterialTheme.colorScheme.onSurface,
            primary = MaterialTheme.colorScheme.primary,
            onPrimary = MaterialTheme.colorScheme.onPrimary,
            secondary = MaterialTheme.colorScheme.secondary,
            onSecondary = MaterialTheme.colorScheme.onSecondary,
            border = MaterialTheme.colorScheme.outline,
            borderVariant = MaterialTheme.colorScheme.outlineVariant,
        )
    ) {
        TvScaffold {
            val tvCurrentRoute = navigationState.backStacks[currentTopLevel]?.lastOrNull()
            val tvIsSubPage = tvCurrentRoute != null && tvCurrentRoute !in activeTopLevelRoutes.keys

            // Hoist the nav-item list so it is only reallocated when the route
            // set changes, not on every MainContent recomposition (which fires
            // frequently — it reads audio title/artist/artwork for the mini-
            // player). Re-running .map{} here would allocate a fresh list + N
            // fresh TvNavItem instances each time, invalidating the drawer.
            // Shortcuts is no longer a top-level tab on phone (relocated to the
            // ⋮ overflow). The TV drawer has no overflow menu, so keep Shortcuts
            // reachable here as an explicit primary item — positioned by the
            // stored nav order like every other item (#152).
            val shortcutsLabel = stringResource(R.string.menu_shortcuts)
            val primaryNavItems = remember(activeTopLevelRoutes, shortcutsLabel, hiddenNavItems, navItemOrder) {
                tvPrimaryRoutes(
                    baseRoutes = activeTopLevelRoutes.keys.toList(),
                    includeShortcuts = SHORTCUTS_NAV_KEY !in hiddenNavItems,
                    navItemOrder = navItemOrder,
                ).map { route ->
                    // Shortcuts's label is a real string resource; the other
                    // routes carry their display label in activeTopLevelRoutes.
                    TvNavItem(
                        route = route,
                        label = if (route == Route.Shortcuts) {
                            shortcutsLabel
                        } else {
                            activeTopLevelRoutes.getValue(route)
                        },
                        icon = route.navIcon,
                    )
                }
            }
            TvNavigationDrawer(
                primaryItems = primaryNavItems,
                libraryFolders = libraryFolders,
                currentTopLevel = currentTopLevel,
                isSubPage = tvIsSubPage,
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                drawerState = tvDrawerState,
                drawerListState = tvDrawerListState,
                currentRoute = tvCurrentRoute,
                nowPlayingTitle = nowPlayingTitle,
                nowPlayingEnabled = nowPlayingEnabled,
                onNowPlayingClick = onNowPlayingClick,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavDisplay(shellParams = shellParams)
                    // TV mini-player transport: the drawer's "Now Playing" row only opens the
                    // full player, so without this overlay backgrounded audio has no D-pad-
                    // reachable pause/skip/close bar on TV. The components are already TV-aware
                    // (MiniPlayer applies tvFocusIndicator); this is the host
                    // wiring gap for TV navigation.
                    if (showMiniPlayer) {
                        AppMiniPlayerHost(
                            audioPlaybackManager = audioPlaybackManager,
                            title = audioTitle,
                            onNowPlayingClick = onNowPlayingClick,
                            onDismissMiniPlayer = onDismissMiniPlayer,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phone (and large-screen NavigationRail) layout: [NavigationSuiteScaffold] hosting
 * [MainNavDisplay] with floating mini-player(s) and the [ExpressiveFloatingNavigationBar].
 * All scroll-coupled bottom-nav state is owned here.
 */
@Composable
internal fun PhoneContent(
    shellParams: ShellNavParams,
    isAudioPlayerScreen: Boolean,
    isExpanded: Boolean,
    bottomNavScrollVisibility: ScrollDirectionVisibility,
    hideBottomNavOnScroll: Boolean,
    bottomNavOffsetHeightPx: androidx.compose.runtime.MutableFloatState,
    showMiniPlayer: Boolean,
    audioPlaybackManager: AudioPlaybackManager,
    audioItemId: String?,
    onDismissMiniPlayer: () -> Unit,
    animatedNavBarColor: Color,
    showNavBarLabels: Boolean,
    offlineMode: com.raulshma.jellyplay.core.model.OfflineMode = com.raulshma.jellyplay.core.model.OfflineMode.ONLINE,
    isGoingOnline: Boolean = false,
    downloadCount: Int = 0,
    onSurpriseClick: () -> Unit = {},
    onToggleOffline: () -> Unit = {},
) {
    val navigationState = shellParams.navigationState
    val currentTopLevel = shellParams.currentTopLevel
    val activeTopLevelRoutes = shellParams.activeTopLevelRoutes
    val navigator = shellParams.navigator
    val playOn = shellParams.playOn
    val onNowPlayingClick = shellParams.onNowPlayingClick
    val onAmbientClick = shellParams.onAmbientClick
    val systemNavBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Play On (cast-to-Jellyfin-session) lives at the app shell so the mini
    // transport persists across tabs. The controller is the parameter
    // threaded from MainContent's single construction site — this wiring
    // (mini bar + device sheet), MainNavDisplay's companion entry and the
    // Home redirect all read that one instance; nothing resolves it here.
    // uiState is the LOW-FREQUENCY slice only (connection, metadata,
    // play/pause): the per-tick position/duration/volume streams pass to the
    // mini bar as narrow flows and are collected at its leaf sliders, so the
    // ~1 Hz cast position tick no longer recomposes this whole shell scope.
    val playOnState by playOn.uiState.collectAsStateWithLifecycle()
    var showPlayOnSheet by remember { mutableStateOf(false) }
    val playOnContext = LocalContext.current
    val onPlayOnClick: () -> Unit = { showPlayOnSheet = true }
    // Current top-of-stack route — used to hide the persistent Play On mini bar
    // while its full-screen companion is open (avoid a bar floating over its own
    // expanded view).
    val currentRoute = navigationState.backStacks[navigationState.topLevelRoute.value]?.lastOrNull()
    val isPlayOnCompanionOpen = currentRoute is Route.PlayOnCompanion
    androidx.compose.runtime.LaunchedEffect(showPlayOnSheet) {
        if (showPlayOnSheet) playOn.startDiscovery(playOnContext)
    }

    // Global "More" overflow — a DotsVertical toggle docked in the nav bar
    // (both Classic and Expressive styles) that expands an animated list of
    // destinations upward, so Settings/Downloads/SyncPlay/Play On are reachable
    // from anywhere (#115). The expanded items render as an overlay above the
    // nav bar (see OverflowMenuItems / OverflowMenuScrim below).
    var isOverflowExpanded by remember { mutableStateOf(false) }
    val onOverflowToggle: (Boolean) -> Unit = { isOverflowExpanded = it }
    // Close the overflow on system/gesture back instead of navigating. Registered
    // only while the menu is open, so when it's disabled the global handler at
    // the call site (back = goBack / exit) takes over again. This also gives the
    // predictive-back system a real OnBackInvokedCallback for the overlay, which
    // stops the back-gesture preview indicator from freezing mid-screen when the
    // scrim's full-screen clickable intercepts the edge swipe (#115).
    BackHandler(enabled = isOverflowExpanded) { isOverflowExpanded = false }

    // When hide-on-scroll is disabled, keep the nav bar permanently visible
        //. The nestedScrollConnection is still constructed so its
        // identity stays stable, but it is only attached to the tree when the
        // setting is on.
        androidx.compose.runtime.LaunchedEffect(hideBottomNavOnScroll) {
            if (!hideBottomNavOnScroll) bottomNavScrollVisibility.resetToVisible()
        }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    // The policy (15px dead zone, hide on scroll-down / show
                    // on scroll-up, no at-top rule, no per-update gate) lives
                    // in the shared ScrollDirectionVisibility; this connection
                    // is only the feed.
                    bottomNavScrollVisibility.onScrollDelta(delta)
                    return Offset.Zero
                }
            }
        }

        NavigationSuiteScaffold(
            navigationSuiteType = if (!isExpanded) NavigationSuiteType.None else NavigationSuiteType.NavigationRail,
            navigationItems = {
                activeTopLevelRoutes.forEach { (route, label) ->
                    // Wrap in key(route) so per-item slot identity survives
                    // route reordering instead of being positional only.
                    androidx.compose.runtime.key(route) {
                        NavigationSuiteItem(
                            selected = route == currentTopLevel,
                            onClick = { navigator.navigate(route) },
                            icon = { NavIcon(route, label, selected = route == currentTopLevel) },
                            label = { Text(label) },
                        )
                    }
                }
                // Tablet NavigationRail has no floating nav bar, so the "More"
                // overflow toggle that phones carry in the nav bar is absent
                // here. Append it as a rail item so Settings/Downloads/SyncPlay/
                // Play On/Shortcuts/Surprise/Offline stay reachable (#115).
                // On phones navigationSuiteType is None, so this never renders.
                if (isExpanded) {
                    NavigationSuiteItem(
                        selected = isOverflowExpanded,
                        onClick = { onOverflowToggle(!isOverflowExpanded) },
                        icon = {
                            MoreToggleIcon(
                                isExpanded = isOverflowExpanded,
                                tint = androidx.compose.material3.LocalContentColor.current,
                                badgeCount = downloadCount,
                            )
                        },
                        label = { Text(stringResource(R.string.nav_more)) },
                    )
                }
            },
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = if (isAudioPlayerScreen) Color.Transparent else MaterialTheme.colorScheme.surface,
                navigationRailContainerColor = animatedNavBarColor,
            ),
        ) {
            // Gradient variants (Synthwave, Aurora) paint a full-bleed vertical
            // gradient instead of the flat M3 background colour.
            val variantBrush = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
                .backgroundBrush()
            val appBackgroundModifier = if (variantBrush != null) {
                Modifier.background(variantBrush)
            } else {
                Modifier.background(MaterialTheme.colorScheme.background)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(appBackgroundModifier)
                    .then(if (!isExpanded && hideBottomNavOnScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavDisplay(shellParams = shellParams)
                }
                if (showMiniPlayer) {
                    val audioTitle by audioPlaybackManager.title.collectAsStateWithLifecycle()
                    if (isExpanded) {
                        AppMiniPlayerHost(
                            audioPlaybackManager = audioPlaybackManager,
                            title = audioTitle,
                            onNowPlayingClick = onNowPlayingClick,
                            onDismissMiniPlayer = onDismissMiniPlayer,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = systemNavBarBottom + 2.dp)
                        )
                    } else {
                        AppMiniPlayerHost(
                            audioPlaybackManager = audioPlaybackManager,
                            title = audioTitle,
                            onNowPlayingClick = onNowPlayingClick,
                            onDismissMiniPlayer = onDismissMiniPlayer,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = systemNavBarBottom + 60.dp)
                                .offset {
                                    val maxOffset = Dimensions.floatingNavHeight.toPx()
                                    val yOffset = (-bottomNavOffsetHeightPx.floatValue).coerceAtMost(maxOffset)
                                    IntOffset(x = 0, y = yOffset.roundToInt())
                                }
                        )
                    }
                }
                // Play On persistent transport bar — visible while a Jellyfin
                // remote session is active and the full-screen companion is not
                // already open. Sits above the floating nav bar. The per-tick
                // transport streams pass through as narrow flows (collected at
                // the bar's leaf sliders) so this scope only recomposes on the
                // low-frequency slice — connection, metadata, play/pause.
                if (playOnState.isConnected && !isPlayOnCompanionOpen) {
                    com.raulshma.jellyplay.components.PlayOnMiniBar(
                        isVisible = playOnState.isConnected,
                        targetDeviceName = playOnState.targetDeviceName,
                        title = playOnState.title,
                        subtitle = playOnState.artist,
                        isPlaying = playOnState.isPlaying,
                        positionMsFlow = playOn.positionMsFlow,
                        durationMsFlow = playOn.durationMsFlow,
                        volumeFlow = playOn.volumeFlow,
                        onPlayPause = {
                            if (playOnState.isPlaying) playOn.castPause() else playOn.castPlay()
                        },
                        onSeek = { playOn.castSeekTo(it) },
                        onVolume = { playOn.setCastVolume(it) },
                        onDisconnect = { playOn.disconnect(playOnContext) },
                        onExpand = { navigator.navigate(Route.PlayOnCompanion) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = systemNavBarBottom + (if (!isExpanded) 72.dp else 8.dp)),
                    )
                }
                if (showPlayOnSheet) {
                    com.raulshma.jellyplay.components.PlayOnDeviceSheet(
                        devices = playOnState.devices,
                        onSelect = { device ->
                            playOn.connectAndFling(playOnContext, device)
                            showPlayOnSheet = false
                        },
                        onDismiss = {
                            playOn.stopDiscovery()
                            showPlayOnSheet = false
                        },
                    )
                }
                // Global "More" overflow — a DotsVertical toggle in the nav bar
                // (both Classic and Expressive styles) expands an animated list
                // of destinations upward, so Settings/Downloads/SyncPlay/Play On
                // are reachable from anywhere (#115). The items render as an
                // overlay above the nav bar (see OverflowMenuItems below).
                // On tablet the toggle lives in the NavigationRail (left edge),
                // so the pills dock top-start beside the rail and flow top-down;
                // on phone they dock bottom-end beside the floating nav toggle.
                if (isOverflowExpanded) {
                    com.raulshma.jellyplay.navigation.components.OverflowMenuScrim(
                        onDismiss = { isOverflowExpanded = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    com.raulshma.jellyplay.navigation.components.OverflowMenuItems(
                        onSurpriseClick = {
                            isOverflowExpanded = false
                            onSurpriseClick()
                        },
                        onSyncPlayClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.SyncPlay)
                        },
                        onDownloadsClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.Downloads)
                        },
                        onToggleOffline = {
                            // Guard re-taps while the offline→online transition is in flight.
                            if (!isGoingOnline) onToggleOffline()
                        },
                        onPlayOnClick = {
                            isOverflowExpanded = false
                            onPlayOnClick()
                        },
                        onShortcutsClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.Shortcuts)
                        },
                        onSettingsClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.Settings)
                        },
                        offlineMode = offlineMode,
                        isGoingOnline = isGoingOnline,
                        downloadCount = downloadCount,
                        alignToStart = isExpanded,
                        modifier = if (isExpanded) {
                            // The scaffold body already starts beside the rail, so a small
                            // margin keeps the pills flush to the drawer. Anchor at the top
                            // (under the status bar) so the list flows top-down.
                            Modifier.align(Alignment.TopStart)
                                .padding(start = 12.dp, top = statusBarTop + 8.dp)
                        } else {
                            Modifier.align(Alignment.BottomEnd)
                                .clearFloatingNav(extraBottom = 0.dp)
                                .padding(end = 16.dp, bottom = 4.dp)
                        },
                    )
                }
                if (!isExpanded) {
                    val navBarModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = systemNavBarBottom + 4.dp)
                        .padding(horizontal = 16.dp)
                        .offset { IntOffset(x = 0, y = -bottomNavOffsetHeightPx.floatValue.roundToInt()) }
                    ExpressiveFloatingNavigationBar(
                        routes = activeTopLevelRoutes,
                        currentTopLevel = currentTopLevel,
                        onNavigate = { navigator.navigate(it) },
                        showLabels = showNavBarLabels,
                        containerColor = animatedNavBarColor,
                        isOverflowExpanded = isOverflowExpanded,
                        onOverflowToggle = onOverflowToggle,
                        downloadCount = downloadCount,
                        modifier = navBarModifier,
                    )
                }
            }
        }
}

/**
 * Full-screen layout (player / onboarding / ambient / photo viewer): bare [Box] with
 * [MainNavDisplay]. Deliberately
 * omits drawer / nav-bar / mini-player chrome.
 */
@Composable
internal fun FullScreenContent(shellParams: ShellNavParams) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MainNavDisplay(shellParams = shellParams)
    }
}

@Composable
internal fun NavIcon(
    route: Route,
    label: String,
    selected: Boolean = false,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
    iconSize: Dp = 24.dp,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale",
    )
    Icon(
        imageVector = route.navIcon,
        contentDescription = label,
        tint = tint,
        modifier = androidx.compose.ui.Modifier
            .size(iconSize)
            .scale(scale),
    )
}
