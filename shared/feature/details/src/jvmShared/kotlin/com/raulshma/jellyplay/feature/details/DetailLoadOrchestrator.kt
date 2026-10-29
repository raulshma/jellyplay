package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.MediaCatalogueEnrichments
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.CollectionOperations
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionFetches
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionLanding
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionRequest
import com.raulshma.jellyplay.core.data.seerr.TmdbCompanionStateHolder
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeMarker
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore
import com.raulshma.jellyplay.core.model.JellyPlayPluginOmdbAwards
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.TmdbDetails
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_access_denied
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_load_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_error_unavailable_offline
import com.raulshma.jellyplay.feature.details.generated.resources.detail_next_up_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_play_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_replay_episode
import com.raulshma.jellyplay.feature.details.generated.resources.detail_resume_episode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * The detail load orchestrator (the `HomeRefresher` precedent applied to the
 * media-detail load graph): the SOLE WRITER of the screen's content state
 * ([state]), owning the entire load pipeline — the provider snapshot
 * collection, the reduction (new-resolution vs attachment-tick), the
 * staleness epoch ([DetailLoadGuard]), the session adoption, the
 * attachment-tick clobber protection, the smart-play resolution, the book
 * extras, the Seerr companion choreography, the plugin gate, and the declared
 * enrichment fan-out with its role adapters ([DetailSectionEnrichmentHost] +
 * the per-family roles).
 *
 * Division of labour (deliberate, the Home split):
 *  * The orchestrator owns WHAT and WHEN: the content state writes (VM-side
 *    UI writes — stream-selection indices, row hides, optimistic user-data
 *    patches — cross the seam through [update], never a second writer), the
 *    load guard's identity + epoch, the generation latch
 *    ([lastAppliedGeneration]), the per-navigation resets, the refresh
 *    policy (pull-to-refresh + the debounced server user-data-push
 *    reaction), and the enrichment evaluator (declaration order is
 *    load-bearing — see [DetailEnrichmentRegistry]).
 *  * [DetailViewModel] is a flows + `onEvent` facade: it folds [state] into
 *    the published [DetailUiState] (with the Seerr request/flags and
 *    theme-music groups, exactly as Home folds `refresher.state`), forwards
 *    user intents, and keeps the action helpers. [publishedState] hands the
 *    orchestrator a read of that published snapshot — the enrichment bodies
 *    read the connection flags that only exist post-fold.
 *
 * A plain-field owner on purpose: every reader and writer runs on the
 * ViewModel's main-confined scope (the same contract [DetailLoadGuard]
 * documents).
 */
