package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wasm actual of the markdown seam: renders [MiniMarkdownParser] output with a
 * plain Column+Text composition — headings (size/weight steps mirroring the
 * jvmShared mikepenz typography map), bold/italic, inline code (monospace on a
 * theme-resolved accessible background), fenced code blocks wrapped without
 * scrollbars, bulleted/ordered lists with hanging indent, links themed
 * primary+underline and clickable via [LinkAnnotation.Url], flat blockquotes
 * with an accent bar, and horizontal rules.
 *
 * DELIBERATE CUT remains on renderer *dependencies*, not fidelity: mikepenz
 * multiplatform-markdown-renderer 0.43.0 publishes only Kotlin-2.4-built wasm
 * klibs which our klib loader skips (and its graph evicts our stdlib; see
 * spike w-10C class C), hence this pure-CMP pipeline. Revisit when the
 * toolchain moves to 2.4+. Syntax still unparsed stays literal text — the
 * exhaustive matrix lives on [MiniMarkdownParser].
 */
@Composable
internal actual fun MarkdownBody(text: String, modifier: Modifier) {
    // Parser output depends only on [text]; recomposition-safe memoization.
    val blocks = remember(text) { MiniMarkdownParser.parse(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block -> RenderBlock(block) }
    }
}

/** Typography mirrors MarkdownText.jvmShared.kt's mikepenz map for parity. */
@Composable
private fun RenderBlock(block: MiniMarkdownParser.Block) {
    val colorScheme = MaterialTheme.colorScheme
    when (block) {
        is MiniMarkdownParser.Block.Heading -> {
            val base = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                3 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.titleSmall
            }
            // Levels 1-3 bold, level 4 semibold — mirrors the jvmShared
            // mikepenz map's weights; h5/h6 only clamp here into the level-4
            // slot (the JVM pipeline gives them its own smaller entry), so the
            // *sizes* of clamped headings diverge, not the bold/semibold rule.
            val weight = if (block.level <= 3) FontWeight.Bold else FontWeight.SemiBold
            RenderSpans(block.spans, base.copy(fontWeight = weight))
        }

        is MiniMarkdownParser.Block.Paragraph ->
            RenderSpans(block.spans, MaterialTheme.typography.bodySmall)

        is MiniMarkdownParser.Block.CodeBlock -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Monospace block; softWrap keeps long lines wrapping instead of
            // overflowing or demanding a scrollbar.
            Text(
                text = block.content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colorScheme.onSurfaceVariant,
                ),
                fontFamily = FontFamily.Monospace,
                softWrap = true,
            )
        }

        is MiniMarkdownParser.Block.ListItem -> {
            // Authored marker verbatim: bullet, or number + the authored
            // separator ('.' or ')') exactly as parsed.
            val marker = if (block.ordered) "${block.index}${block.suffix} " else "• "
            // Hanging indent: first line starts at the margin carrying the
            // marker; wrapped continuation lines indent past it.
            val bodyFontSize = MaterialTheme.typography.bodySmall.fontSize
            val hang = if (bodyFontSize.isSp) (bodyFontSize.value * 1.5f).sp else 18.sp
            RenderSpans(
                spans = listOf(MiniMarkdownParser.Span(marker)) + block.spans,
                style = MaterialTheme.typography.bodySmall.copy(
                    textIndent = TextIndent(firstLine = 0.sp, restLine = hang),
                ),
            )
        }

        is MiniMarkdownParser.Block.Blockquote -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colorScheme.outlineVariant),
            )
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                block.lines.forEach { line ->
                    RenderSpans(
                        spans = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                }
            }
        }

        MiniMarkdownParser.Block.HorizontalRule -> Spacer(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .height(1.dp)
                .fillMaxWidth()
                .background(colorScheme.outlineVariant),
        )
    }
}

/**
 * Builds the [AnnotatedString] for one run of spans. Inline code gets monospace
 * + the M3 surfaceVariant/onSurfaceVariant pair (accessible contrast in both
 * schemes); links get primary+underline inside [LinkAnnotation.Url], making
 * them tappable through the platform UriHandler on every CMP target.
 */
@Composable
private fun RenderSpans(spans: List<MiniMarkdownParser.Span>, style: TextStyle) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            spans.forEach { span ->
                var spanStyle = SpanStyle()
                if (span.bold) spanStyle = spanStyle.merge(SpanStyle(fontWeight = FontWeight.Bold))
                if (span.italic) spanStyle = spanStyle.merge(SpanStyle(fontStyle = FontStyle.Italic))
                if (span.code) {
                    spanStyle = spanStyle.merge(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = colorScheme.surfaceVariant,
                            color = colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                val url = span.url
                if (url != null) {
                    spanStyle = spanStyle.merge(
                        SpanStyle(
                            color = colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(spanStyle))) {
                        append(span.text)
                    }
                } else if (spanStyle != SpanStyle()) {
                    withStyle(spanStyle) { append(span.text) }
                } else {
                    append(span.text)
                }
            }
        },
        style = style,
    )
}
