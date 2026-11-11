package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.distribution.DistributionPlatform
import com.raulshma.jellyplay.feature.onboarding.OnboardingScreen
import com.raulshma.jellyplay.feature.shell.LaunchPhase
import com.raulshma.jellyplay.feature.shell.onboardingGateRoute
import com.raulshma.jellyplay.feature.shell.navigation.SignedOutAuthHost
import com.raulshma.jellyplay.shell.ShellInfra
import com.raulshma.jellyplay.shell.shellSurface

/**
 * The Android app shell's root dispatcher. One `when` over the session state
 * (the shell-session contract — the session policy lives HERE, per shell, never
 * in shared):
 * LaunchPhase.Restoring (nothing — splash pinned) / onboarding gate /
 * authenticated [MainContent] / signed-out [SignedOutAuthHost], with the
 * root overlays (self-update sheet, What's New sheet) composed after the
 * `when` so they sit above main-surface content — but gated to the MAIN
 * surface by the pure [shellSurface] fold (an update found mid-onboarding,
 * signed-out, or during restore stays pending in the coordinator and
 * surfaces when the session reaches main).
 * The `viewModel` parameter is the [ShellGateModel] slice — the gate's view
 * of the activity-scoped ViewModel; the layout subtree receives the
 * [MainShellModel] slice through [MainContent].
 *
 * Split per concern, same package:
 * [MainContent] (MainContent.kt), the request-dispatch holder
 * (NavRequestController.kt), the external-player launch pair
 * (ExternalPlayerLauncherHost.kt), the bottom-nav scroll state
 * (BottomNavScrollState.kt), the peek-preview state (PeekPreviewState.kt),
 * the back-exit confirmation host (BackExitHost.kt), the layout branches +
 * [ShellNavParams] (ShellLayouts.kt), [MainNavDisplay] (MainNavDisplay.kt),
 * the shell overlays (ShellOverlays.kt), and the pure folds
 * (FullScreenRoutePolicy.kt, NavRequestCollector.kt, the remote-navigation
 * routing folds and VisibleTopLevelRoutes in their shared homes —
 * shared/feature/shell and core/ui navigation).
 */
