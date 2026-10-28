package com.raulshma.jellyplay.feature.player.video.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * The cold-start restore prompt's APPLIER — the one home for the shell-side
 * half the shells hand-duplicated (the Android `SessionRestoreHost` and the
 * desktop `DesktopNavScaffold` restoration block): the evaluate-ONCE ladder
 * over [SessionRestorationController.evaluate], the parked prompt state, the
 * dialog's three arms, and the host-level dismissal scope. Everything
 * decision-shaped lives in the controller ([decideSessionRestoration]); this
 * composable only applies the verdict, so the shells stay one-line calls that
 * fold their own launch facts and navigate their own way — no Navigator type
 * crosses this seam (the shells navigate differently).
 *
 *  - `AutoRestore` acks via [SessionRestorationController.onRestoreHandled],
 *    then navigates straight to the player route through [navigate] at the
 *    snapshot's resume position (the same `startPositionTicks` entry point a
 *    Continue Watching tap takes; the server resume point is the source, so
 *    nothing new is persisted).
 *  - `Ask` parks the snapshot in a local dialog state and renders the shared
 *    [SessionRestorePromptDialog] — the app's dialog language (tone PRIMARY:
 *    an affirmative forward action), styled "Resume '<title>'?" with Resume /
 *    Dismiss. The prompt lives in the SHELL's composition (through this
 *    applier), not the player screen, so no VideoPlayerScreen surface changes.
 *  - `None` does nothing.
 *
 * [gate] is the shell's precondition check, run inside the evaluation effect
 * BEFORE the controller is consulted — a refusal skips the evaluation entirely
 * and leaves the controller's latch unconsumed (the desktop's video-surface
 * and onboarding preconditions; the default admits everything — Android has
 * none). [launchedWithExplicitDestination] is read ONCE via `remember` — a
 * mid-session flip after the evaluation is inert (the latch fired with the
 * first read).
 */
@Composable
fun SessionRestorePromptApplier(
    controller: SessionRestorationController,
    launchedWithExplicitDestination: Boolean,
    /** The shell's navigation arm, taking the snapshot (the shells' route folds differ). */
    navigate: (SessionRestorationSnapshot) -> Unit,
    /** The shell's preconditions, before the controller — a refusal leaves the latch unconsumed. */
    gate: suspend () -> Boolean = { true },
) {
    var prompt by remember { mutableStateOf<SessionRestorationSnapshot?>(null) }
    val explicitLaunch = remember { launchedWithExplicitDestination }
    // Host-level ON PURPOSE: the dismissal write launches on this scope, and
    // a scope declared inside the prompt block below is CANCELLED the moment
    // `prompt = null` removes that block from composition — the DataStore
    // write died with the dialog and the marker never landed.
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller, explicitLaunch) {
        if (!gate()) return@LaunchedEffect
        val launchContext = if (explicitLaunch) LaunchContext.DeepLink else LaunchContext.ColdStart
        when (val decision = controller.evaluate(launchContext)) {
            is SessionRestorationDecision.Ask -> prompt = decision.snapshot
            is SessionRestorationDecision.AutoRestore -> {
                controller.onRestoreHandled()
                navigate(decision.snapshot)
            }
            SessionRestorationDecision.None -> Unit
        }
    }

    prompt?.let { snapshot ->
        SessionRestorePromptDialog(
            snapshot = snapshot,
            onResume = {
                controller.onRestoreHandled()
                navigate(snapshot)
            },
            onDismissed = { scope.launch { controller.onAskDismissed(snapshot) } },
            onDismiss = { prompt = null },
        )
    }
}
