package com.raulshma.jellyplay.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The repo's public bounded-decode + pixel-bridge image seam, shared by
 * core:ui's own consumers (BlurHash) and feature modules (the book reader's
 * comic/PDF pagers). Previously feature code carried parallel expect/actual
 * copies of this pair because these declarations were `internal` — six
 * actuals bridging one concept. Visibility is now public; this is a stable
 * surface, not an accident.
 */

/**
 * Turns ARGB-packed pixels into a platform [ImageBitmap]. Android builds a
 * `Bitmap` via `createBitmap`; desktop installs the pixels into a Skia bitmap.
 */
expect fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap

/**
 * Decode encoded image bytes (jpg/png/gif/bmp/webp as the platform supports)
 * into a bitmap; null on failure. The decoded bitmap's longest edge stays
 * within [maxEdgePx] (power-of-two subsampling on Android, post-decode
 * downscale on desktop) — archive scans without it decode at native size,
 * where a single page costs ~100 MB. The wasmJs actual returns null (honest
 * degradation: callers treat it as "failed page" and keep their placeholder).
 */
expect fun decodeImageBytes(bytes: ByteArray, maxEdgePx: Int): ImageBitmap?
