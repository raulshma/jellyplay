package com.raulshma.jellyplay.core.model

/**
 * Path surgery shared by the book pipeline. Jellyfin book items carry their
 * format only in the wire `Path`, which can arrive wrapped in a download URL
 * (query/fragment suffixes) or with either separator convention — these
 * helpers normalize that once instead of every consumer hand-rolling it
 * (format sniffing, download containers, cache file names).
 */

/** This path without any URL query (`?…`) or fragment (`#…`) suffix. */
fun String.stripUrlSuffixes(): String = substringBefore('?').substringBefore('#')

/** The last path segment, accepting both `/` and `\` separators. */
fun String.lastPathSegment(): String = substringAfterLast('/').substringAfterLast('\\')

/**
 * Lowercased file extension of this possibly URL-wrapped path — the last
 * segment's extension, query/fragment stripped; "" when there is none.
 */
fun String.pathExtension(): String =
    lastPathSegment().stripUrlSuffixes().substringAfterLast('.', "").lowercase()
