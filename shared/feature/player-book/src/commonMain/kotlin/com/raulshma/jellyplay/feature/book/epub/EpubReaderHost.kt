package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import okio.Path

/** Where the reflowable reader is in its boot/ready sequence (fed by `reader.js`). */
internal enum class EpubReaderStatus {
    /** Book bytes handed to the WebView, not yet displayed. */
    LOADING,

    /** `locations.generate` finished (can take seconds on large books). */
    LOCATIONS_READY,

    /** First page displayed — the reader is interactive. */
    READY,

    /** The WebView could not open the book. */
    ERROR,
}

/**
 * Event sink the platform hosts feed from the JS bridge. Callers capture
 * Compose state / ViewModel calls here; the bridge threads (JavascriptInterface
 * on Android, the CEF poll on desktop) invoke these off the main thread, so
 * every callback must stay thread-safe (StateFlow/state writes are).
 */
internal class EpubReaderCallbacks(
    val onPercentChanged: (Double) -> Unit = {},
    val onStatusChanged: (EpubReaderStatus) -> Unit = {},
    val onDirectionReported: (ReadingDirection) -> Unit = {},
    val onTocReady: (List<EpubTocItem>) -> Unit = {},
)

/**
 * What the screen may do to a live EPUB rendition. `next`/`prev` are physical
 * (JS `rendition.next/prev`) — reading-direction mapping stays with the screen,
 * same as the paged reader's keyboard handling.
 */
internal interface EpubReaderHandle {

    /** CEF component download fraction (0f..1f) while downloading; `null` otherwise. */
    val viewerDownloadProgress: State<Float?>

    fun next()

    fun prev()

    fun goTo(href: String)
}

/**
 * The platform EPUB host seam. Each actual both owns the platform WebView
 * (rendering it into the composition) and returns the command handle; the book
 * file is base64-inlined into the self-contained reader page, so no file://
 * access is granted to the WebView.
 */
@Composable
internal expect fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    theme: ReaderTheme,
    fontSizePx: Int,
    callbacks: EpubReaderCallbacks,
): EpubReaderHandle

/** ReaderTheme → the `setTheme` argument `reader.js` understands. */
internal fun ReaderTheme.toJsName(): String = when (this) {
    ReaderTheme.DARK -> "dark"
    ReaderTheme.SEPIA -> "sepia"
    ReaderTheme.LIGHT -> "light"
}

/** A JSON string literal for embedding a value in an evaluateJavascript script. */
internal fun String.toJsonStringLiteral(): String = buildString {
    append('"')
    for (c in this@toJsonStringLiteral) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}

/**
 * Max base64 chars per `loadBookChunk` frame. A whole-book single
 * evaluateJavascript payload peaks at ~3x the book size in transient memory
 * and Android WebView is known to fail on multi-MB script strings — chunks
 * stay far below that ceiling.
 */
internal const val BOOK_CHUNK_CHARS = 1_500_000

/*
 * The reader.js command surface, shaped once here so the Android and desktop
 * hosts cannot drift apart — each host only supplies its own
 * evaluate(Java)Script seam.
 */

internal fun buildLoadBookBeginScript(
    chunkCount: Int,
    resumePercent: Double,
    theme: ReaderTheme,
    fontSizePx: Int,
): String = "window.jellyPlayReader.loadBookBegin(" +
    "$chunkCount, ${resumePercent.coerceIn(0.0, 1.0)}, '${theme.toJsName()}', $fontSizePx)"

internal fun buildLoadBookChunkScript(index: Int, chunk: String): String =
    "window.jellyPlayReader.loadBookChunk($index, \"$chunk\")"

internal fun buildLoadBookEndScript(): String = "window.jellyPlayReader.loadBookEnd()"

internal fun buildSetThemeScript(theme: ReaderTheme): String =
    "window.jellyPlayReader.setTheme('${theme.toJsName()}')"

internal fun buildSetFontSizeScript(fontSizePx: Int): String =
    "window.jellyPlayReader.setFontSize($fontSizePx)"

internal fun buildNextScript(): String = "window.jellyPlayReader.next()"

internal fun buildPrevScript(): String = "window.jellyPlayReader.prev()"

internal fun buildGoToScript(href: String): String =
    "window.jellyPlayReader.goTo(${href.toJsonStringLiteral()})"

internal fun buildConsumeEventsScript(): String = "window.jellyPlayReader.consumeEvents()"

/**
 * The chunked book transfer — reader.js assembles the chunks and decodes once
 * on loadBookEnd. The saved theme/font ride the begin frame so the rendition
 * is built with the user's preferences. [eval] is the platform's
 * evaluate(Java)Script seam, so the protocol lives once for both hosts.
 */
internal fun sendBookChunks(
    base64: String,
    resumePercent: Double,
    theme: ReaderTheme,
    fontSizePx: Int,
    eval: (String) -> Unit,
) {
    val chunks = base64.chunked(BOOK_CHUNK_CHARS)
    eval(buildLoadBookBeginScript(chunks.size, resumePercent, theme, fontSizePx))
    chunks.forEachIndexed { index, chunk ->
        eval(buildLoadBookChunkScript(index, chunk))
    }
    eval(buildLoadBookEndScript())
}

/**
 * Theme/font push for CHANGES after load (the initial values ride the
 * loadBook* protocol — a pre-load push would be a silent no-op, so the saved
 * theme/font would be replaced by the reader defaults).
 */
internal fun pushAppearanceScripts(theme: ReaderTheme, fontSizePx: Int, eval: (String) -> Unit) {
    eval(buildSetThemeScript(theme))
    eval(buildSetFontSizeScript(fontSizePx))
}
