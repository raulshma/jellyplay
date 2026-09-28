package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import java.util.UUID

/**
 * One external subtitle handed to the target player: the server [url] the
 * player streams it from, the display [name], and the [filename] tag
 * (JellyPlay fills it with the stream language).
 */
data class ExternalSubtitle(
    val url: String,
    val name: String,
    val filename: String? = null,
    /** Pre-selects this track in the target player (`subs.enable`). */
    val isSelected: Boolean = false,
)

/**
 * The external-player hand-off request — the four fields every dispatch site
 * (the host-decision router, the nav request controller, the shell model and
 * the launch resolver) passes together: which item, which server media source
 * (null for Live TV channel hand-offs), where to resume, and the route's
 * selected subtitle track (feeds the launch's `subs.enable` extra).
 */
data class ExternalPlayerRequest(
    val itemId: String,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0L,
    val subtitleStreamIndex: Int? = null,
)

/**
 * One external-player hand-off: the launch [intent] plus the identity the
 * reporting pair needs (MainViewModel's report-start / report-stop over the
 * server's playback session), the subtitle payload passed at launch, and the
 * targeting state the result side needs: [preferredApp] is the user's choice,
 * [resolvedApp] the app actually targeted (null on the chooser arm — unset
 * preference or uninstalled app), which picks the result contract
 * (`ExternalPlayerResultPolicy`). Previously declared in the app root package
 * beside its only builder; moved beside [ExternalPlayerHost] so the extras
 * vocabulary has one home.
 */
data class ExternalPlayerLaunch(
    val intent: Intent,
    val itemId: String,
    val startPositionTicks: Long,
    val playSessionId: String,
    val subtitles: List<ExternalSubtitle> = emptyList(),
    val preferredApp: ExternalPlayerApp = ExternalPlayerApp.SYSTEM_CHOOSER,
    val resolvedApp: ExternalPlayerApp? = null,
)

/**
 * Builds one [ExternalPlayerLaunch] — the OUTBOUND extras vocabulary of the
 * external-player hand-off (the RESULT side is [externalPlaybackOutcome]'s
 * per-contract parse, read from the shell's ActivityResult callback):
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
 *  - `subs` (Uri[]) / `subs.name` (display titles) / `subs.filename`
 *    (languages) — the external-subtitle payload, jellyfin-android protocol;
 *  - `subs.enable` — the selected track's URL, pre-selecting it in the
 *    target player; `subtitles_location` carries the same URL when the
 *    target is VLC (its single-subtitle extra);
 *  - a freshly minted `playSessionId` per call, so every hand-off reports as
 *    its own playback session.
 *
 * Pure construction: no resolver, no PackageManager, no coroutine — the
 * caller (MainViewModel.buildExternalPlayerLaunch) owns the source and
 * subtitle resolution, the host owns the launch choreography (including the
 * per-player component targeting and its uninstalled fallback).
 */
fun externalPlayerLaunch(
    itemId: String,
    resolvedUrl: String,
    title: String,
    startPositionTicks: Long,
    subtitles: List<ExternalSubtitle> = emptyList(),
    preferredApp: ExternalPlayerApp = ExternalPlayerApp.SYSTEM_CHOOSER,
): ExternalPlayerLaunch {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(resolvedUrl), "video/*")
        putExtra("title", title)
        putExtra("return_result", true)
        val startMs = startPositionTicks / 10_000
        if (startMs > 0) putExtra("position", startMs)
        if (subtitles.isNotEmpty()) {
            putParcelableArrayListExtra("subs", ArrayList(subtitles.map { Uri.parse(it.url) }))
            putExtra("subs.name", subtitles.map { it.name }.toTypedArray())
            putExtra("subs.filename", subtitles.map { it.filename }.toTypedArray())
            subtitles.firstOrNull { it.isSelected }?.let { selected ->
                putExtra("subs.enable", Uri.parse(selected.url))
                // VLC's single-subtitle extra — only it reads this key.
                if (preferredApp == ExternalPlayerApp.VLC) {
                    putExtra("subtitles_location", selected.url)
                }
            }
        }
    }
    return ExternalPlayerLaunch(
        intent = intent,
        itemId = itemId,
        startPositionTicks = startPositionTicks,
        playSessionId = UUID.randomUUID().toString(),
        subtitles = subtitles,
        preferredApp = preferredApp,
    )
}

/**
 * Resolves a targeted [ExternalPlayerApp] against the installed packages:
 * a video-typed ACTION_VIEW probe scoped to the app's package must answer, so
 * an uninstalled (or video-incapable) choice resolves to `null` and the host
 * falls back to the chooser (the jellyfin-android auto-revert pattern).
 * `SYSTEM_CHOOSER` (no [ExternalPlayerApp.activity]) always resolves `null`.
 */
fun resolveExternalPlayerComponent(
    packageManager: android.content.pm.PackageManager,
    app: ExternalPlayerApp,
): android.content.ComponentName? {
    val activity = app.activity ?: return null
    val probe = Intent(Intent.ACTION_VIEW).setType("video/*").setPackage(app.packageName)
    val handlesVideo = packageManager.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null
    return if (handlesVideo) android.content.ComponentName(app.packageName, activity) else null
}
