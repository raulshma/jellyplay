package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationController
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationSnapshot
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorePromptApplier
import com.raulshma.jellyplay.feature.player.video.session.toPlayerRoute

/**
 * The Android shell's cold-start session-restoration host (feature: "resume
 * last watched" after the app itself was killed) — a thin wrapper over the
 * shared [SessionRestorePromptApplier], which owns the evaluate-ONCE ladder,
 * the dialog's three arms and the host-level dismissal scope. This shell
 * contributes only its launch fact and its navigator:
 *
 *  - an explicit launch destination (shortcut / deep link / shared text /
 *    search) becomes the applier's DeepLink fold — the policy refuses to
 *    hijack it; a plain cold start is a ColdStart
 *    ([com.raulshma.jellyplay.feature.player.video.session.LaunchContext]).
 *    The fact rides [com.raulshma.jellyplay.shell.ShellInfra] (classified in
 *    onCreate) and is read ONCE via `remember` (in the applier) — a
 *    mid-session onNewIntent flip after the evaluation is inert (the
 *    controller's latch fired with the first read).
 *  - [SessionRestorePromptApplier.navigate] lands on the same player route a
 *    Continue Watching tap takes (the snapshot's [toPlayerRoute]; the server
 *    resume point is the source, so nothing new is persisted).
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
    SessionRestorePromptApplier(
        controller = controller,
        launchedWithExplicitDestination = launchedWithExplicitDestination,
        navigate = { snapshot -> navigator.navigate(snapshot.toPlayerRoute()) },
    )
}
