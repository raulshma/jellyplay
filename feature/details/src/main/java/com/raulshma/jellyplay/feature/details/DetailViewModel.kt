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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private val TMDB_ID_REGEX = Regex("""/(\d+)(?:$|/|\?)""")

/**
 * Ceiling on the number of season-episode fetches that may run concurrently.
 * Without a cap, a long-running series (30+ seasons) fires that many
 * [MediaRepository.getEpisodes] requests at once, saturating the connection
 * pool and contending with the still-in-flight core detail response.
 */
private const val MAX_PARALLEL_SEASON_FETCHES = 5

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
     * Whether the "Manage Series" action should be shown. True iff:
     * - The DIRECT_ARR_INTEGRATION experimental flag is enabled, AND
     * - The current item is a SERIES (episode navigation goes via the parent
     *   series detail, so the menu naturally appears there), AND
     * - The series has a tvdb id (Sonarr resolves series by tvdb), AND
     * - At least one Sonarr server is resolved.
     *
     * Server resolution is deferred past the cheap checks; the actual series
     * lookup happens inside ManageSeriesScreen.
     */
    val canManageSeries: StateFlow<Boolean> = combine(
        // Map to identity-relevant fields only so favorite/played toggles (which
        // change isFavorite/isPlayed but not id/mediaType) produce structurally
        // equal emissions that StateFlow deduplicates — otherwise every toggle
        // re-triggered the combine and called arrRepository.resolveServers().
        _uiState.map { it.detail?.item?.let { item -> ItemIdentity(item.id, item.mediaType) } },
        _uiState.map { it.detail?.providerIds?.get("tvdb") },
        preferencesStore.preferences.map { it.isExperimentalEnabled(ExperimentalFeature.DIRECT_ARR_INTEGRATION) },
    ) { itemIdentity, tvdbId, flagEnabled ->
        if (!flagEnabled || itemIdentity == null) return@combine false
        if (itemIdentity.mediaType != MediaType.SERIES) return@combine false
        if (tvdbId?.toIntOrNull() == null) return@combine false
        val summary = arrRepository.resolveServers().getOrDefault(com.raulshma.jellyplay.core.model.arr.ArrServiceSummary())
        summary.sonarrServers.isNotEmpty()
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val seerrRequestState = com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder(scope, seerrRequestDelegate)

    /**
     * Aggregated detail-screen state. Eight upstream flows feed this [StateFlow],
     * but they are split into three independently-`stateIn`'d groups so a tick in
     * one group (e.g. Seerr connection polling) doesn't re-run the combine logic of
     * an unrelated group (e.g. the core detail/seasons/episodes tree). A final
     * outer [combine] folds the three snapshots into a single [DetailUiState] so
     * observers see one atomic snapshot, while each group's [StateFlow] deduplicates
     * its own emissions upstream of the merge.
     */
    val uiState: StateFlow<DetailUiState> = run {
        // Group 1 — core load state (detail/seasons/episodes/smart-play/...).
        val core = _uiState.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
        // Group 2 — Seerr request-flow ephemera (radarr/sonarr/result/dialog state).
        val seerrRequest = combine(
            seerrRequestState.requestResult,
            seerrRequestState.radarrServers,
            seerrRequestState.sonarrServers,
            seerrRequestState.isLoadingServices,
            seerrRequestState.tvSeasons,
        ) { requestResult, radarrServers, sonarrServers, isLoadingServices, tvSeasons ->
            SeerrRequestSnapshot(
                requestResult = requestResult,
                radarrServers = radarrServers,
                sonarrServers = sonarrServers,
                isLoadingServices = isLoadingServices,
                tvSeasons = tvSeasons,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrRequestSnapshot())
        // Group 3 — Seerr connection flags that only gate recommendation visibility.
        val seerrFlags = combine(
            seerrRepository.isConnected(),
            seerrRepository.isRecommendationsEnabled(),
        ) { isConnected, isRecommendationsEnabled ->
            SeerrConnectionFlags(isConnected, isRecommendationsEnabled)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrConnectionFlags())

        combine(core, seerrRequest, seerrFlags) { primary, request, flags ->
            primary.copy(
                seerrRequestResult = request.requestResult,
                seerrRadarrServers = request.radarrServers,
                seerrSonarrServers = request.sonarrServers,
                isLoadingSeerrServices = request.isLoadingServices,
                seerrTvSeasons = request.tvSeasons,
                isSeerrConnected = flags.isConnected,
                isSeerrRecommendationsEnabled = flags.isRecommendationsEnabled,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
    }

    // Direct (non-observable) readers for the two stream-selection indices.
    // These are read synchronously at click time inside the play callback
    // (which captures a `remember`-ed lambda), so they must read the current
    // snapshot from [_uiState] rather than a composition-captured value. All
    // other state is consumed reactively via [uiState].
    val selectedSubtitleIndex: Int? get() = _uiState.value.selectedSubtitleIndex
    val selectedAudioIndex: Int? get() = _uiState.value.selectedAudioIndex

    // Internal caches (not observable UI state). All access happens on the
    // Main dispatcher (viewModelScope), so a plain MutableMap suffices — the
    // previous synchronizedMap wrappers added lock overhead with no contention.
    private val episodesMap = mutableMapOf<String, List<MediaItem>>()
    private val downloadSheetEpisodesMap = mutableMapOf<String, List<MediaItem>>()
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
                        // episode row + smart-play target resolve. This must
                        // never gate the Play button, hence the non-blocking call.
                        loadSeasons(detail.item.seriesId!!)
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
                    if (seasonList.isEmpty()) return@onSuccess
                    loadAllSeasonsBatched(seriesId, seasonList)
                }
        }
    }

    /**
     * Fetches every season's episodes concurrently, but folds all results into a
     * single [DetailUiState] emission once the batch completes. This replaces the
     * previous pattern of fanning out N independent [loadEpisodes] calls, each of
     * which performed its own `episodesMap.toMap()` full copy (O(N²) total) and its
     * own smart-play recomputation (O(N × E) total).
     *
     * Concurrency is capped at [MAX_PARALLEL_SEASON_FETCHES] via a [Semaphore] so a
     * long-running series (30+ seasons) doesn't burst that many simultaneous
     * requests. Results land into the shared [episodesMap] as they arrive, but the
     * UI only sees one atomic snapshot when the last season resolves — and smart
     * play is computed exactly once.
     */
    private fun loadAllSeasonsBatched(seriesId: String, seasonList: List<MediaItem>) {
        val pending = AtomicInteger(seasonList.size)
        val semaphore = Semaphore(MAX_PARALLEL_SEASON_FETCHES)
        seasonList.forEach { season ->
            launch {
                semaphore.withPermit {
                    mediaRepository.getEpisodes(seriesId, season.id)
                        .onSuccess { episodeList ->
                            episodesMap[season.id] = episodeList
                        }
                        .onFailure {
                            if (!episodesMap.containsKey(season.id)) {
                                episodesMap[season.id] = emptyList()
                            }
                        }
                }
                if (pending.decrementAndGet() == 0) {
                    _uiState.update {
                        it.copy(
                            episodes = episodesMap.toMap(),
                            fetchedSeasonIds = episodesMap.keys.toSet(),
                        )
                    }
                    maybeComputeSmartPlayTarget()
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
            // Check pending seasons BEFORE flattening — flattening all episode
            // lists is O(total episodes) and was previously paid N times (once
            // per season landing) even when the early-return below would fire.
            val seasonsPending = state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }
            if (seasonsPending) return@launch
            val allEpisodes = state.episodes.values.flatten()
            if (allEpisodes.isEmpty()) {
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

            // All episodes played — fall back to a replay of the first episode.
            // (Seasons-pending was already ruled out by the early-return at the
            // top of this function, so no further guard is needed here.)
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
            // Reuse episodes already fetched by the main seasons display when
            // available, avoiding a duplicate network round-trip and a second
            // in-memory copy of the same episode list.
            val cached = episodesMap[seasonId]
            if (cached != null) {
                downloadSheetEpisodesMap[seasonId] = cached
                _uiState.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
            } else {
                mediaRepository.getEpisodes(seriesId, seasonId)
                    .onSuccess { episodeList ->
                        downloadSheetEpisodesMap[seasonId] = episodeList
                        _uiState.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
                    }
                    .onFailure {
                        downloadSheetEpisodesMap[seasonId] = emptyList()
                        _uiState.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
                    }
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

    /**
     * Available bytes on the volume backing the download destination
     * (`DIRECTORY_MUSIC` for audio, `DIRECTORY_MOVIES` otherwise). Read off the
     * main thread — callers should await this from a coroutine or `produceState`.
     *
     * Extracted from the inline `StatFs`/`Environment` probe that previously
     * lived in the download-confirmation composable so the UI layer no longer
     * touches the filesystem.
     */
    suspend fun getAvailableStorageBytes(isAudio: Boolean): Long = withContext(Dispatchers.IO) {
        val downloadDir = context.getExternalFilesDir(if (isAudio) android.os.Environment.DIRECTORY_MUSIC else android.os.Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val stat = android.os.StatFs(downloadDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
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

/**
 * Snapshot of the Seerr request-flow ephemera (dialog picker state + result
 * banner). Grouped so its upstream flows get a dedicated [StateFlow] in the
 * [DetailViewModel.uiState] combine chain — a tick here doesn't invalidate the
 * core detail or Seerr-connection groups.
 */
@Immutable
private data class SeerrRequestSnapshot(
    val requestResult: SeerrRequestResult? = null,
    val radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingServices: Boolean = false,
    val tvSeasons: List<SeerrSeason> = emptyList(),
)

/**
 * Snapshot of the Seerr connection flags that only gate recommendation
 * visibility. Grouped for the same reason as [SeerrRequestSnapshot].
 */
@Immutable
private data class SeerrConnectionFlags(
    val isConnected: Boolean = false,
    val isRecommendationsEnabled: Boolean = false,
)

/**
 * Identity-only projection of a [MediaItem] used as a [StateFlow] deduplication
 * key. Because favorite/played toggles mutate the item in place but never change
 * its id or mediaType, mapping to [ItemIdentity] collapses those toggles into a
 * single distinct emission.
 */
@Immutable
private data class ItemIdentity(val id: String, val mediaType: MediaType)
