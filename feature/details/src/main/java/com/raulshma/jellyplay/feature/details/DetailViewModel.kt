package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
    private val seerrRepository: SeerrRepository,
) : ViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _detail = mutableStateOf<MediaDetail?>(null)
    val detail: androidx.compose.runtime.State<MediaDetail?> get() = _detail

    // ── Seerr Integration State ──
    private val _seerrRecommendations = MutableStateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrRecommendations: StateFlow<List<SeerrSearchItem>> = _seerrRecommendations.asStateFlow()

    private val _seerrSimilar = MutableStateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrSimilar: StateFlow<List<SeerrSearchItem>> = _seerrSimilar.asStateFlow()

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isSeerrRecommendationsEnabled: StateFlow<Boolean> = seerrRepository.isRecommendationsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _seerrRequestResult = MutableStateFlow<SeerrRequestResult?>(null)
    val seerrRequestResult: StateFlow<SeerrRequestResult?> = _seerrRequestResult.asStateFlow()

    // Service details for request dialog
    private val _radarrServers = MutableStateFlow<List<SeerrRadarrServiceDetail>>(emptyList())
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> = _radarrServers.asStateFlow()

    private val _sonarrServers = MutableStateFlow<List<SeerrSonarrServiceDetail>>(emptyList())
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> = _sonarrServers.asStateFlow()

    private val _isLoadingSeerrServices = MutableStateFlow(false)
    val isLoadingSeerrServices: StateFlow<Boolean> = _isLoadingSeerrServices.asStateFlow()

    // Seerr TV seasons for the request dialog (fetched on-demand per item)
    private val _seerrTvSeasons = MutableStateFlow<List<SeerrSeason>>(emptyList())
    val seerrTvSeasons: StateFlow<List<SeerrSeason>> = _seerrTvSeasons.asStateFlow()

    private val _isLoading = mutableStateOf(false)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading
    private val _error = mutableStateOf<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error

    var seasons by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var episodes by mutableStateOf<Map<String, List<MediaItem>>>(emptyMap())
        private set
    // Tracks season IDs where a fetch was attempted (success or failure)
    // so the UI knows when to stop showing the loading skeleton.
    var fetchedSeasonIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var isDownloading by mutableStateOf(false)
        private set

    var smartPlayTarget by mutableStateOf<SmartPlayTarget?>(null)
        private set

    var selectedSubtitleIndex by mutableStateOf<Int?>(null)
        private set
    var selectedAudioIndex by mutableStateOf<Int?>(null)
        private set

    fun selectSubtitle(index: Int?) {
        selectedSubtitleIndex = index
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                subtitleStreamIndex = index,
                audioStreamIndex = selectedAudioIndex,
            )
        }
    }

    fun selectAudio(index: Int?) {
        selectedAudioIndex = index
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = index,
                subtitleStreamIndex = selectedSubtitleIndex,
            )
        }
    }

    data class SmartPlayTarget(
        val episode: MediaItem,
        val label: String,
        val startPositionTicks: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeDownload: Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        _detail.let { detailState ->
            detailState.value?.item?.id?.let { itemId ->
                downloadRepository.getDownloadByMediaItemIdFlow(itemId)
            } ?: flowOf(null)
        }

    fun getDownloadFlow(itemId: String): Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // Reset season/episode state on fresh load
            seasons = emptyList()
            episodes = emptyMap()
            fetchedSeasonIds = emptySet()
            smartPlayTarget = null
            selectedSubtitleIndex = null
            selectedAudioIndex = null
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    _detail.value = detail
                    val streams = detail.mediaSources.firstOrNull()?.mediaStreams.orEmpty()
                    val storedSelections = preferences.value.mediaStreamSelections
                    val storedSelection = storedSelections[itemId]
                    val hasStoredSelection = storedSelections.containsKey(itemId)

                    selectedSubtitleIndex = if (hasStoredSelection) {
                        storedSelection?.subtitleStreamIndex
                    } else {
                        streams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.SUBTITLE && it.isDefault }?.index
                    }
                    selectedAudioIndex = if (hasStoredSelection) {
                        storedSelection?.audioStreamIndex
                    } else {
                        streams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.AUDIO && it.isDefault }?.index
                    }

                    viewModelScope.launch {
                        preferencesStore.setMediaStreamSelection(
                            itemId = itemId,
                            audioStreamIndex = selectedAudioIndex,
                            subtitleStreamIndex = selectedSubtitleIndex,
                        )
                    }
                    if (detail.item.mediaType == MediaType.SERIES) {
                        loadSeasons(itemId)
                    } else if (detail.item.mediaType == MediaType.EPISODE && detail.item.seriesId != null) {
                        loadSeasons(detail.item.seriesId!!)
                    } else {
                        smartPlayTarget = null
                    }

                    // Seerr data will be loaded on-demand when user scrolls to that section
                    // loadSeerrData(detail)
                }
                .onFailure { _error.value = it.message ?: "Failed to load details" }
            _isLoading.value = false
        }
    }

    private var currentSeriesId: String? = null

    private fun loadSeasons(seriesId: String) {
        currentSeriesId = seriesId
        viewModelScope.launch {
            mediaRepository.getSeasons(seriesId)
                .onSuccess { seasonList ->
                    seasons = seasonList
                    if (seasonList.isNotEmpty()) {
                        loadEpisodes(seriesId, seasonList.first().id)
                    }
                }
        }
    }

    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (fetchedSeasonIds.contains(seasonId)) return
        loadEpisodes(seriesId, seasonId)
    }

    private fun loadEpisodes(seriesId: String, seasonId: String) {
        viewModelScope.launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    episodes = episodes.toMutableMap().apply {
                        this[seasonId] = episodeList
                    }
                    
                    if (episodeList.isEmpty()) {
                        val seasonIndex = seasons.indexOfFirst { it.id == seasonId }
                        if (seasonIndex >= 0 && seasonIndex < seasons.size - 1) {
                            val nextSeasonId = seasons[seasonIndex + 1].id
                            loadEpisodes(seriesId, nextSeasonId)
                        } else {
                            maybeComputeSmartPlayTarget()
                        }
                    } else {
                        maybeComputeSmartPlayTarget()
                    }
                }
                .onFailure {
                    if (!episodes.containsKey(seasonId)) {
                        episodes = episodes.toMutableMap().apply {
                            this[seasonId] = emptyList()
                        }
                    }
                    
                    val seasonIndex = seasons.indexOfFirst { it.id == seasonId }
                    if (seasonIndex >= 0 && seasonIndex < seasons.size - 1) {
                        val nextSeasonId = seasons[seasonIndex + 1].id
                        if (!fetchedSeasonIds.contains(nextSeasonId)) {
                            loadEpisodes(seriesId, nextSeasonId)
                        } else {
                            maybeComputeSmartPlayTarget()
                        }
                    } else {
                        maybeComputeSmartPlayTarget()
                    }
                }
            fetchedSeasonIds = fetchedSeasonIds + seasonId
        }
    }

    private fun maybeComputeSmartPlayTarget() {
        val item = _detail.value?.item ?: return
        when (item.mediaType) {
            MediaType.SERIES -> computeSeriesSmartPlayTarget()
            MediaType.EPISODE -> computeEpisodeSmartPlayTarget(item)
            else -> smartPlayTarget = null
        }
    }

    private fun computeSeriesSmartPlayTarget() {
        val allEpisodes = episodes.values.flatten()
        if (allEpisodes.isEmpty()) {
            if (hasMoreSeasonsToLoad()) {
                loadNextUnfetchedSeason()
            } else {
                smartPlayTarget = null
            }
            return
        }
        val sorted = allEpisodes.sortedByPlaybackOrder()

        val resumeEpisode = sorted.firstOrNull { it.hasResumeProgress() }
        if (resumeEpisode != null) {
            val s = resumeEpisode.seasonNumber ?: 1
            val e = resumeEpisode.episodeNumber ?: resumeEpisode.indexNumber ?: 1
            smartPlayTarget = SmartPlayTarget(
                episode = resumeEpisode,
                label = "Resume S${s}:E${e}",
                startPositionTicks = resumeEpisode.playbackPositionTicks ?: 0,
            )
            return
        }

        val nextEpisode = sorted.firstOrNull { !it.isPlayed }
        if (nextEpisode != null) {
            val s = nextEpisode.seasonNumber ?: 1
            val e = nextEpisode.episodeNumber ?: nextEpisode.indexNumber ?: 1
            val hasWatchedBefore = sorted
                .takeWhile { it.id != nextEpisode.id }
                .any { it.isPlayed || (it.playbackPositionTicks ?: 0L) > 0L }
            val label = if (hasWatchedBefore) {
                "Next Up S${s}:E${e}"
            } else {
                "Play S${s}:E${e}"
            }
            smartPlayTarget = SmartPlayTarget(
                episode = nextEpisode,
                label = label,
                startPositionTicks = 0,
            )
            return
        }

        if (hasMoreSeasonsToLoad()) {
            loadNextUnfetchedSeason()
            return
        }

        val first = sorted.first()
        val s = first.seasonNumber ?: 1
        val e = first.episodeNumber ?: first.indexNumber ?: 1
        smartPlayTarget = SmartPlayTarget(
            episode = first,
            label = "Replay S${s}:E${e}",
            startPositionTicks = 0,
        )
    }

    private fun hasMoreSeasonsToLoad(): Boolean {
        return seasons.any { season -> !fetchedSeasonIds.contains(season.id) }
    }

    private fun loadNextUnfetchedSeason() {
        val seriesId = currentSeriesId ?: return
        val nextSeason = seasons.firstOrNull { season -> !fetchedSeasonIds.contains(season.id) }
        if (nextSeason != null) {
            loadEpisodes(seriesId, nextSeason.id)
        } else {
            smartPlayTarget = null
        }
    }

    private fun computeEpisodeSmartPlayTarget(currentEpisode: MediaItem) {
        val allEpisodes = episodes.values.flatten().sortedByPlaybackOrder()
        if (allEpisodes.isEmpty()) {
            smartPlayTarget = null
            return
        }
        val currentIndex = allEpisodes.indexOfFirst { it.id == currentEpisode.id }
        if (currentIndex < 0) {
            smartPlayTarget = null
            return
        }
        val s = currentEpisode.seasonNumber ?: 1
        val e = currentEpisode.episodeNumber ?: currentEpisode.indexNumber ?: 1
        val hasProgress = (currentEpisode.playbackPositionTicks ?: 0) > 0 && !currentEpisode.isPlayed
        smartPlayTarget = SmartPlayTarget(
            episode = currentEpisode,
            label = if (hasProgress) "Resume S${s}:E${e}" else "Play S${s}:E${e}",
            startPositionTicks = currentEpisode.playbackPositionTicks ?: 0,
        )
    }

    private fun MediaItem.hasResumeProgress(): Boolean =
        (playbackPositionTicks ?: 0L) > 0L && !isPlayed

    private fun List<MediaItem>.sortedByPlaybackOrder(): List<MediaItem> =
        sortedWith(
            compareBy<MediaItem>(
                { it.seasonNumber ?: Int.MAX_VALUE },
                { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
                { it.name },
            )
        )

    fun toggleFavorite() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess { loadItem(itemId) }
        }
    }

    fun markPlayed() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markPlayed(itemId)
            loadItem(itemId)
        }
    }

    fun markUnplayed() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markUnplayed(itemId)
            loadItem(itemId)
        }
    }

    fun startDownload() {
        val detail = _detail.value ?: return
        val item = detail.item
        val source = detail.mediaSources.firstOrNull() ?: return

        viewModelScope.launch {
            isDownloading = true
            try {
                val streamUrl = playbackRepository.getStreamUrl(item.id, source.id)
                if (streamUrl.isBlank()) {
                    isDownloading = false
                    return@launch
                }
                val imageUrl = playbackRepository.getImageUrl(item.id, maxWidth = 300)
                val mediaType = when (item.mediaType) {
                    MediaType.AUDIO, MediaType.MUSIC -> MediaType.AUDIO.name
                    else -> item.mediaType.name
                }

                downloadRepository.startDownload(
                    mediaItemId = item.id,
                    name = item.name,
                    mediaType = mediaType,
                    mediaSourceId = source.id,
                    downloadUrl = streamUrl,
                    imageUrl = imageUrl,
                    imageBlurHash = item.blurHashes.primary,
                ).onSuccess { downloadItem ->
                    enqueueDownloadWorker(downloadItem.id)
                }
            } catch (_: Exception) {
                // Download initiation failed silently
            }
            isDownloading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    private fun enqueueDownloadWorker(downloadId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DownloadWorker.UNIQUE_WORK_PREFIX}$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    // ── Seerr Integration ──

    private fun loadSeerrData(detail: MediaDetail) {
        viewModelScope.launch {
            // Reset
            _seerrRecommendations.value = emptyList()
            _seerrSimilar.value = emptyList()

            val connected = try { seerrRepository.isConnected().first() } catch (_: Exception) { false }
            val enabled = try { seerrRepository.isRecommendationsEnabled().first() } catch (_: Exception) { false }
            if (!connected || !enabled) return@launch

            val mediaType = detail.item.mediaType
            if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return@launch

            // Resolve TMDB ID from external URLs
            val tmdbId = resolveTmdbId(detail)
            if (tmdbId == null) return@launch

            // Fetch recommendations and similar in parallel
            coroutineScope {
                val recsDeferred = async {
                    seerrRepository.getRecommendations(tmdbId, mediaType)
                        .getOrElse { com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse() }
                }
                val similarDeferred = async {
                    seerrRepository.getSimilar(tmdbId, mediaType)
                        .getOrElse { com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse() }
                }
                _seerrRecommendations.value = recsDeferred.await().results.take(20)
                _seerrSimilar.value = similarDeferred.await().results.take(20)
            }
        }
    }

    private var seerrDataLoaded = false

    fun loadSeerrDataIfNeeded(detail: MediaDetail) {
        if (seerrDataLoaded) return
        seerrDataLoaded = true
        loadSeerrData(detail)
    }

    private fun resolveTmdbId(detail: MediaDetail): Int? {
        // Try provider IDs first (most reliable)
        val providerIds = detail.providerIds
        providerIds["tmdb"]?.toIntOrNull()?.let { return it }
        providerIds["tmdbid"]?.toIntOrNull()?.let { return it }

        // Fallback: try to find TMDB ID from external URLs
        for (url in detail.externalUrls) {
            if (url.url.contains("themoviedb.org") || url.url.contains("themoviedb")) {
                val regex = Regex("""/(\\d+)(?:$|/|\?)""")
                val match = regex.find(url.url)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull()
                }
            }
        }
        return null
    }

    fun getSeerrPosterUrl(posterPath: String?): String? =
        posterPath?.let { buildPosterUrl(it) }

    fun requestSeerrMedia(
        item: SeerrSearchItem,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ) {
        viewModelScope.launch {
            _seerrRequestResult.value = SeerrRequestResult(isLoading = true)
            seerrRepository.requestMedia(
                mediaType = item.mediaType,
                tmdbId = item.id,
                seasons = seasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
                tags = tags,
            ).onSuccess {
                _seerrRequestResult.value = SeerrRequestResult(success = true)
            }.onFailure {
                _seerrRequestResult.value = SeerrRequestResult(error = it.message ?: "Request failed")
            }
        }
    }

    /**
     * Fetches service details (Radarr/Sonarr) for the request dialog.
     * Uses /service/ endpoints matching the Seerr web UI flow.
     */
    fun loadSeerrServiceDetails(mediaType: String) {
        viewModelScope.launch {
            _isLoadingSeerrServices.value = true
            try {
                if (mediaType == "movie") {
                    seerrRepository.getServiceRadarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceRadarrDetail(server.id).getOrNull()
                        }
                        _radarrServers.value = details
                    }
                } else {
                    seerrRepository.getServiceSonarrServers().onSuccess { servers ->
                        val details = servers.mapNotNull { server ->
                            seerrRepository.getServiceSonarrDetail(server.id).getOrNull()
                        }
                        _sonarrServers.value = details
                    }
                }
            } finally {
                _isLoadingSeerrServices.value = false
            }
        }
    }

    /**
     * Fetches TV details on-demand from Seerr to get season data for the request dialog.
     */
    fun loadSeerrTvSeasons(tmdbId: Int) {
        viewModelScope.launch {
            _seerrTvSeasons.value = emptyList()
            seerrRepository.getTvDetails(tmdbId).onSuccess { details ->
                _seerrTvSeasons.value = details.seasons.filter { it.seasonNumber > 0 }
            }
        }
    }

    fun clearSeerrRequestResult() {
        _seerrRequestResult.value = null
    }

    /**
     * Pre-fetches Seerr detail data for a related item so the destination
     * Seerr detail screen loads instantly.
     */
    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                if (mediaType == "movie") {
                    seerrRepository.getMovieDetails(tmdbId)
                } else {
                    seerrRepository.getTvDetails(tmdbId)
                }
                val type = if (mediaType == "movie") MediaType.MOVIE else MediaType.SERIES
                launch { seerrRepository.getRatings(tmdbId, mediaType) }
                launch { seerrRepository.getRecommendations(tmdbId, type) }
                launch { seerrRepository.getSimilar(tmdbId, type) }
            } catch (_: Exception) {
                // Detail screen will retry
            }
            onDone()
        }
    }
}

data class SeerrRequestResult(
    val isLoading: Boolean = false,
    val success: Boolean? = null,
    val error: String? = null,
)
