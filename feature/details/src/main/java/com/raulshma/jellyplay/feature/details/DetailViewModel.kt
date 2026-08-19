package com.raulshma.jellyplay.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DetailViewModel @Inject internal constructor(
    // Context only for the storage probe behind [getAvailableStorageBytes];
    // every localized string goes through the [strings] seam.
    @ApplicationContext private val context: Context,
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
    /**
     * The single seam for user-data mutations (watched / favorite). The VM
     * supplies only the container adapter below (which projections of an item
     * exist on this screen); the mutator owns serialization, the write, the
     * provider-session rewrite, and the series-catalogue drop.
     */
    private val userDataMutator: UserDataMutator,
    /**
     * The single external seam for media-detail resolution. Owns the
     * remote/local source decision, the projected [MediaDetail], seasons/
     * episodes (via the shared EpisodeCatalogue), album tracks, local
     * subtitles, local artwork, the download/sync attachment, and capabilities.
     * [loadItemInternal] collects its [DetailLoadState] stream and reduces each
     * emission into [_uiState]; remote-only subordinate work (Seerr, Sonarr,
     * theme music, similar/collection items) fires off the resolved snapshot.
     * Also feeds the action helpers' season expansion / canonical-id lookups.
     */
    private val mediaDetailProvider: MediaDetailProvider,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val offlineRepository: OfflineRepository,
    /** Preference/state stores read by the content core (pure DI aggregation). */
    private val stores: DetailStores,
    /** Seerr/TMDB/Arr remote-discovery clients + their offline gate (pure DI aggregation). */
    private val remoteDiscovery: RemoteDiscoveryClients,
    private val audioPlaybackManager: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager,
    private val audioQueueFacade: AudioQueueFacade,
    private val themeMusicPlayer: com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer,
    /** Hilt factories for the extracted action helpers (see [DetailActionFactories]). */
    private val actionFactories: DetailActionFactories,
) : JellyPlayViewModel() {

    /** Media-detail preference fields, projected centrally off the store slices. */
    val preferences: StateFlow<DetailPreferences> = stores.projections.detailPreferences

    // Single source of truth for detail-screen CONTENT state. All mutations
    // funnel through [_uiState.update]; the [uiState] aggregator additionally
    // folds in [SeerrRequestStateHolder] state via combine() so observers see a
    // single atomic snapshot. Per-sheet action state (downloads, playlists,
    // collections, resync) deliberately does NOT live here — it is published by
    // the owning helper (see [downloads], [playlists], [collections],
    // [resync]) and collected directly at the composition site that needs it.
    private val _uiState = MutableStateFlow(DetailUiState())

    /**
     * The loaded item's session snapshot — what every action helper needs to
     * know about the current screen. Reset to a bare id-only session in
     * [loadItemInternal] and adopted (content sections filled) in
     * [reduceLoaded] on each new resolution; helpers read `.value` at command
     * time, exactly when the former provider lambdas read the VM.
     */
    private val _session = MutableStateFlow<DetailSession?>(null)

    /**
     * One-shot user-facing messages. Buffered so a message emitted before the
     * screen subscribes (e.g. during `loadItem`) is not lost. Shared with every
     * action helper (they `tryEmit` into it), keeping a single one-shot channel
     * for the whole screen.
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
     * per series detail load (in [reduceLoaded]'s remote side effects) rather
     * than inside this combine. The actual series lookup happens inside
     * ManageSeriesScreen.
     */
    val canManageSeries: StateFlow<Boolean> = combine(
        // Map to identity-relevant fields only so favorite/played toggles (which
        // change isFavorite/isPlayed but not id/mediaType) produce structurally
        // equal emissions that StateFlow deduplicates.
        _uiState.map { it.detail?.item?.let { item -> ItemIdentity(item.id, item.mediaType) } },
        _uiState.map { it.detail?.providerIds?.get("tvdb") },
        stores.experimentalStore.experimental.map { it.enabledExperimentalFeatures.contains(ExperimentalFeature.DIRECT_ARR_INTEGRATION) },
        _uiState.map { it.sonarrServersResolved },
    ) { itemIdentity, tvdbId, flagEnabled, sonarrResolved ->
        if (!flagEnabled || itemIdentity == null) false
        else if (itemIdentity.mediaType != MediaType.SERIES) false
        else if (tvdbId?.toIntOrNull() == null) false
        else sonarrResolved
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val seerrRequestState = SeerrRequestStateHolder(scope, remoteDiscovery.seerrRequestDelegate)

    /**
     * Aggregated detail-screen CONTENT state. Three upstream groups feed this
     * [StateFlow], each independently `stateIn`'d so a tick in one group (e.g.
     * Seerr connection polling) doesn't re-run the combine logic of an
     * unrelated group (e.g. the core detail/seasons/episodes tree). A final
     * outer [combine] folds the two Seerr groups into the core snapshot so
     * observers see one atomic snapshot, while each group's [StateFlow]
     * deduplicates its own emissions upstream of the merge.
     *
     * Action-helper state (download lifecycle, playlists, collections, resync)
     * is intentionally absent: those helpers publish their own `StateFlow`s
     * (see [downloads], [playlists], [collections], [resync]) and the screen
     * collects what an open sheet needs directly from the owning helper, so a
     * tick in any sheet state no longer re-copies the content bag.
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
            remoteDiscovery.seerrRepository.isConnected(),
            remoteDiscovery.seerrRepository.isRecommendationsEnabled(),
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

    // ── Extracted action helpers ────────────────────────────────────────
    // Each follows the SeerrRequestStateHolder template: a plain, VM-scoped
    // class (constructed here, dies with viewModelScope) that owns its own
    // coroutines and state. They are the screen's seams — commands go through
    // the properties below, state is collected from the helper's own
    // StateFlow — so the flat [DetailUiState] bag stays a pure content core
    // and a tick in one sheet no longer re-copies it.
    private val offlineDeleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
        // Content reads ride the session flow (not the flat uiState bag), same
        // deferred-read timing as every other helper seam.
        episodesProvider = { _session.value?.episodes ?: emptyMap() },
        seasonsProvider = { _session.value?.seasons ?: emptyList() },
        onContentMutated = ::refreshAfterOfflineMutation,
    )
    private val resyncActions = actionFactories.resync.create(
        scope = scope,
        session = _session,
        mediaRepository = mediaRepository,
        offlineRepository = offlineRepository,
    )
    private val markSeasonReactor = MarkSeasonReactor(
        scope = scope,
        session = _session,
        userDataMutator = userDataMutator,
        messages = _messages,
        strings = strings,
    )
    private val playlistActions = actionFactories.playlists.create(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
        mediaDetailProvider = mediaDetailProvider,
    )
    private val collectionActions = CollectionActions(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
        mediaRepository = mediaRepository,
        mediaDetailProvider = mediaDetailProvider,
    )
    private val downloadLifecycleActions = actionFactories.downloads.create(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
        mediaDetailProvider = mediaDetailProvider,
    )
    private val watchPartyActions = actionFactories.watchParty.create(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
    )

    /** Download-lifecycle seam: single-item/series downloads, sheets, picker. */
    internal val downloads: DownloadLifecycleActions get() = downloadLifecycleActions

    /** Add-to-Playlist seam (picker + create dialog state and commands). */
    internal val playlists: PlaylistActions get() = playlistActions

    /** Add-to-Collection seam (picker + create dialog state and commands). */
    internal val collections: CollectionActions get() = collectionActions

    /** Resync / re-download / freshness-check seam. */
    internal val resync: ResyncActions get() = resyncActions

    /** Offline-delete seam (fire-and-forget; no observable state). */
    internal val offline: OfflineDeleteActions get() = offlineDeleteActions

    /** Watch-party (SyncPlay) bootstrap seam. */
    internal val watchParty: WatchPartyActions get() = watchPartyActions

    /** Seerr request-flow seam (the state-holder pattern the helpers copy). */
    internal val seerrRequests: SeerrRequestStateHolder get() = seerrRequestState

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
     * via `stores.engineStore.setMediaStreamSelection` (subtitleStreamIndex =
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
     * Single persistence seam for the stream selectors: writes the resolved
     * subtitle/audio pair to `stores.engineStore.setMediaStreamSelection`. Extracted
     * so [selectSubtitle], [selectAudio], and [selectLocalSubtitle] cannot drift
     * apart in how they resolve the current item id or launch the write.
     */
    private fun persistStreamSelection(subtitleIndex: Int?, audioIndex: Int?) {
        val itemId = _uiState.value.detail?.item?.id ?: return
        launch {
            stores.engineStore.setMediaStreamSelection(
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
        launch { stores.libraryStore.setEpisodesDescending(descending) }
    }

    /**
     * Toggles the compact vertical episode list preference (mobile only). Like
     * [setEpisodesDescending], persisted app-wide so the choice carries across
     * every series detail screen.
     */
    fun setCompactEpisodeList(enabled: Boolean) {
        launch { stores.libraryStore.setCompactEpisodeList(enabled) }
    }

    init {
        // Live refresh on server `UserDataChanged` pushes (e.g. another
        // client flipping played/favorite on the item on screen). The server
        // emits one change per item, so ids are accumulated across the
        // debounce window and membership is checked at the drain — plain
        // debounce would keep only the last change of a burst and miss the
        // earlier items. Refresh reuses the pull-to-refresh path, which owns
        // the per-type cache invalidation and keeps the current content
        // visible under the Refreshing state.
        launch {
            val burstIds = mutableSetOf<String>()
            mediaRepository.userDataChanges
                .onEach { change -> burstIds += change.itemIds }
                .debounce(HomeFreshness.USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS)
                .collect {
                    val changedIds = burstIds.toList()
                    burstIds.clear()
                    val itemId = currentItemId ?: return@collect
                    if (_uiState.value.detail?.item?.id != itemId) return@collect
                    if (itemId in changedIds) {
                        loadItemInternal(itemId, refresh = true)
                    }
                }
        }
    }

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
        // Same for the helpers' session: a bare id-only session is visible to
        // command-time reads immediately (the content sections fill in
        // reduceLoaded once the provider resolves).
        _session.value = DetailSession(itemId = itemId)
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
                    tmdbReviews = emptyList(),
                    sonarrServersResolved = false,
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
                        message = strings.get(R.string.detail_error_unavailable_offline)
                        accessDenied = false
                    }
                    e.isAccessDenied -> {
                        message = strings.get(R.string.detail_error_access_denied)
                        accessDenied = true
                    }
                    else -> {
                        message = e.message.ifBlank { strings.get(R.string.detail_error_load_failed) }
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
     * Reduces a resolved [MediaDetailSnapshot] into [_uiState] (and adopts it
     * into the helpers' [_session]).
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

        // Publish the resolved session to the action helpers (command-time
        // reads now see the full content snapshot).
        _session.value = DetailSession(
            itemId = itemId,
            seriesId = currentSeriesId,
            detail = detail,
            seasons = snapshot.seasons,
            episodes = snapshot.episodesBySeason,
            sortedEpisodes = snapshot.sortedEpisodes,
        )

        // Stream selection: remote applies the persisted engine-store selection;
        // local clears it (local playback uses the separate local-subtitle index).
        val (subtitleIndex, audioIndex) = if (isRemote) {
            val stored = stores.engineStore.playerEngine.value.mediaStreamSelections[itemId]
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
     * `remoteDiscovery.arrRepository.resolveServers` from inside a `combine` transform, which
     * re-issued network I/O on every identity tick and got cancelled/restarted
     * mid-resolution. Hoisting it here makes the combine a pure derivation.
     */
    private fun resolveSonarrForSeries(detail: MediaDetail) {
        val tvdbId = detail.providerIds["tvdb"]
        if (tvdbId?.toIntOrNull() == null) return
        val itemId = detail.item.id
        launch {
            val summary = remoteDiscovery.arrRepository.resolveServers()
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
        // Queue construction (N image URLs + N queue items) runs on
        // Dispatchers.Default and the playQueue mutation hops to Main inside
        // the facade — the AudioQueueManager thread contract, in one place.
        launch {
            audioQueueFacade.playTracks(tracks, startIndex = startIndex, albumFallback = albumName)
        }
    }

    // ── Instant mix ────────────────────────────────────────────────────
    // One facade call: the mix fetch, queue build, and dispatcher hop all live
    // in [AudioQueueFacade]; the VM keeps only the audio-type gate, the
    // navigation-drift guard, and the outcome → [DetailMessage] mapping.

    /**
     * Starts a Jellyfin instant mix for the current audio item. Fetches the
     * mix seeded off the current item and plays it at index 0 via
     * [AudioQueueFacade.startInstantMix]. Fire-and-forget: success is implicit
     * (playback starts) and the only UI feedback is the empty / failure
     * snackbar emitted via [DetailMessage]. The guard vetoes a mix that
     * resolved after the user navigated away, so playback cannot start on the
     * wrong screen.
     */
    fun startInstantMix() {
        val detail = _uiState.value.detail ?: return
        val item = detail.item
        if (!item.mediaType.isAudioType) return
        val itemId = item.id
        launch {
            when (
                val outcome = audioQueueFacade.startInstantMix(
                    itemId,
                    albumFallback = item.album ?: item.name,
                    guard = { currentItemId == itemId },
                )
            ) {
                is AudioQueueOutcome.Started -> Unit
                AudioQueueOutcome.Empty ->
                    _messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_instant_mix_empty)))
                AudioQueueOutcome.Suppressed -> Unit
                is AudioQueueOutcome.Failed ->
                    _messages.tryEmit(DetailMessage.Text(strings.get(R.string.detail_instant_mix_failed)))
            }
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
            val sorted = state.sortedEpisodes.takeIf { it.isNotEmpty() }
            if (sorted == null) {
                _uiState.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
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
            LabelKind.RESUME_EPISODE -> strings.get(R.string.detail_resume_episode, s, e)
            LabelKind.NEXT_UP_EPISODE -> strings.get(R.string.detail_next_up_episode, s, e)
            LabelKind.PLAY_EPISODE -> strings.get(R.string.detail_play_episode, s, e)
            LabelKind.REPLAY_EPISODE -> strings.get(R.string.detail_replay_episode, s, e)
        }
        return DetailUiState.SmartPlayTarget(
            episode = episode,
            label = label,
            startPositionTicks = startPositionTicks,
            primaryImageUrl = imageUrlProvider.getImageUrl(episode.id),
            labelKind = this.label,
        )
    }

    /**
     * The Detail screen's container adapter: applies a resolved mutation to
     * every visible projection of an item. Detail actions can target the
     * current item, a related/collection card, or an episode card; keeping
     * these projections together prevents one card from replaying the old
     * state until the next full detail load. Folds the former
     * `updatePlayedStateInUi` / `updateFavoriteStateInUi` pair — the patch
     * (resume-zeroing on played flips, favorite-only flips) is derived from
     * the [com.raulshma.jellyplay.core.data.repository.AppliedMutation] the
     * mutator resolved.
     */
    private val detailItemContainer = UserDataContainer { itemId, patch ->
        var shouldRecomputeSmartPlay = false
        _uiState.update { state ->
            val currentDetail = state.detail
            val isCurrentDetail = currentDetail?.item?.id == itemId
            shouldRecomputeSmartPlay = isCurrentDetail || state.sortedEpisodes.any { it.id == itemId }
            state.copy(
                detail = currentDetail?.let { detail ->
                    if (isCurrentDetail) detail.copy(item = patch(detail.item)) else detail
                },
                relatedItems = state.relatedItems.map { if (it.id == itemId) patch(it) else it },
                collectionItems = state.collectionItems.map { if (it.id == itemId) patch(it) else it },
                episodes = state.episodes.mapValues { (_, episodes) ->
                    episodes.map { if (it.id == itemId) patch(it) else it }
                },
                sortedEpisodes = state.sortedEpisodes.map { if (it.id == itemId) patch(it) else it },
            )
        }
        if (shouldRecomputeSmartPlay) maybeComputeSmartPlayTarget()
    }

    fun toggleFavorite() {
        launch {
            val itemId = _uiState.value.detail?.item?.id ?: return@launch
            userDataMutator.setFavorite(
                itemId = itemId,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(detailItemContainer),
                seriesId = seriesIdForItem(itemId),
            ).onFailure {
                // Don't leave the user guessing why the heart didn't flip.
                _messages.emit(DetailMessage.Text(strings.get(R.string.detail_msg_couldnt_update_favorite)))
            }
        }
    }

    fun markPlayed() = setPlayed(played = true)

    fun markUnplayed() = setPlayed(played = false)

    /**
     * Shared optimistic watched-toggle for the detail item. Jellyfin clears a
     * manually (un)watched item's resume point, so both directions mirror that
     * immediately — the detail UI cannot retain an in-progress bar while the
     * queued/offline mutation syncs (the resume rule lives in
     * [com.raulshma.jellyplay.core.data.repository.AppliedMutation.patch]).
     */
    private fun setPlayed(played: Boolean) {
        launch {
            val itemId = _uiState.value.detail?.item?.id ?: return@launch
            userDataMutator.setPlayed(
                itemId = itemId,
                played = played,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(detailItemContainer),
                seriesId = seriesIdForItem(itemId),
            ).onFailure {
                _messages.emit(
                    DetailMessage.Text(
                        strings.get(
                            if (played) R.string.detail_msg_couldnt_mark_played
                            else R.string.detail_msg_couldnt_mark_unplayed
                        )
                    )
                )
            }
        }
    }

    /**
     * Resolves the series an item belongs to from the screen's current
     * projections, for the mutator's series-catalogue drop: an episode card's
     * parent series, the current detail's series (a series resolves to
     * itself), or null when the item is not series-scoped.
     */
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

    /**
     * Marks a row item (related/collection/episode) played or
     * unplayed without switching the screen's current detail item. Flips the
     * item in-place across all visible projections; the mutator rewrites the
     * provider session and drops the parent catalogue so re-entry cannot
     * replay the old state.
     */
    fun markRowItemPlayed(item: MediaItem, played: Boolean) {
        launch {
            userDataMutator.setPlayed(
                itemId = item.id,
                played = played,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(detailItemContainer),
                seriesId = item.seriesId ?: seriesIdForItem(item.id),
            )
        }
    }

    /**
     * Marks every episode in [seasonId] as played. The optimistic rewrite goes
     * through the mutator's provider-season rewrite; the reducer adopts it +
     * recomputes smart-play. Delegates to [MarkSeasonReactor] — see there for
     * the no-refetch / re-entry invalidation contract.
     */
    fun markSeasonPlayed(seasonId: String) = markSeasonReactor.markSeasonPlayed(seasonId)

    fun markSeasonUnplayed(seasonId: String) = markSeasonReactor.markSeasonUnplayed(seasonId)

    fun hideFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            stores.homeDiscoveryStore.excludeSeriesFromNextUp(seriesId)
            _messages.emit(DetailMessage.Text(strings.get(R.string.detail_msg_hidden_from_next_up)))
        }
    }

    fun showFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            stores.homeDiscoveryStore.includeSeriesInNextUp(seriesId)
            _messages.emit(DetailMessage.Text(strings.get(R.string.detail_msg_shown_in_next_up)))
        }
    }

    fun hideFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            stores.homeDiscoveryStore.hideCwItem(item.id)
            _messages.emit(DetailMessage.Text(strings.get(R.string.detail_msg_hidden_from_continue_watching)))
        }
    }

    fun showFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            stores.homeDiscoveryStore.unhideCwItem(item.id)
            _messages.emit(DetailMessage.Text(strings.get(R.string.detail_msg_shown_in_continue_watching)))
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
            stores.homeDiscoveryStore.setLastViewedSeason(seriesId, seasonId)
        }
    }

    fun setShowDetailUpNext(enabled: Boolean) {
        launch {
            stores.libraryStore.setShowDetailUpNext(enabled)
        }
    }

    fun getImageUrl(itemId: String): String =
        imageUrlProvider.getImageUrl(itemId)

    /** Chapter thumbnail URL for the detail-screen chapter row. */
    fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?): String =
        imageUrlProvider.getChapterImageUrl(itemId, imageIndex, tag)

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
                    tmdbReviews = emptyList(),
                )
            }

            if (remoteDiscovery.offlineModeManager.networkStatus.value == NetworkStatus.Local) return@launch

            val mediaType = detail.item.mediaType
            if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return@launch

            val tmdbId = resolveTmdbId(detail) // top-level fn in TmdbIdResolver.kt
            if (tmdbId == null) return@launch

            // Reviews come straight from TMDB — neither the Seerr connection nor
            // the recommendations preference gates them. Separate launch so the
            // review section doesn't serialize behind the Seerr fetches below.
            launch {
                val reviews = remoteDiscovery.seerrRepository.getTmdbReviews(tmdbId, mediaType)
                    .getOrElse { emptyList() }
                if (generation == seerrDataGeneration) {
                    _uiState.update { it.copy(tmdbReviews = reviews.take(5)) }
                }
            }

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
                    remoteDiscovery.seerrRepository.getMovieDetails(tmdbId).map { it.relatedVideos }
                } else {
                    remoteDiscovery.seerrRepository.getTvDetails(tmdbId).map { it.relatedVideos }
                }
                if (generation == seerrDataGeneration) {
                    val videos = videosResult.getOrElse { emptyList() }
                    _uiState.update { it.copy(relatedVideos = videos) }
                }
            } else {
                val videosResult = remoteDiscovery.seerrRepository.getTmdbVideos(tmdbId, mediaType)
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
                        remoteDiscovery.seerrRepository.getRecommendations(tmdbId, mediaType)
                            .getOrElse { com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse() }
                    }
                    val similarDeferred = async {
                        remoteDiscovery.seerrRepository.getSimilar(tmdbId, mediaType)
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

    private fun loadSeerrDataIfNeeded(detail: MediaDetail) {
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

    /**
     * Marks a single episode played/unplayed (offline-aware + outboxed via the
     * mutator). Does NOT refetch the server — the mutator rewrites the provider
     * session optimistically and drops the parent catalogue for re-entry, and
     * the container adapter flips every visible projection of the episode.
     */
    fun markEpisodePlayed(episodeId: String, played: Boolean) {
        launch {
            userDataMutator.setPlayed(
                itemId = episodeId,
                played = played,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(detailItemContainer),
                seriesId = seriesIdForItem(episodeId),
            )
        }
    }

    /**
     * Per-item favorite toggle (offline-aware + outboxed). Distinct from the
     * no-arg [toggleFavorite], which flips the current detail item optimistically.
     */
    fun toggleFavorite(itemId: String) {
        launch {
            userDataMutator.setFavorite(
                itemId = itemId,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(detailItemContainer),
                seriesId = seriesIdForItem(itemId),
            )
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
