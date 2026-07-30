package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ORDERED_LIST_CAPTURE = Regex("^(\\d+)\\.\\s(.*)")

/**
 * A minimal, dependency-free Markdown renderer for the subset of
 * GitHub-flavoured Markdown used in release notes and plugin changelogs:
 * `#`/`##`/`###` headings, `-`/`*` and `1.` lists, inline `**bold**`,
 * `*italic*`, `` `code` ``, `~~strike~~` and `[link](url)` (links styled,
 * not clickable).
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(text) { text.lines() }

    Column(modifier = modifier) {
        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(4.dp))
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val content = trimmed.removePrefix("- ").removePrefix("* ")
                    Text(
                        text = buildAnnotatedString {
                            append("\u2022  ")
                            appendMarkdownInline(content)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                ORDERED_LIST_CAPTURE.matches(trimmed) -> {
                    val matchResult = ORDERED_LIST_CAPTURE.find(trimmed)
                    if (matchResult != null) {
                        val (num, content) = matchResult.destructured
                        Text(
                            text = buildAnnotatedString {
                                append("$num.  ")
                                appendMarkdownInline(content)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                trimmed.startsWith("```") -> {
                    // Code block start/end marker, skip
                }
                else -> {
                    Text(
                        text = buildAnnotatedString { appendMarkdownInline(trimmed) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotatedString.Builder.appendMarkdownInline(text: String) {
    var remaining = text

    while (remaining.isNotEmpty()) {
        when {
            remaining.startsWith("**") || remaining.startsWith("__") -> {
                val closeLen = 2
                val closeMarker = remaining.substring(0, closeLen)
                val endIndex = remaining.indexOf(closeMarker, closeLen)
                if (endIndex > closeLen) {
                    val content = remaining.substring(closeLen, endIndex)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                    remaining = remaining.substring(endIndex + closeLen)
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
            remaining.startsWith("*") && !remaining.startsWith("**") -> {
                val endIndex = remaining.indexOf("*", 1)
                if (endIndex > 0) {
                    val content = remaining.substring(1, endIndex)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                    remaining = remaining.substring(endIndex + 1)
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
            remaining.startsWith("~~") -> {
                val endIndex = remaining.indexOf("~~", 2)
                if (endIndex > 2) {
                    val content = remaining.substring(2, endIndex)
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(content)
                    }
                    remaining = remaining.substring(endIndex + 2)
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
            remaining.startsWith("`") -> {
                val endIndex = remaining.indexOf("`", 1)
                if (endIndex > 1) {
                    val content = remaining.substring(1, endIndex)
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )) {
                        append(content)
                    }
                    remaining = remaining.substring(endIndex + 1)
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
            remaining.startsWith("[") -> {
                val closeBracket = remaining.indexOf("]", 1)
                val openParen = if (closeBracket > 0) remaining.indexOf("(", closeBracket) else -1
                val closeParen = if (openParen == closeBracket + 1) remaining.indexOf(")", openParen) else -1
                if (closeBracket > 0 && closeParen > openParen) {
                    val linkText = remaining.substring(1, closeBracket)
                    withStyle(SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )) {
                        append(linkText)
                    }
                    remaining = remaining.substring(closeParen + 1)
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
            else -> {
                val nextSpecial = remaining.indexOfAny(charArrayOf('*', '~', '`', '['))
                if (nextSpecial > 0) {
                    append(remaining.substring(0, nextSpecial))
                    remaining = remaining.substring(nextSpecial)
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
        }
    }
}
