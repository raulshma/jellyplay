package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.rememberIsLightTheme
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer
import com.raulshma.jellyplay.core.ui.components.SeerrPrefetchCallback
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch
import com.raulshma.jellyplay.core.ui.navigation.Route

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
    LaunchedEffect(detail?.item?.id) {
        detail?.let { viewModel.loadSeerrDataIfNeeded(it) }
    }
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val currentItem = detail?.item
    val targetBackdropId = currentItem
        ?.takeIf { it.mediaType == MediaType.EPISODE }?.seriesId
        ?: currentItem?.id
        ?: itemId
    // Memoized so the URL isn't rebuilt on every recomposition (e.g. on each
    // scroll-derived state change funnelling through ArtworkThemeWrapper).
    val backdropUrl = remember(targetBackdropId) { viewModel.getBackdropUrl(targetBackdropId) }

    val outerIsLightTheme = rememberIsLightTheme()

    var showSeriesDownloadSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarContext = LocalContext.current

    LaunchedEffect(uiState.seriesDownloadResult) {
        uiState.seriesDownloadResult?.let { result ->
            val message = if (result.error != null) {
                result.error
            } else if (result.queuedCount > 0) {
                snackbarContext.resources.getQuantityString(R.plurals.detail_episodes_queued, result.queuedCount, result.queuedCount)
            } else {
                snackbarContext.getString(R.string.detail_msg_no_episodes_queued)
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearSeriesDownloadResult()
        }
    }

    LaunchedEffect(uiState.downloadError) {
        uiState.downloadError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearDownloadError()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearUserMessage()
        }
    }

    val cellularWarningMb = uiState.cellularDownloadWarningMb
    if (cellularWarningMb != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDownloadWarning() },
            title = { Text(stringResource(R.string.detail_cellular_download_title)) },
            text = {
                Text(
                    stringResource(R.string.detail_cellular_download_message, cellularWarningMb),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCellularDownload() }) {
                    Text(stringResource(R.string.detail_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCellularDownloadWarning() }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArtworkThemeWrapper(
            imageUrl = backdropUrl,
            dynamicTheming = preferences.dynamicTheming,
            darkTheme = !outerIsLightTheme,
            oledMode = preferences.oledMode,
            colorStyle = preferences.colorStyle,
            accentColorSwatch = preferences.accentColorSwatch,
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

                // Series id used by season-episode fetches. Depends only on the
                // item id (stable across favorite/played toggles) so the on-season
                // callback captures a value that doesn't invalidate on unrelated
                // detail mutations.
                val seriesIdForSeasons = remember(itemId) {
                    detail?.item?.seriesId ?: itemId
                }

                val state = remember(
                    itemId, detail, uiState.seasons, uiState.episodes, uiState.fetchedSeasonIds,
                    uiState.smartPlayTarget, uiState.selectedSubtitleIndex, uiState.selectedAudioIndex,
                    uiState.isDownloading, uiState.isDownloadingSeries, activeDownload, uiState.isLoading,
                    uiState.error, uiState.albumTracks, uiState.collectionItems, uiState.relatedItems,
                    uiState.relatedVideos, uiState.seerrRecommendations, uiState.seerrSimilar,
                    uiState.isSeerrConnected, uiState.isSeerrRecommendationsEnabled,
                    preferences, canManageSeries,
                ) {
                    DetailContentState(
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
                        error = uiState.error,
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
                    )
                }

                val callbacks = remember(
                    rememberedGetImageUrl, rememberedGetBackdropUrl, rememberedGetSeerrPosterUrl,
                    viewModel, onPlayClick, onAudioClick, itemId, onItemClick, onPersonClick,
                    onNavigateToSeries, onNavigate, onEditClick, onManageSeries, onBack, uriHandler,
                ) {
                    DetailContentCallbacks(
                        getImageUrl = rememberedGetImageUrl,
                        getBackdropUrl = rememberedGetBackdropUrl,
                        getSeerrPosterUrl = rememberedGetSeerrPosterUrl,
                        onRetry = { viewModel.loadItem(itemId) },
                        onPlayClick = { playItemId: String, sourceId: String?, start: Long ->
                            onPlayClick(
                                playItemId,
                                sourceId,
                                start,
                                viewModel.selectedSubtitleIndex,
                                viewModel.selectedAudioIndex,
                            )
                        },
                        onAudioClick = { onAudioClick(itemId) },
                        onDownloadClick = { viewModel.startDownload() },
                        onDownloadSeriesClick = {
                            showSeriesDownloadSheet = true
                            viewModel.loadDownloadedEpisodeIds()
                        },
                        onToggleFavorite = { viewModel.toggleFavorite() },
                        onMarkPlayed = { viewModel.markPlayed() },
                        onMarkUnplayed = { viewModel.markUnplayed() },
                        onSubtitleSelect = { idx: Int? -> viewModel.selectSubtitle(idx) },
                        onAudioSelect = { idx: Int? -> viewModel.selectAudio(idx) },
                        onItemClick = onItemClick,
                        onPersonClick = onPersonClick,
                        onNavigateToSeries = onNavigateToSeries,
                        onSeasonSelected = { seasonId: String ->
                            viewModel.loadEpisodesForSeason(seriesIdForSeasons, seasonId)
                        },
                        onLoadSeerrData = { detail?.let { viewModel.loadSeerrDataIfNeeded(it) } },
                        onBack = onBack,
                        onSeerrRequest = { item: SeerrSearchItem -> seerrRequestItem = item },
                        onNavigate = onNavigate,
                        onEditClick = { onEditClick(itemId) },
                        onPlayAlbumTrack = { index: Int -> viewModel.playAlbum(index) },
                        onVideoClick = { video: SeerrRelatedVideo ->
                            if (video.site?.lowercase() == "youtube" && video.key != null) {
                                activeTrailerKey = video.key
                            } else if (video.key != null) {
                                val url = when (video.site?.lowercase()) {
                                    "youtube" -> "https://www.youtube.com/watch?v=${video.key}"
                                    else -> null
                                }
                                url?.let { uriHandler.openUri(it) }
                            }
                        },
                        onHideFromNextUp = { viewModel.hideFromNextUp() },
                        onHideFromContinueWatching = { viewModel.hideFromContinueWatching() },
                        onManageSeries = { onManageSeries(itemId) },
                    )
                }

                val availableStorageProvider = remember(viewModel) {
                    val provider: suspend (Boolean) -> Long = { isAudio: Boolean -> viewModel.getAvailableStorageBytes(isAudio) }
                    provider
                }

                DetailContent(
                    state = state,
                    callbacks = callbacks,
                    availableStorageProvider = availableStorageProvider,
                )

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

        val detailItem = detail?.item
        if (showSeriesDownloadSheet && detailItem?.mediaType == MediaType.SERIES) {
            TvSafeSheet(
                onDismissRequest = {
                    showSeriesDownloadSheet = false
                    viewModel.resetDownloadSheetState()
                },
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 80.dp),
        ) { data ->
            Snackbar(snackbarData = data)
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
    } // Box
}
