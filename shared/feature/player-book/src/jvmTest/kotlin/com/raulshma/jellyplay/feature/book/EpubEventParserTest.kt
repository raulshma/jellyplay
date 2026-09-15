package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubEventParser
import com.raulshma.jellyplay.feature.book.epub.EpubEventListener
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubRelocation
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.epub.withStatusReceipt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the typed decode of the JS→native event channel: percent clamping,
 * status mapping, direction normalization, TOC flattening, the newer Wave 2
 * events (relocated/tap/selected/selection/search/speech/autoScroll), the
 * array/quoted-string bridge payloads, malformed-input tolerance, and the
 * delivery-receipt decorator both platform hosts wrap the single-event seam
 * with (the funnel that replaced the thirteen-callback dispatch).
 */
class EpubEventParserTest {

    @Test
    fun `percent event decodes and clamps`() {
        val events = EpubEventParser.parse("""{"type":"percent","value":0.42}""")
        assertEquals(listOf(EpubEvent.Percent(0.42)), events)

        val clamped = EpubEventParser.parse("""{"type":"percent","value":3.5}""")
        assertEquals(listOf(EpubEvent.Percent(1.0)), clamped)
    }

    @Test
    fun `status event decodes to the typed status`() {
        assertEquals(
            listOf(EpubEvent.Status(EpubReaderStatus.LOCATIONS_READY)),
            EpubEventParser.parse("""{"type":"status","value":"locationsReady"}"""),
        )
        // `locations` (epub.js started generating) still folds into LOADING.
        assertEquals(
            listOf(EpubEvent.Status(EpubReaderStatus.LOADING)),
            EpubEventParser.parse("""{"type":"status","value":"locations"}"""),
        )
    }

    @Test
    fun `direction event decodes to the typed direction`() {
        assertEquals(
            listOf(EpubEvent.Direction(ReadingDirection.RTL)),
            EpubEventParser.parse("""{"type":"direction","value":"RTL"}"""),
        )
        assertEquals(
            listOf(EpubEvent.Direction(ReadingDirection.LTR)),
            EpubEventParser.parse("""{"type":"direction","value":"ltr"}"""),
        )
    }

    @Test
    fun `toc event decodes flattened items and skips broken rows`() {
        val events = EpubEventParser.parse(
            """{"type":"toc","value":[
               |{"label":"Chapter 1","href":"ch1.xhtml"},
               |{"label":"","href":""},
               |{"href":"ch2.xhtml"},
               |"junk",
               |{"label":"Chapter 2","href":"ch2.xhtml"}
               |]}""".trimMargin(),
        )
        assertEquals(
            listOf(
                EpubEvent.Toc(
                    listOf(
                        EpubTocItem("Chapter 1", "ch1.xhtml"),
                        EpubTocItem("", "ch2.xhtml"),
                        EpubTocItem("Chapter 2", "ch2.xhtml"),
                    ),
                ),
            ),
            events,
        )
    }

