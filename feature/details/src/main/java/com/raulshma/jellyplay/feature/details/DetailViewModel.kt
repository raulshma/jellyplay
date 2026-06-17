package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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

    private val _detail = composeState<MediaDetail?>(null)
    val detail: androidx.compose.runtime.State<MediaDetail?> get() = _detail.asState()

    private val _seerrRecommendations = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrRecommendations: StateFlow<List<SeerrSearchItem>> = _seerrRecommendations.flow

    private val _seerrSimilar = stateFlow<List<SeerrSearchItem>>(emptyList())
    val seerrSimilar: StateFlow<List<SeerrSearchItem>> = _seerrSimilar.flow

    private val _relatedVideos = stateFlow<List<SeerrRelatedVideo>>(emptyList())
    val relatedVideos: StateFlow<List<SeerrRelatedVideo>> = _relatedVideos.flow

    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val isSeerrRecommendationsEnabled: StateFlow<Boolean> = seerrRepository.isRecommendationsEnabled()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val seerrRequestState = com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder(scope, seerrRequestDelegate)
    val seerrRequestResult: StateFlow<SeerrRequestResult?> get() = seerrRequestState.requestResult
    val radarrServers: StateFlow<List<SeerrRadarrServiceDetail>> get() = seerrRequestState.radarrServers
    val sonarrServers: StateFlow<List<SeerrSonarrServiceDetail>> get() = seerrRequestState.sonarrServers
    val isLoadingSeerrServices: StateFlow<Boolean> get() = seerrRequestState.isLoadingServices
    val seerrTvSeasons: StateFlow<List<SeerrSeason>> get() = seerrRequestState.tvSeasons

    private var loadJob: Job? = null

    private val _isLoading = composeState(false)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading.asState()
    private val _error = composeState<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error.asState()

    var seasons by composeState<List<MediaItem>>(emptyList())
        private set
    var episodes by composeState<Map<String, List<MediaItem>>>(emptyMap())
        private set
    private val episodesMap = java.util.Collections.synchronizedMap(mutableMapOf<String, List<MediaItem>>())
    var albumTracks by composeState<List<MediaItem>>(emptyList())
        private set
    var collectionItems by composeState<List<MediaItem>>(emptyList())
        private set
    var fetchedSeasonIds by composeState<Set<String>>(emptySet())
        private set
    var isDownloading by composeState(false)
        private set
    var downloadError by composeState<String?>(null)
        private set

    fun clearDownloadError() {
        downloadError = null
    }
    var isDownloadingSeries by composeState(false)
        private set

    var seriesDownloadResult by composeState<SeriesDownloadResult?>(null)
        private set

    var downloadSheetEpisodes by composeState<Map<String, List<MediaItem>>>(emptyMap())
        private set
    private val downloadSheetEpisodesMap = java.util.Collections.synchronizedMap(mutableMapOf<String, List<MediaItem>>())
    var downloadSheetLoadingSeasons by composeState<Set<String>>(emptySet())
        private set
    private var downloadSheetFetchedSeasonIds by composeState<Set<String>>(emptySet())
    var downloadedEpisodeIds by composeState<Set<String>>(emptySet())
        private set

    fun clearSeriesDownloadResult() {
        seriesDownloadResult = null
    }

    var smartPlayTarget by composeState<SmartPlayTarget?>(null)
        private set

    var selectedSubtitleIndex by composeState<Int?>(null)
        private set
    var selectedAudioIndex by composeState<Int?>(null)
        private set

    fun selectSubtitle(index: Int?) {
        selectedSubtitleIndex = index
        val itemId = _detail.value?.item?.id ?: return
        launch {
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
        launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = index,
                subtitleStreamIndex = selectedSubtitleIndex,
            )
        }
    }

    @Immutable
    data class SmartPlayTarget(
        val episode: MediaItem,
        val label: String,
        val startPositionTicks: Long,
    )

    fun getDownloadFlow(itemId: String): Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)

    fun loadItem(itemId: String) {
        loadJob?.cancel()
        loadJob = launch {
            Snapshot.withMutableSnapshot {
                _detail.value = null
                _isLoading.value = true
                _error.value = null
                seasons = emptyList()
                episodes = emptyMap()
                episodesMap.clear()
                fetchedSeasonIds = emptySet()
                collectionItems = emptyList()
                smartPlayTarget = null
                selectedSubtitleIndex = null
                selectedAudioIndex = null
                seerrDataLoaded = false
                _seerrRecommendations.set(emptyList())
                _seerrSimilar.set(emptyList())
                _relatedVideos.set(emptyList())
                isDownloading = false
                isDownloadingSeries = false
                downloadError = null
                seriesDownloadResult = null
            }
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    _detail.value = detail
                    val storedSelection = preferences.value.mediaStreamSelections[itemId]
                    selectedSubtitleIndex = storedSelection?.subtitleStreamIndex
                    selectedAudioIndex = storedSelection?.audioStreamIndex
                    if (detail.item.mediaType == MediaType.SERIES) {
                        loadSeasons(itemId)
                    } else if (detail.item.mediaType == MediaType.EPISODE && detail.item.seriesId != null) {
                        loadSeasons(detail.item.seriesId!!)
                    } else if (detail.item.mediaType == MediaType.ALBUM) {
                        loadAlbumTracks(itemId)
                    } else if (detail.item.mediaType == MediaType.COLLECTION) {
                        loadCollectionItems(itemId)
                    } else {
                        smartPlayTarget = null
                    }
                }
                .onFailure { _error.value = it.message ?: "Failed to load details" }
            _isLoading.value = false
        }
    }

    private var currentSeriesId: String? = null

    private fun loadSeasons(seriesId: String) {
        currentSeriesId = seriesId
        launch {
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
        launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    episodesMap[seasonId] = episodeList
                    episodes = episodesMap.toMap()

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
                        episodesMap[seasonId] = emptyList()
                        episodes = episodesMap.toMap()
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

    private fun loadAlbumTracks(albumId: String) {
        launch {
            mediaRepository.getAlbumTracks(albumId)
                .onSuccess { albumTracks = it }
        }
    }

    private fun loadCollectionItems(collectionId: String) {
        launch {
            mediaRepository.getCollectionItems(collectionId, limit = 100)
                .onSuccess { result -> collectionItems = result.items }
        }
    }

    fun playAlbum(startIndex: Int = 0) {
        if (albumTracks.isEmpty()) return
        val queueItems = albumTracks.map { track ->
            track.toAudioQueueItem(
                imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                albumFallback = _detail.value?.item?.name,
            )
        }
        audioPlaybackManager.playQueue(queueItems, startIndex)
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
        launch(Dispatchers.Default) {
            val allEpisodes = episodes.values.flatten()
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
                smartPlayTarget = SmartPlayTarget(
                    episode = resumeEpisode,
                    label = "Resume S${s}:E${e}",
                    startPositionTicks = resumeEpisode.playbackPositionTicks ?: 0,
                )
                return@launch
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
                return@launch
            }

            if (hasMoreSeasonsToLoad()) {
                loadNextUnfetchedSeason()
                return@launch
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
        launch(Dispatchers.Default) {
            val allEpisodes = episodes.values.flatten().sortedByPlaybackOrder()
            if (allEpisodes.isEmpty()) {
                smartPlayTarget = null
                return@launch
            }
            val currentIndex = allEpisodes.indexOfFirst { it.id == currentEpisode.id }
            if (currentIndex < 0) {
                smartPlayTarget = null
                return@launch
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
        val currentIsFavorite = _detail.value?.item?.isFavorite ?: return
        launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess {
                    _detail.value = _detail.value?.copy(
                        item = _detail.value!!.item.copy(isFavorite = !currentIsFavorite)
                    )
                }
        }
    }

    fun markPlayed() {
        val itemId = _detail.value?.item?.id ?: return
        launch {
            mediaRepository.markPlayed(itemId)
            _detail.value = _detail.value?.copy(
                item = _detail.value!!.item.copy(isPlayed = true)
            )
        }
    }

    fun markUnplayed() {
        val itemId = _detail.value?.item?.id ?: return
        launch {
            mediaRepository.markUnplayed(itemId)
            _detail.value = _detail.value?.copy(
                item = _detail.value!!.item.copy(isPlayed = false)
            )
        }
    }

    fun hideFromNextUp() {
        val item = _detail.value?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            preferencesStore.excludeSeriesFromNextUp(seriesId)
        }
    }

    fun startDownload() {
        val detail = _detail.value ?: run {
            downloadError = "Media details not loaded"
            return
        }
        val item = detail.item
        val source = detail.mediaSources.firstOrNull() ?: run {
            downloadError = "No media source available for download"
            return
        }

        launch {
            isDownloading = true
            downloadError = null
            try {
                val streamUrl = playbackRepository.getStreamUrl(item.id, source.id)
                if (streamUrl.isBlank()) {
                    downloadError = "Could not get stream URL"
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
                    downloadError = error.message ?: "Download failed"
                }
            } catch (e: Exception) {
                downloadError = e.message ?: "Download failed"
            }
            isDownloading = false
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun downloadSeries(episodeIds: Map<String, List<String>>? = null) {
        val detail = _detail.value ?: run {
            seriesDownloadResult = SeriesDownloadResult(error = "Media details not loaded")
            return
        }
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) {
            seriesDownloadResult = SeriesDownloadResult(error = "This is not a series")
            return
        }

        launch {
            isDownloadingSeries = true
            seriesDownloadResult = null
            downloadRepository.downloadSeries(item.id, episodeIds)
                .onSuccess { downloadIds ->
                    seriesDownloadResult = SeriesDownloadResult(
                        queuedCount = downloadIds.size,
                    )
                }
                .onFailure { error ->
                    seriesDownloadResult = SeriesDownloadResult(
                        error = error.message ?: "Failed to queue downloads",
                    )
                }
            isDownloadingSeries = false
        }
    }

    fun loadDownloadSheetEpisodes(seasonId: String) {
        if (seasonId in downloadSheetFetchedSeasonIds) return
        val seriesId = currentSeriesId ?: return
        downloadSheetLoadingSeasons = downloadSheetLoadingSeasons + seasonId
        launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    downloadSheetEpisodesMap[seasonId] = episodeList
                    downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()
                }
                .onFailure {
                    downloadSheetEpisodesMap[seasonId] = emptyList()
                    downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()
                }
            downloadSheetFetchedSeasonIds = downloadSheetFetchedSeasonIds + seasonId
            downloadSheetLoadingSeasons = downloadSheetLoadingSeasons - seasonId
        }
    }

    fun loadDownloadedEpisodeIds() {
        val seriesId = currentSeriesId ?: return
        launch {
            downloadedEpisodeIds = downloadRepository.getDownloadedEpisodeIdsForSeries(seriesId)
        }
    }

    fun resetDownloadSheetState() {
        downloadSheetEpisodesMap.clear()
        downloadSheetEpisodes = emptyMap()
        downloadSheetLoadingSeasons = emptySet()
        downloadSheetFetchedSeasonIds = emptySet()
        downloadedEpisodeIds = emptySet()
    }

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)

    private fun enqueueDownloadWorker(downloadId: String) {
        downloadRepository.enqueueDownload(downloadId)
    }

    private fun loadSeerrData(detail: MediaDetail, generation: Long) {
        launch {
            if (generation != seerrDataGeneration) return@launch
            _seerrRecommendations.set(emptyList())
            _seerrSimilar.set(emptyList())
            _relatedVideos.set(emptyList())

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
                    _relatedVideos.set(videosResult.getOrElse { emptyList() })
                }
            } else {
                val videosResult = tmdbApiClient.getVideos(tmdbId, mediaType == MediaType.MOVIE)
                if (generation == seerrDataGeneration) {
                    _relatedVideos.set(videosResult.getOrElse { emptyList() })
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
                        _seerrRecommendations.set(recs.results.take(20))
                        _seerrSimilar.set(similar.results.take(20))
                    }
                }
            }
        }
    }

    private var seerrDataLoaded = false
    private var seerrDataGeneration = 0L

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
