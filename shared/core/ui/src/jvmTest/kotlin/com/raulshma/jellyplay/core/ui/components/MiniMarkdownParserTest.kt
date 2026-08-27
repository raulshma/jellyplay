package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.ui.components.MiniMarkdownParser.Block
import com.raulshma.jellyplay.core.ui.components.MiniMarkdownParser.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Deterministic structure tests for [MiniMarkdownParser] — block sequence and
 * span flags only, no rendering. Pure stdlib, so the suite also runs on the
 * wasmJs Node test lane when that is enabled.
 */
class MiniMarkdownParserTest {

    // region basics ----------------------------------------------------------

    @Test
    fun parse_emptyInputYieldsNoBlocks() {
        assertEquals(emptyList(), MiniMarkdownParser.parse(""))
    }

    @Test
    fun parse_whitespaceOnlyInputYieldsNoBlocks() {
        assertEquals(emptyList(), MiniMarkdownParser.parse("\n \n\t\n"))
    }

    @Test
    fun parse_consecutiveBlankLinesCollapseIntoOneParagraphBreak() {
        val blocks = MiniMarkdownParser.parse("a\n\n\n\nb")
        assertEquals(2, blocks.size, "two paragraphs expected")
        assertIs<Block.Paragraph>(blocks[0])
        assertIs<Block.Paragraph>(blocks[1])
    }

    @Test
    fun parse_crlfLineEndingsNormalize() {
        val blocks = MiniMarkdownParser.parse("a\r\nb")
        val p = assertIs<Block.Paragraph>(blocks.single())
        assertEquals(listOf(Span("a\nb")), p.spans)
    }

    // endregion

    // region headings --------------------------------------------------------

    @Test
    fun heading_levelsMapTo1Through4() {
        val blocks = MiniMarkdownParser.parse("# h1\n## h2\n### h3\n#### h4")
        val levels = blocks.map { assertIs<Block.Heading>(it).level }
        assertEquals(listOf(1, 2, 3, 4), levels)
    }

    @Test
    fun heading_hashesBeyondFourClampToLevelFour() {
        val blocks = MiniMarkdownParser.parse("##### five\n###### six")
        assertTrue(blocks.all { assertIs<Block.Heading>(it).level == 4 }, "clamped to 4")
    }

    @Test
    fun heading_hashWithoutSpaceStaysLiteralParagraph() {
        val blocks = MiniMarkdownParser.parse("#hashtag and #2 pencil")
        val p = assertIs<Block.Paragraph>(blocks.single())
        assertEquals(listOf(Span("#hashtag and #2 pencil")), p.spans)
    }

    @Test
    fun heading_inlineEmphasisInsideHeadingParses() {
        val blocks = MiniMarkdownParser.parse("## **bold** head")
        val spans = assertIs<Block.Heading>(blocks.single()).spans
        assertEquals(listOf(Span("bold", bold = true), Span(" head")), spans)
    }

    // endregion

    // region inline styles ---------------------------------------------------

    @Test
    fun inline_boldDoubleAsteriskAndUnderscore() {
        val spans = MiniMarkdownParser.parseInline("**b** and __b__")
        assertEquals(
            listOf(Span("b", bold = true), Span(" and "), Span("b", bold = true)),
            spans,
        )
    }

    @Test
    fun inline_italicSingleMarkers() {
        val spans = MiniMarkdownParser.parseInline("*i* and _i_")
        assertEquals(
            listOf(Span("i", italic = true), Span(" and "), Span("i", italic = true)),
            spans,
        )
    }

    @Test
    fun inline_nestedItalicInsideBoldKeepsBothFlags() {
        val spans = MiniMarkdownParser.parseInline("**bold *both* tail**")
        assertEquals(
            listOf(
                Span("bold ", bold = true),
                Span("both", bold = true, italic = true),
                Span(" tail", bold = true),
            ),
            spans,
        )
    }

    @Test
    fun inline_italicOpenerRequiresAdjacentText() {
        // "* item" reads as a list line to the BLOCK parser; at inline level a
        // marker followed by a space stays literal.
        val spans = MiniMarkdownParser.parseInline("a * b")
        assertEquals(listOf(Span("a * b")), spans)
    }

