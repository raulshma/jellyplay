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
import com.raulshma.jellyplay.core.network.api.ApiException
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
 * Ceiling on the number of season-episode fetches that may run concurrently
 * for the download-sheet path (which still fetches per-season on demand).
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
     * Server resolution is deferred past the cheap checks and performed once
     * per series detail load (in [loadItem]) rather than inside this combine.
     * The actual series lookup happens inside ManageSeriesScreen.
     */
    val canManageSeries: StateFlow<Boolean> = combine(
        // Map to identity-relevant fields only so favorite/played toggles (which
        // change isFavorite/isPlayed but not id/mediaType) produce structurally
        // equal emissions that StateFlow deduplicates.
        _uiState.map { it.detail?.item?.let { item -> ItemIdentity(item.id, item.mediaType) } },
        _uiState.map { it.detail?.providerIds?.get("tvdb") },
        preferencesStore.preferences.map { it.isExperimentalEnabled(ExperimentalFeature.DIRECT_ARR_INTEGRATION) },
        _uiState.map { it.sonarrServersResolved },
    ) { itemIdentity, tvdbId, flagEnabled, sonarrResolved ->
        if (!flagEnabled || itemIdentity == null) false
        else if (itemIdentity.mediaType != MediaType.SERIES) false
        else if (tvdbId?.toIntOrNull() == null) false
        else sonarrResolved
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
    // Internal caches (not observable UI state). Mutations happen on the Main
    // dispatcher (viewModelScope), and cross-dispatcher reads (e.g. the smart-
    // play computation on Dispatchers.Default) only ever observe the
    // `.toMap()` snapshot copied into uiState — never this mutable map. Keep
    // it that way: do NOT read or mutate [episodesMap] from a non-Main path,
    // or switch to a thread-safe container if that changes.
    private val episodesMap = mutableMapOf<String, List<MediaItem>>()
    // Cached flattened+sorted episode list. Keyed on BOTH the set of fetched
    // seasons and [episodeDataEpoch]. A re-fetch (loadAllEpisodes /
    // loadAllSeasonsBatched / loadEpisodes) can land fresh server data — updated
    // isPlayed/playbackPositionTicks on the SAME season set — so keying on the
    // season set alone would return a stale list and pick the wrong resume/next-
    // up target. Bumping the epoch on every episode-map mutation invalidates the
    // cache precisely then.
    //
    // NOTE: markPlayed / markUnplayed / toggleFavorite do NOT bump the epoch.
    // They mutate the series/movie item in [DetailUiState.detail], not the
    // episodes map, so the sorted episode list (and its playback order) is
    // genuinely unchanged. If a future per-episode mutation (e.g. per-episode
    // mark-played, or a playback-position refresh after returning from the
    // player) mutates entries in [episodesMap], it MUST call
    // [invalidateSortedEpisodesCache] or it will silently serve a stale list.
    //
    // Reads and writes are guarded by [sortedEpisodesSnapshot]'s `@Synchronized`
    // (runs on Dispatchers.Default) and [resetSortedEpisodesCache] /
    // [invalidateSortedEpisodesCache] (run on Main). `@Synchronized` locks on the
    // VM instance, so these calls are serialized regardless of thread — the
    // @Volatile below is belt-and-braces, the monitor is what actually makes
    // access safe.
    @Volatile
    private var cachedSortedEpisodes: List<MediaItem>? = null
    @Volatile
    private var cachedSortedEpisodesKey: Set<String> = emptySet()
    @Volatile
    private var cachedSortedEpisodesEpoch: Long = 0L
    // Bumped on every episode-map mutation (see callers of
    // [invalidateSortedEpisodesCache]) so [sortedEpisodesSnapshot] can detect
    // "same season set, but the contents changed" and rebuild.
    @Volatile
    private var episodeDataEpoch: Long = 0L
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
                    sonarrServersResolved = false,
                )
            }
            episodesMap.clear()
            resetSortedEpisodesCache()
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
                    // Subsidiary loads run via [launch] in viewModelScope, so they
                    // are NOT children of loadJob (loadJob?.cancel() will not stop
                    // them). Each subsidiary therefore captures the current item/series
                    // id and bails before writing if navigation has moved on, so a
                    // stale fetch can no longer clobber the new item's state. This
                    // mirrors the seerrDataGeneration guard used by [loadSeerrData].
                    when (itemType) {
                        MediaType.SERIES -> {
                            loadSeasons(itemId)
                            resolveSonarrForSeries(detail)
                        }
                        MediaType.EPISODE -> {
                            // For episodes: load the parent series' seasons so the
                            // episode row + smart-play target resolve. This must
                            // never gate the Play button, hence the non-blocking call.
                            detail.item.seriesId?.let { seriesId ->
                                loadSeasons(seriesId)
                                resolveSonarrForSeries(detail)
                            }
                        }
                        MediaType.ALBUM -> loadAlbumTracks(itemId)
                        MediaType.COLLECTION -> loadCollectionItems(itemId)
                        else -> _uiState.update { state -> state.copy(smartPlayTarget = null) }
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
                                // Guard: if navigation moved to another item while
                                // this fetch was in flight, drop the result rather
                                // than overwriting the new item's relatedItems.
                                if (currentItemId != itemId) return@onSuccess
                                _uiState.update {
                                    it.copy(relatedItems = items.filter { related -> related.id != itemId })
                                }
                            }
                    }
                }
                .onFailure { err ->
                    val accessDenied = (err as? ApiException)?.isAccessDenied == true
                    val message = if (accessDenied) {
                        context.getString(R.string.detail_error_access_denied)
                    } else {
                        err.message ?: context.getString(R.string.detail_error_load_failed)
                    }
                    _uiState.update { it.copy(error = message, isAccessDenied = accessDenied) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Resolves whether any Sonarr server is reachable, once per series load, and
     * stores the boolean in [_uiState]. Previously [canManageSeries] called
     * [ArrRepository.resolveServers] from inside a `combine` transform, which
     * re-issued network I/O on every identity tick and got cancelled/restarted
     * mid-resolution. Hoisting it here makes the combine a pure derivation.
     */
    private fun resolveSonarrForSeries(detail: MediaDetail) {
        val tvdbId = detail.providerIds["tvdb"]
        if (tvdbId?.toIntOrNull() == null) return
        val itemId = detail.item.id
        launch {
            val summary = arrRepository.resolveServers()
                .getOrDefault(com.raulshma.jellyplay.core.model.arr.ArrServiceSummary())
            // Guard: don't write sonarr resolution onto a different item's state.
            if (currentItemId != itemId) return@launch
            _uiState.update { it.copy(sonarrServersResolved = summary.sonarrServers.isNotEmpty()) }
        }
    }

    private fun loadSeasons(seriesId: String) {
        currentSeriesId = seriesId
        launch {
            mediaRepository.getSeasons(seriesId)
                .onSuccess { seasonList ->
                    // Guard: if navigation moved to another series, drop the
                    // result rather than overwriting the new screen's seasons.
                    if (currentSeriesId != seriesId) return@onSuccess
                    _uiState.update { it.copy(seasons = seasonList) }
                    if (seasonList.isEmpty()) return@onSuccess
                    loadAllEpisodes(seriesId, seasonList)
                }
        }
    }

    /**
     * Fetches every episode for a series in a single round-trip via
     * [MediaRepository.getAllEpisodesGrouped], which calls Jellyfin's
     * `/Shows/{seriesId}/Episodes` endpoint with no `seasonId` so the server
     * returns the full set. Collapses an N-season fan-out (one request per
     * season) into a single call and a single [DetailUiState] emission.
     */
    private fun loadAllEpisodes(seriesId: String, seasonList: List<MediaItem>) {
        launch {
            mediaRepository.getAllEpisodesGrouped(seriesId)
                .onSuccess { grouped ->
                    // Guard: navigation moved to another series — drop the
                    // result instead of clobbering the new screen's episodes.
                    if (currentSeriesId != seriesId) return@onSuccess
                    episodesMap.clear()
                    episodesMap.putAll(grouped)
                    // Only mark a season "fetched" when the batch actually
                    // returned a value for THAT season's id. Previously every
                    // season was force-inserted as emptyList() and added to
                    // fetchedSeasonIds, so a season whose episodes grouped under
                    // a different key (e.g. "" when an episode's seasonId came
                    // back null) showed empty AND was marked fetched — which
                    // short-circuited loadEpisodesForSeason and pinned it empty.
                    // Leaving such seasons absent lets the UI show its loading
                    // state and lets loadEpisodesForSeason fire the per-season
                    // refetch (the proven-working old path).
                    val fetchedIds = seasonList
                        .filter { grouped.containsKey(it.id) }
                        .map { it.id }
                        .toSet()
                    // New episode contents (fresh played/position from server)
                    // can land with the same season set as before — bump the
                    // epoch so [sortedEpisodesSnapshot] rebuilds.
                    invalidateSortedEpisodesCache()
                    _uiState.update {
                        it.copy(
                            episodes = episodesMap.toMap(),
                            fetchedSeasonIds = fetchedIds,
                        )
                    }
                    maybeComputeSmartPlayTarget()
                }
                .onFailure {
                    // Guard also applies to the fallback: only fan out if we're
                    // still on the same series.
                    if (currentSeriesId != seriesId) return@onFailure
                    // Fall back to per-season fetches only if the batched call
                    // fails (e.g. older server that rejected the unfiltered
                    // query). Caps concurrency at MAX_PARALLEL_SEASON_FETCHES.
                    loadAllSeasonsBatched(seriesId, seasonList)
                }
        }
    }

    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_uiState.value.fetchedSeasonIds.contains(seasonId)) return
        loadEpisodes(seriesId, seasonId)
    }

    /**
     * Per-season fallback used when the batched [loadAllEpisodes] call fails.
     * Mirrors the previous fan-out: N concurrent requests capped by a semaphore,
     * folded into one emission when the last season resolves.
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
                    // Guard: don't emit if navigation moved to another series.
                    if (currentSeriesId != seriesId) return@launch
                    invalidateSortedEpisodesCache()
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

    /**
     * Fetch episodes for a single season (used by [loadEpisodesForSeason] for
     * on-demand loads outside the initial batch). Smart-play is re-evaluated
     * after the season resolves.
     */
    private fun loadEpisodes(seriesId: String, seasonId: String) {
        launch {
            mediaRepository.getEpisodes(seriesId, seasonId)
                .onSuccess { episodeList ->
                    if (currentSeriesId != seriesId) return@onSuccess
                    episodesMap[seasonId] = episodeList
                    invalidateSortedEpisodesCache()
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
                    if (currentSeriesId != seriesId) return@onFailure
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
                .onSuccess { tracks ->
                    if (currentItemId != albumId) return@onSuccess
                    _uiState.update { it.copy(albumTracks = tracks) }
                }
        }
    }

    private fun loadCollectionItems(collectionId: String) {
        launch {
            mediaRepository.getCollectionItems(collectionId, limit = 100)
                .onSuccess { result ->
                    if (currentItemId != collectionId) return@onSuccess
                    _uiState.update { it.copy(collectionItems = result.items) }
                }
        }
    }

    fun playAlbum(startIndex: Int = 0) {
        val tracks = _uiState.value.albumTracks
        if (tracks.isEmpty()) return
        val albumName = _uiState.value.detail?.item?.name
        // Queue construction builds N image URLs + N queue items; move it off
        // the Main dispatcher (the click handler is a non-suspend call) so a
        // 50–100-track album doesn't block the UI thread before playQueue.
        launch(Dispatchers.Default) {
            val queueItems = tracks.map { track ->
                track.toAudioQueueItem(
                    imageUrl = playbackRepository.getImageUrl(track.id, maxWidth = 400),
                    albumFallback = albumName,
                )
            }
            audioPlaybackManager.playQueue(queueItems, startIndex)
        }
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
            val sorted = sortedEpisodesSnapshot(state)
            if (sorted.isEmpty()) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }

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
            val sorted = sortedEpisodesSnapshot(_uiState.value)
            if (sorted.isEmpty()) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            val currentIndex = sorted.indexOfFirst { it.id == currentEpisode.id }
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

    /**
     * Returns the flattened + playback-sorted episode list, cached keyed on
     * [DetailUiState.fetchedSeasonIds] and [episodeDataEpoch]. A re-fetch can
     * land fresh episode contents (played state, playback position) with the
     * same season set, so the epoch distinguishes "same seasons, same contents"
     * from "same seasons, contents changed"; without this cache each call
     * re-flattened and re-sorted the entire episode set (O(N×E) on a large
     * series). The cache is invalidated in [loadItem] and whenever the set of
     * fetched seasons or the episode contents change.
     */
    @Synchronized
    private fun sortedEpisodesSnapshot(state: DetailUiState): List<MediaItem> {
        val key = state.fetchedSeasonIds
        val epoch = episodeDataEpoch
        if (cachedSortedEpisodes != null && cachedSortedEpisodesKey == key && cachedSortedEpisodesEpoch == epoch) {
            return cachedSortedEpisodes!!
        }
        val sorted = state.episodes.values.flatten().sortedByPlaybackOrder()
        cachedSortedEpisodes = sorted
        cachedSortedEpisodesKey = key
        cachedSortedEpisodesEpoch = epoch
        return sorted
    }

    @Synchronized
    private fun resetSortedEpisodesCache() {
        cachedSortedEpisodes = null
        cachedSortedEpisodesKey = emptySet()
        cachedSortedEpisodesEpoch = 0L
    }

    /**
     * Bumps the episode-data epoch so [sortedEpisodesSnapshot] rebuilds its
     * cached list. Call after any mutation that changes the contents of the
     * fetched episodes (played/favorite/position) without changing the set of
     * fetched seasons.
     */
    @Synchronized
    private fun invalidateSortedEpisodesCache() {
        episodeDataEpoch++
    }

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

                // For episodes, propagate the parent series/season ids so the
                // downloads row is linked to its series. Without these,
                // deleteOfflineSeries (WHERE seriesId = :seriesId) finds no rows
                // and leaves the episode files + download rows orphaned.
                val isEpisode = item.mediaType == MediaType.EPISODE
                downloadRepository.startDownload(
                    mediaItemId = item.id,
                    name = item.name,
                    mediaType = mediaType,
                    mediaSourceId = source.id,
                    downloadUrl = streamUrl,
                    imageUrl = imageUrl,
                    imageBlurHash = item.blurHashes.primary,
                    seriesId = if (isEpisode) item.seriesId else null,
                    seasonId = if (isEpisode) item.seasonId else null,
                    seriesName = if (isEpisode) item.seriesName else null,
                    seasonName = if (isEpisode) item.seasonName else null,
                    episodeNumber = if (isEpisode) item.episodeNumber else null,
                    seasonNumber = if (isEpisode) item.seasonNumber else null,
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

            // Read the already-resolved Seerr connection booleans from the
            // published [uiState] aggregator — NOT [_uiState]. The flags are
            // folded into [uiState] by the outer combine (Group 3 → seerrFlags),
            // but are never written to [_uiState] (the Group 1 primary flow), so
            // reading [_uiState].value here would always yield the default false
            // and skip every Seerr fetch. [uiState] is a hot StateFlow, so .value
            // is a snapshot read with no subscription/probe overhead.
            val connected = uiState.value.isSeerrConnected

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
            val enabled = uiState.value.isSeerrRecommendationsEnabled
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
