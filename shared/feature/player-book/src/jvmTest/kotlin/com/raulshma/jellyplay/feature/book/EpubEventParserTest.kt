package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubEventParser
import com.raulshma.jellyplay.feature.book.epub.EpubReaderCallbacks
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.epub.dispatchEpubEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the typed decode of the JS→native event channel: percent clamping,
 * status mapping, direction normalization, TOC flattening, the
 * array/quoted-string bridge payloads, and malformed-input tolerance.
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
    fun `dispatcher routes events into callbacks`() {
        val percents = mutableListOf<Double>()
        val statuses = mutableListOf<EpubReaderStatus>()
        val directions = mutableListOf<ReadingDirection>()
        val tocs = mutableListOf<List<EpubTocItem>>()

        dispatchEpubEvents(
            """[
               |{"type":"percent","value":0.9},
               |{"type":"status","value":"ready"},
               |{"type":"direction","value":"rtl"},
               |{"type":"toc","value":[{"label":"A","href":"a.xhtml"}]}
               |]""".trimMargin(),
            EpubReaderCallbacks(
                onPercentChanged = { percents.add(it) },
                onStatusChanged = { statuses.add(it) },
                onDirectionReported = { directions.add(it) },
                onTocReady = { tocs.add(it) },
            ),
        )

        assertEquals(listOf(0.9), percents)
        assertEquals(listOf(EpubReaderStatus.READY), statuses)
        assertEquals(listOf(ReadingDirection.RTL), directions)
        assertEquals(listOf(listOf(EpubTocItem("A", "a.xhtml"))), tocs)
    }

    @Test
    fun `dispatcher tolerates null and malformed payloads`() {
        val percents = mutableListOf<Double>()
        dispatchEpubEvents(
            null,
            EpubReaderCallbacks(onPercentChanged = { percents.add(it) }),
        )
        dispatchEpubEvents(
            "garbage",
            EpubReaderCallbacks(onPercentChanged = { percents.add(it) }),
        )
        assertTrue(percents.isEmpty())
    }
}
