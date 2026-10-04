package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The desktop link-open seam: ONE pure parse/route policy behind the two
 * open-with inputs this shell gained — the process argv (`main(args)`, the OS
 * "Open with" chain) and the **Ctrl+Shift+V** clipboard paste — plus the
 * single-slot pending holder the argv seed, the clipboard accelerator and the
 * second-instance forward ([DesktopOpenRequestChannel]) all write into and
 * [DesktopNavScaffold] drains from.
 *
 * Parsing goes through the shared [DeepLinkGrammar] — the same grammar the
 * Android shell's DeepLinkHandler builds and parses every link with, so a
 * link emitted anywhere (notifications, widgets, Discord Rich Presence,
 * share sheet) parses identically on desktop. The JVM side has no
 * android.net.Uri, so the host/path-segments/query-parameters extraction the
 * grammar's [DeepLinkGrammar.parseCustom]/[DeepLinkGrammar.parseWeb] expect
 * is reproduced here over java.net.URI (pure, pinned by DesktopLinkOpenTest):
 * strict URI syntax is DELIBERATE — clipboard junk and local file paths
 * (`show.m3u`, `movie.strm`, `C:\…`) fail the parse and fall to
 * [DesktopOpenLinkEvent.NoLinkFound] instead of half-parsing.
 *
 * SCOPE BOUNDARY: only LINKS open. Local
 * `.m3u`/`.strm` playlist files are NOT routed — the codebase has the
 * playback side of those formats (HLS `.m3u8` live-tuner streams, offline
 * resync) but no file-level "open this playlist file and play" import flow,
 * and this feature does not build one. An argv file path is silently ignored
 * (a double-clicked unrelated file must not scold the user); a clipboard
 * miss gets explicit snackbar feedback, because the user explicitly pressed
 * the paste accelerator.
 */

/** One classified open-with input, ready for the scaffold to consume. */
sealed interface DesktopOpenLinkEvent {

    /** A parsable link — the scaffold routes [target] through [DesktopLinkOpenPolicy.targetRoute]. */
    data class Link(val target: DeepLinkTarget) : DesktopOpenLinkEvent

    /**
     * The input was not a parsable link (clipboard paste only — argv misses
     * never produce this event, see the scope boundary in the file KDoc).
     */
    data object NoLinkFound : DesktopOpenLinkEvent
}

/**
 * Pure parse/route policy for the desktop link-open inputs (no Compose, no
 * AWT, no filesystem — JVM-pinnable by DesktopLinkOpenTest).
 */
object DesktopLinkOpenPolicy {

