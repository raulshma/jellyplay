package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import coil3.PlatformContext

/**
 * Web half of the dominant-color seam: no classifier pipeline exists yet, so
 * returns null exactly like the desktop precedent — callers already render
 * their fallback color (verified at every rememberDominantColor call site).
 * A wasm pixel-classifier lands with the web image polish pass.
 */
internal actual suspend fun extractDominantColor(context: PlatformContext, imageUrl: String): Color? = null