    @Test
    fun inline_unmatchedDelimitersStayLiteral() {
        val cases = mapOf(
            "**unpaired" to listOf(Span("**unpaired")),
            "mid * unpaired" to listOf(Span("mid * unpaired")),
            "`unclosed code" to listOf(Span("`unclosed code")),
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, MiniMarkdownParser.parseInline(input), "input: $input")
        }
    }

    @Test
    fun inline_singleCharUnmatchedMarkerStaysLiteral() {
        assertEquals(listOf(Span("*")), MiniMarkdownParser.parseInline("*"))
        assertEquals(listOf(Span("_")), MiniMarkdownParser.parseInline("_"))
    }

    @Test
    fun inline_codeSpanResetsOtherFlagsButKeepsUrl() {
        // Whole line sits inside **…**, so non-code children inherit bold+url;
        // the code child resets bold/italic but passes url through unchanged.
        val spans = MiniMarkdownParser.parseInline(
            "**x `code` y**",
            bold = true,
            url = "https://e.example",
        )
        assertEquals(
            listOf(
                Span("x ", bold = true, url = "https://e.example"),
                Span("code", code = true, url = "https://e.example"),
                Span(" y", bold = true, url = "https://e.example"),
            ),
            spans,
        )
    }

    @Test
    fun inline_codeContentNeverParsesMarkup() {
        val spans = MiniMarkdownParser.parseInline("`a *b* c`")
        assertEquals(listOf(Span("a *b* c", code = true)), spans)
    }

    @Test
    fun inline_escapesNeutralizeAsciiPunctuation() {
        // Backslash before * [ ] _ turns those markers into literal characters.
        val spans = MiniMarkdownParser.parseInline("""a\*b\[c]\_d""")
        assertEquals(listOf(Span("a*b[c]_d")), spans)
    }

    @Test
    fun inline_backslashBeforeNonEscapableStaysLiteral() {
        assertEquals(listOf(Span("""a\zb""")), MiniMarkdownParser.parseInline("""a\zb"""))
    }

    @Test
    fun inline_escapeInsideCodeSpanStaysRaw() {
        val spans = MiniMarkdownParser.parseInline("`\\*`")
        assertEquals(listOf(Span("\\*", code = true)), spans)
    }

    // endregion

    // region links & images --------------------------------------------------

    @Test
    fun link_basicsAssignUrlAndLeaveTextPlain() {
        val spans = MiniMarkdownParser.parseInline("see [docs](https://x.example) now")
        assertEquals(
            listOf(
                Span("see "),
                Span("docs", url = "https://x.example"),
                Span(" now"),
            ),
            spans,
        )
    }

    @Test
    fun link_nestedEmphasisCarriesTheUrlOnEveryChild() {
        // The literal text between styled children (" ") also becomes a child
        // span carrying the url, so the whole label renders as one link.
        val spans = MiniMarkdownParser.parseInline("[**b** *i*](u)")
        assertEquals(
            listOf(
                Span("b", bold = true, url = "u"),
                Span(" ", url = "u"),
                Span("i", italic = true, url = "u"),
            ),
            spans,
        )
    }

    @Test
    fun link_unterminatedBracketRendersLiterally() {
        val cases = mapOf(
            "[no url]" to listOf(Span("[no url]")),
            "[text](no close" to listOf(Span("[text](no close")),
            "[empty]() x" to listOf(Span("[empty]() x")),
            "[space in](a b)" to listOf(Span("[space in](a b)")),
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, MiniMarkdownParser.parseInline(input), "input: $input")
        }
    }

    @Test
    fun image_degradesToAltTextAsPlainSpans() {
        val spans = MiniMarkdownParser.parseInline("pre ![alt text](img.png) post")
        assertEquals(
            listOf(Span("pre "), Span("alt text"), Span(" post")),
            spans,
        )
    }

    @Test
    fun image_brokenShapeLeavesEverythingLiteral() {
        // No `](` shape anywhere: '!' and '[' both stay plain characters.
        assertEquals(
            listOf(Span("![alt")),
            MiniMarkdownParser.parseInline("![alt"),
        )
    }

    // endregion

    // region fenced code -----------------------------------------------------

    @Test
    fun fence_basicVerbatimContentAndInfo() {
        val blocks = MiniMarkdownParser.parse("```kotlin\nval a = 1\n```")
        val code = assertIs<Block.CodeBlock>(blocks.single())
        assertEquals("kotlin", code.info)
        assertEquals("val a = 1", code.content)
    }

    @Test
    fun fence_noInfoStringYieldsNull() {
        val code = assertIs<Block.CodeBlock>(MiniMarkdownParser.parse("```\nx\n```").single())
        assertEquals(null, code.info)
        assertEquals("x", code.content)
    }

    @Test
    fun fence_preservesBlankLinesAndInternalNewlinesVerbatim() {
        val code = assertIs<Block.CodeBlock>(
            MiniMarkdownParser.parse("```\na\n\n\nb\n```").single(),
        )
        assertEquals("a\n\n\nb", code.content)
    }

    @Test
    fun fence_markupInsideStaysUnparsed() {
        val code = assertIs<Block.CodeBlock>(
            MiniMarkdownParser.parse("```\n**not bold** [link](u)\n```").single(),
        )
        assertEquals("**not bold** [link](u)", code.content)
    }

    @Test
    fun fence_unterminatedRunsToEndOfInputSilently() {
        val blocks = MiniMarkdownParser.parse("para\n```kotlin\ncode continues\nforever")
        assertEquals(2, blocks.size)
        assertIs<Block.Paragraph>(blocks[0])
        val code = assertIs<Block.CodeBlock>(blocks[1])
        assertEquals("kotlin", code.info)
        assertEquals("code continues\nforever", code.content)
    }

    @Test
    fun fence_closingFenceMustBeBareBackticks() {
        // A ```-prefixed content line with trailing text does NOT close.
        val code = assertIs<Block.CodeBlock>(
            MiniMarkdownParser.parse("```\ncode ``` still code\n```").single(),
        )
        assertEquals("code ``` still code", code.content)
    }

    @Test
    fun fence_indentStripsLeadingSpacesFromContinuationLines() {
        val code = assertIs<Block.CodeBlock>(
            MiniMarkdownParser.parse("   ```\n  indented\nplain\n   ```").single(),
        )
        assertEquals("indented\nplain", code.content)
    }

    @Test
    fun fence_adjacentBlocksResumeParsingAfterClose() {
        val blocks = MiniMarkdownParser.parse("```\nc\n```\ntext after")
        assertIs<Block.CodeBlock>(blocks[0])
        assertIs<Block.Paragraph>(blocks[1])
    }

    // endregion

    // region lists -----------------------------------------------------------

    @Test
    fun list_unorderedBothMarkers() {
        val blocks = MiniMarkdownParser.parse("- one\n* two")
        val items = blocks.map { assertIs<Block.ListItem>(it) }
        assertTrue(items.all { !it.ordered }, "unordered")
        assertEquals(listOf("one", "two"), items.map { it.spans.single().text })
    }

    @Test
    fun list_orderedAcceptsDotAndParenMarkersAndBothSeparators() {
        val blocks = MiniMarkdownParser.parse("3. three\n4. four\n5) five")
        val numbers = blocks.map { assertIs<Block.ListItem>(it).index }
        assertEquals(listOf(3, 4, 5), numbers, "explicit marker numbers pass through verbatim")
    }

    @Test
    fun list_explicitNumbersNeverAutoRenumber() {
        // No run-incrementing: an authored "9." keeps showing 9 even after "7.".
        val blocks = MiniMarkdownParser.parse("7. seven\n\n9. nine")
        val numbers = blocks.map { assertIs<Block.ListItem>(it).index }
        assertEquals(listOf(7, 9), numbers)
    }

    @Test
    fun list_inlineFormattingInsideItems() {
        val item = assertIs<Block.ListItem>(MiniMarkdownParser.parse("- **hi** there").single())
        assertEquals(
            listOf(Span("hi", bold = true), Span(" there")),
            item.spans,
        )
    }

    @Test
    fun list_markerWithoutSpaceIsLiteralParagraphText() {
        val p = assertIs<Block.Paragraph>(MiniMarkdownParser.parse("-5 degrees").single())
        assertEquals(listOf(Span("-5 degrees")), p.spans)
    }

    @Test
    fun list_codeBlockInterruptsOrderedRunNumbering() {
        val blocks = MiniMarkdownParser.parse("1. a\n```\ncode\n```\n1. restarted")
        val numbers = blocks.mapNotNull { (it as? Block.ListItem)?.index }
        assertEquals(listOf(1, 1), numbers, "fence between items leaves explicit '1.' intact")
    }

    // endregion

    // region quotes & rules --------------------------------------------------

    @Test
    fun quote_linesStripMarkerAndParseInlinePerLine() {
        val quote = assertIs<Block.Blockquote>(
            MiniMarkdownParser.parse("> *wisdom*\n> more").single(),
        )
        assertEquals(
            listOf(
                listOf(Span("wisdom", italic = true)),
                listOf(Span("more")),
            ),
            quote.lines,
        )
    }

    @Test
    fun quote_bareGtMarkerStaysInsideQuoteAsSpanlessLine() {
        // A bare ">" is not a blank source line, so it keeps the quote open;
        // its empty body yields no spans (renders as a thin visual gap).
        val quote = assertIs<Block.Blockquote>(MiniMarkdownParser.parse("> a\n>\n> b").single())
        assertEquals(
            listOf(listOf(Span("a")), emptyList(), listOf(Span("b"))),
            quote.lines,
        )
    }

    @Test
    fun quote_trulyBlankLineSplitsIntoSeparateQuotes() {
        val blocks = MiniMarkdownParser.parse("> a\n\n> b")
        val quotes = blocks.map { assertIs<Block.Blockquote>(it) }
        assertEquals(2, quotes.size)
        assertEquals(1, quotes[0].lines.size)
        assertEquals(1, quotes[1].lines.size)
    }

    @Test
    fun quote_nonPrefixedLineAfterQuoteStopsIt() {
        val blocks = MiniMarkdownParser.parse("> q\nafter")
        assertIs<Block.Blockquote>(blocks[0])
        assertIs<Block.Paragraph>(blocks[1])
    }

    @Test
    fun rule_threePlusIdenticalCharsFormRule() {
        val inputs = listOf("---", "----", "***", "___")
        inputs.forEach { input ->
            assertIs<Block.HorizontalRule>(
                MiniMarkdownParser.parse(input).single(),
                "input: $input",
            )
        }
    }

    @Test
    fun rule_twoDashesStayParagraph() {
        val p = assertIs<Block.Paragraph>(MiniMarkdownParser.parse("--").single())
        assertEquals(listOf(Span("--")), p.spans)
    }

    // endregion

    // region document-level combos --------------------------------------------

    @Test
    fun doc_typicalChangelogShape() {
        val markdown = """
            ## v2.0

            - **new**: fancy thing ([details](https://x))
            - fixed bug

            ```
            gradle sync
            ```

            > upgrade carefully
        """.trimIndent()
        val blocks = MiniMarkdownParser.parse(markdown)
        assertEquals(
            listOf("Heading", "ListItem", "ListItem", "CodeBlock", "Blockquote"),
            blocks.map { it::class.simpleName },
        )
        val firstItem = blocks[1] as Block.ListItem
        assertEquals(
            listOf(
                Span("new", bold = true),
                Span(": fancy thing ("),
                Span("details", url = "https://x"),
                Span(")"),
            ),
            firstItem.spans,
        )
    }

    @Test
    fun doc_paragraphAbsorbsUntilBlankOrBlockStarter() {
        val blocks = MiniMarkdownParser.parse("line1\nline2\n# Heading")
        assertIs<Block.Paragraph>(blocks[0])
        val p = blocks[0] as Block.Paragraph
        assertEquals(listOf(Span("line1\nline2")), p.spans)
        assertIs<Block.Heading>(blocks[1])
    }

    @Test
    fun doc_blockStartersInsideParagraphFlushItFirst() {
        val blocks = MiniMarkdownParser.parse("intro\n- bullet")
        val items = blocks.map { it::class.simpleName }
        assertEquals(listOf("Paragraph", "ListItem"), items)
    }

    // endregion
}
