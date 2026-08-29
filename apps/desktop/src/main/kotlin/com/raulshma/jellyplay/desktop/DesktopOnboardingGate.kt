package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Pure first-run gate decision for the desktop shell (wave 21B) — the
 * JellyPlayApp twin of Android's
 * `isAuthenticated && !preferences.onboardingCompleted` branch: an
 * authenticated session that has never completed the wizard gets the shared
 * onboarding section pushed ONCE (see DesktopNavScaffold's gate effect).
 *
 * Gate ORDER mirrors Android exactly: Android composes its signed-out
 * AuthContent first and evaluates the gate only inside the authenticated
 * branch, so a brand-new install signs in first and meets the wizard after —
 * the desktop keeps the same sequence for free, because DesktopNavScaffold
 * (where the gate lives) only composes once
 * [com.raulshma.jellyplay.core.data.repository.AuthRepository.isAuthenticated]
 * has flipped true. Android's TV auto-complete has no desktop twin (the
 * desktop build is never the TV build).
 *
 * Completion persistence is shared, not re-implemented: the wizard's
 * OnboardingViewModel.completeOnboarding writes the same `onboarding_completed`
 * pref (AppRuntimeStateStore) on both platforms, so a completed desktop user
 * never sees the gate again.
 *
 * @return [Route.Onboarding] when the wizard should be pushed, `null` when
 *   the session passes the gate (already onboarded, or signed out — the
 *   signed-out host never asks).
 */
internal fun desktopOnboardingGateRoute(
    isAuthenticated: Boolean,
    onboardingCompleted: Boolean,
): Route.Onboarding? =
    if (isAuthenticated && !onboardingCompleted) Route.Onboarding else null
