package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/**
 * Wasm actual of the markdown seam: an honest plain-text fallback. Markup is
 * shown as-is (single Text block, monospace for stability across headings,
 * fences, and inline tokens); no heading/list/link/code styling or clickable
 * links. DELIBERATE CUT while this repo stays on Kotlin 2.3.x — mikepenz
 * multiplatform-markdown-renderer 0.43.0 publishes only Kotlin-2.4-built wasm
 * klibs which our klib loader skips (and its graph evicts our stdlib; see
 * spike w-10C class C). Revisit when the toolchain moves to 2.4+.
 */
@Composable
internal actual fun MarkdownBody(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}
