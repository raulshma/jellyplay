package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadOutcomeMessenger
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.NoopBookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.book.BookTocProber
import com.raulshma.jellyplay.core.data.repository.MediaExtrasReads
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionFetches
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionLanding
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionRequest
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionStateHolder
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.experimental.directArrEnabled
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.InstantMixError
import com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder
import com.raulshma.jellyplay.core.data.playback.toInstantMixOutcome
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import com.raulshma.jellyplay.core.ui.components.seerr.SeerrRequestDialogHolder
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_access_denied
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_load_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_unavailable_offline
import com.raulshma.jellyplay.feature.details.generated.resources.detail_instant_mix_empty
import com.raulshma.jellyplay.feature.details.generated.resources.detail_radio_empty
import com.raulshma.jellyplay.feature.details.generated.resources.detail_radio_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_instant_mix_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_mark_played
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_mark_unplayed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_update_favorite
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_download_start_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_download_started
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_hidden_from_continue_watching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_hidden_from_next_up
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_shown_in_continue_watching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_shown_in_next_up
import com.raulshma.jellyplay.feature.details.generated.resources.detail_next_up_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_play_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_replay_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resume_episode

/**
 * ViewModel for the media-detail screen — the thin async caller that owns the
 * [DetailUiState] content writes over the provider's resolved snapshots.
 *
 * Every user intent arrives as a [DetailUiEvent] through the single [onEvent]
 * funnel (the home feature's `HomeViewModel` precedent;
 * [ManageSeriesViewModel] is the in-module template) — the per-action command
 * handlers are private, so there is no per-screen command method to keep in
 * sync. Two surfaces deliberately stay public beside the funnel: the read
 * side (the [uiState]/[preferences]/[messages]/[canManageSeries]/
 * [quickActionDownloadedIds] flows, the image-URL getters, the click-time
 * `selected*Index` reads, the storage probe — queries, not commands) and the
 * eight deep helper seams ([downloads], [playlists], [watchLater],
 * [collections], [resync], [offline], [watchParty], [seerrRequests]) —
 * extracted modules the screen drives directly, which would only gain
 * shallow pass-through events.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DetailViewModel internal constructor(
    // Storage probe seam (StatFs/usable-space behind [getAvailableStorageBytes]);
    // every localized string goes through the [strings] seam.
    private val storageProbe: DetailStorageProbe,
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
    /** The item-attached extras seam (the detail screen's special-features row). */
    private val mediaExtrasReads: MediaExtrasReads,
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
    private val audioQueueFacade: AudioQueueFacade,
    private val themeMusicPlayer: DetailThemeMusic,
    /** Hilt factories for the extracted action helpers (see [DetailActionFactories]). */
    private val actionFactories: DetailActionFactories,
    /** Quick-action download/remove routing shared with the other host screens (#147). */
    private val mediaDownloadActions: MediaDownloadActions,
    /**
     * Book-only seams (BOOK media type). The TOC cache is the reader's
     * write-through table (`book_toc_cache`); the marks repo supplies the
     * bookmark/highlight counts; the prober parses a never-opened book's
     * LOCAL file (player-book binding) when no cache row exists yet. All
     * defaulted so existing test constructions compile.
     */
    private val bookTocCacheRepository: BookTocCacheRepository = NoopBookTocCacheRepository(),
    private val readerAnnotationsRepository: ReaderAnnotationsRepository? = null,
    private val bookTocProber: BookTocProber? = null,
    /**
     * Dispatcher for the smart-play resolution launches ([computeSeriesSmartPlayTarget]
     * / [computeEpisodeSmartPlayTarget]): production resolves off Main, but the
     * result is a uiState write the screen (and tests) read immediately after a
     * load settles — a hardcoded Default dispatcher makes that read race the
     * update (a real worker thread the test scheduler cannot order against).
     * Tests inject the test dispatcher so `advanceUntilIdle` covers the launch.
     */
    private val smartPlayDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * The jellyfin-plugin-jellyplay companion-plugin seams (ADR 0010), behind
     * the three plugin-gated detail sections (ratings row, server-scored
     * "More like this", anime filler/recap badges). Nullable-with-default
     * keeps the direct-construction test harnesses compiling — the Settings
     * screen's `jellyPlayStatusStore` precedent; the Koin factory passes the
     * real singles and every enrichment degrades to silent absence when
     * either is null (the plugin contract's never-an-error-surface rule).
     *
     * Gating goes through the status store ONLY ([JellyPlayPluginFeatures]
     * keys, one [JellyPlayPluginStatusStore.refresh] probe per item
     * navigation); data calls ride the client family — never per-endpoint
     * 404 handling.
     */
    private val pluginStatusStore: com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore? = null,
    private val pluginApiClient: com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient? = null,
    /**
     * The per-feature gate (probe AND the user's toggle — the ONE seam).
     * Nullable-with-default like the two seams above; without it the
     * probe-only fallback keeps the pre-toggle behavior (tests).
     */
    private val jellyPlayFeatureGate: com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate? = null,
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
        // ONE _uiState projection + dedupe: only a change to one of the
        // identity-relevant fields re-runs the gate below. Favorite/played
        // toggles change isFavorite/isPlayed but not id/mediaType, so their
        // emissions collapse into a single distinct one here.
        _uiState.map {
            SeriesManageInputs(
                identity = it.detail?.item?.let { item -> ItemIdentity(item.id, item.mediaType) },
                tvdbId = it.detail?.providerIds?.get("tvdb"),
                sonarrResolved = it.sonarrServersResolved,
            )
        }.distinctUntilChanged(),
        stores.experimentalStore.directArrEnabled(),
    ) { inputs, flagEnabled ->
        if (!flagEnabled || inputs.identity == null) false
        else if (inputs.identity.mediaType != MediaType.SERIES) false
        else if (inputs.tvdbId?.toIntOrNull() == null) false
        else inputs.sonarrResolved
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val seerrRequestState = SeerrRequestStateHolder(scope, remoteDiscovery.seerrRequestDelegate)

    // The dialog half of the Seerr request lifecycle: which item the request
    // dialog is open for (frozen at open) plus the open/dismiss choreography.
    // The data half (service details, seasons, result) stays in the holder
    // above, reached through the two constructor seams.
    private val seerrRequestDialogHolder = SeerrRequestDialogHolder(
        prepare = seerrRequestState::prepare,
        clearRequestResult = seerrRequestState::clearRequestResult,
    )

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
        // The holder's snapshot flow already combines + dedupes its six
        // sub-flows; snapshotIn gives the group its dedicated StateFlow so its
        // ticks don't re-run the outer combine.
        val seerrRequest = seerrRequestState.snapshotIn(scope)
        // Group 3 — Seerr connection flags that only gate recommendation visibility
        // (the two retired repository lenses, derived off the one preferences flow).
        val seerrFlags = remoteDiscovery.seerrRepository.preferences
            .map { prefs ->
                SeerrConnectionFlags(
                    isConnected = prefs.serverUrl.isNotBlank(),
                    isRecommendationsEnabled = prefs.recommendationsEnabled,
                )
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrConnectionFlags())

        combine(core, seerrRequest, seerrFlags) { primary, request, flags ->
            primary.copy(
                seerrRequest = request,
                isSeerrConnected = flags.isConnected,
                isSeerrRecommendationsEnabled = flags.isRecommendationsEnabled,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
    }

    /** The single command funnel — routes each intent to its private handler. */
    fun onEvent(event: DetailUiEvent) {
        when (event) {
            is DetailUiEvent.LoadItem -> loadItem(event.itemId)
            is DetailUiEvent.ForceRefresh -> forceRefresh()
            is DetailUiEvent.LoadEpisodesForSeason -> loadEpisodesForSeason(event.seriesId, event.seasonId)
            is DetailUiEvent.SelectSubtitle -> selectSubtitle(event.index)
            is DetailUiEvent.SelectAudio -> selectAudio(event.index)
            is DetailUiEvent.SelectLocalSubtitle -> selectLocalSubtitle(event.index)
            is DetailUiEvent.SetEpisodesDescending -> setEpisodesDescending(event.descending)
            is DetailUiEvent.SetCompactEpisodeList -> setCompactEpisodeList(event.enabled)
            is DetailUiEvent.PlayAlbum -> playAlbum(startIndex = event.startIndex)
            is DetailUiEvent.StartInstantMix -> startInstantMix()
            is DetailUiEvent.StartRadio -> startRadio()
            is DetailUiEvent.ToggleFavorite -> toggleFavorite()
            is DetailUiEvent.MarkPlayed -> markPlayed()
            is DetailUiEvent.MarkUnplayed -> markUnplayed()
            is DetailUiEvent.MarkRowItemPlayed -> markRowItemPlayed(event.item, event.played)
            is DetailUiEvent.MarkSeasonPlayed -> markSeasonPlayed(event.seasonId)
            is DetailUiEvent.MarkSeasonUnplayed -> markSeasonUnplayed(event.seasonId)
            is DetailUiEvent.DownloadRowItem -> downloadRowItem(event.item, event.onOpenDetail)
            is DetailUiEvent.RemoveRowItemDownload -> removeRowItemDownload(event.item)
            is DetailUiEvent.HideFromNextUp -> hideFromNextUp()
            is DetailUiEvent.ShowFromNextUp -> showFromNextUp()
            is DetailUiEvent.HideFromContinueWatching -> hideFromContinueWatching()
            is DetailUiEvent.ShowFromContinueWatching -> showFromContinueWatching()
            is DetailUiEvent.SetLastViewedSeason -> setLastViewedSeason(event.seriesId, event.seasonId)
            is DetailUiEvent.SetShowDetailUpNext -> setShowDetailUpNext(event.enabled)
        }
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
    private val playlistTargets = actionFactories.playlists.create(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
        mediaDetailProvider = mediaDetailProvider,
    )
    private val collectionActions = AddToTargetActions(
        scope = scope,
        session = _session,
        messages = _messages,
        adapter = CollectionAddTarget(strings, mediaRepository),
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
    private val metadataAdminActions = actionFactories.metadataAdmin.create(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
    )

    init {
        // Fold the metadata-maintenance admin gate into the content bag (read
        // side — a DetailUiState field, not a VM member; the ownership ratchet
        // counts members, not bag fields). The helper's flow is cold and
        // re-derives on every user switch.
        scope.launch {
            metadataAdminActions.isAdmin.collect { isAdmin ->
                _uiState.update { it.copy(canManageMetadata = isAdmin) }
            }
        }
    }

    /** Download-lifecycle seam: single-item/series downloads, sheets, picker. */
    internal val downloads: DownloadLifecycleActions get() = downloadLifecycleActions

    /** Add-to-Playlist seam (picker + create dialog state and commands). */
    internal val playlists: AddToTargetActions<Playlist> get() = playlistTargets.picker

    /** Watch-Later quick action (cached reserved playlist, no picker). */
    internal val watchLater: WatchLaterActions get() = playlistTargets.watchLater

    /** Add-to-Collection seam (picker + create dialog state and commands). */
    internal val collections: AddToTargetActions<CollectionSummary> get() = collectionActions

    /** Resync / re-download / freshness-check seam. */
    internal val resync: ResyncActions get() = resyncActions

    /** Offline-delete seam (fire-and-forget; no observable state). */
    internal val offline: OfflineDeleteActions get() = offlineDeleteActions

    /** Watch-party (SyncPlay) bootstrap seam. */
    internal val watchParty: WatchPartyActions get() = watchPartyActions

    /** Admin metadata actions (refresh + identify; the ⋮ menu's metadata-maintenance seam). */
    internal val metadataAdmin: MetadataAdminActions get() = metadataAdminActions

    /** Seerr request-flow seam (the state-holder pattern the helpers copy). */
    internal val seerrRequests: SeerrRequestStateHolder get() = seerrRequestState

    /**
     * Seerr request-DIALOG seam: the open item (frozen at open) plus the
     * open/dismiss choreography. The screen collects `item` for the render
     * gate and routes the open/dismiss commands here; the data-side commands
     * (request/prefetch) stay on [seerrRequests].
     */
    internal val seerrRequestDialog: SeerrRequestDialogHolder get() = seerrRequestDialogHolder

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
    /** The BOOK item whose extras (TOC cache + marks counts) are being observed. */
    private var bookExtrasJob: Job? = null
    /**
     * The staleness guard owning what used to be `currentItemId` +
     * `seerrDataGeneration`: navigation publishes the item identity and bumps
     * one epoch ([DetailLoadGuard.enter]); every suspension-point write below
     * re-checks [DetailLoadGuard.isCurrent] instead of hand-copying
     * `if (currentItemId != itemId) return`. See its KDoc for why one epoch
     * covers both lifecycles.
     */
    private val loadGuard = DetailLoadGuard()
    /**
     * The series whose provider catalogue the current screen consumes — the
     * [loadItemInternal] invalidation target and the [loadEpisodesForSeason]
     * identity. Kept as a plain field (not folded into [loadGuard]) because it
     * is an identity READ, not a staleness epoch.
     */
    private var currentSeriesId: String? = null
    /** Idempotency latch: one Seerr-data load per navigation (see [loadSeerrDataIfNeeded]). */
    private var seerrDataLoaded = false
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

    private fun selectSubtitle(index: Int?) {
        _uiState.update { it.copy(selectedSubtitleIndex = index) }
        persistStreamSelection(subtitleIndex = index, audioIndex = _uiState.value.selectedAudioIndex)
    }

    private fun selectAudio(index: Int?) {
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
    private fun selectLocalSubtitle(index: Int?) {
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
    private fun setEpisodesDescending(descending: Boolean) {
        launch { stores.libraryStore.setEpisodesDescending(descending) }
    }

    /**
     * Toggles the compact vertical episode list preference (mobile only). Like
     * [setEpisodesDescending], persisted app-wide so the choice carries across
     * every series detail screen.
     */
    private fun setCompactEpisodeList(enabled: Boolean) {
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
                    val itemId = loadGuard.itemId ?: return@collect
                    if (_uiState.value.detail?.item?.id != itemId) return@collect
                    if (itemId in changedIds) {
                        loadItemInternal(itemId, refresh = true)
                    }
                }
        }
    }

    private fun loadItem(itemId: String) {
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
    private fun forceRefresh() {
        val itemId = _uiState.value.detail?.item?.id ?: return
        loadItemInternal(itemId, refresh = true)
    }

    private fun loadItemInternal(itemId: String, refresh: Boolean) {
        // Record the item we're loading synchronously — and bump the load
        // epoch in the same atomic step — so that a stale
        // loadSeerrDataIfNeeded() call (from a freshly-composed screen still
        // observing the previous item's detail via the shared ViewModel) can be
        // rejected before it loads the wrong item's trailers/videos, and any
        // in-flight Seerr fetch from the previous item cannot write its stale
        // results onto this item's screen.
        loadGuard.enter(itemId)
        // Same for the helpers' session: a bare id-only session is visible to
        // command-time reads immediately (the content sections fill in
        // reduceLoaded once the provider resolves).
        _session.value = DetailSession(itemId = itemId)
        loadJob?.cancel()
        loadJob = launch {
            // Single atomic reset — collapses what used to be ~14 separate
            // composeState/stateFlow mutations into one emission so observers
            // see one recomposition, not fourteen. The surviving leaves and
            // the per-flavour load state are declared once in
            // [DetailUiState.clearedForReload]: on refresh the detail stays
            // visible under the Refreshing indicator; every content slice
            // (sortedEpisodes included) is cleared so fresh data replaces it
            // wholesale.
            _uiState.update { it.clearedForReload(keepDetail = refresh) }
            // Drop the provider's catalogue cache for any series we were viewing
            // so the new item's load starts fresh (the VM is reused across
            // navigations). The provider owns the catalogue internally now.
            currentSeriesId?.let { mediaDetailProvider.invalidate(it) }
            currentSeriesId = null
            seerrDataLoaded = false
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
                if (!loadGuard.isCurrent(itemId)) return@collect
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
    private suspend fun applyLoadState(itemId: String, state: DetailLoadState) {
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
                        message = strings.get(Res.string.detail_error_unavailable_offline)
                        accessDenied = false
                    }
                    e.isAccessDenied -> {
                        message = strings.get(Res.string.detail_error_access_denied)
                        accessDenied = true
                    }
                    else -> {
                        message = e.message.ifBlank { strings.get(Res.string.detail_error_load_failed) }
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
                loadState = DetailUiLoadState.Loaded,
            )
        }
        lastAppliedGeneration = snapshot.contentGeneration

        // Smart-play targets the next episode to watch from the already-loaded
        // sorted episodes, so it runs for both origins: a LOCAL series with
        // downloaded episodes shows the same Play/Resume/Next up target (and Up
        // Next section) as its remote counterpart. Not an enrichment — this is
        // the core resolution the screen's primary button reads.
        maybeComputeSmartPlayTarget()
        // Declared enrichment fan-out: the subordinate loads below the core
        // resolution, as an ordered declaration list (see [enrichments] for
        // the gate table and what deliberately stayed inline).
        runEnrichments(itemId, snapshot)
        // Book extras (TOC cache + marks counts) ride their own observe job —
        // cache-first, with the local-file probe as the never-opened fallback.
        // DELIBERATELY NOT a [DetailEnrichment] declaration: its entry must
        // run for EVERY media type (the leading bookExtrasJob cancel is a
        // lifecycle cancel — a book → non-book move must kill the previous
        // book's marks collector), which no snapshot-keyed gate can express
        // without either leaking the collector or duplicating the guard.
        loadBookExtras(itemId, detail.path, snapshot.context.download?.downloadPath)
    }

    /**
     * The declared enrichment fan-out — the subordinate loads a fresh
     * resolution triggers, reduced from the former hand-launched blocks
     * (triggerRemoteSideEffects / triggerLocalSideEffects /
     * resolveSonarrForSeries / loadCollectionItems) to DATA: each entry is a
     * [DetailEnrichment] whose gate reads the resolved snapshot only
     * (mediaType × origin × capability) and whose body is the former launch
     * content verbatim (guards included). Evaluated in declaration order —
     * the exact order the remote block fired them — by [runEnrichments], so
     * "snapshot of type X with capabilities Y fires E1..En and not the
     * others" is directly testable (DetailViewModelEnrichmentsTest).
     *
     * Ordering note: the former sequence ran the collection-items launch
     * AFTER [loadBookExtras]; both are fire-and-forget, guard-checked
     * coroutines, so the declaration list placing collectionItems last (and
     * [loadBookExtras] after the evaluator) preserves the observable
     * behavior.
     *
     * Still inline by design (see reduceLoaded): [loadBookExtras] (lifecycle
     * cancel for every media type) and [maybeComputeSmartPlayTarget] (core
     * resolution, not a side effect).
     */
    private val enrichments: List<DetailEnrichment> = listOf(
        DetailEnrichment(
            name = "themeMusic",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            val themeSourceId = inputs.detail.item.seriesId ?: inputs.itemId
            themeMusicPlayer.playThemeFor(themeSourceId)
        },
        DetailEnrichment(
            name = "arrServerResolve",
            // SERIES/EPISODE only (the canManageSeries menu lives on series
            // detail), and only when a Sonarr-resolvable tvdb id exists.
            gate = {
                it.remoteDiscoveryAllowed &&
                    (it.mediaType == MediaType.SERIES || it.mediaType == MediaType.EPISODE) &&
                    it.detail.providerIds["tvdb"]?.toIntOrNull() != null
            },
        ) { inputs ->
            val summary = remoteDiscovery.arrRepository.resolveServers()
                .getOrDefault(ArrServiceSummary())
            // Guard: don't write sonarr resolution onto a different item's state.
            if (loadGuard.isCurrent(inputs.itemId)) {
                _uiState.update { it.copy(sonarrServersResolved = summary.sonarrServers.isNotEmpty()) }
            }
        },
        DetailEnrichment(
            name = "similarItems",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Fetch similar/related items concurrently and non-blocking so the core
            // detail renders immediately. The result lands in relatedItems.
            mediaRepository.getSimilarItems(inputs.itemId, limit = 12)
                .onSuccess { items ->
                    if (!loadGuard.isCurrent(inputs.itemId)) return@onSuccess
                    _uiState.update {
                        it.copy(relatedItems = items.filter { related -> related.id != inputs.itemId })
                    }
                }
        },
        DetailEnrichment(
            name = "specialFeatures",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Fetch special features / extras (featurettes, deleted scenes, etc.)
            // concurrently so the core detail renders immediately; the result lands
            // in specialFeatures and renders as its own horizontal row.
            mediaExtrasReads.getSpecialFeatures(inputs.itemId)
                .onSuccess { extras ->
                    if (!loadGuard.isCurrent(inputs.itemId)) return@onSuccess
                    _uiState.update { it.copy(specialFeatures = extras) }
                }
        },
        DetailEnrichment(
            name = "mediaSegments",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Pre-warm the player's media-segment TTL cache and surface the two
            // intro/credits skip affordances as a detail-side chip. The cache fill
            // is an implicit side effect of the call; only the booleans flow into
            // uiState so the chip can render before the player attaches.
            playbackRepository.getMediaSegments(inputs.itemId).onSuccess { segments ->
                if (!loadGuard.isCurrent(inputs.itemId)) return@onSuccess
                val availability = segments.toAvailability()
                _uiState.update {
                    it.copy(
                        hasIntroSegment = availability.hasIntro,
                        hasCreditSegment = availability.hasCredits,
                    )
                }
            }
        },
        DetailEnrichment(
            name = "seerrDiscovery",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Trigger the Seerr recommendations/videos fetch from the VM. The 350ms
            // delay is preserved for frame priority (don't contend with first-frame
            // GPU work); seerrDataLoaded keeps it idempotent across re-entries.
            kotlinx.coroutines.delay(350)
            if (!loadGuard.isCurrent(inputs.itemId)) return@DetailEnrichment
            loadSeerrDataIfNeeded(inputs.detail)
        },
        DetailEnrichment(
            name = "localRelatedItems",
            // LOCAL-origin counterpart: remote discovery is server-only, so a
            // downloaded item would otherwise render as an island. Requires at
            // least one genre/studio to mine the on-device offline library for
            // (the former triggerLocalSideEffects early return).
            gate = {
                !it.isRemote &&
                    (it.detail.item.genres.isNotEmpty() || it.detail.item.studios.isNotEmpty())
            },
        ) { inputs ->
            val related = offlineRepository.getLocalRelated(
                currentId = inputs.itemId,
                genres = inputs.detail.item.genres,
                studios = inputs.detail.item.studios,
                limit = 12,
            )
            if (!loadGuard.isCurrent(inputs.itemId)) return@DetailEnrichment
            _uiState.update {
                it.copy(localRelatedItems = related.filter { r -> r.id != inputs.itemId })
            }
        },
        DetailEnrichment(
            name = "collectionItems",
            // Collections are remote-only companion content (not part of the
            // snapshot); gated on remoteDiscovery so a local origin never
            // starts it.
            gate = {
                it.remoteDiscoveryAllowed && it.mediaType == MediaType.COLLECTION
            },
        ) { inputs ->
            mediaRepository.getCollectionItems(inputs.itemId, limit = 100)
                .onSuccess { result ->
                    if (!loadGuard.isCurrent(inputs.itemId)) return@onSuccess
                    _uiState.update { it.copy(collectionItems = result.items) }
                }
        },
        DetailEnrichment(
            name = "pluginRatings",
            // ADR 0010: the mdblist ratings chips row. The feature-key gate is
            // a suspend probe, so it lives in the BODY (the gate stays a pure
            // snapshot read); failure/null → the field stays empty → the
            // section is silently absent.
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            if (!pluginFeature(JellyPlayPluginFeatures.Ratings, inputs.itemId)) return@DetailEnrichment
            val imdbId = resolveImdbId(inputs.detail) ?: return@DetailEnrichment
            val result = pluginApiClient?.getMdbListRatings(imdbId)?.getOrNull() ?: return@DetailEnrichment
            if (!loadGuard.isCurrent(inputs.itemId)) return@DetailEnrichment
            _uiState.update {
                // Scoreless entries would render an empty chip — dropped here so
                // the section's emptiness check matches what it can render.
                it.copy(pluginRatings = result.ratings.filter { r -> r.score != null })
            }
        },
        DetailEnrichment(
            name = "pluginSimilarItems",
            // The server-scored "More like this" — REPLACES the stock
            // similarItems row while non-empty (the admission fold suppresses
            // MORE_LIKE_THIS on hasPluginSimilar): the plugin also feeds the
            // server's similar-items pipeline, so the stock endpoint returns
            // the same scored list and both rows would duplicate. The plugin
            // returns scored item ids only, so each id is hydrated through
            // the same per-item fetch the detail screen already uses
            // (getMediaDetail), preserving score order; per-id failures drop
            // out and an empty hydration leaves the stock row in place.
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            if (!pluginFeature(JellyPlayPluginFeatures.Recommendations, inputs.itemId)) return@DetailEnrichment
            val scored = pluginApiClient?.getJellyPlaySimilarItems(inputs.itemId, limit = SIMILAR_ITEMS_LIMIT)
                ?.getOrNull()
                .orEmpty()
                .filter { it.itemId != inputs.itemId }
                .distinctBy { it.itemId }
            if (scored.isEmpty()) return@DetailEnrichment
            val hydrated = kotlinx.coroutines.coroutineScope {
                scored.map { candidate ->
                    async { mediaRepository.getMediaDetail(candidate.itemId).getOrNull()?.item }
                }.awaitAll()
            }
            if (!loadGuard.isCurrent(inputs.itemId)) return@DetailEnrichment
            _uiState.update {
                it.copy(
                    pluginSimilarItems = hydrated
                        .filterNotNull()
                        .filter { item -> item.id != inputs.itemId }
                        .distinctBy { item -> item.id },
                )
            }
        },
        DetailEnrichment(
            name = "pluginAnimeMarkers",
            // Series-scoped filler/recap badges for the seasons section's
            // episode rows. SERIES resolves to itself, EPISODE/SEASON to the
            // parent series (seriesIdForDetail) — the same series context the
            // seasons tree renders.
            gate = {
                it.remoteDiscoveryAllowed && it.detail.item.seriesIdForDetail != null
            },
        ) { inputs ->
            if (!pluginFeature(JellyPlayPluginFeatures.AnimeMarkers, inputs.itemId)) return@DetailEnrichment
            val seriesId = inputs.detail.item.seriesIdForDetail ?: return@DetailEnrichment
            val providerSeriesId = resolveProviderSeriesId(inputs.detail) ?: return@DetailEnrichment
            val markers = pluginApiClient?.getAnimeMarkers(seriesId, providerSeriesId)
                ?.getOrNull() ?: return@DetailEnrichment
            if (!loadGuard.isCurrent(inputs.itemId)) return@DetailEnrichment
            _uiState.update { it.copy(animeMarkers = animeBadges(markers.markers)) }
        },
        DetailEnrichment(
            name = "pluginSeasonRatings",
            // The TMDB per-episode scores for the seasons section's episode
            // rows + the season header's average (ADR 0010, the `ratings`
            // feature family's season leg). Same series context as the anime
            // badges; one fetch per LOADED season (a lazily-expanded season
            // re-emits a new generation, so the enrichment re-runs and picks
            // it up). Per-season null/empty results drop out; a wholly empty
            // fold leaves the field empty — silent absence, never an error.
            gate = {
                it.remoteDiscoveryAllowed && it.detail.item.seriesIdForDetail != null
            },
        ) { inputs ->
            if (!pluginFeature(JellyPlayPluginFeatures.Ratings, inputs.itemId)) return@DetailEnrichment
            val tmdbId = resolveTmdbId(inputs.detail) ?: return@DetailEnrichment
            val ratingsBySeasonId = buildMap {
                for (season in inputs.snapshot.seasons) {
                    val seasonNumber = season.indexNumber ?: continue
                    val ratings = pluginApiClient?.getTmdbSeasonRatings(tmdbId.toString(), seasonNumber)
                        ?.getOrNull() ?: continue
                    if (ratings.isEmpty()) continue
                    put(season.id, ratings)
                }
            }
            if (ratingsBySeasonId.isEmpty()) return@DetailEnrichment
            if (!loadGuard.isCurrent(inputs.itemId)) return@DetailEnrichment
            _uiState.update { it.copy(seasonRatings = ratingsBySeasonId) }
        },
    )

    /** The stock similar row's limit (the plugin row mirrors it). */
    private companion object {
        const val SIMILAR_ITEMS_LIMIT = 12
    }

    /**
     * The ADR 0010 feature gate for the plugin sections: one capabilities
     * probe per item navigation (latched on the item id — the store's own
     * contract re-probes on identity transitions), then the ONE gate seam —
     * [JellyPlayFeatureGate.isAvailableNow] (probe AND the user's per-feature
     * toggle; the fresh one-shot arm honors the refresh that just landed).
     * Without the gate seam (direct-construction tests) the probe-only
     * snapshot read keeps the pre-toggle behavior. False when the plugin
     * seams are unwired or the probe failed — silent absence.
     */
    private var pluginProbedItemId: String? = null

    private suspend fun pluginFeature(feature: String, itemId: String): Boolean {
        val store = pluginStatusStore ?: return false
        if (pluginProbedItemId != itemId) {
            pluginProbedItemId = itemId
            store.refresh()
        }
        return jellyPlayFeatureGate.isAvailableNowOrProbe(store, feature)
    }

    /**
     * The evaluator beside [reduceLoaded]: walks the declaration list in
     * order, launching each gated entry on the VM scope. viewModelScope's
     * Main.immediate keeps the launch bodies in declaration order up to each
     * body's first suspension point — the former synchronous-then-launched
     * ordering of the remote block. [DetailLoadGuard] stays the single
     * admission seam: every body re-checks it at its suspension-point writes.
     */
    private fun runEnrichments(itemId: String, snapshot: MediaDetailSnapshot) {
        val inputs = DetailEnrichmentInputs(itemId = itemId, snapshot = snapshot)
        for (enrichment in enrichments) {
            if (!enrichment.gate(inputs)) continue
            launch { enrichment.run(inputs) }
        }
    }

    /**
     * Book-only extras: publishes [DetailUiState.BookDetailState] for the
     * current BOOK item and keeps the marks counts live while the screen is
     * up.
     *
     * TOC resolution order mirrors the reading experience itself: the
     * reader's write-through cache first (instant, offline, exactly what the
     * last open saw); when there is no row yet, a one-shot probe of the
     * book's LOCAL file (a completed download) parses the same structures
     * the reader would (PDF outline, EPUB NCX/nav, comic page count) and
     * write-throughs the result, so the probe cost is paid once per install.
     * A remote never-downloaded book has no local file — the sections stay
     * hidden rather than fetching the whole file for a speculative parse.
     */
    private fun loadBookExtras(itemId: String, itemPath: String?, downloadPath: String?) {
        // Cancel before the book check: the marks collector below must not
        // outlive a book → non-book navigation.
        bookExtrasJob?.cancel()
        val format = BookFormat.fromPath(itemPath) ?: return
        bookExtrasJob = launch {
            fun publish(toc: List<BookTocEntry>, pageCount: Int) {
                if (!loadGuard.isCurrent(itemId)) return
                _uiState.update {
                    it.copy(
                        book = (it.book ?: DetailUiState.BookDetailState(format = format)).copy(
                            toc = toc,
                            pageCount = pageCount,
                        ),
                    )
                }
            }

            val cached = runCatching { bookTocCacheRepository.getToc(itemId) }.getOrNull()
            publish(cached?.entries ?: emptyList(), cached?.pageCount ?: 0)

            if (cached == null) {
                val probe = runCatching { bookTocProber?.probe(downloadPath, format) }.getOrNull()
                if (probe != null && loadGuard.isCurrent(itemId)) {
                    // Write-through: the next detail visit reads the cache.
                    runCatching {
                        bookTocCacheRepository.putToc(itemId, probe.format, probe.pageCount, probe.entries)
                    }
                    publish(probe.entries, probe.pageCount)
                }
            }

            // Marks counts observe live (the reader edits them on another
            // screen; returning here re-renders the counts). Cancelled with
            // the job on the next load.
            val marks = readerAnnotationsRepository ?: return@launch
            combine(
                marks.observeBookmarks(itemId),
                marks.observeAnnotations(itemId),
            ) { bookmarks, annotations -> bookmarks.size to annotations.size }
                .collect { (bookmarkCount, highlightCount) ->
                    if (!loadGuard.isCurrent(itemId)) return@collect
                    _uiState.update {
                        it.copy(
                            book = (it.book ?: DetailUiState.BookDetailState(format = format)).copy(
                                bookmarkCount = bookmarkCount,
                                highlightCount = highlightCount,
                            ),
                        )
                    }
                }
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
    private fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_uiState.value.fetchedSeasonIds.contains(seasonId)) return
        val itemId = loadGuard.itemId ?: return
        launch {
            if (currentSeriesId != seriesId) return@launch
            // expandSeason fetches the season via the catalogue (serving from
            // its cached snapshot when present, else fetching the one season),
            // merges it into the provider's content, and re-emits. The reducer
            // picks up the new snapshot on the next observe() emission.
            mediaDetailProvider.expandSeason(itemId, seasonId)
        }
    }

    private fun playAlbum(startIndex: Int = 0) {
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
    // in [AudioQueueFacade]; the shared [InstantMixStateHolder] owns the
    // outcome choreography. The VM keeps only the audio-type gate and folds
    // holder errors into this screen's [DetailMessage] snackbar channel —
    // clearing after each emit so a repeated identical failure re-fires
    // (StateFlow equality would otherwise swallow it).

    private val instantMixHolder = InstantMixStateHolder(
        scope = scope,
        startMix = { seedItemId, fallbackName ->
            audioQueueFacade.startInstantMix(
                seedItemId,
                albumFallback = fallbackName,
                guard = { loadGuard.isCurrent(seedItemId) },
            ).toInstantMixOutcome()
        },
    )

    init {
        launch {
            instantMixHolder.state
                .map { it.error }
                .distinctUntilChanged()
                .collect { mixError ->
                    when (mixError) {
                        InstantMixError.EmptyMix ->
                            _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_instant_mix_empty)))
                        is InstantMixError.Failed ->
                            _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_instant_mix_failed)))
                        null -> Unit
                    }
                    if (mixError != null) instantMixHolder.clearError()
                }
        }
    }

    /**
     * Starts a Jellyfin instant mix for the current audio item. Fetches the
     * mix seeded off the current item and plays it at index 0 via
     * [AudioQueueFacade.startInstantMix]. Fire-and-forget: success is implicit
     * (playback starts) and the only UI feedback is the empty / failure
     * snackbar emitted via [DetailMessage]. The guard vetoes a mix that
     * resolved after the user navigated away, so playback cannot start on the
     * wrong screen.
     */
    private fun startInstantMix() {
        val detail = _uiState.value.detail ?: return
        val item = detail.item
        if (!item.mediaType.isAudioType) return
        instantMixHolder.start(item.id, item.album ?: item.name)
    }

    /**
     * Starts an endless radio for the current audio item: instant-mix seed via
     * [AudioQueueFacade.startRadio] (the refill loop arms only when the seed
     * actually starts playing). Fire-and-forget like [startInstantMix] — the
     * only UI feedback is the empty / failure snackbar. Stopping happens in
     * the player's queue sheet (the radio chip's stop action).
     */
    private fun startRadio() {
        val detail = _uiState.value.detail ?: return
        val item = detail.item
        if (!item.mediaType.isAudioType) return
        launch {
            when (val outcome = audioQueueFacade.startRadio(
                item.id,
                albumFallback = item.album ?: item.name,
                guard = { loadGuard.isCurrent(item.id) },
            )) {
                AudioQueueOutcome.Empty ->
                    _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_radio_empty)))
                is AudioQueueOutcome.Failed ->
                    _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_radio_failed)))
                else -> Unit
            }
        }
    }

    private fun maybeComputeSmartPlayTarget() {
        val item = _uiState.value.detail?.item ?: return
        when (item.mediaType) {
            MediaType.SERIES -> computeSeriesSmartPlayTarget()
            MediaType.EPISODE -> computeEpisodeSmartPlayTarget(item)
            // A SEASON entry (#168) plays within that season only: the same
            // resolver over the entry season's slice of the series snapshot.
            MediaType.SEASON -> computeSeasonSmartPlayTarget(item)
            else -> _uiState.update { it.copy(smartPlayTarget = null) }
        }
    }

    private fun computeSeasonSmartPlayTarget(season: MediaItem) {
        launch(smartPlayDispatcher) {
            val sorted = _uiState.value.sortedEpisodes.takeIf { it.isNotEmpty() }
            val result = sorted?.let { SmartPlayResolver.resolveSeason(season.id, it) }
            _uiState.update {
                it.copy(smartPlayTarget = result?.toUiTarget())
            }
        }
    }

    private fun computeSeriesSmartPlayTarget() {
        launch(smartPlayDispatcher) {
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
        launch(smartPlayDispatcher) {
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
    private suspend fun SmartPlayResult.toUiTarget(): DetailUiState.SmartPlayTarget {
        val s = episode.seasonNumber ?: 1
        val e = episode.episodeNumber ?: episode.indexNumber ?: 1
        val label = when (label) {
            LabelKind.RESUME_EPISODE -> strings.get(Res.string.detail_resume_episode, s, e)
            LabelKind.NEXT_UP_EPISODE -> strings.get(Res.string.detail_next_up_episode, s, e)
            LabelKind.PLAY_EPISODE -> strings.get(Res.string.detail_play_episode, s, e)
            LabelKind.REPLAY_EPISODE -> strings.get(Res.string.detail_replay_episode, s, e)
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

    private fun toggleFavorite() {
        launch {
            val itemId = _uiState.value.detail?.item?.id ?: return@launch
            userDataMutator.setFavorite(
                itemId = itemId,
                mode = UserDataMutator.FlipMode.Optimistic,
                containers = listOf(detailItemContainer),
                seriesId = seriesIdForItem(itemId),
            ).onFailure {
                // Don't leave the user guessing why the heart didn't flip.
                _messages.emit(DetailMessage.Text(strings.get(Res.string.detail_msg_couldnt_update_favorite)))
            }
        }
    }

    private fun markPlayed() = setPlayed(played = true)

    private fun markUnplayed() = setPlayed(played = false)

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
                            if (played) Res.string.detail_msg_couldnt_mark_played
                            else Res.string.detail_msg_couldnt_mark_unplayed
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
    private fun markRowItemPlayed(item: MediaItem, played: Boolean) {
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

    /** Ids whose quick actions flip to "Remove download" — see [MediaDownloadActions.downloadedIds]. */
    val quickActionDownloadedIds = mediaDownloadActions.downloadedIds

    /**
     * Long-press Download from a detail row card (related/collection/episode,
     * #147): same routing as the library grid — inline start for single-stream
     * items, detail screen for series (selection sheet) and other richer
     * flows. The outcome cascade is the shared
     * [MediaDownloadActions.downloadAndReport] fold; this host's messages ride
     * [rowDownloadSink]'s DetailMessage queue and its strings.
     */
    private fun downloadRowItem(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) {
        launch {
            mediaDownloadActions.downloadAndReport(
                item = item,
                onOpenDetail = { id, _ -> onOpenDetail(id) },
                seriesOpensSheet = false,
                messenger = rowDownloadSink,
            )
        }
    }

    /** The fold's message sink: this screen's DetailMessage queue + strings. */
    private val rowDownloadSink = object : DownloadOutcomeMessenger {
        override fun downloadStarted() {
            launch { _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_download_started))) }
        }

        override fun downloadStartFailed() {
            launch { _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_download_start_failed))) }
        }
    }

    /** Long-press Remove download from a detail row card — deletes the local copy only. */
    private fun removeRowItemDownload(item: MediaItem) {
        mediaDownloadActions.removeDownload(item)
    }

    /**
     * Marks every episode in [seasonId] as played. The optimistic rewrite goes
     * through the mutator's provider-season rewrite; the reducer adopts it +
     * recomputes smart-play. Delegates to [MarkSeasonReactor] — see there for
     * the no-refetch / re-entry invalidation contract.
     */
    private fun markSeasonPlayed(seasonId: String) = markSeasonReactor.markSeasonPlayed(seasonId)

    private fun markSeasonUnplayed(seasonId: String) = markSeasonReactor.markSeasonUnplayed(seasonId)

    private fun hideFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            stores.homeDiscoveryStore.excludeSeriesFromNextUp(seriesId)
            _messages.emit(DetailMessage.Text(strings.get(Res.string.detail_msg_hidden_from_next_up)))
        }
    }

    private fun showFromNextUp() {
        val item = _uiState.value.detail?.item ?: return
        val seriesId = item.seriesId ?: item.id
        launch {
            stores.homeDiscoveryStore.includeSeriesInNextUp(seriesId)
            _messages.emit(DetailMessage.Text(strings.get(Res.string.detail_msg_shown_in_next_up)))
        }
    }

    private fun hideFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            stores.homeDiscoveryStore.hideCwItem(item.id)
            _messages.emit(DetailMessage.Text(strings.get(Res.string.detail_msg_hidden_from_continue_watching)))
        }
    }

    private fun showFromContinueWatching() {
        val item = _uiState.value.detail?.item ?: return
        launch {
            stores.homeDiscoveryStore.unhideCwItem(item.id)
            _messages.emit(DetailMessage.Text(strings.get(Res.string.detail_msg_shown_in_continue_watching)))
        }
    }

    /**
     * Silently pins the last-viewed season for [seriesId] so the series detail
     * screen reopens on that season tab. Mirrors [hideFromNextUp]'s launch shape
     * but emits NO user-facing message (a background preference write). The
     * value flows back reactively via [preferences].
     */
    private fun setLastViewedSeason(seriesId: String, seasonId: String) {
        launch {
            stores.homeDiscoveryStore.setLastViewedSeason(seriesId, seasonId)
        }
    }

    private fun setShowDetailUpNext(enabled: Boolean) {
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

    /** Clear-logo URL for the "prefer logos" detail title. */
    fun getLogoUrl(itemId: String): String =
        imageUrlProvider.getLogoUrl(itemId)

    /**
     * Available bytes on the volume backing the download destination
     * (`DIRECTORY_MUSIC` for audio, `DIRECTORY_MOVIES` otherwise). Read off the
     * main thread — callers should await this from a coroutine or `produceState`.
     *
     * Extracted from the inline `StatFs`/`Environment` probe that previously
     * lived in the download-confirmation composable so the UI layer no longer
     * touches the filesystem.
     */
    suspend fun getAvailableStorageBytes(isAudio: Boolean): Long =
        storageProbe.availableBytes(isAudio)

    /**
     * The TMDB-companion choreography shared with [SeerrDetailViewModel]
     * (formerly hand-copied here): the holder owns the movie/tv videos fork,
     * the connected × recommendations-enabled × staleness gate, the bounded
     * fan-out, per-leg error tolerance, and the take-limits. Built on the
     * [SeerrRequestStateHolder] template — constructor-lambda fetch seams
     * straight onto [RemoteDiscoveryClients.seerrRepository], a single
     * snapshot surface, landings folded into this screen's bag below.
     */
    private val tmdbCompanion = TmdbCompanionStateHolder(
        scope = scope,
        fetches = TmdbCompanionFetches.of(remoteDiscovery.seerrRepository),
    )

    private fun loadSeerrData(detail: MediaDetail, generation: Long) {
        launch {
            if (!loadGuard.isCurrent(generation)) return@launch
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

            // Read the already-resolved Seerr connection booleans from the
            // published [uiState] aggregator — NOT [_uiState]. The flags are
            // folded into [uiState] by the outer combine (Group 3 → seerrFlags),
            // but are never written to [_uiState] (the Group 1 primary flow), so
            // reading [_uiState].value here would always yield the default false
            // and skip every Seerr fetch. [uiState] is a hot StateFlow, so .value
            // is a snapshot read with no subscription/probe overhead.
            val connected = uiState.value.isSeerrConnected

            if (!loadGuard.isCurrent(generation)) return@launch

            // Fire-and-forget: each leg lands into [_uiState] as it resolves,
            // staleness-checked atomically with its write via the request's
            // [TmdbCompanionRequest.isCurrent] (this screen's captured epoch —
            // the same seam startInstantMix passes its guard through).
            tmdbCompanion.load(
                TmdbCompanionRequest(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    connected = connected,
                    recommendationsEnabled = uiState.value.isSeerrRecommendationsEnabled,
                    loadVideos = true,
                    loadReviews = true,
                    loadRecommendations = true,
                    isCurrent = { loadGuard.isCurrent(generation) },
                    onLanding = ::foldCompanionLanding,
                ),
            )
        }
    }

    /** Maps one holder landing onto its [DetailUiState] field. */
    private fun foldCompanionLanding(landing: TmdbCompanionLanding) {
        _uiState.update {
            when (landing) {
                is TmdbCompanionLanding.Videos -> it.copy(relatedVideos = landing.videos)
                is TmdbCompanionLanding.Reviews -> it.copy(tmdbReviews = landing.reviews)
                is TmdbCompanionLanding.Recommendations -> it.copy(seerrRecommendations = landing.items)
                is TmdbCompanionLanding.Similar -> it.copy(seerrSimilar = landing.items)
                // This screen never requests the ratings leg (the ratings row
                // renders from the loaded detail's own fields here).
                is TmdbCompanionLanding.Ratings -> it
            }
        }
    }

    private fun loadSeerrDataIfNeeded(detail: MediaDetail) {
        // Reject details that don't belong to the item currently being viewed.
        // Because the DetailViewModel is shared across detail navigations, a
        // freshly-composed screen briefly observes the *previous* item's detail
        // and may invoke this with a stale MediaDetail — which would load (and
        // cache) the wrong item's trailers/videos and block the real item's load.
        if (!loadGuard.isCurrent(detail.item.id)) return
        if (seerrDataLoaded) return
        seerrDataLoaded = true
        val generation = loadGuard.bump()
        loadSeerrData(detail, generation)
    }

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
        val itemId = loadGuard.itemId ?: return
        if (_uiState.value.origin?.isLocal == true) {
            launch { mediaDetailProvider.refresh(itemId) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        themeMusicPlayer.stop()
    }
}

/**
 * Snapshot of the Seerr connection flags that only gate recommendation
 * visibility. Grouped for the same reason as the request snapshot.
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

/**
 * Structural snapshot of the [DetailUiState]-derived [canManageSeries] inputs,
 * grouped so a single map + dedupe per uiState emission feeds the combine
 * instead of one projection per field.
 */
@Immutable
private data class SeriesManageInputs(
    val identity: ItemIdentity?,
    val tvdbId: String?,
    val sonarrResolved: Boolean,
)
