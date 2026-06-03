package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private data class CacheEntry<T>(val value: T, val timestampMs: Long)

@Singleton
class SeerrRepositoryImpl @Inject constructor(
    private val seerrApiClient: SeerrApiClient,
    private val seerrPreferencesStore: SeerrPreferencesStore,
) : SeerrRepository {

    @Volatile
    private var cachedCredentials: Pair<String, String>? = null
    @Volatile
    private var lastPrefsHash: Int = 0

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cachedPrefs = seerrPreferencesStore.preferences
        .stateIn(cacheScope, SharingStarted.Eagerly, null)

    private val detailCache = ConcurrentHashMap<String, CacheEntry<Any>>()
    private val CACHE_TTL_MS = 60_000L
    private val EVICTION_INTERVAL_MS = 30_000L
    private val CACHE_MAX_ENTRIES = 50

    init {
        cacheScope.launch {
            while (isActive) {
                delay(EVICTION_INTERVAL_MS)
                evictExpired()
                if (detailCache.size > CACHE_MAX_ENTRIES) {
                    val excess = detailCache.size - CACHE_MAX_ENTRIES
                    detailCache.keys.take(excess).forEach { detailCache.remove(it) }
                }
            }
        }
    }

    private fun <T> getCached(key: String): T? {
        val entry = detailCache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > CACHE_TTL_MS) {
            detailCache.remove(key, entry)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return entry.value as T
    }

    private fun putCached(key: String, value: Any) {
        detailCache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val iter = detailCache.entries.iterator()
        while (iter.hasNext()) {
            if (now - iter.next().value.timestampMs > CACHE_TTL_MS) {
                iter.remove()
            }
        }
    }

    private suspend fun getCredentials(): Pair<String, String>? {
        val prefs = cachedPrefs.value ?: return null
        val prefsHash = 31 * prefs.serverUrl.hashCode() + prefs.apiKey.hashCode()
        if (prefsHash == lastPrefsHash) {
            cachedCredentials?.let { return it }
        }
        if (prefs.serverUrl.isBlank() || prefs.apiKey.isBlank()) return null
        val creds = Pair(prefs.serverUrl, prefs.apiKey)
        cachedCredentials = creds
        lastPrefsHash = prefsHash
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
        getCached<SeerrMovieDetails>("movie_details_$tmdbId")?.let { return Result.success(it) }
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getMovieDetails(serverUrl, apiKey, tmdbId).also { result ->
            result.getOrNull()?.let { putCached("movie_details_$tmdbId", it) }
        }
    }

    override suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails> {
        getCached<SeerrTvDetails>("tv_details_$tmdbId")?.let { return Result.success(it) }
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTvDetails(serverUrl, apiKey, tmdbId).also { result ->
            result.getOrNull()?.let { putCached("tv_details_$tmdbId", it) }
        }
    }

    override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): Result<SeerrSeasonDetail> {
        getCached<SeerrSeasonDetail>("tv_season_${tvId}_$seasonNumber")?.let { return Result.success(it) }
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTvSeasonDetails(serverUrl, apiKey, tvId, seasonNumber).also { result ->
            result.getOrNull()?.let { putCached("tv_season_${tvId}_$seasonNumber", it) }
        }
    }

    override suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> {
        getCached<SeerrRatings>("ratings_${tmdbId}_$mediaType")?.let { return Result.success(it) }
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))

        return (if (mediaType == "movie") {
            seerrApiClient.getMovieRatingsCombined(serverUrl, apiKey, tmdbId)
        } else {
            seerrApiClient.getTvRatings(serverUrl, apiKey, tmdbId)
        }).also { result ->
            result.getOrNull()?.let { putCached("ratings_${tmdbId}_$mediaType", it) }
        }
    }

    override suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> {
        getCached<SeerrSearchResponse>("recommendations_${tmdbId}_${mediaType.name}")?.let { return Result.success(it) }
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return (when (mediaType) {
            MediaType.MOVIE -> seerrApiClient.getMovieRecommendations(serverUrl, apiKey, tmdbId)
            MediaType.SERIES -> seerrApiClient.getTvRecommendations(serverUrl, apiKey, tmdbId)
            else -> Result.failure(Exception("Unsupported media type for recommendations"))
        }).also { result ->
            result.getOrNull()?.let { putCached("recommendations_${tmdbId}_${mediaType.name}", it) }
        }
    }

    override suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> {
        getCached<SeerrSearchResponse>("similar_${tmdbId}_${mediaType.name}")?.let { return Result.success(it) }
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return (when (mediaType) {
            MediaType.MOVIE -> seerrApiClient.getMovieSimilar(serverUrl, apiKey, tmdbId)
            MediaType.SERIES -> seerrApiClient.getTvSimilar(serverUrl, apiKey, tmdbId)
            else -> Result.failure(Exception("Unsupported media type for similar items"))
        }).also { result ->
            result.getOrNull()?.let { putCached("similar_${tmdbId}_${mediaType.name}", it) }
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

    override fun isDiscoverEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.discoverEnabled }

    override fun getPreferences(): Flow<SeerrPreferences> = seerrPreferencesStore.preferences

    // ── Discover endpoints ──

    override suspend fun getTrending(page: Int): Result<SeerrSearchResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTrending(serverUrl, apiKey, page)
    }

    override suspend fun getDiscoverMovies(page: Int, primaryReleaseDateGte: String?): Result<SeerrSearchResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getDiscoverMovies(serverUrl, apiKey, page, primaryReleaseDateGte)
    }

    override suspend fun getDiscoverTv(page: Int, firstAirDateGte: String?): Result<SeerrSearchResponse> {
        val (serverUrl, apiKey) = getCredentials()
            ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getDiscoverTv(serverUrl, apiKey, page, firstAirDateGte)
    }
}
