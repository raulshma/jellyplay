package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import android.net.Uri
import java.util.UUID

/**
 * One external-player hand-off: the launch [intent] plus the identity the
 * reporting pair needs (MainViewModel's report-start / report-stop over the
 * server's playback session). Previously declared in the app root package
 * beside its only builder; moved beside [ExternalPlayerHost] so the extras
 * vocabulary has one home.
 */
data class ExternalPlayerLaunch(
    val intent: Intent,
    val itemId: String,
    val startPositionTicks: Long,
    val playSessionId: String,
)

/**
 * Builds one [ExternalPlayerLaunch] — the OUTBOUND extras vocabulary of the
 * external-player hand-off (the RESULT side is
 * [externalPlayerPositionTicks]'s "position"/"positionMs" alias, read by the
 * shell's ActivityResult callback):
 *
 *  - `ACTION_VIEW` with the resolved `file://` URI or stream URL, typed so
 *    any video player can accept it (the exact MIME literal is pinned in
 *    `ExternalPlayerLaunchTest` — a literal slash-star cannot appear inside a
 *    Kotlin block comment: comments nest);
 *  - `title` — the display title (offline item title / download name /
 *    server item name, per the resolver);
 *  - `return_result` — advertises that the external player must report its
 *    final position back, so the result arm can credit watched progress;
 *  - `position` — the resume position in MILLISECONDS (ticks ÷ 10 000),
 *    omitted entirely when the start position is zero;
 *  - a freshly minted `playSessionId` per call, so every hand-off reports as
 *    its own playback session.
 *
 * Pure construction: no resolver, no reporting, no coroutine — the caller
 * (MainViewModel.buildExternalPlayerLaunch) owns the source resolution, the
 * host owns the launch choreography.
 */
fun externalPlayerLaunch(
    itemId: String,
    resolvedUrl: String,
    title: String,
    startPositionTicks: Long,
): ExternalPlayerLaunch {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(resolvedUrl), "video/*")
        putExtra("title", title)
        putExtra("return_result", true)
        val startMs = startPositionTicks / 10_000
        if (startMs > 0) putExtra("position", startMs)
    }
    return ExternalPlayerLaunch(
        intent = intent,
        itemId = itemId,
        startPositionTicks = startPositionTicks,
        playSessionId = UUID.randomUUID().toString(),
    )
}
