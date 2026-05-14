package com.raulshma.jellyplay.core.network.seerr

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
const val SEERR_POSTER_SIZE = "w500"
const val SEERR_BACKDROP_SIZE = "w1280"
const val SEERR_PROFILE_SIZE = "h632"

fun buildPosterUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "$TMDB_IMAGE_BASE/$SEERR_POSTER_SIZE${path}"
}

fun buildBackdropUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "$TMDB_IMAGE_BASE/$SEERR_BACKDROP_SIZE${path}"
}

fun buildProfileUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "$TMDB_IMAGE_BASE/$SEERR_PROFILE_SIZE${path}"
}
