package com.raulshma.jellyplay.core.ui.image

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * A full-bleed, intentionally soft background rendered from a single [blurHash]
 * string. BlurHash decodes to a tiny bitmap (~[decodeSize]²) which is then
 * upscaled across the whole screen by [ContentScale.Crop] — the low source
 * resolution is the blur effect, so no GPU `RenderEffect` is needed.
 *
 * Decoding runs off the main thread and is memoised by [BlurHashCache], so
 * repeatedly recomposing with the same hash (e.g. during hero rotation back to
 * a previously shown item) is free.
 *
 * This is the only public entry point that can render a blur hash outside of
 * `:core:ui` — the underlying [BlurHashImage] / [BlurHashDecoder] are module
 * internal. Callers layer their own scrim (tint/alpha) on top.
 *
 * @param blurHash the BlurHash string to render. Nothing is drawn while this
 *  is blank or fails to decode.
 * @param decodeSize edge length (px) of the decoded source bitmap. Larger is
 *  sharper but costlier; 48 stays cheap even on a 4K TV while remaining soft
 *  enough to read as a backdrop.
 */
@Composable
fun BlurHashBackdrop(
    blurHash: String,
    modifier: Modifier = Modifier,
    decodeSize: Int = 48,
) {
    BlurHashImage(
        blurHash = blurHash,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        decodeWidth = decodeSize,
        decodeHeight = decodeSize,
    )
}
