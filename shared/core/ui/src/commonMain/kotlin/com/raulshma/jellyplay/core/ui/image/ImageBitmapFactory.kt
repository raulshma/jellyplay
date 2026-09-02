package com.raulshma.jellyplay.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Turns ARGB-packed pixels into a platform [ImageBitmap]. Android builds a
 * `Bitmap` via `createBitmap`; desktop installs the pixels into a Skia bitmap.
 */
internal expect fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap
