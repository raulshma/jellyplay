package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationColor
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationStyle
import com.raulshma.jellyplay.feature.book.epub.EpubAppearance
import com.raulshma.jellyplay.feature.book.epub.buildAddAnnotationScript
import com.raulshma.jellyplay.feature.book.epub.buildApplyAnnotationsScript
import com.raulshma.jellyplay.feature.book.epub.buildClearSelectionScript
import com.raulshma.jellyplay.feature.book.epub.buildGoToCfiScript
import com.raulshma.jellyplay.feature.book.epub.buildLoadBookBeginScript
import com.raulshma.jellyplay.feature.book.epub.buildLoadBookEndScript
import com.raulshma.jellyplay.feature.book.epub.buildRemoveAnnotationScript
import com.raulshma.jellyplay.feature.book.epub.buildSearchScript
import com.raulshma.jellyplay.feature.book.epub.buildSetAutoScrollScript
import com.raulshma.jellyplay.feature.book.epub.buildSetFlowScript
import com.raulshma.jellyplay.feature.book.epub.buildSetFontFamilyScript
import com.raulshma.jellyplay.feature.book.epub.buildSetJustifyScript
import com.raulshma.jellyplay.feature.book.epub.buildSetLineHeightScript
import com.raulshma.jellyplay.feature.book.epub.buildSetMarginsScript
import com.raulshma.jellyplay.feature.book.epub.buildSpeechContextScript
import com.raulshma.jellyplay.feature.book.epub.pushAppearanceScripts
import com.raulshma.jellyplay.feature.book.epub.sendBookChunks
import com.raulshma.jellyplay.feature.book.epub.toJsonArg
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the reader.js command scripts byte-for-byte: the appearance bundle's
 * JSON (and its escaping), the annotation list serialization, and the new
 * Wave 2 command signatures both platform hosts evaluate.
 */
class EpubReaderScriptTest {

