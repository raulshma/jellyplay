package com.raulshma.jellyplay.feature.shell.navigation

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.feature.home.navigation.HomePlayOnRedirect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The shell-supplied surface behind [appSections]: everything the shared
 * section graph needs that genuinely differs per shell — by source-set
 * availability (each shell reads its own audio core) or by state owner
 * (Android wires MainViewModel, desktop wires AuthRepository + stores).
 *
 * The flat thirteen-constructor era is grouped: the members were consumed by
 * disjoint sections, so each cohesive group is its own pre-assembled value
 * bundle — [home] for homeSection, [audio] for the music home cards (the
 * rememberShellAudioClicks output), [settings] for settingsSection, [admin]
 * for adminSection, [search] for searchSection — and [ShellAdminHooks]'s
 * reads stay lazy for the same reason as before (admin refreshes don't
 * rebuild the graph). The constructor having five parameters is the honest
 * measure of how much of the old per-shell entryProvider blocks was already
 * shell policy rather than graph shape. A constructor-arg bundle (the
 * HomeCallbacks / SettingsNavActions idiom), not an interface: shells build
 * it inside `remember` (through [rememberShellHost]), and the bundle fields
 * reference the shell's own locals without any member-name shadowing.
 */
class ShellHostHooks(
    /** homeSection's inputs — see [ShellHomeHooks]. */
    val home: ShellHomeHooks,
    /** The music home cards' push pair — see [ShellAudioClicks]. */
    val audio: ShellAudioClicks,
    /** settingsSection's logout + update check — see [ShellSettingsHooks]. */
    val settings: ShellSettingsHooks,
    /** adminSection's access gate — see [ShellAdminHooks]. */
    val admin: ShellAdminHooks,
    /** searchSection's prefill channel — see [ShellSearchHooks]. */
    val search: ShellSearchHooks,
)

/**
 * homeSection's hook bundle. A data class so the remember-key discipline of
 * [rememberShellHost] works on the WHOLE bundle: shells may build it fresh
 * per recomposition and still compare equal (all members remembered/stable),
 * while a mode flip changes [homeMode]'s value and rebuilds the hooks.
 *
 * @param homeMode Home's Video/Music mode. Android derives it from
 *   MainViewModel's persisted preferences; desktop from HomeDiscoveryStore —
 *   same persisted pref either way. A mode flip rebuilds the hooks.
 * @param onHomeModeChange the persistence callback — pass a remembered
 *   reference (Android remembers the model's method reference; desktop the
 *   session controller's).
 * @param playOnRedirect homeSection's Play-On redirect — the "remote session
 *   is the current player" cast surface behind the HomePlayOnRedirect seam.
 *   Only the Android shell has a cast strategy to adapt; shells without one
 *   keep null (no redirect offered).
 * @param surpriseRequests homeSection's "Surprise Me" signal flow, armed from
 *   the Android launcher-shortcut intent. Shells without the seam pass
 *   [kotlinx.coroutines.flow.emptyFlow] (an identity-stable singleton).
 */
data class ShellHomeHooks(
    val homeMode: HomeMode,
    val onHomeModeChange: (HomeMode) -> Unit,
    val playOnRedirect: HomePlayOnRedirect?,
    val surpriseRequests: Flow<Unit>,
)

/**
 * adminSection's access-gate bundle: the two LAZY reads (built by the shared
 * rememberShellAdminGate — admin refreshes re-compose entries through them
 * without rebuilding the hooks or either section graph) plus the refresh arm,
 * which stays per shell (each wraps its own session owner: Android
 * MainViewModel, desktop the shared ShellSessionController).
 */
data class ShellAdminHooks(
    val isAdmin: () -> Boolean,
    val isRefreshingAdmin: () -> Boolean,
    val onRefreshAdmin: () -> Unit,
)

/**
 * settingsSection's hook bundle.
 *
 * @param onLogout logout (revoke=true also revokes the server session).
 *   Android: SessionCoordinator; desktop: AuthRepository.
 * @param onCheckForUpdates the About-row update check. Android: the
 *   UpdateCoordinator's manual check; desktop: AppUpdateRepository + shell
 *   snackbar (no self-update on desktop).
 */
data class ShellSettingsHooks(
    val onLogout: (Boolean) -> Unit,
    val onCheckForUpdates: () -> Unit,
)

/**
 * searchSection's prefill bundle: the pending-query channel the shell arms
 * (Android: launcher-shortcut/shared-text intents), which SearchScreen fires
 * as a search and then clears through [onConsumeSearchQuery]. The query flow
 * is a StateFlow on purpose — the armed value must survive until the search
 * screen consumes it. Shells without a prefill source keep a never-armed
 * MutableStateFlow(null).
 */
data class ShellSearchHooks(
    val pendingSearchQuery: StateFlow<String?>,
    val onConsumeSearchQuery: () -> Unit,
)
