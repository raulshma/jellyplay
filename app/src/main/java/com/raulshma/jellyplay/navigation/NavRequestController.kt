package com.raulshma.jellyplay.navigation

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.navigation.RemoteNavigationDispatcher
import com.raulshma.jellyplay.navigation.playbackhost.ExternalPlayerHost
import com.raulshma.jellyplay.navigation.playbackhost.HostDecision
import com.raulshma.jellyplay.navigation.playbackhost.PlaybackHostRouter
import com.raulshma.jellyplay.shell.ShellInfra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The request-dispatch half of `MainContent`, extracted into a remembered
 * holder (the `DesktopUpdateCheckController` idiom: a plain class with narrow
 * public members, constructed in one `remember`): the playback-host
 * navigateFilter and its [Navigator], the stateless [NavRequestCollector]
 * over that navigator's seams, and the per-loop collect members the shell's
 * keyed effects drive. Everything MainContent kept is layout dispatch —
 * preference-derived locals, the peek overlay, scroll visibility, the TV
 * drawer, back-exit, the three branch composables.
 *
 * STABILITY CONTRACT (why this is a remember at all): the holder is
 * constructed on the stable seams it captures — navigationState, model, infra,
 * the snackbar host, the external-player host+launcher pair, scope, context
 * (see [rememberNavRequestController]) — so [navigator] keeps ONE identity
 * for the composition's lifetime. That identity is what MainContent's
 * `shellHost` remember and MainNavDisplay's `remember(navigator, shellHost)`
 * section-graph memoization key on; a fresh `Navigator(...)` per
 * recomposition (the former inline construction) made both always miss and
 * re-invoked the ~25 section builders on every tab switch, preference write
 * or download-count emission. The one filter input that genuinely changes —
 * the preferred-player preference — arrives as the [preferredPlayer] getter
 * (a rememberUpdatedState read), so an engine flip re-decides at the next
 * navigate WITHOUT rebuilding the holder or the graph.
 *
 * CAPTURE CONTRACT (NavRequestCollector's documented semantics, unchanged):
 * the collector stays stateless — the holder constructs it exactly once per
 * holder instance, and the shell's effects keep their per-key re-capture:
 * the value-keyed pending-route effect re-captures the holder (and its
 * collector) on every new route, while the flow-keyed collectors hold their
 * launch-time instance. Because the holder's remember keys are exactly the
 * collector's captured seams, a holder rebuild is the only way those captures
 * go stale, and any effect that re-keys picks the rebuilt instance up.
 *
 * NOT here: the [ExternalPlayerHost] construction and its
 * ActivityResultLauncher — the host must be constructed BEFORE the launcher
 * (the launcher's result callback feeds `host.onResult`) and this holder
 * needs the launcher, so the pair stays in MainContent and arrives as
 * constructor seams; the [SnackbarHostState] (shared with the layout's
 * SnackbarHost and the `shellUserMessagePresent` adapter); and
 * message-presentation policy (`UserMessageHost`, shared/feature/shell — see
 * [NavRequestCollector]'s KDoc).
 */
internal class NavRequestController(
    private val navigationState: NavigationState,
    private val scope: CoroutineScope,
    private val context: Context,
    private val model: MainShellModel,
    private val infra: ShellInfra,
    private val snackbarHostState: SnackbarHostState,
    externalPlayerHost: ExternalPlayerHost,
    externalPlayerLauncher: ActivityResultLauncher<Intent>,
    private val preferredPlayer: () -> PlayerType,
) {

    /**
     * The shell's ONE filter-carrying [Navigator] — identity-stable for this
     * holder's lifetime (the class KDoc's stability contract: MainContent's
     * `shellHost` remember and MainNavDisplay's section-graph remember key
     * on it). The filter is the thin executing adapter for
     * [PlaybackHostRouter] — the single owner of the "which host mounts
     * playback" decision. ExternalPlayer → the injected host (its returned
     * position is credited via reportExternalPlaybackStopped, so Continue
     * Watching advances for regular videos and Live TV channels);
     * DedicatedActivity → PlayerActivity (system PiP floats over this browse
     * UI; back-stack choreography: shared taskAffinity, singleTask). Both
     * return false so the route never enters an in-nav back stack.
     * InNav/NotPlayback → true, the Navigator pushes normally. The player
     * preference is read through [preferredPlayer] at DECIDE time, so an
     * engine flip never rebuilds this navigator (or the graph behind it).
     */
    val navigator: Navigator = Navigator(
        navigationState,
        navigateFilter = { route: NavKey ->
            when (val decision = PlaybackHostRouter.decide(route, preferredPlayer())) {
                is HostDecision.ExternalPlayer -> {
                    scope.launch {
                        externalPlayerHost.launch(
                            request = decision.request,
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
        },
    )

    /**
     * The stateless collector over this holder's seams — constructed exactly
     * once per holder instance (the class KDoc's capture contract). Private:
     * the shell reaches its loops through the members below.
     */
    private val collector = NavRequestCollector(
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

    /**
     * Deep links / launcher shortcuts / shared-text targets
     * (`MainViewModel.pendingRoute`) — dispatch + the consume-once ack live in
     * [NavRequestCollector.dispatchPendingRoute]; this member is the
     * value-keyed effect's one-line launcher.
     */
    fun dispatchPendingRoute(route: Route?) = collector.dispatchPendingRoute(route)

    /**
     * Consume remote "Play" / "Playstate" / "GeneralCommand" navigation
     * requests emitted by the WebSocket receiver — the whole ladder is
     * [NavRequestCollector.collectRemoteNavigation]'s. The bridge resolves
     * INSIDE this member's body — it is only ever called from a
     * LaunchedEffect block, which runs after the frame applies, so the Koin
     * construction never runs during any composition pass.
     */
    suspend fun collectRemoteNavigation(contextMenuUnavailableMessage: String) {
        collector.collectRemoteNavigation(
            targets = infra.remoteNavigationBridgeLazy.value.targets,
            contextMenuUnavailableMessage = contextMenuUnavailableMessage,
        )
    }

    /**
     * Consume SyncPlay auto-open requests —
     * [NavRequestCollector.collectSyncPlayOpens]'s loop. The coordinator
     * resolves inside the member body for the same post-frame reason as
     * [collectRemoteNavigation].
     */
    suspend fun collectSyncPlayOpens() {
        collector.collectSyncPlayOpens(infra.syncPlayOpenCoordinatorLazy.value.openRequests)
    }

    /**
     * Consume remote-control play events into the now-playing snackbar —
     * [NavRequestCollector.collectNowPlayingSnackbars]'s loop; the title
     * fallback + template format are the shared
     * [RemoteNavigationDispatcher.nowPlayingMessage] fold. The receiver
     * resolves inside the member body for the same post-frame reason as
     * [collectRemoteNavigation].
     */
    suspend fun collectNowPlayingSnackbars(messageTemplate: String) {
        collector.collectNowPlayingSnackbars(
            events = infra.remoteControlReceiverLazy.value.playEvents,
            messageTemplate = messageTemplate,
        )
    }
}

/**
 * Constructs [NavRequestController] on its stability contract's keys — every
 * captured seam that can change identity is a remember key (scope and context
 * resolve here; the rest arrive as parameters), while the preferred-player
 * preference rides a [rememberUpdatedState] getter so preference writes never
 * rebuild the holder, its navigator, or the section graph memoized behind
 * them.
 */
@Composable
internal fun rememberNavRequestController(
    navigationState: NavigationState,
    model: MainShellModel,
    infra: ShellInfra,
    snackbarHostState: SnackbarHostState,
    externalPlayerHost: ExternalPlayerHost,
    externalPlayerLauncher: ActivityResultLauncher<Intent>,
    preferredPlayer: PlayerType,
): NavRequestController {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentPreferredPlayer by rememberUpdatedState(preferredPlayer)
    return remember(
        navigationState,
        model,
        infra,
        snackbarHostState,
        externalPlayerHost,
        externalPlayerLauncher,
        scope,
        context,
    ) {
        NavRequestController(
            navigationState = navigationState,
            scope = scope,
            context = context,
            model = model,
            infra = infra,
            snackbarHostState = snackbarHostState,
            externalPlayerHost = externalPlayerHost,
            externalPlayerLauncher = externalPlayerLauncher,
            preferredPlayer = { currentPreferredPlayer },
        )
    }
}
