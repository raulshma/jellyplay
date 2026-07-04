package com.raulshma.jellyplay.feature.details

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaDetailScreen(
    itemId: String,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long, subtitleStreamIndex: Int?, audioStreamIndex: Int?) -> Unit,
    onAudioClick: (itemId: String) -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onPersonClick: (personId: String) -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
    onEditClick: (itemId: String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail
    val isLoading = uiState.isLoading
    val error = uiState.error
    val seasons = uiState.seasons
    val episodes = uiState.episodes
    val fetchedSeasonIds = uiState.fetchedSeasonIds
    val smartPlayTarget = uiState.smartPlayTarget
    val selectedSubtitleIndex = uiState.selectedSubtitleIndex
    val selectedAudioIndex = uiState.selectedAudioIndex
    val albumTracks = uiState.albumTracks
    val collectionItems = uiState.collectionItems
    val isDownloading = uiState.isDownloading
    val isDownloadingSeries = uiState.isDownloadingSeries
    val downloadSheetEpisodes = uiState.downloadSheetEpisodes
    val downloadSheetLoadingSeasons = uiState.downloadSheetLoadingSeasons
    val downloadedEpisodeIds = uiState.downloadedEpisodeIds
    val seriesDownloadResult = uiState.seriesDownloadResult
    val downloadError = uiState.downloadError
    val userMessage = uiState.userMessage
    val cellularDownloadWarningMb = uiState.cellularDownloadWarningMb
    val seerrTvSeasons = uiState.seerrTvSeasons
    LaunchedEffect(detail) {
        detail?.let {
            viewModel.loadSeerrDataIfNeeded(it)
        }
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
    var activeTrailerKey by remember { mutableStateOf<String?>(null) }
    // Dismiss any open trailer dialog when navigating to a different item.
    LaunchedEffect(itemId) { activeTrailerKey = null }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarContext = LocalContext.current

    LaunchedEffect(seriesDownloadResult) {
        seriesDownloadResult?.let { result ->
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

    LaunchedEffect(downloadError) {
        downloadError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearDownloadError()
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearUserMessage()
        }
    }

    if (cellularDownloadWarningMb != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDownloadWarning() },
            title = { Text(stringResource(R.string.detail_cellular_download_title)) },
            text = {
                Text(
                    stringResource(R.string.detail_cellular_download_message, cellularDownloadWarningMb),
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
        val seerrRecommendations = uiState.seerrRecommendations
        val seerrSimilar = uiState.seerrSimilar
        val isSeerrConnected = uiState.isSeerrConnected
        val isSeerrRecommendationsEnabled = uiState.isSeerrRecommendationsEnabled
        val relatedVideos = uiState.relatedVideos
        val effectiveIsSeerrConnected = isSeerrConnected
        val seerrRequestResult = uiState.seerrRequestResult
        val seerrRadarrServers = uiState.seerrRadarrServers
        val seerrSonarrServers = uiState.seerrSonarrServers
        val seerrIsLoadingServices = uiState.isLoadingSeerrServices
        var seerrRequestItem by remember { mutableStateOf<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem?>(null) }

        // Seerr card loading state for prefetch animation
        val seerrLoadingState = rememberSeerrCardLoadingState()
        val seerrPrefetchCallback: com.raulshma.jellyplay.core.ui.components.SeerrPrefetchCallback =
            remember(seerrLoadingState, viewModel) {
                { tmdbId, mediaType, onDone ->
                    seerrLoadingState.startLoading(tmdbId)
                    viewModel.prefetchSeerrDetails(tmdbId, mediaType) {
                        seerrLoadingState.stopLoading(tmdbId)
                        onDone()
                    }
                }
            }

        androidx.compose.runtime.CompositionLocalProvider(
            com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch provides seerrPrefetchCallback,
            com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState provides seerrLoadingState,
        ) {
        val rememberedGetImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } }
        val rememberedGetBackdropUrl = remember(viewModel) { { id: String -> viewModel.getBackdropUrl(id) } }
        val rememberedGetSeerrPosterUrl = remember(viewModel) { { path: String? -> viewModel.getSeerrPosterUrl(path) } }

        DetailContent(
            itemId = itemId,
            detail = detail,
            seasons = seasons,
            episodes = episodes,
            fetchedSeasonIds = fetchedSeasonIds,
            smartPlayTarget = smartPlayTarget,
            selectedSubtitleIndex = selectedSubtitleIndex,
            selectedAudioIndex = selectedAudioIndex,
            getImageUrl = rememberedGetImageUrl,
            getBackdropUrl = rememberedGetBackdropUrl,
            isDownloading = isDownloading,
            isDownloadingSeries = isDownloadingSeries,
            activeDownload = activeDownload,
            isLoading = isLoading,
            error = error,
            onRetry = remember(viewModel, itemId) { { viewModel.loadItem(itemId) } },
            onPlayClick = remember(viewModel, onPlayClick) {
                { playItemId: String, sourceId: String?, start: Long ->
                    onPlayClick(
                        playItemId,
                        sourceId,
                        start,
                        viewModel.selectedSubtitleIndex,
                        viewModel.selectedAudioIndex,
                    )
                }
            },
            onAudioClick = remember(onAudioClick, itemId) { { onAudioClick(itemId) } },
            onDownloadClick = remember(viewModel) { { viewModel.startDownload() } },
            onDownloadSeriesClick = remember(viewModel) {
                {
                    showSeriesDownloadSheet = true
                    viewModel.loadDownloadedEpisodeIds()
                }
            },
            onToggleFavorite = remember(viewModel) { { viewModel.toggleFavorite() } },
            onMarkPlayed = remember(viewModel) { { viewModel.markPlayed() } },
            onMarkUnplayed = remember(viewModel) { { viewModel.markUnplayed() } },
            onSubtitleSelect = remember(viewModel) { { idx: Int? -> viewModel.selectSubtitle(idx) } },
            onAudioSelect = remember(viewModel) { { idx: Int? -> viewModel.selectAudio(idx) } },
            onItemClick = remember { onItemClick },
            onPersonClick = remember { onPersonClick },
            onNavigateToSeries = onNavigateToSeries,
            onSeasonSelected = remember(viewModel, detail, itemId) {
                { seasonId: String ->
                    val seriesId = detail?.item?.seriesId ?: itemId
                    viewModel.loadEpisodesForSeason(seriesId, seasonId)
                }
            },
            onLoadSeerrData = remember(viewModel, detail) {
                { detail?.let { viewModel.loadSeerrDataIfNeeded(it) } }
            },
            onBack = onBack,
            seerrRecommendations = seerrRecommendations,
            seerrSimilar = seerrSimilar,
            isSeerrConnected = effectiveIsSeerrConnected,
            isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
            getSeerrPosterUrl = rememberedGetSeerrPosterUrl,
            onSeerrRequest = remember { { item: SeerrSearchItem -> seerrRequestItem = item } },
            onNavigate = onNavigate,
            onEditClick = remember(onEditClick, itemId) { { onEditClick(itemId) } },
            albumTracks = albumTracks,
            collectionItems = collectionItems,
            onPlayAlbumTrack = remember(viewModel) { { index: Int -> viewModel.playAlbum(index) } },
            relatedVideos = relatedVideos,
            onVideoClick = { video ->
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
            preferences = preferences,
            onHideFromNextUp = remember(viewModel) { { viewModel.hideFromNextUp() } },
            onHideFromContinueWatching = remember(viewModel) { { viewModel.hideFromContinueWatching() } },
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
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = {
                showSeriesDownloadSheet = false
                viewModel.resetDownloadSheetState()
            },
        ) {
            SeriesDownloadSheet(
                seasons = seasons,
                episodes = downloadSheetEpisodes,
                loadingSeasons = downloadSheetLoadingSeasons,
                downloadedEpisodeIds = downloadedEpisodeIds,
                onLoadEpisodes = { seasonId ->
                    viewModel.loadDownloadSheetEpisodes(seasonId)
                },
                isDownloading = isDownloadingSeries,
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
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
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
                    }
                )
            }
        }
    }
    } // Box
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailContent(
    itemId: String,
    detail: MediaDetail?,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
    selectedSubtitleIndex: Int?,
    selectedAudioIndex: Int?,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    isDownloading: Boolean,
    isDownloadingSeries: Boolean = false,
    activeDownload: com.raulshma.jellyplay.core.model.DownloadItem?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDownloadSeriesClick: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
    onAudioSelect: (Int?) -> Unit,
    onItemClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeries: (String) -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit = {},
    onLoadSeerrData: () -> Unit = {},
    onBack: () -> Unit,
    seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    seerrSimilar: List<SeerrSearchItem> = emptyList(),
    isSeerrConnected: Boolean = false,
    isSeerrRecommendationsEnabled: Boolean = false,
    getSeerrPosterUrl: (String?) -> String? = { null },
    onSeerrRequest: (SeerrSearchItem) -> Unit = {},
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
    onEditClick: () -> Unit = {},
    albumTracks: List<MediaItem> = emptyList(),
    collectionItems: List<MediaItem> = emptyList(),
    onPlayAlbumTrack: (Int) -> Unit = {},
    relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    onVideoClick: (SeerrRelatedVideo) -> Unit = {},
    preferences: UserPreferences,
    onHideFromNextUp: () -> Unit = {},
    onHideFromContinueWatching: () -> Unit = {},
) {
    val item = detail?.item
    val listState = rememberLazyListState()
    val isAudio = item?.mediaType == MediaType.AUDIO || item?.mediaType == MediaType.MUSIC || item?.mediaType == MediaType.ALBUM
    val isAlbum = item?.mediaType == MediaType.ALBUM
    val isSeries = item?.mediaType == MediaType.SERIES
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showTvOptionsMenu by remember { mutableStateOf(false) }
    val artworkColors = LocalArtworkColors.current

    val trailerVideo = remember(relatedVideos) {
        relatedVideos.firstOrNull {
            it.site?.lowercase() == "youtube" &&
            (it.type?.lowercase() == "trailer" || it.type?.lowercase() == "teaser")
        } ?: relatedVideos.firstOrNull { it.site?.lowercase() == "youtube" }
    }
    var autoplayEmbedFailed by remember(itemId) { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = LocalTvMode.current
    val context = LocalContext.current

    // Single resolved options list shared by the touch DropdownMenu and the TV
    // TvSafeSheet so the two menus can never drift apart.
    val shareMedia = remember(itemId, context) {
        {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "jellyplay://media/$itemId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.detail_share_via)))
        }
    }
    val mediaOptions = rememberMediaOptions(
        item = item,
        detail = detail,
        itemId = itemId,
        isAudio = isAudio,
        isSeries = isSeries,
        seasons = seasons,
        preferences = preferences,
        activeDownload = activeDownload,
        isDownloading = isDownloading,
        isDownloadingSeries = isDownloadingSeries,
        onClose = { /* menus close themselves */ },
        onEditClick = onEditClick,
        onShare = shareMedia,
        onDownload = { showDownloadDialog = true },
        onDownloadSeries = onDownloadSeriesClick,
        onHideFromNextUp = onHideFromNextUp,
        onHideFromContinueWatching = onHideFromContinueWatching,
        onTechnicalInfo = { onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.MediaInfo(itemId)) },
    )

    val density = LocalDensity.current
    val backdropHeight = when {
        isTv -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Tv
        adaptiveInfo.isLandscape && isExpanded -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.LandscapeExpanded
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Expanded
        else -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Portrait
    }
    val baseBackdropHeight = with(density) { (backdropHeight.toPx() / 1.2f).toDp() }
    val collapsedHeight = with(density) { backdropHeight.toPx() }
    val spacerHeightPx = with(density) { (baseBackdropHeight - 150.dp).toPx() }
    val scrollOffset by remember {
        derivedStateOf {
            (if (listState.firstVisibleItemIndex > 0) spacerHeightPx else 0f) + listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val scrollFraction by remember {
        derivedStateOf {
            (scrollOffset / collapsedHeight).coerceIn(0f, 1f)
        }
    }

    val isLightTheme = rememberIsLightTheme()

    val baseOverlayColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: MaterialTheme.colorScheme.background

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current

    val targetBackgroundColor = when {
        isSynthwave -> com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SYNTHWAVE_DETAIL_BG
        isSoothing -> MaterialTheme.colorScheme.background
        isLightTheme -> MaterialTheme.colorScheme.background
        else -> lerp(baseOverlayColor, Color.Black, 0.65f)
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "backgroundColor",
    )

    val navBarColor = LocalNavigationBarColor.current
    SideEffect {
        if (navBarColor.value != backgroundColor) navBarColor.value = backgroundColor
    }

    val contentVisible = detail != null && item != null
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "contentAlpha",
    )

    val scrollCollapsed by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scrollCollapsed",
    )

    val animatedContainerColor = lerp(
        Color.Transparent,
        backgroundColor.copy(alpha = 0.95f),
        scrollCollapsed,
    )

    val animatedTitleAlpha = scrollCollapsed

    val targetBackdropId = item
        ?.takeIf { it.mediaType == MediaType.EPISODE }?.seriesId
        ?: itemId

    val contentFocusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    if (isTv) {
        LaunchedEffect(contentVisible) {
            if (contentVisible) {
                // The Play button carrying contentFocusRequester lives inside AnimatedVisibility(contentVisible),
                // so it may not be composed/attached on the very frame contentVisible flips true. Wait a frame
                // and retry briefly so the request is not silently swallowed by tryRequestFocus.
                for (attempt in 1..20) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (contentFocusRequester.tryRequestFocus("detail_content")) break
                }
            }
        }
    }

    val backgroundModifier = if (isSynthwave) {
        Modifier.background(
            com.raulshma.jellyplay.core.designsystem.theme.synthwaveBackgroundBrush()
        )
    } else {
        Modifier.drawBehind { drawRect(backgroundColor) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .onDpadKeyEvent(
                onBack = { e ->
                    if (e.isKeyUp) { onBack() }
                    true
                },
            ),
    ) {
        // While the detail is still loading (contentVisible == false) show a loading surface.
        // On TV it must be focusable (LoadingScreen grabs focus) so the D-pad isn't orphaned
        // until data arrives; on touch we use a delayed spinner so fast loads don't flicker.
        if (!contentVisible && error == null) {
            if (isTv) {
                LoadingScreen(modifier = Modifier.fillMaxSize())
            } else {
                DelayedLoadingScreen(modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(backdropHeight)
                .graphicsLayer {
                    translationY = -scrollOffset * 0.5f
                    alpha = 1f - (scrollFraction * 0.8f)
                }
        ) {
            AnimatedContent(
                targetState = targetBackdropId,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(400),
                    ) + scaleIn(
                        initialScale = 1.035f,
                        animationSpec = tween(400),
                    ) togetherWith fadeOut(
                        animationSpec = tween(300),
                    ) + scaleOut(
                        targetScale = 0.99f,
                        animationSpec = tween(300),
                    )
                },
                label = "detailBackdrop",
            ) { backdropId ->
                val backdropModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f + (scrollOffset * 0.001f).coerceAtLeast(0f)
                        scaleX = scale
                        scaleY = scale
                    }
                MediaImage(
                    url = getBackdropUrl(backdropId),
                    contentDescription = null,
                    blurHash = item?.blurHashes?.backdrop,
                    modifier = backdropModifier,
                    contentScale = ContentScale.Crop,
                )
            }

            // Trailer player rendered outside AnimatedContent so it composes
            // independently when relatedVideos loads asynchronously.
            val playAutoplayTrailer = preferences.trailerAutoplay && trailerVideo != null && !autoplayEmbedFailed
            val trailerKey = trailerVideo?.key
            if (playAutoplayTrailer && trailerKey != null) {
                InlineTrailerPlayer(
                    videoKey = trailerKey,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f + (scrollOffset * 0.001f).coerceAtLeast(0f)
                            scaleX = scale
                            scaleY = scale
                        },
                    muted = true,
                    showControls = false,
                    autoplay = true,
                    focusable = false,
                    cropToFill = true,
                    onEmbedFailed = { autoplayEmbedFailed = true },
                )
            }

            val isLandscapeExpanded = isExpanded && adaptiveInfo.isLandscape
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    if (isLandscapeExpanded) backgroundColor.copy(alpha = 0.5f) else Color.Transparent,
                                    backgroundColor.copy(alpha = if (isLandscapeExpanded) 0.8f else 0.4f),
                                    backgroundColor.copy(alpha = 0.9f),
                                    backgroundColor,
                                ),
                                startY = if (isLandscapeExpanded) 0f else (baseBackdropHeight - 200.dp).toPx(),
                                endY = backdropHeight.toPx()
                            )
                        )
                    }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
        ) {
            item { Spacer(modifier = Modifier.height(baseBackdropHeight - 150.dp)) }

            item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    backgroundColor.copy(alpha = 0.9f),
                                    backgroundColor,
                                ),
                                startY = 0f,
                                endY = 150.dp.toPx()
                            )
                        )
                    }
            ) {
                if (isExpanded && adaptiveInfo.isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = adaptiveInfo.contentPadding(isTv))
                            .offset(y = (-80).dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left column: poster + action buttons
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(220.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FadingItem {
                                    MediaImage(
                                        url = getImageUrl(itemId),
                                        contentDescription = null,
                                        blurHash = item?.blurHashes?.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(ShapeCache.smooth12)
                                            .graphicsLayer { alpha = contentAlpha }
                                        .then(
                                            run {
                                                val sharedTransitionScope = LocalSharedTransitionScope.current
                                                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                                                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                                    with(sharedTransitionScope) {
                                                        Modifier.sharedElement(
                                                            rememberSharedContentState(key = "poster_$itemId"),
                                                            animatedVisibilityScope = animatedVisibilityScope,
                                                        )
                                                    }
                                                } else Modifier
                                            }
                                        ),
                                    contentScale = ContentScale.Crop,
                                )
                                }
                                if (detail != null && item != null) {
                                    DetailActionButtons(
                                        item = item,
                                        detail = detail,
                                        seasons = seasons,
                                        episodes = episodes,
                                        fetchedSeasonIds = fetchedSeasonIds,
                                        smartPlayTarget = smartPlayTarget,
                                        isAudio = isAudio,
                                        isAlbum = isAlbum,
                                        albumTracks = albumTracks,
                                        onPlayClick = onPlayClick,
                                        onAudioClick = onAudioClick,
                                        onPlayAlbumTrack = onPlayAlbumTrack,
                                        onNavigate = onNavigate,
                                        onToggleFavorite = onToggleFavorite,
                                        onMarkPlayed = onMarkPlayed,
                                        onMarkUnplayed = onMarkUnplayed,
                                        vertical = true,
                                        contentFocusRequester = contentFocusRequester,
                                    )
                                }
                            }
                        }

                        if (detail != null && item != null) {
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = EnterTransition.None,
                                exit = ExitTransition.None,
                                modifier = Modifier.weight(1f)
                            ) {
                                DetailContentBody(
                                    item = item,
                                    detail = detail,
                                    seasons = seasons,
                                    episodes = episodes,
                                    fetchedSeasonIds = fetchedSeasonIds,
                                    smartPlayTarget = smartPlayTarget,
                                    selectedSubtitleIndex = selectedSubtitleIndex,
                                    selectedAudioIndex = selectedAudioIndex,
                                    getImageUrl = getImageUrl,
                                    isAudio = isAudio,
                                    isAlbum = isAlbum,
                                    albumTracks = albumTracks,
                                    collectionItems = collectionItems,
                                    onPlayClick = onPlayClick,
                                    onAudioClick = onAudioClick,
                                    onToggleFavorite = onToggleFavorite,
                                    onMarkPlayed = onMarkPlayed,
                                    onMarkUnplayed = onMarkUnplayed,
                                    onSubtitleSelect = onSubtitleSelect,
                                    onAudioSelect = onAudioSelect,
                                    onItemClick = onItemClick,
                                    onPersonClick = onPersonClick,
                                    onNavigateToSeries = onNavigateToSeries,
                                    onSeasonSelected = onSeasonSelected,
                                    onLoadSeerrData = onLoadSeerrData,
                                    contentFocusRequester = contentFocusRequester,
                                    seerrRecommendations = seerrRecommendations,
                                    seerrSimilar = seerrSimilar,
                                    isSeerrConnected = isSeerrConnected,
                                    isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
                                    getSeerrPosterUrl = getSeerrPosterUrl,
                                    onSeerrRequest = onSeerrRequest,
                                    onNavigate = onNavigate,
                                    onPlayAlbumTrack = onPlayAlbumTrack,
                                    showActionButtons = false,
                                    relatedVideos = relatedVideos,
                                    onVideoClick = onVideoClick,
                                    preferences = preferences,
                                )
                            }
                        } else if (error != null) {
                            ErrorScreen(
                                message = error,
                                onRetry = onRetry,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(top = 0.dp)
                    ) {
                        val posterWidth = when {
                            isTv -> 160.dp
                            isExpanded -> 140.dp
                            else -> 120.dp
                        }
                        val posterHeight = posterWidth * 1.2f
                        val overlap = 40.dp
                        val boxHeight = posterHeight - overlap

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(boxHeight)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = adaptiveInfo.contentPadding(isTv))
                                    .offset(y = -overlap),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                AnimatedVisibility(
                                    visible = contentVisible,
                                    enter = EnterTransition.None,
                                    exit = ExitTransition.None,
                                ) {
                                    FadingItem(
                                        modifier = Modifier
                                            .width(posterWidth)
                                            .requiredHeight(posterHeight)
                                    ) {
                                        MediaImage(
                                            url = getImageUrl(itemId),
                                            contentDescription = null,
                                            blurHash = item?.blurHashes?.primary,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(ShapeCache.smooth8)
                                                .graphicsLayer { alpha = contentAlpha },
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (detail != null && item != null) {
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = EnterTransition.None,
                                exit = ExitTransition.None,
                            ) {
                                DetailContentBody(
                                    item = item,
                                    detail = detail,
                                    seasons = seasons,
                                    episodes = episodes,
                                    fetchedSeasonIds = fetchedSeasonIds,
                                    smartPlayTarget = smartPlayTarget,
                                    selectedSubtitleIndex = selectedSubtitleIndex,
                                    selectedAudioIndex = selectedAudioIndex,
                                    getImageUrl = getImageUrl,
                                    isAudio = isAudio,
                                    isAlbum = isAlbum,
                                    albumTracks = albumTracks,
                                    collectionItems = collectionItems,
                                    onPlayClick = onPlayClick,
                                    onAudioClick = onAudioClick,
                                    onToggleFavorite = onToggleFavorite,
                                    onMarkPlayed = onMarkPlayed,
                                    onMarkUnplayed = onMarkUnplayed,
                                    onSubtitleSelect = onSubtitleSelect,
                                    onAudioSelect = onAudioSelect,
                                    onItemClick = onItemClick,
                                    onPersonClick = onPersonClick,
                                    onNavigateToSeries = onNavigateToSeries,
                                    onSeasonSelected = onSeasonSelected,
                                    onLoadSeerrData = onLoadSeerrData,
                                    contentFocusRequester = contentFocusRequester,
                                    seerrRecommendations = seerrRecommendations,
                                    seerrSimilar = seerrSimilar,
                                    isSeerrConnected = isSeerrConnected,
                                    isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
                                    getSeerrPosterUrl = getSeerrPosterUrl,
                                    onSeerrRequest = onSeerrRequest,
                                    onNavigate = onNavigate,
                                    onPlayAlbumTrack = onPlayAlbumTrack,
                                    relatedVideos = relatedVideos,
                                    onVideoClick = onVideoClick,
                                    preferences = preferences,
                                )
                            }
                        } else if (error != null) {
                            ErrorScreen(
                                message = error,
                                onRetry = onRetry,
                            )
                        }
                    }
                }
            }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(animatedContainerColor) }
        ) {
            MediumTopAppBar(
                title = {
                    Text(
                        text = item?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = animatedTitleAlpha),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    CircleBgBackButton(
                        onClick = onBack,
                        scrollCollapsed = scrollCollapsed,
                    )
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    val editIconColor = if (scrollCollapsed < 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
                    Box {
                        IconButton(
                            onClick = {
                                if (isTv) {
                                    showTvOptionsMenu = true
                                } else {
                                    menuExpanded = true
                                }
                            },
                                modifier = Modifier
                                    .focusIndicator(CircleShape)
                                    .focusProperties { down = contentFocusRequester }
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (scrollCollapsed < 0.5f) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f) else Color.Transparent
                                    )
                            ) {
                                Icon(
                                    Tabler.Outline.DotsVertical,
                                    contentDescription = stringResource(R.string.detail_cd_options),
                                    tint = editIconColor,
                                )
                            }
                            if (!isTv) {
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                mediaOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { menuExpanded = false; option.onClick() },
                                        enabled = option.enabled,
                                        leadingIcon = {
                                            Icon(option.icon, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
                scrollBehavior = scrollBehavior,
            )
        }
    }

    if (showDownloadDialog) {
        val source = detail?.mediaSources?.firstOrNull()
        val fileSize = source?.size
        val context = LocalContext.current
        val availableBytes by produceState(initialValue = 0L) {
            value = withContext(Dispatchers.IO) {
                val downloadDir = context.getExternalFilesDir(if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
                    ?: context.filesDir
                val stat = StatFs(downloadDir.absolutePath)
                stat.availableBlocksLong * stat.blockSizeLong
            }
        }
        val fileSizeText = fileSize?.let { size ->
            when {
                size >= 1_000_000_000 -> stringResource(R.string.detail_size_gb, size / 1_000_000_000.0)
                size >= 1_000_000 -> stringResource(R.string.detail_size_mb, size / 1_000_000.0)
                size >= 1_000 -> stringResource(R.string.detail_size_kb, size / 1_000.0)
                else -> stringResource(R.string.detail_size_b, size)
            }
        } ?: stringResource(R.string.detail_size_unknown)
        val availableText = when {
            availableBytes >= 1_000_000_000 -> stringResource(R.string.detail_size_gb, availableBytes / 1_000_000_000.0)
            availableBytes >= 1_000_000 -> stringResource(R.string.detail_size_mb, availableBytes / 1_000_000.0)
            else -> stringResource(R.string.detail_size_kb, availableBytes / 1_000_000.0)
        }
        val enoughSpace = fileSize == null || fileSize <= availableBytes

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.detail_download_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.detail_estimated_size, fileSizeText))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.detail_available_storage, availableText))
                    if (!enoughSpace) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.detail_not_enough_storage),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadDialog = false
                        onDownloadClick()
                    },
                    enabled = enoughSpace,
                ) {
                    Text(stringResource(R.string.detail_download_dialog_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }

    if (showTvOptionsMenu) {
        TvSafeSheet(
            onDismissRequest = { showTvOptionsMenu = false },
            title = stringResource(R.string.detail_cd_options),
        ) {
            Column(modifier = Modifier.verticalWrapAround()) {
                mediaOptions.forEach { option ->
                    TvOptionItem(
                        icon = option.icon,
                        label = option.label,
                        enabled = option.enabled,
                        onClick = {
                            showTvOptionsMenu = false
                            option.onClick()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberIsLightTheme(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    return remember(bg) {
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f
    }
}

@Composable
private fun TvOptionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeCache.smooth12,
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                       else MaterialTheme.colorScheme.onSurface,
        interactionSource = interactionSource,
        modifier = Modifier.focusIndicator().fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}


