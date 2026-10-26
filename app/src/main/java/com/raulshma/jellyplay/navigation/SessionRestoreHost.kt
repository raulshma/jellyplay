package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.video.session.LaunchContext
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationController
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationDecision
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationSnapshot
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
 *    [ConfirmDialog] — the app's dialog language (tone PRIMARY: an affirmative
 *    forward action), here styled "Resume '<title>'?" with Resume / Dismiss.
 *    The prompt lives in the SHELL, not the player screen, so no
 *    VideoPlayerScreen surface changes.
 *
 * The strings are literal English deliberately: the shells' one-shot texts have
 * no shared resource home (desktop has no Android res — the
 * DesktopLinkOpenMessages precedent; the Android MainViewModel's bus posts are
 * the same register).
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
        // ConfirmDialog invokes the host's onDismiss after the confirm arm
        // too, so the prompt state clears itself on both exits — and this
        // flag splits the arms: only a REAL dismiss persists the marker.
        var confirmed by remember(snapshot) { mutableStateOf(false) }
        ConfirmDialog(
            title = "Resume \"${snapshot.title}\"?",
            message = "Continue where you left off.",
            confirmText = "Resume",
            dismissText = "Dismiss",
            tone = ConfirmTone.PRIMARY,
            onConfirm = {
                confirmed = true
                controller.onRestoreHandled()
                navigator.navigate(snapshot.toPlayerRoute())
            },
            onDismiss = {
                prompt = null
                if (!confirmed) scope.launch { controller.onAskDismissed(snapshot) }
            },
        )
    }
}

/** The snapshot's player entry point: the resume position as route ticks. */
private fun SessionRestorationSnapshot.toPlayerRoute(): Route =
    Route.VideoPlayer(itemId = itemId, startPositionTicks = positionMs * 10_000)
