package com.raulshma.jellyplay.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalFocusManager
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.remote.DisplayMessagePayload
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.ui.message.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.shell.ShellSessionController
import com.raulshma.jellyplay.feature.shell.navigation.RemoteNavigationDispatcher
import com.raulshma.jellyplay.feature.shell.navigation.ShellSectionRegistry
import com.raulshma.jellyplay.desktop.update.DesktopUpdateCheckController
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The shell-services holder extracted from [DesktopNavScaffold] (the
 * DesktopStartup/DesktopTrayActions extraction idiom, scaled up): ONE
 * plain class owning the CONSTRUCTION of every non-UI service the scaffold
 * composition needs, and — through [rememberDesktopShellServices] — the
 * COLLECTION effects that keep them alive. The scaffold keeps the chrome
 * (rail, NavDisplay, snackbar surface, key handling, the idle overlay's
 * rendering) and reads these through the properties below.
 *
 *  - the shell session policy (ADR 0001): [sessionController] — the shared
 *    [ShellSessionController] over this shell's own stores (no Koin
 *    binding, the same direct construction MainViewModel performs on
 *    Android), owning admin-status state + the 30 s refresh arbitration,
 *    homeMode collect/persist, and the revoke/plain logout fork, on this
 *    composition's scope so every job dies with the scaffold;
 *  - the About update check (ADR desktop-auto-update): [updateCheckController]
 *    — check→message mapping, browser handoff, snackbar wording; never a
 *    silent install, browser handoff only;
 *  - the dead-end guard: [sectionRegistry] + [guardedNavigator] — the
 *    shell-owned ledger plus the navigator adapter that surfaces unregistered
 *    routes as a snackbar (see [desktopGuardedNavigator] /
 *    [desktopDeadEndMessage], pinned by DesktopNavGuardTest). The registry
 *    starts EMPTY here; the scaffold's section-graph build attaches the
 *    shared sections into it, which is why the graph build itself stays in
 *    the scaffold — see the ordering note in [rememberDesktopShellServices];
 *  - the user-message sources: [userMessageSources] — the shared
 *    [UserMessageBus], the DesktopMusicMessageBus relay and the
 *    remote-control receiver's DisplayMessages (the receiver is the SAME
 *    Koin single DesktopAppRoot arms through
 *    RealtimeSessionController.create — collecting its displayMessages flow
 *    here does not re-arm it; see [desktopUserMessageSources] for the
 *    source order and the playEvents decision);
 *  - the remote navigation bridge collector: [remoteNavigation] — the shared
 *    [RemoteNavigationDispatcher] ladder over this shell's seams (pushes
 *    through [guardedNavigator], tab switches writing `topLevelRoute`
 *    directly, Compose focus moves, the synthesized AWT Enter for select,
 *    the context-menu fallback message); collected by
 *    [collectRemoteNavigation];
 *  - the idle ambient seam: [idleAmbientController] — DesktopIdleAmbient
 *    Controller (idle monitor + idle-gated active-remote-session count),
 *    started/stopped by the factory's DisposableEffect;
 *  - the live desktop audio core: [audioQueueManager], exposed because the
 *    scaffold's ShellHostHooks now-playing/ambient lambdas read it at click
 *    time (flows read lazily, never collected here);
 *  - the scaffold's remaining Koin reads: [networkMonitor],
 *    [navigationStore], [appRuntimeStateStore] and [appearanceStore]
 *    (beside the [authRepository] / [sharedUserMessageBus] collaborators
 *    exposed below) — the scaffold composition itself carries no
 *    koinInject of its own; only DesktopAppRoot's pre-scaffold window
 *    does, per the composition-order contract on
 *    [rememberDesktopShellServices].
 *
 * A plain class on constructor-injected collaborators (the
 * DesktopUpdateCheckController idiom, no Koin awareness of its own), so the
 * wiring is JVM-constructible and the scaffold's `remember` keys reduce to
 * the stable inputs of [rememberDesktopShellServices].
 *
 * @param scope the scaffold composition's scope — every service job
 *   (session arbitration, update check, guard snackbars, idle ticks) dies
 *   with the composition, exactly like the inline `scope.launch` calls this
 *   holder replaced.
 * @param navigation the scaffold's nav3 [NavigationState] — the guard's
 *   back stacks and the remote tab-switch seam write through it.
 * @param showMessage the snackbar sink shared by the update check, the
 *   dead-end guard and the remote-nav fallback messages.
 * @param moveFocus the composition's Compose focus seam — the remote
 *   MoveFocus target, pre-adapted from the FocusManager through
 *   [composeFocusDirection] by [rememberDesktopShellServices] (a lambda
 *   rather than the FocusManager itself so this holder stays free of the
 *   Compose-UI type and JVM-constructible, as its tests rely on).
 * @param windowRef Main.kt's AWT window ref — the remote select seam's
 *   Enter-key synthesis posts through it; content null until composed.
 */
