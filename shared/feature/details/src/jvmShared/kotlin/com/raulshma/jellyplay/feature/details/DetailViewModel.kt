package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadOutcomeMessenger
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.CollectionOperations
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.MediaCatalogueEnrichments
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.experimental.directArrEnabled
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.InstantMixError
import com.raulshma.jellyplay.core.data.playback.InstantMixStateHolder
import com.raulshma.jellyplay.core.data.playback.toInstantMixOutcome
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import com.raulshma.jellyplay.core.ui.components.seerr.SeerrRequestDialogHolder
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_instant_mix_empty
import com.raulshma.jellyplay.feature.details.generated.resources.detail_radio_empty
import com.raulshma.jellyplay.feature.details.generated.resources.detail_radio_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_instant_mix_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import com.raulshma.jellyplay.feature.details.generated.resources.detail_next_up_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_play_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_replay_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resume_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_mark_played
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_mark_unplayed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_update_favorite
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_download_start_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_download_started
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_hidden_from_continue_watching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_hidden_from_next_up
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_series_notifications_off
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_series_notifications_on
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_shown_in_continue_watching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_shown_in_next_up

/**
 * ViewModel for the media-detail screen — the thin flows + `onEvent` facade
 * over [DetailLoadOrchestrator] (the `HomeRefresher` split): the loader is
 * the SOLE WRITER of the [DetailUiState] content state over the provider's
 * resolved snapshots, and this class folds its [DetailLoadOrchestrator.state]
 * into the published snapshot and routes user intents.
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
    /**
     * The detail-read and collection-verbs family seams, handed to the
     * loader ([DetailLoadOrchestrator]) and to the resync helper and the
     * Add-to-Collection adapter respectively: those consumers each make
     * exactly their one family's calls, so they take the seam.
     */
    private val detailFeed: DetailFeed,
    private val collectionOperations: CollectionOperations,
    /** The server user-data change feed (the loader's debounced reload trigger). */
    private val userDataChanges: UserDataChanges,
    private val mediaCatalogueEnrichments: MediaCatalogueEnrichments,
    /** The item-attached extras + provider-id reads (the detail screen's
     *  special-features row, the Seerr item resolution). */
    private val libraryApiClient: LibraryApiClient,
    /** The collection family reads/writes (the Add-to-Collection picker). */
    private val collectionApiClient: com.raulshma.jellyplay.core.network.api.CollectionApiClient,
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
     * The loader ([DetailLoadOrchestrator]) collects its [DetailLoadState]
     * stream and reduces each emission into the content state; remote-only
     * subordinate work (Seerr, Sonarr, theme music, similar/collection items)
     * fires off the resolved snapshot.
     * Also feeds the action helpers' season expansion / canonical-id lookups.
     */
    private val mediaDetailProvider: MediaDetailProvider,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    /** The item/tag-aware artwork resolver exposed as [artwork] (the raw seam is [imageUrlProvider]). */
    private val artworkUrls: ArtworkUrls,
    private val offlineRepository: OfflineRepository,
    /** Preference/state stores read by the content core (pure DI aggregation). */
    private val stores: DetailStores,
    /** Seerr/TMDB/Arr remote-discovery clients + their offline gate (pure DI aggregation). */
    private val remoteDiscovery: RemoteDiscoveryClients,
    private val audioQueueFacade: AudioQueueFacade,
    private val themeMusicPlayer: DetailThemeMusic,
    /** Factories for the action helpers (see [DetailActionFactories]). */
    private val actionFactories: DetailActionFactories,
    /** Quick-action download/remove routing shared with the other host screens (#147). */
    private val mediaDownloadActions: MediaDownloadActions,
    /**
     * Book-only seams (BOOK media type), one bundle ([DetailBookArms]). The
     * TOC cache is the reader's write-through table (`book_toc_cache`); the
     * marks repo supplies the bookmark/highlight counts; the prober parses a
     * never-opened book's LOCAL file (player-book binding) when no cache row
     * exists yet. The bundle normalizes the platforms' optional registrations
     * (a platform without a prober binding passes null) onto the no-op
     * adapters once, so every read below is a plain call — the silent absence
     * (no cached TOC, no probe, counts never written) is decided inside the
     * bundle, not at each call site. Defaulted so existing test constructions
     * compile.
     */
    private val bookArms: DetailBookArms = DetailBookArms(),
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
     * "More like this", anime filler/recap badges), one bundle
     * ([DetailPluginArms]). The data seams are the NARROW plugin roles the
     * enrichments read — the ratings role (mdblist chips + per-season TMDB
     * scores), the recommendations role (similar items) and the markers role
     * (filler/recap badges) — plus the ONE gate seam ([JellyPlayFeatureGate],
     * one member per ADR 0010 §6), not the whole client family. Every read
     * goes through the bundle's operations, whose null-arm yields the plugin
     * contract's silent absence (never an error surface) — decided once
     * inside the bundle, not at every call site. The unwired default keeps
     * the direct-construction test harnesses compiling — the Settings
     * screen's `jellyPlayStatusStore` precedent; the Koin factory passes the
     * real singles.
     *
     * Gating goes through the status store ONLY ([JellyPlayPluginFeatures]
     * keys, one [JellyPlayPluginStatusStore.refresh] probe per item
     * navigation); data calls ride the roles — never per-endpoint 404
     * handling.
     */
    private val pluginArms: DetailPluginArms = DetailPluginArms(),
    /**
     * The TMDB-fallback seam: the direct-TMDB details read behind the
     * `externalRatings` enrichment when Seerr is disconnected (or its ratings
     * fetch failed/returned nothing). Nullable-with-default like the bundles
     * above — deliberately NOT absorbed into [pluginArms]: this is the stock
     * TMDB fallback, not a companion-plugin seam (nothing about it gates on
     * the plugin's capabilities registry), and its single read site reads
     * silent absence inline without sprawling. The Koin defs pass the
     * core:network single and the enrichment degrades to silent absence
     * (TMDB-only source unavailable) when null, keeping the
     * direct-construction test harnesses compiling.
     */
    private val tmdbApiClient: com.raulshma.jellyplay.core.network.api.TmdbApiClient? = null,
    /**
     * The appearance store, read DIRECTLY for the awards-badge toggle
     * (`AppearanceSlice.detailsAwardsBadgesEnabled`): the projection bundle
     * ([DetailPreferences]) cannot carry it (the datastore projection lives
     * outside this feature's edit surface), so the store rides the same
     * nullable-with-default pattern — deliberately NOT absorbed into
     * [pluginArms] or [bookArms]: it is a user-appearance preference, not a
     * plugin or book seam, and its single read site's silent absence (null
     * suppresses the badge) is exactly what an OFF toggle produces.
     * Production passes the core:datastore single; null (unwired tests)
     * suppresses the badge.
     */
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore? = null,
) : JellyPlayViewModel() {

    /** Media-detail preference fields, projected centrally off the store slices. */
    val preferences: StateFlow<DetailPreferences> = stores.projections.detailPreferences

    // Single source of truth for detail-screen CONTENT state is the
    // [DetailLoadOrchestrator]'s state flow (constructed below, after the
    // download helper it resets); the [uiState] aggregator additionally
    // folds in [SeerrRequestStateHolder] state via combine() so observers see
    // a single atomic snapshot. Per-sheet action state (downloads, playlists,
    // collections, resync) deliberately does NOT live here — it is published
    // by the owning helper (see [downloads], [playlists], [collections],
    // [resync]) and collected directly at the composition site that needs it.

    /**
     * The loaded item's session snapshot - what every action helper needs to
     * know about the current screen. Reset to a bare id-only session at load
     * start and adopted (content sections filled) on each new resolution by
     * the loader ([DetailLoadOrchestrator]); helpers read `.value` at command
     * time.
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

    // The download helper is constructed FIRST: the loader resets its state
    // on every navigation and loads the downloaded-episode ids on every new
    // resolution (the HomeRefresher-first ordering rule).
    private val downloadLifecycleActions = actionFactories.downloads.create(
        scope = scope,
        session = _session,
        messages = _messages,
        strings = strings,
        mediaDetailProvider = mediaDetailProvider,
    )

    /**
     * The load pipeline ([DetailLoadOrchestrator]) — sole writer of the
     * content state. The VM hands it its collaborators (constructor-lambda
     * wiring, the Home refresher's shape); [publishedState] defers to
     * [uiState], whose lazy combine reads `loader.state` — no construction
     * cycle, the lambda only runs after both exist.
     */
    private val loader = DetailLoadOrchestrator(
        scope = scope,
        strings = strings,
        detailFeed = detailFeed,
        collectionOperations = collectionOperations,
        userDataChanges = userDataChanges,
        mediaDetailProvider = mediaDetailProvider,
        libraryApiClient = libraryApiClient,
        mediaCatalogueEnrichments = mediaCatalogueEnrichments,
        playbackRepository = playbackRepository,
        offlineRepository = offlineRepository,
        remoteDiscovery = remoteDiscovery,
        pluginArms = pluginArms,
        tmdbApiClient = tmdbApiClient,
        appearanceStore = appearanceStore,
        themeMusicPlayer = themeMusicPlayer,
        bookArms = bookArms,
        smartPlayDispatcher = smartPlayDispatcher,
        preferences = stores.projections.detailPreferences,
        session = _session,
        downloadActions = downloadLifecycleActions,
        engineStore = stores.engineStore,
        artworkUrls = artworkUrls,
        publishedState = { uiState.value },
    )

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
        // ONE content-state projection + dedupe: only a change to one of the
        // identity-relevant fields re-runs the gate below. Favorite/played
        // toggles change isFavorite/isPlayed but not id/mediaType, so their
        // emissions collapse into a single distinct one here.
        loader.state.map {
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
        // Group 1 — core load state (detail/seasons/episodes/smart-play/...),
        // the loader's content state (it is the sole writer).
        val core = loader.state.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
        // Group 2 — Seerr request-flow ephemera (radarr/sonarr/result/dialog state).
        // The holder's snapshot flow already combines + dedupes its six
        // sub-flows; snapshotIn gives the group its dedicated StateFlow so its
        // ticks don't re-run the outer combine.
        val seerrRequest = seerrRequestState.snapshotIn(scope)
        // Group 3 - Seerr connection flags that only gate recommendation visibility
        // (both lenses derived off the one preferences flow).
        val seerrFlags = remoteDiscovery.seerrRepository.preferences
            .map { prefs ->
                SeerrConnectionFlags(
                    isConnected = prefs.serverUrl.isNotBlank(),
                    isRecommendationsEnabled = prefs.recommendationsEnabled,
                )
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SeerrConnectionFlags())
        // Group 4 — the theme-music seam's playing signal, folded so the
        // top-bar mute chip exists only while audio is actually audible.
        val themePlaying = themeMusicPlayer.isPlaying
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

        combine(core, seerrRequest, seerrFlags, themePlaying) { primary, request, flags, playing ->
            primary.copy(
                seerrRequest = request,
                isSeerrConnected = flags.isConnected,
                isSeerrRecommendationsEnabled = flags.isRecommendationsEnabled,
                themeMusicPlaying = playing,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
    }

    /** The single command funnel — routes each intent to its private handler (the load intents land on the loader). */
    fun onEvent(event: DetailUiEvent) {
        when (event) {
            is DetailUiEvent.LoadItem -> loader.load(event.itemId, refresh = false)
            is DetailUiEvent.ForceRefresh -> forceRefresh()
            is DetailUiEvent.SetThemeMusicMuted -> setThemeMusicMuted(event.muted)
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
            is DetailUiEvent.HideFranchiseRow -> hideFranchiseRow(event.collectionId)
            is DetailUiEvent.SetSeriesNotifications -> setSeriesNotifications(event.enabled)
        }
    }

    // Action helpers -------------------------------------------------------
    // Each follows the SeerrRequestStateHolder template: a plain, VM-scoped
    // class (constructed here, dies with viewModelScope) that owns its own
    // coroutines and state. They are the screen's seams - commands go through
    // the properties below, state is collected from the helper's own
    // StateFlow - so the flat [DetailUiState] bag stays a pure content core
    // and a tick in one sheet never re-copies it.
    private val offlineDeleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
        // Content reads ride the session flow (not the flat uiState bag), same
        // deferred-read timing as every other helper seam.
        episodesProvider = { _session.value?.episodes ?: emptyMap() },
        seasonsProvider = { _session.value?.seasons ?: emptyList() },
        onContentMutated = loader::refreshAfterOfflineMutation,
    )
    private val resyncActions = actionFactories.resync.create(
        scope = scope,
        session = _session,
        detailFeed = detailFeed,
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
        adapter = CollectionAddTarget(strings, collectionApiClient, collectionOperations),
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
        onRefreshAccepted = { forceRefresh() },
    )

    init {
        // Fold the metadata-maintenance admin gate into the content bag (read
        // side — a DetailUiState field, not a VM member; the ownership ratchet
        // counts members, not bag fields). The helper's flow is cold and
        // re-derives on every user switch.
        scope.launch {
            metadataAdminActions.isAdmin.collect { isAdmin ->
                loader.update { it.copy(canManageMetadata = isAdmin) }
            }
        }
        // Mirror the CURRENT series' new-episode notification opt-in
        // into the content bag, so the ⋮ entry's checkmark and the upcoming rows'
        // bell stay true to the store even when the config changes out from
        // under the screen (the notification settings' management sheet,
        // another device). An episode resolves to its parent series, a
        // series to itself — the same keying the ⋮ menu's next-up exclusion
        // entry uses.
        scope.launch {
            stores.notificationStore.notification
                .map { it.notificationPreferences.seriesConfigs }
                .distinctUntilChanged()
                .collect { configs ->
                    val session = _session.value ?: return@collect
                    val seriesId = session.detail?.item?.seriesId
                        ?: session.detail?.item?.id
                        ?: session.itemId
                    loader.update {
                        it.copy(seriesNotificationsEnabled = configs[seriesId]?.enabled == true)
                    }
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
    // snapshot from the loader's state rather than a composition-captured
    // value. All other state is consumed reactively via [uiState].
    val selectedSubtitleIndex: Int? get() = loader.state.value.selectedSubtitleIndex
    val selectedAudioIndex: Int? get() = loader.state.value.selectedAudioIndex
    val selectedLocalSubtitleIndex: Int? get() = loader.state.value.selectedLocalSubtitleIndex

    // The load graph's internal caches (load job, staleness guard, generation
    // latch, Seerr latch, hydration semaphore) live in [DetailLoadOrchestrator]
    // — the load pipeline's one owner.

    private fun selectSubtitle(index: Int?) {
        loader.update { it.copy(selectedSubtitleIndex = index) }
        persistStreamSelection(subtitleIndex = index, audioIndex = loader.state.value.selectedAudioIndex)
    }

    private fun selectAudio(index: Int?) {
        loader.update { it.copy(selectedAudioIndex = index) }
        persistStreamSelection(subtitleIndex = loader.state.value.selectedSubtitleIndex, audioIndex = index)
    }

    /**
     * Persists a manifest-backed local-subtitle selection for the current item.
     *
     * Sets [DetailUiState.selectedLocalSubtitleIndex] in the content state AND persists
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
        loader.update { it.copy(selectedLocalSubtitleIndex = index) }
        persistStreamSelection(subtitleIndex = index, audioIndex = loader.state.value.selectedAudioIndex)
    }

    /**
     * Single persistence seam for the stream selectors: writes the resolved
     * subtitle/audio pair to `stores.engineStore.setMediaStreamSelection`. Extracted
     * so [selectSubtitle], [selectAudio], and [selectLocalSubtitle] cannot drift
     * apart in how they resolve the current item id or launch the write.
     */
    private fun persistStreamSelection(subtitleIndex: Int?, audioIndex: Int?) {
        val itemId = loader.state.value.detail?.item?.id ?: return
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

    /**
     * Pull-to-refresh: delegates to [MediaDetailProvider.refresh], which owns
     * the per-type cache invalidation (detail, seasons/episodes, album, collection)
     * and re-resolves the snapshot. Unlike [loadItem] the current content stays
     * on screen (the full-screen loading state is skipped); the pull-to-refresh
     * indicator is driven by [DetailUiLoadState.Refreshing] (via
     * [DetailUiState.loadState]) instead.
     */
    private fun forceRefresh() {
        val itemId = loader.state.value.detail?.item?.id ?: return
        loader.load(itemId, refresh = true)
    }

    /**
     * On-demand per-season expand — the provider supplies seasons/episodes up
     * front; a season NOT in [DetailUiState.fetchedSeasonIds] is fetched and
     * merged by the loader (which owns the provider graph and the identity
     * checks).
     */
    private fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        loader.loadEpisodesForSeason(seriesId, seasonId)
    }

    private fun playAlbum(startIndex: Int = 0) {
        val tracks = loader.state.value.albumTracks
        if (tracks.isEmpty()) return
        val albumName = loader.state.value.detail?.item?.name
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
                guard = { loader.isCurrent(seedItemId) },
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
        val detail = loader.state.value.detail ?: return
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
        val detail = loader.state.value.detail ?: return
        val item = detail.item
        if (!item.mediaType.isAudioType) return
        launch {
            when (val outcome = audioQueueFacade.startRadio(
                item.id,
                albumFallback = item.album ?: item.name,
                guard = { loader.isCurrent(item.id) },
            )) {
                AudioQueueOutcome.Empty ->
                    _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_radio_empty)))
                is AudioQueueOutcome.Failed ->
                    _messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_radio_failed)))
                else -> Unit
            }
        }
    }

    /**
     * The Detail screen's container adapter: applies a resolved mutation to
     * every visible projection of an item. Detail actions can target the
     * current item, a related/collection card, or an episode card; keeping
     * these projections together prevents one card from replaying stale
     * state until the next full detail load. One patch covers both played and
     * favorite flips (resume-zeroing on played flips, favorite-only flips),
     * derived from the
     * [com.raulshma.jellyplay.core.data.repository.AppliedMutation] the
     * mutator resolved.
     *
     * Membership is checked before rebuilding: a projection that cannot
     * contain the item keeps its list instance, so a detail-item flip leaves
     * every episode list untouched (and vice versa) instead of paying an
     * O(total episodes) copy per optimistic toggle. The smart-play predicate
     * itself is unchanged.
     */
    private val detailItemContainer = UserDataContainer { itemId, patch ->
        var shouldRecomputeSmartPlay = false
        loader.update { state ->
            val currentDetail = state.detail
            val isCurrentDetail = currentDetail?.item?.id == itemId
            val inSortedEpisodes = state.sortedEpisodes.any { it.id == itemId }
            shouldRecomputeSmartPlay = isCurrentDetail || inSortedEpisodes

            fun List<MediaItem>.patchedWhereContained(): List<MediaItem> =
                if (any { it.id == itemId }) map { if (it.id == itemId) patch(it) else it } else this

            state.copy(
                detail = currentDetail?.let { detail ->
                    if (isCurrentDetail) detail.copy(item = patch(detail.item)) else detail
                },
                relatedItems = state.relatedItems.patchedWhereContained(),
                collectionItems = state.collectionItems.patchedWhereContained(),
                episodes = if (state.episodes.any { (_, seasonEpisodes) ->
                        seasonEpisodes.any { it.id == itemId }
                    }
                ) {
                    state.episodes.mapValues { (_, seasonEpisodes) -> seasonEpisodes.patchedWhereContained() }
                } else {
                    state.episodes
                },
                sortedEpisodes = if (inSortedEpisodes) {
                    state.sortedEpisodes.map { if (it.id == itemId) patch(it) else it }
                } else {
                    state.sortedEpisodes
                },
            )
        }
        if (shouldRecomputeSmartPlay) loader.recomputeSmartPlayTarget()
    }

    private fun toggleFavorite() {
        launch {
            val itemId = loader.state.value.detail?.item?.id ?: return@launch
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
            val itemId = loader.state.value.detail?.item?.id ?: return@launch
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
        val state = loader.state.value
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

    /**
     * The discovery-pref write funnel: every item-keyed preference write on
     * this screen is the same row - resolve the key off the current item (an
     * episode keys to its parent series, a series to itself - the
     * hideFromNextUp keying), run the store write, then optionally surface
     * the snackbar once the write has landed. The event funnel's entries stay;
     * their bodies all delegate here.
     *
     * [setLastViewedSeason] and [setShowDetailUpNext] stay hand-written: the
     * first is event-keyed (its series+season pair arrives with the event -
     * no item resolution, no message, a silent background write), the second
     * has no key at all; neither follows the item-keyed skeleton.
     */
    private fun writeDiscoveryPref(
        /** The write's identity off the current item — [seriesKey] or the item id. */
        key: (MediaItem) -> String,
        /** The store write, with the resolved key. */
        write: suspend (key: String) -> Unit,
        /** The snackbar, emitted after the write lands; null = silent. */
        message: (suspend () -> DetailMessage)? = null,
    ) {
        val item = loader.state.value.detail?.item ?: return
        val resolvedKey = key(item)
        launch {
            write(resolvedKey)
            message?.let { _messages.emit(it()) }
        }
    }

    /** The item-keyed write identity: an episode's parent series, else the item itself. */
    private fun seriesKey(item: MediaItem): String = item.seriesId ?: item.id

    private fun hideFromNextUp() = writeDiscoveryPref(
        key = ::seriesKey,
        write = stores.homeDiscoveryStore::excludeSeriesFromNextUp,
        message = { DetailMessage.Text(strings.get(Res.string.detail_msg_hidden_from_next_up)) },
    )

    private fun showFromNextUp() = writeDiscoveryPref(
        key = ::seriesKey,
        write = stores.homeDiscoveryStore::includeSeriesInNextUp,
        message = { DetailMessage.Text(strings.get(Res.string.detail_msg_shown_in_next_up)) },
    )

    private fun hideFromContinueWatching() = writeDiscoveryPref(
        key = { it.id },
        write = stores.homeDiscoveryStore::hideCwItem,
        message = { DetailMessage.Text(strings.get(Res.string.detail_msg_hidden_from_continue_watching)) },
    )

    private fun showFromContinueWatching() = writeDiscoveryPref(
        key = { it.id },
        write = stores.homeDiscoveryStore::unhideCwItem,
        message = { DetailMessage.Text(strings.get(Res.string.detail_msg_shown_in_continue_watching)) },
    )

    /**
     * Silently pins the last-viewed season for [seriesId] so the series detail
     * screen reopens on that season tab. Event-keyed (no item resolution, no
     * message — see [writeDiscoveryPref]). The value flows back reactively
     * via [preferences].
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

    /**
     * Toggles the CURRENT series' new-episode notification opt-in (the
     * ⋮ menu entry and the upcoming rows' bell both land here). An episode resolves
     * to its parent series, a series to itself — the hideFromNextUp keying.
     * The POST_NOTIFICATIONS runtime prompt is the screen seam's job (it
     * needs the activity), so only the store write + the snackbar happen
     * here.
     */
    private fun setSeriesNotifications(enabled: Boolean) = writeDiscoveryPref(
        key = ::seriesKey,
        write = { stores.notificationStore.setSeriesNotificationEnabled(it, enabled) },
        message = {
            DetailMessage.Text(
                strings.get(
                    if (enabled) {
                        Res.string.detail_msg_series_notifications_on
                    } else {
                        Res.string.detail_msg_series_notifications_off
                    },
                ),
            )
        },
    )

    /**
     * The top-bar mute chip — flips the live theme audio immediately
     * (the seam's volume half, BEFORE the funnel's launch) and writes the
     * per-series memory (the store half), so a muted series's theme
     * enrichment never starts playback again. An episode resolves to its
     * parent series, a series to itself — the hideFromNextUp keying. No
     * message: the chip's own state is the feedback.
     */
    private fun setThemeMusicMuted(muted: Boolean) {
        themeMusicPlayer.setMuted(muted)
        writeDiscoveryPref(
            key = ::seriesKey,
            write = {
                if (muted) {
                    stores.homeDiscoveryStore.muteThemeMusicForSeries(it)
                } else {
                    stores.homeDiscoveryStore.unmuteThemeMusicForSeries(it)
                }
            },
        )
    }

    /**
     * Inline eye-hide: records the collection id under the CURRENT item's
     * key in [DetailUiState.hiddenFranchiseCollections] — the Up Next card
     * hide's pattern, per row and per item instead of one global switch.
     * Session memory by design (the map is a surviving leaf of
     * [DetailUiState.clearedForReload], so it survives navigation and
     * refresh but not a process restart; cross-launch persistence would
     * need a store slice). Idempotent: a second hide of the same row is a
     * no-op on the set.
     */
    private fun hideFranchiseRow(collectionId: String) {
        val itemId = _session.value?.itemId ?: return
        loader.update { state ->
            state.copy(
                hiddenFranchiseCollections = state.hiddenFranchiseCollections + (
                    itemId to (
                        state.hiddenFranchiseCollections[itemId].orEmpty() + collectionId
                        )
                    ),
            )
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
     * The item/tag-aware artwork resolver (the [ArtworkUrls] seam): one
     * MediaItem/PersonInfo in, the URL for the slot it renders with the right
     * image tag already selected. The same read-side query shape as the raw
     * getters above — it replaces the per-id `getLogoUrl` delegate, whose only
     * consumer (the "prefer logos" title) now reads `urls.logo(item)` off the
     * content tree's [ArtworkCallbacks].
     */
    internal val artwork: ArtworkUrls get() = artworkUrls

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
