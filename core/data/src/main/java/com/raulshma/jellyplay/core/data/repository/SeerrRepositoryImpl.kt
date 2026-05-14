package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrRepositoryImpl @Inject constructor(
    private val seerrApiClient: SeerrApiClient,
    private val seerrPreferencesStore: SeerrPreferencesStore,
) : SeerrRepository {

    private val cachedCredentials = AtomicReference<Pair<String, String>?>(null)

    private suspend fun getCredentials(): Pair<String, String>? {
        cachedCredentials.get()?.let { return it }
        val prefs = seerrPreferencesStore.preferences.first()
        if (prefs.serverUrl.isBlank() || prefs.apiKey.isBlank()) return null
        val creds = Pair(prefs.serverUrl, prefs.apiKey)
        cachedCredentials.set(creds)
        return creds
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
            seerrApiClient.getMovieRatingsCombined(serverUrl, apiKey, tmdbId)
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

    override suspend fun requestMedia(
        tmdbId: Int,
        mediaType: String,
        seasons: List<Int>?,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
    ): Result<SeerrMediaRequest> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.requestMedia(
            serverUrl, apiKey, mediaType, tmdbId,
            seasons = seasons, serverId = serverId, profileId = profileId,
            rootFolder = rootFolder, tags = tags,
        )
    }

    override suspend fun getRadarrSettings(): Result<List<SeerrRadarrSettings>> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRadarrSettings(serverUrl, apiKey)
    }

    override suspend fun getSonarrSettings(): Result<List<SeerrSonarrSettings>> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getSonarrSettings(serverUrl, apiKey)
    }

    override suspend fun getRadarrServiceDetail(id: Int): Result<SeerrRadarrServiceDetail> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRadarrServiceDetail(serverUrl, apiKey, id)
    }

    override suspend fun getSonarrServiceDetail(id: Int): Result<SeerrSonarrServiceDetail> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getSonarrServiceDetail(serverUrl, apiKey, id)
    }

    // ── /service/ endpoints ──

    override suspend fun getServiceRadarrServers(): Result<List<SeerrServiceServer>> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceRadarrServers(serverUrl, apiKey)
    }

    override suspend fun getServiceSonarrServers(): Result<List<SeerrServiceServer>> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceSonarrServers(serverUrl, apiKey)
    }

    override suspend fun getServiceRadarrDetail(id: Int): Result<SeerrRadarrServiceDetail> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceRadarrDetail(serverUrl, apiKey, id)
    }

    override suspend fun getServiceSonarrDetail(id: Int): Result<SeerrSonarrServiceDetail> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceSonarrDetail(serverUrl, apiKey, id)
    }

    override fun isConnected(): Flow<Boolean> = seerrPreferencesStore.isConnected

    override fun isEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.enabled }

    override fun isSearchEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.searchEnabled }

    override fun isRecommendationsEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.recommendationsEnabled }

    override fun getPreferences(): Flow<SeerrPreferences> = seerrPreferencesStore.preferences
}
