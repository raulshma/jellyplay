package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Module-local image seams (core:ui's ImageBitmapFactory pair is internal —
 * not visible across modules, so the book reader carries its own small
 * expect/actual with the same ARGB-int → ImageBitmap bridge shape).
 */

/** Decode encoded image bytes (jpg/png/gif/bmp/webp as the platform supports) into a bitmap; null on failure. */
internal expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?

/**
 * ARGB-packed pixels → [ImageBitmap]. Android builds a `Bitmap` via
 * `createBitmap`; desktop expands to an explicit RGBA raster for Skia.
 * Only the jvmMain (PDFBox) PDF path uses this — Android's PdfRenderer
 * produces a `Bitmap` directly.
 */
internal expect fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap
