package com.raulshma.jellyplay.feature.shell.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.remote.PlayEventPayload
import com.raulshma.jellyplay.core.model.remote.NavigationTarget
import com.raulshma.jellyplay.core.model.remote.RemoteFocusDirection
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.flow.Flow

/**
 * The ONE remote-navigation ladder both shells run — the exhaustive
 * when([NavigationTarget]) the Android `NavRequestCollector` and the
 * desktop shell used to hand-copy beside the
 * [RemoteNavigationRouting] folds. The hand-copies had drifted (the
 * survey-measured fork): desktop routed [NavigationTarget.GoToTopLevel]
 * through its navigator, whose pop-to-root-when-already-on-tab behavior
 * (`Navigator.navigate`) the Android shell deliberately bypasses — so the
 * same remote "GoHome" popped desktop's current tab to its root while
 * Android only switched. This class owns the tab-vs-push policy ONCE
 * ([dispatch] / [routeDispatch]): a route that names one of the shell's
 * [topLevelKeys] SWITCHES the tab (never pops, never runs the push filter —
 * tab switches are never playback routes), everything else pushes. Android
 * always had the select semantics; desktop inherited them with the fold.
 *
 * Per-shell seams are constructor lambdas (the [ShellHostHooks]
 * constructor-arg idiom, no interface): the push seam (desktop passes its
 * guarded navigator so dead-end routes surface the guard's message; Android
 * the filter-carrying `Navigator::navigate` whose navigateFilter carries the
 * playback-host decision), the tab-switch seam (writes `topLevelRoute`
 * directly, bypassing the Navigator), back pop, the live back stacks (read
 * per request, never cached, so the ClosePlayer pop sees current stacks),
 * the message sink (context-menu fallback), and the focus / select /
 * context-menu-key arms where the shells genuinely diverge (Android
 * synthesizes D-pad/center/menu keycodes, desktop moves the Compose
 * FocusManager / posts an AWT Enter). No compose, no navigator type — so the
 * ladder is JVM-pinnable through fake lambdas
 * (`RemoteNavigationDispatcherTest`; the RemoteNavigationRouting precedent).
 *
 * The two per-shell collectors that are NOT part of this ladder (Android's
 * SyncPlay auto-open and now-playing snackbar loops) dispatch through the
 * same sinks and the companion folds below, so their policies live here too.
 * Desktop arms none of their flows DELIBERATELY: it has no SyncPlay
 * coordinator, and since the receiver port the shell arms the receiver's
 * other outputs instead — its DisplayMessages ride the user-message host
 * while its playEvents stay uncollected (the desktop dispatcher twins
 * already open the player for every remote Play, which is the confirmation
 * the now-playing banner exists to give; the decision is documented at the
 * collection seam in `desktopUserMessageSources`).
 */
