package com.raulshma.jellyplay.core.model.seerr

object TmdbImageUrls {
    const val BASE = "https://image.tmdb.org/t/p"
    const val POSTER_W500 = "$BASE/w500"
    const val BACKDROP_W1280 = "$BASE/w1280"
    const val PROFILE_H632 = "$BASE/h632"
    const val LOGO_W45 = "$BASE/w45"
}

private fun buildUrl(prefix: String, path: String?): String? =
    if (path.isNullOrBlank()) null else "$prefix$path"

fun buildPosterUrl(path: String?): String? = buildUrl(TmdbImageUrls.POSTER_W500, path)

fun buildBackdropUrl(path: String?): String? = buildUrl(TmdbImageUrls.BACKDROP_W1280, path)

fun buildProfileUrl(path: String?): String? = buildUrl(TmdbImageUrls.PROFILE_H632, path)