    /**
     * Classifies one raw open-with input (an argv entry or the clipboard's
     * text): a [DeepLinkTarget] for a `jellyplay://` link or the https
     * mirror (`https://raulshma.github.io/jellyplay/…`), `null` for
     * everything else — local file paths included (see the scope boundary in
     * the file KDoc). Surrounding whitespace is trimmed (clipboard text
     * routinely carries a trailing newline); the https scheme is matched
     * exactly like the Android handler's (`DeepLinkGrammar.SCHEME_HTTPS`
     * only — a plain http:// URL is not a link we parse).
     */
    fun parse(raw: String): DeepLinkTarget? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = try {
            java.net.URI(trimmed)
        } catch (_: java.net.URISyntaxException) {
            return null
        }
        return when (uri.scheme?.lowercase()) {
            DeepLinkGrammar.SCHEME_CUSTOM -> DeepLinkGrammar.parseCustom(
                host = uri.host?.lowercase(),
                pathSegments = uri.pathSegments(),
                queryParameters = uri.rawQuery.queryParameters(),
            )
            DeepLinkGrammar.SCHEME_HTTPS ->
                if (uri.host?.lowercase() == DeepLinkGrammar.HOST_WEB) {
                    DeepLinkGrammar.parseWeb(uri.pathSegments())
                } else {
                    null
                }
            else -> null
        }
    }

    /**
     * The argv fold: the FIRST parsable link among [args], or `null` when
     * none parses (mixed argv — a link plus junk — opens the link and
     * ignores the rest; an all-file/all-junk argv opens nothing, silently).
     */
    fun firstLink(args: List<String>): DeepLinkTarget? =
        args.firstNotNullOfOrNull { parse(it) }

    /**
     * The link-args filter the second-instance forward enqueues: the raw
     * argv entries that ARE parsable links (forwarded verbatim, one per
     * line — the running instance re-parses, so the writer never has to
     * serialize targets). `.m3u`/`.strm` paths and junk drop out here, which
     * is exactly the scope boundary: the forward channel carries links only.
     */
    fun linkArgs(args: List<String>): List<String> =
        args.filter { parse(it) != null }

    /**
     * The parsed-target → route fold — the desktop twin of the Android
     * shell's DeepLinkHandler.targetToRoute (same rows, same nulls):
     * [DeepLinkTarget.SyncPlayJoin] routes NOWHERE on both shells — it is
     * the Discord Rich Presence Join button's payload, consumed here by
     * DiscordPresenceService.handleJoinSecret, not a navigation destination.
     * `null` (with the grammar's own parse misses folded in) is the
     * scaffold's cue for the no-desktop-route snackbar.
     */
    fun targetRoute(target: DeepLinkTarget?): Route? = when (target) {
        is DeepLinkTarget.MediaDetail -> Route.MediaDetail(target.itemId)
        is DeepLinkTarget.NewsletterSection -> Route.NewsletterSectionList(target.section)
        is DeepLinkTarget.SeerrDetail -> Route.SeerrDetail(
            tmdbId = target.tmdbId,
            mediaType = target.mediaType,
        )
        DeepLinkTarget.Search -> Route.Search
        DeepLinkTarget.Settings -> Route.Settings
        DeepLinkTarget.Downloads -> Route.Downloads
        DeepLinkTarget.Library -> Route.Library
        is DeepLinkTarget.SyncPlayJoin -> null
        null -> null
    }

    /** android.net.Uri.getPathSegments over java.net.URI's path (decoded, like Android's). */
    private fun java.net.URI.pathSegments(): List<String> =
        (path ?: "").removePrefix("/").split('/').map { percentDecode(it) }

    /** android.net.Uri.getQueryParameter over java.net.URI's raw query. */
    private fun String?.queryParameters(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap {
            for (pair in split('&')) {
                val name = pair.substringBefore('=', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
                    ?: continue
                val value = pair.substringAfter('=', missingDelimiterValue = "")
                put(percentDecode(name), percentDecode(value))
            }
        }
    }

    /**
     * Percent-decoding the android.net.Uri way: `%XX` sequences decode
     * (UTF-8), everything else — `+` included — stays literal (Uri.decode
     * never treats `+` as space; form-encoding's `+`-is-space convention
     * does not apply to URI paths/queries). Malformed sequences fall back to
     * the raw text — classification must not throw on clipboard junk.
     */
    private fun percentDecode(raw: String): String {
        if ('%' !in raw) return raw
        return try {
            java.net.URLDecoder.decode(raw.replace("+", "%2B"), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            raw
        }
    }
}

/** Snackbar wording the scaffold shows for the no-route / no-link outcomes. */
internal object DesktopLinkOpenMessages {
    const val NO_LINK_IN_CLIPBOARD = "Clipboard does not contain a JellyPlay link."
    const val NO_DESKTOP_ROUTE = "This link has no desktop destination."
}

/**
 * The pending-open queue between the window-level inputs (argv seed,
 * Ctrl+Shift+V accelerator, the second-instance forward watcher) and the
 * scaffold's drain effect. A [kotlinx.coroutines.channels.Channel] (the
 * consume-once primitive) rather than a StateFlow: events must NEVER conflate
 * (two identical pastes route twice), must survive until the scaffold first
 * composes (an argv link waits out the whole session-restore window), and
 * must route exactly once even when the drain effect is torn down and
 * re-composed — receiveAsFlow() gives all three for free, and the single
 * consumer (the scaffold) can never fall behind: the buffer holds events,
 * `trySend` from the AWT/argv/daemon-watcher threads never blocks or drops
 * while under capacity.
 */
internal class DesktopLinkOpenQueue {

    private val events = Channel<DesktopOpenLinkEvent>(Channel.BUFFERED)

    /** The scaffold-side consume flow: each event is delivered exactly once. */
    val pending: Flow<DesktopOpenLinkEvent> = events.receiveAsFlow()

    /**
     * Classifies [raw] (usually the clipboard text) and enqueues the
     * outcome — a [DesktopOpenLinkEvent.Link] or the explicit
     * [DesktopOpenLinkEvent.NoLinkFound] feedback event (the paste
     * accelerator's caller always wants an outcome, never silence).
     *
     * @return the enqueued event (the caller may also want it for logging).
     */
    fun submitRaw(raw: String): DesktopOpenLinkEvent {
        val target = DesktopLinkOpenPolicy.parse(raw)
        val event = if (target != null) DesktopOpenLinkEvent.Link(target) else DesktopOpenLinkEvent.NoLinkFound
        events.trySend(event)
        return event
    }

    /** Enqueues an already-parsed target (the argv seed / forwarded link path). */
    fun submitLink(target: DeepLinkTarget) {
        events.trySend(DesktopOpenLinkEvent.Link(target))
    }
}

/**
 * The system-clipboard text read behind the Ctrl+Shift+V accelerator (the
 * ONE AWT touchpoint of the link-open seam, split out so the policy above
 * stays JVM-pure). Returns `null` when the clipboard is unreadable
 * (headless session, clipboard briefly held by another process — Windows
 * fails the open transiently) or holds no string flavor; the caller maps
 * `null` to the same [DesktopOpenLinkEvent.NoLinkFound] outcome as
 * unparsable text, so the user always gets one snackbar either way.
 */
internal object DesktopClipboard {
    fun readText(): String? = runCatching {
        val contents = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
        contents?.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()
}
