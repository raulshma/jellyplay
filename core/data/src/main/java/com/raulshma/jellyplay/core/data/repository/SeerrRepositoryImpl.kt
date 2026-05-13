package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrRepositoryImpl @Inject constructor(
    private val seerrApiClient: SeerrApiClient,
    private val seerrPreferencesStore: SeerrPreferencesStore,
) : SeerrRepository {

    private suspend fun getCredentials(): Pair<String, String>? {
        val prefs = seerrPreferencesStore.preferences.first()
        if (prefs.serverUrl.isBlank() || prefs.apiKey.isBlank()) return null
        return Pair(prefs.serverUrl, prefs.apiKey)
    }

    override suspend fun testConnection(): Result<SeerrStatusResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Server URL and API key are required"))
        return seerrApiClient.testConnection(serverUrl, apiKey)
    }

    override suspend fun search(query: String, page: Int): Result<SeerrSearchResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.search(serverUrl, apiKey, query, page)
    }

    override suspend fun getMovieDetails(tmdbId: Int): Result<SeerrMovieDetails> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getMovieDetails(serverUrl, apiKey, tmdbId)
    }

    override suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTvDetails(serverUrl, apiKey, tmdbId)
    }

    override suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return if (mediaType == "movie") {
            seerrApiClient.getMovieRatings(serverUrl, apiKey, tmdbId)
        } else {
            seerrApiClient.getTvRatings(serverUrl, apiKey, tmdbId)
        }
    }

    override suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return when (mediaType) {
            MediaType.MOVIE -> seerrApiClient.getMovieRecommendations(serverUrl, apiKey, tmdbId)
            MediaType.SERIES -> seerrApiClient.getTvRecommendations(serverUrl, apiKey, tmdbId)
            else -> Result.failure(Exception("Unsupported media type for recommendations"))
        }
    }

    override suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return when (mediaType) {
            MediaType.MOVIE -> seerrApiClient.getMovieSimilar(serverUrl, apiKey, tmdbId)
            MediaType.SERIES -> seerrApiClient.getTvSimilar(serverUrl, apiKey, tmdbId)
            else -> Result.failure(Exception("Unsupported media type for similar items"))
        }
    }

    override suspend fun requestMedia(tmdbId: Int, mediaType: String, seasons: List<Int>?): Result<SeerrMediaRequest> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.requestMedia(serverUrl, apiKey, mediaType, tmdbId, seasons = seasons)
    }

    override fun isConnected(): Flow<Boolean> = seerrPreferencesStore.isConnected

    override fun isEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.enabled }

    override fun isSearchEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.searchEnabled }

    override fun isRecommendationsEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.recommendationsEnabled }

    override fun getPreferences(): Flow<SeerrPreferences> = seerrPreferencesStore.preferences
}
