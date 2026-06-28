package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.collection.LruCache

object VttTagParser {

    // Matches any WebVTT cue-internal tag. Captures:
    //   group 1 — "/" for end tags (e.g. </b>)
    //   group 2 — tag name (b, i, u, c, v, ruby, rt, lang, br, …)
    //   group 3 — attributes / class annotation (".red", " Speaker", " fr")
    //   group 4 — trailing "/" for self-closing tags (<br/>, <b/>)
    //
    // Previous pattern only recognised b/i/u/lang and therefore leaked every
    // other legal WebVTT tag (<c.class>, <v Speaker>, <ruby>, <rt>, <br>) as
    // literal markup into rendered captions. Entities (&lt; …) were also
    // unhandled.
    private val TAG_PATTERN = Regex("""<(/?)([a-zA-Z]+)([^>]*?)(/?)>""")

    private val ENTITY_PATTERN = Regex("""&(amp|lt|gt|quot|apos|nbsp|#x([0-9a-fA-F]+)|#([0-9]+));""")

    private val annotatedCache = LruCache<String, AnnotatedString>(MAX_CACHED_CUES)

    fun parseAnnotated(text: String): AnnotatedString {
        annotatedCache.get(text)?.let { return it }
        val segments = mutableListOf<Segment>()
        val stack = mutableListOf<MutableList<SpanStyle>>()
        var currentStyles = mutableListOf<SpanStyle>()
        var lastEnd = 0

        for (match in TAG_PATTERN.findAll(text)) {
            if (match.range.first > lastEnd) {
                segments.add(Segment(decodeEntities(text.substring(lastEnd, match.range.first)), currentStyles.toList()))
            }
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2]
            val isSelfClosing = match.groupValues[4] == "/"

            when {
                // <br> / <br/> is a line break with no matching close.
                tagName == "br" -> segments.add(Segment("\n", currentStyles.toList()))
                isClosing -> {
                    if (stack.isNotEmpty()) {
                        currentStyles = stack.removeAt(stack.lastIndex)
                    }
                }
                // A self-closed styling tag such as <b/> carries no content,
                // so we neither push (it would never pop) nor emit anything.
                isSelfClosing -> Unit
                else -> {
                    stack.add(currentStyles)
                    currentStyles = currentStyles.toMutableList()
                    when (tagName) {
                        "b" -> currentStyles.add(SpanStyle(fontWeight = FontWeight.Bold))
                        "i" -> currentStyles.add(SpanStyle(fontStyle = FontStyle.Italic))
                        "u" -> currentStyles.add(SpanStyle(textDecoration = TextDecoration.Underline))
                        // c, v, ruby, rt, lang and any other recognised tag
                        // carry semantics we don't model in Compose yet; we
                        // strip the tag but keep the inner text unstyled.
                    }
                }
            }
            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            segments.add(Segment(decodeEntities(text.substring(lastEnd)), currentStyles.toList()))
        }

        return buildAnnotatedString {
            for (segment in segments) {
                val start = length
                append(segment.text)
                for (style in segment.styles) {
                    addStyle(style, start, length)
                }
            }
        }.also { annotatedCache.put(text, it) }
    }

    fun stripTags(text: String): String {
        // Convert <br> to newlines first, then drop all remaining tags, then
        // decode entities. Decoding entities last avoids re-introducing angle
        // brackets that could be mistaken for tags.
        val brConverted = TAG_PATTERN.replace(text) { m ->
            if (m.groupValues[2] == "br") "\n" else ""
        }
        return decodeEntities(brConverted)
    }

    // Single-pass entity decoder. Handles the named entities that occur in
    // real-world WebVTT captions plus numeric character references
    // (&#65; / &#x41;). Using one regex pass (rather than chained .replace
    // calls) prevents double-decoding of sequences like "&amp;lt;".
    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY_PATTERN.replace(text) { m ->
            when (m.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> "\u00A0"
                else -> {
                    val hex = m.groupValues[2]
                    val dec = m.groupValues[3]
                    when {
                        hex.isNotEmpty() -> hex.toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        dec.isNotEmpty() -> dec.toIntOrNull(10)?.let { String(Character.toChars(it)) }
                        else -> null
                    } ?: m.value
                }
            }
        }
    }

    private const val MAX_CACHED_CUES = 32

    private data class Segment(val text: String, val styles: List<SpanStyle>)
}
