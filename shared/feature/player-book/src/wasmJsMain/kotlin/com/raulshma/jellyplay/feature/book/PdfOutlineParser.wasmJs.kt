package com.raulshma.jellyplay.feature.book

import okio.Path

/**
 * The wasmJs actual of the PDF outline seam: no PDF back-end exists on the
 * browser target (the paged document layer — android PdfRenderer / desktop
 * PDFBox — is jvmShared/jvmMain only, and `BookDocumentOpener` has no wasm
 * binding), so the parser degrades to a permanently empty outline. Honest
 * dead weight-free shape: a paged book that somehow reached this target
 * renders with no TOC rather than crashing.
 */
actual class PdfOutlineParser {
    actual fun parse(path: Path): List<PdfOutlineNode> = emptyList()
}