@Composable
internal fun JellyPlayApp(
    viewModel: ShellGateModel,
    infra: ShellInfra,
) {
    // The shell coordinators ride the infra bundle — the composition's ONE
    // resolution site for them (MainActivity builds the bundle; MainViewModel
    // keeps its constructor injection only to START the same Koin singles on
    // its scope). Resolved here on first composition.
    val session = infra.sessionCoordinatorLazy.value
    // The launch gate as ONE phase (shared LaunchPhase): Restoring pins the
    // splash, NeedsAuth/Authenticated name the branch. Every session
    // predicate below reads THIS value and nothing else — the former
    // two-boolean read (isRestoring + isAuthenticated, independently
    // written) could observe splash-down while the authenticated flag still
    // read false after a successful restore and flash the login screen for
    // a frame; one mutually exclusive phase makes that frame
    // unrepresentable (see SessionRestore's class KDoc).
    val phase by session.phase.collectAsStateWithLifecycle()
    // Topic preference slices (ShellPreferenceSlices.kt): the gate's
    // onboarding flag plus the nav-chrome and shell-environment slices the
    // layout subtree consumes. Each maps the same merged
    // `MainPreferences` pipeline and de-duplicates, so an unrelated write
    // (theme, subtitles, security, ...) yields an equal slice and no longer
    // invalidates this root — the former whole-flow collection here
    // recomposed the shell on EVERY preference write.
    val onboardingCompleted = rememberOnboardingCompleted(viewModel.preferences)
    val navPrefs = rememberShellNavPrefs(viewModel.preferences)
    val envPrefs = rememberShellEnvironmentPrefs(viewModel.preferences)

    val context = LocalContext.current
    val isTv = context.isTv()

    LaunchedEffect(Unit) {
        if (isTv && phase == LaunchPhase.Authenticated && !onboardingCompleted) {
            viewModel.markOnboardingCompleted()
        }
    }

    // The app-wide UserMessageBus (commonMain core:ui message) — the ONE
    // bus every poster (ViewModels, screens, seam adapters) posts through.
    // Resolved through the infra bundle (same resolution path as every
    // other shell service — see ShellInfra's KDoc).
    val sharedUserMessageBus = remember { infra.userMessageBusLazy.value }

    CompositionLocalProvider(
        // Resolved here (first composition) instead of MainActivity's
        // onCreate — the bus is needed by every branch below, so this is as
        // late as its provider can fire without redesigning the local.
        LocalUserMessageBus provides sharedUserMessageBus,
    ) {
        when {
            phase == LaunchPhase.Restoring -> {}
            // First-run wizard gate — the shared pure decision both shells run
            // (feature/shell OnboardingGateRoute); the TV build auto-completes
            // in the LaunchedEffect below instead of gating. No onComplete
            // wiring here: the wizard persists completion itself
            // (OnboardingViewModel writes the same onboarding_completed pref on
            // both platforms — see OnboardingGate), and the phase flip below
            // swaps this branch out.
            onboardingGateRoute(
                authenticated = phase == LaunchPhase.Authenticated,
                onboardingCompleted = onboardingCompleted,
                isTv = isTv,
            ) != null -> {
                OnboardingScreen(onComplete = {})
            }
            phase == LaunchPhase.Authenticated -> {
                // "Surprise Me" launcher-shortcut controller. Built
                // once so the StateFlow reference is stable across recompositions.
                val surpriseController = remember(viewModel) {
                    com.raulshma.jellyplay.core.ui.components.SurpriseLaunchController(
                        armed = viewModel.surpriseOnLaunch,
                        consume = { viewModel.consumeSurpriseOnLaunch() },
                    )
                }
                CompositionLocalProvider(
                    // Resolved in the AUTHENTICATED branch only, so
                    // NetworkMonitor (and its connectivity-callback
                    // registration) is never built for auth/onboarding
                    // sessions.
                    com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus provides infra.networkStatusLazy.value,
                    com.raulshma.jellyplay.core.ui.components.LocalNetworkMetered provides infra.networkMeteredLazy.value,
                    com.raulshma.jellyplay.core.ui.components.LocalServerHealth provides session.serverHealth,
                    com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch provides surpriseController,
                ) {
                    // The revoke/plain fork dispatches through
                    // the shared ShellSessionController, whose sign-out
                    // action lands in SessionCoordinator (remote-control
                    // stop + sign-out) — the same fork desktop runs
                    // against AuthRepository. Remembered on the ViewModel so
                    // MainContent receives a STABLE lambda: it is a key of
                    // the shellHost hooks remember (and a ShellNavParams
                    // field), and a fresh lambda per recomposition here would
                    // make the section-graph memoization behind them always
                    // miss. The topic preference slices collected above keep
                    // this root quiet for unrelated preference writes, so
                    // recompositions that could regress the lambda's identity
                    // no longer fire at all.
                    val onLogout: (Boolean) -> Unit = remember(viewModel) { viewModel::logout }
                    MainContent(
                        onLogout = onLogout,
                        model = viewModel,
                        navPrefs = navPrefs,
                        envPrefs = envPrefs,
                        infra = infra,
                        // Resolved here — the authenticated branch only — so the
                        // playback engine stays unbuilt for auth/onboarding
                        // sessions; collecting it anywhere earlier would defeat
                        // the lazy provider.
                        audioPlaybackManager = infra.audioPlaybackManagerLazy.value,
                    )
                }
            }
            else -> {
                // Signed out (phase == LaunchPhase.NeedsAuth): the shared
                // auth host (shared/feature/shell) — the same ServerList-seeded
                // NavDisplay over authSection the desktop shell runs. Its
                // onAuthenticated stays empty: the Authenticated phase above
                // swaps this branch out and disposes the stack.
                SignedOutAuthHost()
            }
        }

        // Which surface the shell is on, for the root overlays' main-surface
        // gate. [shellSurface] mirrors the `when` above branch-for-branch (it
        // re-runs the same onboardingGateRoute decision — see its KDoc), so
        // the surface can never name something the `when` isn't rendering.
        val surface = shellSurface(
            phase = phase,
            onboardingCompleted = onboardingCompleted,
            isTv = isTv,
        )

        // In-app self-update sheet. Composed at the root so it sits above all
        // main-surface content — but MAIN-surface only: an update found while
        // the user is mid-onboarding, signed out, or still restoring stays
        // pending in the coordinator's StateFlow (no dismissal, no stamp —
        // the sheet is never composed) and surfaces when the session reaches
        // main. The gate itself is the pure AppUpdateDecision.shouldShowOverlay
        // (store) applied inside the flavor-bound overlay. Stays hidden while
        // Idle; the launch-time check (store) flips it to UpdateAvailable when
        // a newer build exists. fdroid binds a permanently-idle noop, so the
        // sheet never renders there by construction.
        DistributionPlatform.UpdateSheetOverlay(
            update = infra.updateCoordinatorLazy.value,
            surface = surface,
        )

        // Post-update What's New sheet — same root placement, same
        // main-surface gate (a returning signed-out user with an unseen
        // upgrade would otherwise have it pop over the auth host), plus the
        // existing deference to the update sheet above (never stacks on it).
        // Deep links ride the ViewModel's pending-route channel.
        WhatsNewSheetOverlay(
            whatsNew = infra.whatsNewCoordinatorLazy.value,
            update = infra.updateCoordinatorLazy.value,
            surface = surface,
            onNavigate = viewModel::navigateFromShell,
        )
    }
}
