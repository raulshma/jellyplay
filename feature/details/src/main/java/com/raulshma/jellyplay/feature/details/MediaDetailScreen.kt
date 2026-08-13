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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
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
import com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer
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
    viewModel: DetailViewModel = hiltViewModel(),
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
            viewModel.checkForUpdates()
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
    val snackbarContext = LocalContext.current

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

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                is DetailMessage.Text -> snackbarHostState.showSnackbar(message.text)
                is DetailMessage.SeriesDownload -> {
                    val text = if (message.error != null) {
                        message.error
                    } else if (message.queuedCount > 0) {
                        snackbarContext.resources.getQuantityString(
                            R.plurals.detail_episodes_queued,
                            message.queuedCount,
                            message.queuedCount,
                        )
                    } else {
                        snackbarContext.getString(R.string.detail_msg_no_episodes_queued)
                    }
                    snackbarHostState.showSnackbar(text)
                }
            }
        }
    }

    val cellularWarningMb = uiState.cellularDownloadWarningMb
    if (cellularWarningMb != null) {
        ConfirmDialog(
            title = stringResource(R.string.detail_cellular_download_title),
            message = stringResource(R.string.detail_cellular_download_message, cellularWarningMb),
            confirmText = stringResource(R.string.detail_download_anyway),
            dismissText = stringResource(R.string.detail_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = { viewModel.confirmCellularDownload() },
            onDismiss = { viewModel.dismissCellularDownloadWarning() },
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
            val downloadFlow = remember(itemId) { viewModel.getDownloadFlow(itemId) }
            val activeDownload by downloadFlow.collectAsStateWithLifecycle(initialValue = null)

            // Seerr integration state (derived from the uiState snapshot)
            val seerrRadarrServers = uiState.seerrRadarrServers
            val seerrSonarrServers = uiState.seerrSonarrServers
            val seerrIsLoadingServices = uiState.isLoadingSeerrServices
            val seerrTvSeasons = uiState.seerrTvSeasons
            val seerrRequestResult = uiState.seerrRequestResult
            var seerrRequestItem by remember { mutableStateOf<SeerrSearchItem?>(null) }

            // Seerr card loading state for prefetch animation
            val seerrLoadingState = rememberSeerrCardLoadingState()
            val seerrPrefetchCallback: SeerrPrefetchCallback =
                remember(seerrLoadingState, viewModel) {
                    { tmdbId, mediaType, onDone ->
                        seerrLoadingState.startLoading(tmdbId)
                        viewModel.prefetchSeerrDetails(tmdbId, mediaType) {
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
                    selectedSubtitleIndex = uiState.selectedSubtitleIndex,
                    selectedAudioIndex = uiState.selectedAudioIndex,
                    isDownloading = uiState.isDownloading,
                    isDownloadingSeries = uiState.isDownloadingSeries,
                    activeDownload = activeDownload,
                    isLoading = uiState.isLoading,
                    isRefreshing = uiState.isRefreshing,
                    error = uiState.error,
                    isAccessDenied = uiState.isAccessDenied,
                    albumTracks = uiState.albumTracks,
                    collectionItems = uiState.collectionItems,
                    relatedItems = uiState.relatedItems,
                    relatedVideos = uiState.relatedVideos,
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
                    resyncState = uiState.resyncState,
                    downloadedEpisodeIds = uiState.downloadedEpisodeIds,
                )

                val onVideoClick = rememberVideoClickHandler(
                    uriHandler = uriHandler,
                    onPlayYouTube = { key -> activeTrailerKey = key },
                )

                val callbacks = remember(
                    rememberedGetImageUrl, rememberedGetBackdropUrl, rememberedGetSeerrPosterUrl,
                    viewModel, onPlayClick, onAudioClick, itemId, onItemClick, onPersonClick,
                    onNavigateToSeries, onNavigate, onEditClick, onManageSeries, onBack, onVideoClick,
                ) {
                    DetailContentCallbacks(
                        getImageUrl = rememberedGetImageUrl,
                        getBackdropUrl = rememberedGetBackdropUrl,
                        getSeerrPosterUrl = rememberedGetSeerrPosterUrl,
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
                        onAudioClick = { onAudioClick(itemId) },
                        onDownloadClick = { viewModel.startDownload() },
                        onDownloadSeriesClick = {
                            showSeriesDownloadSheet = true
                            viewModel.loadDownloadedEpisodeIds()
                            viewModel.prepareDownloadSheetEpisodes()
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
                        onNavigateToSeries = onNavigateToSeries,
                        onSeasonSelected = { seasonId: String ->
                            viewModel.loadEpisodesForSeason(seriesIdForSeasons, seasonId)
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
                        onAddToPlaylist = { viewModel.openPlaylistPicker() },
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
                        onResync = { viewModel.resync() },
                        onRedownloadMedia = { viewModel.redownloadMedia() },
                        onClearResync = { viewModel.clearResyncState() },
                        onOpenDownloadDetails = {
                            // Load the on-disk inventory (media + sidecars) before showing
                            // the sheet so sizes are fresh; it re-reads on every open.
                            viewModel.loadDownloadFileInventory()
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
                        viewModel.loadSeerrServiceDetails(item.mediaType)
                        if (item.mediaType.equals("tv", ignoreCase = true)) {
                            viewModel.loadSeerrTvSeasons(item.id)
                        }
                    }

                    SeerrRequestDialog(
                        item = item,
                        radarrServers = seerrRadarrServers,
                        sonarrServers = seerrSonarrServers,
                        seasons = if (item.mediaType.equals("tv", ignoreCase = true)) seerrTvSeasons else emptyList(),
                        isLoadingServices = seerrIsLoadingServices,
                        isRequesting = seerrRequestResult?.isLoading == true,
                        requestSuccess = seerrRequestResult?.success,
                        requestError = seerrRequestResult?.error,
                        onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                            viewModel.requestSeerrMedia(item, seasons, serverId, profileId, rootFolder, tags)
                        },
                        onDismiss = {
                            seerrRequestItem = null
                            viewModel.clearSeerrRequestResult()
                        },
                    )
                }
            } // CompositionLocalProvider
        }

        if (uiState.showPlaylistPicker && detail != null) {
            val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { viewModel.dismissPlaylistPicker() },
                sheetState = playlistSheetState,
            ) {
                AddToPlaylistSheet(
                    playlists = uiState.playlists,
                    isLoading = uiState.isLoadingPlaylists,
                    isAdding = uiState.isAddingToPlaylist,
                    onWatchLater = { viewModel.addToWatchLater() },
                    onPick = { playlist -> viewModel.addToPlaylist(playlist) },
                    onCreateNew = { viewModel.openCreatePlaylistDialog() },
                    onDismiss = { viewModel.dismissPlaylistPicker() },
                )
            }
        }

        if (uiState.showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                isLoading = uiState.isAddingToPlaylist,
                onConfirm = { name, overview ->
                    viewModel.createAndAddPlaylist(name, overview)
                },
                onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            )
        }

        val detailItem = detail?.item
        if (showSeriesDownloadSheet && detailItem?.mediaType == MediaType.SERIES) {
            val downloadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = {
                    showSeriesDownloadSheet = false
                    viewModel.resetDownloadSheetState()
                },
                sheetState = downloadSheetState,
            ) {
                SeriesDownloadSheet(
                    seasons = uiState.seasons,
                    episodes = uiState.downloadSheetEpisodes,
                    loadingSeasons = uiState.downloadSheetLoadingSeasons,
                    downloadedEpisodeIds = uiState.downloadedEpisodeIds,
                    onLoadEpisodes = { seasonId ->
                        viewModel.loadDownloadSheetEpisodes(seasonId)
                    },
                    isDownloading = uiState.isDownloadingSeries,
                    onDownload = { selectedEpisodes ->
                        showSeriesDownloadSheet = false
                        val nonEmpty = selectedEpisodes.filter { it.value.isNotEmpty() }
                        viewModel.downloadSeries(nonEmpty)
                        viewModel.resetDownloadSheetState()
                    },
                    onDismiss = {
                        showSeriesDownloadSheet = false
                        viewModel.resetDownloadSheetState()
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
            val downloadedEpisodesBySeason = uiState.episodes
                .filterValues { it.isNotEmpty() }
            val downloadableSeasons = uiState.seasons.filter { it.id in downloadedEpisodesBySeason }
            val totalSizeBytes = uiState.detailContext?.seriesAggregate?.totalSizeBytes ?: 0L
            val downloadedEpisodeCount = uiState.detailContext?.seriesAggregate?.downloadedEpisodeCount
                ?: downloadedEpisodesBySeason.values.sumOf { it.size }
            val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TvSafeSheet(
                onDismissRequest = { showDeleteEpisodesSheet = false },
                sheetState = deleteSheetState,
            ) {
                DeleteDownloadedEpisodesSheet(
                    seasons = downloadableSeasons,
                    episodes = downloadedEpisodesBySeason,
                    totalSizeBytes = totalSizeBytes,
                    onDelete = { episodeIds ->
                        showDeleteEpisodesSheet = false
                        viewModel.deleteOfflineEpisodes(episodeIds.toList())
                        // If every downloaded episode was selected there's nothing
                        // left to show — pop back to where the user came from.
                        if (episodeIds.size >= downloadedEpisodeCount) {
                            onBack()
                        }
                    },
                    onDeleteEntireSeries = {
                        showDeleteEpisodesSheet = false
                        viewModel.deleteOfflineSeries(itemId)
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
                if (markSeriesToWatched) R.string.detail_mark_series_watched_confirm_title
                else R.string.detail_mark_series_unwatched_confirm_title,
            )
            val message = stringResource(
                if (markSeriesToWatched) R.string.detail_mark_series_watched_confirm_message
                else R.string.detail_mark_series_unwatched_confirm_message,
            )
            markSeriesConfirm.ConfirmDialog(
                title = title,
                message = message,
                confirmText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_confirm),
                dismissText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_cancel),
            )
        }

        // Season mark-played confirm.
        if (markSeasonConfirm.isVisible) {
            val title = stringResource(
                if (markSeasonToWatched) R.string.detail_mark_season_watched_confirm_title
                else R.string.detail_mark_season_unwatched_confirm_title,
            )
            val message = stringResource(
                if (markSeasonToWatched) R.string.detail_mark_season_watched_confirm_message
                else R.string.detail_mark_season_unwatched_confirm_message,
            )
            markSeasonConfirm.ConfirmDialog(
                title = title,
                message = message,
                confirmText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_confirm),
                dismissText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_cancel),
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
                    InlineTrailerPlayer(
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
                title = stringResource(R.string.detail_delete_download_title),
                message = stringResource(
                    R.string.detail_delete_download_message,
                    target.name,
                    target.sizeBytes.formatBytes(),
                ),
                confirmText = stringResource(R.string.detail_delete),
                dismissText = stringResource(R.string.detail_cancel),
                icon = Tabler.Outline.Trash,
                tone = ConfirmTone.DESTRUCTIVE,
                onConfirm = {
                    viewModel.deleteOfflineItem(target.itemId)
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
                title = stringResource(R.string.detail_delete_episode_title),
                message = stringResource(R.string.detail_delete_episode_message, ep.name),
                confirmText = stringResource(R.string.detail_delete),
                dismissText = stringResource(R.string.detail_cancel),
                icon = Tabler.Outline.Trash,
                tone = ConfirmTone.DESTRUCTIVE,
                onConfirm = { viewModel.deleteOfflineEpisode(ep.episodeId) },
                onDismiss = { pendingDeleteEpisode = null },
            )
        }

        // ── Resync bottom sheet. Lists what changed and offers a
        // resync / re-download action with live status. ──
        if (showResyncSheet) {
            ResyncSheet(
                syncState = uiState.detailContext?.syncState,
                resyncState = uiState.resyncState,
                onResync = { viewModel.resync() },
                onRedownloadMedia = { viewModel.redownloadMedia() },
                onDismiss = {
                    showResyncSheet = false
                    viewModel.clearResyncState()
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
                inventory = uiState.downloadFileInventory,
                isLoadingInventory = uiState.isLoadingDownloadFiles,
                backdropUrl = sheetBackdropUrl,
                posterUrl = sheetPosterUrl,
                onDismiss = {
                    showDownloadDetailsSheet = false
                    viewModel.clearDownloadFileInventory()
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
