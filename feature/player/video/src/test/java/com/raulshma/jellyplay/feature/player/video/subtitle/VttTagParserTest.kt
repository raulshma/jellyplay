package com.raulshma.jellyplay.feature.player.video.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VttTagParserTest {

    @Test
    fun stripTags_plainText_returnsUnchanged() {
        assertEquals("Hello world", VttTagParser.stripTags("Hello world"))
    }

    @Test
    fun stripTags_boldStripped() {
        assertEquals("Hello world", VttTagParser.stripTags("<b>Hello world</b>"))
    }

    @Test
    fun stripTags_italicStripped() {
        assertEquals("Hello", VttTagParser.stripTags("<i>Hello</i>"))
    }

    @Test
    fun stripTags_underlineStripped() {
        assertEquals("Hello", VttTagParser.stripTags("<u>Hello</u>"))
    }

    @Test
    fun stripTags_nestedTagsStripped() {
        assertEquals("Hello world", VttTagParser.stripTags("<b><i>Hello</i> world</b>"))
    }

    @Test
    fun stripTags_langTagStripped() {
        assertEquals("Bonjour", VttTagParser.stripTags("<lang fr>Bonjour</lang>"))
    }

    @Test
    fun stripTags_mixedTagsStripped() {
        assertEquals("abc def ghi", VttTagParser.stripTags("<b>abc</b> <i>def</i> <u>ghi</u>"))
    }

    @Test
    fun parseAnnotated_plainText_noStyles() {
        val result = VttTagParser.parseAnnotated("Hello world")
        assertEquals("Hello world", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun parseAnnotated_boldText_hasBoldSpan() {
        val result = VttTagParser.parseAnnotated("<b>Hello</b>")
        assertEquals("Hello", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun parseAnnotated_italicText_hasItalicSpan() {
        val result = VttTagParser.parseAnnotated("<i>Hello</i>")
        assertEquals("Hello", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun parseAnnotated_underlineText_hasUnderlineSpan() {
        val result = VttTagParser.parseAnnotated("<u>Hello</u>")
        assertEquals("Hello", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun parseAnnotated_nestedBoldItalic_hasBothSpans() {
        val result = VttTagParser.parseAnnotated("<b><i>Hello</i></b>")
        assertEquals("Hello", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun parseAnnotated_mixedStyledAndPlain_correctPositions() {
        val result = VttTagParser.parseAnnotated("<b>Hello</b> world")
        assertEquals("Hello world", result.text)
        val span = result.spanStyles.first()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
    }

    @Test
    fun parseAnnotated_partialBold_correctPositions() {
        val result = VttTagParser.parseAnnotated("Say <b>hello</b> back")
        assertEquals("Say hello back", result.text)
        val span = result.spanStyles.first()
        assertEquals(4, span.start)
        assertEquals(9, span.end)
    }

    @Test
    fun parseAnnotated_emptyString_returnsEmpty() {
        val result = VttTagParser.parseAnnotated("")
        assertEquals("", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun parseAnnotated_unmatchedOpeningTag_stillParses() {
        val result = VttTagParser.parseAnnotated("<b>Hello")
        assertEquals("Hello", result.text)
        assertEquals(1, result.spanStyles.size)
    }

    @Test
    fun parseAnnotated_multipleSpansInSequence() {
        val result = VttTagParser.parseAnnotated("<b>bold</b> <i>italic</i>")
        assertEquals("bold italic", result.text)
        assertEquals(2, result.spanStyles.size)
        assertTrue(result.spanStyles[0].start == 0 && result.spanStyles[0].end == 4)
        assertTrue(result.spanStyles[1].start == 5 && result.spanStyles[1].end == 11)
    }
}
