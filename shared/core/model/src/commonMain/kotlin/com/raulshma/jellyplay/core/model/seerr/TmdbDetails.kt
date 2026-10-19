package com.raulshma.jellyplay.core.model.seerr

/**
 * The TMDB `/details` slice the detail ratings strip reads: the public
 * vote aggregate plus the overview TMDB hands back on the same call. This is
 * the no-plugin ratings source — TMDB serves it directly, independent of both
 * the JellyPlay plugin and a Seerr connection.
 */
data class TmdbDetails(
    /** TMDB's 0..10 community vote average, null when the item has no votes yet. */
    val voteAverage: Double? = null,
    /** Number of votes behind [voteAverage], null when the item has no votes yet. */
    val voteCount: Int? = null,
    /** TMDB's overview, when trivially available on the same response. */
    val overview: String? = null,
)
