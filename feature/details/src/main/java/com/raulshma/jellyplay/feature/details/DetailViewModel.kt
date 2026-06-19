package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.data.playback.toAudioQueueItem
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
    private val offlineModeManager: OfflineModeManager,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val audioPlaybackManager: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager,
    private val tmdbApiClient: TmdbApiClient,
) : JellyPlayViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    // Single source of truth for detail-screen state. All mutations
    // funnel through [_uiState.update]; the [uiState] aggregator additionally
    // folds in [SeerrRequestStateHolder] state via combine() so observers see a
    // single atomic snapshot.
    private val _uiState = MutableStateFlow(DetailUiState())

    private val seerrRequestState = com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder(scope, seerrRequestDelegate)

    val uiState: StateFlow<DetailUiState> = combine(
        _uiState,
        seerrRequestState.requestResult,
        seerrRequestState.radarrServers,
        seerrRequestState.sonarrServers,
        seerrRequestState.isLoadingServices,
    ) { primary, requestResult, radarrServers, sonarrServers, isLoadingServices ->
        primary.copy(
            seerrRequestResult = requestResult,
            seerrRadarrServers = radarrServers,
            seerrSonarrServers = sonarrServers,
            isLoadingSeerrServices = isLoadingServices,
        )
    }.let { intermediate ->
        combine(
            intermediate,
            seerrRequestState.tvSeasons,
            seerrRepository.isConnected(),
            seerrRepository.isRecommendationsEnabled(),
        ) { partial, tvSeasons, isSeerrConnected, isSeerrRecommendationsEnabled ->
            partial.copy(
                seerrTvSeasons = tvSeasons,
                isSeerrConnected = isSeerrConnected,
                isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    // ---- Backward-compatible property accessors ----
    // Each projects a single field out of [_uiState] (or [uiState] for seerr
    // delegate state) so existing call sites keep working without churn. New
    // call sites should prefer `viewModel.uiState.collectAsStateWithLifecycle()`
    // for atomic snapshots.
    val detail: StateFlow<MediaDetail?> = _uiState
        .map { it.detail }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
    val isLoading: StateFlow<Boolean> = _uiState
        .map { it.isLoading }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
    val error: StateFlow<String?> = _uiState
        .map { it.error }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
    val seerrRecommendations: StateFlow<List<SeerrSearchItem>> = _uiState
        .map { it.seerrRecommendations }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val seerrSimilar: StateFlow<List<SeerrSearchItem>> = _uiState
        .map { it.seerrSimilar }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val relatedVideos: StateFlow<List<SeerrRelatedVideo>> = _uiState
        .map { it.relatedVideos }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isSeerrConnected: StateFlow<Boolean> = uiState
        .map { it.isSeerrConnected }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
    val isSeerrRecommendationsEnabled: StateFlow<Boolean> = uiState
        .map { it.isSeerrRecommendationsEnabled }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
    val seerrRequestResult: StateFlow<SeerrRequestResult?> get() = seerrRequestState.requestResult
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> get() = seerrRequestState.radarrServers
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> get() = seerrRequestState.sonarrServers
    val isLoadingSeerrServices: StateFlow<Boolean> get() = seerrRequestState.isLoadingServices
    val seerrTvSeasons: StateFlow<List<SeerrSeason>> get() = seerrRequestState.tvSeasons

    // Direct (non-observable) readers for property-style access at call sites
    // that previously used `var ... by composeState(...)`. Each reads the
    // current snapshot from [_uiState].
    val seasons: List<MediaItem> get() = _uiState.value.seasons
    val episodes: Map<String, List<MediaItem>> get() = _uiState.value.episodes
    val albumTracks: List<MediaItem> get() = _uiState.value.albumTracks
    val collectionItems: List<MediaItem> get() = _uiState.value.collectionItems
    val fetchedSeasonIds: Set<String> get() = _uiState.value.fetchedSeasonIds
    val isDownloading: Boolean get() = _uiState.value.isDownloading
    val downloadError: String? get() = _uiState.value.downloadError
    val isDownloadingSeries: Boolean get() = _uiState.value.isDownloadingSeries
    val seriesDownloadResult: SeriesDownloadResult? get() = _uiState.value.seriesDownloadResult
    val downloadSheetEpisodes: Map<String, List<MediaItem>> get() = _uiState.value.downloadSheetEpisodes
    val downloadSheetLoadingSeasons: Set<String> get() = _uiState.value.downloadSheetLoadingSeasons
    val downloadedEpisodeIds: Set<String> get() = _uiState.value.downloadedEpisodeIds
    val smartPlayTarget: DetailUiState.SmartPlayTarget? get() = _uiState.value.smartPlayTarget
    val selectedSubtitleIndex: Int? get() = _uiState.value.selectedSubtitleIndex
    val selectedAudioIndex: Int? get() = _uiState.value.selectedAudioIndex

    // Internal caches (not observable UI state).
    private val episodesMap = java.util.Collections.synchronizedMap(mutableMapOf<String, List<MediaItem>>())
    private val downloadSheetEpisodesMap = java.util.Collections.synchronizedMap(mutableMapOf<String, List<MediaItem>>())
    private var downloadSheetFetchedSeasonIds: Set<String> = emptySet()
    private var loadJob: Job? = null
    private var currentSeriesId: String? = null
    private var seerrDataLoaded = false
    private var seerrDataGeneration = 0L

    fun clearDownloadError() {
        _uiState.update { it.copy(downloadError = null) }
    }

    fun clearSeriesDownloadResult() {
        _uiState.update { it.copy(seriesDownloadResult = null) }
    }

    fun selectSubtitle(index: Int?) {
        _uiState.update { it.copy(selectedSubtitleIndex = index) }
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                subtitleStreamIndex = index,
                audioStreamIndex = _uiState.value.selectedAudioIndex,
            )
        }
    }

    fun selectAudio(index: Int?) {
        _uiState.update { it.copy(selectedAudioIndex = index) }
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = index,
                subtitleStreamIndex = _uiState.value.selectedSubtitleIndex,
            )
        }
    }

    fun getDownloadFlow(itemId: String): Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)

    fun loadItem(itemId: String) {
        loadJob?.cancel()
        loadJob = launch {
            // Single atomic reset — collapses what used to be ~14 separate
            // composeState/stateFlow mutations into one emission so observers
            // see one recomposition, not fourteen.
            _uiState.update {
                it.copy(
                    detail = null,
                    isLoading = true,
                    error = null,
                    seasons = emptyList(),
                    episodes = emptyMap(),
                    fetchedSeasonIds = emptySet(),
                    collectionItems = emptyList(),
                    smartPlayTarget = null,
                    selectedSubtitleIndex = null,
                    selectedAudioIndex = null,
                    seerrRecommendations = emptyList(),
                    seerrSimilar = emptyList(),
                    relatedVideos = emptyList(),
                    isDownloading = false,
                    isDownloadingSeries = false,
                    downloadError = null,
                    seriesDownloadResult = null,
                )
            }
            episodesMap.clear()
            seerrDataLoaded = false
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    val storedSelection = preferences.value.mediaStreamSelections[itemId]
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            selectedSubtitleIndex = storedSelection?.subtitleStreamIndex,
                            selectedAudioIndex = storedSelection?.audioStreamIndex,
                        )
                    }
                    val itemType = detail.item.mediaType
                    if (itemType == MediaType.SERIES) {
                        loadSeasons(itemId)
                    } else if (itemType == MediaType.EPISODE && detail.item.seriesId != null) {
                        loadSeasons(detail.item.seriesId!!)
                    } else if (itemType == MediaType.ALBUM) {
                        loadAlbumTracks(itemId)
                    } else if (itemType == MediaType.COLLECTION) {
                        loadCollectionItems(itemId)
                    } else {
                        _uiState.update { state -> state.copy(smartPlayTarget = null) }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message ?: "Failed to load details") }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadSeasons(seriesId: String) {
        currentSeriesId = seriesId
        launch {
            mediaRepository.getSeasons(seriesId)
                .onSuccess { seasonList ->
                    _uiState.update { it.copy(seasons = seasonList) }
                    if (seasonList.isNotEmpty()) {
                        loadEpisodes(seriesId, seasonList.first().id)
                    }
                }
        }
    }

    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_uiState.value.fetchedSeasonIds.contains(seasonId)) return
        loadEpisodes(seriesId, seasonId)
    }

    private fun loadEpisodes(seriesId: String, seasonId: String) {
        launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    episodesMap[seasonId] = episodeList
                    _uiState.update { it.copy(episodes = episodesMap.toMap()) }

                    if (episodeList.isEmpty()) {
                        val seasons = _uiState.value.seasons
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
                    if (!_uiState.value.episodes.containsKey(seasonId)) {
                        episodesMap[seasonId] = emptyList()
                        _uiState.update { it.copy(episodes = episodesMap.toMap()) }
                    }

                    val seasons = _uiState.value.seasons
                    val seasonIndex = seasons.indexOfFirst { it.id == seasonId }
                    if (seasonIndex >= 0 && seasonIndex < seasons.size - 1) {
                        val nextSeasonId = seasons[seasonIndex + 1].id
                        if (!_uiState.value.fetchedSeasonIds.contains(nextSeasonId)) {
                            loadEpisodes(seriesId, nextSeasonId)
                        } else {
                            maybeComputeSmartPlayTarget()
                        }
                    } else {
                        maybeComputeSmartPlayTarget()
                    }
                }
            _uiState.update { it.copy(fetchedSeasonIds = it.fetchedSeasonIds + seasonId) }
        }
    }

    private fun loadAlbumTracks(albumId: String) {
        launch {
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { tracks -> _uiState.update { it.copy(albumTracks = tracks) } }
        }
    }

    private fun loadCollectionItems(collectionId: String) {
        launch {
            mediaRepository.getCollectionItems(collectionId, limit = 100)
                .onSuccess { result -> _uiState.update { it.copy(collectionItems = result.items) } }
        }
    }

    fun playAlbum(startIndex: Int = 0) {
        val tracks = _uiState.value.albumTracks
        if (tracks.isEmpty()) return
        val queueItems = tracks.map { track ->
            track.toAudioQueueItem(
                imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                albumFallback = _uiState.value.detail?.item?.name,
            )
        }
        audioPlaybackManager.playQueue(queueItems, startIndex)
    }

    private fun maybeComputeSmartPlayTarget() {
        val item = _uiState.value.detail?.item ?: return
        when (item.mediaType) {
            MediaType.SERIES -> computeSeriesSmartPlayTarget()
            MediaType.EPISODE -> computeEpisodeSmartPlayTarget(item)
            else -> _uiState.update { it.copy(smartPlayTarget = null) }
        }
    }

    private fun computeSeriesSmartPlayTarget() {
        launch(Dispatchers.Default) {
            val allEpisodes = _uiState.value.episodes.values.flatten()
            if (allEpisodes.isEmpty()) {
                if (hasMoreSeasonsToLoad()) {
                    loadNextUnfetchedSeason()
                }
                return@launch
            }
            val sorted = allEpisodes.sortedByPlaybackOrder()

            val resumeEpisode = sorted.firstOrNull { it.hasResumeProgress() }
            if (resumeEpisode != null) {
                val s = resumeEpisode.seasonNumber ?: 1
                val e = resumeEpisode.episodeNumber ?: resumeEpisode.indexNumber ?: 1
                _uiState.update {
                    it.copy(
                        smartPlayTarget = DetailUiState.SmartPlayTarget(
                            episode = resumeEpisode,
                            label = "Resume S${s}:E${e}",
                            startPositionTicks = resumeEpisode.playbackPositionTicks ?: 0,
                        )
                    )
                }
                return@launch
            }

            val nextEpisode = sorted.firstOrNull { !it.isPlayed }
            if (nextEpisode != null) {
                val s = nextEpisode.seasonNumber ?: 1
                val e = nextEpisode.episodeNumber ?: nextEpisode.indexNumber ?: 1
                val hasWatchedBefore = sorted
                    .takeWhile { it.id != nextEpisode.id }
                    .any { it.isPlayed || (it.playbackPositionTicks ?: 0L) > 0L }
                val label = if (hasWatchedBefore) "Next Up S${s}:E${e}" else "Play S${s}:E${e}"
                _uiState.update {
                    it.copy(
                        smartPlayTarget = DetailUiState.SmartPlayTarget(
                            episode = nextEpisode,
                            label = label,
                            startPositionTicks = 0,
                        )
                    )
                }
                return@launch
            }

            if (hasMoreSeasonsToLoad()) {
                loadNextUnfetchedSeason()
                return@launch
            }

            val first = sorted.first()
            val s = first.seasonNumber ?: 1
            val e = first.episodeNumber ?: first.indexNumber ?: 1
            _uiState.update {
                it.copy(
                    smartPlayTarget = DetailUiState.SmartPlayTarget(
                        episode = first,
                        label = "Replay S${s}:E${e}",
                        startPositionTicks = 0,
                    )
                )
            }
        }
    }

    private fun hasMoreSeasonsToLoad(): Boolean {
        val state = _uiState.value
        return state.seasons.any { season -> !state.fetchedSeasonIds.contains(season.id) }
    }

    private fun loadNextUnfetchedSeason() {
        val seriesId = currentSeriesId ?: return
        val state = _uiState.value
        val nextSeason = state.seasons.firstOrNull { season -> !state.fetchedSeasonIds.contains(season.id) }
        if (nextSeason != null) {
            loadEpisodes(seriesId, nextSeason.id)
        } else {
            _uiState.update { it.copy(smartPlayTarget = null) }
        }
    }

    private fun computeEpisodeSmartPlayTarget(currentEpisode: MediaItem) {
        launch(Dispatchers.Default) {
            val allEpisodes = _uiState.value.episodes.values.flatten().sortedByPlaybackOrder()
            if (allEpisodes.isEmpty()) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            val currentIndex = allEpisodes.indexOfFirst { it.id == currentEpisode.id }
            if (currentIndex < 0) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            val s = currentEpisode.seasonNumber ?: 1
            val e = currentEpisode.episodeNumber ?: currentEpisode.indexNumber ?: 1
            val hasProgress = (currentEpisode.playbackPositionTicks ?: 0) > 0 && !currentEpisode.isPlayed
            val label = if (hasProgress) "Resume S${s}:E${e}" else "Play S${s}:E${e}"
            _uiState.update {
                it.copy(
                    smartPlayTarget = DetailUiState.SmartPlayTarget(
                        episode = currentEpisode,
                        label = label,
                        startPositionTicks = currentEpisode.playbackPositionTicks ?: 0,
                    )
                )
            }
        }
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
        val detail = _uiState.value.detail ?: return
        val itemId = detail.item.id
        val currentIsFavorite = detail.item.isFavorite
        launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail?.copy(
                                item = state.detail.item.copy(isFavorite = !currentIsFavorite)
                            )
                        )
                    }
                }
        }
    }

    fun markPlayed() {
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            mediaRepository.markPlayed(itemId)
            _uiState.update { state ->
                state.copy(
                    detail = state.detail?.copy(
                        item = state.detail.item.copy(isPlayed = true)
                    )
                )
            }
        }
    }

    fun markUnplayed() {
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            mediaRepository.markUnplayed(itemId)
            _uiState.update { state ->
                state.copy(
                    detail = state.detail?.copy(
                        item = state.detail.item.copy(isPlayed = false)
                    )
                )
            }
        }
    }

    fun hideFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            preferencesStore.excludeSeriesFromNextUp(seriesId)
        }
    }

    fun startDownload() {
        val detail = _uiState.value.detail ?: run {
            _uiState.update { it.copy(downloadError = "Media details not loaded") }
            return
        }
        val item = detail.item
        val source = detail.mediaSources.firstOrNull() ?: run {
            _uiState.update { it.copy(downloadError = "No media source available for download") }
            return
        }

        launch {
            _uiState.update { it.copy(isDownloading = true, downloadError = null) }
            try {
                val streamUrl = playbackRepository.getStreamUrl(item.id, source.id)
                if (streamUrl.isBlank()) {
                    _uiState.update { it.copy(downloadError = "Could not get stream URL", isDownloading = false) }
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
                    if (downloadItem.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                        enqueueDownloadWorker(downloadItem.id)
                        try {
                            val backdropUrl = playbackRepository.getBackdropUrl(item.id, maxWidth = 1280)
                            downloadRepository.saveOfflineMediaItem(item, imageUrl, backdropUrl)
                        } catch (_: Exception) {
                        }
                        source.trickplayInfo?.let { info ->
                            launch {
                                downloadRepository.downloadTrickplayData(item.id, info, downloadItem.downloadPath)
                            }
                        }
                        // Bundle external subtitles + intro/outro segments for offline use.
                        launch {
                            try {
                                downloadRepository.downloadExternalSubtitles(
                                    item.id, source.id, source.mediaStreams, downloadItem.downloadPath,
                                )
                            } catch (_: Exception) {
                            }
                        }
                        launch {
                            try {
                                downloadRepository.downloadMediaSegments(item.id, downloadItem.downloadPath)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(downloadError = error.message ?: "Download failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(downloadError = e.message ?: "Download failed") }
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun downloadSeries(episodeIds: Map<String, List<String>>? = null) {
        val detail = _uiState.value.detail ?: run {
            _uiState.update { it.copy(seriesDownloadResult = SeriesDownloadResult(error = "Media details not loaded")) }
            return
        }
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) {
            _uiState.update { it.copy(seriesDownloadResult = SeriesDownloadResult(error = "This is not a series")) }
            return
        }

        launch {
            _uiState.update { it.copy(isDownloadingSeries = true, seriesDownloadResult = null) }
            downloadRepository.downloadSeries(item.id, episodeIds)
                .onSuccess { downloadIds ->
                    _uiState.update {
                        it.copy(
                            seriesDownloadResult = SeriesDownloadResult(queuedCount = downloadIds.size),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            seriesDownloadResult = SeriesDownloadResult(error = error.message ?: "Failed to queue downloads"),
                        )
                    }
                }
            _uiState.update { it.copy(isDownloadingSeries = false) }
        }
    }

    fun loadDownloadSheetEpisodes(seasonId: String) {
        if (seasonId in downloadSheetFetchedSeasonIds) return
        val seriesId = currentSeriesId ?: return
        _uiState.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons + seasonId) }
        launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    downloadSheetEpisodesMap[seasonId] = episodeList
                    _uiState.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
                }
                .onFailure {
                    downloadSheetEpisodesMap[seasonId] = emptyList()
                    _uiState.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
                }
            downloadSheetFetchedSeasonIds = downloadSheetFetchedSeasonIds + seasonId
            _uiState.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons - seasonId) }
        }
    }

    fun loadDownloadedEpisodeIds() {
        val seriesId = currentSeriesId ?: return
        launch {
            val ids = downloadRepository.getDownloadedEpisodeIdsForSeries(seriesId)
            _uiState.update { it.copy(downloadedEpisodeIds = ids) }
        }
    }

    fun resetDownloadSheetState() {
        downloadSheetEpisodesMap.clear()
        downloadSheetFetchedSeasonIds = emptySet()
        _uiState.update {
            it.copy(
                downloadSheetEpisodes = emptyMap(),
                downloadSheetLoadingSeasons = emptySet(),
                downloadedEpisodeIds = emptySet(),
            )
        }
    }

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    private fun enqueueDownloadWorker(downloadId: String) {
        downloadRepository.enqueueDownload(downloadId)
    }

    private fun loadSeerrData(detail: MediaDetail, generation: Long) {
        launch {
            if (generation != seerrDataGeneration) return@launch
            _uiState.update {
                it.copy(
                    seerrRecommendations = emptyList(),
                    seerrSimilar = emptyList(),
                    relatedVideos = emptyList(),
                )
            }

            if (offlineModeManager.networkStatus.value == NetworkStatus.Local) return@launch

            val mediaType = detail.item.mediaType
            if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return@launch

            val tmdbId = resolveTmdbId(detail)
            if (tmdbId == null) return@launch

            val connected = try { seerrRepository.isConnected().first() } catch (_: Exception) { false }

            if (generation != seerrDataGeneration) return@launch
            // 1. Fetch related videos (trailers)
            if (connected) {
                val videosResult = if (mediaType == MediaType.MOVIE) {
                    seerrRepository.getMovieDetails(tmdbId).map { it.relatedVideos }
                } else {
                    seerrRepository.getTvDetails(tmdbId).map { it.relatedVideos }
                }
                if (generation == seerrDataGeneration) {
                    val videos = videosResult.getOrElse { emptyList() }
                    _uiState.update { it.copy(relatedVideos = videos) }
                }
            } else {
                val videosResult = tmdbApiClient.getVideos(tmdbId, mediaType == MediaType.MOVIE)
                if (generation == seerrDataGeneration) {
                    val videos = videosResult.getOrElse { emptyList() }
                    _uiState.update { it.copy(relatedVideos = videos) }
                }
            }

            // 2. Fetch recommendations and similar if enabled
            val enabled = try { seerrRepository.isRecommendationsEnabled().first() } catch (_: Exception) { false }
            if (connected && enabled && generation == seerrDataGeneration) {
                coroutineScope {
                    val recsDeferred = async {
                        seerrRepository.getRecommendations(tmdbId, mediaType)
                            .getOrElse { com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse() }
                    }
                    val similarDeferred = async {
                        seerrRepository.getSimilar(tmdbId, mediaType)
                            .getOrElse { com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse() }
                    }
                    val recs = recsDeferred.await()
                    val similar = similarDeferred.await()
                    if (generation == seerrDataGeneration) {
                        _uiState.update {
                            it.copy(
                                seerrRecommendations = recs.results.take(20),
                                seerrSimilar = similar.results.take(20),
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadSeerrDataIfNeeded(detail: MediaDetail) {
        if (seerrDataLoaded) return
        seerrDataLoaded = true
        val generation = ++seerrDataGeneration
        loadSeerrData(detail, generation)
    }

    private fun resolveTmdbId(detail: MediaDetail): Int? {
        val providerIds = detail.providerIds
        providerIds["tmdb"]?.toIntOrNull()?.let { return it }
        providerIds["tmdbid"]?.toIntOrNull()?.let { return it }

        for (url in detail.externalUrls) {
            if (url.url.contains("themoviedb.org") || url.url.contains("themoviedb")) {
                val regex = Regex("""/(\d+)(?:$|/|\?)""")
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
    ) = seerrRequestState.requestMedia(item, seasons, serverId, profileId, rootFolder, tags)

    fun loadSeerrServiceDetails(mediaType: String) = seerrRequestState.loadServiceDetails(mediaType)

    fun loadSeerrTvSeasons(tmdbId: Int) = seerrRequestState.loadTvSeasons(tmdbId)

    fun clearSeerrRequestResult() = seerrRequestState.clearRequestResult()

    fun prefetchSeerrDetails(tmdbId: Int, mediaType: String, onDone: () -> Unit) =
        seerrRequestState.prefetchDetails(tmdbId, mediaType, onDone)
}

@Immutable
data class SeriesDownloadResult(
    val queuedCount: Int = 0,
    val error: String? = null,
)
