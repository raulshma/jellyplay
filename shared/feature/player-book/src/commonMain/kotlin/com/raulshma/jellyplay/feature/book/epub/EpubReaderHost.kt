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
 * The reader's whole appearance bundle. Rides the `loadBookBegin` frame as one
 * JSON string and backs the post-load push; `null` optionals mean "leave the
 * reader.js default" so partial bundles stay back-compatible.
 */
internal data class EpubAppearance(
    val theme: ReaderTheme,
    val fontSizePx: Int,
    val fontFamilyCss: String? = null,
    val lineHeight: Double? = null,
    val marginsPx: Int? = null,
    val justify: Boolean? = null,
    val scrolled: Boolean = false,
)

/** Content tap zone as reported by reader.js (`x < width/3` etc.). */
internal enum class EpubTapZone { LEFT, RIGHT, CENTER }

/** Annotation paint style (epub.js highlight vs underline mark). */
internal enum class EpubAnnotationStyle { HIGHLIGHT, UNDERLINE }

/** Annotation palette slot — reader.js maps each to a concrete CSS color. */
internal enum class EpubAnnotationColor { YELLOW, GREEN, BLUE, RED }

/** One annotation to paint: a range CFI plus its style/color. */
internal data class EpubAnnotationSpec(
    val cfi: String,
    val style: EpubAnnotationStyle,
    val color: EpubAnnotationColor,
)

/** The richer relocation payload (`relocated` event), complementing `percent`. */
internal data class EpubRelocation(
    /** 0.0..1.0, or `null` before locations are generated. */
    val percent: Double?,
    val chapterLabel: String,
    /** `total - page` of the current chapter, or `null` when not reported. */
    val remainingPages: Int?,
    /**
     * The current page-start CFI (`epubcfi(…)`), or `null` before locations
     * exist. This is what bookmarks and exact resume persist — the plain
     * `percent` event carries no anchor at all.
     */
    val cfi: String? = null,
)

/** One full-text search hit (chapter scan in reader.js). */
internal data class EpubSearchResult(val cfi: String, val excerpt: String, val chapter: String)

/** One speakable block of the chapter (block-level text with its own CFI). */
internal data class EpubSpeechParagraph(val cfi: String, val text: String)

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
    /** Fires on every relocation with the chapter label / remaining pages. */
    val onRelocated: (EpubRelocation) -> Unit = {},
    val onTap: (EpubTapZone) -> Unit = {},
    val onSelection: (cfi: String, text: String) -> Unit = { _, _ -> },
    val onSelectionCleared: () -> Unit = {},
    /** One final event per `search` call — a newer token supersedes older ones. */
    val onSearchResults: (token: Int, results: List<EpubSearchResult>) -> Unit = { _, _ -> },
    val onSpeechContext: (paragraphs: List<EpubSpeechParagraph>) -> Unit = {},
    val onAutoScrollStopped: () -> Unit = {},
    /** A `display(cfi)` failed (goToCfi / flow switch) — native may fall back to percent. */
    val onDisplayError: (cfi: String) -> Unit = {},
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

    /** Jump to an exact range/chapter CFI; failure surfaces via [EpubReaderCallbacks.onDisplayError]. */
    fun goToCfi(cfi: String)

    /** Rebuild the rendition as paginated/scrolled, keeping the current position. */
    fun setFlow(scrolled: Boolean)

    /** Paint a full annotation set (replace semantics: clears whatever is painted). */
    fun applyAnnotations(entries: List<EpubAnnotationSpec>)

    /**
     * Paint ONE annotation. epub.js replaces an existing mark on the same CFI,
     * so this is the incremental path — unlike [applyAnnotations] it never
     * wipes unrelated marks (the search-result flash highlight relies on that).
     */
    fun addAnnotation(entry: EpubAnnotationSpec)

    fun removeAnnotation(cfi: String)

    /** Drop the live DOM selection (dismisses the native selection action row). */
    fun clearSelection()

    /**
     * Full-text search; [token] rides the result event — only the newest
     * token's scan posts `searchResults`, older ones are invalidated in JS.
     */
    fun search(query: String, token: Int)

    /** Ask for the chapter's speakable paragraphs ([cfi] of null = current chapter). */
    fun requestSpeechContext(cfi: String? = null)

    /** rAF auto-scroll for scrolled flow; stops post `autoScrollStopped`. */
    fun setAutoScroll(enabled: Boolean, pxPerSec: Int)
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
    appearance: EpubAppearance,
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

/**
 * The appearance as the single JSON string `loadBookBegin`/`loadBook` takes
 * (reader.js JSON-parses it; absent fields keep its defaults).
 */
internal fun EpubAppearance.toJsonArg(): String = buildString {
    append("{\"theme\":").append(theme.toJsName().toJsonStringLiteral())
    append(",\"fontSize\":").append(fontSizePx)
    fontFamilyCss?.takeIf { it.isNotBlank() }?.let {
        append(",\"fontFamily\":").append(it.toJsonStringLiteral())
    }
    lineHeight?.takeIf { it > 0.0 }?.let { append(",\"lineHeight\":").append(it) }
    marginsPx?.takeIf { it >= 0 }?.let { append(",\"margins\":").append(it) }
    justify?.let { append(",\"justify\":").append(it) }
    append(",\"flow\":\"").append(if (scrolled) "scrolled" else "paginated").append("\"")
    append("}")
}

