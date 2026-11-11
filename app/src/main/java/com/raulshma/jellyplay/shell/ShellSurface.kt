package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.feature.shell.LaunchPhase
import com.raulshma.jellyplay.feature.shell.onboardingGateRoute

/**
 * Which surface the Android shell is currently rendering, as far as the root
 * overlays are concerned — the shared vocabulary the root-level modal sheets
 * (the self-update sheet, the What's New sheet) consult to decide whether
 * they may render at all.
 *
 * Why this exists: the self-update sheet used to be composed at
 * JellyPlayApp's root behind only an `updateState !is UpdateState.Idle`
 * guard, so its placement AFTER the session `when` made it overlay EVERY
 * branch — an update check completing mid-onboarding (both shell
 * coordinators are armed on the same session-restored callback,
 * MainViewModel.init) popped a modal over the first-run wizard, the
 * signed-out auth host, and even the pinned splash. Whether a modal belongs
 * on the current surface is a DECISION, not a composition-ordering accident,
 * so the exclusion now lives in pure code —
 * `AppUpdateDecision.shouldShowOverlay` (store flavor) and the direct
 * [Main] comparison (What's New, src/main) — with this enum as the one
 * type both read. Lives in src/main (not the store source set) because the
 * flavor-duplicated `DistributionPlatform.UpdateSheetOverlay` signature must
 * carry it on BOTH classpaths.
 *
 * Two values only: the overlays treat every pre-main surface alike (never
 * show there) and derive nothing else from which one it is.
 *
 *  - [Main] — the authenticated main shell (JellyPlayApp's MainContent
 *    branch). The only surface where the root modals may render.
 *  - [OnboardingOrAuth] — everything before it: the first-run onboarding
 *    wizard and the signed-out auth host. LaunchPhase.Restoring folds in
 *    here too even though the name doesn't say so — during Restoring the
 *    `when` renders no branch at all (the splash is the system's, not a
 *    composition branch), so the overlays must be withheld equally: a sheet
 *    appearing over the splash is the same interruption with nowhere to
 *    anchor.
 */
enum class ShellSurface {
    Main,
    OnboardingOrAuth,
}

/**
 * Derives the [ShellSurface] the root overlays render on, from exactly what
 * JellyPlayApp's session `when` already knows — and BY the same decision, so
 * the two can never disagree: [onboardingGateRoute] is re-run here instead of
 * copying its predicate, making the mirror structural rather than textual
 * (a change to the gate's inputs or order changes this fold with it).
 *
 * Branch-for-branch with JellyPlayApp's `when`:
 *  - Restoring → [ShellSurface.OnboardingOrAuth] (splash pinned; overlays
 *    withheld).
 *  - the onboarding gate fires → [ShellSurface.OnboardingOrAuth] (the wizard
 *    branch — the gate only fires for Authenticated && !completed && !TV).
 *  - Authenticated past the gate → [ShellSurface.Main]. TV lands here even
 *    while the onboarding pref is still unset (the gate returns null for TV —
 *    the build auto-completes via a call-site LaunchedEffect), the same frame
 *    the `when` renders MainContent.
 *  - else (NeedsAuth) → [ShellSurface.OnboardingOrAuth] (the signed-out auth
 *    host branch).
 *
 * Pure, and pinned by ShellSurfaceTest. JellyPlayApp evaluates it once per
 * composition and hands the result to both root overlays
 * (DistributionPlatform.UpdateSheetOverlay and WhatsNewSheetOverlay); the
 * deferred-render semantics of a gated update sheet are documented on
 * `AppUpdateDecision.shouldShowOverlay` (store source set).
 */
fun shellSurface(
    phase: LaunchPhase,
    onboardingCompleted: Boolean,
    isTv: Boolean,
): ShellSurface =
    if (phase == LaunchPhase.Authenticated &&
        onboardingGateRoute(
            authenticated = true,
            onboardingCompleted = onboardingCompleted,
            isTv = isTv,
        ) == null
    ) {
        ShellSurface.Main
    } else {
        ShellSurface.OnboardingOrAuth
    }
