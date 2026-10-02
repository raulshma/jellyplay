package com.raulshma.jellyplay.navigation.playbackhost

import android.content.ComponentName
import android.content.Intent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.navigation.ExternalPlaybackOutcome
import com.raulshma.jellyplay.navigation.externalPlaybackOutcome

/**
 * The external-player launch protocol — the recorded deferred
 * `ExternalPlayerHost` design, landed now that the shell churned. Every step
 * that used to live composable-inline in `JellyPlayApp`'s navigate-filter and
 * ActivityResult callback is owned here, so the ORDERING between the pure
 * inputs/outputs around it (already pinned: [externalPlaybackOutcome],
 * `ExternalPlayerResultPolicy`, the MainViewModel report pair) is pinned too
 * (`ExternalPlayerHostTest`, fake-lambda choreography in the
 * `NavRequestCollectorTest` shape):
 *
 *  1. **resolve** — [buildLaunch] (MainViewModel's download-vs-stream
 *     resolver + subtitle payload) decides whether a hand-off is possible at
 *     all; `null` short-circuits everything (no report, no stash, no chooser);
 *  2. **report-start** — the server's playback-start report fires BEFORE the
 *     stash, so a session is never awaited on the UI side first;
 *  3. **stash** — the launch is remembered as [pendingLaunch]; this is the
 *     ONLY state (a plain field, not snapshot state — nothing composes off
 *     it; the composable constructs the host via `remember`). A targeted
 *     preferred player (the launch's `preferredApp`, resolved installed via
 *     [resolveComponent]) is recorded on the stash as `resolvedApp` —
 *     the key the result arm's contract parse routes on; uninstalled or
 *     unset preferences resolve null and fall back to the chooser (the
 *     jellyfin-android auto-revert pattern);
 *  4. **start** — a resolved target starts the component intent DIRECTLY;
 *     anything else runs through the `Intent.createChooser` hand-off. Both
 *     go through the caller-supplied [startChooser] seam;
 *  5. **failure clears stash + error** — a throwing `startChooser` (typically
 *     `ActivityNotFoundException`) clears the stash FIRST, then fires
 *     [notifyNoPlayerFound], so a stray later result can never credit a
 *     playback that never started;
 *  6. **result** — [onResult] consumes the stash exactly once, folds the
 *     returned extras through [externalPlaybackOutcome] (the resolved
 *     player's contract: MPV/mpvKt, MX, VLC, or the chooser alias parse) and
 *     reports playback stopped; a result with no stash is a no-op.
 *
 * The shell keeps only wiring: the `remember` construction, the
 * ActivityResultLauncher whose callback feeds [onResult] with the two raw
 * extras, and the navigate-filter branch's one [launch] call inside its
 * coroutine scope. The launcher deliberately arrives as a per-call parameter
 * rather than a constructor lambda — the shell must construct the host BEFORE
 * the launcher (the launcher's result callback needs the host), so a
 * constructor-captured launcher would be a forward reference.
 *
 * @param buildLaunch resolves the item into an [ExternalPlayerLaunch]
 *   (download file vs stream url, subtitle payload, preferred app) or null
 *   when hand-off is impossible.
 * @param resolveComponent resolves the launch's preferred app to an installed
 *   launch component ([resolveExternalPlayerComponent] in production);
 *   `null` = uninstalled — the chooser fallback.
 * @param reportStart the server playback-start report
 *   (MainViewModel.reportExternalPlaybackStart).
 * @param reportStopped the server playback-stop report with the parsed
 *   [ExternalPlaybackOutcome] (MainViewModel.reportExternalPlaybackStopped).
 * @param notifyNoPlayerFound the user-facing "no video player found" error
 *   emission for the failure arm.
 */
internal class ExternalPlayerHost(
    private val buildLaunch: suspend (ExternalPlayerRequest) -> ExternalPlayerLaunch?,
    private val resolveComponent: (com.raulshma.jellyplay.core.model.ExternalPlayerApp) -> ComponentName?,
    private val reportStart: (ExternalPlayerLaunch) -> Unit,
    private val reportStopped: (ExternalPlayerLaunch, ExternalPlaybackOutcome) -> Unit,
    private val notifyNoPlayerFound: () -> Unit,
) {

    /**
     * The stashed launch awaiting its ActivityResult — set by [launch],
     * consumed by [onResult], cleared by the failure arm. Null when nothing
     * is in flight.
     */
    var pendingLaunch: ExternalPlayerLaunch? = null
        private set

    /**
     * Runs the launch protocol (steps 1–5 in the class KDoc) for one
     * external-player hand-off. [startChooser] starts the chooser activity —
     * the shell's `ActivityResultLauncher.launch`; a non-cancellation throw
     * from it is the failure arm (step 5), absorbed exactly as the former
     * inline hand copy absorbed it. Declared upgrade over that copy: the
     * wrapper rethrows cancellation, so a cancelled hand-off leaves the stash
     * for a potential retry instead of clearing it and firing the
     * no-player-found error.
     */
    suspend fun launch(
        request: ExternalPlayerRequest,
        startChooser: (Intent) -> Unit,
    ) {
        val built = buildLaunch(request) ?: return
        reportStart(built)
        // Targeting: a preferred player that resolves installed starts its
        // component directly (recorded on the stash for the result contract);
        // an unset or uninstalled choice falls back to the chooser. The
        // resolver itself answers null for the non-targeted chooser arm.
        val resolved = resolveComponent(built.preferredApp)
        val stash = built.copy(resolvedApp = built.preferredApp.takeIf { resolved != null })
        pendingLaunch = stash
        val startIntent = if (resolved != null) {
            Intent(built.intent).setComponent(resolved)
        } else {
            Intent.createChooser(built.intent, CHOOSER_TITLE)
        }
        runCatchingRethrowingCancellation { startChooser(startIntent) }.onFailure {
            pendingLaunch = null
            notifyNoPlayerFound()
        }
    }

    /**
     * The ActivityResult arm (step 6): consume the stash once, fold the
     * result extras through [externalPlaybackOutcome] (routed by the stashed
     * `resolvedApp` — the resolved player's contract; the chooser arm's
     * alias parse never credits completion) and report playback stopped.
     * With no stash the call is a complete no-op (a result arriving after a
     * failed or already-consumed launch).
     */
    fun onResult(extras: Map<String, Any?>) {
        val stashed = pendingLaunch
        pendingLaunch = null
        if (stashed == null) return
        reportStopped(
            stashed,
            externalPlaybackOutcome(
                resolvedApp = stashed.resolvedApp,
                startPositionTicks = stashed.startPositionTicks,
                extras = extras,
            ),
        )
    }

    private companion object {
        /** Chooser title, verbatim from the former inline hand copy. */
        const val CHOOSER_TITLE = "Open with…"
    }
}

/**
 * Drains the result [android.os.Bundle] into the plain map
 * [ExternalPlayerHost.onResult] parses — the Android-typed half of the
 * result seam (the per-contract parse itself stays Android-free).
 */
internal fun android.os.Bundle.toExtrasMap(): Map<String, Any?> = keySet().associateWith { get(it) }
