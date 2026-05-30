package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem

fun ratingToSizeFraction(voteAverage: Float?): Float {
    if (voteAverage == null) return 1.0f
    return when {
        voteAverage >= 9.2f -> 1.2f
        voteAverage >= 8.5f -> 1.1f
        voteAverage >= 7.5f -> 1.0f
        else -> 0.9f
    }
}
