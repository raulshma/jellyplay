package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.TmdbImageUrls

const val SEERR_POSTER_SIZE = "w500"
const val SEERR_BACKDROP_SIZE = "w1280"
const val SEERR_PROFILE_SIZE = "h632"

fun buildPosterUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "${TmdbImageUrls.POSTER_W500}${path}"
}

fun buildBackdropUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "${TmdbImageUrls.BACKDROP_W1280}${path}"
}

fun buildProfileUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "${TmdbImageUrls.PROFILE_H632}${path}"
}
