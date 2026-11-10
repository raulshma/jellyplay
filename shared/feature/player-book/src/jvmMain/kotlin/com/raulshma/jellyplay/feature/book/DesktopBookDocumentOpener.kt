package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.raulshma.jellyplay.core.model.BookFormat
import okio.Path

/**
 * Desktop book opener: CBZ → the shared ZipFile pager, PDF → the PDFBox
 * back-end. Every open failure collapses to `null` — the opener's
 * cannot-open contract.
 *
 * [rarEntryReaderFactory] is the host-supplied RAR back-end (apps/desktop
 * binds its own junrar twin — same UnRAR license note as the store adapter:
 * free to use for decompression only). Null means RAR comics are unsupported
 * in this host.
 */
class DesktopBookDocumentOpener(
    private val rarEntryReaderFactory: RarEntryReaderFactory?,
) : BookDocumentOpener {

    override suspend fun open(path: Path, format: BookFormat): BookDocument? =
        withContext(Dispatchers.IO) {
            // ImageIO has no WebP codec — desktop excludes webp entries instead of
            // showing pages that can never decode. Shared by CBZ and CBR.
            val desktopImageExtensions = ComicArchiveDocument.IMAGE_EXTENSIONS - "webp"
            when (format) {
                BookFormat.CBZ, BookFormat.CBR ->
                    ComicArchiveDocument.open(
                        path,
                        desktopImageExtensions,
                        rarEntryReaderFactory = rarEntryReaderFactory,
                    )
                BookFormat.PDF -> DesktopPdfDocument.open(path.toFile())
                BookFormat.EPUB -> null // reflowable — resolved by the epub reader upstream
            }
        }
}
