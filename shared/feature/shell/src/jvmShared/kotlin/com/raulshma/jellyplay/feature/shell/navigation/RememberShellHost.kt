package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The ONE construction site for the shell-supplied [ShellHostHooks]: both
 * shells build their hooks through this factory (Android's MainContent and
 * the desktop DesktopNavScaffold), so the field-by-field wiring of the
 * thirteen fields lives in this one file — adding hook #14 is an edit here
 * plus each call site's value wiring. The hook type itself stays a dumb
 * constructor-arg bundle, untouched (its KDoc's deletion test still governs
 * what MAY become a field).
 *
 * REMEMBER-KEY DISCIPLINE (owned here): every parameter is a `remember`
 * key, so the hooks rebuild exactly when a captured input's identity
 * changes. Callers MUST therefore pass remembered/stable instances — a
 * fresh-per-recomposition value (an inline `{ ... }` lambda, an
 * un-remembered bound `expr::method` reference, a raw wrapper object)
 * compares unequal every time and would rebuild the hooks — and through
 * them BOTH shells' section graphs — on every recomposition. The
 * section-graph memoization itself deliberately stays per shell (genuinely
 * different), and this factory is what keeps its `shellHost` key from
 * thrashing:
 *  - Android: MainNavDisplay's `remember(navigator, shellHost)`
 *    `shellEntryProvider` build (the ~25 section builders); stability
 *    contract documented on MainContent's NavRequestController — the
 *    navigator is identity-stable per composition, the click lambdas are
 *    the rememberShellAudioClicks outputs, the admin reads the
 *    rememberShellAdminGate ones.
 *  - desktop: the scaffold's `remember(guardedNavigator, shellHost)`
 *    `shellEntryProvider(..., registry = services.sectionRegistry)` build
 *    (the ~20 shared sections; the graph build is also what attaches them
 *    into the holder's registry, under the composition-order contract on
 *    rememberDesktopShellServices); stability contract: the guarded
 *    navigator is a DesktopShellServices val, re-issued only when the
 *    holder itself rebuilds.
 *
 * [navigator] is a KEY, not a field: no hook stores it directly — the
 * now-playing/ambient click lambdas close over it inside the shared
 * rememberShellAudioClicks helper (keyed on the navigator + the shell's
 * ShellAudioSource adapter), and keying here rebuilds the hooks —
 * refreshing those captures — exactly when the navigator identity
 * changes, and never otherwise.
 *
 * The admin triple arrives as read lambdas on purpose — "Lazy .value reads
 * — admin refreshes don't rebuild the graph": admin refreshes re-compose
 * entries through the lazy reads without rebuilding the hooks or either
 * graph.
 *
 * @param navigator remember key only — see above; the click lambdas'
 *   captured navigator.
 * @param homeMode Home's Video/Music mode (a field AND a key: a mode flip
 *   rebuilds the hooks).
 * @param onHomeModeChange persistence callback — pass a remembered
 *   reference (Android remembers the model's method reference; desktop the
 *   session controller's).
 * @param onNowPlayingClick / @param onAmbientClick the music home cards'
 *   push lambdas — built by the shared rememberShellAudioClicks over each
 *   shell's ShellAudioSource adapter (each shell's audio core is read at
 *   CLICK time — flows read lazily, never captured values; a blank art URL
 *   arrives as null, the helper's declared normalization).
 * @param onLogout settingsSection's logout (revoke=true also revokes the
 *   server session).
 * @param onCheckForUpdates settingsSection's About-row update check.
 * @param isAdmin / @param isRefreshingAdmin / @param onRefreshAdmin the
 *   admin triple — the reads come from the shared rememberShellAdminGate
 *   (lazy over the shell's collected admin states); the refresh arm wraps
 *   each shell's own session owner.
 * @param playOnRedirect homeSection's Play-On redirect — Android-only
 *   today (its cast strategy lives on the Play On controller); shells
 *   without a cast strategy pass null (no redirect offered). Required —
 *   every parameter is, because each one is a remember key: an omitted
 *   slot's silent default would build a fresh instance per call and
 *   rebuild the shells' section graphs every recomposition.
 * @param surpriseRequests homeSection's "Surprise Me" signal flow —
 *   armed from the Android launcher-shortcut intent; shells without the
 *   surface pass [kotlinx.coroutines.flow.emptyFlow] (an identity-stable
 *   singleton, so the remember key never churns).
 * @param pendingSearchQuery searchSection's prefill channel — a StateFlow
 *   on purpose (the armed value must survive until the search screen
 *   consumes it).
 * @param onConsumeSearchQuery clears [pendingSearchQuery] after the search
 *   screen fires it.
 */
@Composable
fun rememberShellHost(
    navigator: Navigator,
    homeMode: HomeMode,
    onHomeModeChange: (HomeMode) -> Unit,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    onLogout: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    isAdmin: () -> Boolean,
    isRefreshingAdmin: () -> Boolean,
    onRefreshAdmin: () -> Unit,
    playOnRedirect: HomePlayOnRedirect?,
    surpriseRequests: Flow<Unit>,
    pendingSearchQuery: StateFlow<String?>,
    onConsumeSearchQuery: () -> Unit,
): ShellHostHooks = remember(
    navigator,
    homeMode,
    onHomeModeChange,
    onNowPlayingClick,
    onAmbientClick,
    onLogout,
    onCheckForUpdates,
    isAdmin,
    isRefreshingAdmin,
    onRefreshAdmin,
    playOnRedirect,
    surpriseRequests,
    pendingSearchQuery,
    onConsumeSearchQuery,
) {
    ShellHostHooks(
        homeMode = homeMode,
        onHomeModeChange = onHomeModeChange,
        onNowPlayingClick = onNowPlayingClick,
        onAmbientClick = onAmbientClick,
        onLogout = onLogout,
        onCheckForUpdates = onCheckForUpdates,
        isAdmin = isAdmin,
        isRefreshingAdmin = isRefreshingAdmin,
        onRefreshAdmin = onRefreshAdmin,
        playOnRedirect = playOnRedirect,
        surpriseRequests = surpriseRequests,
        pendingSearchQuery = pendingSearchQuery,
        onConsumeSearchQuery = onConsumeSearchQuery,
    )
}
