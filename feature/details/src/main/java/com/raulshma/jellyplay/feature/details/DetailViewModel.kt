package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ResyncResult
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.core.model.seriesIdForDetail
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    /**
     * The single external seam for media-detail resolution. Owns the
     * remote/local source decision, the projected [MediaDetail], seasons/
     * episodes (via the shared EpisodeCatalogue), album tracks, local
     * subtitles, local artwork, the download/sync attachment, and capabilities.
     * [loadItemInternal] collects its [DetailLoadState] stream and reduces each
     * emission into [_uiState]; remote-only subordinate work (Seerr, Sonarr,
     * theme music, similar/collection items) fires off the resolved snapshot.
     */
    private val mediaDetailProvider: MediaDetailProvider,
    /**
     * Orchestrates offline download freshness checks and metadata/image resyncs.
     * [checkForUpdates] is TTL-gated (1h) and safe to fire on entry; [resyncItem]
     * powers the resync / re-download actions surfaced via [DetailUiState.resyncState].
     */
    private val offlineSyncManager: OfflineSyncManager,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
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
    private val syncPlayManager: SyncPlayManager,
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
    val uiState: StateFlow<DetailUiState> by lazy {
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
            // Group 4 — fold the extracted action helpers' state back into the
            // flat [DetailUiState]. The helpers own their StateFlows; this combine
            // projects them into the existing fields so composables + tests that
            // read uiState.* are unchanged. One emission per helper tick.
            .let { seerr ->
                combine(
                    seerr,
                    resyncActions.state,
                    playlistActions.state,
                    downloadLifecycleActions.state,
                    collectionActions.state,
                ) { primary, resync, playlist, download, collection ->
                    primary.copy(
                        resyncState = resync,
                        playlists = playlist.playlists,
                        isLoadingPlaylists = playlist.isLoadingPlaylists,
                        isAddingToPlaylist = playlist.isAddingToPlaylist,
                        showPlaylistPicker = playlist.showPlaylistPicker,
                        showCreatePlaylistDialog = playlist.showCreatePlaylistDialog,
                        collections = collection.collections,
                        isLoadingCollections = collection.isLoadingCollections,
                        isAddingToCollection = collection.isAddingToCollection,
                        showCollectionPicker = collection.showCollectionPicker,
                        showCreateCollectionDialog = collection.showCreateCollectionDialog,
                        isDownloading = download.isDownloading,
                        cellularDownloadWarningMb = download.cellularDownloadWarningMb,
                        isDownloadingSeries = download.isDownloadingSeries,
                        downloadSheetEpisodes = download.downloadSheetEpisodes,
                        downloadSheetLoadingSeasons = download.downloadSheetLoadingSeasons,
                        downloadedEpisodeIds = download.downloadedEpisodeIds,
                        downloadPicker = download.downloadPicker,
                    )
                }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
            }
    }

    // Direct (non-observable) readers for the two stream-selection indices.
    // These are read synchronously at click time inside the play callback
    // (which captures a `remember`-ed lambda), so they must read the current
    // snapshot from [_uiState] rather than a composition-captured value. All
    // other state is consumed reactively via [uiState].
    val selectedSubtitleIndex: Int? get() = _uiState.value.selectedSubtitleIndex
    val selectedAudioIndex: Int? get() = _uiState.value.selectedAudioIndex
    val selectedLocalSubtitleIndex: Int? get() = _uiState.value.selectedLocalSubtitleIndex

    // Internal caches (not observable UI state). Mutations happen on the Main
    // dispatcher (viewModelScope). Seasons/episodes/sortedEpisodes all live in
    // [MediaDetailSnapshot] now — the provider owns the read graph and the
    // optimistic rewrite, so the VM keeps no local catalogue snapshot. The
    // download sheet's per-season cache moved into [downloadLifecycleActions].
    private var loadJob: Job? = null
    private var currentItemId: String? = null
    private var currentSeriesId: String? = null
    private var seerrDataLoaded = false
    private var seerrDataGeneration = 0L
    /** Serializes user-data mutations so rapid taps resolve in input order. */
    private val userDataMutationMutex = Mutex()
    /**
     * The [MediaDetailSnapshot.contentGeneration] of the last snapshot whose
     * *content* sections (detail, seasons, episodes, album tracks, subtitles,
     * origin) were fully reduced into [_uiState]. The provider guarantees stable
     * content references + the same generation across attachment-only ticks, so
     * a value equal to the incoming snapshot's generation means "attachment tick"
     * — we update only [DetailUiState.detailContext]/[DetailUiState.capabilities]/
     * [DetailUiState.assets]/[DetailUiState.localSubtitles] and leave the
     * consumer's optimistic content (watched/favorite flips, episodes) untouched.
     * Reset to -1 in [loadItemInternal] so the first Loaded emission of every
     * screen entry is treated as a fresh resolution.
     */
    private var lastAppliedGeneration = -1L

    // ── Extracted action helpers ────────────────────────────────────────
    // Each follows the SeerrRequestStateHolder template: a plain, VM-scoped
    // class (constructed here, dies with viewModelScope) that owns its own
    // coroutines and state. The VM keeps thin delegating methods below so
    // existing callers and tests stay stable; each helper is independently
    // unit-testable. Stateful helpers expose a StateFlow folded into [uiState]
    // (see the combine below); one-shot messages flow back through _messages.
    private val offlineDeleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
        episodesProvider = { _uiState.value.episodes },
        seasonsProvider = { _uiState.value.seasons },
        onContentMutated = ::refreshAfterOfflineMutation,
    )
    private val resyncActions = ResyncActions(
        scope = scope,
        offlineSyncManager = offlineSyncManager,
        mediaRepository = mediaRepository,
        offlineRepository = offlineRepository,
        downloadIntake = downloadIntake,
        context = context,
        itemIdProvider = { currentItemId },
    )
    private val markSeasonReactor = MarkSeasonReactor(
        scope = scope,
        mediaRepository = mediaRepository,
        context = context,
        itemIdProvider = { currentItemId },
        episodesProvider = { _uiState.value.episodes },
        applyRewrite = mediaDetailProvider::applyOptimisticSeasonRewrite,
        messageSink = { _messages.tryEmit(it) },
        seriesIdProvider = { currentSeriesId },
        onItemPlayed = { itemId, played, seriesId ->
            updatePlayedStateInUi(itemId, played)
            applyOptimisticItemMutation(
                itemId = itemId,
                isPlayed = played,
                seriesId = seriesId,
            )
        },
        mutationMutex = userDataMutationMutex,
    )
    private val playlistActions = PlaylistActions(
        scope = scope,
        mediaRepository = mediaRepository,
        appRuntimeStateStore = appRuntimeStateStore,
        context = context,
        detailProvider = { _uiState.value.detail },
        sortedEpisodesProvider = { _uiState.value.sortedEpisodes },
        canonicalEpisodeIds = mediaDetailProvider::canonicalEpisodeIds,
        messageSink = { _messages.tryEmit(it) },
    )
    private val collectionActions = CollectionActions(
        scope = scope,
        mediaRepository = mediaRepository,
        context = context,
        detailProvider = { _uiState.value.detail },
        sortedEpisodesProvider = { _uiState.value.sortedEpisodes },
        canonicalEpisodeIds = mediaDetailProvider::canonicalEpisodeIds,
        messageSink = { _messages.tryEmit(it) },
    )
    private val instantMixActions = InstantMixActions(
        scope = scope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        audioPlaybackManager = audioPlaybackManager,
        context = context,
        detailProvider = { _uiState.value.detail },
        currentItemProvider = { currentItemId },
        messageSink = { _messages.tryEmit(it) },
    )
    private val downloadLifecycleActions = DownloadLifecycleActions(
        scope = scope,
        downloadIntake = downloadIntake,
        downloadsStore = downloadsStore,
        adaptiveBitrateManager = adaptiveBitrateManager,
        downloadRepository = downloadRepository,
        context = context,
        detailProvider = { _uiState.value.detail },
        seasonsProvider = { _uiState.value.seasons },
        currentSeriesIdProvider = { currentSeriesId },
        itemIdProvider = { currentItemId },
        expandSeason = mediaDetailProvider::expandSeason,
        messageSink = { _messages.tryEmit(it) },
    )
    private val watchPartyActions = WatchPartyActions(
        mediaRepository = mediaRepository,
        syncPlayManager = syncPlayManager,
        context = context,
        messageSink = { _messages.tryEmit(it) },
    )

    fun selectSubtitle(index: Int?) {
        _uiState.update { it.copy(selectedSubtitleIndex = index) }
        persistStreamSelection(subtitleIndex = index, audioIndex = _uiState.value.selectedAudioIndex)
    }

    fun selectAudio(index: Int?) {
        _uiState.update { it.copy(selectedAudioIndex = index) }
        persistStreamSelection(subtitleIndex = _uiState.value.selectedSubtitleIndex, audioIndex = index)
    }

    /**
     * Persists a manifest-backed local-subtitle selection for the current item.
     *
     * Sets [DetailUiState.selectedLocalSubtitleIndex] in [_uiState] AND persists
     * via [PlayerEngineStore.setMediaStreamSelection] (subtitleStreamIndex =
     * [index], audioStreamIndex = the existing remote audio index). A spike
     * confirmed `mediaStreamSelections[itemId]` is honored offline, and the
     * player resolves the side-loaded subtitle by its `offline:${index}` id via
     * `TrackSelectionPolicy.resolveByOfflineSubtitleId` (wired in this change).
     *
     * Distinct from [selectSubtitle]: that writes the REMOTE subtitle stream
     * index for a server source; this writes the local-manifest index for a
     * downloaded file (the two are independent selection spaces).
     */
    fun selectLocalSubtitle(index: Int?) {
        _uiState.update { it.copy(selectedLocalSubtitleIndex = index) }
        persistStreamSelection(subtitleIndex = index, audioIndex = _uiState.value.selectedAudioIndex)
    }

    /**
     * Loads the on-disk file inventory (media + sidecar artifacts) for the
     * download-details sheet. Runs only when the sheet opens — sidecar sizes
     * aren't persisted, so this is the one place the filesystem walk executes.
     * Resolves the item id from the current snapshot, falling back to
     * [currentItemId] so a not-yet-snapshotted download still inventories.
     */
    fun loadDownloadFileInventory() {
        val itemId = _uiState.value.detail?.item?.id ?: currentItemId ?: return
        _uiState.update { it.copy(isLoadingDownloadFiles = true) }
        launch {
            val inventory = downloadRepository.getDownloadFileInventory(itemId)
            _uiState.update {
                it.copy(downloadFileInventory = inventory, isLoadingDownloadFiles = false)
            }
        }
    }

    /** Clears the loaded inventory so the next sheet open re-reads fresh sizes. */
    fun clearDownloadFileInventory() {
        _uiState.update {
            it.copy(downloadFileInventory = null, isLoadingDownloadFiles = false)
        }
    }

    /**
     * Single persistence seam for the stream selectors: writes the resolved
     * subtitle/audio pair to [PlayerEngineStore.setMediaStreamSelection]. Extracted
     * so [selectSubtitle], [selectAudio], and [selectLocalSubtitle] cannot drift
     * apart in how they resolve the current item id or launch the write.
     */
    private fun persistStreamSelection(subtitleIndex: Int?, audioIndex: Int?) {
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            engineStore.setMediaStreamSelection(
                itemId = itemId,
                subtitleStreamIndex = subtitleIndex,
                audioStreamIndex = audioIndex,
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
     * Pull-to-refresh: delegates to [MediaDetailProvider.refresh], which owns
     * the per-type cache invalidation (detail, seasons/episodes, album, collection)
     * and re-resolves the snapshot. Unlike [loadItem] the current content stays
     * on screen (the full-screen loading state is skipped); the pull-to-refresh
     * indicator is driven by [DetailUiLoadState.Refreshing] (via
     * [DetailUiState.loadState]) instead.
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
                    loadState = if (refresh) DetailUiLoadState.Refreshing else DetailUiLoadState.Loading,
                    origin = null,
                    detailContext = null,
                    capabilities = DetailUiState.DefaultCapabilities,
                    assets = com.raulshma.jellyplay.core.model.DetailAssets(),
                    localSubtitles = emptyList(),
                    selectedLocalSubtitleIndex = null,
                    seasons = emptyList(),
                    episodes = emptyMap(),
                    fetchedSeasonIds = emptySet(),
                    collectionItems = emptyList(),
                    relatedItems = emptyList(),
                    localRelatedItems = emptyList(),
                    specialFeatures = emptyList(),
                    albumTracks = emptyList(),
                    // Segment availability is only re-populated on the REMOTE
                    // success path of triggerRemoteSideEffects; reset here so a
                    // navigation to a LOCAL item (or a failed REMOTE fetch) can't
                    // leave the prior item's "skip available" chip stale.
                    hasIntroSegment = false,
                    hasCreditSegment = false,
                    smartPlayTarget = null,
                    selectedSubtitleIndex = null,
                    selectedAudioIndex = null,
                    seerrRecommendations = emptyList(),
                    seerrSimilar = emptyList(),
                    relatedVideos = emptyList(),
                    sonarrServersResolved = false,
                    // NOTE: isDownloading / isDownloadingSeries / download-sheet
                    // fields are owned by [downloadLifecycleActions] and reset
                    // via resetForNavigation() below, not here.
                )
            }
            // Drop the provider's catalogue cache for any series we were viewing
            // so the new item's load starts fresh (the VM is reused across
            // navigations). The provider owns the catalogue internally now.
            currentSeriesId?.let { mediaDetailProvider.invalidate(it) }
            currentSeriesId = null
            seerrDataLoaded = false
            // Bump the seerr generation so any in-flight trailer/video/recommendation
            // fetch from the *previous* item is invalidated and cannot write its stale
            // results onto this item's screen (the VM is shared across detail navigations).
            seerrDataGeneration++
            // Reset the content-generation guard so the first Loaded emission of this
            // screen entry is treated as a fresh resolution (fires remote side effects,
            // adopts content sections). The provider never emits a generation of -1.
            lastAppliedGeneration = -1L
            // Reset the download-lifecycle helper's state + sheet caches, since the
            // same VM instance is reused across navigations.
            downloadLifecycleActions.resetForNavigation()
            if (refresh) {
                // The provider owns per-type cache invalidation + the remote refetch.
                // Suspend until the new generation lands so the collector that follows
                // observes the refreshed snapshot rather than a stale replay; the detail
                // stays visible (kept above) under the Refreshing indicator meanwhile.
                mediaDetailProvider.refresh(itemId)
            }
            mediaDetailProvider.observe(itemId).collect { state ->
                // Stale-write guard: a collector from a previous itemId is cancelled
                // by loadJob?.cancel() on the next loadItem, but defend in depth.
                if (currentItemId != itemId) return@collect
                applyLoadState(itemId, state)
            }
        }
    }

    /**
     * Reduces a single [DetailLoadState] from the provider into [_uiState].
     *
     * - [DetailLoadState.Loading]: surfaces a full-screen loading state only
     *   when no content is shown yet; never clears an already-rendered detail.
     * - [DetailLoadState.Error]: classifies the provider's error into the
     *   unavailable-offline / access-denied / generic buckets.
     * - [DetailLoadState.Loaded]: either a full content reduction (new
     *   [MediaDetailSnapshot.contentGeneration]) or an attachment-only tick
     *   (same generation), per the clobber-protection contract in [reduceLoaded].
     */
    private fun applyLoadState(itemId: String, state: DetailLoadState) {
        when (state) {
            DetailLoadState.Loading -> {
                // Only flip into the full-screen loading state when there is no
                // content to show. A transient Loading after content is rendered
                // (e.g. a provider re-resolution) must NOT blank the screen; the
                // prior detail stays visible until the next Loaded replaces it.
                _uiState.update {
                    it.copy(
                        loadState = if (it.detail == null) DetailUiLoadState.Loading else DetailUiLoadState.Loaded,
                    )
                }
            }
            is DetailLoadState.Error -> {
                val e = state.error
                val message: String
                val accessDenied: Boolean
                when {
                    e.isUnavailableOffline -> {
                        message = context.getString(R.string.detail_error_unavailable_offline)
                        accessDenied = false
                    }
                    e.isAccessDenied -> {
                        message = context.getString(R.string.detail_error_access_denied)
                        accessDenied = true
                    }
                    else -> {
                        message = e.message.ifBlank { context.getString(R.string.detail_error_load_failed) }
                        accessDenied = false
                    }
                }
                _uiState.update {
                    it.copy(
                        loadState = DetailUiLoadState.Error(
                            message = message,
                            accessDenied = accessDenied,
                            unavailableOffline = e.isUnavailableOffline,
                        ),
                    )
                }
            }
            is DetailLoadState.Loaded -> reduceLoaded(itemId, state.snapshot)
        }
    }

    /**
     * Reduces a resolved [MediaDetailSnapshot] into [_uiState].
     *
     * Two paths, keyed on [MediaDetailSnapshot.contentGeneration] vs
     * [lastAppliedGeneration]:
     *
     * 1. **New resolution** (generation changed): adopts the content sections
     *    (detail, seasons, episodes, album tracks, local subtitles, origin,
     *    assets, capabilities, detailContext) atomically, clears the smart-play
     *    target, then recomputes it — for both origins, since a LOCAL series
     *    carries the same loaded episode data and should expose the same
     *    Play/Resume/Next up target. For a REMOTE origin, additionally fires the
     *    remote-only subordinate work (similar/collection items, theme music,
     *    Sonarr resolution, Seerr discovery) exactly once per resolution. For a
     *    LOCAL origin no remote coroutines start.
     * 2. **Attachment tick** (same generation): updates ONLY detailContext,
     *    capabilities, assets and localSubtitles — the provider guarantees
     *    stable content references across attachment ticks, so the consumer's
     *    optimistic mutations (watched/favorite flips, episode rewrites) survive
     *    download-progress / sync-state re-emissions without being clobbered.
     */
    private fun reduceLoaded(itemId: String, snapshot: MediaDetailSnapshot) {
        val detail = snapshot.detail
        val isRemote = snapshot.context.origin == DetailOrigin.REMOTE
        val isNewResolution = snapshot.contentGeneration != lastAppliedGeneration
        if (!isNewResolution) {
            // Attachment-only tick: preserve the consumer's optimistic content.
            _uiState.update {
                it.copy(
                    detailContext = snapshot.context,
                    capabilities = snapshot.capabilities,
                    assets = snapshot.assets,
                    localSubtitles = snapshot.localSubtitles,
                )
            }
            return
        }

        // New resolution: adopt content sections wholesale.
        currentSeriesId = detail.item.seriesIdForDetail

        // Stream selection: remote applies the persisted engine-store selection;
        // local clears it (local playback uses the separate local-subtitle index).
        val (subtitleIndex, audioIndex) = if (isRemote) {
            val stored = engineStore.playerEngine.value.mediaStreamSelections[itemId]
            stored?.subtitleStreamIndex to stored?.audioStreamIndex
        } else {
            null to null
        }

        _uiState.update {
            it.copy(
                detail = detail,
                origin = snapshot.context.origin,
                detailContext = snapshot.context,
                capabilities = snapshot.capabilities,
                assets = snapshot.assets,
                localSubtitles = snapshot.localSubtitles,
                seasons = snapshot.seasons,
                episodes = snapshot.episodesBySeason,
                fetchedSeasonIds = snapshot.fetchedSeasonIds,
                sortedEpisodes = snapshot.sortedEpisodes,
                albumTracks = snapshot.albumTracks,
                selectedSubtitleIndex = subtitleIndex,
                selectedAudioIndex = audioIndex,
                // Smart-play is recomputed below; cleared first so a stale target
                // from the previous item never survives a resolution change.
                smartPlayTarget = null,
                contentGeneration = snapshot.contentGeneration,
                loadState = DetailUiLoadState.Loaded,
            )
        }
        lastAppliedGeneration = snapshot.contentGeneration

        // Smart-play targets the next episode to watch from the already-loaded
        // sorted episodes, so it runs for both origins: a LOCAL series with
        // downloaded episodes shows the same Play/Resume/Next up target (and Up
        // Next section) as its remote counterpart. Only the remote-only
        // subordinate work stays gated on isRemote below.
        maybeComputeSmartPlayTarget()
        if (isRemote) {
            triggerRemoteSideEffects(itemId, detail, snapshot.capabilities)
        } else {
            triggerLocalSideEffects(itemId, detail)
        }
        // Collections are remote-only companion content (not part of the snapshot);
        // gated on remoteDiscovery so a local origin never starts it.
        if (isRemote && snapshot.capabilities.remoteDiscovery &&
            detail.item.mediaType == MediaType.COLLECTION
        ) {
            loadCollectionItems(itemId)
        }
    }

    /**
     * Fires the remote-only subordinate work for a freshly-resolved REMOTE
     * snapshot. Each launch captures [itemId] and bails if navigation moved on,
     * mirroring the seerrDataGeneration guard. All branches are additionally
     * gated on [DetailCapabilities.remoteDiscovery] so the capability flip is
     * the single authority for whether discovery may run.
     */
    private fun triggerRemoteSideEffects(
        itemId: String,
        detail: MediaDetail,
        capabilities: DetailCapabilities,
    ) {
        if (!capabilities.remoteDiscovery) return
        val themeSourceId = detail.item.seriesId ?: itemId
        themeMusicPlayer.playThemeFor(themeSourceId)
        when (detail.item.mediaType) {
            MediaType.SERIES, MediaType.EPISODE -> resolveSonarrForSeries(detail)
            else -> Unit
        }
        // Fetch similar/related items concurrently and non-blocking so the core
        // detail renders immediately. The result lands in relatedItems.
        launch {
            mediaRepository.getSimilarItems(itemId, limit = 12)
                .onSuccess { items ->
                    if (currentItemId != itemId) return@onSuccess
                    _uiState.update {
                        it.copy(relatedItems = items.filter { related -> related.id != itemId })
                    }
                }
        }
        // Fetch special features / extras (featurettes, deleted scenes, etc.)
        // concurrently so the core detail renders immediately; the result lands
        // in specialFeatures and renders as its own horizontal row.
        launch {
            mediaRepository.getSpecialFeatures(itemId)
                .onSuccess { extras ->
                    if (currentItemId != itemId) return@onSuccess
                    _uiState.update { it.copy(specialFeatures = extras) }
                }
        }
        // Pre-warm the player's media-segment TTL cache and surface the two
        // intro/credits skip affordances as a detail-side chip. The cache fill
        // is an implicit side effect of the call; only the booleans flow into
        // uiState so the chip can render before the player attaches.
        launch {
            playbackRepository.getMediaSegments(itemId).onSuccess { segments ->
                if (currentItemId != itemId) return@onSuccess
                val availability = segments.toAvailability()
                _uiState.update {
                    it.copy(
                        hasIntroSegment = availability.hasIntro,
                        hasCreditSegment = availability.hasCredits,
                    )
                }
            }
        }
        // Trigger the Seerr recommendations/videos fetch from the VM. The 350ms
        // delay is preserved for frame priority (don't contend with first-frame
        // GPU work); seerrDataLoaded keeps it idempotent across re-entries.
        launch {
            kotlinx.coroutines.delay(350)
            if (currentItemId != itemId) return@launch
            loadSeerrDataIfNeeded(detail)
        }
    }

    /**
     * LOCAL-origin counterpart to [triggerRemoteSideEffects]. Remote discovery
     * (similar/Seerr/trailers) is server-only, so a downloaded item would
     * otherwise render as an island. Instead we mine the on-device offline
     * library for titles sharing a genre (then studio) and surface them in the
     * same "More like this" row with an "On-device" label. Read-only single
     * fetch — no helper class — over the already-indexed offline rows.
     */
    private fun triggerLocalSideEffects(itemId: String, detail: MediaDetail) {
        val genres = detail.item.genres
        val studios = detail.item.studios
        if (genres.isEmpty() && studios.isEmpty()) return
        launch {
            val related = offlineRepository.getLocalRelated(
                currentId = itemId,
                genres = genres,
                studios = studios,
                limit = 12,
            )
            if (currentItemId != itemId) return@launch
            _uiState.update {
                it.copy(localRelatedItems = related.filter { r -> r.id != itemId })
            }
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

    /**
     * On-demand per-season expand. The provider supplies seasons/episodes up
     * front (including [DetailUiState.fetchedSeasonIds]); a season NOT in that
     * set (e.g. the mismatched-season-key edge) is fetched here through the
     * provider, which merges it into its snapshot and re-emits a new-generation
     * [MediaDetailSnapshot] via [observe]. [reduceLoaded] adopts the merged
     * episodes and recomputes smart-play — no local uiState merge needed.
     */
    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_uiState.value.fetchedSeasonIds.contains(seasonId)) return
        val itemId = currentItemId ?: return
        launch {
            if (currentSeriesId != seriesId) return@launch
            // expandSeason fetches the season via the catalogue (serving from
            // its cached snapshot when present, else fetching the one season),
            // merges it into the provider's content, and re-emits. The reducer
            // picks up the new snapshot on the next observe() emission.
            mediaDetailProvider.expandSeason(itemId, seasonId)
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

    // ── Instant mix ────────────────────────────────────────────────────
    // Delegated to [instantMixActions] (InstantMixActions). The VM keeps a thin
    // pass-through so callers/tests stay stable; the helper owns the mix fetch
    // + queue build + navigation-drift guard.

    /**
     * Starts a Jellyfin instant mix for the current audio item. Delegates to
     * [InstantMixActions]; see that class for the fetch + queue-build contract.
     */
    fun startInstantMix() = instantMixActions.startInstantMix()

    // ── Watch party ────────────────────────────────────────────────────
    // Delegated to [watchPartyActions] (WatchPartyActions). The VM resolves the
    // current item into the bootstrap params (id / group title / default media
    // source) and launches the coroutine; the helper owns the create→join→queue
    // sequence and emits success/failure via DetailMessage.

    /**
     * Bootstraps a SyncPlay watch party for the current item and opens the
     * player on success. The group is named after the item (falling back to a
     * generic label) and seeded with the item's default media source at position
     * 0. The player is opened by [MediaDetailScreen] on
     * [DetailMessage.WatchPartyStarted]; the existing SyncPlayBridge then
     * auto-detects the active session. Fire-and-forget from the UI's standpoint
     * — success/failure flow back as one-shot messages.
     */
    fun startWatchParty() {
        val detail = _uiState.value.detail ?: return
        val item = detail.item
        val itemId = item.id
        val title = item.name.orEmpty().ifBlank {
            context.getString(R.string.detail_watch_party_default_name)
        }
        val mediaSourceId = detail.mediaSources.firstOrNull()?.id
        launch {
            watchPartyActions.start(itemId, title, mediaSourceId)
        }
    }

    /**
     * Plays a single LOCAL-origin album track.
     *
     * The remote [playAlbum] builds a queue via [AudioPlaybackManager.playQueue],
     * which depends on a server [MediaRepository.getMediaDetail] fetch. For a
     * local track we instead use the per-item [AudioPlaybackManager.play], which
     * has its own local-source fallback (`resolveLocalSource`) when the server
     * fetch fails — so a downloaded track plays without a server round-trip.
     *
     * Decision (plan §I): `AudioPlaybackManager.play(itemId)` exists and carries
     * the local-source fallback, so it is used directly rather than routing
     * through `onAudioClick` → `Route.AudioPlayer`. `play()` asserts the main
     * thread (ExoPlayer contract); the click handler runs on the main thread.
     */
    fun playLocalTrack(itemId: String) {
        audioPlaybackManager.play(itemId)
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
            // Check pending seasons BEFORE reading the sorted list — a season
            // not yet in fetchedSeasonIds means episodes are incomplete and
            // smart-play would target the wrong episode.
            val seasonsPending = state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }
            if (seasonsPending) return@launch
            val sorted = state.sortedEpisodes.takeIf { it.isNotEmpty() } ?: return@launch
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
            val sorted = _uiState.value.sortedEpisodes.takeIf { it.isNotEmpty() } ?: return@launch
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
        launch {
            userDataMutationMutex.withLock {
                val itemId = _uiState.value.detail?.item?.id ?: return@withLock
                mediaRepository.toggleFavorite(itemId)
                .onSuccess {
                    val targetIsFavorite = it
                    applyFavoriteMutation(itemId, targetIsFavorite)
                }
                .onFailure {
                    // Don't leave the user guessing why the heart didn't flip.
                    _messages.emit(DetailMessage.Text(context.getString(R.string.detail_msg_couldnt_update_favorite)))
                }
            }
        }
    }

    fun markPlayed() = setPlayed(played = true)

    fun markUnplayed() = setPlayed(played = false)

    /**
     * Shared optimistic watched-toggle for the detail item. Jellyfin clears a
     * manually (un)watched item's resume point, so both directions mirror that
     * immediately — the detail UI cannot retain an in-progress bar while the
     * queued/offline mutation syncs.
     */
    private fun setPlayed(played: Boolean) {
        launch {
            userDataMutationMutex.withLock {
                val itemId = _uiState.value.detail?.item?.id ?: return@withLock
                val result = if (played) mediaRepository.markPlayed(itemId)
                else mediaRepository.markUnplayed(itemId)
                result.onSuccess {
                    updatePlayedStateInUi(itemId, played)
                    applyOptimisticItemMutation(
                        itemId = itemId,
                        isPlayed = played,
                    )
                }.onFailure {
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
    }

    /**
     * Applies a played flip to every visible projection of an item. Detail
     * actions can target the current item, a related/collection card, or an
     * episode card; keeping these projections together prevents one card from
     * replaying the old state until the next full detail load.
     */
    private fun updatePlayedStateInUi(itemId: String, played: Boolean) {
        var shouldRecomputeSmartPlay = false
        _uiState.update { state ->
            val currentDetail = state.detail
            val isCurrentDetail = currentDetail?.item?.id == itemId
            shouldRecomputeSmartPlay = isCurrentDetail || state.sortedEpisodes.any { it.id == itemId }
            val updateItem: (MediaItem) -> MediaItem = { item ->
                if (item.id == itemId) item.copy(isPlayed = played, playbackPositionTicks = 0L) else item
            }
            state.copy(
                detail = currentDetail?.let { detail ->
                    if (isCurrentDetail) detail.copy(item = updateItem(detail.item)) else detail
                },
                relatedItems = state.relatedItems.map(updateItem),
                collectionItems = state.collectionItems.map(updateItem),
                episodes = state.episodes.mapValues { (_, episodes) -> episodes.map(updateItem) },
                sortedEpisodes = state.sortedEpisodes.map(updateItem),
            )
        }
        if (shouldRecomputeSmartPlay) maybeComputeSmartPlayTarget()
    }

    /** Updates the active provider snapshot and invalidates any parent series cache. */
    private suspend fun applyOptimisticItemMutation(
        itemId: String,
        isFavorite: Boolean? = null,
        isPlayed: Boolean? = null,
        seriesId: String? = null,
    ) {
        mediaDetailProvider.applyOptimisticItemState(
            itemId = itemId,
            isFavorite = isFavorite,
            isPlayed = isPlayed,
        )
        (seriesId ?: seriesIdForItem(itemId))?.let(mediaDetailProvider::invalidate)
        currentItemId?.takeIf { it != itemId }?.let { parentItemId ->
            // Related/collection cards are loaded through parent-keyed caches,
            // so invalidating only the mutated item's detail is insufficient.
            mediaRepository.invalidateDetailCache(parentItemId)
            mediaRepository.invalidateCollectionItemsCache(parentItemId)
        }
    }

    private fun seriesIdForItem(itemId: String): String? {
        val state = _uiState.value
        val episodeSeriesId = state.episodes.values
            .asSequence()
            .flatten()
            .firstOrNull { it.id == itemId }
            ?.seriesId
        if (episodeSeriesId != null) return episodeSeriesId
        return state.detail?.item
            ?.takeIf { it.id == itemId }
            ?.seriesIdForDetail
    }

    /** Keeps every visible projection of an item aligned after a favorite flip. */
    private fun updateFavoriteStateInUi(itemId: String, favorite: Boolean) {
        _uiState.update { state ->
            val currentDetail = state.detail
            val isCurrentDetail = currentDetail?.item?.id == itemId
            val updateItem: (MediaItem) -> MediaItem = { item ->
                if (item.id == itemId) item.copy(isFavorite = favorite) else item
            }
            state.copy(
                detail = currentDetail?.let { detail ->
                    if (isCurrentDetail) detail.copy(item = updateItem(detail.item)) else detail
                },
                relatedItems = state.relatedItems.map(updateItem),
                collectionItems = state.collectionItems.map(updateItem),
                episodes = state.episodes.mapValues { (_, episodes) -> episodes.map(updateItem) },
                sortedEpisodes = state.sortedEpisodes.map(updateItem),
            )
        }
    }

    private suspend fun applyFavoriteMutation(itemId: String, favorite: Boolean) {
        updateFavoriteStateInUi(itemId, favorite)
        applyOptimisticItemMutation(itemId = itemId, isFavorite = favorite)
    }

    /**
     * Marks a row item (related/collection/episode) played or
     * unplayed without switching the screen's current detail item. Flips the
     * item in-place across all visible projections and invalidates the parent
     * content/catalogue caches so re-entry cannot replay the old state.
     */
    fun markRowItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutationMutex.withLock {
                val result = if (played) mediaRepository.markPlayed(item.id)
                else mediaRepository.markUnplayed(item.id)
                result.onSuccess {
                    updatePlayedStateInUi(item.id, played)
                    applyOptimisticItemMutation(
                        itemId = item.id,
                        isPlayed = played,
                        seriesId = item.seriesId,
                    )
                }
            }
        }
    }

    /**
     * Marks every episode in [seasonId] as played. The optimistic rewrite goes
     * through [MediaDetailProvider.applyOptimisticSeasonRewrite]; the reducer
     * adopts it + recomputes smart-play. Delegates to [MarkSeasonReactor] — see
     * there for the no-refetch / re-entry invalidation contract.
     */
    fun markSeasonPlayed(seasonId: String) = markSeasonReactor.markSeasonPlayed(seasonId)

    fun markSeasonUnplayed(seasonId: String) = markSeasonReactor.markSeasonUnplayed(seasonId)

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

    /**
     * Silently pins the last-viewed season for [seriesId] so the series detail
     * screen reopens on that season tab. Mirrors [hideFromNextUp]'s launch shape
     * but emits NO user-facing message (a background preference write). The
     * value flows back reactively via [preferences].
     */
    fun setLastViewedSeason(seriesId: String, seasonId: String) {
        launch {
            homeDiscoveryStore.setLastViewedSeason(seriesId, seasonId)
        }
    }

    fun setShowDetailUpNext(enabled: Boolean) {
        launch {
            libraryStore.setShowDetailUpNext(enabled)
        }
    }

    fun startDownload() = downloadLifecycleActions.startDownload()

    // ── Pre-download picker (quality + external-subtitle selection) ──
    fun openDownloadPicker() = downloadLifecycleActions.openDownloadPicker()
    fun dismissDownloadPicker() = downloadLifecycleActions.dismissDownloadPicker()
    fun setPendingDownloadQuality(quality: DownloadQuality) =
        downloadLifecycleActions.setPendingQuality(quality)
    fun setPendingSubtitleSelection(selection: SubtitleSelection) =
        downloadLifecycleActions.setPendingSubtitleSelection(selection)

    /**
     * Called from the UI after the user explicitly confirms a cellular download
     * that exceeded the warning threshold. Delegates to [DownloadLifecycleActions].
     */
    fun confirmCellularDownload() = downloadLifecycleActions.confirmCellularDownload()

    fun dismissCellularDownloadWarning() = downloadLifecycleActions.dismissCellularDownloadWarning()

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /** Chapter thumbnail URL for the detail-screen chapter row. */
    fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?): String =
        imageUrlProvider.getChapterImageUrl(itemId, imageIndex, tag)

    fun downloadSeries(episodeIds: Map<String, List<String>>? = null) =
        downloadLifecycleActions.downloadSeries(episodeIds)

    fun prepareDownloadSheetEpisodes() = downloadLifecycleActions.prepareDownloadSheetEpisodes()

    fun loadDownloadSheetEpisodes(seasonId: String) =
        downloadLifecycleActions.loadDownloadSheetEpisodes(seasonId)

    fun loadDownloadedEpisodeIds() = downloadLifecycleActions.loadDownloadedEpisodeIds()

    fun resetDownloadSheetState() = downloadLifecycleActions.resetDownloadSheetState()

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

    // ── Add to Playlist ────────────────────────────────────────────────
    // Delegated to [playlistActions] (PlaylistActions). The VM keeps thin
    // pass-throughs so existing callers/tests stay stable; the helper owns the
    // playlist state machine + the series→episode-id expansion.

    /** Opens the Add-to-Playlist picker. Delegates to [PlaylistActions]. */
    fun openPlaylistPicker() = playlistActions.openPlaylistPicker()

    fun dismissPlaylistPicker() = playlistActions.dismissPlaylistPicker()

    fun openCreatePlaylistDialog() = playlistActions.openCreatePlaylistDialog()

    fun dismissCreatePlaylistDialog() = playlistActions.dismissCreatePlaylistDialog()

    /** Adds the current item to an existing playlist. Delegates to [PlaylistActions]. */
    fun addToPlaylist(playlist: com.raulshma.jellyplay.core.model.Playlist) =
        playlistActions.addToPlaylist(playlist)

    /** Adds to the reserved "Watch Later" playlist. Delegates to [PlaylistActions]. */
    fun addToWatchLater() = playlistActions.addToWatchLater()

    /** Creates a new playlist seeded with the current item. Delegates to [PlaylistActions]. */
    fun createAndAddPlaylist(name: String, overview: String) =
        playlistActions.createAndAddPlaylist(name, overview)

    // ── Add to Collection ───────────────────────────────────────────────
    // Delegated to [collectionActions] (CollectionActions). The VM keeps thin
    // pass-throughs so callers/tests stay stable; the helper owns the
    // collection state machine + the series→episode-id expansion. A mirror of
    // the playlist block above, minus the Watch Later bucket (collections have
    // none) and minus the media-type tagging (the create endpoint takes only a
    // name).

    /** Opens the Add-to-Collection picker. Delegates to [CollectionActions]. */
    fun openCollectionPicker() = collectionActions.openCollectionPicker()

    fun dismissCollectionPicker() = collectionActions.dismissCollectionPicker()

    fun openCreateCollectionDialog() = collectionActions.openCreateCollectionDialog()

    fun dismissCreateCollectionDialog() = collectionActions.dismissCreateCollectionDialog()

    /** Adds the current item to an existing collection. Delegates to [CollectionActions]. */
    fun addToCollection(collection: com.raulshma.jellyplay.core.model.CollectionSummary) =
        collectionActions.addToCollection(collection)

    /** Creates a new collection seeded with the current item. Delegates to [CollectionActions]. */
    fun createAndAddCollection(name: String) =
        collectionActions.createAndAddCollection(name)

    // ── Offline / download-lifecycle management ──────────────────────────
    // Ports the operations previously owned by OfflineDetailViewModel and
    // OfflineSeriesViewModel so the unified detail screen can manage a local
    // download in place. These read the reactive
    // [DetailUiState.detailContext] attachment for capability gating and act on
    // the current item or the passed ids. Per-item write actions route through
    // the offline-aware PlayedStateSync / playback outbox (mirroring today).

    /**
     * Called by [OfflineDeleteActions] after each delete transaction lands. The
     * provider only re-resolves content on a refresh tick, and the reducer
     * short-circuits same-generation attachment ticks — so without this the
     * screen would keep showing the pre-delete episodes (or, after a re-resolve,
     * a stuck loading spinner / "Finding Episode" on an emptied season) until
     * the next navigation. Drop the now-stale series catalogue and re-resolve
     * the current view. The refresh is gated to local views: a remote view's
     * server episode list is unchanged by a download delete (the attachment
     * flow already refreshes download badges), so refreshing it would only
     * wastefully refetch from the server.
     */
    private fun refreshAfterOfflineMutation() {
        val seriesId = currentSeriesId ?: return
        mediaDetailProvider.invalidate(seriesId)
        val itemId = currentItemId ?: return
        if (_uiState.value.origin?.isLocal == true) {
            launch { mediaDetailProvider.refresh(itemId) }
        }
    }

    /** Deletes a single downloaded item by id. Delegates to [OfflineDeleteActions]. */
    fun deleteOfflineItem(id: String) = offlineDeleteActions.deleteOfflineItem(id)

    /** Deletes a single downloaded episode by id. Delegates to [OfflineDeleteActions]. */
    fun deleteOfflineEpisode(episodeId: String) =
        offlineDeleteActions.deleteOfflineEpisode(episodeId)

    /**
     * Deletes a batch of downloaded episodes. Whole-season selections collapse
     * into one [offlineRepository.deleteOfflineSeason] transaction; partial
     * selections fall back to per-episode deletes. See [OfflineDeleteActions].
     */
    fun deleteOfflineEpisodes(episodeIds: List<String>) =
        offlineDeleteActions.deleteOfflineEpisodes(episodeIds)

    /** Drops an entire downloaded season (one DB transaction + artifact cleanup). */
    fun deleteOfflineSeason(seasonId: String) = offlineDeleteActions.deleteOfflineSeason(seasonId)

    /** Drops an entire downloaded series and all its seasons/episodes. */
    fun deleteOfflineSeries(seriesId: String) = offlineDeleteActions.deleteOfflineSeries(seriesId)

    /**
     * TTL-gated server freshness check for the current item. Safe to call on
     * every screen entry. Delegates to [ResyncActions.checkForUpdates].
     */
    fun checkForUpdates() = resyncActions.checkForUpdates()

    /**
     * Re-syncs the current item's metadata and changed images from the server.
     * Surfaces progress via [DetailUiState.resyncState]. Delegates to
     * [ResyncActions.resync].
     */
    fun resync() = resyncActions.resync()

    /** Resets [DetailUiState.resyncState] to [ResyncUiState.Idle] (no-op while Working). */
    fun clearResyncState() = resyncActions.clearResyncState()

    /**
     * Re-downloads the media file when the server's MediaSource changed. This is
     * the DETAIL re-download path, not the *arr Manage-Series action
     * ([ArrRepository.redownloadMedia]). Delegates to [ResyncActions.redownloadMedia].
     */
    fun redownloadMedia() = resyncActions.redownloadMedia()

    /**
     * Marks a single episode played/unplayed (offline-aware + outboxed). Does NOT
     * refetch the server — the provider snapshot is rewritten optimistically and
     * the parent catalogue is invalidated for re-entry. Delegates to
     * [MarkSeasonReactor.markEpisodePlayed].
     */
    fun markEpisodePlayed(episodeId: String, played: Boolean) =
        markSeasonReactor.markEpisodePlayed(episodeId, played)

    /**
     * Per-item favorite toggle (offline-aware + outboxed). Distinct from the
     * no-arg [toggleFavorite], which flips the current detail item optimistically.
     */
    fun toggleFavorite(itemId: String) {
        launch {
            userDataMutationMutex.withLock {
                mediaRepository.toggleFavorite(itemId).onSuccess { targetIsFavorite ->
                    applyFavoriteMutation(itemId, targetIsFavorite)
                }
            }
        }
    }

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
