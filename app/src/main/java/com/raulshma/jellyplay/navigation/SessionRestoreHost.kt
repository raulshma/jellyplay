package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.feature.player.video.session.LaunchContext
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationController
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationDecision
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationSnapshot
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorePromptDialog
import com.raulshma.jellyplay.feature.player.video.session.toPlayerRoute
import kotlinx.coroutines.launch

/**
 * The Android shell's cold-start session-restoration host (feature: "resume
 * last watched" after the app itself was killed). The [SessionRestorationController]
 * evaluates ONCE per process — this composable is the shell-side APPLIER:
 *
 *  - [LaunchContext] folds from the launch facts the shell already owns: an
 *    explicit launch destination (shortcut / deep link / shared text / search)
 *    becomes [LaunchContext.DeepLink] — the policy refuses to hijack it; a
 *    plain cold start is [LaunchContext.ColdStart]. The fact rides
 *    [com.raulshma.jellyplay.shell.ShellInfra] (classified in onCreate) and is
 *    read ONCE via `remember` — a mid-session onNewIntent flip after the
 *    evaluation is inert (the controller's latch fired with the first read).
 *  - `AutoRestore` navigates straight to the player route at the snapshot's
 *    resume position (the same `startPositionTicks` entry point a Continue
 *    Watching tap takes; the server resume point is the source, so nothing new
 *    is persisted).
 *  - `Ask` parks the snapshot in a local dialog state and renders the shared
 *    [SessionRestorePromptDialog] — the app's dialog language (tone PRIMARY:
 *    an affirmative forward action), styled "Resume '<title>'?" with Resume /
 *    Dismiss. The prompt lives in the SHELL, not the player screen, so no
 *    VideoPlayerScreen surface changes.
 *
 * INCOGNITO: an incognito session reports no progress to the server, so the
 * resume query finds nothing to offer for it — no resume DATA is ever stored
 * locally (see [SessionRestorationSnapshot]); the feature's one local write is
 * the prompt's dismissal marker (see [SessionRestorationController.onAskDismissed]).
 */
@Composable
internal fun SessionRestorePromptHost(
    controller: SessionRestorationController,
    launchedWithExplicitDestination: Boolean,
    navigator: Navigator,
) {
    var prompt by remember { mutableStateOf<SessionRestorationSnapshot?>(null) }
    val explicitLaunch = remember { launchedWithExplicitDestination }
    // Host-level ON PURPOSE: the dismissal write launches on this scope, and
    // a scope declared inside the prompt block below is CANCELLED the moment
    // `prompt = null` removes that block from composition — the DataStore
    // write died with the dialog and the marker never landed.
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller, explicitLaunch) {
        val launchContext = if (explicitLaunch) LaunchContext.DeepLink else LaunchContext.ColdStart
        when (val decision = controller.evaluate(launchContext)) {
            is SessionRestorationDecision.Ask -> prompt = decision.snapshot
            is SessionRestorationDecision.AutoRestore -> {
                controller.onRestoreHandled()
                navigator.navigate(decision.snapshot.toPlayerRoute())
            }
            SessionRestorationDecision.None -> Unit
        }
    }

    prompt?.let { snapshot ->
        SessionRestorePromptDialog(
            snapshot = snapshot,
            onResume = {
                controller.onRestoreHandled()
                navigator.navigate(snapshot.toPlayerRoute())
            },
            onDismissed = { scope.launch { controller.onAskDismissed(snapshot) } },
            onDismiss = { prompt = null },
        )
    }
}