    @Test
    fun `relocated event decodes with all fields`() {
        assertEquals(
            listOf(
                EpubEvent.Relocated(
                    EpubRelocation(
                        percent = 0.37,
                        chapterLabel = "Chapter 4",
                        remainingPages = 12,
                        cfi = "epubcfi(/6/14!/4/2/1:0)",
                    ),
                ),
            ),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.37,"chapterLabel":"Chapter 4",""" +
                    """"remainingPages":12,"cfi":"epubcfi(/6/14!/4/2/1:0)"}""",
            ),
        )
    }

    @Test
    fun `relocated book scope remaining locations decode and default to null`() {
        assertEquals(
            listOf(
                EpubEvent.Relocated(
                    EpubRelocation(
                        percent = 0.37,
                        chapterLabel = "Chapter 4",
                        remainingPages = 12,
                        remainingLocations = 340,
                        cfi = "epubcfi(/6/14!/4/2/1:0)",
                    ),
                ),
            ),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.37,"chapterLabel":"Chapter 4",""" +
                    """"remainingPages":12,"remainingLocations":340,"cfi":"epubcfi(/6/14!/4/2/1:0)"}""",
            ),
        )
        // Absent field (pre-locations, or an older reader.js) stays null.
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", 3, remainingLocations = null))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One","remainingPages":3}""",
            ),
        )
    }

    @Test
    fun `relocated event tolerates missing optional fields`() {
        // Pre-locations boot relocation: percent may be null; label/pages absent.
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(null, "", null))),
            EpubEventParser.parse("""{"type":"relocated"}"""),
        )
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", null))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One"}""",
            ),
        )
    }

    @Test
    fun `relocated cfi is null when absent or blank`() {
        // An older reader.js without the cfi field decodes with a null anchor.
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", 3, cfi = null))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One","remainingPages":3}""",
            ),
        )
        // A blank anchor folds to null — bookmarks must not persist "".
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", 3, cfi = null))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One","remainingPages":3,"cfi":""}""",
            ),
        )
    }

    @Test
    fun `relocated href decodes and defaults to null when absent or blank`() {
        // The spine href the TOC tick rail matches the current entry with.
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", 3, href = "text/ch1.xhtml"))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One","remainingPages":3,""" +
                    """"href":"text/ch1.xhtml"}""",
            ),
        )
        // An older reader.js (or a relocation without a start href) stays null.
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", 3, href = null))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One","remainingPages":3}""",
            ),
        )
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(0.5, "One", 3, href = null))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":0.5,"chapterLabel":"One","remainingPages":3,"href":""}""",
            ),
        )
    }

    @Test
    fun `relocated event clamps percent and drops non-numeric extras`() {
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(1.0, "Two", 3))),
            EpubEventParser.parse(
                """{"type":"relocated","percent":2.7,"chapterLabel":"Two","remainingPages":3}""",
            ),
        )
    }

    @Test
    fun `tap event resolves the zone from raw x and width`() {
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.LEFT)),
            EpubEventParser.parse("""{"type":"tap","x":40,"width":400}"""),
        )
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.CENTER)),
            EpubEventParser.parse("""{"type":"tap","x":200,"width":400}"""),
        )
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.RIGHT)),
            EpubEventParser.parse("""{"type":"tap","x":360,"width":400}"""),
        )
    }

    @Test
    fun `tap event degrades to center — never a page turn — on broken geometry`() {
        // The WebView geometry report failing (width 0/absent, x outside the
        // viewport) must yield the harmless chrome toggle, not a page turn.
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.CENTER)),
            EpubEventParser.parse("""{"type":"tap","x":40,"width":0}"""),
        )
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.CENTER)),
            EpubEventParser.parse("""{"type":"tap","x":40}"""),
        )
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.CENTER)),
            EpubEventParser.parse("""{"type":"tap"}"""),
        )
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.CENTER)),
            EpubEventParser.parse("""{"type":"tap","x":900,"width":400}"""),
        )
        assertEquals(
            listOf(EpubEvent.Tap(EpubTapZone.CENTER)),
            EpubEventParser.parse("""{"type":"tap","x":-5,"width":400}"""),
        )
    }

    @Test
    fun `swipe event decodes physical direction and drops unknown directions`() {
        assertEquals(
            listOf(EpubEvent.Swipe(toLeft = true)),
            EpubEventParser.parse("""{"type":"swipe","dir":"left"}"""),
        )
        assertEquals(
            listOf(EpubEvent.Swipe(toLeft = false)),
            EpubEventParser.parse("""{"type":"swipe","dir":"right"}"""),
        )
        assertTrue(EpubEventParser.parse("""{"type":"swipe","dir":"up"}""").isEmpty())
        assertTrue(EpubEventParser.parse("""{"type":"swipe"}""").isEmpty())
    }

    @Test
    fun `selected event decodes and requires a cfi`() {
        assertEquals(
            listOf(EpubEvent.Selected("epubcfi(/6/4!/4/10/2:0..28)", "the chosen words")),
            EpubEventParser.parse(
                """{"type":"selected","cfi":"epubcfi(/6/4!/4/10/2:0..28)","text":"the chosen words"}""",
            ),
        )
        // Missing text defaults to empty; missing/blank cfi drops the event.
        assertEquals(
            listOf(EpubEvent.Selected("epubcfi(/6/4)", "")),
            EpubEventParser.parse("""{"type":"selected","cfi":"epubcfi(/6/4)"}"""),
        )
        assertTrue(EpubEventParser.parse("""{"type":"selected","text":"orphan"}""").isEmpty())
        assertTrue(EpubEventParser.parse("""{"type":"selected","cfi":""}""").isEmpty())
    }

    @Test
    fun `selectionCleared event decodes`() {
        assertEquals(
            listOf(EpubEvent.SelectionCleared),
            EpubEventParser.parse("""{"type":"selectionCleared"}"""),
        )
    }

    @Test
    fun `searchResults event decodes rows and skips broken ones`() {
        assertEquals(
            listOf(
                EpubEvent.SearchResults(
                    token = 7,
                    results = listOf(
                        EpubSearchResult("epubcfi(/6/4!/4/2)", "…hello world…", "Chapter 1"),
                        EpubSearchResult("epubcfi(/6/6!/4/8)", "say hello", ""),
                    ),
                ),
            ),
            EpubEventParser.parse(
                """{"type":"searchResults","token":7,"results":[
                   |{"cfi":"epubcfi(/6/4!/4/2)","excerpt":"…hello world…","chapter":"Chapter 1"},
                   |{"excerpt":"no cfi","chapter":"Chapter 1"},
                   |"junk",
                   |{"cfi":"epubcfi(/6/6!/4/8)","excerpt":"say hello"}
                   |]}""".trimMargin(),
            ),
        )
    }

    @Test
    fun `searchResults event without a token is dropped`() {
        assertTrue(
            EpubEventParser.parse("""{"type":"searchResults","results":[]}""").isEmpty(),
        )
        // An empty result list is still a valid answer — empty results, one event.
        assertEquals(
            listOf(EpubEvent.SearchResults(3, emptyList())),
            EpubEventParser.parse("""{"type":"searchResults","token":3,"results":[]}"""),
        )
    }

    @Test
    fun `speechContext event decodes paragraphs and skips broken ones`() {
        assertEquals(
            listOf(
                EpubEvent.SpeechContext(
                    listOf(
                        EpubSpeechParagraph("epubcfi(/6/4!/4/2)", "First paragraph."),
                        // Missing text folds to ""; missing cfi drops the row.
                        EpubSpeechParagraph("epubcfi(/6/4!/4/4)", ""),
                    ),
                ),
            ),
            EpubEventParser.parse(
                """{"type":"speechContext","paragraphs":[
                   |{"cfi":"epubcfi(/6/4!/4/2)","text":"First paragraph."},
                   |{"text":"no anchor"},
                   |{"cfi":"epubcfi(/6/4!/4/4)"}
                   |]}""".trimMargin(),
            ),
        )
    }

    @Test
    fun `autoScrollStopped and displayError events decode`() {
        assertEquals(
            listOf(EpubEvent.AutoScrollStopped),
            EpubEventParser.parse("""{"type":"autoScrollStopped"}"""),
        )
        assertEquals(
            listOf(EpubEvent.DisplayError("epubcfi(/6/4!/4/10)")),
            EpubEventParser.parse("""{"type":"displayError","cfi":"epubcfi(/6/4!/4/10)"}"""),
        )
        assertEquals(
            listOf(EpubEvent.DisplayError("")),
            EpubEventParser.parse("""{"type":"displayError"}"""),
        )
    }

    @Test
    fun `array payload decodes every event`() {
        val events = EpubEventParser.parse(
            """[
               |{"type":"percent","value":0.1},
               |{"type":"status","value":"ready"},
               |{"type":"unknown","value":"dropped"}
               |]""".trimMargin(),
        )
        assertEquals(
            listOf(
                EpubEvent.Percent(0.1),
                EpubEvent.Status(EpubReaderStatus.READY),
            ),
            events,
        )
    }

    @Test
    fun `quoted string payload (desktop evaluateJavascript form) unwraps`() {
        val payload = "\"[{\\\"type\\\":\\\"percent\\\",\\\"value\\\":0.25}]\""
        assertEquals(
            listOf(EpubEvent.Percent(0.25)),
            EpubEventParser.parse(payload),
        )
    }

    @Test
    fun `malformed input yields empty`() {
        assertTrue(EpubEventParser.parse(null).isEmpty())
        assertTrue(EpubEventParser.parse("").isEmpty())
        assertTrue(EpubEventParser.parse("   ").isEmpty())
        assertTrue(EpubEventParser.parse("not json {").isEmpty())
        assertTrue(EpubEventParser.parse("""{"type":"percent","value":}""").isEmpty())
        assertTrue(EpubEventParser.parse("""[{"type":"percent" """).isEmpty())
        assertTrue(EpubEventParser.parse("null").isEmpty())
        assertTrue(EpubEventParser.parse("""{"type":"percent","value":"0.5"}""").isEmpty())
    }

    @Test
    fun `malformed new-event shapes yield empty or partial decode`() {
        // Broken containers stay empty…
        assertTrue(EpubEventParser.parse("""{"type":"tap","zone":""""").isEmpty())
        assertTrue(EpubEventParser.parse("""{"type":"searchResults","token":"x"}""").isEmpty())
        // …while wrong-typed optional fields fold to defaults instead of failing.
        assertEquals(
            listOf(EpubEvent.Relocated(EpubRelocation(null, "L", null))),
            EpubEventParser.parse("""{"type":"relocated","percent":"half","chapterLabel":"L"}"""),
        )
    }

    @Test
    fun `status receipt decorator latches before the listener and passes other events through`() {
        val seen = mutableListOf<String>()
        val listener = EpubEventListener { event ->
            seen += when (event) {
                is EpubEvent.Status -> "event:${event.status}"
                is EpubEvent.Percent -> "event:${event.value}"
                else -> "event:other"
            }
        }
        val receipted = listener.withStatusReceipt { seen += "receipt" }

        receipted.onEvent(EpubEvent.Percent(0.25))
        receipted.onEvent(EpubEvent.Status(EpubReaderStatus.READY))

        assertEquals(
            listOf("event:0.25", "receipt", "event:READY"),
            seen,
            "only Status latches the receipt — and it latches BEFORE the listener " +
                "(the delivery ladder must hold the generation before the screen reacts)",
        )
    }
}