internal class DesktopShellServices(
    private val scope: CoroutineScope,
    navigation: NavigationState,
    showMessage: suspend (String) -> Unit,
    moveFocus: (RemoteFocusDirection) -> Unit,
    windowRef: AtomicReference<ComposeWindow?>?,

    /**
     * ONE owner for the scaffold's auth reads: the session controller's
     * wiring here, the idle overlay's server/user identity lines in the
     * scaffold (resolved through [authRepository]). Exposed as a property
     * so the scaffold reads it through the holder instead of its own
     * koinInject — the pre-scaffold DesktopAppRoot window keeps its own
     * reads (the composition-order contract on
     * [rememberDesktopShellServices]).
     */
    val authRepository: AuthRepository,
    homeDiscoveryStore: HomeDiscoveryStore,
    appUpdateRepository: AppUpdateRepository,

    /**
     * The shared (commonMain) message bus the scaffold provides as
     * LocalUserMessageBus — the SAME instance [userMessageSources] collects
     * below, so messages posted through the composition local reach this
     * shell's snackbar.
     */
    val sharedUserMessageBus: UserMessageBus,
    musicMessageBus: MusicMessageBus,
    /**
     * The receiver's DisplayMessage flow — the host source [userMessageSources]
     * adds third (see [desktopUserMessageSources]). The receiver is the SAME
     * Koin single DesktopAppRoot arms through RealtimeSessionController.create;
     * the factory passes its `displayMessages` flow here (a plain flow, not
     * the receiver itself, so the holder stays free of the final transport
     * type and JVM-constructible, as its test relies on), and collecting the
     * flow does not re-arm the receiver.
     */
    remoteDisplayMessages: Flow<DisplayMessagePayload>,
    private val remoteNavigationBridge: RemoteNavigationBridge,
    val audioQueueManager: DesktopAudioQueueManager,
    activePlayerRegistry: ActivePlayerController,
    webSocketClient: JellyfinWebSocketClient,
    screensaverStore: ScreensaverStore,
    /**
     * The shell hooks' idle arms (feature 4.3): [collectIdleTransitions]
     * forwards this shell's idle-monitor transitions onto the shared
     * [NowPlayingReporter] event stream, where [DesktopHookRunner] consumes
     * them. The shared core:data single, injected like every other Koin
     * collaborator above.
     */
    private val idleReporter: NowPlayingReporter,

    /**
     * The scaffold's remaining Koin reads, pulled behind this holder so the
     * scaffold composition carries no koinInject of its own (only
     * DesktopAppRoot's pre-scaffold window does — the composition-order
     * contract on [rememberDesktopShellServices]):
     *  - [networkMonitor] — the rail's offline hide-set source and the
     *    LocalNetworkStatus flow (LIVE desktop connectivity);
     *  - [navigationStore] — bottom-nav customization (#152), the same
     *    store the phone settings write through;
     *  - [appRuntimeStateStore] — the first-run onboarding gate's one-shot
     *    `onboarding_completed` read;
     *  - [appearanceStore] — the stored manual layout override behind the
     *    adaptive shell wiring (#166).
     */
    val networkMonitor: NetworkMonitor,
    val navigationStore: NavigationStore,
    val appRuntimeStateStore: AppRuntimeStateStore,
    val appearanceStore: AppearanceStore,
) {
    /** ADR 0001's shared session-policy wiring — see class KDoc. */
    val sessionController = ShellSessionController(
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

    /** The About row's check controller (ADR desktop-auto-update). */
    val updateCheckController = DesktopUpdateCheckController(
        scope = scope,
        repository = appUpdateRepository,
        showMessage = showMessage,
    )

    /**
     * The shell-owned section ledger the dead-end guard derives from —
     * EMPTY until the scaffold's section-graph build (shellEntryProvider)
     * attaches the shared sections; guard reads before that attach see a
     * route as a dead end, which is the safe direction.
     */
    val sectionRegistry = ShellSectionRegistry()

    /** The guarded navigator over [sectionRegistry] — the guard's push seam. */
    val guardedNavigator: Navigator = desktopGuardedNavigator(
        navigation = navigation,
        isRegistered = sectionRegistry::isRegistered,
        scope = scope,
        showMessage = showMessage,
    )

    /**
     * The UserMessageHost source list — shared bus, music relay, receiver
     * DisplayMessages, in that order (see [desktopUserMessageSources]).
     */
    val userMessageSources: List<Flow<UserMessage>> = desktopUserMessageSources(
        sharedUserMessageBus = sharedUserMessageBus,
        musicMessageBus = musicMessageBus,
        remoteDisplayMessages = remoteDisplayMessages,
    )

    /**
     * The remote-navigation ladder over this shell's seams (the former
     * DesktopRemoteNavCollector wrapper folded away): pushes through
     * [guardedNavigator] (dead-end routes surface the guard's snackbar),
     * tab switches write `topLevelRoute` directly — NOT through the
     * navigator, whose pop-to-root-when-already-on-tab behavior must not
     * fire for a remote GoHome/GoToSettings/GoToSearch — ClosePlayer pops
     * player routes off every tab's stack, GoBack pops one entry, MoveFocus
     * drives the Compose FocusManager (through [composeFocusDirection]),
     * InvokeSelect synthesizes an AWT Enter pair posted through the system
     * event queue — the exact route every real keystroke takes into the
     * Compose preview-key chain — and OpenContextMenu keeps the default
     * never-consumed arm (no desktop context-menu affordance), falling back
     * to [DESKTOP_CONTEXT_MENU_UNAVAILABLE].
     */
    private val remoteNavigation = RemoteNavigationDispatcher(
        topLevelKeys = DESKTOP_TOP_LEVEL_ROUTES,
        navigate = guardedNavigator::navigate,
        selectTab = { route -> navigation.topLevelRoute.value = route },
        goBack = { guardedNavigator.goBack() },
        backStacks = { navigation.backStacks.values },
        presentMessage = showMessage,
        moveFocus = moveFocus,
        invokeSelect = { DesktopKeySynthesizer.postEnterKey(windowRef?.get()) },
    )

    /**
     * The idle "Ready to play" ambient controller — the monitor + the
     * idle-gated active-remote-session count off the receiver socket's
     * Sessions push; the scaffold keeps the collect reads, the overlay
     * rendering and the input-reset hook. Idle definition unchanged:
     * nothing playing (audio queue + engine registry), window active,
     * debounced timeout from the screensaver store's idle-ambient settings
     * (0 = off).
     */
    val idleAmbientController = DesktopIdleAmbientController(
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

    /**
     * The shell hooks' idle arms (feature 4.3): forwards this shell's
     * idle-monitor transitions onto the shared [NowPlayingReporter] event
     * stream. The initial StateFlow value is dropped — only real transitions
     * may fire hooks. Called from [rememberDesktopShellServices]'s effect
     * block, beside the remote-nav collector.
     */
    fun collectIdleTransitions() {
        scope.launch {
            idleAmbientController.isIdle
                .drop(1)
                .collect { idle -> idleReporter.reportIdle(idle) }
        }
    }

    /**
     * Consumes `RemoteNavigationBridge.targets` until cancellation — the
     * collect half of the remote-nav seam, driven by
     * [rememberDesktopShellServices]'s effect.
     */
    suspend fun collectRemoteNavigation() {
        remoteNavigation.collect(remoteNavigationBridge.targets, DESKTOP_CONTEXT_MENU_UNAVAILABLE)
    }
}

/**
 * Constructs and arms [DesktopShellServices] for one scaffold composition:
 * the `remember` over the stable inputs (Koin singles + the navigation /
 * snackbar / focus identities) plus the two collection effects — the
 * remote-nav collector and the idle-ambient start/stop — so the scaffold
 * itself carries no service wiring.
 *
 * COMPOSITION-ORDER CONTRACT: call this AFTER the scaffold's video-surface
 * probe registration and BEFORE its section-graph build. The graph build is
 * what attaches the shared sections into [DesktopShellServices.sectionRegistry]
 * (the refresh-registry provide rides the same build), and the Route.Video
 * Player registration inside it reads the probe — so the probe must already
 * be armed and this holder (which owns the registry the graph attaches into)
 * must already exist when the graph-build remember runs. Nothing inside this
 * factory composes UI, so its position only has to honor that ordering.
 */
@Composable
internal fun rememberDesktopShellServices(
    navigation: NavigationState,
    snackbarHostState: SnackbarHostState,
    windowRef: AtomicReference<ComposeWindow?>?,
): DesktopShellServices {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val showMessage: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    // The MoveFocus adapter (FocusManager + composeFocusDirection) the holder
    // receives as a plain lambda — remembered on the focus manager so the
    // holder's remember key below stays as stable as the old focusManager key.
    val moveFocus: (RemoteFocusDirection) -> Unit = remember(focusManager) {
        { direction -> focusManager.moveFocus(composeFocusDirection(direction)) }
    }

    // Koin singles (stable for the app lifetime — keyed on anyway, the same
    // discipline the scaffold's per-service remembers applied, so a rebound
    // single rebuilds the holder exactly where the inline remembers would
    // have rebuilt their one service).
    val authRepository: AuthRepository = koinInject()
    val homeDiscoveryStore: HomeDiscoveryStore = koinInject()
    val appUpdateRepository: AppUpdateRepository = koinInject()
    val sharedUserMessageBus: UserMessageBus = koinInject()
    val musicMessageBus: MusicMessageBus = koinInject()
    val remoteControlReceiver: RemoteControlReceiver = koinInject()
    val remoteDisplayMessages = remoteControlReceiver.displayMessages
    val remoteNavigationBridge: RemoteNavigationBridge = koinInject()
    val audioQueueManager: DesktopAudioQueueManager = koinInject()
    val activePlayerRegistry: ActivePlayerController = koinInject()
    val webSocketClient: JellyfinWebSocketClient = koinInject()
    val screensaverStore: ScreensaverStore = koinInject()
    val nowPlayingReporter: NowPlayingReporter = koinInject()
    val networkMonitor: NetworkMonitor = koinInject()
    val navigationStore: NavigationStore = koinInject()
    val appRuntimeStateStore: AppRuntimeStateStore = koinInject()
    val appearanceStore: AppearanceStore = koinInject()

    val services = remember(
        scope,
        navigation,
        snackbarHostState,
        moveFocus,
        windowRef,
        authRepository,
        homeDiscoveryStore,
        appUpdateRepository,
        sharedUserMessageBus,
        musicMessageBus,
        remoteDisplayMessages,
        remoteNavigationBridge,
        audioQueueManager,
        activePlayerRegistry,
        webSocketClient,
        screensaverStore,
        nowPlayingReporter,
        networkMonitor,
        navigationStore,
        appRuntimeStateStore,
        appearanceStore,
    ) {
        DesktopShellServices(
            scope = scope,
            navigation = navigation,
            showMessage = showMessage,
            moveFocus = moveFocus,
            windowRef = windowRef,
            authRepository = authRepository,
            homeDiscoveryStore = homeDiscoveryStore,
            appUpdateRepository = appUpdateRepository,
            sharedUserMessageBus = sharedUserMessageBus,
            musicMessageBus = musicMessageBus,
            remoteDisplayMessages = remoteDisplayMessages,
            remoteNavigationBridge = remoteNavigationBridge,
            audioQueueManager = audioQueueManager,
            activePlayerRegistry = activePlayerRegistry,
            webSocketClient = webSocketClient,
            screensaverStore = screensaverStore,
            idleReporter = nowPlayingReporter,
            networkMonitor = networkMonitor,
            navigationStore = navigationStore,
            appRuntimeStateStore = appRuntimeStateStore,
            appearanceStore = appearanceStore,
        )
    }

    // The remote-nav ladder collector: consumes the bridge's targets (no
    // replay, buffered 4) until the composition leaves.
    LaunchedEffect(services) {
        services.collectRemoteNavigation()
    }

    // The shell hooks' idle arms (feature 4.3): the idle monitor's
    // transitions forward onto the shared now-playing event stream.
    LaunchedEffect(services) {
        services.collectIdleTransitions()
    }

    // The idle-ambient lifecycle: ticks + the idle-gated session-count
    // collector run for exactly this composition, like the scaffold's own
    // DisposableEffect did.
    DisposableEffect(services, scope) {
        services.idleAmbientController.start(scope)
        onDispose { services.idleAmbientController.stop() }
    }

    return services
}
