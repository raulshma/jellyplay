package com.raulshma.jellyplay.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.navigation.playbackhost.ExternalPlayerHost
import com.raulshma.jellyplay.navigation.playbackhost.ExternalPlayerReports
import com.raulshma.jellyplay.navigation.playbackhost.resolveExternalPlayerComponent
import com.raulshma.jellyplay.navigation.playbackhost.toExtrasMap

/**
 * The external-player half of `MainContent`, extracted into a remembered
 * pair-holder (the `NavRequestController` idiom: a plain class with narrow
 * public members, constructed in one `remember` + the launcher protocol that
 * must be composed around it). Everything here is the launch protocol's own
 * wiring — the host construction, its no-player message path, and the
 * ActivityResultLauncher whose result callback feeds `host.onResult`.
 *
 * NOT here: the playback-host navigateFilter and the request-dispatch loops
 * that consume the pair — those are `NavRequestController`'s
 * (NavRequestController.kt), which takes both members as constructor seams.
 */
internal class ExternalPlayerLauncherHost(
    val host: ExternalPlayerHost,
    val launcher: ActivityResultLauncher<Intent>,
)

/**
 * Constructs the [ExternalPlayerLauncherHost] pair on the composition's
 * stable seams — the activity [android.content.Context] (stable for the
 * host's lifetime) and the [ExternalPlayerReports] reporting contract
 * (a Koin single, resolved through the ShellInfra bundle).
 */
@Composable
internal fun rememberExternalPlayerLauncherHost(
    externalPlayerReports: ExternalPlayerReports,
): ExternalPlayerLauncherHost {
    val context = LocalContext.current
    // The app-wide (commonMain) message bus — the ONE bus since the legacy
    // androidMain feedback bus was deleted; same instance the root provider
    // in JellyPlayApp supplies.
    val userMessageBus = LocalUserMessageBus.current
    val currentMessageBus by rememberUpdatedState(userMessageBus)
    // One home for the external-player launch protocol (ExternalPlayerHost,
    // beside PlaybackHostRouter): resolve → report-start → stash → chooser →
    // failure-clears-stash+error, plus the result arm's position parse +
    // report-stop — the ordering between those steps is pinned there
    // (ExternalPlayerHostTest). The host owns the pending-launch stash, so it
    // is remembered (stateful, unlike the stateless NavRequestCollector the
    // remembered NavRequestController below constructs); the
    // ActivityResultLauncher arrives as a per-call seam because the shell
    // constructs the host BEFORE the launcher (the launcher's result callback
    // feeds host.onResult), and the holder needs the launcher in turn. The
    // bus rides a rememberUpdatedState wrapper so the remembered host always
    // posts to the composition's current bus.
    // The preferred-app targeting resolution: the production packageManager
    // probe (null when the chosen app is uninstalled → chooser fallback).
    // Context is the composition's activity — stable for the host's lifetime.
    val externalPlayerHost = remember {
        ExternalPlayerHost(
            buildLaunch = externalPlayerReports::buildExternalPlayerLaunch,
            resolveComponent = { app -> resolveExternalPlayerComponent(context.packageManager, app) },
            reportStart = externalPlayerReports::reportExternalPlaybackStart,
            reportStopped = externalPlayerReports::reportExternalPlaybackStopped,
            notifyNoPlayerFound = {
                // Resolved at POST time through the captured activity context —
                // the same context (and resource table) the bus's former
                // render-time UiText.Resource resolution used; :app's classpath
                // has no compose-resources StringResource, and the string files
                // stay untouched.
                currentMessageBus.error(
                    context.getString(
                        com.raulshma.jellyplay.shared.core.ui.R.string.msg_no_video_player_found,
                    ),
                )
            },
        )
    }
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result: ActivityResult ->
        externalPlayerHost.onResult(
            extras = result.data?.extras?.toExtrasMap() ?: emptyMap(),
        )
    }
    return ExternalPlayerLauncherHost(
        host = externalPlayerHost,
        launcher = externalPlayerLauncher,
    )
}
