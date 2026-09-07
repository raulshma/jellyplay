package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shell.onboardingGateRoute

/**
 * Desktop specialization of the shared first-run gate
 * ([com.raulshma.jellyplay.feature.shell.onboardingGateRoute] — wave 21B, now
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