internal class DetailLoadOrchestrator(
    /** The VM's scope: load jobs must die with the VM. */
    private val scope: CoroutineScope,
    private val strings: DetailStrings,
    /** The detail + similar reads (the detail-read family seam). */
    private val detailFeed: DetailFeed,
    /** The collection page read (the collection-verbs family seam). */
    private val collectionOperations: CollectionOperations,
    /** The server user-data change feed (the debounced reload trigger). */
    private val userDataChanges: UserDataChanges,
    /**
     * The detail-read seam's provider half: owns the remote/local source
     * decision, the projected [MediaDetail], seasons/episodes, album tracks,
     * local subtitles, the download/sync attachment, and capabilities.
     * [load] collects its [DetailLoadState] stream and reduces each emission
     * into the content state; remote-only subordinate work (Seerr, Sonarr,
     * theme music, similar/collection items) fires off the resolved snapshot.
     */
    private val mediaDetailProvider: MediaDetailProvider,
    /** The item-attached extras + provider-id reads (special features, trailers, ThemeVideo, the upcoming strip). */
    private val libraryApiClient: LibraryApiClient,
    /** The browse-catalogue enrichments (the franchise rows' BoxSet ancestors). */
    private val mediaCatalogueEnrichments: MediaCatalogueEnrichments,
    private val playbackRepository: PlaybackRepository,
    private val offlineRepository: OfflineRepository,
    /** Seerr/TMDB/Arr remote-discovery clients + their offline gate (pure DI aggregation). */
    private val remoteDiscovery: RemoteDiscoveryClients,
    /** The ADR 0010 companion-plugin seams (see [DetailPluginArms]). */
    private val pluginArms: DetailPluginArms,
    /** The TMDB-fallback seam behind the externalRatings enrichment (nullable — unwired tests). */
    private val tmdbApiClient: com.raulshma.jellyplay.core.network.api.TmdbApiClient?,
    /** The appearance store behind the awards badge toggle (nullable — an OFF toggle). */
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore?,
    private val themeMusicPlayer: DetailThemeMusic,
    /** The book-only seams ([DetailBookArms]) — the TOC cache, marks, prober. */
    private val bookArms: DetailBookArms,
    /** The engine store (the persisted remote stream selection applied on a new resolution). */
    private val engineStore: com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore,
    /** The item/tag-aware artwork resolver (the smart-play target's art). */
    private val artworkUrls: ArtworkUrls,
    /** Dispatcher for the smart-play resolution launches (test-injectable — see the VM's param KDoc). */
    private val smartPlayDispatcher: CoroutineDispatcher,
    /** The projected detail-preference flow (the arms' body-pref halves + the trailer merge order). */
    private val preferences: StateFlow<DetailPreferences>,
    /**
     * The helpers' session: reset to a bare id-only session in [load] and
     * adopted (content sections filled) in [reduceLoaded] on each new
     * resolution; helpers read `.value` at command time.
     */
    private val session: MutableStateFlow<DetailSession?>,
    /** The download-lifecycle helper (per-navigation resets + screen-open reads). */
    private val downloadActions: DownloadLifecycleActions,
    /**
     * A read of the PUBLISHED detail state (the VM's outer combine output):
     * the enrichment bodies' `currentState` role reads the Seerr connection
     * flags that only exist after the fold — never on the raw content state.
     */
    private val publishedState: () -> DetailUiState,
) {

    /**
     * Single source of truth for detail-screen CONTENT state — this class is
     * its sole writer; VM-side UI writes cross [update]. All mutations
     * funnel through `.update`.
     */
    private val _state = MutableStateFlow(DetailUiState())

    /** The content state (the VM folds this into its published snapshot). */
    val state: StateFlow<DetailUiState> = _state

    /**
     * The narrow write seam for the VM-side UI writes (stream-selection
     * indices, franchise-row hides, the optimistic user-data patch, the
     * admin/notification folds): the Home `patchItems` rule — patches cross
     * the seam, never a second writer of the load-derived fields.
     */
    fun update(reduce: (DetailUiState) -> DetailUiState) {
        _state.update(reduce)
    }

    /** The staleness guard's identity check for the VM's command-time guards. */
    fun isCurrent(itemId: String): Boolean = loadGuard.isCurrent(itemId)

    /**
     * The staleness guard owning the item identity and the Seerr data
     * generation: navigation publishes the item identity and bumps one epoch
     * ([DetailLoadGuard.enter]); every suspension-point write below re-checks
     * [DetailLoadGuard.isCurrent] instead of hand-copying
     * `if (currentItemId != itemId) return`. See its KDoc for why one epoch
     * covers both lifecycles.
     */
    private val loadGuard = DetailLoadGuard()

    private var loadJob: Job? = null

    /** The BOOK item whose extras (TOC cache + marks counts) are being observed. */
    private var bookExtrasJob: Job? = null

    /**
     * The series whose provider catalogue the current screen consumes — the
     * [load] invalidation target and the [loadEpisodesForSeason] identity.
     * Kept as a plain field (not folded into [loadGuard]) because it is an
     * identity READ, not a staleness epoch.
     */
    private var currentSeriesId: String? = null

    /** Idempotency latch: one Seerr-data load per navigation (see [loadSeerrDataIfNeeded]). */
    private var seerrDataLoaded = false

    /**
     * The [MediaDetailSnapshot.contentGeneration] of the last snapshot whose
     * *content* sections (detail, seasons, episodes, album tracks, subtitles,
     * origin) were fully reduced into the content state. The provider
     * guarantees stable content references + the same generation across
     * attachment-only ticks, so a value equal to the incoming snapshot's
     * generation means "attachment tick" — we update only
     * [DetailUiState.detailContext]/[DetailUiState.capabilities]/
     * [DetailUiState.assets]/[DetailUiState.localSubtitles] and leave the
     * consumer's optimistic content (watched/favorite flips, episodes)
     * untouched. Reset to -1 in [load] so the first Loaded emission of every
     * screen entry is treated as a fresh resolution.
     */
    private var lastAppliedGeneration = -1L

    /**
     * The plugin similar-items hydration width (the arrqueue searchSemaphore /
     * heatmap resolveSemaphore idiom, [DEFAULT_FANOUT_PARALLELISM]) — the one
     * bounded-parallel surface for the per-candidate getMediaDetail fan-out.
     */
    private val hydrationSemaphore = Semaphore(DEFAULT_FANOUT_PARALLELISM)

    init {
        // Live refresh on server `UserDataChanged` pushes (e.g. another
        // client flipping played/favorite on the item on screen). The server
        // emits one change per item, so ids are accumulated across the
        // debounce window and membership is checked at the drain — plain
        // debounce would keep only the last change of a burst and miss the
        // earlier items. Refresh reuses the pull-to-refresh path, which owns
        // the per-type cache invalidation and keeps the current content
        // visible under the Refreshing state.
        scope.launch {
            val burstIds = mutableSetOf<String>()
            userDataChanges.userDataChanges
                .onEach { change -> burstIds += change.itemIds }
                .debounce(HomeFreshness.USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS)
                .collect {
                    val changedIds = burstIds.toList()
                    burstIds.clear()
                    val itemId = loadGuard.itemId ?: return@collect
                    if (_state.value.detail?.item?.id != itemId) return@collect
                    if (itemId in changedIds) {
                        load(itemId, refresh = true)
                    }
                }
        }
    }

    /**
     * The load entry (the VM's former `loadItemInternal`): records the item
     * synchronously — bumping the load epoch in the same atomic step — so a
     * stale flight path from the previous item is rejected before it writes.
     */
    fun load(itemId: String, refresh: Boolean) {
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
        session.value = DetailSession(itemId = itemId)
        loadJob?.cancel()
        loadJob = scope.launch {
            // Single atomic reset: one emission replaces ~14 separate
            // composeState/stateFlow mutations, so observers see one
            // recomposition. The surviving leaves and
            // the per-flavour load state are declared once in
            // [DetailUiState.clearedForReload]: on refresh the detail stays
            // visible under the Refreshing indicator; every content slice
            // (sortedEpisodes included) is cleared so fresh data replaces it
            // wholesale.
            _state.update { it.clearedForReload(keepDetail = refresh) }
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
            downloadActions.resetForNavigation()
            if (refresh) {
                // The provider owns per-type cache invalidation + the remote refetch.
                // Suspend until the new generation lands so the collector that follows
                // observes the refreshed snapshot rather than a stale replay; the detail
                // stays visible (kept above) under the Refreshing indicator meanwhile.
                // The provider seeds a freshness marker on completion, so the
                // observe below adopts the refreshed caches without forcing a
                // SECOND full fan-out (its session's first resolve runs the
                // resolver's non-forced path; a genuine later re-entry still
                // revalidates — see the provider's acquireSession contract).
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
     * On-demand per-season expand. The provider supplies seasons/episodes up
     * front (including [DetailUiState.fetchedSeasonIds]); a season NOT in that
     * set (e.g. the mismatched-season-key edge) is fetched here through the
     * provider, which merges it into its snapshot and re-emits a new-generation
     * [MediaDetailSnapshot] via [observe]. [reduceLoaded] adopts the merged
     * episodes and recomputes smart-play — no local state merge needed.
     */
    fun loadEpisodesForSeason(seriesId: String, seasonId: String) {
        if (_state.value.fetchedSeasonIds.contains(seasonId)) return
        val itemId = loadGuard.itemId ?: return
        scope.launch {
            if (currentSeriesId != seriesId) return@launch
            // expandSeason fetches the season via the catalogue (serving from
            // its cached snapshot when present, else fetching the one season),
            // merges it into the provider's content, and re-emits. The reducer
            // picks up the new snapshot on the next observe() emission.
            mediaDetailProvider.expandSeason(itemId, seasonId)
        }
    }

    /**
     * Called by the offline-delete seam after each delete transaction lands.
     * The provider only re-resolves content on a refresh tick, and the reducer
     * short-circuits same-generation attachment ticks — so without this the
     * screen would keep showing the pre-delete episodes (or, after a
     * re-resolve, a stuck loading spinner / "Finding Episode" on an emptied
     * season) until the next navigation. Drop the now-stale series catalogue
     * and re-resolve the current view. The refresh is gated to local views: a
     * remote view's server episode list is unchanged by a download delete (the
     * attachment flow already refreshes download badges), so refreshing it
     * would only wastefully refetch from the server.
     */
    fun refreshAfterOfflineMutation() {
        val seriesId = currentSeriesId ?: return
        mediaDetailProvider.invalidate(seriesId)
        val itemId = loadGuard.itemId ?: return
        if (_state.value.origin?.isLocal == true) {
            scope.launch { mediaDetailProvider.refresh(itemId) }
        }
    }

    /**
     * Recomputes the smart-play target from the CURRENT content — the seam the
     * VM's optimistic user-data patch calls after rewriting the episode
     * projections (the resolver reads the freshly patched sorted episodes).
     */
    fun recomputeSmartPlayTarget() {
        maybeComputeSmartPlayTarget()
    }

    /**
     * Reduces a single [DetailLoadState] from the provider into the content
     * state.
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
                _state.update {
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
                _state.update {
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
     * Reduces a resolved [MediaDetailSnapshot] into the content state (and
     * adopts it into the helpers' [session]).
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
            _state.update {
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
        session.value = DetailSession(
            itemId = itemId,
            seriesId = currentSeriesId,
            detail = detail,
            seasons = snapshot.seasons,
            episodes = snapshot.episodesBySeason,
            sortedEpisodes = snapshot.sortedEpisodes,
        )

        // Downloaded-episode ids load at SCREEN-OPEN, not sheet-open:
        // the seasons section's rows read the set for the delete affordance
        // and the on-disk states, so in-flight + completed downloads must
        // render without the user ever opening the download sheet. The
        // sheet-open call stays as an idempotent freshness re-read.
        downloadActions.loadDownloadedEpisodeIds()

        // Stream selection: remote applies the persisted engine-store selection;
        // local clears it (local playback uses the separate local-subtitle index).
        val (subtitleIndex, audioIndex) = if (isRemote) {
            val stored = engineStore.playerEngine.value.mediaStreamSelections[itemId]
            stored?.subtitleStreamIndex to stored?.audioStreamIndex
        } else {
            null to null
        }

        _state.update {
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
     * The declared enrichment fan-out - the subordinate loads a fresh
     * resolution triggers, expressed as DATA: each entry is a
     * [DetailEnrichment] whose gate reads the resolved snapshot only
     * (mediaType x origin x capability) and whose body carries the load's
     * guards. Evaluated in declaration order by [runEnrichments], so
     * "snapshot of type X with capabilities Y fires E1..En and not the
     * others" is directly testable (DetailViewModelEnrichmentsTest).
     *
     * Ordering note: the collection-items load runs AFTER [loadBookExtras];
     * both are fire-and-forget, guard-checked coroutines, so any declaration
     * order that keeps that relative order is equivalent.
     *
     * Still inline by design (see reduceLoaded): [loadBookExtras] (lifecycle
     * cancel for every media type) and [maybeComputeSmartPlayTarget] (core
     * resolution, not a side effect).
     *
     * The detail section modules' enrichment arms
     * ([DetailSectionRegistry.enrichments]) are spliced into the list. The
     * splice point is load-bearing: the externalRatings entry reads the
     * feature probe that the pluginRatings arm latches. All entries are
     * independent, fire-and-forget, guard-checked coroutines
     * (single-writer-per-field), so their positions within the spliced list
     * preserve the observable behavior the fan-out order contract documents
     * above.
     */

    // The section modules' enrichment seams ([DetailSectionEnrichmentHost] +
    // the per-family role interfaces): private adapters over the collaborators
    // this class was handed — the guard/funnel host, then one adapter per
    // fetch family.

    private val sectionHost = object : DetailSectionEnrichmentHost {
        override fun isCurrent(itemId: String): Boolean = loadGuard.isCurrent(itemId)

        override fun updateState(reduce: (DetailUiState) -> DetailUiState) {
            _state.update(reduce)
        }

        override fun detailPreferences(): DetailPreferences = preferences.value

        override suspend fun <T, R> mapConcurrent(items: List<T>, transform: suspend (T) -> R): List<R> =
            hydrationSemaphore.mapConcurrentCatching(items, transform)

        override fun currentState(): DetailUiState = publishedState()
    }

    private val pluginGateRole = object : DetailPluginGateRole {
        override suspend fun pluginFeature(feature: String, itemId: String): Boolean =
            this@DetailLoadOrchestrator.pluginFeature(feature, itemId)
    }

    private val ratingsRoles = object : DetailRatingsRoles {
        override suspend fun mdbListRatings(imdbId: String): Result<List<JellyPlayPluginRating>?>? =
            pluginArms.mdbListRatings(imdbId)

        override suspend fun seerrRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> =
            remoteDiscovery.seerrRepository.getRatings(tmdbId, mediaType)

        override suspend fun tmdbDetails(tmdbId: Int, mediaType: MediaType): TmdbDetails? =
            tmdbApiClient?.getDetails(tmdbId, mediaType)?.getOrNull()

        override suspend fun omdbAwards(imdbId: String): JellyPlayPluginOmdbAwards? =
            pluginArms.omdbAwards(imdbId)

        override fun awardsBadgesEnabled(): Boolean =
            appearanceStore?.appearance?.value?.detailsAwardsBadgesEnabled == true

        override suspend fun tmdbSeasonRatings(
            tmdbId: String,
            seasonNumber: Int,
        ): Map<Int, JellyPlayEpisodeScore>? =
            pluginArms.tmdbSeasonRatings(tmdbId, seasonNumber)
    }

    private val similarRoles = object : DetailSimilarRoles {
        override suspend fun similarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
            detailFeed.getSimilarItems(itemId, limit)

        override suspend fun localRelatedItems(
            currentId: String,
            genres: List<String>,
            studios: List<String>,
            limit: Int,
        ): List<MediaItem> =
            offlineRepository.getLocalRelated(
                currentId = currentId,
                genres = genres,
                studios = studios,
                limit = limit,
            )

        override suspend fun boxSetsContaining(itemId: String): Result<List<CollectionSummary>> =
            mediaCatalogueEnrichments.getBoxSetsContaining(itemId)
    }

    private val pluginRoles = object : DetailPluginRoles {
        override suspend fun pluginSimilarIds(itemId: String, limit: Int): List<String> =
            pluginArms.pluginSimilarIds(itemId, limit)

        override suspend fun mediaDetailItems(ids: List<String>): List<MediaItem> {
            // ONE batched ids read (the home plugin-row seam) instead of a
            // per-id detail fan-out; found-only (dead ids drop — the old
            // mapConcurrentCatching drop semantics) and mapped back onto the
            // requested order so the plugin's score order survives.
            val byId = libraryApiClient.getItemsByIds(ids).getOrNull().orEmpty().associateBy { it.id }
            return ids.mapNotNull(byId::get)
        }

        override fun serverSimilarPipeline(): Boolean =
            pluginArms.serverSimilarPipeline()

        override suspend fun animeMarkers(
            seriesId: String,
            providerSeriesId: String,
        ): List<JellyPlayEpisodeMarker>? =
            pluginArms.animeMarkers(seriesId, providerSeriesId)
    }

    private val mediaRoles = object : DetailMediaRoles {
        override suspend fun mediaSegments(itemId: String): Result<List<MediaSegment>> =
            playbackRepository.getMediaSegments(itemId)

        override suspend fun localTrailers(itemId: String): Result<List<MediaItem>> =
            libraryApiClient.getLocalTrailers(itemId)

        override suspend fun themeVideoClips(itemId: String): List<MediaItem> =
            libraryApiClient.getThemeVideos(itemId).getOrNull().orEmpty()

        override fun streamUrl(itemId: String, mediaSourceId: String): String =
            playbackRepository.getStreamUrl(itemId = itemId, mediaSourceId = mediaSourceId)
    }

    private val collectionStripRoles = object : DetailCollectionStripRoles {
        override suspend fun specialFeatures(itemId: String): Result<List<MediaItem>> =
            libraryApiClient.getSpecialFeatures(itemId)

        override suspend fun collectionItems(
            collectionId: String,
            startIndex: Int,
            limit: Int,
        ): Result<SearchResult> =
            collectionOperations.getCollectionItems(
                collectionId,
                startIndex = startIndex,
                limit = limit,
                force = false,
            )

        override suspend fun upcomingEpisodes(parentId: String, limit: Int): Result<List<MediaItem>> =
            libraryApiClient.getUpcoming(parentId = parentId, limit = limit)
    }

    private val ambientRoles = object : DetailAmbientRoles {
        override fun playThemeMusic(itemId: String) = themeMusicPlayer.playThemeFor(itemId)

        override suspend fun resolveArrServers(): Result<ArrServiceSummary> =
            remoteDiscovery.arrRepository.resolveServers()

        override suspend fun triggerSeerrDiscovery(detail: MediaDetail) =
            loadSeerrDataIfNeeded(detail)
    }

    private val enrichments: List<DetailEnrichment> =
        // Wave 3: the twelve state-feeding declarations moved verbatim into
        // DetailEnrichmentModules.kt (one module per arm family, evaluated
        // through the role adapters above); the section modules' arms stay
        // spliced between the registry's leading and trailing lists — the
        // pluginRatings probe externalRatings reads latches before the
        // trailing arms fire (the load-bearing order contract above).
        DetailEnrichmentRegistry.leadingArms(
            host = sectionHost,
            ambient = ambientRoles,
            similar = similarRoles,
            media = mediaRoles,
            pluginGate = pluginGateRole,
        ) +
            DetailSectionRegistry.enrichments(
                host = sectionHost,
                pluginGate = pluginGateRole,
                ratings = ratingsRoles,
                strip = collectionStripRoles,
                similar = similarRoles,
            ) +
            DetailEnrichmentRegistry.trailingArms(
                host = sectionHost,
                pluginGate = pluginGateRole,
                ratings = ratingsRoles,
                plugin = pluginRoles,
                media = mediaRoles,
            )

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
        val store = pluginArms.statusStore ?: return false
        if (pluginProbedItemId != itemId) {
            pluginProbedItemId = itemId
            store.refresh()
        }
        return pluginArms.featureGate.isAvailableNowOrProbe(store, feature)
    }

    /**
     * The evaluator beside [reduceLoaded]: walks the declaration list in
     * order, launching each gated entry on the VM scope. viewModelScope's
     * Main.immediate keeps the launch bodies in declaration order up to each
     * body's first suspension point. [DetailLoadGuard] stays the single
     * admission seam: every body re-checks it at its suspension-point writes.
     */
    private fun runEnrichments(itemId: String, snapshot: MediaDetailSnapshot) {
        val inputs = DetailEnrichmentInputs(itemId = itemId, snapshot = snapshot)
        for (enrichment in enrichments) {
            if (!enrichment.gate(inputs)) continue
            scope.launch { enrichment.run(inputs) }
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
        bookExtrasJob = scope.launch {
            fun publish(toc: List<BookTocEntry>, pageCount: Int) {
                if (!loadGuard.isCurrent(itemId)) return
                _state.update {
                    it.copy(
                        book = (it.book ?: DetailUiState.BookDetailState(format = format)).copy(
                            toc = toc,
                            pageCount = pageCount,
                        ),
                    )
                }
            }

            val cached = runCatching { bookArms.tocCacheRepository.getToc(itemId) }.getOrNull()
            publish(cached?.entries ?: emptyList(), cached?.pageCount ?: 0)

            if (cached == null) {
                val probe = runCatching { bookArms.tocProber.probe(downloadPath, format) }.getOrNull()
                if (probe != null && loadGuard.isCurrent(itemId)) {
                    // Write-through: the next detail visit reads the cache.
                    runCatching {
                        bookArms.tocCacheRepository.putToc(itemId, probe.format, probe.pageCount, probe.entries)
                    }
                    publish(probe.entries, probe.pageCount)
                }
            }

            // Marks counts observe live (the reader edits them on another
            // screen; returning here re-renders the counts). Cancelled with
            // the job on the next load. The unwired marks repo's observe flows
            // never emit (NoopReaderAnnotationsRepository), so this collector
            // parks without writing — the counts stay untouched (silent
            // absence, never a state write).
            combine(
                bookArms.readerAnnotationsRepository.observeBookmarks(itemId),
                bookArms.readerAnnotationsRepository.observeAnnotations(itemId),
            ) { bookmarks, annotations -> bookmarks.size to annotations.size }
                .collect { (bookmarkCount, highlightCount) ->
                    if (!loadGuard.isCurrent(itemId)) return@collect
                    _state.update {
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

    // ── Smart-play resolution (the core reduction's continuation) ─────────

    private fun maybeComputeSmartPlayTarget() {
        val item = _state.value.detail?.item ?: return
        when (item.mediaType) {
            MediaType.SERIES -> computeSeriesSmartPlayTarget()
            MediaType.EPISODE -> computeEpisodeSmartPlayTarget(item)
            // A SEASON entry (#168) plays within that season only: the same
            // resolver over the entry season's slice of the series snapshot.
            MediaType.SEASON -> computeSeasonSmartPlayTarget(item)
            else -> _state.update { it.copy(smartPlayTarget = null) }
        }
    }

    private fun computeSeasonSmartPlayTarget(season: MediaItem) {
        scope.launch(smartPlayDispatcher) {
            val sorted = _state.value.sortedEpisodes.takeIf { it.isNotEmpty() }
            val result = sorted?.let { SmartPlayResolver.resolveSeason(season.id, it) }
            _state.update {
                it.copy(smartPlayTarget = result?.toUiTarget())
            }
        }
    }

    private fun computeSeriesSmartPlayTarget() {
        scope.launch(smartPlayDispatcher) {
            val state = _state.value
            val sorted = state.sortedEpisodes.takeIf { it.isNotEmpty() }
            if (sorted == null) {
                _state.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            val result = SmartPlayResolver.resolveSeries(sorted)
            if (result == null) {
                _state.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            _state.update {
                it.copy(smartPlayTarget = result.toUiTarget())
            }
        }
    }

    private fun computeEpisodeSmartPlayTarget(currentEpisode: MediaItem) {
        scope.launch(smartPlayDispatcher) {
            val sorted = _state.value.sortedEpisodes.takeIf { it.isNotEmpty() } ?: return@launch
            // The episode must still be present in the current sorted view.
            if (sorted.none { it.id == currentEpisode.id }) {
                _state.update { it.copy(smartPlayTarget = null) }
                return@launch
            }
            _state.update {
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
            // The episode's own Primary art — tag included, so a re-uploaded
            // still mints a new URL (the stale-artwork fix).
            primaryImageUrl = artworkUrls.poster(episode),
            labelKind = this.label,
        )
    }

    // ── Seerr companion discovery (the ambient role's trigger half) ───────

    /**
     * The TMDB-companion choreography shared with [SeerrDetailViewModel]: the
     * holder owns the movie/tv videos fork, the connected x
     * recommendations-enabled x staleness gate, the bounded fan-out, per-leg
     * error tolerance, and the take-limits. Built on the
     * [SeerrRequestStateHolder] template - constructor-lambda fetch seams
     * straight onto [RemoteDiscoveryClients.seerrRepository], a single
     * snapshot surface, landings folded into this screen's content state.
     */
    private val tmdbCompanion = TmdbCompanionStateHolder(
        scope = scope,
        fetches = TmdbCompanionFetches.of(remoteDiscovery.seerrRepository),
    )

    private fun loadSeerrData(detail: MediaDetail, generation: Long) {
        scope.launch {
            if (!loadGuard.isCurrent(generation)) return@launch
            _state.update {
                it.copy(
                    seerrRecommendations = emptyList(),
                    seerrSimilar = emptyList(),
                    relatedVideos = emptyList(),
                    // Parity: the merged trailer list drops its (now
                    // stale) remote entries and keeps the local ones — the
                    // videos leg re-merges on landing, exactly like the raw
                    // relatedVideos reset below.
                    trailers = mergeTrailerEntries(
                        localTrailers = it.trailers.localTrailerItems(),
                        remoteVideos = emptyList(),
                        preferLocalTrailers = preferences.value.preferLocalTrailers,
                    ),
                    tmdbReviews = emptyList(),
                )
            }

            if (remoteDiscovery.offlineModeManager.networkStatus.value == NetworkStatus.Local) return@launch

            val mediaType = detail.item.mediaType
            if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return@launch

            val tmdbId = resolveTmdbId(detail) // top-level fn in TmdbIdResolver.kt
            if (tmdbId == null) return@launch

            // Read the already-resolved Seerr connection booleans from the
            // published [DetailUiState] aggregator — NOT the raw content
            // state. The flags are folded into the published snapshot by the
            // VM's outer combine, but are never written to the content state,
            // so reading the raw value here would always yield the default
            // false and skip every Seerr fetch. The published state is a hot
            // StateFlow, so the read is a snapshot with no subscription cost.
            val connected = publishedState().isSeerrConnected

            if (!loadGuard.isCurrent(generation)) return@launch

            // Fire-and-forget: each leg lands into the content state as it
            // resolves, staleness-checked atomically with its write via the
            // request's [TmdbCompanionRequest.isCurrent] (this screen's
            // captured epoch — the same seam startInstantMix passes its guard
            // through).
            tmdbCompanion.load(
                TmdbCompanionRequest(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    connected = connected,
                    recommendationsEnabled = publishedState().isSeerrRecommendationsEnabled,
                    loadVideos = true,
                    // The reviews section's own pref — the admission fold
                    // re-checks it (and an empty tmdbReviews renders nothing),
                    // so skipping the leg when it's off is a pure perf skip,
                    // never correctness. Videos/recommendations keep their
                    // hardcoded legs (no owning pref / recommendationsEnabled
                    // already gates the latter).
                    loadReviews = preferences.value.showReviews,
                    loadRecommendations = true,
                    isCurrent = { loadGuard.isCurrent(generation) },
                    onLanding = ::foldCompanionLanding,
                ),
            )
        }
    }

    /** Maps one holder landing onto its [DetailUiState] field. */
    private fun foldCompanionLanding(landing: TmdbCompanionLanding) {
        _state.update {
            when (landing) {
                is TmdbCompanionLanding.Videos ->
                    it.copy(
                        relatedVideos = landing.videos,
                        // The merged trailer list re-merges with the
                        // fresh remote leg, keeping whichever local entries
                        // the `trailers` enrichment already wrote.
                        trailers = mergeTrailerEntries(
                            localTrailers = it.trailers.localTrailerItems(),
                            remoteVideos = landing.videos,
                            preferLocalTrailers = preferences.value.preferLocalTrailers,
                        ),
                    )
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
}
