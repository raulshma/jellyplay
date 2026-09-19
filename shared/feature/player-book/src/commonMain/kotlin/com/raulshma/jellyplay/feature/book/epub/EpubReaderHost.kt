package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.feature.book.ReaderPrefsSnapshot
import com.raulshma.jellyplay.feature.book.epubCssStack
import com.raulshma.jellyplay.feature.book.epubLineHeight
import com.raulshma.jellyplay.feature.book.epubMarginsPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) {
    companion object {
        /**
         * The ONE snapshot → bundle fold: theme/font size from the snapshot's
         * EFFECTIVE appearance (per-book override ?: global), every typography
         * axis from the global slice — through the ReaderAppearance.kt
         * mappings (family → CSS stack, leading pct → multiplier, margin pct →
         * the proportional px band).
         */
        fun from(snapshot: ReaderPrefsSnapshot): EpubAppearance {
            val effective = snapshot.effective
            val global = snapshot.global
            return EpubAppearance(
                theme = effective.theme,
                fontSizePx = effective.fontSizePx,
                fontFamilyCss = global.fontFamily.epubCssStack(),
                lineHeight = epubLineHeight(global.lineHeightPct),
                marginsPx = epubMarginsPx(global.marginPct),
                justify = global.justify,
                scrolled = global.scrollMode,
            )
        }
    }
}

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
     * Book-scope remaining epub.js location pages (`total - current` of the
     * whole-book location list), or `null` before locations exist — the
     * whole-book time-left estimate's source (see [EpubLocation]).
     */
    val remainingLocations: Int? = null,
    /**
     * The current page-start CFI (`epubcfi(…)`), or `null` before locations
     * exist. This is what bookmarks and exact resume persist — the plain
     * `percent` event carries no anchor at all.
     */
    val cfi: String? = null,
    /**
     * The raw current spine href, or `null` when not reported — lets native
     * identify the current TOC entry (the chapter label alone collides on
     * duplicate titles).
     */
    val href: String? = null,
)

/** One full-text search hit (chapter scan in reader.js). */
internal data class EpubSearchResult(val cfi: String, val excerpt: String, val chapter: String)

/** One speakable block of the chapter (block-level text with its own CFI). */
internal data class EpubSpeechParagraph(val cfi: String, val text: String)

/**
 * The JS→native event seam: ONE listener invoked once per parsed [EpubEvent]
 * (the hosts feed [EpubEventParser.parse] output straight through it — there
 * is no separate dispatch step). Callers capture Compose state / ViewModel
 * calls inside; the bridge threads (JavascriptInterface on Android, the CEF
 * poll on desktop) invoke the seam off the main thread, so the listener must
 * stay thread-safe (StateFlow/state writes are).
 */
internal fun interface EpubEventListener {
    fun onEvent(event: EpubEvent)
}

/**
 * The ONE event-side forwarding site — both platform hosts build their
 * delivery receipt (see [BookDeliveryTracker.confirmDelivery]) through this
 * decorator: a [EpubEvent.Status] latches the receipt FIRST (the ladder must
 * hold the generation before the screen reacts to the status) and only then
 * reaches the wrapped listener; every other event rides the wrap untouched.
 * With a single-event seam a new event kind CANNOT bypass the receipt — it
 * arrives as the same [onEvent] call by construction, which is why the old
 * thirteen-callback wrap needed a reflection guard and this one does not.
 */
internal fun EpubEventListener.withStatusReceipt(receipt: () -> Unit): EpubEventListener =
    EpubEventListener { event ->
        if (event is EpubEvent.Status) receipt()
        this@withStatusReceipt.onEvent(event)
    }

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

    /** Jump to an exact range/chapter CFI; failure surfaces via [EpubEvent.DisplayError]. */
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
 * (rendering it into the composition at [modifier]) and returns the command
 * handle; the book file is base64-inlined into the self-contained reader
 * page, so no file:// access is granted to the WebView. [modifier] positions
 * the view: overlay layouts pass the full-screen default, the desktop
 * in-flow layout (see `epubChromeOverlaysContent`) constrains it to the
 * content region between the chrome bars — the windowed CEF browser paints
 * above every Compose overlay, so the bars cannot float over it there.
 *
 * [overlayActive] reports "a sheet or dialog holds the screen" (the sheet
 * stack's `open` fold). Windowed CEF composites above the M3 sheet/dialog
 * windows too — a raised sheet showed the book through its own middle band —
 * so the desktop actual hides the browser's native surface while the flag is
 * set and restores it on dismiss (the sheet then renders over the blank
 * content region). Overlay platforms' lightweight views composite normally
 * and ignore the flag.
 */
@Composable
internal expect fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    onEvent: EpubEventListener,
    overlayActive: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize(),
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
 *
 * Chunking + per-chunk script concat transiently cost a few times the book
 * size in strings — they run on [Dispatchers.Default]; the [eval] loop stays
 * on the caller's (main) dispatcher, where evaluate(Java)Script must run.
 */
internal suspend fun sendBookChunks(
    base64: String,
    resumePercent: Double,
    appearance: EpubAppearance,
    eval: (String) -> Unit,
) {
    val scripts = withContext(Dispatchers.Default) {
        val chunks = base64.chunked(BOOK_CHUNK_CHARS)
        buildList(chunks.size + 2) {
            add(buildLoadBookBeginScript(chunks.size, resumePercent, appearance))
            chunks.forEachIndexed { index, chunk -> add(buildLoadBookChunkScript(index, chunk)) }
            add(buildLoadBookEndScript())
        }
    }
    scripts.forEach(eval)
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
