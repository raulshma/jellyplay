package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
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

private val TMDB_ID_REGEX = Regex("""/(\d+)(?:$|/|\?)""")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
    private val offlineModeManager: OfflineModeManager,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val audioPlaybackManager: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager,
    private val themeMusicPlayer: com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer,
    private val tmdbApiClient: TmdbApiClient,
    private val arrRepository: ArrRepository,
) : JellyPlayViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    // Single source of truth for detail-screen state. All mutations
    // funnel through [_uiState.update]; the [uiState] aggregator additionally
    // folds in [SeerrRequestStateHolder] state via combine() so observers see a
    // single atomic snapshot.
    private val _uiState = MutableStateFlow(DetailUiState())

    /**
     * Whether the "Delete & Re-download" action should be shown. True iff:
     * - The DIRECT_ARR_INTEGRATION experimental flag is enabled, AND
     * - The current item is a MOVIE or EPISODE (series-level re-download is
     *   intentionally out of scope — too destructive), AND
     * - The item has the provider id the *arr lookup needs (tmdb for movies,
     *   tvdb for episodes), AND
     * - A relevant *arr server is resolved (Radarr for movies, Sonarr for
     *   episodes).
     *
     * Server resolution is deferred: the flag/type/provider-id checks run in
     * the combine (cheap, pure), and the (potentially blocking) server resolve
     * only fires once those pass — avoiding a network-resolution call on every
     * unrelated `_uiState`/preferences emission.
     */
    val canRedownload: StateFlow<Boolean> = combine(
        _uiState.map { it.detail?.item },
        _uiState.map { it.seriesProviderIds },
        preferencesStore.preferences.map { it.isExperimentalEnabled(ExperimentalFeature.DIRECT_ARR_INTEGRATION) },
    ) { item, _, flagEnabled ->
        if (!flagEnabled || item == null) return@combine false
        val isEligibleType = item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.EPISODE
        if (!isEligibleType) return@combine false
        // Cheap provider-id check first — short-circuits before any server work.
        if (!hasRequiredProviderId(item)) return@combine false
        // Only resolve servers once the cheap checks pass. Best-effort: a
        // resolve failure simply hides the action rather than throwing.
        val kind = if (item.mediaType == MediaType.MOVIE) ArrServiceKind.RADARR else ArrServiceKind.SONARR
        val summary = arrRepository.resolveServers().getOrDefault(com.raulshma.jellyplay.core.model.arr.ArrServiceSummary())
        val hasServer = if (kind == ArrServiceKind.RADARR) summary.radarrServers.isNotEmpty() else summary.sonarrServers.isNotEmpty()
        hasServer
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Movies need a tmdb id (Radarr resolves tmdbId → internal id); episodes
     * need the *series* tvdb id (Sonarr resolves series tvdb → series → episode).
     * For episodes, [DetailUiState.seriesProviderIds] holds the parent series'
     * provider ids, which is what Sonarr's findSeriesByTvdb expects. The
     * episode-level [MediaDetail.providerIds] holds the episode's own tvdb id,
     * which is different and won't match the Sonarr series lookup.
     * Provider ids are lowercased by the Jellyfin mapper.
     */
    private fun hasRequiredProviderId(item: MediaItem): Boolean {
        return if (item.mediaType == MediaType.MOVIE) {
            val providerIds = _uiState.value.detail?.providerIds.orEmpty()
            providerIds["tmdb"]?.toIntOrNull() != null
        } else {
            // For episodes, use the parent series' tvdb id (from seriesProviderIds),
            // not the episode's own tvdb id.
            val seriesIds = _uiState.value.seriesProviderIds
            seriesIds["tvdb"]?.toIntOrNull() != null
        }
    }

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
    val isSeerrConnected: StateFlow<Boolean> = seerrRepository.isConnected()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
    val isSeerrRecommendationsEnabled: StateFlow<Boolean> = seerrRepository.isRecommendationsEnabled()
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
    val relatedItems: List<MediaItem> get() = _uiState.value.relatedItems
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
    private var currentItemId: String? = null
    private var currentSeriesId: String? = null
    private var seerrDataLoaded = false
    private var seerrDataGeneration = 0L

    fun clearDownloadError() {
        _uiState.update { it.copy(downloadError = null) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
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
        // Record the item we're loading synchronously so that a stale
        // loadSeerrDataIfNeeded() call (from a freshly-composed screen still
        // observing the previous item's detail via the shared ViewModel) can be
        // rejected before it loads the wrong item's trailers/videos.
        currentItemId = itemId
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
                    relatedItems = emptyList(),
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
                    seriesProviderIds = emptyMap(),
                )
            }
            episodesMap.clear()
            seerrDataLoaded = false
            // Bump the seerr generation so any in-flight trailer/video/recommendation
            // fetch from the *previous* item is invalidated and cannot write its stale
            // results onto this item's screen (the VM is shared across detail navigations).
            seerrDataGeneration++
            // Clear download-sheet caches too, since the same VM instance is reused.
            downloadSheetEpisodesMap.clear()
            downloadSheetFetchedSeasonIds = emptySet()
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
                        // For episodes: load the parent series' seasons so the
                        // episode row + smart-play target resolve. The series'
                        // provider IDs (for the redownload feature) are read from
                        // the seasons call's parent if available; we avoid a
                        // redundant full getMediaDetail(seriesId) round-trip by
                        // fetching the series detail concurrently and non-blocking
                        // — it only feeds the opt-in *arr redownload path, so it
                        // must never gate the Play button.
                        val seriesId = detail.item.seriesId!!
                        loadSeasons(seriesId)
                        launch {
                            mediaRepository.getMediaDetail(seriesId).onSuccess { seriesDetail ->
                                _uiState.update { state ->
                                    state.copy(seriesProviderIds = seriesDetail.providerIds)
                                }
                            }
                        }
                    } else if (itemType == MediaType.ALBUM) {
                        loadAlbumTracks(itemId)
                    } else if (itemType == MediaType.COLLECTION) {
                        loadCollectionItems(itemId)
                    } else {
                        _uiState.update { state -> state.copy(smartPlayTarget = null) }
                    }
                    val themeSourceId = detail.item.seriesId ?: itemId
                    themeMusicPlayer.playThemeFor(themeSourceId)

                    // Fetch similar/related items concurrently and
                    // non-blocking so the core detail (title, poster, cast,
                    // streams) renders immediately. The result lands in
                    // [DetailUiState.relatedItems] and the "More like this"
                    // section appears when it arrives.
                    launch {
                        mediaRepository.getSimilarItems(itemId, limit = 12)
                            .onSuccess { items ->
                                _uiState.update {
                                    it.copy(relatedItems = items.filter { related -> related.id != itemId })
                                }
                            }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message ?: context.getString(R.string.detail_error_load_failed)) }
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
                        // Fetch ALL seasons' episodes in parallel up-front.
                        // Previously this fetched only the first season and
                        // recursed serially through empty seasons (one network
                        // round-trip at a time) before the Play button label
                        // could resolve. Parallel fetch collapses N serial
                        // round-trips into one concurrent batch; smart-play
                        // is computed once all land.
                        seasonList.forEach { season ->
                            loadEpisodes(seriesId, season.id)
                        }
                    }
                }
        }
    }

    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_uiState.value.fetchedSeasonIds.contains(seasonId)) return
        loadEpisodes(seriesId, seasonId)
    }

    /**
     * Fetch episodes for a single season. Each call is independent (no
     * recursion), so [loadSeasons] can fan them out in parallel. Smart-play
     * is re-evaluated after each season resolves; the smart-play logic itself
     * is idempotent and will keep waiting for more seasons if none have a
     * resumeable/unplayed episode yet.
     */
    private fun loadEpisodes(seriesId: String, seasonId: String) {
        launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    episodesMap[seasonId] = episodeList
                    // Fold the fetchedSeasonIds update into the same emission as
                    // the episodes update so each call produces one uiState copy.
                    _uiState.update {
                        it.copy(
                            episodes = episodesMap.toMap(),
                            fetchedSeasonIds = it.fetchedSeasonIds + seasonId,
                        )
                    }
                    maybeComputeSmartPlayTarget()
                }
                .onFailure {
                    if (!_uiState.value.episodes.containsKey(seasonId)) {
                        episodesMap[seasonId] = emptyList()
                        _uiState.update {
                            it.copy(
                                episodes = episodesMap.toMap(),
                                fetchedSeasonIds = it.fetchedSeasonIds + seasonId,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(fetchedSeasonIds = it.fetchedSeasonIds + seasonId) }
                    }
                    maybeComputeSmartPlayTarget()
                }
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
            val state = _uiState.value
            val allEpisodes = state.episodes.values.flatten()
            // If there are still seasons in-flight (parallel batch from
            // loadSeasons hasn't fully resolved), defer: do not set a target
            // yet, and do NOT re-trigger fetches (all seasons are already
            // launched in parallel). The next season landing will call
            // maybeComputeSmartPlayTarget() again.
            if (allEpisodes.isEmpty()) {
                if (state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }) {
                    return@launch // more seasons pending — wait
                }
                _uiState.update { it.copy(smartPlayTarget = null) }
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
                            label = context.getString(R.string.detail_resume_episode, s, e),
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
                val label = if (hasWatchedBefore) context.getString(R.string.detail_next_up_episode, s, e)
                    else context.getString(R.string.detail_play_episode, s, e)
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

            // All episodes played — check whether more seasons are still
            // in-flight before falling back to a replay target.
            if (state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }) {
                return@launch // wait for remaining seasons
            }

            val first = sorted.first()
            val s = first.seasonNumber ?: 1
            val e = first.episodeNumber ?: first.indexNumber ?: 1
            _uiState.update {
                it.copy(
                    smartPlayTarget = DetailUiState.SmartPlayTarget(
                        episode = first,
                        label = context.getString(R.string.detail_replay_episode, s, e),
                        startPositionTicks = 0,
                    )
                )
            }
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
            val label = if (hasProgress) context.getString(R.string.detail_resume_episode, s, e)
                else context.getString(R.string.detail_play_episode, s, e)
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
                .onFailure {
                    // Don't leave the user guessing why the heart didn't flip.
                    _uiState.update { it.copy(userMessage = context.getString(R.string.detail_msg_couldnt_update_favorite)) }
                }
        }
    }

    fun markPlayed() {
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            mediaRepository.markPlayed(itemId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail?.copy(
                                item = state.detail.item.copy(isPlayed = true)
                            )
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(userMessage = context.getString(R.string.detail_msg_couldnt_mark_played)) }
                }
        }
    }

    fun markUnplayed() {
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            mediaRepository.markUnplayed(itemId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            detail = state.detail?.copy(
                                item = state.detail.item.copy(isPlayed = false)
                            )
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(userMessage = context.getString(R.string.detail_msg_couldnt_mark_unplayed)) }
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

    fun hideFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            preferencesStore.hideCwItem(item.id)
        }
    }

    // ── Delete & re-download ──────────────────────────────────────────────

    /** Opens the confirmation dialog (no destructive action yet). */
    fun requestRedownload() {
        _uiState.update {
            it.copy(showRedownloadDialog = true, redownloadProgress = null)
        }
    }

    /** Cancels the dialog / dismisses the result. */
    fun dismissRedownloadDialog() {
        _uiState.update {
            it.copy(showRedownloadDialog = false, redownloadProgress = null)
        }
    }

    /**
     * Runs the 4-step *arr delete & re-download flow: delete file → verify →
     * re-monitor (if needed) → search. The whole flow is driven through the
     * *arr file-delete API (NOT Jellyfin) so *arr's `hasFile` flips to false
     * and the search actually re-grabs. Per-step results are surfaced live via
     * [DetailUiState.redownloadProgress]; the dialog renders each step's status.
     */
    fun confirmRedownload() {
        val detail = _uiState.value.detail ?: return
        val item = detail.item
        val providerIds = detail.providerIds
        val kind = if (item.mediaType == MediaType.MOVIE) ArrServiceKind.RADARR else ArrServiceKind.SONARR

        // Resolve ids up-front; bail with a message if the required id is
        // missing (the action shouldn't have been visible, but guard anyway).
        val tmdbId = providerIds["tmdb"]?.toIntOrNull()
        val tvdbId = if (item.mediaType == MediaType.EPISODE) {
            _uiState.value.seriesProviderIds["tvdb"]?.toIntOrNull()
        } else {
            providerIds["tvdb"]?.toIntOrNull()
        }
        if (kind == ArrServiceKind.RADARR && tmdbId == null) {
            _uiState.update {
                it.copy(redownloadProgress = DetailUiState.RedownloadProgress(
                    isRunning = false,
                    steps = listOf(
                        com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult(
                            com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE,
                            com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.FAILED,
                            context.getString(R.string.detail_redownload_missing_id_movie),
                        ),
                    ),
                ))
            }
            return
        }
        if (kind == ArrServiceKind.SONARR && (tvdbId == null || item.seasonNumber == null || item.episodeNumber == null)) {
            _uiState.update {
                it.copy(redownloadProgress = DetailUiState.RedownloadProgress(
                    isRunning = false,
                    steps = listOf(
                        com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult(
                            com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE,
                            com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.FAILED,
                            context.getString(R.string.detail_redownload_missing_id_episode),
                        ),
                    ),
                ))
            }
            return
        }

        _uiState.update {
            it.copy(redownloadProgress = DetailUiState.RedownloadProgress(isRunning = true))
        }
        launch {
            val result = arrRepository.redownloadMedia(
                tmdbId = tmdbId ?: 0,
                kind = kind,
                tvdbId = tvdbId,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
            ).getOrNull()

            _uiState.update {
                it.copy(redownloadProgress = DetailUiState.RedownloadProgress(
                    isRunning = false,
                    steps = result?.steps,
                ))
            }
        }
    }

    fun startDownload() {
        val detail = _uiState.value.detail ?: run {
            _uiState.update { it.copy(downloadError = context.getString(R.string.detail_error_details_not_loaded)) }
            return
        }
        val source = detail.mediaSources.firstOrNull() ?: run {
            _uiState.update { it.copy(downloadError = context.getString(R.string.detail_error_no_source)) }
            return
        }

        // Cellular download size warning: when on a metered network and the
        // user has configured a warning threshold (MB), surface a
        // confirmation dialog instead of silently consuming data.
        val prefs = preferencesStore.preferences.value
        val thresholdMb = prefs.cellularDownloadSizeWarningMb
        if (thresholdMb > 0 && !adaptiveBitrateManager.isUnmeteredConnection()) {
            val sizeBytes = source.size ?: 0L
            val sizeMb = (sizeBytes / (1024L * 1024L)).toInt()
            if (sizeMb >= thresholdMb) {
                _uiState.update { it.copy(cellularDownloadWarningMb = sizeMb) }
                return
            }
        }

        performDownload(detail.item, source)
    }

    /**
     * Called from the UI after the user explicitly confirms a cellular
     * download that exceeded the [com.raulshma.jellyplay.core.model.UserPreferences.cellularDownloadSizeWarningMb]
     * threshold. Clears the warning state and proceeds with the download.
     */
    fun confirmCellularDownload() {
        val detail = _uiState.value.detail ?: return
        val source = detail.mediaSources.firstOrNull() ?: return
        _uiState.update { it.copy(cellularDownloadWarningMb = null) }
        performDownload(detail.item, source)
    }

    fun dismissCellularDownloadWarning() {
        _uiState.update { it.copy(cellularDownloadWarningMb = null) }
    }

    private fun performDownload(
        item: com.raulshma.jellyplay.core.model.MediaItem,
        source: com.raulshma.jellyplay.core.model.MediaSource,
    ) {
        launch {
            _uiState.update { it.copy(isDownloading = true, downloadError = null) }
            try {
                // Apply the user's download quality preference when building the
                // stream URL so the server transcodes to the requested ceiling.
                val prefs = preferencesStore.preferences.value
                val maxBitrate = qualityToMaxBitrate(prefs.downloadQuality)
                val streamUrl = playbackRepository.getStreamUrl(
                    itemId = item.id,
                    mediaSourceId = source.id,
                    maxBitrate = maxBitrate,
                )
                if (streamUrl.isBlank()) {
                    _uiState.update { it.copy(downloadError = context.getString(R.string.detail_error_no_stream_url), isDownloading = false) }
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
                    _uiState.update { it.copy(downloadError = error.message ?: context.getString(R.string.detail_error_download_failed)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(downloadError = e.message ?: context.getString(R.string.detail_error_download_failed)) }
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun downloadSeries(episodeIds: Map<String, List<String>>? = null) {
        val detail = _uiState.value.detail ?: run {
            _uiState.update { it.copy(seriesDownloadResult = SeriesDownloadResult(error = context.getString(R.string.detail_error_details_not_loaded))) }
            return
        }
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) {
            _uiState.update { it.copy(seriesDownloadResult = SeriesDownloadResult(error = context.getString(R.string.detail_error_not_a_series))) }
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
                            seriesDownloadResult = SeriesDownloadResult(error = error.message ?: context.getString(R.string.detail_error_queue_failed)),
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
        imageUrlProvider.getBackdropUrl(itemId)

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
        // Reject details that don't belong to the item currently being viewed.
        // Because the DetailViewModel is shared across detail navigations, a
        // freshly-composed screen briefly observes the *previous* item's detail
        // and may invoke this with a stale MediaDetail — which would load (and
        // cache) the wrong item's trailers/videos and block the real item's load.
        if (detail.item.id != currentItemId) return
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
                val match = TMDB_ID_REGEX.find(url.url)
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

    private fun qualityToMaxBitrate(quality: DownloadQuality): Int? = when (quality) {
        DownloadQuality.ORIGINAL -> null
        DownloadQuality.HIGH_1080P -> 8_000_000
        DownloadQuality.MEDIUM_720P -> 3_000_000
        DownloadQuality.LOW_480P -> 1_500_000
    }

    override fun onCleared() {
        super.onCleared()
        themeMusicPlayer.stop()
    }
}

@Immutable
data class SeriesDownloadResult(
    val queuedCount: Int = 0,
    val error: String? = null,
)
