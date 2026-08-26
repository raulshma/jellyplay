package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders GitHub-flavoured Markdown (release notes, plugin changelogs).
 *
 * The renderer engine sits behind the [MarkdownBody] seam: android + desktop
 * keep the `multiplatform-markdown-renderer` M3 pipeline verbatim, wasm falls
 * back to a styled plain text block. That dependency's 0.43.0 wasm klibs are
 * built with Kotlin 2.4 and are silently skipped by this repo's 2.3.21 klib
 * loader (and its graph evicts kotlin-stdlib to 2.4) — see spike w-10C class
 * C — so it cannot enter commonMain while the toolchain is on 2.3.x.
 *
 * Public signature preserved so call sites need no changes.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    MarkdownBody(text = text, modifier = modifier)
}

/**
 * Platform markdown rendering body behind [MarkdownText]: full GFM fidelity
 * on JVM targets; plain styled text on wasm.
 */
@Composable
internal expect fun MarkdownBody(text: String, modifier: Modifier)
