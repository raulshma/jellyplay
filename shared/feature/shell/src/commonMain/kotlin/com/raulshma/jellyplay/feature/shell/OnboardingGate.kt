package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Shared first-run onboarding gate — the one pure decision both shells
 * (Android's JellyPlayApp and apps/desktop's DesktopNavScaffold) run once per
 * authenticated session: a signed-in user that never completed the wizard gets
 * the shared onboarding section ([Route.Onboarding]) pushed once.
 *
 * Gate ORDER mirrors the Android shell exactly: Android composes its
 * signed-out AuthContent first and evaluates the gate only inside the
 * authenticated branch, so a brand-new install signs in first and meets the
 * wizard after. Desktop keeps the same sequence for free — its gate lives in
 * a scaffold that only composes once authentication has flipped true.
 *
 * The [isTv] delta is Android's: the TV build auto-completes onboarding (a
 * call-site LaunchedEffect marks the pref) and must never gate, so TV returns
 * `null` here; the desktop passes `isTv = false` (it is never the TV build).
 * Completion persistence is shared, not re-implemented: the wizard's
 * OnboardingViewModel writes the same `onboarding_completed` pref
 * (AppRuntimeStateStore) on both platforms, so a completed user never sees
 * the gate again.
 *
 * @return [Route.Onboarding] when the wizard should be pushed, `null` when
 *   the session passes the gate (already onboarded, signed out, or the TV
 *   build that auto-completes).
 */
fun onboardingGateRoute(
    authenticated: Boolean,
    onboardingCompleted: Boolean,
    isTv: Boolean,
): Route.Onboarding? =
    if (authenticated && !onboardingCompleted && !isTv) Route.Onboarding else null
