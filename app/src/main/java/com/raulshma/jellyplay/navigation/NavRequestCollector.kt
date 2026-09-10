package com.raulshma.jellyplay.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
import com.raulshma.jellyplay.core.data.remote.PlayEventPayload
import com.raulshma.jellyplay.core.ui.feedback.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessage as SharedUserMessage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.UserMessageDuration
import com.raulshma.jellyplay.feature.shell.UserMessageHost
import com.raulshma.jellyplay.feature.shell.resolveUiText
import com.raulshma.jellyplay.shell.SyncPlayOpenRequest
import kotlinx.coroutines.flow.Flow

/**
 * One home for the Android shell's nav-request collectors — the five
 * collect-then-dispatch loops JellyPlayApp's `MainContent` used to hand-copy
 * composable-inline: the pending-route (deep link / shortcut) dispatch, the
 * remote-navigation collector, the remote-control now-playing snackbar, the
 * SyncPlay auto-open guard, and (via [shellUserMessageHost] below) the
 * message-bus adaptation seams. Every loop is the same shape — collect an
 * external request, apply one small policy fork, drive the navigator or the
 * snackbar host — and the forks are pure companion folds
 * ([pendingRouteDispatch], [syncPlayAutoOpenRoute],
 * [nowPlayingSnackbarMessage]) in the [RemoteNavigationRouting] style, so
 * both halves are JVM-pinned through fake lambdas
 * (`NavRequestCollectorTest`; the PinGateController shape).
 *
 * Capture semantics the shell relies on, preserved from the inline loops:
 * this controller is STATELESS, so `MainContent` constructs it inline (no
 * `remember`) and each `LaunchedEffect` captures the instance current when
 * it (re)launches — exactly as the former inline collectors captured
 * `navigator`, whose `navigateFilter` carries the playback-host decision
 * (external player / dedicated activity / in-nav). The value-keyed
 * pending-route effect therefore re-captures on every new route, while the
 * flow-keyed collectors hold their launch-time instance.
 *
 * NOT here: message-presentation POLICY (serial merge→resolve→present,
 * severity → duration) — that is [UserMessageHost]'s (shared/feature/shell);
 * the adapters at the bottom of this file only construct the host and
 * project the legacy bus's payload. The external-player launch protocol
 * lives in `ExternalPlayerHost` (navigation/playbackhost, beside
 * `PlaybackHostRouter`) — it is STATEFUL (the pending-launch stash), so it
 * is remembered rather than constructed inline like this collector.
 *
 * @param topLevelKeys the shell's registered top-level tab routes
 *   (`ALL_TOP_LEVEL_ROUTE_KEYS`) — the tab-vs-nested fork's vocabulary.
 * @param navigate the filter-carrying push seam (`Navigator::navigate`) —
 *   the playback-host decision must apply to every pushed route.
 * @param selectTopLevelTab the tab-switch seam; deliberately bypasses
 *   [navigate] because the pending-route fork switches tabs WITHOUT the
 *   Navigator's pop-to-root-when-already-on-tab behavior (see
 *   [pendingRouteDispatch]).
 * @param backStacks provider over the live
 *   [com.raulshma.jellyplay.core.ui.navigation.NavigationState] back stacks
 *   — read per request, never cached, so pops and guards observe the
 *   current stacks.
 * @param consumePendingRoute the consume-once ack for a dispatched pending
 *   route (`MainViewModel.consumePendingRoute`).
 * @param presentSnackbar the now-playing surface — the shell's
 *   `SnackbarHostState` (dismiss action on), duration left to the host
 *   default exactly as the inline collector did.
 */
