package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Renders GitHub-flavoured Markdown (release notes, plugin changelogs) via the
 * Material 3 `multiplatform-markdown-renderer` wrapper. Covers headings, lists
 * (including GFM task-list checkboxes), fenced code blocks, blockquotes,
 * horizontal rules, tables, inline formatting, and clickable links — links
 * route through the ambient `UriHandler`, opening in the browser.
 *
 * Public signature preserved from the previous hand-rolled renderer so call
 * sites need no changes.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    // Map the renderer's styles onto the app's typography. Release notes render
    // in compact surfaces (update sheet, plugin version rows), so body text
    // stays at bodySmall to match the previous dense feel; headings use the
    // title styles, all bold.
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        h2 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        h3 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        h4 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.titleSmall,
        text = MaterialTheme.typography.bodySmall,
        paragraph = MaterialTheme.typography.bodySmall,
        ordered = MaterialTheme.typography.bodySmall,
        bullet = MaterialTheme.typography.bodySmall,
        list = MaterialTheme.typography.bodySmall,
        code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        inlineCode = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )

    Markdown(
        content = text,
        modifier = modifier,
        typography = typography,
    )
}
