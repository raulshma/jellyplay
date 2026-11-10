package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.raulshma.jellyplay.core.model.BookFormat
import okio.Path

/**
 * Android book opener: CBZ → the shared ZipFile pager, PDF → the platform
 * PdfRenderer back-end. Every open failure (corrupt zip, password PDF, missing
 * file) collapses to `null` — the opener's cannot-open contract — so no
 * exception ever escapes into composition.
 *
 * [rarEntryReaderFactory] rides the distribution seam: bound (junrar) on the
 * store line, null on fdroid — where RAR comics are unsupported by
 * construction because junrar's UnRAR license is non-free there. See
 * [RarEntryReaderFactory].
 */
class AndroidBookDocumentOpener(
    private val rarEntryReaderFactory: RarEntryReaderFactory?,
) : BookDocumentOpener {

    override suspend fun open(path: Path, format: BookFormat): BookDocument? =
        withContext(Dispatchers.IO) {
            when (format) {
                BookFormat.CBZ, BookFormat.CBR ->
                    ComicArchiveDocument.open(path, rarEntryReaderFactory = rarEntryReaderFactory)
                BookFormat.PDF -> AndroidPdfDocument.open(path.toFile())
                // Reflowable never reaches the page-based opener; the epub
                // reader resolves it upstream — null keeps `when` total.
                BookFormat.EPUB -> null
            }
        }
}