internal class NavRequestCollector(
    private val topLevelKeys: Set<Route>,
    private val navigate: (NavKey) -> Unit,
    private val selectTopLevelTab: (Route) -> Unit,
    private val backStacks: () -> Collection<MutableList<NavKey>>,
    private val consumePendingRoute: () -> Unit,
    private val presentSnackbar: suspend (message: String) -> Unit,
) {

    /**
     * The tab-vs-nested fork for one shell-pending route — the decision the
     * inline `pendingRoute` collector made with its `if
     * (ALL_TOP_LEVEL_ROUTE_KEYS.contains(route))`. A top-level route SWITCHES
     * the tab directly (deliberately NOT via the Navigator: it must not
     * pop-to-root when the tab is already selected, and it must not run the
     * navigate filter — tab switches are never playback routes); anything
     * else is a normal push through the filter. `null` (no pending route) is
     * a no-op that must not consume.
     */
    sealed interface PendingRouteDispatch {
        data class SwitchTab(val route: Route) : PendingRouteDispatch
        data class Push(val route: Route) : PendingRouteDispatch
        data object None : PendingRouteDispatch
    }

    /**
     * Dispatches one shell-pending route (deep links, launcher shortcuts,
     * shared-text targets — `MainViewModel.pendingRoute`). Synchronous by
     * design: the shell drives it from a value-keyed effect over the
     * lifecycle-aware state, and the consume-once ack must land in the same
     * call as the navigation (dispatch FIRST, then consume — a consume
     * before the dispatch would race the StateFlow back to null and drop
     * the route).
     */
    fun dispatchPendingRoute(route: Route?) {
        when (val action = pendingRouteDispatch(route, topLevelKeys)) {
            is PendingRouteDispatch.SwitchTab -> selectTopLevelTab(action.route)
            is PendingRouteDispatch.Push -> navigate(action.route)
            PendingRouteDispatch.None -> return
        }
        consumePendingRoute()
    }

    /**
     * Consume remote "Play" / "Playstate" / "GeneralCommand" navigation
     * requests emitted by the WebSocket receiver
     * (`RemoteNavigationBridge.targets`). The target→route mapping and the
     * Jellyfin-web "Stop" pop (`ClosePlayer` → player entries off the top of
     * EVERY back stack) are [RemoteNavigationRouting]'s pure folds; pushed
     * routes go through the filter-carrying [navigate] seam.
     */
    suspend fun collectRemoteNavigation(targets: Flow<NavigationTarget>) {
        targets.collect { target ->
            if (target is NavigationTarget.ClosePlayer) {
                popPlayerRoutes(backStacks())
            } else {
                routeForNavigationTarget(target)?.let(navigate)
            }
        }
    }

    /**
     * Consume SyncPlay auto-open requests
     * (`SyncPlayOpenCoordinator.openRequests`): a joined group started
     * playing (or switched items) → open the video player. The player-open
     * guard is the pure [syncPlayAutoOpenRoute] fold.
     */
    suspend fun collectSyncPlayOpens(requests: Flow<SyncPlayOpenRequest>) {
        requests.collect { request ->
            syncPlayAutoOpenRoute(request, backStacks())?.let(navigate)
        }
    }

    /**
     * Consume remote-control play events (`RemoteControlReceiver.playEvents`)
     * into the now-playing snackbar. The title fallback (blank title → item
     * id) and the template format are the pure [nowPlayingSnackbarMessage]
     * fold; the surface itself is the injected [presentSnackbar] seam.
     */
    suspend fun collectNowPlayingSnackbars(
        events: Flow<PlayEventPayload>,
        messageTemplate: String,
    ) {
        events.collect { event ->
            presentSnackbar(nowPlayingSnackbarMessage(event, messageTemplate))
        }
    }

    companion object {

        /** The pure half of [dispatchPendingRoute] — see that member's KDoc. */
        fun pendingRouteDispatch(route: Route?, topLevelKeys: Set<Route>): PendingRouteDispatch =
            when {
                route == null -> PendingRouteDispatch.None
                topLevelKeys.contains(route) -> PendingRouteDispatch.SwitchTab(route)
                else -> PendingRouteDispatch.Push(route)
            }

        /**
         * The SyncPlay auto-open guard: return the player route to push, or
         * null when a [Route.VideoPlayer] already sits ON TOP of any back
         * stack — that player's SyncPlayBridge drives the item load in
         * place, and pushing another VideoPlayer here would stack duplicate
         * player screens. Top-only by design: a player buried below a
         * non-player top does NOT veto (its screen is not visible), and an
         * AudioPlayer top does not either (it is not the SyncPlay surface).
         */
        fun syncPlayAutoOpenRoute(
            request: SyncPlayOpenRequest,
            backStacks: Collection<List<NavKey>>,
        ): Route.VideoPlayer? =
            if (backStacks.any { it.lastOrNull() is Route.VideoPlayer }) {
                null
            } else {
                Route.VideoPlayer(
                    itemId = request.itemId,
                    startPositionTicks = request.startPositionTicks,
                )
            }

        /**
         * The now-playing snackbar fold: the item's display title, falling
         * back to the raw item id when the server sent a blank title, into
         * the localized template (`snackbar_now_playing`, one %s).
         */
        fun nowPlayingSnackbarMessage(event: PlayEventPayload, messageTemplate: String): String =
            messageTemplate.format(event.title.ifBlank { event.itemId })
    }
}

/**
 * Constructs the Android shell's [UserMessageHost] — the present adapter the
 * shared host needs: TV renders a system Toast (the TV layout has no root
 * SnackbarHost), phone renders the shell's [SnackbarHostState] snackbar
 * (accessible, dismissible). Owns ONLY the surface fork and the duration
 * mappings; the serial merge→resolve→present choreography and the
 * severity → duration policy stay in the shared host (`UserMessageHost`).
 * `MainContent` remembers the result keyed on `isTv`, exactly as it did when
 * this adapter was inline.
 */
internal fun shellUserMessageHost(
    context: Context,
    isTv: Boolean,
    snackbarHostState: SnackbarHostState,
): UserMessageHost = UserMessageHost(
    resolveText = ::resolveUiText,
    present = { text, duration ->
        if (isTv) {
            Toast.makeText(context, text, toastDurationFor(duration)).show()
        } else {
            snackbarHostState.showSnackbar(
                message = text,
                withDismissAction = true,
                duration = snackbarDurationFor(duration),
            )
        }
    },
)

/**
 * The legacy (`core:ui` feedback) bus's severity projected onto the shared
 * bus's vocabulary — the adaptation `UserMessageHost.hostAdapted` needs for
 * a shell-owned payload type the shared module cannot name. Exhaustive: a
 * new legacy arm is a compile-time decision.
 */
internal fun legacySeverityOf(message: UserMessage): SharedUserMessage.Severity = when (message) {
    is UserMessage.Error -> SharedUserMessage.Severity.Error
    is UserMessage.Info -> SharedUserMessage.Severity.Info
}

/** TV surface: the shared duration policy onto Toast constants. */
internal fun toastDurationFor(duration: UserMessageDuration): Int = when (duration) {
    UserMessageDuration.Long -> Toast.LENGTH_LONG
    UserMessageDuration.Short -> Toast.LENGTH_SHORT
}

/** Phone surface: the shared duration policy onto snackbar durations. */
internal fun snackbarDurationFor(duration: UserMessageDuration): SnackbarDuration = when (duration) {
    UserMessageDuration.Long -> SnackbarDuration.Long
    UserMessageDuration.Short -> SnackbarDuration.Short
}