internal fun buildLoadBookBeginScript(
    chunkCount: Int,
    resumePercent: Double,
    appearance: EpubAppearance,
): String = "window.jellyPlayReader.loadBookBegin(" +
    "$chunkCount, ${resumePercent.coerceIn(0.0, 1.0)}, ${appearance.toJsonArg().toJsonStringLiteral()})"

internal fun buildLoadBookChunkScript(index: Int, chunk: String): String =
    "window.jellyPlayReader.loadBookChunk($index, \"$chunk\")"

internal fun buildLoadBookEndScript(): String = "window.jellyPlayReader.loadBookEnd()"

internal fun buildSetThemeScript(theme: ReaderTheme): String =
    "window.jellyPlayReader.setTheme('${theme.toJsName()}')"

internal fun buildSetFontSizeScript(fontSizePx: Int): String =
    "window.jellyPlayReader.setFontSize($fontSizePx)"

internal fun buildSetFontFamilyScript(cssStack: String): String =
    "window.jellyPlayReader.setFontFamily(${cssStack.toJsonStringLiteral()})"

internal fun buildSetLineHeightScript(lineHeight: Double): String =
    "window.jellyPlayReader.setLineHeight($lineHeight)"

internal fun buildSetMarginsScript(px: Int): String =
    "window.jellyPlayReader.setMargins($px)"

internal fun buildSetJustifyScript(enabled: Boolean): String =
    "window.jellyPlayReader.setJustify($enabled)"

internal fun buildSetFlowScript(scrolled: Boolean): String =
    "window.jellyPlayReader.setFlow('${if (scrolled) "scrolled" else "paginated"}')"

internal fun buildNextScript(): String = "window.jellyPlayReader.next()"

internal fun buildPrevScript(): String = "window.jellyPlayReader.prev()"

internal fun buildGoToScript(href: String): String =
    "window.jellyPlayReader.goTo(${href.toJsonStringLiteral()})"

internal fun buildGoToCfiScript(cfi: String): String =
    "window.jellyPlayReader.goToCfi(${cfi.toJsonStringLiteral()})"

/** One [EpubAnnotationSpec] as the JS object `addAnnotation` takes. */
internal fun EpubAnnotationSpec.toJsonArg(): String =
    "{\"cfi\":${cfi.toJsonStringLiteral()},\"style\":\"${style.name}\",\"color\":\"${color.name}\"}"

internal fun buildApplyAnnotationsScript(entries: List<EpubAnnotationSpec>): String =
    "window.jellyPlayReader.applyAnnotations(${entries.joinToString(",", "[", "]") { it.toJsonArg() }})"

internal fun buildAddAnnotationScript(entry: EpubAnnotationSpec): String =
    "window.jellyPlayReader.addAnnotation(${entry.toJsonArg()})"

internal fun buildRemoveAnnotationScript(cfi: String): String =
    "window.jellyPlayReader.removeAnnotation(${cfi.toJsonStringLiteral()})"

internal fun buildClearSelectionScript(): String = "window.jellyPlayReader.clearSelection()"

internal fun buildSearchScript(query: String, token: Int): String =
    "window.jellyPlayReader.search(${query.toJsonStringLiteral()}, $token)"

internal fun buildSpeechContextScript(cfi: String?): String =
    if (cfi == null) {
        "window.jellyPlayReader.getSpeechContext(null)"
    } else {
        "window.jellyPlayReader.getSpeechContext(${cfi.toJsonStringLiteral()})"
    }

internal fun buildSetAutoScrollScript(enabled: Boolean, pxPerSec: Int): String =
    "window.jellyPlayReader.setAutoScroll($enabled, $pxPerSec)"

internal fun buildConsumeEventsScript(): String = "window.jellyPlayReader.consumeEvents()"

/**
 * The chunked book transfer — reader.js assembles the chunks and decodes once
 * on loadBookEnd. The saved appearance rides the begin frame so the rendition
 * is built with the user's preferences. [eval] is the platform's
 * evaluate(Java)Script seam, so the protocol lives once for both hosts.
 */
internal fun sendBookChunks(
    base64: String,
    resumePercent: Double,
    appearance: EpubAppearance,
    eval: (String) -> Unit,
) {
    val chunks = base64.chunked(BOOK_CHUNK_CHARS)
    eval(buildLoadBookBeginScript(chunks.size, resumePercent, appearance))
    chunks.forEachIndexed { index, chunk ->
        eval(buildLoadBookChunkScript(index, chunk))
    }
    eval(buildLoadBookEndScript())
}

/**
 * Appearance push for CHANGES after load (the initial values ride the
 * loadBook* protocol — a pre-load push would be a silent no-op, so the saved
 * values would be replaced by the reader defaults). The font family is
 * ALWAYS pushed: `null` (SYSTEM) pushes the empty stack, which reader.js
 * maps to removeOverride — switching back to the system font must clear a
 * previously applied stack. The remaining null optionals are not pushed:
 * they mean "leave the reader.js default in place" (and their `false`/`0`
 * script equivalents behave identically to the default anyway).
 */
internal fun pushAppearanceScripts(appearance: EpubAppearance, eval: (String) -> Unit) {
    eval(buildSetThemeScript(appearance.theme))
    eval(buildSetFontSizeScript(appearance.fontSizePx))
    eval(buildSetFontFamilyScript(appearance.fontFamilyCss.orEmpty()))
    appearance.lineHeight?.let { eval(buildSetLineHeightScript(it)) }
    appearance.marginsPx?.let { eval(buildSetMarginsScript(it)) }
    appearance.justify?.let { eval(buildSetJustifyScript(it)) }
}
