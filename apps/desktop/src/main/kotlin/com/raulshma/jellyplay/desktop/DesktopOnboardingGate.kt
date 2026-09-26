package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.onboardingGateRoute

/**
 * Desktop specialization of the shared first-run gate
 * ([com.raulshma.jellyplay.feature.shell.onboardingGateRoute] — now
 * one shared pure fn in :shared:feature:shell that the Android JellyPlayApp
 * branch runs too). Kept as a named wrapper so DesktopNavScaffold's call site
 * reads exactly as before and the desktop build pins its specialization:
 * `isTv = false` — the desktop build is never the TV build, so Android's TV
 * auto-complete delta does not apply here.
 *
 * The pure decision's full boolean matrix (incl. the TV delta) is pinned by
 * OnboardingGateTest in the shared module; DesktopOnboardingGateTest pins that
 * this wrapper IS the `isTv = false` row of that matrix.
 *
 * @return [Route.Onboarding] when the wizard should be pushed, `null` when
 *   the session passes the gate (already onboarded, or signed out — the
 *   signed-out host never asks).
 */
internal fun desktopOnboardingGateRoute(
    isAuthenticated: Boolean,
    onboardingCompleted: Boolean,
): Route.Onboarding? = onboardingGateRoute(
    authenticated = isAuthenticated,
    onboardingCompleted = onboardingCompleted,
    isTv = false,
)

/**
 * The gate's one-shot read + dispatch, extracted from DesktopNavScaffold's
 * inline `LaunchedEffect` body: the persisted `onboarding_completed` flag is
 * read ONCE per scaffold composition — i.e. once per authenticated session
 * entry — and a not-yet-onboarded session gets the shared wizard pushed (the
 * same [Route.Onboarding] the Shortcuts entry and the settings "rerun setup"
 * row open). The [readOnboardingCompleted] one-shot suspend read (not the
 * eagerly-shared state flow, whose seed is all-defaults before the prefs
 * file lands) is what keeps an already-onboarded user from seeing the wizard
 * at every boot; completion flows back through the shared OnboardingViewModel
 * — same pref on both platforms — so the gate never re-fires for a completer.
 *
 * The gate runs only in the authenticated branch, so the decision is always
 * the `isAuthenticated = true` row of [desktopOnboardingGateRoute]'s matrix
 * (the parameter stays honest against the Android gate it mirrors). Pure
 * constructor-lambda wiring (the DesktopUpdateCheckController idiom) —
 * JVM-pinnable by DesktopOnboardingGateTest.
 */
internal suspend fun runDesktopOnboardingGateOnce(
    readOnboardingCompleted: suspend () -> Boolean,
    navigate: (Route.Onboarding) -> Unit,
) {
    desktopOnboardingGateRoute(
        isAuthenticated = true,
        onboardingCompleted = readOnboardingCompleted(),
    )?.let(navigate)
}
