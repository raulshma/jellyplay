package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The wasmJs actuals of the book image seams — honest degradation:
 *
 * - [decodeImageBytes] returns null (the same contract shape the jvmShared
 *   CBZ pager already treats as "failed page" — the UI keeps the placeholder).
 *   A real browser decode (`Image` element + canvas readback) is feasible but
 *   dead weight until the reader has a web document/opener story: the CBZ
 *   pager (java.util.zip) and both PDF back-ends are jvmShared/jvmMain, and
 *   `BookDocumentOpener` has no wasm binding for now.
 *
 * - [argbPixelsToImageBitmap] throws: its only caller is the jvmMain PDFBox
 *   PDF raster step, which is unreachable on wasm.
 */
internal actual fun decodeImageBytes(bytes: ByteArray, maxEdgePx: Int): ImageBitmap? = null

internal actual fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    error("PDF rasterization is a desktop-only path; unreachable on wasm")
