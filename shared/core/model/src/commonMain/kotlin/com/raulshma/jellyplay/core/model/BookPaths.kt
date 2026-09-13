package com.raulshma.jellyplay.core.model

/**
 * Path surgery shared by the book pipeline. Jellyfin book items carry their
 * format only in the wire `Path`, which can arrive wrapped in a download URL
 * (query/fragment suffixes) or with either separator convention — these
 * helpers normalize that once instead of every consumer hand-rolling it
 * (format sniffing, download containers, cache file names).
 */

/**
 * This path without any URL query (`?…`) or fragment (`#…`) suffix. Both the
 * raw server path and the URL-wrapped download form flow through here, and
 * the two shapes disagree about what `#` means — a fragment to a URL, a
 * literal character to every "C# …" book on disk. URL-shaped inputs carry a
 * `scheme://` prefix or no spaces at all (URLs percent-encode spaces;
 * filesystem names keep them); anything else is a raw path and passes
 * through untouched.
 */
fun String.stripUrlSuffixes(): String {
    if ('?' !in this && '#' !in this) return this
    val isUrlShaped = "://" in this || ' ' !in this
    return if (isUrlShaped) substringBefore('?').substringBefore('#') else this
}

/** The last path segment, accepting both `/` and `\` separators. */
fun String.lastPathSegment(): String = substringAfterLast('/').substringAfterLast('\\')

/**
 * Lowercased file extension of this possibly URL-wrapped path — the last
 * segment's extension, query/fragment stripped; "" when there is none.
 */
fun String.pathExtension(): String =
    lastPathSegment().stripUrlSuffixes().substringAfterLast('.', "").lowercase()

/**
 * Extract the filename from an HTTP `Content-Disposition` header value
 * (Jellyfin's book download serves
 * `attachment; filename="Flat Book.epub"; filename*=UTF-8''Flat%20Book.epub`).
 * The RFC 5987 `filename*=` form wins over the legacy `filename=`; the
 * `charset''` prefix is dropped and percent-escapes decoded. Null when the
 * header carries neither form.
 */
fun parseContentDispositionFileName(header: String?): String? {
    if (header.isNullOrBlank()) return null
    val extended = header.substringAfter("filename*=", "").substringBefore(';').trim()
    if (extended.isNotEmpty()) {
        val encoded = extended.substringAfter("''", extended)
        return percentDecode(encoded).takeIf { it.isNotEmpty() }
    }
    val legacy = header.substringAfter("filename=", "").let {
        // Guard against matching the `filename*=` key: only reach the legacy
        // branch when the star form is absent entirely.
        if (it == header) "" else it
    }.substringBefore(';').trim().trim('"')
    return legacy.takeIf { it.isNotEmpty() }
}

/** RFC 3986 percent-decoding over ASCII escapes ('+' passes through untouched). */
private fun percentDecode(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch == '%' && i + 2 < value.length) {
            val hi = value[i + 1].digitToIntOrNull(16)
            val lo = value[i + 2].digitToIntOrNull(16)
            if (hi != null && lo != null) {
                append(((hi shl 4) or lo).toChar())
                i += 3
                continue
            }
        }
        append(ch)
        i++
    }
}
