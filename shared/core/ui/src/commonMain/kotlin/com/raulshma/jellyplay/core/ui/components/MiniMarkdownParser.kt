package com.raulshma.jellyplay.core.ui.components

/**
 * Pure-Kotlin markdown block/span parser backing the wasm [MarkdownBody]
 * actual. Intentionally tiny and dependency-free so it is unit-testable on any
 * target (see MiniMarkdownParserTest) while the wasm renderer stays a plain
 * Column+Text composition — no DOM, no mikepenz.
 *
 * COVERED SYNTAX (per-rule precision lives next to each branch):
 *  - ATX headings `#`..`######`; levels beyond 4 clamp down to 4 rendering steps
 *  - paragraphs; single newlines inside a paragraph are preserved as '\n' so
 *    changelog lines keep their breaks
 *  - inline: **bold**, __bold__, *italic*, _italic_, `` `code` ``,
 *    backslash-escaped ASCII punctuation, [text](url) links; `![alt](url)`
 *    images degrade to their alt text as plain spans
 *  - fenced code blocks ``` ``` ```: content verbatim incl. blank lines;
 *    unterminated fences run to end of input; inline code never parses markup
 *  - unordered lists `- `/`* `, ordered lists `N.`/`N)` displaying the
 *    explicitly written number (no auto-renumbering: what the author wrote is
 *    what renders, keeping runs like `7.` then `9.` exactly as authored)
 *  - blockquotes `> ` (flat: one span-list per source line, no nesting)
 *  - horizontal rules `---` / `***` / `___`
 *
 * NOT covered (stays literal text): setext headings, tables, task-list
 * checkboxes, HTML/tags/entities, reference links, autolinks `<http://…>`,
 * nested blocks inside quotes or list items.
 */
internal object MiniMarkdownParser {