class RemoteNavigationDispatcher(
    /**
     * The shell's registered top-level tab routes (Android's
     * `ALL_TOP_LEVEL_ROUTE_KEYS`, desktop's `DESKTOP_TOP_LEVEL_ROUTES`) — the
     * tab-vs-push fork's vocabulary.
     */
    private val topLevelKeys: Set<Route>,
    /** The push seam — every pushed remote route rides it (and its filter). */
    private val navigate: (NavKey) -> Unit,
    /**
     * The tab-switch seam — deliberately NOT [navigate]: switching to the
     * already-selected tab must not pop that tab's stack to its root, and a
     * tab switch must never run the push (playback-host) filter.
     */
    private val selectTab: (Route) -> Unit,
    /** The back seam (remote "Back" — one pop off the current stack). */
    private val goBack: () -> Unit,
    /**
     * Provider over the live
     * [com.raulshma.jellyplay.core.ui.navigation.NavigationState] back stacks
     * — read per request, never cached, so the ClosePlayer pop observes the
     * current stacks.
     */
    private val backStacks: () -> Collection<MutableList<NavKey>>,
    /** The user-message sink (context-menu fallback today). */
    private val presentMessage: suspend (message: String) -> Unit,
    /** Per-shell d-pad arm (Android: synthesized DPAD keycodes). */
    private val moveFocus: (RemoteFocusDirection) -> Unit,
    /** Per-shell select arm (Android: synthesized DPAD_CENTER). */
    private val invokeSelect: () -> Unit,
    /**
     * Per-shell context-menu arm, returning whether the key was consumed.
     * Shells without a context-menu affordance keep the default
     * never-consumed lambda — every [NavigationTarget.OpenContextMenu] then
     * falls back to the standard message.
     */
    private val contextMenuKey: () -> Boolean = { false },
) {

    /**
     * Consumes `RemoteNavigationBridge.targets` until cancellation. The
     * message is a parameter (not a constructor seam) because it is a
     * composition-resolved string on Android; desktop passes its constant.
     */
    suspend fun collect(
        targets: Flow<NavigationTarget>,
        contextMenuUnavailableMessage: String,
    ) {
        targets.collect { target ->
            when (target) {
                NavigationTarget.ClosePlayer -> popPlayerRoutes(backStacks())
                NavigationTarget.GoBack -> goBack()
                is NavigationTarget.MoveFocus -> moveFocus(target.direction)
                NavigationTarget.InvokeSelect -> invokeSelect()
                NavigationTarget.OpenContextMenu -> {
                    val handled = contextMenuKey()
                    if (!handled) presentMessage(contextMenuUnavailableMessage)
                }
                is NavigationTarget.OpenVideoPlayer,
                is NavigationTarget.OpenAudioPlayer,
                is NavigationTarget.OpenMediaDetail,
                is NavigationTarget.GoToTopLevel,
                -> routeForNavigationTarget(target)?.let { dispatch(it) }
            }
        }
    }

    /**
     * The tab-vs-push fork for one mapped [route] — see [routeDispatch].
     * Top-level route SWITCHES the tab through [selectTab] (must not
     * pop-to-root when the tab is already selected); anything else pushes
     * through [navigate].
     */
    fun dispatch(route: Route) {
        when (routeDispatch(route, topLevelKeys)) {
            is RemoteRouteDispatch.SwitchTab -> selectTab(route)
            is RemoteRouteDispatch.Push -> navigate(route)
        }
    }

    companion object {

        /**
         * The pure half of [dispatch] — THE tab-vs-push decision both shells
         * run. A route naming one of [topLevelKeys] folds to
         * [RemoteRouteDispatch.SwitchTab], anything else to
         * [RemoteRouteDispatch.Push]. Exhaustive by construction: the route
         * is never null here (the unroutable targets branch in [collect]
         * before any route exists), which is why this fold has no None arm —
         * the Android pending-route fold (nullable route → None, must not
         * consume) wraps this one instead.
         */
        fun routeDispatch(route: Route, topLevelKeys: Set<Route>): RemoteRouteDispatch =
            if (topLevelKeys.contains(route)) {
                RemoteRouteDispatch.SwitchTab(route)
            } else {
                RemoteRouteDispatch.Push(route)
            }

        /**
         * The SyncPlay auto-open guard: return the player route to push, or
         * null when a [Route.VideoPlayer] already sits ON TOP of any back
         * stack — that player's SyncPlayBridge drives the item load in
         * place, and pushing another VideoPlayer here would stack duplicate
         * player screens. Top-only by design: a player buried below a
         * non-player top does NOT veto (its screen is not visible), and an
         * AudioPlayer top does not either (it is not the SyncPlay surface).
         * Field-parameterized because the request type the Android
         * coordinator emits is app-owned (see `SyncPlayOpenRequest`).
         */
        fun syncPlayAutoOpenRoute(
            itemId: String,
            startPositionTicks: Long,
            backStacks: Collection<List<NavKey>>,
        ): Route.VideoPlayer? =
            if (backStacks.any { it.lastOrNull() is Route.VideoPlayer }) {
                null
            } else {
                Route.VideoPlayer(
                    itemId = itemId,
                    startPositionTicks = startPositionTicks,
                )
            }

        /**
         * The now-playing message fold: the item's display title, falling
         * back to the raw item id when the server sent a blank title, into
         * the localized template (`snackbar_now_playing`, one %s).
         */
        fun nowPlayingMessage(event: PlayEventPayload, messageTemplate: String): String =
            messageTemplate.format(event.title.ifBlank { event.itemId })
    }
}

/**
 * One mapped remote route's execution — the [RemoteNavigationDispatcher.routeDispatch]
 * vocabulary. No None arm: the fold only sees routes
 * [routeForNavigationTarget] already produced.
 */
sealed interface RemoteRouteDispatch {
    /** The route is one of the shell's tabs — switch, never pop. */
    data class SwitchTab(val route: Route) : RemoteRouteDispatch

    /** Everything else — a normal push through the shell's push seam. */
    data class Push(val route: Route) : RemoteRouteDispatch
}
