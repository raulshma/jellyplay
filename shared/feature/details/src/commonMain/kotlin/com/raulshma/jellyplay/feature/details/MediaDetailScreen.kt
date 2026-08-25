package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import kotlinx.coroutines.launch
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.rememberIsLightTheme
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.quickActions
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmState
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.DeleteDownloadedEpisodesSheet
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.SeerrPrefetchCallback
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberVideoClickHandler
import com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cancel
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cellular_download_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cellular_download_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_download_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_download_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_episode_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_episode_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_download_anyway
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_unwatched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_unwatched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_watched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_watched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_unwatched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_unwatched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_watched_confirm_message
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_series_watched_confirm_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_no_episodes_queued
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_watch_party_started
import com.raulshma.jellyplay.feature.details.generated.resources.detail_episodes_queued
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_confirm
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import org.jetbrains.compose.resources.pluralStringResource

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaDetailScreen(
    itemId: String,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long, subtitleStreamIndex: Int?, audioStreamIndex: Int?) -> Unit,
    onAudioClick: (itemId: String) -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onPersonClick: (personId: String) -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
    onManageSeries: (seriesId: String) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onEditClick: (itemId: String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DetailViewModel = koinViewModel(),
) {
    // Declared before the load-item effect below so the trailer-dismiss reset
    // can be merged into it (was previously a separate LaunchedEffect(itemId)).
    var activeTrailerKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
        // Dismiss any open trailer dialog when navigating to a different item
        // (previously a separate LaunchedEffect(itemId) block).
        activeTrailerKey = null
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Download-lifecycle slice: collected ONCE here because the always-composed
    // content tree needs the button spinner + picker + downloaded-ids fields
    // (they flow into DetailContentState below); the sheets read the same
    // value with no per-sheet subscription.
    val downloads by viewModel.downloads.state.collectAsStateWithLifecycle()
    // Resync slice: same story — the always-composed freshness banner reads it
    // through the bag; the resync sheet reads the same value.
    val resyncState by viewModel.resync.state.collectAsStateWithLifecycle()
    val detail = uiState.detail
    val canManageSeries by viewModel.canManageSeries.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val currentItem = detail?.item
    val targetBackdropId = currentItem
        ?.takeIf { it.mediaType == MediaType.EPISODE }?.seriesId
        ?: currentItem?.id
        ?: itemId
    // On-entry freshness check (TTL-gated, safe on every entry) — ports the
    // old OfflineDetailScreen auto-fire. Only relevant when there's local
    // content to check (an attached download or a local origin); the sync
    // manager no-ops within the per-item 1h TTL or while offline.
    val hasLocalContent = uiState.detailContext?.download != null || uiState.origin?.isLocal == true
    LaunchedEffect(currentItem?.id, hasLocalContent) {
        if (currentItem != null && hasLocalContent) {
            viewModel.resync.checkForUpdates()
        }
    }
    // Memoized so the URL isn't rebuilt on every recomposition (e.g. on each
    // scroll-derived state change funnelling through ArtworkThemeWrapper).
    val backdropUrl = remember(targetBackdropId) { viewModel.getBackdropUrl(targetBackdropId) }

    val outerIsLightTheme = rememberIsLightTheme()

    var showSeriesDownloadSheet by remember { mutableStateOf(false) }
    /** Series batch-delete sheet (multi-select downloaded episodes). */
    var showDeleteEpisodesSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()

    // ── Unified-provider action dialog state. Delete / resync /
    // re-download get the same TV/mobile focus, back, and snackbar handling as
    // the existing detail actions. ──
    /** Pending delete of the current item's attached download (single item or episode). */
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    /** Pending delete of a downloaded episode from the seasons section. */
    var pendingDeleteEpisode by remember { mutableStateOf<PendingEpisodeDelete?>(null) }
    /** Resync bottom-sheet visibility (banner tap). */
    var showResyncSheet by remember { mutableStateOf(false) }
    /** Full download-details bottom-sheet visibility (DownloadInfoCard tap). */
    var showDownloadDetailsSheet by remember { mutableStateOf(false) }

    // Series/season mark-played cascades recurse into every episode and clear all
    // resume positions, so they're gated behind a confirm
    // Direction is tracked alongside so the dialog shows the right verb/message
    // for a watched vs unwatched flip.
    val markSeriesConfirm = rememberConfirmState()
    var markSeriesToWatched by remember { mutableStateOf(true) }
    val markSeasonConfirm = rememberConfirmState()
    var markSeasonToWatched by remember { mutableStateOf(true) }

    // Quick actions for row items (related/collection/episode cards) and the
    // TV Menu key on the focused card. The controller is
    // provided to every PosterCard/EpisodeCard below via CompositionLocal.
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember(viewModel) { { item: MediaItem -> item.quickActions(MediaQuickActionScope.DETAIL) } },
        executeAction = remember(viewModel, onPlayClick, onItemClick) {
            { item: MediaItem, action: QuickAction ->
                when (action) {
                    QuickAction.PLAY -> onPlayClick(
                        item.id,
                        null,
                        item.playbackPositionTicks ?: 0L,
                        viewModel.selectedSubtitleIndex,
                        viewModel.selectedAudioIndex,
                    )
                    QuickAction.MARK_WATCHED -> viewModel.markRowItemPlayed(item, played = true)
                    QuickAction.MARK_UNWATCHED -> viewModel.markRowItemPlayed(item, played = false)
                    QuickAction.DETAILS -> onItemClick(item.id)
                    else -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions.
    var tvFocusedItem by remember { mutableStateOf<MediaItem?>(null) }

    // Composition state hop for the queued-episodes plural snackbar (see the
    // SeriesDownload branch below): the plural resolves in composition
    // (@Composable pluralStringResource — CMP's suspend plural resolver is
    // internal), the effect shows it and clears the hop.
    var queuedEpisodesSnackbarCount by remember { mutableStateOf<Int?>(null) }
    val queuedEpisodesCount = queuedEpisodesSnackbarCount
    if (queuedEpisodesCount != null) {
        val queuedText = pluralStringResource(
            Res.plurals.detail_episodes_queued,
            queuedEpisodesCount,
            queuedEpisodesCount,
        )
        LaunchedEffect(queuedEpisodesCount) {
            snackbarHostState.showSnackbar(queuedText)
            queuedEpisodesSnackbarCount = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                is DetailMessage.Text -> snackbarHostState.showSnackbar(message.text)
                is DetailMessage.SeriesDownload -> {
                    // Plain strings resolve via the suspend compose-resources
                    // resolver inside collect (syncplay flow-escape pattern).
                    // The PLURAL cannot: CMP's suspend plural resolver is
                    // internal, so the count hops through composition state
                    // and the effect below renders it with the @Composable
                    // pluralStringResource.
                    if (message.error != null) {
                        snackbarHostState.showSnackbar(message.error)
                    } else if (message.queuedCount > 0) {
                        queuedEpisodesSnackbarCount = message.queuedCount
                    } else {
                        snackbarHostState.showSnackbar(getString(Res.string.detail_msg_no_episodes_queued))
                    }
                }
                is DetailMessage.WatchPartyStarted -> {
                    // Fire-and-forget confirmation; navigation (below) is the
                    // primary feedback. Launched on screenScope so it does not
                    // block the navigate-to-player handoff.
                    screenScope.launch {
                        snackbarHostState.showSnackbar(
                            getString(Res.string.detail_msg_watch_party_started)
                        )
                    }
                    // Open the player, reusing the SAME Route.VideoPlayer builder
                    // the play button uses (DetailsNavigation wires onPlayClick →
                    // Route.VideoPlayer). startPositionTicks = 0 = fresh group
                    // start; the SyncPlayBridge auto-detects the active session.
                    onPlayClick(message.itemId, null, 0L, null, null)
                }
            }
        }
    }

    val cellularWarningMb = downloads.cellularDownloadWarningMb
    if (cellularWarningMb != null) {
        ConfirmDialog(
            title = stringResource(Res.string.detail_cellular_download_title),
            message = stringResource(Res.string.detail_cellular_download_message, cellularWarningMb),
            confirmText = stringResource(Res.string.detail_download_anyway),
            dismissText = stringResource(Res.string.detail_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = { viewModel.downloads.confirmCellularDownload() },
            onDismiss = { viewModel.downloads.dismissCellularDownloadWarning() },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    // TV remote Menu button: open the focused card's quick
                    // actions. The focused card is tracked by the
                    // rows via onFocusedMediaItem.
                    val focused = tvFocusedItem
                    if (focused != null) {
                        quickActionController.show(focused)
                        true
                    } else {
                        false
                    }
                },
            ),
    ) {
        ArtworkThemeWrapper(
            imageUrl = backdropUrl,
            dynamicTheming = preferences.theme.dynamicTheming,
            darkTheme = !outerIsLightTheme,
            oledMode = preferences.theme.oledMode,
            colorStyle = preferences.theme.colorStyle,
            accentColorSwatch = preferences.theme.accentColorSwatch,
        ) {
            val downloadFlow = remember(itemId) { viewModel.downloads.downloadFlow(itemId) }
            val activeDownload by downloadFlow.collectAsStateWithLifecycle(initialValue = null)

            // Seerr integration state (the holder's single snapshot, folded
            // into uiState as-is)
            val seerrRequest = uiState.seerrRequest
            var seerrRequestItem by remember { mutableStateOf<SeerrSearchItem?>(null) }

            // Seerr card loading state for prefetch animation
            val seerrLoadingState = rememberSeerrCardLoadingState()
            val seerrPrefetchCallback: SeerrPrefetchCallback =
                remember(seerrLoadingState, viewModel) {
                    { tmdbId, mediaType, onDone ->
                        seerrLoadingState.startLoading(tmdbId)
                        viewModel.seerrRequests.prefetchDetails(tmdbId, mediaType) {
                            seerrLoadingState.stopLoading(tmdbId)
                            onDone()
                        }
                    }
                }

            CompositionLocalProvider(
                LocalSeerrPrefetch provides seerrPrefetchCallback,
                LocalSeerrCardLoadingState provides seerrLoadingState,
            ) {
                val rememberedGetImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } }
                val rememberedGetBackdropUrl = remember(viewModel) { { id: String -> viewModel.getBackdropUrl(id) } }
                val rememberedGetSeerrPosterUrl = remember(viewModel) { { path: String? -> viewModel.getSeerrPosterUrl(path) } }
                val rememberedGetChapterImageUrl = remember(viewModel) {
                    { id: String, index: Int, tag: String? -> viewModel.getChapterImageUrl(id, index, tag) }
                }

                // Series id used by season-episode fetches. Keyed on BOTH the
                // item id and the resolved series id so it recomputes once the
                // detail arrives. Previously this keyed only on `itemId`, so on
                // first composition (detail still null) an episode resolved to
                // its own id as the series id — producing wrong/empty season
                // fetches and wasted refetches.
                val seriesIdForSeasons = remember(itemId, detail?.item?.seriesId) {
                    detail?.item?.seriesId ?: itemId
                }

                // Constructed directly (not via `remember`) — DetailContentState
                // is a @Immutable data class, so Compose structural-equals it for
                // free and `DetailContent` is skipped whenever no field actually
                // changed. The former 22-key `remember` added per-recomposition
                // key-comparison cost and silently drifted whenever a new uiState
                // field was added without updating the key list.
                val state = DetailContentState(
                    itemId = itemId,
                    detail = detail,
                    seasons = uiState.seasons,
                    episodes = uiState.episodes,
                    fetchedSeasonIds = uiState.fetchedSeasonIds,
                    smartPlayTarget = uiState.smartPlayTarget,
                    // Resolve the user's last-pinned season for this series from
                    // the projected preferences. An active resume still wins
                    // (decided in SeasonStartResolver); null when nothing is
                    // pinned → prior behaviour.
                    persistedSeasonId = preferences.lastViewedSeasonBySeries[seriesIdForSeasons],
                    selectedSubtitleIndex = uiState.selectedSubtitleIndex,
                    selectedAudioIndex = uiState.selectedAudioIndex,
                    isDownloading = downloads.isDownloading,
                    isDownloadingSeries = downloads.isDownloadingSeries,
                    activeDownload = activeDownload,
                    loadState = uiState.loadState,
                    albumTracks = uiState.albumTracks,
                    collectionItems = uiState.collectionItems,
                    relatedItems = uiState.relatedItems,
                    specialFeatures = uiState.specialFeatures,
                    localRelatedItems = uiState.localRelatedItems,
                    hasIntroSegment = uiState.hasIntroSegment,
                    hasCreditSegment = uiState.hasCreditSegment,
                    relatedVideos = uiState.relatedVideos,
                    tmdbReviews = uiState.tmdbReviews,
                    seerrRecommendations = uiState.seerrRecommendations,
                    seerrSimilar = uiState.seerrSimilar,
                    isSeerrConnected = uiState.isSeerrConnected,
                    isSeerrRecommendationsEnabled = uiState.isSeerrRecommendationsEnabled,
                    preferences = preferences,
                    canManageSeries = canManageSeries,
                    origin = uiState.origin,
                    detailContext = uiState.detailContext,
                    capabilities = uiState.capabilities,
                    assets = uiState.assets,
                    localSubtitles = uiState.localSubtitles,
                    selectedLocalSubtitleIndex = uiState.selectedLocalSubtitleIndex,
                    resyncState = resyncState,
                    downloadedEpisodeIds = downloads.downloadedEpisodeIds,
                    downloadPicker = downloads.downloadPicker,
                )

                val onVideoClick = rememberVideoClickHandler(
                    uriHandler = uriHandler,
                    onPlayYouTube = { key -> activeTrailerKey = key },
                )

                val callbacks = remember(
                    rememberedGetImageUrl, rememberedGetBackdropUrl, rememberedGetSeerrPosterUrl,
                    rememberedGetChapterImageUrl,
                    viewModel, onPlayClick, onAudioClick, itemId, onItemClick, onPersonClick,
                    onNavigateToSeries, onNavigate, onEditClick, onManageSeries, onBack, onVideoClick,
                ) {
                    DetailContentCallbacks(
                        getImageUrl = rememberedGetImageUrl,
                        getBackdropUrl = rememberedGetBackdropUrl,
                        getSeerrPosterUrl = rememberedGetSeerrPosterUrl,
                        getChapterImageUrl = rememberedGetChapterImageUrl,
                        onRetry = { viewModel.loadItem(itemId) },
                        onRefresh = { viewModel.forceRefresh() },
                        onPlayClick = { playItemId: String, sourceId: String?, start: Long ->
                            // For a LOCAL origin the server stream index is meaningless; pass
                            // the chosen local-manifest subtitle index instead so the player's
                            // offline-id wiring (TrackSelectionPolicy.resolveByOfflineSubtitleId)
                            // resolves the right side-loaded subtitle. The remote audio index is
                            // still threaded because the local audio inventory is not selectable
                            // here.
                            val isLocalOrigin = uiState.origin?.isLocal == true
                            val subtitleIndex = if (isLocalOrigin) {
                                viewModel.selectedLocalSubtitleIndex
                            } else {
                                viewModel.selectedSubtitleIndex
                            }
                            onPlayClick(
                                playItemId,
                                sourceId,
                                start,
                                subtitleIndex,
                                viewModel.selectedAudioIndex,
                            )
                        },
                        onPlayChapter = { start ->
                            // Resume the current item at a chapter position. Reuses the
                            // same play path + stream selection as the primary play button
                            // (local-origin subtitle index when offline).
                            val isLocalOrigin = uiState.origin?.isLocal == true
                            val subtitleIndex = if (isLocalOrigin) {
                                viewModel.selectedLocalSubtitleIndex
                            } else {
                                viewModel.selectedSubtitleIndex
                            }
                            onPlayClick(
                                itemId,
                                null,
                                start,
                                subtitleIndex,
                                viewModel.selectedAudioIndex,
                            )
                        },
                        onPlayExtra = { extra ->
                            // Play a special feature / extra from the start. An extra is
                            // its own item (trailer / featurette / deleted scene), so it
                            // starts at position 0 and inherits no subtitle/audio selection
                            // from the parent item.
                            onPlayClick(extra.id, null, 0L, null, null)
                        },
                        onAudioClick = { onAudioClick(itemId) },
                        onDownloadClick = { viewModel.downloads.startDownload() },
                        onOpenDownloadPicker = { viewModel.downloads.openDownloadPicker() },
                        onDismissDownloadPicker = { viewModel.downloads.dismissDownloadPicker() },
                        onPendingQualityChange = { viewModel.downloads.setPendingQuality(it) },
                        onPendingSubtitleSelectionChange = { viewModel.downloads.setPendingSubtitleSelection(it) },
                        onDownloadSeriesClick = {
                            showSeriesDownloadSheet = true
                            viewModel.downloads.loadDownloadedEpisodeIds()
                            viewModel.downloads.prepareDownloadSheetEpisodes()
                        },
                        onToggleFavorite = { viewModel.toggleFavorite() },
                        onMarkPlayed = {
                            // A series mark recurses into every episode and clears every
                            // resume position; confirm first. Single movies/episodes flip
                            // immediately (trivially reversible via the same button).
                            if (currentItem?.mediaType == MediaType.SERIES) {
                                markSeriesToWatched = true
                                markSeriesConfirm.request { viewModel.markPlayed() }
                            } else {
                                viewModel.markPlayed()
                            }
                        },
                        onMarkUnplayed = {
                            if (currentItem?.mediaType == MediaType.SERIES) {
                                markSeriesToWatched = false
                                markSeriesConfirm.request { viewModel.markUnplayed() }
                            } else {
                                viewModel.markUnplayed()
                            }
                        },
                        onMarkSeasonPlayed = { seasonId ->
                            markSeasonToWatched = true
                            markSeasonConfirm.request { viewModel.markSeasonPlayed(seasonId) }
                        },
                        onMarkSeasonUnplayed = { seasonId ->
                            markSeasonToWatched = false
                            markSeasonConfirm.request { viewModel.markSeasonUnplayed(seasonId) }
                        },
                        onSubtitleSelect = { idx: Int? -> viewModel.selectSubtitle(idx) },
                        onAudioSelect = { idx: Int? -> viewModel.selectAudio(idx) },
                        onItemClick = onItemClick,
                        onPersonClick = onPersonClick,
                        onSeeAllCast = {
                            onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.CastAndCrew(itemId))
                        },
                        onNavigateToSeries = onNavigateToSeries,
                        onSeasonSelected = { seasonId: String ->
                            viewModel.loadEpisodesForSeason(seriesIdForSeasons, seasonId)
                        },
                        onSeasonPinned = { seasonId: String ->
                            viewModel.setLastViewedSeason(seriesIdForSeasons, seasonId)
                        },
                        onEpisodesDescendingChange = { descending: Boolean ->
                            viewModel.setEpisodesDescending(descending)
                        },
                        onCompactEpisodeListChange = { enabled: Boolean ->
                            viewModel.setCompactEpisodeList(enabled)
                        },
                        onBack = onBack,
                        onSeerrRequest = { item: SeerrSearchItem -> seerrRequestItem = item },
                        onNavigate = onNavigate,
                        onEditClick = { onEditClick(itemId) },
                        onPlayAlbumTrack = { index: Int -> viewModel.playAlbum(index) },
                        onVideoClick = onVideoClick,
                        onHideFromNextUp = { viewModel.hideFromNextUp() },
                        onShowFromNextUp = { viewModel.showFromNextUp() },
                        onHideFromContinueWatching = { viewModel.hideFromContinueWatching() },
                        onShowFromContinueWatching = { viewModel.showFromContinueWatching() },
                        onShowDetailUpNext = { viewModel.setShowDetailUpNext(true) },
                        onHideDetailUpNext = { viewModel.setShowDetailUpNext(false) },
                        onManageSeries = { onManageSeries(itemId) },
                        onAddToPlaylist = { viewModel.playlists.openPlaylistPicker() },
                        onAddToCollection = { viewModel.collections.openCollectionPicker() },
                        onStartInstantMix = { viewModel.startInstantMix() },
                        onStartWatchParty = { viewModel.watchParty.startScreenItem() },
                        onMediaQuickActions = { item -> quickActionController.show(item) },
                        onFocusedMediaItem = { item -> tvFocusedItem = item },
                        onDeleteDownload = {
                            val target = detail?.item
                            val isEpisode = target?.mediaType == MediaType.EPISODE
                            pendingDelete = PendingDelete(
                                itemId = target?.id ?: itemId,
                                name = target?.name ?: "",
                                sizeBytes = uiState.detailContext?.download?.totalSizeBytes ?: 0L,
                                isEpisode = isEpisode,
                            )
                        },
                        onDeleteDownloadedEpisodes = { showDeleteEpisodesSheet = true },
                        onDeleteEpisode = { episodeId ->
                            val ep = uiState.episodes.values.flatten().firstOrNull { it.id == episodeId }
                            pendingDeleteEpisode = PendingEpisodeDelete(
                                episodeId = episodeId,
                                name = ep?.name ?: "",
                            )
                        },
                        onOpenResync = { showResyncSheet = true },
                        onResync = { viewModel.resync.resync() },
                        onRedownloadMedia = { viewModel.resync.redownloadMedia() },
                        onClearResync = { viewModel.resync.clearResyncState() },
                        onOpenDownloadDetails = {
                            // Load the on-disk inventory (media + sidecars) before showing
                            // the sheet so sizes are fresh; it re-reads on every open.
                            viewModel.downloads.loadDownloadFileInventory()
                            showDownloadDetailsSheet = true
                        },
                        onSelectLocalSubtitle = { index -> viewModel.selectLocalSubtitle(index) },
                    )
                }

                val availableStorageProvider = remember(viewModel) {
                    val provider: suspend (Boolean) -> Long = { isAudio: Boolean -> viewModel.getAvailableStorageBytes(isAudio) }
                    provider
                }

                CompositionLocalProvider(
                    LocalMediaQuickActionController provides quickActionController,
                ) {
                    DetailContent(
                        state = state,
                        callbacks = callbacks,
                        availableStorageProvider = availableStorageProvider,
                    )
                }

                // Seerr request dialog
                seerrRequestItem?.let { item ->
                    // Fetch service details and TV seasons on-demand when dialog opens
                    LaunchedEffect(item.id) {
                        viewModel.seerrRequests.loadServiceDetails(item.mediaType)
                        if (item.mediaType.equals("tv", ignoreCase = true)) {
                            viewModel.seerrRequests.loadTvSeasons(item.id)
                        }
                    }

                    SeerrRequestDialog(
                        item = item,
                        snapshot = seerrRequest,
                        onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                            viewModel.seerrRequests.requestMedia(item, seasons, serverId, profileId, rootFolder, tags)
                        },
                        onDismiss = {
                            seerrRequestItem = null
                            viewModel.seerrRequests.clearRequestResult()
                        },
                    )
                }
            } // CompositionLocalProvider
        }

        // ── Add-to-Playlist sheet + create dialog. State is collected from the
        // owning helper at the composition site that reads it — a closed sheet
        // composes nothing and the content core never sees a playlist tick. ──
        val playlistState by viewModel.playlists.state.collectAsStateWithLifecycle()
        if (playlistState.showPlaylistPicker && detail != null) {
            val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { viewModel.playlists.dismissPlaylistPicker() },
                sheetState = playlistSheetState,
            ) {
                AddToPlaylistSheet(
                    playlists = playlistState.playlists,
                    isLoading = playlistState.isLoadingPlaylists,
                    isAdding = playlistState.isAddingToPlaylist,
                    onWatchLater = { viewModel.playlists.addToWatchLater() },
                    onPick = { playlist -> viewModel.playlists.addToPlaylist(playlist) },
                    onCreateNew = { viewModel.playlists.openCreatePlaylistDialog() },
                    onDismiss = { viewModel.playlists.dismissPlaylistPicker() },
                )
            }
        }

        if (playlistState.showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                isLoading = playlistState.isAddingToPlaylist,
                onConfirm = { name, overview ->
                    viewModel.playlists.createAndAddPlaylist(name, overview)
                },
                onDismiss = { viewModel.playlists.dismissCreatePlaylistDialog() },
            )
        }

        // ── Add-to-Collection sheet + create dialog (mirror of the playlist
        // block; same collection locality). ──
        val collectionState by viewModel.collections.state.collectAsStateWithLifecycle()
        if (collectionState.showCollectionPicker && detail != null) {
            val collectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { viewModel.collections.dismissCollectionPicker() },
                sheetState = collectionSheetState,
            ) {
                AddToCollectionSheet(
                    collections = collectionState.collections,
                    isLoading = collectionState.isLoadingCollections,
                    isAdding = collectionState.isAddingToCollection,
                    onPick = { collection -> viewModel.collections.addToCollection(collection) },
                    onCreateNew = { viewModel.collections.openCreateCollectionDialog() },
                    onDismiss = { viewModel.collections.dismissCollectionPicker() },
                )
            }
        }

        if (collectionState.showCreateCollectionDialog) {
            CreateCollectionDialog(
                isLoading = collectionState.isAddingToCollection,
                onConfirm = { name ->
                    viewModel.collections.createAndAddCollection(name)
                },
                onDismiss = { viewModel.collections.dismissCreateCollectionDialog() },
            )
        }

        val detailItem = detail?.item
        if (showSeriesDownloadSheet && detailItem?.mediaType == MediaType.SERIES) {
            val downloadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = {
                    showSeriesDownloadSheet = false
                    viewModel.downloads.resetDownloadSheetState()
                },
                sheetState = downloadSheetState,
            ) {
                SeriesDownloadSheet(
                    seasons = uiState.seasons,
                    episodes = downloads.downloadSheetEpisodes,
                    loadingSeasons = downloads.downloadSheetLoadingSeasons,
                    downloadedEpisodeIds = downloads.downloadedEpisodeIds,
                    onLoadEpisodes = { seasonId ->
                        viewModel.downloads.loadDownloadSheetEpisodes(seasonId)
                    },
                    isDownloading = downloads.isDownloadingSeries,
                    onDownload = { selectedEpisodes ->
                        showSeriesDownloadSheet = false
                        val nonEmpty = selectedEpisodes.filter { it.value.isNotEmpty() }
                        viewModel.downloads.downloadSeries(nonEmpty)
                        viewModel.downloads.resetDownloadSheetState()
                    },
                    onDismiss = {
                        showSeriesDownloadSheet = false
                        viewModel.downloads.resetDownloadSheetState()
                    },
                )
            }
        }

        // ── Series batch-delete sheet. Replaces the old
        // OfflineSeriesScreen trash flow: multi-select downloaded episodes /
        // whole seasons / the entire series, then route through the merged
        // DetailViewModel offline-delete methods (which collapse a fully-
        // selected season into a single deleteOfflineSeason transaction). ──
        if (showDeleteEpisodesSheet && detailItem?.mediaType == MediaType.SERIES) {
            // For a LOCAL origin every episode in the snapshot is downloaded;
            // the sheet treats each listed episode as deletable. Only seasons
            // that actually carry episodes are passed so the sheet renders no
            // empty rows.
            val downloadedEpisodesBySeason = remember(uiState.episodes) {
                uiState.episodes.filterValues { it.isNotEmpty() }
            }
            val downloadableSeasons = remember(uiState.seasons, downloadedEpisodesBySeason) {
                uiState.seasons.filter { it.id in downloadedEpisodesBySeason }
            }
            val totalSizeBytes = uiState.detailContext?.seriesAggregate?.totalSizeBytes ?: 0L
            val downloadedEpisodeCount = remember(uiState.detailContext, downloadedEpisodesBySeason) {
                uiState.detailContext?.seriesAggregate?.downloadedEpisodeCount
                    ?: downloadedEpisodesBySeason.values.sumOf { it.size }
            }
            val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { showDeleteEpisodesSheet = false },
                sheetState = deleteSheetState,
            ) {
                DeleteDownloadedEpisodesSheet(
                    seasons = downloadableSeasons,
                    episodes = downloadedEpisodesBySeason,
                    totalSizeBytes = totalSizeBytes,
                    episodeSizeBytes = uiState.detailContext?.seriesAggregate?.episodeSizeBytes ?: emptyMap(),
                    onDelete = { episodeIds ->
                        showDeleteEpisodesSheet = false
                        viewModel.offline.deleteOfflineEpisodes(episodeIds.toList())
                        // If every downloaded episode was selected there's nothing
                        // left to show — pop back to where the user came from.
                        if (episodeIds.size >= downloadedEpisodeCount) {
                            onBack()
                        }
                    },
                    onDeleteEntireSeries = {
                        showDeleteEpisodesSheet = false
                        viewModel.offline.deleteOfflineSeries(itemId)
                        onBack()
                    },
                    onDismiss = { showDeleteEpisodesSheet = false },
                )
            }
        }

        com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )

        // Series mark-played confirm : watched vs unwatched differ only in verb.
        if (markSeriesConfirm.isVisible) {
            val title = stringResource(
                if (markSeriesToWatched) Res.string.detail_mark_series_watched_confirm_title
                else Res.string.detail_mark_series_unwatched_confirm_title,
            )
            val message = stringResource(
                if (markSeriesToWatched) Res.string.detail_mark_series_watched_confirm_message
                else Res.string.detail_mark_series_unwatched_confirm_message,
            )
            markSeriesConfirm.ConfirmDialog(
                title = title,
                message = message,
                confirmText = stringResource(CoreUiRes.string.core_confirm),
                dismissText = stringResource(CoreUiRes.string.core_cancel),
            )
        }

        // Season mark-played confirm.
        if (markSeasonConfirm.isVisible) {
            val title = stringResource(
                if (markSeasonToWatched) Res.string.detail_mark_season_watched_confirm_title
                else Res.string.detail_mark_season_unwatched_confirm_title,
            )
            val message = stringResource(
                if (markSeasonToWatched) Res.string.detail_mark_season_watched_confirm_message
                else Res.string.detail_mark_season_unwatched_confirm_message,
            )
            markSeasonConfirm.ConfirmDialog(
                title = title,
                message = message,
                confirmText = stringResource(CoreUiRes.string.core_confirm),
                dismissText = stringResource(CoreUiRes.string.core_cancel),
            )
        }

        activeTrailerKey?.let { key ->
            Dialog(
                onDismissRequest = { activeTrailerKey = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    InlineTrailerPlayerHost(
                        videoKey = key,
                        modifier = Modifier.fillMaxSize(),
                        muted = false,
                        showControls = true,
                        autoplay = true,
                        onEmbedFailed = {
                            activeTrailerKey = null
                            uriHandler.openUri("https://www.youtube.com/watch?v=$key")
                        },
                    )
                }
            }
        }

        // Long-press / TV-Menu quick actions for row cards.
        MediaQuickActionHost(quickActionController)

        // ── Unified-provider delete confirmation. ──
        // Single item: deletes the current item's attached download. Episode: a
        // downloaded episode from the seasons section. Both route through the
        // merged DetailViewModel offline-delete methods.
        pendingDelete?.let { target ->
            ConfirmDialog(
                title = stringResource(Res.string.detail_delete_download_title),
                message = stringResource(
                    Res.string.detail_delete_download_message,
                    target.name,
                    target.sizeBytes.formatBytes(),
                ),
                confirmText = stringResource(Res.string.detail_delete),
                dismissText = stringResource(Res.string.detail_cancel),
                icon = Tabler.Outline.Trash,
                tone = ConfirmTone.DESTRUCTIVE,
                onConfirm = {
                    viewModel.offline.deleteOfflineItem(target.itemId)
                    // A LOCAL origin has nothing left once its only download is
                    // removed — pop back instead of stranding the user on an
                    // empty detail (matches the series batch-delete behavior).
                    if (uiState.origin?.isLocal == true) onBack()
                },
                onDismiss = { pendingDelete = null },
            )
        }
        pendingDeleteEpisode?.let { ep ->
            ConfirmDialog(
                title = stringResource(Res.string.detail_delete_episode_title),
                message = stringResource(Res.string.detail_delete_episode_message, ep.name),
                confirmText = stringResource(Res.string.detail_delete),
                dismissText = stringResource(Res.string.detail_cancel),
                icon = Tabler.Outline.Trash,
                tone = ConfirmTone.DESTRUCTIVE,
                onConfirm = { viewModel.offline.deleteOfflineEpisode(ep.episodeId) },
                onDismiss = { pendingDeleteEpisode = null },
            )
        }

        // ── Resync bottom sheet. Lists what changed and offers a
        // resync / re-download action with live status. ──
        if (showResyncSheet) {
            ResyncSheet(
                syncState = uiState.detailContext?.syncState,
                resyncState = resyncState,
                onResync = { viewModel.resync.resync() },
                onRedownloadMedia = { viewModel.resync.redownloadMedia() },
                onDismiss = {
                    showResyncSheet = false
                    viewModel.resync.clearResyncState()
                },
            )
        }

        // ── Full download-details bottom sheet. Consolidates the attached
        // download lifecycle, media identity + watch progress, per-source media
        // info, and the on-disk downloaded files into one scrollable surface.
        // Opened by tapping the DownloadInfoCard header. Gated on the snapshot
        // item so a mid-open clearance simply dismisses it. ──
        val detailsItem = detail?.item
        if (showDownloadDetailsSheet && detailsItem != null) {
            val sheetBackdropUrl = uiState.assets.backdropPath
                ?: viewModel.getBackdropUrl(detailsItem.seriesId ?: detailsItem.id)

            val sheetPosterUrl = uiState.assets.posterPath
                ?: viewModel.getImageUrl(detailsItem.id)

            DownloadDetailsSheet(
                download = uiState.detailContext?.download,
                item = detailsItem,
                mediaSources = detail.mediaSources,
                inventory = downloads.downloadFileInventory,
                isLoadingInventory = downloads.isLoadingDownloadFiles,
                backdropUrl = sheetBackdropUrl,
                posterUrl = sheetPosterUrl,
                onDismiss = {
                    showDownloadDetailsSheet = false
                    viewModel.downloads.clearDownloadFileInventory()
                },
            )
        }
    } // Box
}

/**
 * Pending delete of the current item's attached download (single item or the
 * current episode). Surfaced as a confirm dialog because removing the file
 * clears resume state and frees storage.
 */
@androidx.compose.runtime.Immutable
private data class PendingDelete(
    val itemId: String,
    val name: String,
    val sizeBytes: Long,
    val isEpisode: Boolean,
)

/** Pending delete of a downloaded episode from the seasons section. */
@androidx.compose.runtime.Immutable
private data class PendingEpisodeDelete(
    val episodeId: String,
    val name: String,
)
