package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Cadence of the Seerr pending-request / current-user background poll. */
private const val POLL_INTERVAL_MS = 60_000L

class SeerrRepositoryImpl(
    private val seerrApiClient: SeerrApiClient,
    private val tmdbApiClient: TmdbApiClient,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val secureCredentialsStore: SeerrSecureCredentialsStore,
    /**
     * Identity source for the detail cache's composite keys (see
     * [SessionIdentityProvider.cacheIdentity]): a bare-String key previously
     * let the previous Jellyfin user's Seerr view survive a switch for the
     * full TTL. jvmShared DI binds [HomeSession]; wasmJs binds the
     * AtomicSessionState-backed provider.
     */
    private val sessionIdentity: SessionIdentityProvider,
    /** Registers the detail cache for wholesale clears on identity change. */
    private val sessionCacheRegistry: SessionCacheRegistry,
    /**
     * Shared application scope for the background poll loop (the
     * `@ApplicationScope` binding — same lifetime discipline as
     * `ServerIdentityStore`; never cancelled for this singleton).
     */
    private val cacheScope: CoroutineScope,
) : SeerrRepository {

    // Both fields carried @Volatile on the pre-15B JVM sources; the promotion
    // keeps it via kotlin.concurrent.Volatile (common — has a wasmJs actual;
    // the same annotation this repo already uses in commonMain,
    // e.g. WidgetDataStore).
    @Volatile
    private var cachedCredentials: SeerrCredentials? = null
    @Volatile
    private var lastCredsHash: Int = 0

    private val _currentUser = MutableStateFlow<SeerrCurrentUser?>(null)
    override val currentUser: StateFlow<SeerrCurrentUser?> = _currentUser

    private val _pendingRequestCount = MutableStateFlow(0)
    override val pendingRequestCount: StateFlow<Int> = _pendingRequestCount

    // SeerrPreferencesStore.preferences is itself a SharingStarted.Eagerly
    // StateFlow, so .value is warm from the moment the singleton is
    // materialised — no local cache layer needed here.

    private val CACHE_TTL_MS = 60_000L
    private val detailCache = TtlCache<Any>(ttlMs = CACHE_TTL_MS)

    init {
        sessionCacheRegistry.registerCaches("seerr", detailCache)
    }

    private suspend fun <T> getCached(key: String): T? {
        return detailCache.get(sessionIdentity.cacheIdentity(), key) as? T
    }

    private suspend fun putCached(key: String, value: Any) {
        detailCache.put(sessionIdentity.cacheIdentity(), key, value)
    }

    private suspend fun getCredentials(): SeerrCredentials? {
        val prefs = seerrPreferencesStore.preferences.value
        val apiKey = secureCredentialsStore.getApiKey()
        val cookie = secureCredentialsStore.getSessionCookie()
        val hash = 31 * prefs.serverUrl.hashCode() + apiKey.hashCode() + cookie.hashCode() + prefs.authMethod.hashCode()
        if (hash == lastCredsHash) {
            cachedCredentials?.let { return it }
        }
        if (prefs.serverUrl.isBlank()) return null
        val creds = when (prefs.authMethod) {
            SeerrAuthMethod.API_KEY -> {
                if (apiKey.isBlank()) return null
                SeerrCredentials.ApiKey(apiKey)
            }
            SeerrAuthMethod.JELLYFIN,
            SeerrAuthMethod.LOCAL -> {
                if (cookie.isBlank()) return null
                SeerrCredentials.SessionCookie(cookie)
            }
        }
        cachedCredentials = creds
        lastCredsHash = hash
        return creds
    }

    private fun serverUrl(): String? {
        val prefs = seerrPreferencesStore.preferences.value
        return prefs.serverUrl.ifBlank { null }
    }

    override suspend fun testConnection(): Result<SeerrStatusResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Server URL is required"))
        val credentials = getCredentials()
            ?: return Result.failure(Exception("Authentication credentials are required"))
        return seerrApiClient.testConnection(url, credentials)
    }

    override suspend fun loginJellyfin(username: String, password: String): Result<SeerrStatusResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Server URL is required"))
        seerrApiClient.loginJellyfin(url, username, password)
            .onSuccess { cookie ->
                secureCredentialsStore.setSessionCookie(cookie)
                cachedCredentials = SeerrCredentials.SessionCookie(cookie)
                lastCredsHash = 0
            }
            .onFailure { return Result.failure(it) }
        return seerrApiClient.testConnection(url, SeerrCredentials.SessionCookie(secureCredentialsStore.getSessionCookie()))
    }

    override suspend fun loginLocal(email: String, password: String): Result<SeerrStatusResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Server URL is required"))
        seerrApiClient.loginLocal(url, email, password)
            .onSuccess { cookie ->
                secureCredentialsStore.setSessionCookie(cookie)
                cachedCredentials = SeerrCredentials.SessionCookie(cookie)
                lastCredsHash = 0
            }
            .onFailure { return Result.failure(it) }
        return seerrApiClient.testConnection(url, SeerrCredentials.SessionCookie(secureCredentialsStore.getSessionCookie()))
    }

    override suspend fun testApiKeyConnection(): Result<SeerrStatusResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Server URL is required"))
        val apiKey = secureCredentialsStore.getApiKey()
        if (apiKey.isBlank()) return Result.failure(Exception("API key is required"))
        return seerrApiClient.testConnection(url, SeerrCredentials.ApiKey(apiKey))
    }

    override suspend fun search(query: String, page: Int): Result<SeerrSearchResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.search(url, credentials, query, page)
    }

    override suspend fun getMovieDetails(tmdbId: Int): Result<SeerrMovieDetails> {
        getCached<SeerrMovieDetails>("movie_details_$tmdbId")?.let { return Result.success(it) }
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getMovieDetails(url, credentials, tmdbId).also { result ->
            result.getOrNull()?.let { putCached("movie_details_$tmdbId", it) }
        }
    }

    override suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails> {
        getCached<SeerrTvDetails>("tv_details_$tmdbId")?.let { return Result.success(it) }
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTvDetails(url, credentials, tmdbId).also { result ->
            result.getOrNull()?.let { putCached("tv_details_$tmdbId", it) }
        }
    }

    override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): Result<SeerrSeasonDetail> {
        getCached<SeerrSeasonDetail>("tv_season_${tvId}_$seasonNumber")?.let { return Result.success(it) }
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTvSeasonDetails(url, credentials, tvId, seasonNumber).also { result ->
            result.getOrNull()?.let { putCached("tv_season_${tvId}_$seasonNumber", it) }
        }
    }

    override suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> {
        getCached<SeerrRatings>("ratings_${tmdbId}_$mediaType")?.let { return Result.success(it) }
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))

        return (if (mediaType == "movie") {
            seerrApiClient.getMovieRatingsCombined(url, credentials, tmdbId)
        } else {
            seerrApiClient.getTvRatings(url, credentials, tmdbId)
        }).also { result ->
            result.getOrNull()?.let { putCached("ratings_${tmdbId}_$mediaType", it) }
        }
    }

    override suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> {
        getCached<SeerrSearchResponse>("recommendations_${tmdbId}_${mediaType.name}")?.let { return Result.success(it) }
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        val typeStr = if (mediaType == MediaType.MOVIE) "movie" else "tv"
        return (when (mediaType) {
            MediaType.MOVIE -> seerrApiClient.getMovieRecommendations(url, credentials, tmdbId)
            MediaType.SERIES -> seerrApiClient.getTvRecommendations(url, credentials, tmdbId)
            else -> Result.failure(Exception("Unsupported media type for recommendations"))
        }).map { response ->
            response.copy(
                results = response.results.map { item ->
                    if (item.mediaType.isBlank()) item.copy(mediaType = typeStr) else item
                }
            )
        }.also { result ->
            result.getOrNull()?.let { putCached("recommendations_${tmdbId}_${mediaType.name}", it) }
        }
    }

    override suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse> {
        getCached<SeerrSearchResponse>("similar_${tmdbId}_${mediaType.name}")?.let { return Result.success(it) }
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        val typeStr = if (mediaType == MediaType.MOVIE) "movie" else "tv"
        return (when (mediaType) {
            MediaType.MOVIE -> seerrApiClient.getMovieSimilar(url, credentials, tmdbId)
            MediaType.SERIES -> seerrApiClient.getTvSimilar(url, credentials, tmdbId)
            else -> Result.failure(Exception("Unsupported media type for similar items"))
        }).map { response ->
            response.copy(
                results = response.results.map { item ->
                    if (item.mediaType.isBlank()) item.copy(mediaType = typeStr) else item
                }
            )
        }.also { result ->
            result.getOrNull()?.let { putCached("similar_${tmdbId}_${mediaType.name}", it) }
        }
    }

    override suspend fun getTmdbVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>> =
        tmdbApiClient.getVideos(tmdbId, mediaType)

    override suspend fun getTmdbReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>> {
        getCached<List<TmdbReview>>("tmdb_reviews_${tmdbId}_${mediaType.name}")?.let { return Result.success(it) }
        return tmdbApiClient.getReviews(tmdbId, mediaType).also { result ->
            result.getOrNull()?.let { putCached("tmdb_reviews_${tmdbId}_${mediaType.name}", it) }
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
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.requestMedia(
            url, credentials, mediaType, tmdbId,
            seasons = seasons, serverId = serverId, profileId = profileId,
            rootFolder = rootFolder, tags = tags,
        )
    }

    override suspend fun getRadarrSettings(): Result<List<SeerrRadarrSettings>> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRadarrSettings(url, credentials)
    }

    override suspend fun getSonarrSettings(): Result<List<SeerrSonarrSettings>> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getSonarrSettings(url, credentials)
    }

    override suspend fun getRadarrServiceDetail(id: Int): Result<SeerrRadarrServiceDetail> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRadarrServiceDetail(url, credentials, id)
    }

    override suspend fun getSonarrServiceDetail(id: Int): Result<SeerrSonarrServiceDetail> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getSonarrServiceDetail(url, credentials, id)
    }

    override suspend fun getServiceRadarrServers(): Result<List<SeerrServiceServer>> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceRadarrServers(url, credentials)
    }

    override suspend fun getServiceSonarrServers(): Result<List<SeerrServiceServer>> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceSonarrServers(url, credentials)
    }

    override suspend fun getServiceRadarrDetail(id: Int): Result<SeerrRadarrServiceDetail> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceRadarrDetail(url, credentials, id)
    }

    override suspend fun getServiceSonarrDetail(id: Int): Result<SeerrSonarrServiceDetail> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getServiceSonarrDetail(url, credentials, id)
    }

    override fun isConnected(): Flow<Boolean> = seerrPreferencesStore.isConnected

    override fun isEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.enabled }

    override fun isSearchEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.searchEnabled }

    override fun isRecommendationsEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.recommendationsEnabled }

    override fun isDiscoverEnabled(): Flow<Boolean> = seerrPreferencesStore.preferences.map { it.discoverEnabled }

    override fun getPreferences(): Flow<SeerrPreferences> = seerrPreferencesStore.preferences

    override suspend fun getTrending(page: Int): Result<SeerrSearchResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getTrending(url, credentials, page)
    }

    override suspend fun getDiscoverMovies(page: Int, primaryReleaseDateGte: String?): Result<SeerrSearchResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getDiscoverMovies(url, credentials, page, primaryReleaseDateGte).map { response ->
            response.copy(
                results = response.results.map { item ->
                    if (item.mediaType.isBlank()) item.copy(mediaType = "movie") else item
                }
            )
        }
    }

    override suspend fun getDiscoverTv(page: Int, firstAirDateGte: String?): Result<SeerrSearchResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getDiscoverTv(url, credentials, page, firstAirDateGte).map { response ->
            response.copy(
                results = response.results.map { item ->
                    if (item.mediaType.isBlank()) item.copy(mediaType = "tv") else item
                }
            )
        }
    }

    override suspend fun getRequests(
        take: Int,
        skip: Int,
        filter: String,
        sort: String,
        sortDirection: String,
        requestedBy: Int?,
        mediaType: String?,
        search: String?,
    ): Result<SeerrRequestListResponse> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRequests(url, credentials, take, skip, filter, sort, sortDirection, requestedBy, mediaType, search)
    }

    override suspend fun getRequest(id: Int): Result<SeerrRequestItem> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRequest(url, credentials, id)
    }

    override suspend fun approveRequest(id: Int): Result<SeerrRequestItem> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.approveRequest(url, credentials, id)
    }

    override suspend fun declineRequest(id: Int): Result<SeerrRequestItem> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.declineRequest(url, credentials, id)
    }

    override suspend fun retryRequest(id: Int): Result<SeerrRequestItem> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.retryRequest(url, credentials, id)
    }

    override suspend fun deleteRequest(id: Int): Result<Unit> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.deleteRequest(url, credentials, id)
    }

    override suspend fun deleteMedia(mediaId: Int, is4k: Boolean): Result<Unit> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.deleteMedia(url, credentials, mediaId, is4k)
    }

    override suspend fun editRequest(
        id: Int,
        mediaType: String,
        mediaId: Int,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
        seasons: List<Int>?,
    ): Result<SeerrRequestItem> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.editRequest(url, credentials, id, mediaType, mediaId, serverId, profileId, rootFolder, tags, seasons)
    }

    override suspend fun getRequestCount(): Result<SeerrRequestCount> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getRequestCount(url, credentials)
    }

    override suspend fun getCurrentUser(): Result<SeerrCurrentUser> {
        val url = serverUrl() ?: return Result.failure(Exception("Seerr not configured"))
        val credentials = getCredentials() ?: return Result.failure(Exception("Seerr not configured"))
        return seerrApiClient.getCurrentUser(url, credentials).also { result ->
            result.getOrNull()?.let { _currentUser.value = it }
        }
    }

    override fun isAdmin(): Flow<Boolean> = _currentUser.map { user ->
        user?.canManageRequests == true
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    /**
     * Starts a 60s background poll that refreshes [pendingRequestCount] and
     * [currentUser]. No-op if already running. Safe to call repeatedly.
     *
     * Polling is intentionally NOT auto-started from `init {}` — the
     * repository is a Singleton instantiated at app start, so auto-starting
     * would wake up every 60s for users who have never configured Seerr.
     * Callers (currently [com.raulshma.jellyplay.feature.requests.RequestsViewModel])
     * start polling when their UI is entered and stop it when cleared.
     *
     * The timer cadence is decoupled from the preferences collector: a `delay`
     * inside `collect {}` would serialize pref emissions and fire back-to-back
     * network calls after a burst of unrelated Seerr-pref edits, instead of
     * coalescing. The latest enabled flag is tracked reactively and the poll
     * loop runs on its own fixed cadence gated on that flag.
     */
    override fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = cacheScope.launch {
            // Track the latest enabled flag without delaying the collector.
            var enabled = false
            val prefsJob = launch {
                seerrPreferencesStore.preferences.collect { prefs ->
                    enabled = prefs.enabled
                    // Refresh immediately when Seerr is (re)enabled so the UI
                    // doesn't wait up to 60s for the first poll.
                    if (enabled) doPoll()
                }
            }
            try {
                while (isActive) {
                    kotlinx.coroutines.delay(POLL_INTERVAL_MS)
                    if (enabled) doPoll()
                }
            } finally {
                prefsJob.cancel()
            }
        }
    }

    private suspend fun doPoll() {
        getRequestCount().onSuccess { count ->
            _pendingRequestCount.value = count.pending
        }
        if (_currentUser.value == null) {
            getCurrentUser()
        }
    }

    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
