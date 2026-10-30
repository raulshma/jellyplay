package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.data.playback.NowPlayingSurface
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-shell ARMS for [rememberShellComposition] — the ADR-0001 line drawn
 * through the whole shell composition, not just the session wiring it
 * already moved: the shells hand over the values that genuinely differ
 * (their audio core, their Surprise-Me emitter, their Play-On strategy,
 * their unsupported-action presenter, their home/admin/settings/search
 * session owners) and the shared function performs the CONSTRUCTION they
 * used to duplicate — [rememberShellAudioClicks], [rememberShellAdminGate],
 * the [ShortcutActionSurface] with its shared focusHome arm, and the
 * [rememberShellHost] five-bundle wiring. Policy stays per shell: which
 * flows back which state, lifecycle-awareness of the admin collects, the
 * presenter's wording (Android's loud `error` vs desktop's snackbar), the
 * Play-On capability verdict — those remain the call sites'.
 *
 * REMEMBER-KEY DISCIPLINE (the [rememberShellHost] factory's contract,
 * extended to the arms): this is a DATA class compared structurally, so a
 * shell may build it fresh per recomposition as long as every MEMBER is
 * remembered/stable — a fresh-per-recomposition lambda member compares
 * unequal every time and would rebuild the composition, the hooks behind it,
 * and BOTH shells' section graphs on every recomposition. [homeMode] stays a
 * de-facto key: a mode flip changes its value and rebuilds the hooks
 * (documented on [ShellHomeHooks]).
 *
 * ADR-0001's hook-grouping rule stands one level up: adding hook #15 is one
 * new member here + its wiring line in [rememberShellComposition] + the two
 * shells' arm values — never a factory-wide edit of either shell.
 *
 * @param audioSurface the shell's audio core — the shared [NowPlayingSurface]
 *   (AudioPlaybackManager on Android, DesktopAudioQueueManager on desktop);
 *   the [rememberShellAudioClicks] pair reads its flows at CLICK time.
 * @param surpriseRequests the home hero's Surprise-Me signal flow.
 * @param armSurprise the shortcut surface's SURPRISE_ME arm; `null` =
 *   unsupported (desktop's buffered channel is armed, Android's VM method is).
 * @param playOnRedirect the home section's Play-On cast strategy behind the
 *   [HomePlayOnRedirect] seam; `null` = this shell has no cast strategy
 *   (desktop).
 * @param requestPlayOn the shortcut surface's PLAY_ON arm — Android's Play On
 *   device-sheet counter; `null` = unsupported (desktop).
 * @param toggleOffline the shortcut surface's TOGGLE_OFFLINE arm (each shell
 *   routes to its platform OfflineModeManager); `null` = unsupported.
 * @param onUnsupportedShortcutAction the loud-but-non-crashing failure for an
 *   action this shell did not arm (Android: `error(..)` — every action is
 *   armed there; desktop: a snackbar).
 * @param homeMode the shell's persisted Home Video/Music mode.
 * @param onHomeModeChange the persistence callback — pass a remembered
 *   reference (Android the model's, desktop the session controller's).
 * @param isAdminState / @param isRefreshingAdminState the ALREADY-COLLECTED
 *   admin states — collected by the shell with its own idiom
 *   (lifecycle-awareness is per-shell policy), the gate's lazy reads are
 *   shared ([rememberShellAdminGate]).
 * @param onRefreshAdmin wraps each shell's own session owner (Android
 *   MainViewModel, desktop the shared [com.raulshma.jellyplay.feature.shell.ShellSessionController]).
 * @param onLogout logout (revoke=true also revokes the server session).
 * @param onCheckForUpdates the About-row update check.
 * @param pendingSearchQuery the search prefill channel (a StateFlow on
 *   purpose — see [ShellSearchHooks]); shells without a source keep a
 *   never-armed one.
 * @param onConsumeSearchQuery clears the armed prefill once consumed.
 */
