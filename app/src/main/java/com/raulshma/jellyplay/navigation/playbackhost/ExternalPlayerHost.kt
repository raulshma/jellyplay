package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import com.raulshma.jellyplay.ExternalPlayerLaunch
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.navigation.externalPlayerPositionTicks

/**
 * The external-player launch protocol — the recorded deferred
 * `ExternalPlayerHost` design, landed now that the shell churned. Every step
 * that used to live composable-inline in `JellyPlayApp`'s navigate-filter and
 * ActivityResult callback is owned here, so the ORDERING between the pure
 * inputs/outputs around it (already pinned: [externalPlayerPositionTicks],
 * `ExternalPlayerResultPolicy`, the MainViewModel report pair) is pinned too
 * (`ExternalPlayerHostTest`, fake-lambda choreography in the
 * `NavRequestCollectorTest` shape):
 *
 *  1. **resolve** — [buildLaunch] (MainViewModel's download-vs-stream
 *     resolver) decides whether a hand-off is possible at all; `null`
 *     short-circuits everything (no report, no stash, no chooser);
 *  2. **report-start** — the server's playback-start report fires BEFORE the
 *     stash, so a session is never awaited on the UI side first;
 *  3. **stash** — the launch is remembered as [pendingLaunch]; this is the
 *     ONLY state (a plain field, not snapshot state — nothing composes off
 *     it; the composable constructs the host via `remember`);
 *  4. **chooser** — the `Intent.createChooser` hand-off runs through the
 *     caller-supplied [launch]'s `startChooser` seam;
 *  5. **failure clears stash + error** — a throwing `startChooser` (typically
 *     `ActivityNotFoundException`) clears the stash FIRST, then fires
 *     [notifyNoPlayerFound], so a stray later result can never credit a
 *     playback that never started;
 *  6. **result** — [onResult] consumes the stash exactly once, folds the
 *     returned position through [externalPlayerPositionTicks] (the
 *     "position"/"positionMs" alias + `>=0` gate + ms→ticks parse) and
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
 *   (download file vs stream url) or null when hand-off is impossible.
 * @param reportStart the server playback-start report
 *   (MainViewModel.reportExternalPlaybackStart).
 * @param reportStopped the server playback-stop report with the final
 *   position in ticks (MainViewModel.reportExternalPlaybackStopped).
 * @param notifyNoPlayerFound the user-facing "no video player found" error
 *   emission for the failure arm.
 */
internal class ExternalPlayerHost(
    private val buildLaunch: suspend (
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ) -> ExternalPlayerLaunch?,
    private val reportStart: (ExternalPlayerLaunch) -> Unit,
    private val reportStopped: (ExternalPlayerLaunch, Long) -> Unit,
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
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        startChooser: (Intent) -> Unit,
    ) {
        val built = buildLaunch(itemId, mediaSourceId, startPositionTicks) ?: return
        reportStart(built)
        pendingLaunch = built
        val chooser = Intent.createChooser(built.intent, CHOOSER_TITLE)
        runCatchingRethrowingCancellation { startChooser(chooser) }.onFailure {
            pendingLaunch = null
            notifyNoPlayerFound()
        }
    }

    /**
     * The ActivityResult arm (step 6): consume the stash once, fold the two
     * raw position extras through [externalPlayerPositionTicks] (`-1` when
     * the external player reported no parseable position — the caller passes
     * nulls when the result carries no extras) and report playback stopped.
     * With no stash the call is a complete no-op (a result arriving after a
     * failed or already-consumed launch).
     */
    fun onResult(position: Any?, positionMs: Any?) {
        val stashed = pendingLaunch
        pendingLaunch = null
        if (stashed == null) return
        reportStopped(stashed, externalPlayerPositionTicks(position, positionMs))
    }

    private companion object {
        /** Chooser title, verbatim from the former inline hand copy. */
        const val CHOOSER_TITLE = "Open with…"
    }
}
