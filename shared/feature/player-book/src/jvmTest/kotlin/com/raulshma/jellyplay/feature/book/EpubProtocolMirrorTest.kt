package com.raulshma.jellyplay.feature.book

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The JS↔Kotlin protocol MIRROR pin: reader.js is the EPUB engine and
 * [com.raulshma.jellyplay.feature.book.epub.EpubReaderHost] its only
 * protocol, but the two sides live in different languages — this test
 * source-scans both and fails the build the moment they drift apart:
 *  - the location-generation size must equal [EPUB_LOCATION_PAGE_CHARS]
 *    (the Kotlin "≈ N min left" math divides by the same unit epub.js
 *    built its location list with);
 *  - every event type reader.js posts must be a type
 *    [com.raulshma.jellyplay.feature.book.epub.EpubEventParser] accepts
 *    (an unaccepted type is a silently dropped event — the reader loses a
 *    capability with no error anywhere);
 *  - every `window.jellyPlayReader` handler must have a command builder
 *    (and vice versa — a handler without a builder is dead JS, a builder
 *    without a handler is a dead button);
 *  - the annotation swatch palette the Compose chips preview must be the
 *    exact tint reader.js paints (ReaderSelection's `swatch()`).
 *
 * Source-scanning (not reflection) on purpose: the wire vocabulary is what
 * ships inside the built page, and the scan reads the same files the build
 * inlines. Regexes are whitespace-tolerant but the assertions are strict
 * set comparisons, so drift in either direction fails.
 */
class EpubProtocolMirrorTest {

    private val readerJs = File("src/commonMain/composeResources/files/epubjs/reader.js").readText()
    private val eventParserKt = File("src/commonMain/kotlin/com/raulshma/jellyplay/feature/book/epub/EpubEventParser.kt").readText()
    private val readerHostKt = File("src/commonMain/kotlin/com/raulshma/jellyplay/feature/book/epub/EpubReaderHost.kt").readText()
    private val selectionKt = File("src/commonMain/kotlin/com/raulshma/jellyplay/feature/book/ReaderSelection.kt").readText()

    /** Collapse all whitespace runs — tolerant to line wrapping/indent churn. */
    private fun String.whitespaceNormalized(): String = replace(Regex("\\s+"), " ")

    @Test
    fun `location generation size is the Kotlin location page chars constant`() {
        val sizes = Regex("locations\\.generate\\(\\s*(\\d+)\\s*\\)")
            .findAll(readerJs)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            listOf(EPUB_LOCATION_PAGE_CHARS.toString()),
            sizes,
            "reader.js must generate locations with exactly EPUB_LOCATION_PAGE_CHARS-char units " +
                "(the Kotlin time-left math divides by the unit epub.js built the list with)",
        )
    }

    @Test
    fun `every posted event type is one the Kotlin parser accepts`() {
        val posted = Regex("type\\s*:\\s*'([a-zA-Z]+)'")
            .findAll(readerJs.whitespaceNormalized())
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("status" in posted, "the post() scan must find events — a broken scan must not pass vacuously")

        val dispatchStart = eventParserKt.indexOf("when (map[\"type\"])")
        assertTrue(dispatchStart >= 0, "EpubEventParser must keep its event dispatch keyed on map[\"type\"]")
        val dispatch = eventParserKt.substring(dispatchStart, eventParserKt.indexOf("else -> null", dispatchStart))
        val accepted = Regex("\"(\\w+)\"\\s*->")
            .findAll(dispatch)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("percent" in accepted, "the parser-key scan must find keys — a broken scan must not pass vacuously")

        val drift = posted - accepted
        assertTrue(
            drift.isEmpty(),
            "reader.js posts event types EpubEventParser drops silently: $drift " +
                "(add a parser branch + sealed EpubEvent variant, or stop posting the type)",
        )
    }

    @Test
    fun `every jellyPlayReader handler has a command builder and vice versa`() {
        val objectStart = readerJs.indexOf("window.jellyPlayReader = {")
        assertTrue(objectStart >= 0, "reader.js must define the window.jellyPlayReader command surface")
        val handlerBlock = readerJs.substring(objectStart, readerJs.indexOf("\n    };", objectStart))
        val handlers = Regex("(\\w+)\\s*:\\s*function")
            .findAll(handlerBlock)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("loadBookBegin" in handlers, "the handler scan must find handlers — a broken scan must not pass vacuously")

        val built = Regex("jellyPlayReader\\.(\\w+)\\s*\\(")
            .findAll(readerHostKt)
            .map { it.groupValues[1] }
            .toSet()
        // loadBook is reader.js's single-shot entry for small books/tests —
        // the hosts deliberately ride only the chunked loadBookBegin/Chunk/End
        // ladder (a whole-book evaluateJavascript fails on large EPUBs), so
        // it is the one handler with no Kotlin builder.
        val unhosted = setOf("loadBook")
        assertEquals(
            handlers - unhosted,
            built,
            "reader.js handlers and EpubReaderHost builders must name the same commands: " +
                "handlers without builders are dead JS, builders without handlers are dead buttons",
        )
    }

    @Test
    fun `compose swatch palette is the exact tint reader js paints`() {
        val paletteStart = readerJs.indexOf("ANNOTATION_COLORS")
        assertTrue(paletteStart >= 0, "reader.js must define its ANNOTATION_COLORS palette")
        val paletteBlock = readerJs.substring(paletteStart, readerJs.indexOf("}", paletteStart))
        val jsPalette = Regex("(\\w+)\\s*:\\s*'#([0-9a-fA-F]{6})'")
            .findAll(paletteBlock)
            .associate { it.groupValues[1] to it.groupValues[2].lowercase() }
        assertTrue("YELLOW" in jsPalette, "the palette scan must find swatches — a broken scan must not pass vacuously")

        // The Kotlin side is no list — the swatches live where they are used
        // (ReaderSelection's when-mapping), so the mirror pins those arms.
        val composeSwatches = Regex("ReaderAnnotationColor\\.(\\w+)\\s*->\\s*Color\\(0x([0-9A-Fa-f]{8})\\)")
            .findAll(selectionKt)
            .associate { it.groupValues[1] to it.groupValues[2].lowercase() }
        assertEquals(jsPalette.keys, composeSwatches.keys, "the palette must have the same slots on both sides")

        jsPalette.forEach { (name, tint) ->
            val argb = composeSwatches.getValue(name)
            assertEquals("ff", argb.substring(0, 2), "the $name swatch must be opaque (alpha ff)")
            assertEquals(
                tint,
                argb.substring(2),
                "the $name Compose swatch must preview the exact tint reader.js paints ($tint)",
            )
        }
    }
}
