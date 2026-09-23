package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.feature.onboarding.OnboardingScreen
import com.raulshma.jellyplay.feature.shell.onboardingGateRoute
import com.raulshma.jellyplay.feature.shell.navigation.SignedOutAuthHost
import com.raulshma.jellyplay.shell.ShellInfra

/**
 * The Android app shell's root dispatcher. One `when` over the session state
 * (ADR 0001 — the session policy lives HERE, per shell, never in shared):
 * restoring / onboarding gate / authenticated [MainContent] / signed-out
 * [SignedOutAuthHost], with the update sheet overlaid above every branch.
 *
 * The former 1.6k-line single file is split per concern, same package:
 * [MainContent] (MainContent.kt), the layout branches + [ShellNavParams]
 * (ShellLayouts.kt), [MainNavDisplay] (MainNavDisplay.kt), the shell overlays
 * (ShellOverlays.kt), and the pure folds (FullScreenRoutePolicy.kt,
 * NavRequestCollector.kt, the remote-navigation routing folds and
 * VisibleTopLevelRoutes in their shared homes — shared/feature/shell and
 * core/ui navigation).
 */
@Composable
fun JellyPlayApp(
    viewModel: MainViewModel,
    infra: ShellInfra,
) {
    val session = viewModel.sessionCoordinator
    val isRestoring by session.isRestoring.collectAsStateWithLifecycle()
    val isAuthenticated by session.isAuthenticated.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val isTv = context.isTv()

    LaunchedEffect(Unit) {
        if (isTv && isAuthenticated && !preferences.onboardingCompleted) {
            viewModel.markOnboardingCompleted()
        }
    }

    // The shared (commonMain) UserMessageBus — the single the migrated
    // ViewModels (home, player session, library) post through. Provided
    // alongside the legacy bus below so both message stacks render.
    val sharedUserMessageBus = remember {
        org.koin.mp.KoinPlatform.getKoin()!!.get<com.raulshma.jellyplay.core.ui.message.UserMessageBus>()
    }

    CompositionLocalProvider(
        // Resolved here (first composition) instead of MainActivity's
        // onCreate — the bus is needed by every branch below, so this is as
        // late as its provider can fire without redesigning the local.
        LocalUserMessageBus provides infra.userMessageBusLazy.value,
        com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus provides sharedUserMessageBus,
    ) {
        when {
            isRestoring -> {}
            // First-run wizard gate — the shared pure decision both shells run
            // (feature/shell OnboardingGateRoute); the TV build auto-completes
            // in the LaunchedEffect below instead of gating. No onComplete
            // wiring here: the wizard persists completion itself
            // (OnboardingViewModel writes the same onboarding_completed pref on
            // both platforms — see OnboardingGate), and the flip below swaps
            // this branch out.
            onboardingGateRoute(
                authenticated = isAuthenticated,
                onboardingCompleted = preferences.onboardingCompleted,
                isTv = isTv,
            ) != null -> {
                OnboardingScreen(onComplete = {})
            }
            isAuthenticated -> {
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
                    com.raulshma.jellyplay.core.ui.components.LocalServerHealth provides session.serverHealth,
                    com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch provides surpriseController,
                ) {
                    MainContent(
                        // ADR 0001: the revoke/plain fork dispatches through
                        // the shared ShellSessionController, whose sign-out
                        // action lands in SessionCoordinator (remote-control
                        // stop + sign-out) — the same fork desktop runs
                        // against AuthRepository.
                        onLogout = { revoke -> viewModel.logout(revoke) },
                        viewModel = viewModel,
                        preferences = preferences,
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
                // Signed out: the shared auth host (shared/feature/shell) — the
                // same ServerList-seeded NavDisplay over authSection the
                // desktop shell runs. Its onAuthenticated stays empty: the
                // isAuthenticated flip above swaps this branch out and
                // disposes the stack.
                SignedOutAuthHost()
            }
        }

        // In-app self-update sheet. Rendered at the root so it overlays every
        // screen (auth, onboarding, main). Stays hidden while Idle; the launch-time
        // check in UpdateCoordinator flips it to UpdateAvailable when a newer
        // build exists. Keep this after the `when` so the sheet sits above all
        // content.
        UpdateSheetOverlay(viewModel.updateCoordinator)
    }
}