data class ShellCompositionArms(
    val audioSurface: NowPlayingSurface,
    val surpriseRequests: Flow<Unit>,
    val armSurprise: (() -> Unit)?,
    val playOnRedirect: HomePlayOnRedirect?,
    val requestPlayOn: (() -> Unit)?,
    val toggleOffline: (() -> Unit)?,
    val onUnsupportedShortcutAction: (ShortcutAction) -> Unit,
    val homeMode: HomeMode,
    val onHomeModeChange: (HomeMode) -> Unit,
    val isAdminState: State<Boolean>,
    val isRefreshingAdminState: State<Boolean>,
    val onRefreshAdmin: () -> Unit,
    val onLogout: (Boolean) -> Unit,
    val onCheckForUpdates: () -> Unit,
    val pendingSearchQuery: StateFlow<String?>,
    val onConsumeSearchQuery: () -> Unit,
)

/**
 * The ONE construction site for the shared shell composition both shells
 * feed ([ShellCompositionArms]): the audio clicks, the admin gate's lazy
 * reads, the [ShortcutActionSurface] (the focusHome arm is derived HERE from
 * the [Navigator] — both shells' focusHome is the same write to the
 * navigation state's top-level route), and the [rememberShellHost]
 * five-bundle wiring, returned as the [ShellHostHooks] the shells' graphs
 * render through. Android's MainContent (through its ShellHostArms holder)
 * and the desktop DesktopNavScaffold call this; neither shell hand-wires the
 * bundles anymore.
 *
 * REMEMBER-KEY DISCIPLINE: every internal remember keys on the values its
 * product captures — [rememberShellAudioClicks] on (navigator, audio
 * surface), [rememberShellAdminGate] on the collected States, the shortcut
 * surface on (navigator, the four arm lambdas), and [rememberShellHost] on
 * the navigator + the structurally-compared bundles. See each factory's KDoc
 * and [ShellCompositionArms]' contract on member stability.
 */
@Composable
fun rememberShellComposition(
    navigator: Navigator,
    arms: ShellCompositionArms,
): ShellHostHooks {
    val audioClicks = rememberShellAudioClicks(navigator, arms.audioSurface)
    // The admin reads are the shared rememberShellAdminGate outputs — lazy
    // .value reads, so admin refreshes don't rebuild the graph (each shell
    // collects the States with its own idiom and hands them over here).
    val adminGate = rememberShellAdminGate(arms.isAdminState, arms.isRefreshingAdminState)
    // Keyed on exactly what the surface captures — the navigator identity and
    // the four arms — so an unrelated arms member change (a homeMode flip)
    // does not rebuild it, matching the per-shell remember keys this
    // construction replaces.
    val shortcutActions = remember(
        navigator,
        arms.armSurprise,
        arms.requestPlayOn,
        arms.toggleOffline,
        arms.onUnsupportedShortcutAction,
    ) {
        ShortcutActionSurface(
            focusHome = { navigator.state.topLevelRoute.value = Route.Home },
            armSurprise = arms.armSurprise,
            requestPlayOn = arms.requestPlayOn,
            toggleOffline = arms.toggleOffline,
            onUnsupported = arms.onUnsupportedShortcutAction,
        )
    }
    return rememberShellHost(
        navigator = navigator,
        home = ShellHomeHooks(
            homeMode = arms.homeMode,
            onHomeModeChange = arms.onHomeModeChange,
            playOnRedirect = arms.playOnRedirect,
            surpriseRequests = arms.surpriseRequests,
            shortcutActions = shortcutActions,
        ),
        audio = audioClicks,
        settings = ShellSettingsHooks(
            onLogout = arms.onLogout,
            onCheckForUpdates = arms.onCheckForUpdates,
        ),
        admin = ShellAdminHooks(
            isAdmin = adminGate.isAdmin,
            isRefreshingAdmin = adminGate.isRefreshingAdmin,
            onRefreshAdmin = arms.onRefreshAdmin,
        ),
        search = ShellSearchHooks(
            pendingSearchQuery = arms.pendingSearchQuery,
            onConsumeSearchQuery = arms.onConsumeSearchQuery,
        ),
    )
}