    @Test
    fun `appearance bundle serializes theme font and default flow`() {
        assertEquals(
            "{\"theme\":\"sepia\",\"fontSize\":18,\"flow\":\"paginated\"}",
            EpubAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 18).toJsonArg(),
        )
    }

    @Test
    fun `appearance bundle includes every optional field and escapes quotes`() {
        val appearance = EpubAppearance(
            theme = ReaderTheme.DARK,
            fontSizePx = 17,
            fontFamilyCss = "Georgia, \"Times New Roman\", serif",
            lineHeight = 1.6,
            marginsPx = 12,
            justify = true,
            scrolled = true,
        )
        assertEquals(
            "{\"theme\":\"dark\",\"fontSize\":17," +
                "\"fontFamily\":\"Georgia, \\\"Times New Roman\\\", serif\"," +
                "\"lineHeight\":1.6,\"margins\":12,\"justify\":true,\"flow\":\"scrolled\"}",
            appearance.toJsonArg(),
        )
    }

    @Test
    fun `appearance bundle omits blank font family`() {
        val appearance = EpubAppearance(theme = ReaderTheme.LIGHT, fontSizePx = 20, fontFamilyCss = "  ")
        assertEquals(
            "{\"theme\":\"light\",\"fontSize\":20,\"flow\":\"paginated\"}",
            appearance.toJsonArg(),
        )
    }

    @Test
    fun `loadBookBegin script carries the appearance as one JSON string arg`() {
        val script = buildLoadBookBeginScript(
            chunkCount = 2,
            resumePercent = 0.5,
            appearance = EpubAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 18),
        )
        assertEquals(
            "window.jellyPlayReader.loadBookBegin(2, 0.5, " +
                "\"{\\\"theme\\\":\\\"sepia\\\",\\\"fontSize\\\":18,\\\"flow\\\":\\\"paginated\\\"}\")",
            script,
        )
    }

    @Test
    fun `annotation list serializes to a JSON array with escaped cfis`() {
        val entries = listOf(
            EpubAnnotationSpec("epubcfi(/6/4!/4/10/2:0..28)", EpubAnnotationStyle.HIGHLIGHT, EpubAnnotationColor.YELLOW),
            EpubAnnotationSpec("we\"ird", EpubAnnotationStyle.UNDERLINE, EpubAnnotationColor.RED),
        )
        assertEquals(
            "window.jellyPlayReader.applyAnnotations([" +
                "{\"cfi\":\"epubcfi(/6/4!/4/10/2:0..28)\",\"style\":\"HIGHLIGHT\",\"color\":\"YELLOW\"}," +
                "{\"cfi\":\"we\\\"ird\",\"style\":\"UNDERLINE\",\"color\":\"RED\"}])",
            buildApplyAnnotationsScript(entries),
        )
        assertEquals(
            "window.jellyPlayReader.applyAnnotations([])",
            buildApplyAnnotationsScript(emptyList()),
        )
    }

    @Test
    fun `single annotation and removal scripts escape their cfi`() {
        assertEquals(
            "window.jellyPlayReader.addAnnotation(" +
                "{\"cfi\":\"epubcfi(/6/4)\",\"style\":\"HIGHLIGHT\",\"color\":\"GREEN\"})",
            buildAddAnnotationScript(
                EpubAnnotationSpec("epubcfi(/6/4)", EpubAnnotationStyle.HIGHLIGHT, EpubAnnotationColor.GREEN),
            ),
        )
        assertEquals(
            "window.jellyPlayReader.removeAnnotation(\"epubcfi(/6/4!/4/2:0..9)\")",
            buildRemoveAnnotationScript("epubcfi(/6/4!/4/2:0..9)"),
        )
        assertEquals(
            "window.jellyPlayReader.clearSelection()",
            buildClearSelectionScript(),
        )
    }

    @Test
    fun `search script escapes the query and passes the raw token`() {
        assertEquals(
            "window.jellyPlayReader.search(\"find \\\"me\\\"\", 42)",
            buildSearchScript("find \"me\"", 42),
        )
        assertEquals(
            "window.jellyPlayReader.search(\"\", 0)",
            buildSearchScript("", 0),
        )
    }

    @Test
    fun `speech context script takes a cfi or null for the current chapter`() {
        assertEquals(
            "window.jellyPlayReader.getSpeechContext(null)",
            buildSpeechContextScript(null),
        )
        assertEquals(
            "window.jellyPlayReader.getSpeechContext(\"epubcfi(/6/4!/4/2)\")",
            buildSpeechContextScript("epubcfi(/6/4!/4/2)"),
        )
    }

    @Test
    fun `appearance knob scripts use the readerjs command names`() {
        assertEquals(
            "window.jellyPlayReader.setFontFamily(\"Georgia, \\\"Times New Roman\\\", serif\")",
            buildSetFontFamilyScript("Georgia, \"Times New Roman\", serif"),
        )
        assertEquals(
            "window.jellyPlayReader.setLineHeight(1.6)",
            buildSetLineHeightScript(1.6),
        )
        assertEquals(
            "window.jellyPlayReader.setMargins(8)",
            buildSetMarginsScript(8),
        )
        assertEquals(
            "window.jellyPlayReader.setJustify(true)",
            buildSetJustifyScript(true),
        )
        assertEquals(
            "window.jellyPlayReader.setJustify(false)",
            buildSetJustifyScript(false),
        )
        assertEquals(
            "window.jellyPlayReader.setFlow('scrolled')",
            buildSetFlowScript(scrolled = true),
        )
        assertEquals(
            "window.jellyPlayReader.setFlow('paginated')",
            buildSetFlowScript(scrolled = false),
        )
        assertEquals(
            "window.jellyPlayReader.goToCfi(\"epubcfi(/6/4!/4/2:0..9)\")",
            buildGoToCfiScript("epubcfi(/6/4!/4/2:0..9)"),
        )
        assertEquals(
            "window.jellyPlayReader.setAutoScroll(true, 90)",
            buildSetAutoScrollScript(enabled = true, pxPerSec = 90),
        )
        assertEquals(
            "window.jellyPlayReader.setAutoScroll(false, 0)",
            buildSetAutoScrollScript(enabled = false, pxPerSec = 0),
        )
    }

    @Test
    fun `appearance push clears the font family for system`() {
        val scripts = mutableListOf<String>()
        pushAppearanceScripts(EpubAppearance(theme = ReaderTheme.LIGHT, fontSizePx = 20)) {
            scripts.add(it)
        }
        assertEquals(
            listOf(
                "window.jellyPlayReader.setTheme('light')",
                "window.jellyPlayReader.setFontSize(20)",
                "window.jellyPlayReader.setFontFamily(\"\")",
            ),
            scripts,
        )
    }

    @Test
    fun `appearance push covers every knob when set`() {
        val scripts = mutableListOf<String>()
        pushAppearanceScripts(
            EpubAppearance(
                theme = ReaderTheme.DARK,
                fontSizePx = 17,
                fontFamilyCss = "serif",
                lineHeight = 1.5,
                marginsPx = 10,
                justify = true,
            ),
        ) { scripts.add(it) }
        assertEquals(
            listOf(
                "window.jellyPlayReader.setTheme('dark')",
                "window.jellyPlayReader.setFontSize(17)",
                "window.jellyPlayReader.setFontFamily(\"serif\")",
                "window.jellyPlayReader.setLineHeight(1.5)",
                "window.jellyPlayReader.setMargins(10)",
                "window.jellyPlayReader.setJustify(true)",
            ),
            scripts,
        )
    }

    @Test
    fun `chunked book transfer rides the appearance on the begin frame`() {
        val scripts = mutableListOf<String>()
        sendBookChunks(
            base64 = "QUJD", // "ABC" — a single chunk
            resumePercent = 0.25,
            appearance = EpubAppearance(theme = ReaderTheme.DARK, fontSizePx = 17),
        ) { scripts.add(it) }

        assertEquals(
            listOf(
                "window.jellyPlayReader.loadBookBegin(1, 0.25, " +
                    "\"{\\\"theme\\\":\\\"dark\\\",\\\"fontSize\\\":17,\\\"flow\\\":\\\"paginated\\\"}\")",
                "window.jellyPlayReader.loadBookChunk(0, \"QUJD\")",
                buildLoadBookEndScript(),
            ),
            scripts,
        )
    }

    /**
     * Regresses the dead tap-zone wiring: epub.js 0.3.x emits the raw
     * forwarded content events (click/mousedown/touchstart/touchend) on the
     * per-chapter Contents emitter ONLY — they never bubble to the
     * rendition, so a `rendition.on('click', …)` listener silently never
     * fires and the reader's tap zones (chrome toggle + paging) stay dead.
     */
    @Test
    fun `reader js wires content input on the contents emitter`() {
        val source = java.io.File("src/commonMain/composeResources/files/epubjs/reader.js").readText()
        kotlin.test.assertTrue(source.contains("contents.on('click', onContentClick)"), "click must ride contents")
        kotlin.test.assertTrue(source.contains("contents.on('touchend', onContentsTouchEnd)"), "swipe must ride contents")
        kotlin.test.assertTrue(!source.contains("rendition.on('click'"), "rendition-level click never fires")
        // Touch taps are detected from the touch pair (touchstart/touchend),
        // not the synthesized click — the WebView may never produce a click
        // inside the content iframe, which left the chrome toggle dead.
        kotlin.test.assertTrue(source.contains("reportTap(endX"), "touch taps must come from the touch pair")
        // Late synthesized echo clicks (renderer falls behind during page
        // turns) must be timestamp-gated, or a stale echo double-fires a
        // different zone than the gesture's own tap.
        kotlin.test.assertTrue(source.contains("e.timeStamp - lastTouchAt"), "echo clicks must be time-gated")
        // The JS side reports RAW gesture facts only — tap x + viewport
        // width, swipe direction — never a zone. Native owns the mapping
        // (unit-tested there) so JS-side geometry drift can never turn a
        // center tap into a page turn again.
        kotlin.test.assertTrue(source.contains("width: window.innerWidth || 0"), "taps must carry the viewport width")
        kotlin.test.assertTrue(!source.contains("zoneFromX"), "zone judgment must not live in JS")
        kotlin.test.assertTrue(source.contains("'swipe', dir:"), "swipes must report physical direction")
    }
}
