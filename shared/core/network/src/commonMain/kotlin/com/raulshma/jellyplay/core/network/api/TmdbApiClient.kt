package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.TmdbDetails
import com.raulshma.jellyplay.core.model.seerr.TmdbReview

interface TmdbApiClient {
    suspend fun getVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>>

    /** Reviews for a movie/series, straight from TMDB (independent of Seerr). */
    suspend fun getReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>>

    /**
     * Details for a movie/series, straight from TMDB (`/3/{movie|tv}/{id}`) —
     * the vote aggregate + overview: the ratings strip's no-plugin,
     * no-Seerr source. Null vote fields mean the item has no votes yet.
     */
    suspend fun getDetails(tmdbId: Int, mediaType: MediaType): Result<TmdbDetails>
}
