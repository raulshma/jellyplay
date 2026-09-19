package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The book formats jellyplay can open. [CBZ], [CBR] and [PDF] are PAGE-based:
 * jellyfin-web / Fladder persist their position as
 * `pageIndex (0-based) × 10,000 ticks` in UserData.PlaybackPositionTicks.
 * [EPUB] is the only [isReflowable] format — its progress is not
 * page-addressable and must never be persisted as a page index. Still-unknown
 * reflowable extensions (mobi/azw…) deliberately do not resolve here.
 */
@Immutable
@Serializable
enum class BookFormat(val extension: String, val isReflowable: Boolean) {
    CBZ("cbz", false),
    PDF("pdf", false),
    CBR("cbr", false),
    EPUB("epub", true),
    ;

    companion object {
        /**
         * Case-insensitive extension match; null when the path is null/blank
         * or its extension is not a [BookFormat]. Books carry no
         * MediaSources — the file's format is only knowable from the item's
         * `Path`, which can arrive wrapped in a download URL with a query
         * string, so [pathExtension] strips those suffixes before matching.
         */
        fun fromPath(path: String?): BookFormat? {
            if (path.isNullOrBlank()) return null
            val extension = path.pathExtension()
            if (extension.isEmpty() || '/' in extension || '\\' in extension) return null
            return entries.firstOrNull { it.extension == extension }
        }

        /**
         * Fallback when the item's `Path` yielded nothing (server withheld or
         * blanked it — measured on misconfigured/path-substituted servers):
         * the book DOWNLOAD response's Content-Type and Content-Disposition
         * filename (Jellyfin serves both for every book, verified 10.9–12).
         * The filename wins when present — it is the server's own file name —
         * then the MIME table covers the filename-less case. Null when
         * neither maps to a [BookFormat] (mobi/azw3 land here: known names,
         * deliberately unsupported).
         */
        fun fromDownloadMetadata(contentType: String?, fileName: String?): BookFormat? {
            fromPath(fileName)?.let { return it }
            val mime = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
            return when (mime) {
                "application/epub+zip" -> EPUB
                "application/pdf" -> PDF
                "application/vnd.comicbook+zip", "application/x-cbz", "application/cbz" -> CBZ
                "application/vnd.comicbook-rar", "application/x-cbr",
                "application/vnd.rar", "application/x-rar-compressed",
                -> CBR
                else -> null
            }
        }
    }
}
