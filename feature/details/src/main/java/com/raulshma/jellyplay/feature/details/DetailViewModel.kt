package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.core.model.NetworkStatus
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    /**
     * The consolidated series seasons/episodes snapshot — the single owner of
     * the "seasons → per-season episodes → sorted" shape this screen used to
     * re-assemble privately (`episodesMap`, `cachedSortedEpisodes*`,
     * `episodeDataEpoch`, the three `load*` functions). All episode loads now
     * read from the catalogue snapshot; smart-play reads its `sortedEpisodes`.
     */
    private val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    private val projections: PreferenceProjections,
    private val libraryStore: LibraryStore,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val experimentalStore: ExperimentalStore,
    private val downloadsStore: DownloadsStore,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val engineStore: PlayerEngineStore,
    private val offlineModeManager: OfflineModeManager,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val seerrRepository: SeerrRepository,
    private val seerrRequestDelegate: SeerrRequestDelegate,
    private val audioPlaybackManager: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager,
    private val themeMusicPlayer: com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer,
    private val tmdbApiClient: TmdbApiClient,
    private val arrRepository: ArrRepository,
) : JellyPlayViewModel() {

    /** Media-detail preference fields, projected centrally off the store slices. */
    val preferences: StateFlow<DetailPreferences> = projections.detailPreferences

    // Single source of truth for detail-screen state. All mutations
    // funnel through [_uiState.update]; the [uiState] aggregator additionally
    // folds in [SeerrRequestStateHolder] state via combine() so observers see a
    // single atomic snapshot.
    private val _uiState = MutableStateFlow(DetailUiState())

    /**
     * One-shot user-facing messages. Buffered so a message emitted before the
     * screen subscribes (e.g. during `loadItem`) is not lost. Replaces the
     * former `userMessage` / `downloadError` / `seriesDownloadResult` nullable
     * fields on [DetailUiState] and their clear-* methods.
     */
    private val _messages = MutableSharedFlow<DetailMessage>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    val messages: SharedFlow<DetailMessage> = _messages.asSharedFlow()

    /**
     * Whether the "Manage Series" action should be shown. True iff:
     * - The DIRECT_ARR_INTEGRATION experimental flag is enabled, AND
     * - The current item is a SERIES (episode navigation goes via the parent
     * series detail, so the menu naturally appears there), AND
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
        experimentalStore.experimental.map { it.enabledExperimentalFeatures.contains(ExperimentalFeature.DIRECT_ARR_INTEGRATION) },
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

    // Internal caches (not observable UI state). Mutations happen on the Main
    // dispatcher (viewModelScope). The seasons/episodes map, the sorted-episodes
    // cache and the episode-data epoch that used to live here have moved into
    // [episodeCatalogue] — the single owner of the series snapshot. The only
    // episode state kept locally is the download sheet's per-season on-demand
    // cache (the sheet fetches seasons lazily, independent of the main display).
    private val downloadSheetEpisodesMap = mutableMapOf<String, List<MediaItem>>()
    private var downloadSheetFetchedSeasonIds: Set<String> = emptySet()
    /**
     * The last catalogue snapshot loaded for [currentSeriesId]. Held locally so
     * smart-play and the optimistic mark-season rewrite read the canonical
     * playback order without a second catalogue round-trip. Nulled in
     * [loadItemInternal] when the series changes.
     */
    @Volatile
    private var currentCatalogueSnapshot: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot? = null
    private var loadJob: Job? = null
    private var currentItemId: String? = null
    private var currentSeriesId: String? = null
    private var seerrDataLoaded = false
    private var seerrDataGeneration = 0L

    fun selectSubtitle(index: Int?) {
        _uiState.update { it.copy(selectedSubtitleIndex = index) }
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            engineStore.setMediaStreamSelection(
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
            engineStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = index,
                subtitleStreamIndex = _uiState.value.selectedSubtitleIndex,
            )
        }
    }

    /**
     * Persists the season-episode sort order so it is shared across every
     * series detail screen (and survives navigation/relaunch). The value is
     * read back reactively via [preferences], so [SeasonsSection] picks it up
     * without any per-screen plumbing.
     */
    fun setEpisodesDescending(descending: Boolean) {
        launch { libraryStore.setEpisodesDescending(descending) }
    }

    /**
     * Toggles the compact vertical episode list preference (mobile only). Like
     * [setEpisodesDescending], persisted app-wide so the choice carries across
     * every series detail screen.
     */
    fun setCompactEpisodeList(enabled: Boolean) {
        launch { libraryStore.setCompactEpisodeList(enabled) }
    }

    fun getDownloadFlow(itemId: String): Flow<com.raulshma.jellyplay.core.model.DownloadItem?> =
        downloadRepository.getDownloadByMediaItemIdFlow(itemId)

    fun loadItem(itemId: String) {
        loadItemInternal(itemId, refresh = false)
    }

    /**
     * Pull-to-refresh: invalidates every in-memory cache backing this detail
     * screen (detail, similar, seasons/episodes, album tracks, collection
     * items) and re-fetches all data fresh from the server. Unlike [loadItem]
     * the current content stays on screen (the full-screen loading state is
     * skipped); the pull-to-refresh indicator is driven by
     * [DetailUiState.isRefreshing] instead.
     */
    fun forceRefresh() {
        val itemId = _uiState.value.detail?.item?.id ?: return
        loadItemInternal(itemId, refresh = true)
    }

    private fun loadItemInternal(itemId: String, refresh: Boolean) {
        // Record the item we're loading synchronously so that a stale
        // loadSeerrDataIfNeeded() call (from a freshly-composed screen still
        // observing the previous item's detail via the shared ViewModel) can be
        // rejected before it loads the wrong item's trailers/videos.
        currentItemId = itemId
        loadJob?.cancel()
        loadJob = launch {
            // Single atomic reset — collapses what used to be ~14 separate
            // composeState/stateFlow mutations into one emission so observers
            // see one recomposition, not fourteen. On refresh the detail is
            // kept so the content stays visible under the pull-to-refresh
            // indicator; every subsidiary slice is still cleared so fresh data
            // replaces it wholesale.
            _uiState.update {
                it.copy(
                    detail = if (refresh) it.detail else null,
                    isLoading = !refresh,
                    isRefreshing = refresh,
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
                    sonarrServersResolved = false,
                )
            }
            // Drop the catalogue snapshot for any series we were viewing so the
            // new item's load starts fresh (the VM is reused across navigations).
            currentSeriesId?.let { episodeCatalogue.invalidateSeries(it) }
            currentCatalogueSnapshot = null
            seerrDataLoaded = false
            // Bump the seerr generation so any in-flight trailer/video/recommendation
            // fetch from the *previous* item is invalidated and cannot write its stale
            // results onto this item's screen (the VM is shared across detail navigations).
            seerrDataGeneration++
            // Clear download-sheet caches too, since the same VM instance is reused.
            downloadSheetEpisodesMap.clear()
            downloadSheetFetchedSeasonIds = emptySet()
            if (refresh) {
                // Invalidate every cache the detail screen reads so the refetch
                // hits the server rather than the TTL caches.
                val item = _uiState.value.detail?.item
                mediaRepository.invalidateDetailCache(itemId)
                when (item?.mediaType) {
                    MediaType.SERIES -> episodeCatalogue.invalidateSeries(item.id)
                    MediaType.EPISODE -> item.seriesId?.let { episodeCatalogue.invalidateSeries(it) }
                    MediaType.ALBUM -> mediaRepository.invalidateUserDataCaches(itemId)
                    MediaType.COLLECTION -> mediaRepository.invalidateCollectionItemsCache(itemId)
                    else -> Unit
                }
            }
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    val storedSelection = engineStore.playerEngine.value.mediaStreamSelections[itemId]
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

                    // Trigger the Seerr recommendations/videos fetch from the VM
                    // (not the UI) so the former UI-side LaunchedEffect with its
                    // hard-coded 350ms delay and connection-polling over-keying is
                    // gone. The same delay is preserved here for frame priority
                    // (don't contend with first-frame GPU work), and the
                    // seerrDataLoaded guard keeps it idempotent across re-entries.
                    // Late-connect (Seerr enabled while on the detail screen) is
                    // not handled; revisit if it becomes a real need.
                    launch {
                        kotlinx.coroutines.delay(350)
                        if (currentItemId != itemId) return@launch
                        loadSeerrDataIfNeeded(detail)
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
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
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
            // One consolidated load: seasons + every season's episodes + the
            // canonical playback order, all from [episodeCatalogue]. The
            // catalogue owns single-flight, caching, the batched→per-season
            // fallback and the fetchedSeasonIds edge (a season whose episodes
            // group under a different key stays absent so the on-demand refetch
            // below can still fire).
            val snapshot = episodeCatalogue.loadSeriesEpisodes(seriesId).getOrNull()
            // Guard: if navigation moved to another series, drop the result.
            if (currentSeriesId != seriesId) return@launch
            if (snapshot == null) return@launch
            currentCatalogueSnapshot = snapshot
            _uiState.update {
                it.copy(
                    seasons = snapshot.seasons,
                    episodes = snapshot.episodesBySeason,
                    fetchedSeasonIds = snapshot.fetchedSeasonIds,
                )
            }
            maybeComputeSmartPlayTarget()
        }
    }

    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_uiState.value.fetchedSeasonIds.contains(seasonId)) return
        launch {
            // On-demand per-season load via the catalogue: serves from the
            // shared snapshot if present, else fetches the one season and merges
            // it back. The local snapshot is updated by folding the new season
            // in (rather than re-reading the full snapshot) so a season that was
            // absent from the batched load — the fetchedSeasonIds edge — still
            // lands in uiState and smart-play's source list.
            val episodes = episodeCatalogue.loadSeasonEpisodes(seriesId, seasonId).getOrNull()
                ?: return@launch
            if (currentSeriesId != seriesId) return@launch
            val merged = currentCatalogueSnapshot?.let { current ->
                current.copy(
                    episodesBySeason = current.episodesBySeason + (seasonId to episodes),
                    fetchedSeasonIds = current.fetchedSeasonIds + seasonId,
                    sortedEpisodes = (current.episodesBySeason + (seasonId to episodes))
                        .values.flatten().sortedByPlaybackOrder(),
                )
            }
            currentCatalogueSnapshot = merged
            _uiState.update {
                it.copy(
                    episodes = merged?.episodesBySeason ?: it.episodes + (seasonId to episodes),
                    fetchedSeasonIds = (merged?.fetchedSeasonIds ?: it.fetchedSeasonIds) + seasonId,
                )
            }
            maybeComputeSmartPlayTarget()
        }
    }

    /**
     * The canonical playback order, matching the catalogue's
     * `sortedByPlaybackOrder`. Used only by [loadEpisodesForSeason]'s optimistic
     * merge to rebuild `sortedEpisodes` after a per-season fetch lands.
     */
    private fun Iterable<MediaItem>.sortedByPlaybackOrder(): List<MediaItem> =
        sortedWith(
            compareBy(
                { it.seasonNumber ?: Int.MAX_VALUE },
                { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE },
                { it.name },
            )
        )

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
            // Check pending seasons BEFORE reading the snapshot — a season not
            // yet in fetchedSeasonIds means the catalogue snapshot is incomplete
            // and smart-play would target the wrong episode.
            val seasonsPending = state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }
            if (seasonsPending) return@launch
            val sorted = currentCatalogueSnapshot?.sortedEpisodes ?: return@launch
            val result = SmartPlayResolver.resolveSeries(sorted)
            if (result == null) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            _uiState.update {
                it.copy(smartPlayTarget = result.toUiTarget())
            }
        }
    }

    private fun computeEpisodeSmartPlayTarget(currentEpisode: MediaItem) {
        launch(Dispatchers.Default) {
            val sorted = currentCatalogueSnapshot?.sortedEpisodes ?: return@launch
            // The episode must still be present in the current sorted view.
            if (sorted.none { it.id == currentEpisode.id }) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            _uiState.update {
                it.copy(smartPlayTarget = SmartPlayResolver.resolveEpisode(currentEpisode).toUiTarget())
            }
        }
    }

    /** Maps a pure [SmartPlayResult] to the localized UI target. */
    private fun SmartPlayResult.toUiTarget(): DetailUiState.SmartPlayTarget {
        val s = episode.seasonNumber ?: 1
        val e = episode.episodeNumber ?: episode.indexNumber ?: 1
        val label = when (label) {
            LabelKind.RESUME_EPISODE -> context.getString(R.string.detail_resume_episode, s, e)
            LabelKind.NEXT_UP_EPISODE -> context.getString(R.string.detail_next_up_episode, s, e)
            LabelKind.PLAY_EPISODE -> context.getString(R.string.detail_play_episode, s, e)
            LabelKind.REPLAY_EPISODE -> context.getString(R.string.detail_replay_episode, s, e)
        }
        return DetailUiState.SmartPlayTarget(
            episode = episode,
            label = label,
            startPositionTicks = startPositionTicks,
            primaryImageUrl = imageUrlProvider.getImageUrl(episode.id),
        )
    }

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
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_update_favorite)))
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
                                // Jellyfin clears a manual watched item's
                                // resume point. Mirror that immediately so the
                                // detail UI cannot retain an in-progress bar
                                // while the queued/offline mutation syncs.
                                item = state.detail.item.copy(
                                    isPlayed = true,
                                    playbackPositionTicks = 0L,
                                )
                            )
                        )
                    }
                }
                .onFailure {
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_mark_played)))
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
                                // Marking unwatched also resets resume state;
                                // otherwise the detail screen would instantly
                                // show the title as partially watched again.
                                item = state.detail.item.copy(
                                    isPlayed = false,
                                    playbackPositionTicks = 0L,
                                )
                            )
                        )
                    }
                }
                .onFailure {
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_mark_unplayed)))
                }
        }
    }

    /**
     * Marks a row item (related/collection/episode) played or
     * unplayed without switching the screen's current detail item. Flips the
     * item in-place in [DetailUiState.relatedItems] so the card's badge updates
     * immediately; the next detail fetch reconciles the server truth.
     */
    fun markRowItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            val result = if (played) mediaRepository.markPlayed(item.id)
            else mediaRepository.markUnplayed(item.id)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        relatedItems = state.relatedItems.map {
                            if (it.id == item.id) it.copy(
                                isPlayed = played,
                                playbackPositionTicks = 0L,
                            ) else it
                        },
                        collectionItems = state.collectionItems.map {
                            if (it.id == item.id) it.copy(
                                isPlayed = played,
                                playbackPositionTicks = 0L,
                            ) else it
                        },
                    )
                }
            }
        }
    }

    /**
     * Marks every episode in [seasonId] as played. Jellyfin's
     * `markPlayedItem` endpoint recurses into a season's children, so this is a
     * single network call — but the UI needs the optimistic in-place flip so
     * every `EpisodeCard` shows the WATCHED badge and the Play button target
     * recomputes without waiting on a re-fetch.
     *
     * The optimistic write goes through [episodeCatalogue.updateSeasonEpisodes],
     * which rewrites the season in the cached snapshot and rebuilds its derived
     * `sortedEpisodes` — so smart-play's next-up target recomputes against the
     * flipped contents. The catalogue snapshot is then dropped via
     * [EpisodeCatalogue.invalidateSeries] so re-entry refetches the
     * fully-cascaded server state (no refetch here — see the old comment below).
     */
    fun markSeasonPlayed(seasonId: String) {
        markSeason(seasonId, played = true)
    }

    fun markSeasonUnplayed(seasonId: String) {
        markSeason(seasonId, played = false)
    }

    private fun markSeason(seasonId: String, played: Boolean) {
        val seriesId = currentSeriesId ?: return
        val current = currentCatalogueSnapshot?.seasonEpisodes(seasonId) ?: return
        // No-op if there is nothing to flip — avoids an unnecessary network
        // call, a spurious cache invalidation, and a redundant uiState emission.
        val alreadyInTargetState = current.all { it.isPlayed == played }
        if (alreadyInTargetState) return

        launch {
            val result = if (played) mediaRepository.markPlayed(seasonId)
            else mediaRepository.markUnplayed(seasonId)
            result
                .onSuccess {
                    // Optimistically rewrite the season in the catalogue snapshot;
                    // updateSeasonEpisodes rebuilds the derived sortedEpisodes so
                    // smart-play targets the right episode immediately.
                    val rewritten = episodeCatalogue.updateSeasonEpisodes(seriesId, seasonId) { episodes ->
                        episodes.map { episode ->
                            // The mark-played/unplayed endpoints clear the resume
                            // position server-side; mirror that locally for BOTH
                            // directions. For mark-unplayed this is what stops the
                            // in-progress bar and the "remaining time" label from
                            // lingering on an episode the user just marked unplayed,
                            // and keeps the episode out of continue watching locally
                            // until the next re-fetch confirms the server state.
                            episode.copy(
                                isPlayed = played,
                                playbackPositionTicks = 0L,
                            )
                        }
                    }
                    currentCatalogueSnapshot = rewritten
                    _uiState.update { state ->
                        state.copy(episodes = rewritten?.episodesBySeason ?: state.episodes)
                    }
                    // Drop the catalogue snapshot for this series. The optimistic
                    // flip above keeps the current screen correct, but
                    // `MediaRepositoryImpl.invalidateUserDataCaches` keys the
                    // series-cache drop off `detailCache.get(seasonId)`, which is
                    // null — seasons are loaded via the catalogue, not as
                    // standalone details. Without this explicit drop, re-entering
                    // the series detail (back navigation, app background) would
                    // serve the stale pre-mutation snapshot.
                    episodeCatalogue.invalidateSeries(seriesId)
                    // No post-mutation server refetch. The optimistic flip above
                    // already holds the correct post-mutation state for this
                    // screen, and a refetch would actively cause a stale-badge
                    // regression on re-entry: the per-season slice would be written
                    // back into the catalogue, so a later loadSeriesEpisodes would
                    // serve pre-cascade data — the watched/unwatched badges would
                    // flip back to stale and only self-correct once the TTL
                    // expired. Dropping the cache above and NOT re-populating it
                    // forces re-entry's load to miss and hit the server, which by
                    // then has the fully-cascaded UserData.Played state.
                    // The Play-button target may now point to a different
                    // episode (e.g. next-up moved to the following season), so
                    // recompute it against the updated episode contents.
                    maybeComputeSmartPlayTarget()
                }
                .onFailure {
                    _messages.emit(
                        DetailMessage.Text(
                            context.getString(
                                if (played) R.string.detail_msg_couldnt_mark_played
                                else R.string.detail_msg_couldnt_mark_unplayed
                            )
                        )
                    )
                }
        }
    }

    fun hideFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            homeDiscoveryStore.excludeSeriesFromNextUp(seriesId)
            _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_hidden_from_next_up)))
        }
    }

    fun showFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            homeDiscoveryStore.includeSeriesInNextUp(seriesId)
            _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_shown_in_next_up)))
        }
    }

    fun hideFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            homeDiscoveryStore.hideCwItem(item.id)
            _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_hidden_from_continue_watching)))
        }
    }

    fun showFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            homeDiscoveryStore.unhideCwItem(item.id)
            _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_shown_in_continue_watching)))
        }
    }

    fun startDownload() {
        val detail = _uiState.value.detail ?: run {
            launch { _messages.emit(DetailMessage.Text(context.getString(R.string.detail_error_details_not_loaded))) }
            return
        }
        val source = detail.mediaSources.firstOrNull() ?: run {
            launch { _messages.emit(DetailMessage.Text(context.getString(R.string.detail_error_no_source))) }
            return
        }

        // Cellular download size warning: when on a metered network and the
        // user has configured a warning threshold (MB), surface a
        // confirmation dialog instead of silently consuming data.
        val prefs = downloadsStore.downloads.value
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
     * download that exceeded the [com.raulshma.jellyplay.core.model.legacy.UserPreferences.cellularDownloadSizeWarningMb]
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
        val detail = _uiState.value.detail ?: return
        launch {
            _uiState.update { it.copy(isDownloading = true) }
            try {
                // Apply the user's download quality preference when building the
                // stream URL so the server transcodes to the requested ceiling.
                // The intake seam owns the full bundle (local images, trickplay,
                // subtitles, segments, offline metadata row), so feature modules
                // no longer re-implement the artifact-writing recipe.
                val prefs = downloadsStore.downloads.value
                val maxBitrate = qualityToMaxBitrate(prefs.downloadQuality)
                val result = downloadIntake.start(detail, maxBitrate)
                if (result.downloadItem == null) {
                    val message = result.error
                        ?: context.getString(R.string.detail_error_download_failed)
                    _messages.emit(DetailMessage.Text(message))
                }
            } catch (e: Exception) {
                _messages.emit(DetailMessage.Text(e.message ?: context.getString(R.string.detail_error_download_failed)))
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    fun downloadSeries(episodeIds: Map<String, List<String>>? = null) {
        val detail = _uiState.value.detail ?: run {
            launch { _messages.emit(DetailMessage.SeriesDownload(queuedCount = 0, error = context.getString(R.string.detail_error_details_not_loaded))) }
            return
        }
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) {
            launch { _messages.emit(DetailMessage.SeriesDownload(queuedCount = 0, error = context.getString(R.string.detail_error_not_a_series))) }
            return
        }

        launch {
            _uiState.update { it.copy(isDownloadingSeries = true) }
            downloadIntake.startSeries(item.id, episodeIds)
                .onSuccess { downloadIds ->
                    _messages.emit(DetailMessage.SeriesDownload(queuedCount = downloadIds.size, error = null))
                }
                .onFailure { error ->
                    _messages.emit(DetailMessage.SeriesDownload(queuedCount = 0, error = error.message ?: context.getString(R.string.detail_error_queue_failed)))
                }
            _uiState.update { it.copy(isDownloadingSeries = false) }
        }
    }

    fun prepareDownloadSheetEpisodes() {
        val seriesId = currentSeriesId ?: return
        val seasons = _uiState.value.seasons
        if (seasons.isEmpty()) return
        val seasonIds = seasons.map { it.id }.toSet()

        downloadSheetEpisodesMap.clear()
        downloadSheetFetchedSeasonIds = emptySet()
        _uiState.update { it.copy(downloadSheetLoadingSeasons = seasonIds) }

        launch {
            // The catalogue snapshot already holds the grouped map (and refetches
            // any missing season on demand internally), so the download sheet
            // reads it directly instead of re-issuing getAllEpisodesGrouped + a
            // per-season fallback fan-out.
            val snapshot = episodeCatalogue.loadSeriesEpisodes(seriesId).getOrNull()
            if (currentSeriesId != seriesId) return@launch
            currentCatalogueSnapshot = snapshot
            seasons.forEach { season ->
                downloadSheetEpisodesMap[season.id] = snapshot?.seasonEpisodes(season.id) ?: emptyList()
            }

            downloadSheetFetchedSeasonIds = seasonIds
            _uiState.update {
                it.copy(
                    downloadSheetEpisodes = downloadSheetEpisodesMap.toMap(),
                    downloadSheetLoadingSeasons = emptySet(),
                )
            }
        }
    }

    fun loadDownloadSheetEpisodes(seasonId: String) {
        if (seasonId in downloadSheetFetchedSeasonIds) return
        val seriesId = currentSeriesId ?: return
        _uiState.update { it.copy(downloadSheetLoadingSeasons = it.downloadSheetLoadingSeasons + seasonId) }
        launch {
            // Read the season from the catalogue snapshot (serves from the shared
            // cache when already present, else fetches the one season), avoiding
            // a duplicate round-trip and a second in-memory copy.
            val episodes = episodeCatalogue.loadSeasonEpisodes(seriesId, seasonId).getOrDefault(emptyList())
            downloadSheetEpisodesMap[seasonId] = episodes
            _uiState.update { it.copy(downloadSheetEpisodes = downloadSheetEpisodesMap.toMap()) }
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

            val tmdbId = resolveTmdbId(detail) // top-level fn in TmdbIdResolver.kt
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

    // ── Add to Playlist ────────────────────────────────────────────────
    // The playlist repository path is fully wired (PlaylistRepository mixin on
    // MediaRepository); these functions load the picker list and resolve the
    // current item's ids into a playlist. Series expand to their episode ids
    // (a Jellyfin playlist only holds playable items, not a series itself).

    /**
     * Opens the Add-to-Playlist picker and loads the user's playlists on demand.
     * The list is fetched fresh each open (server playlists are not cached
     * locally, matching the music playlists flow).
     */
    fun openPlaylistPicker() {
        val detail = _uiState.value.detail ?: return
        // Only playable video items and series are eligible (audio already has
        // its own playlist flow in feature/music).
        val type = detail.item.mediaType
        if (!type.isVideoType && type != MediaType.SERIES) return
        _uiState.update { it.copy(showPlaylistPicker = true) }
        loadPlaylists()
    }

    fun dismissPlaylistPicker() {
        _uiState.update { it.copy(showPlaylistPicker = false) }
    }

    fun openCreatePlaylistDialog() {
        _uiState.update {
            it.copy(
                showPlaylistPicker = false,
                showCreatePlaylistDialog = true,
            )
        }
    }

    fun dismissCreatePlaylistDialog() {
        _uiState.update { it.copy(showCreatePlaylistDialog = false) }
    }

    private fun loadPlaylists() {
        _uiState.update { it.copy(isLoadingPlaylists = true) }
        launch {
            mediaRepository.getPlaylists(limit = 100)
                .onSuccess { playlists ->
                    if (currentItemId != _uiState.value.detail?.item?.id) return@onSuccess
                    _uiState.update {
                        it.copy(
                            playlists = playlists.filter { p -> p.canEdit },
                            isLoadingPlaylists = false,
                        )
                    }
                }
                .onFailure {
                    if (currentItemId != _uiState.value.detail?.item?.id) return@onFailure
                    _uiState.update { it.copy(isLoadingPlaylists = false) }
                }
        }
    }

    /**
     * Adds the current item to an existing playlist. For a series, all fetched
     * episodes are added (Jellyfin rejects a bare series id in a playlist).
     */
    fun addToPlaylist(playlist: com.raulshma.jellyplay.core.model.Playlist) {
        val detail = _uiState.value.detail ?: return
        launch {
            _uiState.update { it.copy(isAddingToPlaylist = true) }
            resolvePlaylistItemIds(detail)
                .onSuccess { ids ->
                    if (ids.isEmpty()) {
                        _messages.emit(
                            DetailMessage.Text(context.getString(R.string.detail_msg_no_episodes_queued))
                        )
                        return@onSuccess
                    }
                    mediaRepository.addItemsToPlaylist(playlist.id, ids)
                        .onSuccess {
                            _messages.emit(
                                DetailMessage.Text(
                                    context.getString(R.string.detail_msg_added_to_playlist, playlist.name)
                                )
                            )
                        }
                        .onFailure {
                            _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                        }
                }
                .onFailure {
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                }
            _uiState.update {
                it.copy(
                    isAddingToPlaylist = false,
                    showPlaylistPicker = false,
                )
            }
        }
    }

    /**
     * Adds to the reserved "Watch Later" playlist, creating it on first use and
     * caching its id in preferences so subsequent adds reuse it.
     */
    fun addToWatchLater() {
        val detail = _uiState.value.detail ?: return
        val cachedId = appRuntimeStateStore.state.value.watchLaterPlaylistId
        launch {
            _uiState.update { it.copy(isAddingToPlaylist = true) }
            val ids = resolvePlaylistItemIds(detail).getOrElse {
                _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                _uiState.update {
                    it.copy(
                        isAddingToPlaylist = false,
                        showPlaylistPicker = false,
                    )
                }
                return@launch
            }
            if (ids.isEmpty()) {
                _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_no_episodes_queued)))
                _uiState.update {
                    it.copy(
                        isAddingToPlaylist = false,
                        showPlaylistPicker = false,
                    )
                }
                return@launch
            }
            if (cachedId != null) {
                mediaRepository.addItemsToPlaylist(cachedId, ids)
                    .onSuccess {
                        _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_added_to_watch_later)))
                    }
                    .onFailure {
                        _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                    }
            } else {
                mediaRepository.createPlaylist(
                    name = context.getString(R.string.detail_playlist_watch_later),
                    overview = null,
                    itemIds = ids,
                    mediaType = playlistMediaType(detail.item.mediaType),
                ).onSuccess { newId ->
                    appRuntimeStateStore.setWatchLaterPlaylistId(newId)
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_added_to_watch_later)))
                }.onFailure {
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                }
            }
            _uiState.update {
                it.copy(
                    isAddingToPlaylist = false,
                    showPlaylistPicker = false,
                )
            }
        }
    }

    /**
     * Creates a new playlist seeded with the current item and closes the
     * create-playlist dialog.
     */
    fun createAndAddPlaylist(name: String, overview: String) {
        val detail = _uiState.value.detail ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        launch {
            _uiState.update { it.copy(isAddingToPlaylist = true) }
            val ids = resolvePlaylistItemIds(detail).getOrElse {
                _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
                _uiState.update {
                    it.copy(
                        isAddingToPlaylist = false,
                        showCreatePlaylistDialog = false,
                    )
                }
                return@launch
            }
            mediaRepository.createPlaylist(
                name = trimmed,
                overview = overview.ifBlank { null },
                itemIds = ids,
                mediaType = playlistMediaType(detail.item.mediaType),
            ).onSuccess {
                _messages.emit(
                    DetailMessage.Text(context.getString(R.string.detail_msg_playlist_created, trimmed))
                )
            }.onFailure {
                _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_add_to_playlist)))
            }
            _uiState.update {
                it.copy(
                    isAddingToPlaylist = false,
                    showCreatePlaylistDialog = false,
                )
            }
        }
    }

    /**
     * Resolves the current item into the Jellyfin item ids to add to a playlist.
     * Movies/episodes/music-videos resolve to themselves; a series expands to
     * its fetched episodes (fetching them first if the seasons haven't loaded
     * yet — e.g. the user opened the picker before episodes resolved).
     */
    private suspend fun resolvePlaylistItemIds(
        detail: MediaDetail,
    ): Result<List<String>> = runCatching {
        val item = detail.item
        if (item.mediaType != MediaType.SERIES) return@runCatching listOf(item.id)
        // A series is playable as a playlist only via its episodes. Read the
        // canonical playback order from the catalogue snapshot if loaded, else
        // load it; an empty result is surfaced as a no-op message rather than
        // adding the (invalid) series id.
        val sortedIds = currentCatalogueSnapshot?.takeIf { it.seriesId == item.id }?.allEpisodeIds
        if (!sortedIds.isNullOrEmpty()) return@runCatching sortedIds
        episodeCatalogue.loadSeriesEpisodes(item.id)
            .getOrNull()
            ?.allEpisodeIds
            ?: emptyList()
    }

    /**
     * Maps the item's media type to the value passed to `createPlaylist`. The
     * network layer only cares whether the playlist is audio- or video-typed
     * (it branches to `SdkMediaType.AUDIO` vs `SdkMediaType.VIDEO`), so any
     * non-audio [MediaType] is equivalent here — [MediaType.MOVIE] is used as a
     * representative video type rather than adding a synthetic VIDEO constant.
     */
    private fun playlistMediaType(type: MediaType): MediaType =
        if (type.isAudioType) MediaType.AUDIO else MediaType.MOVIE

    override fun onCleared() {
        super.onCleared()
        themeMusicPlayer.stop()
    }
}

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
