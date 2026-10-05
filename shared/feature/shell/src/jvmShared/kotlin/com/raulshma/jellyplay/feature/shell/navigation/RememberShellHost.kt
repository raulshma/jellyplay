package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.ui.navigation.Navigator

/**
 * The ONE construction site for the shell-supplied [ShellHostHooks]: both
 * shells build their hooks through this factory (Android's MainContent and
 * the desktop DesktopNavScaffold), so the bundle-by-bundle wiring of the
 * five groups lives in this one file — adding a group is an edit here plus
 * each call site's value wiring. The hook types stay dumb constructor-arg
 * bundles, untouched (their KDocs' deletion test still governs what MAY
 * become a field).
 *
 * REMEMBER-KEY DISCIPLINE (owned here): every parameter is a `remember`
 * key, so the hooks rebuild exactly when a captured input changes. The
 * group bundles are DATA classes — they compare structurally — so a shell
 * may construct one fresh per recomposition without churning the key, as
 * long as every MEMBER is remembered/stable (a fresh-per-recomposition
 * lambda member compares unequal every time and would rebuild the hooks —
 * and through them BOTH shells' section graphs — on every recomposition).
 * [homeMode] (a bundle member) stays a de-facto key: a mode flip changes
 * its value and rebuilds the hooks. The section-graph memoization itself
 * deliberately stays per shell (genuinely different), and this factory is
 * what keeps its `shellHost` key from thrashing:
 *  - Android: MainNavDisplay's `remember(navigator, shellHost)`
 *    `shellEntryProvider` build (the ~25 section builders); stability
 *    contract documented on MainContent's NavRequestController — the
 *    navigator is identity-stable per composition, the audio bundle is the
 *    rememberShellAudioClicks output, the admin bundle reads the
 *    rememberShellAdminGate outputs.
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
 * NowPlayingSurface), and keying here rebuilds the hooks —
 * refreshing those captures — exactly when the navigator identity
 * changes, and never otherwise.
 *
 * The admin reads arrive as read lambdas on purpose — "Lazy .value reads
 * — admin refreshes don't rebuild the graph": admin refreshes re-compose
 * entries through the lazy reads without rebuilding the hooks or either
 * graph.
 *
 * @param navigator remember key only — see above; the audio lambdas'
 *   captured navigator.
 * @param home homeSection's bundle (ShellHomeHooks) — mode + persistence,
 *   Play-On redirect, Surprise Me flow.
 * @param audio the music home cards' push pair (ShellAudioClicks) — built
 *   by the shared rememberShellAudioClicks over each shell's
 *   NowPlayingSurface (each shell's audio core is read at CLICK
 *   time — flows read lazily, never captured values; a blank art URL
 *   arrives as null, the helper's declared normalization).
 * @param settings settingsSection's bundle (ShellSettingsHooks) — logout
 *   (revoke=true also revokes the server session) + the About-row update
 *   check.
 * @param admin adminSection's bundle (ShellAdminHooks) — the lazy reads
 *   come from the shared rememberShellAdminGate (lazy over the shell's
 *   collected admin states); the refresh arm wraps each shell's own
 *   session owner.
 * @param search searchSection's bundle (ShellSearchHooks) — the prefill
 *   channel (a StateFlow on purpose: the armed value must survive until
 *   the search screen consumes it) and its consume callback.
 */
@Composable
fun rememberShellHost(
    navigator: Navigator,
    home: ShellHomeHooks,
    audio: ShellAudioClicks,
    settings: ShellSettingsHooks,
    admin: ShellAdminHooks,
    search: ShellSearchHooks,
): ShellHostHooks = remember(
    navigator,
    home,
    audio,
    settings,
    admin,
    search,
) {
    ShellHostHooks(
        home = home,
        audio = audio,
        settings = settings,
        admin = admin,
        search = search,
    )
}
