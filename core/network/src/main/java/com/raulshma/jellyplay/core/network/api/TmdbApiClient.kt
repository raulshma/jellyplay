package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo

interface TmdbApiClient {
    suspend fun getVideos(tmdbId: Int, isMovie: Boolean): Result<List<SeerrRelatedVideo>>
}
