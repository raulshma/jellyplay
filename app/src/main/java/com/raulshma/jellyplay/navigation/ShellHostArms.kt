package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.playback.ShellAudioSurface
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import com.raulshma.jellyplay.feature.shell.navigation.ShellCompositionArms
import com.raulshma.jellyplay.feature.shell.navigation.rememberShellComposition
import com.raulshma.jellyplay.shell.ShellInfra

/**
 * The shell-HOST arms of `MainContent`, extracted into its own holder (the
 * `NavRequestController` idiom): the Play On controller, the admin states,
 * the Play On sheet counter, the shared [rememberShellComposition] call that
 * builds the audio clicks / admin gate / [ShortcutActionSurface] /
 * [com.raulshma.jellyplay.feature.shell.navigation.ShellHostHooks] from this
 * shell's arms, and the [ShellNavParams] bundle everything below drills into
 * the three layout branches.
 *
 * This shell's ARMS (ADR-0001: wiring shared, arms per shell — the arms
 * VALUES here, the construction in shared/feature/shell's
 * [rememberShellComposition]): MainViewModel owns every session signal
 * (surprise, offline, admin, search prefill, home mode), the activity-scoped
 * Play On ViewModel is this shell's cast strategy, and every shortcut action
 * is armed — the unsupported arm is a defensive error, unreachable here.
 *
 * Exposed to MainContent: [shellParams] for the branch dispatch,
 * [isAdminState] for the overflow menu's admin-gated shortcut surfaces, and
 * [playOnOpenRequests] for the phone branch's Play On device sheet. The
 * hooks themselves ride [ShellNavParams.shellHost] — no duplicate scalars.
 */
internal class ShellHostArms(
    val shellParams: ShellNavParams,
    /** The collected admin flag — the overflow menu's admin-gated rows read it. */
    val isAdminState: State<Boolean>,
    /** The Play On device-sheet open counter — PhoneContent observes increments. */
    val playOnOpenRequests: MutableIntState,
)

/**
 * Constructs the [ShellHostArms] — the former `MainContent` region from the
 * audio-click construction through the [ShellNavParams] bundle, verbatim.
 * The holder is fresh per recomposition; every lambda member of the arms is
 * remembered on its capture (the [ShellCompositionArms] stability contract),
 * so the shared composition's remembers — and the section graph memoized
 * behind them — rebuild only when an input genuinely changes.
 */
@Composable
internal fun rememberShellHostArms(
    navigator: Navigator,
    navigationState: NavigationState,
    model: MainShellModel,
    onLogout: (Boolean) -> Unit,
    infra: ShellInfra,
    audioPlaybackManager: ShellAudioSurface,
    homeMode: HomeMode,
    currentTopLevel: NavKey,
    activeTopLevelRoutes: LinkedHashMap<Route, String>,
    saveableStateHolder: SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
): ShellHostArms {
    // Play On (cast-to-Jellyfin-session) controller — the ONE
    // construction site for the whole Play On surface family, hoisted
    // above the TV / phone / full-screen branches for the same reason
    // as the saveable state holder (MainContent): a runtime TV-mode flip (or
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
    // context) and threaded into the arms as States so the shared
    // composition builds the rememberShellAdminGate lazy reads over them —
    // the navigation entries, which are composed lazily, observe the
    // latest value without re-building the entry graph. The flows
    // arrive through the [MainShellModel] seam; nothing below this
    // point reaches past it.
    val isAdminState = model.isAdmin.collectAsStateWithLifecycle()
    val isRefreshingAdminState = model.isRefreshingAdmin.collectAsStateWithLifecycle()

    // The Play On device sheet's state lives in PhoneContent; header
    // shortcuts request it through this counter (the sheet-owning
    // scope observes increments).
    val playOnOpenRequests = remember { mutableIntStateOf(0) }

    // This shell's arms for the shared composition — every lambda remembered
    // on its capture so the data-class arms compare equal across
    // recompositions (the ShellCompositionArms stability contract).
    //  - The shared home module narrows the Play-On surface to its
    //    HomePlayOnRedirect seam (the concrete strategy is Android-
    //    bound); the probe + fling choreography lives on the controller
    //    (flingIfConnected), so the strategy never leaves it. The
    //    redirect is active on EVERY host — TV and full-screen
    //    MainNavDisplay calls included. Only observable when
    //    a remote session is already connected, which itself can only
    //    be initiated from the phone layout's Play On device sheet.
    //    Remembered on the controller, keyed on playOn.
    //  - onModeChange is stable on purpose: it is a key of the shellHost
    //    construction (the rememberShellHost factory keys on it
    //    structurally through the arms), so a fresh lambda per
    //    recomposition would make the hooks compare unequal and rebuild
    //    the section graph. Remembered on the model, whose activity scope
    //    is the lambda's only capture.
    //  - Header-shortcut shell actions — the ONE execution surface behind
    //    every action pill (home dock's header rows, the overflow menu's
    //    action fold): the shared ShortcutActionSurface owns the
    //    capability verdict and the navigate-home-before-arm-surprise
    //    ordering. Android arms all three actions (MainViewModel
    //    + the Play On sheet counter), so the unsupported arm is a
    //    defensive error — it is unreachable from this shell.
    val arms = ShellCompositionArms(
        audioSurface = audioPlaybackManager,
        surpriseRequests = model.surpriseRequests,
        armSurprise = remember(model) { model::requestSurprise },
        playOnRedirect = remember(playOn) { HomePlayOnRedirect(playOn::flingIfConnected) },
        requestPlayOn = remember(playOnOpenRequests) { { playOnOpenRequests.value += 1 } },
        toggleOffline = remember(model) { model::toggleOfflineMode },
        onUnsupportedShortcutAction = remember {
            { action: ShortcutAction ->
                error("ShortcutActionSurface: the Android shell arms every action; $action cannot be unsupported")
            }
        },
        homeMode = homeMode,
        onHomeModeChange = remember(model) { model::setHomeMode },
        isAdminState = isAdminState,
        isRefreshingAdminState = isRefreshingAdminState,
        onRefreshAdmin = remember(model) { { model.refreshAdminStatus() } },
        onLogout = onLogout,
        onCheckForUpdates = remember(infra) {
            { infra.updateCoordinatorLazy.value.manualCheckForUpdate() }
        },
        pendingSearchQuery = model.pendingSearchQuery,
        onConsumeSearchQuery = remember(model) {
            model::consumePendingSearchQuery
        },
    )

    // The shell-host hooks (ShellHostHooks) behind the shared section
    // graph, built through the shared rememberShellComposition — the ONE
    // construction site for the audio clicks, the admin gate's lazy reads,
    // the ShortcutActionSurface and the five hook bundles (the desktop's
    // DesktopNavScaffold feeds the same arms; the construction lives in
    // shared/feature/shell). The model's signals (admin gate, update
    // check, surprise/search prefill) and the Play On controller meet the
    // shell seam HERE — this call site is the value wiring. Every factory
    // parameter is a remember key, so the graph rebuilds only when these
    // identities change.
    val shellHost = rememberShellComposition(navigator, arms)

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

    return ShellHostArms(
        shellParams = shellParams,
        isAdminState = isAdminState,
        playOnOpenRequests = playOnOpenRequests,
    )
}
