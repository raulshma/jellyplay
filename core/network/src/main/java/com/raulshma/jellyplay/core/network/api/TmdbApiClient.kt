package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.TmdbReview

interface TmdbApiClient {
    suspend fun getVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>>

    /** Reviews for a movie/series, straight from TMDB (independent of Seerr). */
    suspend fun getReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>>
}
