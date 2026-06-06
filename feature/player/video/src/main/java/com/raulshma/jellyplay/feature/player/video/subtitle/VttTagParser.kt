package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.collection.LruCache

object VttTagParser {

    private val TAG_PATTERN = Regex("""<(/?)(b|i|u|lang[^>]*)>""")

    private val annotatedCache = LruCache<String, AnnotatedString>(MAX_CACHED_CUES)

    fun parseAnnotated(text: String): AnnotatedString {
        annotatedCache.get(text)?.let { return it }
        val segments = mutableListOf<Segment>()
        val stack = mutableListOf<MutableList<SpanStyle>>()
        var currentStyles = mutableListOf<SpanStyle>()
        var lastEnd = 0

        for (match in TAG_PATTERN.findAll(text)) {
            if (match.range.first > lastEnd) {
                segments.add(Segment(text.substring(lastEnd, match.range.first), currentStyles.toList()))
            }
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2]
            if (isClosing) {
                if (stack.isNotEmpty()) {
                    currentStyles = stack.removeLast()
                }
            } else {
                stack.add(currentStyles)
                currentStyles = currentStyles.toMutableList()
                when {
                    tagName == "b" -> currentStyles.add(SpanStyle(fontWeight = FontWeight.Bold))
                    tagName == "i" -> currentStyles.add(SpanStyle(fontStyle = FontStyle.Italic))
                    tagName == "u" -> currentStyles.add(SpanStyle(textDecoration = TextDecoration.Underline))
                }
            }
            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            segments.add(Segment(text.substring(lastEnd), currentStyles.toList()))
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

    fun stripTags(text: String): String =
        TAG_PATTERN.replace(text, "")

    private const val MAX_CACHED_CUES = 32

    private data class Segment(val text: String, val styles: List<SpanStyle>)
}
