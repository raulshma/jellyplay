package com.raulshma.jellyplay.core.model.seerr

object TmdbImageUrls {
    const val BASE = "https://image.tmdb.org/t/p"
    const val POSTER_W500 = "$BASE/w500"
    const val BACKDROP_W1280 = "$BASE/w1280"
    const val PROFILE_H632 = "$BASE/h632"
    const val LOGO_W45 = "$BASE/w45"
}

private fun buildUrl(prefix: String, path: String?): String? = when {
    path.isNullOrBlank() -> null
    // Seerr backends sometimes return absolute URLs instead of bare TMDB
    // paths: the season-detail endpoint rewrites episode stills to
    // image.tmdb.org originals, and the TVDB metadata provider serves
    // artworks.thetvdb.com URLs (and empty strings for season posters).
    // Prefixing any of those with a TMDB size segment produces a broken URL,
    // and an empty string must yield null so callers' poster fallbacks kick in.
    path.startsWith("http://") || path.startsWith("https://") -> path
    else -> "$prefix$path"
}

fun buildPosterUrl(path: String?): String? = buildUrl(TmdbImageUrls.POSTER_W500, path)

fun buildBackdropUrl(path: String?): String? = buildUrl(TmdbImageUrls.BACKDROP_W1280, path)

fun buildProfileUrl(path: String?): String? = buildUrl(TmdbImageUrls.PROFILE_H632, path)

fun buildStillUrl(path: String?): String? = buildUrl(TmdbImageUrls.POSTER_W500, path)
