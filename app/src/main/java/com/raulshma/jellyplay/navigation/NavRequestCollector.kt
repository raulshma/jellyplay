package com.raulshma.jellyplay.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.PlayEventPayload
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.ui.feedback.UserMessage
import com.raulshma.jellyplay.core.ui.message.UserMessage as SharedUserMessage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.UserMessageDuration
import com.raulshma.jellyplay.feature.shell.navigation.RemoteNavigationDispatcher
import com.raulshma.jellyplay.feature.shell.navigation.RemoteRouteDispatch
import com.raulshma.jellyplay.shell.SyncPlayOpenRequest
import kotlinx.coroutines.flow.Flow

/**
 * One home for the Android shell's nav-request collectors — the five
 * collect-then-dispatch loops JellyPlayApp's `MainContent` used to hand-copy
 * composable-inline: the pending-route (deep link / shortcut) dispatch, the
 * remote-navigation collector, the remote-control now-playing snackbar, and
 * the SyncPlay auto-open guard. Beside them live the message-bus adaptation
 * seams ([shellUserMessagePresent] / [legacySeverityOf] below) — the former
 * sixth loop, the hand-copied message collectors, now reduced to feeding the
 * shared `rememberShellUserMessages` seam. Every loop is the same shape — collect an
 * external request, apply one small policy fork, drive the navigator or the
 * snackbar host — and the forks are the shared
 * `feature.shell.navigation.RemoteNavigationDispatcher` folds themselves (the
 * one remote-navigation LADDER both shells run — ladder dispatch, tab-vs-push
 * policy, SyncPlay guard and now-playing format included; this class adds NO
 * local dispatch vocabulary), so the fold tables are JVM-pinned once in
 * `RemoteNavigationDispatcherTest` while the collect-then-dispatch
 * choreography is pinned here through fake lambdas
 * (`NavRequestCollectorTest`; the PinGateController shape).
 *
 * Capture semantics the shell relies on, preserved from the inline loops:
 * this controller is STATELESS — the remembered `NavRequestController`
 * (MainContent's request-dispatch holder) constructs it once per holder
 * instance over the holder's stable seams — and each `LaunchedEffect`
 * captures the instance current when it (re)launches, exactly as the former
 * inline collectors captured `navigator`, whose `navigateFilter` carries the
 * playback-host decision (external player / dedicated activity / in-nav). The
 * value-keyed pending-route effect therefore re-captures on every new route,
 * while the flow-keyed collectors hold their launch-time instance.
 *
 * NOT here: message-presentation POLICY (serial merge→resolve→present,
 * severity → duration) — that is
 * [com.raulshma.jellyplay.feature.shell.UserMessageHost]'s
 * (shared/feature/shell), whose composition wiring is the shared
 * `rememberShellUserMessages` seam; the adapters at the bottom of this file
 * only fork the surface ([shellUserMessagePresent]) and project the legacy
 * bus's payload ([legacySeverityOf]). The external-player launch protocol
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
    private val goBack: () -> Unit,
    private val dispatchKey: ((Int) -> Boolean)?,
) {

    /**
     * The shared ladder (feature.shell.navigation
     * [RemoteNavigationDispatcher]) over this collector's seams — the
     * exhaustive when(NavigationTarget) and the tab-vs-push fork are ITS now,
     * not a hand-copy. The Android-specific arms are lambdas over
     * [dispatchKey], keeping the keycode vocabulary in the :app
     * `RemoteNavigationRouting.kt`: D-pad directions and the select arm
     * synthesize their keycodes unconditionally, while the context-menu arm
     * reports consumption so an unconsumed menu key falls back to the
     * standard message. Pushes ride the filter-carrying [navigate] seam; tab
     * switches ride [selectTopLevelTab] (bypassing the Navigator — no
     * pop-to-root on the selected tab); the context-menu fallback presents
     * through [presentSnackbar], exactly as this class's former inline
     * ladder did.
     */
    private val remoteNavigation = RemoteNavigationDispatcher(
        topLevelKeys = topLevelKeys,
        navigate = navigate,
        selectTab = selectTopLevelTab,
        goBack = goBack,
        backStacks = backStacks,
        presentMessage = presentSnackbar,
        moveFocus = { direction -> dispatchKey?.invoke(keyCodeForFocusDirection(direction)) },
        invokeSelect = { dispatchKey?.invoke(REMOTE_SELECT_KEYCODE) },
        contextMenuKey = { dispatchKey?.invoke(REMOTE_CONTEXT_MENU_KEYCODE) == true },
    )

    /**
     * Dispatches one shell-pending route (deep links, launcher shortcuts,
     * shared-text targets — `MainViewModel.pendingRoute`). Synchronous by
     * design: the shell drives it from a value-keyed effect over the
     * lifecycle-aware state, and the consume-once ack must land in the same
     * call as the navigation (dispatch FIRST, then consume — a consume
     * before the dispatch would race the StateFlow back to null and drop
     * the route). A `null` route is a no-op that must not consume.
     */
    fun dispatchPendingRoute(route: Route?) {
        when (val action = pendingRouteDispatch(route, topLevelKeys)) {
            is RemoteRouteDispatch.SwitchTab -> selectTopLevelTab(action.route)
            is RemoteRouteDispatch.Push -> navigate(action.route)
            null -> return
        }
        consumePendingRoute()
    }

    /**
     * Consume remote "Play" / "Playstate" / "GeneralCommand" navigation
     * requests emitted by the WebSocket receiver
     * (`RemoteNavigationBridge.targets`). The whole ladder — the
     * target→route mapping, the Jellyfin-web "Stop" pop
     * (`ClosePlayer` → player entries off the top of EVERY back stack), the
     * navigation-ladder arms and the tab-vs-push fork — is
     * shared/feature/shell's [RemoteNavigationDispatcher], constructed over
     * this class's seams as [remoteNavigation]; this member is the Android
     * wiring only (keycode vocabulary, filter-carrying push, direct
     * tab-switch seam).
     */
    suspend fun collectRemoteNavigation(
        targets: Flow<NavigationTarget>,
        contextMenuUnavailableMessage: String,
    ) {
        remoteNavigation.collect(targets, contextMenuUnavailableMessage)
    }

    /**
     * Consume SyncPlay auto-open requests
     * (`SyncPlayOpenCoordinator.openRequests`): a joined group started
     * playing (or switched items) → open the video player. The player-open
     * guard is the shared
     * [RemoteNavigationDispatcher.syncPlayAutoOpenRoute] fold (the request is
     * spread to fields because the request type is app-owned); its result —
     * null when a player already tops a back stack and drives the item load
     * in place — rides the filter-carrying [navigate] seam.
     */
    suspend fun collectSyncPlayOpens(requests: Flow<SyncPlayOpenRequest>) {
        requests.collect { request ->
            RemoteNavigationDispatcher.syncPlayAutoOpenRoute(
                itemId = request.itemId,
                startPositionTicks = request.startPositionTicks,
                backStacks = backStacks(),
            )?.let(navigate)
        }
    }

    /**
     * Consume remote-control play events (`RemoteControlReceiver.playEvents`)
     * into the now-playing snackbar. The title fallback (blank title → item
     * id) and the template format are the shared
     * [RemoteNavigationDispatcher.nowPlayingMessage] fold; the surface itself
     * is the injected [presentSnackbar] seam.
     */
    suspend fun collectNowPlayingSnackbars(
        events: Flow<PlayEventPayload>,
        messageTemplate: String,
    ) {
        events.collect { event ->
            presentSnackbar(RemoteNavigationDispatcher.nowPlayingMessage(event, messageTemplate))
        }
    }

    companion object {

        /**
         * The pure half of [dispatchPendingRoute] — see that member's KDoc.
         * The decision table is the shared
         * [RemoteNavigationDispatcher.routeDispatch], returned in its own
         * [RemoteRouteDispatch] vocabulary with no local rename; this
         * wrapper only adds the null arm the nullable pending route needs
         * (`null` route → `null` dispatch — "no decision", which
         * [dispatchPendingRoute] must not consume).
         */
        fun pendingRouteDispatch(route: Route?, topLevelKeys: Set<Route>): RemoteRouteDispatch? =
            route?.let { pendingRoute ->
                RemoteNavigationDispatcher.routeDispatch(pendingRoute, topLevelKeys)
            }
    }
}

/**
 * The Android shell's present adapter for the shared
 * `rememberShellUserMessages` seam — the surface fork the host needs: TV
 * renders a system Toast (the TV layout has no root SnackbarHost), phone
 * renders the shell's [SnackbarHostState] snackbar (accessible,
 * dismissible). Owns ONLY the surface fork and the duration mappings; host
 * construction, the serial merge→resolve→present choreography and the
 * severity → duration policy stay in the shared seam.
 */
internal fun shellUserMessagePresent(
    context: Context,
    isTv: Boolean,
    snackbarHostState: SnackbarHostState,
): suspend (String, UserMessageDuration) -> Unit = { text, duration ->
    if (isTv) {
        Toast.makeText(context, text, toastDurationFor(duration)).show()
    } else {
        snackbarHostState.showSnackbar(
            message = text,
            withDismissAction = true,
            duration = snackbarDurationFor(duration),
        )
    }
}

/**
 * The legacy (`core:ui` feedback) bus's severity projected onto the shared
 * bus's vocabulary — the adaptation
 * `com.raulshma.jellyplay.feature.shell.UserMessageHost.hostAdapted` needs
 * for a shell-owned payload type the shared module cannot name. Exhaustive:
 * a new legacy arm is a compile-time decision.
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
