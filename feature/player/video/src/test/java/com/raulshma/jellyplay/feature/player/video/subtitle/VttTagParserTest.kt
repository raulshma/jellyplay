package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VttTagParserTest {

    @Test
    fun parseAnnotated_formatsBoldItalicAndUnderline() {
        val annotated = VttTagParser.parseAnnotated("<b>Bold</b> <i>Italic</i> <u>Underline</u>")
        assertEquals("Bold Italic Underline", annotated.text)

        val boldSpan = annotated.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0, boldSpan?.start)
        assertEquals(4, boldSpan?.end)

        val italicSpan = annotated.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assertEquals(5, italicSpan?.start)
        assertEquals(11, italicSpan?.end)

        val underlineSpan = annotated.spanStyles.find { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(12, underlineSpan?.start)
        assertEquals(21, underlineSpan?.end)
    }

    @Test
    fun parseAnnotated_handlesBrTagsAndLineBreaks() {
        val annotated = VttTagParser.parseAnnotated("First Line<br>Second Line<br/>Third Line")
        assertEquals("First Line\nSecond Line\nThird Line", annotated.text)
    }

    @Test
    fun parseAnnotated_handlesUnrecognisedAndSelfClosingTags() {
        val annotated = VttTagParser.parseAnnotated("<c.red><v Speaker>Hello</v></c><b/> World")
        assertEquals("Hello World", annotated.text)
    }

    @Test
    fun parseAnnotated_decodesCharacterEntities() {
        val annotated = VttTagParser.parseAnnotated("AT&amp;T &lt;Tag&gt; &quot;Quote&apos; &nbsp; &#65; &#x42;")
        assertEquals("AT&T <Tag> \"Quote' \u00A0 A B", annotated.text)
    }

    @Test
    fun stripTags_convertsBrToNewlineAndStripsAllMarkup() {
        val result = VttTagParser.stripTags("<p>Hello<br/>World &amp; <b>Everyone</b></p>")
        assertEquals("Hello\nWorld & Everyone", result)
    }
}
