package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*

interface SeerrApiClient {

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
    ): Result<SeerrStatusResponse>

    suspend fun search(
        baseUrl: String,
        apiKey: String,
        query: String,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getMovieDetails(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrMovieDetails>

    suspend fun getTvDetails(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrTvDetails>

    suspend fun getMovieRatings(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings>

    suspend fun getTvRatings(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings>

    suspend fun getMovieRatingsCombined(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings>

    suspend fun getMovieRecommendations(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getMovieSimilar(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getTvRecommendations(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getTvSimilar(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun requestMedia(
        baseUrl: String,
        apiKey: String,
        mediaType: String,
        mediaId: Int,
        tvdbId: Int? = null,
        seasons: List<Int>? = null,
    ): Result<SeerrMediaRequest>

    suspend fun getRadarrSettings(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrRadarrSettings>>

    suspend fun getSonarrSettings(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrSonarrSettings>>
}
