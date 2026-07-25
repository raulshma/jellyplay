package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaDetail

/**
 * Deep module: resolves a TMDB numeric id for a media item, for cross-linking
 * into Seerr. Previously this pure logic lived as a private method on the
 * 1343-LOC [DetailViewModel], where its five branches could only be exercised
 * through the VM's public surface — forcing the test to reach in via
 * `java.reflect`. Extracted to a top-level function so the resolution order has
 * a home and a direct (reflection-free) test surface.
 *
 * Resolution order:
 *   1. `tmdb` provider id
 *   2. `tmdbid` provider id
 *   3. The first themoviedb.org external URL whose path contains a numeric id
 */
private val TMDB_ID_REGEX = Regex("""/(\d+)(?:$|/|\?)""")

internal fun resolveTmdbId(detail: MediaDetail): Int? {
    val providerIds = detail.providerIds
    providerIds["tmdb"]?.toIntOrNull()?.let { return it }
    providerIds["tmdbid"]?.toIntOrNull()?.let { return it }

    for (url in detail.externalUrls) {
        if (url.url.contains("themoviedb.org") || url.url.contains("themoviedb")) {
            val match = TMDB_ID_REGEX.find(url.url)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
    }
    return null
}