    /** Flat styled run of text. [url] non-null marks a link segment. */
    internal data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val url: String? = null,
    )

    internal sealed interface Block {
        /** ATX heading; [level] is 1..4 after clamping. */
        data class Heading(val level: Int, val spans: List<Span>) : Block

        /** Soft line breaks inside the paragraph are kept as '\n' in spans. */
        data class Paragraph(val spans: List<Span>) : Block

        /**
         * Fenced block: verbatim content including blank lines and internal
         * newlines, fence lines excluded. [info] is the trimmed text after the
         * opening ``` ("kotlin" for ```kotlin), null when empty.
         */
        data class CodeBlock(val info: String?, val content: String) : Block

        /**
         * One list item. [index] is 0 for unordered items; for ordered ones it
         * is the explicitly written number (no auto-renumbering).
         */
        data class ListItem(val ordered: Boolean, val index: Int, val spans: List<Span>) : Block

        /** Consecutive `>` lines, prefix stripped; blank line ends the quote. */
        data class Blockquote(val lines: List<List<Span>>) : Block

        data object HorizontalRule : Block
    }

    private const val MAX_HEADING_LEVEL = 4

    // Ordered marker: digits + '.' or ')' + optional single space + content.
    private val ORDERED_ITEM = Regex("""(\d{1,9})[.)][ ]?(.*)""")

    fun parse(markdown: String): List<Block> {
        if (markdown.isEmpty()) return emptyList()
        val lines = markdown.replace("\r\n", "\n").split('\n')

        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) {
                i++ // blank lines only separate blocks; consecutive blanks collapse
                continue
            }
            val trimmed = raw.trim()

            if (trimmed.startsWith("```")) {
                // Fence info string is everything after the opening backticks.
                val indent = raw.length - raw.trimStart(' ').length
                val info = trimmed.removePrefix("```").trim().ifEmpty { null }
                val content = StringBuilder()
                i++
                while (i < lines.size) {
                    val closing = lines[i].trim()
                    if (closing == "```") {
                        i++
                        break
                    }
                    if (content.isNotEmpty()) content.append('\n')
                    // Strip the opening fence's leading-space indentation from
                    // continuation lines when they carry at least that much.
                    content.append(lines[i].drop(minOf(indent, countLeadingSpaces(lines[i]))))
                    i++
                }
                // Unterminated fence: EOF closes it silently (test-documented).
                blocks += Block.CodeBlock(info, content.toString())
            } else if (isHorizontalRule(trimmed)) {
                blocks += Block.HorizontalRule
                i++
            } else if (headingLevel(trimmed) != 0) {
                val hashes = headingLevel(trimmed)
                val text = trimmed.dropWhile { it == '#' }.trimStart(' ')
                blocks += Block.Heading(
                    level = hashes.coerceAtMost(MAX_HEADING_LEVEL),
                    spans = parseInline(text),
                )
                i++
            } else if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<List<Span>>()
                while (i < lines.size && lines[i].isNotBlank()) {
                    val t = lines[i].trim()
                    if (!t.startsWith(">")) break // non-quote line also stops the quote
                    var body = t.removePrefix(">")
                    if (body.startsWith(" ")) body = body.removePrefix(" ")
                    quoteLines += parseInline(body)
                    i++
                }
                blocks += Block.Blockquote(quoteLines)
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                blocks += Block.ListItem(
                    ordered = false,
                    index = 0,
                    spans = parseInline(trimmed.substring(2)),
                )
                i++
            } else if (orderedMatch(trimmed) != null) {
                val m = orderedMatch(trimmed)!!
                // The explicitly written marker number is what renders.
                blocks += Block.ListItem(ordered = true, index = m.first, spans = parseInline(m.second))
                i++
            } else {
                // Paragraph: absorb plain-text lines until a blank line or any
                // block starter interrupts. Lines join with '\n' (soft-break
                // preserved) before inline parsing so bold/code may span them.
                val para = StringBuilder(trimmed)
                i++
                while (i < lines.size && !startsBlock(lines[i])) {
                    para.append('\n').append(lines[i].trim())
                    i++
                }
                blocks += Block.Paragraph(parseInline(para.toString()))
            }
        }
        return blocks
    }

    private fun countLeadingSpaces(line: String): Int {
        var n = 0
        while (n < line.length && line[n] == ' ') n++
        return n
    }

    /** `---`, `***`, `___` lines of 3+ identical marker chars are rules. */
    private fun isHorizontalRule(t: String): Boolean =
        t.length >= 3 &&
            (t[0] == '-' || t[0] == '*' || t[0] == '_') &&
            t.all { it == t[0] }

    /**
     * Leading-# count when it forms an ATX heading (space or end-of-line must
     * follow); otherwise 0. Accepts 1..6 hashes; callers clamp to 4.
     */
    private fun headingLevel(t: String): Int {
        val hashes = t.takeWhile { it == '#' }.length
        if (hashes == 0 || hashes > 6) return 0
        // "#tag" without a following space is literal text, not a heading.
        return if (hashes < t.length && t[hashes] != ' ') 0 else hashes
    }

    private fun orderedMatch(t: String): Pair<Int, String>? =
        ORDERED_ITEM.matchEntire(t)?.let { m ->
            // Content must exist: a bare "3." stays paragraph text (same rule
            // as the empty-content requirement for unordered markers).
            m.groupValues[2].takeIf { it.isNotEmpty() }?.let { m.groupValues[1].toInt() to it }
        }

    private fun startsBlock(line: String): Boolean {
        if (line.isBlank()) return true
        val t = line.trim()
        return t.startsWith("```") ||
            t.startsWith(">") ||
            t.startsWith("- ") ||
            t.startsWith("* ") ||
            isHorizontalRule(t) ||
            headingLevel(t) != 0 ||
            orderedMatch(t) != null
    }

    // region inline parsing -------------------------------------------------

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|>~"

    private data class LinkShape(val text: String, val url: String, val end: Int)

    /**
     * Parses emphasis, code spans, links, images, escapes. Precedence:
     * escape > code span > image > link > double-marker bold > single-marker
     * italic. Delimiters whose closer is missing render literally. Inside code
     * spans nothing further is parsed and styling flags reset (url survives).
     * Nested segments inherit composite flags; link children each carry [url].
     */
    internal fun parseInline(
        s: String,
        bold: Boolean = false,
        italic: Boolean = false,
        url: String? = null,
    ): List<Span> {
        val out = mutableListOf<Span>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out += Span(plain.toString(), bold, italic, code = false, url = url)
                plain.clear()
            }
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE -> {
                    plain.append(s[i + 1])
                    i += 2
                }

                c == '`' -> {
                    val close = s.indexOf('`', i + 1)
                    if (close == -1) {
                        plain.append(c)
                        i++
                    } else {
                        flushPlain()
                        out += Span(s.substring(i + 1, close), bold = false, italic = false, code = true, url = url)
                        i = close + 1
                    }
                }

                c == '!' && i + 1 < s.length && s[i + 1] == '[' -> {
                    // Image degrades to its alt text as unstyled spans.
                    val parsed = parseLinkShape(s, i + 1)
                    if (parsed != null) {
                        flushPlain()
                        out += parseInline(parsed.text, bold = false, italic = false, url = null)
                        i = parsed.end
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                c == '[' -> {
                    val parsed = parseLinkShape(s, i)
                    if (parsed != null) {
                        flushPlain()
                        out += parseInline(parsed.text, bold = bold, italic = italic, url = parsed.url)
                        i = parsed.end
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                c == '*' || c == '_' -> {
                    val doubled = i + 1 < s.length && s[i + 1] == c
                    if (doubled) {
                        val close = findCloser(s, i + 2, "$c$c")
                        // Empty ** pair (closer right after opener) renders
                        // both markers literally instead of an empty span.
                        if (close == -1 || close == i + 2) {
                            plain.append(c).append(c)
                            i += 2
                        } else {
                            flushPlain()
                            out += parseInline(
                                s.substring(i + 2, close),
                                bold = true,
                                italic = italic,
                                url = url,
                            )
                            i = close + 2
                        }
                    } else {
                        // Single marker: opener must touch text (no whitespace
                        // right after); closer must not follow a space.
                        val openOk = i + 1 < s.length && !s[i + 1].isWhitespace()
                        val close = if (openOk) findCloser(s, i + 2, c.toString()) else -1
                        if (close == -1 || s[close - 1].isWhitespace()) {
                            plain.append(c)
                            i++
                        } else {
                            flushPlain()
                            out += parseInline(
                                s.substring(i + 1, close),
                                bold = bold,
                                italic = true,
                                url = url,
                            )
                            i = close + 1
                        }
                    }
                }

                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flushPlain()
        return out
    }

    /**
     * Attempts `[text](url)` with '[' at [open]; returns link text, url, and
     * index past ')'. URL must be non-empty and whitespace-free (light rule);
     * a missing shape yields null → the caller emits '[' literally.
     */
    private fun parseLinkShape(s: String, open: Int): LinkShape? {
        val mid = s.indexOf("](", open + 1)
        if (mid == -1) return null
        val close = s.indexOf(')', mid + 2)
        if (close == -1) return null
        val url = s.substring(mid + 2, close)
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        return LinkShape(text = s.substring(open + 1, mid), url = url, end = close + 1)
    }

    /** First occurrence of [marker] at/after [from] whose prior char isn't a space. */
    private fun findCloser(s: String, from: Int, marker: String): Int {
        var idx = from.coerceAtLeast(marker.length)
        while (true) {
            idx = s.indexOf(marker, idx)
            if (idx == -1) return -1
            if (s[idx - 1].isWhitespace()) {
                idx += marker.length
                continue
            }
            return idx
        }
    }

    // endregion
}
