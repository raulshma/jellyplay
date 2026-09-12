package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.raulshma.jellyplay.core.model.BookFormat
import okio.Path

/**
 * Desktop book opener: CBZ → the shared ZipFile pager, PDF → the PDFBox
 * back-end. Every open failure collapses to `null` — the opener's
 * cannot-open contract.
 */
class DesktopBookDocumentOpener : BookDocumentOpener {

    override suspend fun open(path: Path, format: BookFormat): BookDocument? =
        withContext(Dispatchers.IO) {
            // ImageIO has no WebP codec — desktop excludes webp entries instead of
            // showing pages that can never decode. Shared by CBZ and CBR.
            val desktopImageExtensions = ComicArchiveDocument.IMAGE_EXTENSIONS - "webp"
            when (format) {
                BookFormat.CBZ, BookFormat.CBR ->
                    ComicArchiveDocument.open(path, desktopImageExtensions)
                BookFormat.PDF -> DesktopPdfDocument.open(path.toFile())
                BookFormat.EPUB -> null // reflowable — resolved by the epub reader upstream
            }
        }
}
