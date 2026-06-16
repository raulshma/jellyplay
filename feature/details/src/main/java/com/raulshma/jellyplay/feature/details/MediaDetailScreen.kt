package com.raulshma.jellyplay.feature.details

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.FastInvokeEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor


import androidx.compose.material3.MediumTopAppBar
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.isLanguageMatch
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.rememberInitialFocus
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
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

    val detail by viewModel.detail
    LaunchedEffect(detail) {
        detail?.let {
            viewModel.loadSeerrDataIfNeeded(it)
        }
    }
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val currentItem = detail?.item
    val targetBackdropId = if (currentItem?.mediaType == MediaType.EPISODE && currentItem.seriesId != null) {
        currentItem.seriesId!!
    } else {
        currentItem?.id ?: itemId
    }
    val backdropUrl = viewModel.getBackdropUrl(targetBackdropId)

    val outerIsLightTheme = rememberIsLightTheme()

    var showSeriesDownloadSheet by remember { mutableStateOf(false) }
    var activeTrailerKey by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.seriesDownloadResult) {
        viewModel.seriesDownloadResult?.let { result ->
            val message = if (result.error != null) {
                result.error
            } else if (result.queuedCount > 0) {
                "${result.queuedCount} episode${if (result.queuedCount != 1) "s" else ""} queued for download"
            } else {
                "No episodes could be queued"
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearSeriesDownloadResult()
        }
    }

    LaunchedEffect(viewModel.downloadError) {
        viewModel.downloadError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearDownloadError()
        }
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

        // Seerr integration state
        val seerrRecommendations by viewModel.seerrRecommendations.collectAsStateWithLifecycle()
        val seerrSimilar by viewModel.seerrSimilar.collectAsStateWithLifecycle()
        val isSeerrConnected by viewModel.isSeerrConnected.collectAsStateWithLifecycle()
        val isSeerrRecommendationsEnabled by viewModel.isSeerrRecommendationsEnabled.collectAsStateWithLifecycle()
        val relatedVideos by viewModel.relatedVideos.collectAsStateWithLifecycle()
        val effectiveIsSeerrConnected = isSeerrConnected
        val seerrRequestResult by viewModel.seerrRequestResult.collectAsStateWithLifecycle()
        val seerrRadarrServers by viewModel.radarrServers.collectAsStateWithLifecycle()
        val seerrSonarrServers by viewModel.sonarrServers.collectAsStateWithLifecycle()
        val seerrIsLoadingServices by viewModel.isLoadingSeerrServices.collectAsStateWithLifecycle()
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
            seasons = viewModel.seasons,
            episodes = viewModel.episodes,
            fetchedSeasonIds = viewModel.fetchedSeasonIds,
            smartPlayTarget = viewModel.smartPlayTarget,
            selectedSubtitleIndex = viewModel.selectedSubtitleIndex,
            selectedAudioIndex = viewModel.selectedAudioIndex,
            getImageUrl = rememberedGetImageUrl,
            getBackdropUrl = rememberedGetBackdropUrl,
            isDownloading = viewModel.isDownloading,
            isDownloadingSeries = viewModel.isDownloadingSeries,
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
            onLoadSeerrData = remember(viewModel) {
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
            albumTracks = viewModel.albumTracks,
            collectionItems = viewModel.collectionItems,
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

            val seerrTvSeasons by viewModel.seerrTvSeasons.collectAsStateWithLifecycle()

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
                seasons = viewModel.seasons,
                episodes = viewModel.downloadSheetEpisodes,
                loadingSeasons = viewModel.downloadSheetLoadingSeasons,
                downloadedEpisodeIds = viewModel.downloadedEpisodeIds,
                onLoadEpisodes = { seasonId ->
                    viewModel.loadDownloadSheetEpisodes(seasonId)
                },
                isDownloading = viewModel.isDownloadingSeries,
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
    smartPlayTarget: DetailViewModel.SmartPlayTarget?,
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
) {
    val item = detail?.item
    val listState = rememberLazyListState()
    val isAudio = item?.mediaType == MediaType.AUDIO || item?.mediaType == MediaType.MUSIC || item?.mediaType == MediaType.ALBUM
    val isAlbum = item?.mediaType == MediaType.ALBUM
    val isSeries = item?.mediaType == MediaType.SERIES
    var showDownloadDialog by remember { mutableStateOf(false) }
    val artworkColors = LocalArtworkColors.current

    val trailerVideo = remember(relatedVideos) {
        relatedVideos.firstOrNull {
            it.site?.lowercase() == "youtube" &&
            (it.type?.lowercase() == "trailer" || it.type?.lowercase() == "teaser")
        } ?: relatedVideos.firstOrNull { it.site?.lowercase() == "youtube" }
    }
    var autoplayEmbedFailed by remember { mutableStateOf(false) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = LocalTvMode.current
    val context = LocalContext.current

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
        isSynthwave -> Color(0xFF0C061A)
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

    val targetBackdropId = if (item?.mediaType == MediaType.EPISODE && item.seriesId != null) {
        item.seriesId!!
    } else {
        itemId
    }

    val contentFocusRequester = remember { FocusRequester() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    if (isTv) {
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { contentVisible }.first { it }
            // The Play button carrying contentFocusRequester lives inside AnimatedVisibility(contentVisible),
            // so it may not be composed/attached on the very frame contentVisible flips true. Wait a frame
            // and retry briefly so the request is not silently swallowed by tryRequestFocus.
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                if (contentFocusRequester.tryRequestFocus("detail_content")) break
            }
        }
    }

    val backgroundModifier = if (isSynthwave) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF0D061A), Color(0xFF1B0B3A))
            )
        )
    } else {
        Modifier.background(backgroundColor)
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
        // While the detail is still loading (contentVisible == false) there is no focusable node on
        // screen, so TV focus is orphaned until data arrives. Show a focusable loading surface for
        // that window; LoadingScreen requests focus on TV. It is removed the moment contentVisible
        // flips true, after which the contentFocusRequester grab takes over on the Play button.
        if (isTv && !contentVisible && error == null) {
            LoadingScreen(modifier = Modifier.fillMaxSize())
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isLandscapeExpanded) backgroundColor.copy(alpha = 0.5f) else Color.Transparent,
                                backgroundColor.copy(alpha = if (isLandscapeExpanded) 0.8f else 0.4f),
                                backgroundColor.copy(alpha = 0.9f),
                                backgroundColor,
                            ),
                            startY = if (isLandscapeExpanded) 0f else with(density) { (baseBackdropHeight - 200.dp).toPx() },
                            endY = with(density) { backdropHeight.toPx() }
                            )
                    )
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                backgroundColor.copy(alpha = 0.9f),
                                backgroundColor,
                            ),
                            startY = 0f,
                            endY = with(density) { 150.dp.toPx() }
                        )
                    )
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
                                            if (itemId != null) {
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
                                            } else Modifier
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
                                        onMediaInfoClick = { onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.MediaInfo(item.id)) },
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
                .background(animatedContainerColor)
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
                    if (!isTv) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        val editIconColor = if (scrollCollapsed < 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (scrollCollapsed < 0.5f) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f) else Color.Transparent
                                    )
                            ) {
                                Icon(
                                    Tabler.Outline.DotsVertical,
                                    contentDescription = "Options",
                                    tint = editIconColor,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        menuExpanded = false
                                        onEditClick()
                                    },
                                    leadingIcon = {
                                        Icon(Tabler.Outline.Pencil, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = {
                                        menuExpanded = false
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "jellyplay://media/$itemId")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                    },
                                    leadingIcon = {
                                        Icon(Tabler.Outline.Share, contentDescription = null)
                                    }
                                )
                                val canDownload = item != null && detail != null && detail.mediaSources.isNotEmpty() &&
                                        (item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC || (!isAudio && !isSeries))
                                if (canDownload) {
                                    val downloadStatus = activeDownload?.status
                                    val isDownloadActive = downloadStatus == DownloadStatus.PENDING ||
                                            downloadStatus == DownloadStatus.DOWNLOADING ||
                                            downloadStatus == DownloadStatus.PAUSED
                                    val isDownloadCompleted = downloadStatus == DownloadStatus.COMPLETED
                                    val downloadProgress = if (activeDownload != null && activeDownload.totalSizeBytes > 0) {
                                        activeDownload.downloadedBytes.toFloat() / activeDownload.totalSizeBytes
                                    } else 0f

                                    DropdownMenuItem(
                                        text = {
                                            val text = when {
                                                isDownloadCompleted -> "Downloaded"
                                                isDownloading || isDownloadActive -> {
                                                    if (downloadProgress > 0f && downloadStatus == DownloadStatus.DOWNLOADING) {
                                                        "Downloading (${(downloadProgress * 100).toInt()}%)"
                                                    } else {
                                                        "Downloading..."
                                                    }
                                                }
                                                else -> "Download"
                                            }
                                            Text(text)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showDownloadDialog = true
                                        },
                                        enabled = !isDownloading && !isDownloadActive && !isDownloadCompleted,
                                        leadingIcon = {
                                            val icon = if (isDownloadCompleted) Tabler.Outline.Check else Tabler.Outline.Download
                                            Icon(icon, contentDescription = null)
                                        }
                                    )
                                } else if (!isAudio && item != null && isSeries && seasons.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isDownloadingSeries) "Downloading Series..." else "Download Series")
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDownloadSeriesClick()
                                        },
                                        enabled = !isDownloadingSeries,
                                        leadingIcon = {
                                            Icon(Tabler.Outline.Download, contentDescription = null)
                                        }
                                    )
                                }
                                if (isSeries || item?.seriesId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Hide from Next Up") },
                                        onClick = {
                                            menuExpanded = false
                                            onHideFromNextUp()
                                        },
                                        leadingIcon = {
                                            Icon(Tabler.Outline.EyeOff, contentDescription = null)
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
                size >= 1_000_000_000 -> "%.1f GB".format(size / 1_000_000_000.0)
                size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
                size >= 1_000 -> "%.1f KB".format(size / 1_000.0)
                else -> "$size B"
            }
        } ?: "Unknown"
        val availableText = when {
            availableBytes >= 1_000_000_000 -> "%.1f GB".format(availableBytes / 1_000_000_000.0)
            availableBytes >= 1_000_000 -> "%.1f MB".format(availableBytes / 1_000_000.0)
            else -> "%.1f KB".format(availableBytes / 1_000.0)
        }
        val enoughSpace = fileSize == null || fileSize <= availableBytes

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download") },
            text = {
                Column {
                    Text("Estimated size: $fileSizeText")
                    Spacer(Modifier.height(8.dp))
                    Text("Available storage: $availableText")
                    if (!enoughSpace) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Not enough storage space available.",
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
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StaggeredDetailSection(
    visible: Boolean,
    delayIndex: Int,
    content: @Composable () -> Unit,
) {
    if (visible) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaInfoSection(
    mediaStreams: List<MediaStream>,
    selectedAudioIndex: Int?,
    selectedSubtitleIndex: Int?,
    onAudioSelect: (Int?) -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
    preferences: UserPreferences,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }
    val audioStreams = mediaStreams.filter { it.type == StreamType.AUDIO }
    val subtitleStreams = mediaStreams.filter { it.type == StreamType.SUBTITLE }

    if (videoStream == null && audioStreams.isEmpty() && subtitleStreams.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val chipBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        var picker by remember { mutableStateOf<StreamPickerType?>(null) }

        val defaultAudio = audioStreams.firstOrNull { it.isDefault } ?: audioStreams.firstOrNull()
        val selectedAudio = when (selectedAudioIndex) {
            -1 -> defaultAudio
            null -> {
                val prefAudioLang = preferences.preferredAudioLanguage ?: "eng"
                audioStreams.firstOrNull { isLanguageMatch(it.language, prefAudioLang) } ?: defaultAudio
            }
            else -> audioStreams.firstOrNull { it.index == selectedAudioIndex } ?: defaultAudio
        }

        val defaultSubtitle = subtitleStreams.firstOrNull { it.isDefault }
        val selectedSubtitle = when (selectedSubtitleIndex) {
            -1 -> null // Explicitly Off
            null -> {
                val prefSubLang = preferences.preferredSubtitleLanguage ?: "eng"
                subtitleStreams.firstOrNull { isLanguageMatch(it.language, prefSubLang) }
            }
            else -> subtitleStreams.firstOrNull { it.index == selectedSubtitleIndex } ?: defaultSubtitle
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val qualityLabel = buildString {
                val res = videoStream?.height?.let { h ->
                    when {
                        h >= 2160 -> "4K"
                        h >= 1080 -> "HD"
                        h >= 720 -> "HD"
                        else -> "SD"
                    }
                } ?: "Auto"
                append(res)
                append(" ")
                val range = videoStream?.videoDoViTitle
                    ?: videoStream?.videoRangeType
                    ?: videoStream?.videoRange
                    ?: "SDR"
                append(range.uppercase())
            }

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.BadgeHd,
                    text = qualityLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val audioLabel = buildString {
                append(
                    selectedAudio
                        ?.language?.uppercase()?.take(3)
                        ?: selectedAudio?.displayTitle?.take(3)?.uppercase()
                        ?: "AUTO"
                )
                selectedAudio?.channels?.let { channels ->
                    append(" - ")
                    append(
                        when (channels) {
                            1 -> "MONO"
                            2 -> "STEREO"
                            6 -> "5.1"
                            8 -> "7.1"
                            else -> "${channels}CH"
                        }
                    )
                }
            }

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.WaveSine,
                    text = audioLabel,
                    showTrailingIndicator = true,
                    onClick = { if (audioStreams.isNotEmpty()) picker = StreamPickerType.AUDIO },
                    containerColor = chipBackgroundColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val subtitleLabel = selectedSubtitle
                ?.displayTitle
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.length > 10) it.take(10) + "…" else it }
                ?: selectedSubtitle?.language?.uppercase()?.take(3)
                ?: "OFF"

            FadingItem(modifier = Modifier.weight(1f)) {
                QuickInfoPill(
                    icon = Tabler.Outline.Subtitles,
                    text = subtitleLabel,
                    showTrailingIndicator = subtitleStreams.isNotEmpty(),
                    onClick = { picker = StreamPickerType.SUBTITLE },
                    containerColor = chipBackgroundColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (picker != null) {
            val activePicker = picker ?: return@Column
            val options = when (activePicker) {
                StreamPickerType.AUDIO -> {
                    buildList {
                        if (selectedAudioIndex != null) {
                            add(StreamPickerOption(index = null, label = "Auto", isDefault = false))
                        }
                        add(StreamPickerOption(index = -1, label = "Default", isDefault = true))
                        addAll(
                            audioStreams.map { stream ->
                                StreamPickerOption(
                                    index = stream.index,
                                    label = stream.displayTitle
                                        ?: stream.title
                                        ?: stream.language
                                        ?: "Track ${stream.index}",
                                    isDefault = stream.isDefault,
                                )
                            }
                        )
                    }
                }
                StreamPickerType.SUBTITLE -> {
                    buildList {
                        if (selectedSubtitleIndex != null) {
                            add(StreamPickerOption(index = null, label = "Auto", isDefault = false))
                        }
                        add(StreamPickerOption(index = -1, label = "Off", isDefault = true))
                        addAll(
                            subtitleStreams.map { stream ->
                                StreamPickerOption(
                                    index = stream.index,
                                    label = stream.displayTitle
                                        ?: stream.title
                                        ?: stream.language
                                        ?: "Track ${stream.index}",
                                    isDefault = stream.isDefault,
                                )
                            }
                        )
                    }
                }
            }

            val selectedIndex = when (activePicker) {
                StreamPickerType.AUDIO -> selectedAudioIndex
                StreamPickerType.SUBTITLE -> selectedSubtitleIndex
            }

            TvSafeSheet(
                onDismissRequest = { picker = null },
                title = if (activePicker == StreamPickerType.AUDIO) "Select Audio" else "Select Subtitle",
            ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(options, key = { "${activePicker}_${it.index}_${it.label}" }, contentType = { "streamOption" }) { option ->
                            val isSelected = option.index == selectedIndex
                            val optionInteractionSource = remember { MutableInteractionSource() }
                            val isOptionPressed by optionInteractionSource.collectIsPressedAsState()
                            val optionScale by animateFloatAsState(
                                targetValue = if (isOptionPressed) 0.97f else 1f,
                                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                label = "optionScale",
                            )
                            val optionFocusState = rememberTvFocusState(focusedScale = 1.03f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShapeCache.smooth12)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    .graphicsLayer {
                                        scaleX = optionScale
                                        scaleY = optionScale
                                    }
                                    .then(optionFocusState.focusModifier)
                                    .then(Modifier.tvFocusIndicator(optionFocusState, ShapeCache.smooth12))
                                    .clickable(
                                        interactionSource = optionInteractionSource,
                                        indication = null,
                                    ) {
                                        if (picker == StreamPickerType.AUDIO) {
                                            onAudioSelect(option.index)
                                        } else {
                                            onSubtitleSelect(option.index)
                                        }
                                        picker = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (option.isDefault && option.index != null) {
                                    Text(
                                        text = "DEFAULT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Tabler.Outline.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

private enum class StreamPickerType {
    AUDIO,
    SUBTITLE,
}

private data class StreamPickerOption(
    val index: Int?,
    val label: String,
    val isDefault: Boolean,
)

@Composable
private fun QuickInfoPill(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    showTrailingIndicator: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
    onClick: (() -> Unit)? = null,
) {
    val isTv = LocalTvMode.current
    val pillFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Row(
        modifier = modifier
            .clip(ShapeCache.smooth14)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier
                        .ifElse(isTv, pillFocusState.focusModifier)
                        .ifElse(isTv, Modifier.tvFocusIndicator(pillFocusState, ShapeCache.smooth14))
                        .clickable { onClick() }
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (showTrailingIndicator) {
            Icon(
                imageVector = Tabler.Outline.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun InfoBadge(
    text: String,
    highlight: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth4)
            .background(
                if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SubtitleChip(
    label: String,
    isSelected: Boolean,
    isDefault: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "subtitleChipBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "subtitleChipContent",
    )
    val isTv = LocalTvMode.current
    val chipFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Row(
        modifier = Modifier
            .clip(ShapeCache.smooth16)
            .background(bgColor)
            .ifElse(isTv, chipFocusState.focusModifier)
            .ifElse(isTv, Modifier.tvFocusIndicator(chipFocusState, ShapeCache.smooth16))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
        if (isDefault && !isSelected) {
            Text(
                text = "(default)",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DetailActionButtons(
    item: MediaItem,
    detail: MediaDetail,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailViewModel.SmartPlayTarget?,
    isAudio: Boolean,
    isAlbum: Boolean,
    albumTracks: List<MediaItem>,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onPlayAlbumTrack: (Int) -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onMediaInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    contentFocusRequester: FocusRequester? = null,
) {
    val isSeriesOrEpisode = item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE
    val isSeries = item.mediaType == MediaType.SERIES
    val target = if (isSeriesOrEpisode) smartPlayTarget else null
    val hasProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0
    val allSeasonsFetched = seasons.isNotEmpty() && fetchedSeasonIds.size >= seasons.size
    val allEpisodesEmpty = seasons.isNotEmpty() && episodes.values.all { it.isEmpty() }
    val isResolvingSeriesTarget = isSeries &&
        target == null &&
        !allSeasonsFetched
    val hasNoEpisodes = isSeries && allSeasonsFetched && allEpisodesEmpty
    val canPlayPrimary = isAudio || !isSeries || target != null || hasNoEpisodes
    val progress = if (target != null) {
        val t = target.startPositionTicks
        val rt = target.episode.runTimeTicks
        if (t > 0 && rt != null && rt > 0) (t.toFloat() / rt).coerceIn(0f, 1f) else 0f
    } else if (hasProgress && item.runTimeTicks != null && item.runTimeTicks!! > 0) {
        (item.playbackPositionTicks!!.toFloat() / item.runTimeTicks!!).coerceIn(0f, 1f)
    } else 0f

    val playLabel = when {
        target != null -> target.label
        isResolvingSeriesTarget -> "Finding Episode"
        hasNoEpisodes -> "No Episodes Available"
        isSeries -> "No Episodes"
        hasProgress -> "Resume"
        else -> "Play"
    }

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "playButtonScale",
    )
    val markInteractionSource = remember { MutableInteractionSource() }
    val isMarkPressed by markInteractionSource.collectIsPressedAsState()
    val markScale by animateFloatAsState(
        targetValue = if (isMarkPressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "markButtonScale",
    )
    val favoriteInteractionSource = remember { MutableInteractionSource() }
    val isFavoritePressed by favoriteInteractionSource.collectIsPressedAsState()
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavoritePressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "favoriteButtonScale",
    )

    val isTv = LocalTvMode.current

    // Play button — full width in vertical mode, fixed width in horizontal
    val playButton: @Composable () -> Unit = {
        val playTvFocusState = rememberTvFocusState(focusedScale = 1.05f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(ShapeCache.smooth14)
                .background(
                    if (canPlayPrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
                .then(
                    contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                )
                .then(
                    if (isTv) playTvFocusState.focusModifier else Modifier
                )
                .then(
                    if (isTv) Modifier.tvFocusIndicator(playTvFocusState, ShapeCache.smooth14) else Modifier
                )
                .graphicsLayer { scaleX = playScale; scaleY = playScale }
                .clickable(
                    interactionSource = playInteractionSource,
                    indication = null,
                    enabled = canPlayPrimary,
                ) {
                    if (isAlbum && albumTracks.isNotEmpty()) {
                        onPlayAlbumTrack(0)
                        albumTracks.firstOrNull()?.let { track ->
                            onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.AudioPlayer(track.id))
                        }
                    } else if (isAudio) {
                        onAudioClick()
                    } else if (target != null) {
                        onPlayClick(target.episode.id, null, target.startPositionTicks)
                    } else {
                        val sourceId = detail.mediaSources.firstOrNull()?.id
                        val startPos = item.playbackPositionTicks ?: 0L
                        onPlayClick(item.id, sourceId, startPos)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Tabler.Outline.PlayerPlay, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(6.dp))
                Text(playLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    // Icon action buttons — watch, favorite, download
    val iconsModifier = Modifier
        .fillMaxWidth()

    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FadingItem {
                playButton()
            }
            Row(
                modifier = iconsModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isTv = LocalTvMode.current
                val markTvFocusState = rememberTvFocusState(focusedScale = 1.08f)
                val favoriteTvFocusState = rememberTvFocusState(focusedScale = 1.08f)

                FadingItem(modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            .then(
                                if (isTv) markTvFocusState.focusModifier else Modifier
                            )
                            .then(
                                if (isTv) Modifier.tvFocusIndicator(markTvFocusState, ShapeCache.smooth12) else Modifier
                            )
                            .graphicsLayer { scaleX = markScale; scaleY = markScale }
                            .clickable(interactionSource = markInteractionSource, indication = null) {
                                if (item.isPlayed) onMarkUnplayed() else onMarkPlayed()
                            }
                    ) {
                        Icon(
                            if (item.isPlayed) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                            contentDescription = if (item.isPlayed) "Mark as Unwatched" else "Mark as Watched",
                            tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                FadingItem(modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            .then(
                                if (isTv) favoriteTvFocusState.focusModifier else Modifier
                            )
                            .then(
                                if (isTv) Modifier.tvFocusIndicator(favoriteTvFocusState, ShapeCache.smooth12) else Modifier
                            )
                            .graphicsLayer { scaleX = favoriteScale; scaleY = favoriteScale }
                            .clickable(interactionSource = favoriteInteractionSource, indication = null) { onToggleFavorite() }
                    ) {
                        Icon(
                            if (item.isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                val infoVTvFocusState = rememberTvFocusState(focusedScale = 1.08f)
                FadingItem(modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            .ifElse(isTv, infoVTvFocusState.focusModifier)
                            .ifElse(isTv, Modifier.tvFocusIndicator(infoVTvFocusState, ShapeCache.smooth12))
                            .clickable { onMediaInfoClick() }
                    ) {
                        Icon(
                            Tabler.Outline.InfoCircle,
                            contentDescription = "Technical Info",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        ) {
            // Horizontal: play button fixed width, icon buttons fixed size
            val playHFocusState = rememberTvFocusState(focusedScale = 1.05f)
            FadingItem {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .width(200.dp)
                        .clip(ShapeCache.smooth16)
                        .background(
                            if (canPlayPrimary) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        )
                        .then(
                            contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                        )
                        .ifElse(isTv, playHFocusState.focusModifier)
                        .ifElse(isTv, Modifier.tvFocusIndicator(playHFocusState, ShapeCache.smooth16))
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null,
                            enabled = canPlayPrimary,
                        ) {
                            if (isAudio) {
                                onAudioClick()
                            } else if (target != null) {
                                onPlayClick(target.episode.id, null, target.startPositionTicks)
                            } else {
                                val sourceId = detail.mediaSources.firstOrNull()?.id
                                val startPos = item.playbackPositionTicks ?: 0L
                                onPlayClick(item.id, sourceId, startPos)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .align(Alignment.CenterStart)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Tabler.Outline.PlayerPlay, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.size(8.dp))
                        Text(playLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            val markHFocusState = rememberTvFocusState(focusedScale = 1.08f)
            val favoriteHFocusState = rememberTvFocusState(focusedScale = 1.08f)

            FadingItem {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = markScale; scaleY = markScale }
                        .ifElse(isTv, markHFocusState.focusModifier)
                        .ifElse(isTv, Modifier.tvFocusIndicator(markHFocusState, ShapeCache.smooth16))
                        .clickable(interactionSource = markInteractionSource, indication = null) {
                            if (item.isPlayed) onMarkUnplayed() else onMarkPlayed()
                        }
                ) {
                    Icon(
                        if (item.isPlayed) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                        contentDescription = if (item.isPlayed) "Mark as Unwatched" else "Mark as Watched",
                        tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            FadingItem {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = favoriteScale; scaleY = favoriteScale }
                        .ifElse(isTv, favoriteHFocusState.focusModifier)
                        .ifElse(isTv, Modifier.tvFocusIndicator(favoriteHFocusState, ShapeCache.smooth16))
                        .clickable(interactionSource = favoriteInteractionSource, indication = null) { onToggleFavorite() }
                ) {
                    Icon(
                        if (item.isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            val infoHFocusState = rememberTvFocusState(focusedScale = 1.08f)
            FadingItem {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .ifElse(isTv, infoHFocusState.focusModifier)
                        .ifElse(isTv, Modifier.tvFocusIndicator(infoHFocusState, ShapeCache.smooth16))
                        .clickable { onMediaInfoClick() }
                ) {
                    Icon(
                        Tabler.Outline.InfoCircle,
                        contentDescription = "Technical Info",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            }
    }
}

@Composable
private fun DetailContentBody(
    item: MediaItem,
    detail: MediaDetail,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailViewModel.SmartPlayTarget?,
    selectedSubtitleIndex: Int?,
    selectedAudioIndex: Int?,
    getImageUrl: (String) -> String,
    isAudio: Boolean,
    isAlbum: Boolean,
    albumTracks: List<MediaItem>,
    collectionItems: List<MediaItem> = emptyList(),
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onPlayAlbumTrack: (Int) -> Unit = {},
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
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
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    showActionButtons: Boolean = true,
    showMediaInfo: Boolean = true,
    contentFocusRequester: FocusRequester? = null,
    seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    seerrSimilar: List<SeerrSearchItem> = emptyList(),
    isSeerrConnected: Boolean = false,
    isSeerrRecommendationsEnabled: Boolean = false,
    getSeerrPosterUrl: (String?) -> String? = { null },
    onSeerrRequest: (SeerrSearchItem) -> Unit = {},
    relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    onVideoClick: (SeerrRelatedVideo) -> Unit = {},
    preferences: UserPreferences,
) {
    val showContent = true

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val maxWidth = adaptiveInfo.detailBodyMaxWidth(isTv)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        StaggeredDetailSection(visible = showContent, delayIndex = 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                if (item.mediaType == MediaType.EPISODE && item.seriesId != null) {
                    val seriesNavFocusState = rememberTvFocusState(focusedScale = 1.02f)
                    FadingItem {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(ShapeCache.smooth8)
                                .then(seriesNavFocusState.focusModifier)
                                .then(Modifier.tvFocusIndicator(seriesNavFocusState, ShapeCache.smooth8))
                                .clickable { onNavigateToSeries(item.seriesId!!) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.seriesName ?: "Series",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.seasonName?.let { season ->
                                Text(
                                    text = " › ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = season,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (item.mediaType == MediaType.EPISODE) {
                    val season = item.seasonNumber ?: item.parentId?.toIntOrNull()
                    val episode = item.episodeNumber ?: item.indexNumber
                    val episodeContext = buildString {
                        if (season != null) append("S$season")
                        if (episode != null) {
                            if (isNotEmpty()) append(" · ")
                            append("E$episode")
                        }
                        item.seriesName?.takeIf { it.isNotBlank() }?.let { series ->
                            if (isNotEmpty()) append(" · ")
                            append(series)
                        }
                    }
                    if (episodeContext.isNotBlank()) {
                        FadingItem {
                            Column {
                                Text(
                                    text = episodeContext,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                FadingItem {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item.originalTitle
                    ?.takeIf { it.isNotBlank() && !it.equals(item.name, ignoreCase = true) }
                    ?.let { originalTitle ->
                        FadingItem {
                            Column {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = originalTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                Spacer(Modifier.height(12.dp))
                FadingItem {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        item.runTimeTicks?.let { ticks ->
                            val minutes = ticks / 600_000_000
                            Text(
                                text = "${minutes}m",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        item.officialRating?.let {
                            Box(
                                modifier = Modifier
                                    .clip(ShapeCache.smooth4)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        item.communityRating?.let { rating ->
                            val ratingText = remember(rating) { String.format("%.1f", rating) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Tabler.Outline.Heart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = ratingText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }

                item.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                    Spacer(Modifier.height(14.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .tvFocusRestorer(),
                    ) {
                        items(genres, key = { it }, contentType = { "genre" }) { genre ->
                            FadingItem {
                                Box(
                                    modifier = Modifier
                                        .clip(ShapeCache.smooth16)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showActionButtons) StaggeredDetailSection(visible = showContent, delayIndex = 1) {
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
                onMediaInfoClick = { onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.MediaInfo(detail.item.id)) },
                vertical = false,
                contentFocusRequester = contentFocusRequester,
            )
        }

        if (showMediaInfo) StaggeredDetailSection(visible = showContent && !isAudio, delayIndex = 2) {
            val source = detail.mediaSources.firstOrNull()
            if (source != null) {
                MediaInfoSection(
                    mediaStreams = source.mediaStreams,
                    selectedAudioIndex = selectedAudioIndex,
                    selectedSubtitleIndex = selectedSubtitleIndex,
                    onAudioSelect = onAudioSelect,
                    onSubtitleSelect = onSubtitleSelect,
                    preferences = preferences,
                )
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 3) { }

        StaggeredDetailSection(visible = showContent, delayIndex = 4) {
            item.overview?.let { overview ->
                FadingItem {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                    }
                }
            }
        }

        StaggeredDetailSection(visible = showContent && isAudio && albumTracks.isNotEmpty(), delayIndex = 5) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                FadingItem {
                    Text(
                        text = "Tracks",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    albumTracks.forEachIndexed { index, track ->
                        val trackClick = remember(track.id) { { onItemClick(track.id) } }
                        val trackPlayClick = remember(track.id, index) { { onPlayAlbumTrack(index); onItemClick(track.id) } }
                        FadingItem {
                            AlbumTrackItem(
                                track = track,
                                index = index + 1,
                                imageUrl = getImageUrl(track.id),
                                onClick = trackClick,
                                onPlayClick = trackPlayClick,
                            )
                        }
                    }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 6) {
            val showSeasons = (item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE) && seasons.isNotEmpty()
            if (showSeasons) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SeasonsSection(
                        seriesItem = item,
                        seasons = seasons,
                        episodes = episodes,
                        fetchedSeasonIds = fetchedSeasonIds,
                        smartPlayTarget = smartPlayTarget,
                        getImageUrl = getImageUrl,
                        currentItemId = if (item.mediaType == MediaType.EPISODE) item.id else null,
                        currentSeasonId = if (item.mediaType == MediaType.EPISODE) item.seasonId else null,
                        onEpisodePlayClick = { episode ->
                            val sourceId = null
                            val startPos = episode.playbackPositionTicks ?: 0L
                            onPlayClick(episode.id, sourceId, startPos)
                        },
                        onEpisodeDetailClick = { episode ->
                            onItemClick(episode.id)
                        },
                        onSeasonSelected = onSeasonSelected,
                    )
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 7) {
            if (item.mediaType == MediaType.COLLECTION && collectionItems.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = "Items",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    TvFocusableItemRow(
                        items = collectionItems,
                        key = { "collection_${it.id}" },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { _, collectionItem, focusModifier ->
                            val collectionClick = remember(collectionItem.id) { { onItemClick(collectionItem.id) } }
                            FadingItem {
                                PosterCard(
                                    item = collectionItem,
                                    imageUrl = getImageUrl(collectionItem.id),
                                    onClick = collectionClick,
                                    showProgress = collectionItem.playbackPositionTicks != null && collectionItem.playbackPositionTicks!! > 0,
                                    progressPercent = if (collectionItem.runTimeTicks != null && collectionItem.runTimeTicks!! > 0) {
                                        (collectionItem.playbackPositionTicks?.toFloat() ?: 0f) / collectionItem.runTimeTicks!!.toFloat()
                                    } else 0f,
                                    modifier = focusModifier.width(160.dp),
                                )
                            }
                    }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 7) {
            if (detail.people.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = "Cast & Crew",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        TvFocusableItemRow(
                            items = detail.people,
                            key = { "person_${it.id}" },
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) { _, person, focusModifier ->
                                val personClick = remember(person.id) { { onPersonClick(person.id) } }
                                FadingItem {
                                    PersonItem(
                                        person = person,
                                        imageUrl = getImageUrl(person.id),
                                        onClick = personClick,
                                        modifier = focusModifier,
                                    )
                                }
                }

        }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 8) {
            if (relatedVideos.isNotEmpty()) {
                VideosSection(videos = relatedVideos, onVideoClick = onVideoClick)
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 8) {
            if (detail.relatedItems.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = "More Like This",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    TvFocusableItemRow(
                        items = detail.relatedItems,
                        key = { "related_${it.id}" },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { _, related, focusModifier ->
                            val relatedClick = remember(related.id) { { onItemClick(related.id) } }
                            val adaptiveInfo = LocalAdaptiveInfo.current
                            FadingItem {
                                PosterCard(
                                    item = related,
                                    imageUrl = getImageUrl(related.id),
                                    onClick = relatedClick,
                                    modifier = focusModifier.width(
                                        if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                                    ),
                                )
                            }
                    }
                }
            }
        }

        // ── Seerr Recommendations Section ──
        LaunchedEffect(isSeerrConnected, isSeerrRecommendationsEnabled, seerrRecommendations.isEmpty()) {
            if (isSeerrConnected && isSeerrRecommendationsEnabled && seerrRecommendations.isEmpty()) {
                kotlinx.coroutines.delay(1000)
                onLoadSeerrData()
            }
        }
        
        if (isSeerrConnected && isSeerrRecommendationsEnabled && seerrRecommendations.isNotEmpty()) {
            StaggeredDetailSection(visible = showContent, delayIndex = 8) {
                SeerrItemsRow(
                    title = "Seerr Recommendations",
                    keyPrefix = "seerr_rec",
                    contentType = "seerrRecItem",
                    items = seerrRecommendations,
                    onSeerrRequest = onSeerrRequest,
                    onNavigate = onNavigate,
                )
            }
        }

        // ── Seerr Similar Section ──
        if (isSeerrConnected && isSeerrRecommendationsEnabled && seerrSimilar.isNotEmpty()) {
            StaggeredDetailSection(visible = showContent, delayIndex = 9) {
                SeerrItemsRow(
                    title = "Seerr Similar",
                    keyPrefix = "seerr_sim",
                    contentType = "seerrSimItem",
                    items = seerrSimilar,
                    onSeerrRequest = onSeerrRequest,
                    onNavigate = onNavigate,
                )
            }
        }
    }
    }
}

@Composable
private fun SeerrItemsRow(
    title: String,
    keyPrefix: String,
    contentType: String,
    items: List<SeerrSearchItem>,
    onSeerrRequest: (SeerrSearchItem) -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val loadingState = com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState.current
    val prefetch = com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch.current
    val cardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp

    Column {
        FadingItem {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableItemRow(
            items = items,
            key = { "${keyPrefix}_${it.id}" },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, seerrItem, focusModifier ->
                FadingItem {
                    SeerrMediaCard(
                        item = seerrItem,
                        imageUrl = seerrItem.posterUrl,
                        isLoading = loadingState?.isLoading(seerrItem.id) == true,
                        onClick = {
                            if (loadingState != null && prefetch != null) {
                                loadingState.startLoading(seerrItem.id)
                                prefetch(seerrItem.id, seerrItem.mediaType) {
                                    loadingState.stopLoading(seerrItem.id)
                                    onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(seerrItem.id, seerrItem.mediaType))
                                }
                            } else {
                                onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(seerrItem.id, seerrItem.mediaType))
                            }
                        },
                        onRequestClick = { onSeerrRequest(seerrItem) },
                        modifier = focusModifier.width(cardWidth),
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeasonsSection(
    seriesItem: MediaItem,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailViewModel.SmartPlayTarget?,
    getImageUrl: (String) -> String,
    currentItemId: String? = null,
    currentSeasonId: String? = null,
    onEpisodePlayClick: (MediaItem) -> Unit,
    onEpisodeDetailClick: (MediaItem) -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit = {},
) {
    val smartTargetSeasonId = smartPlayTarget?.episode?.seasonId
    val initialSeasonIndex = when {
        smartTargetSeasonId != null -> {
            seasons.indexOfFirst { it.id == smartTargetSeasonId }.coerceAtLeast(0)
        }
        currentSeasonId != null -> {
            seasons.indexOfFirst { it.id == currentSeasonId }.coerceAtLeast(0)
        }
        else -> 0
    }
    var selectedSeasonIndex by remember { mutableStateOf(initialSeasonIndex) }

    LaunchedEffect(selectedSeasonIndex) {
        val season = seasons.getOrNull(selectedSeasonIndex)
        if (season != null) {
            onSeasonSelected(season.id)
        }
    }

    Column {
        FadingItem {
            Text(
                text = "Seasons",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(16.dp))

        TvFocusableItemRow(
            items = seasons,
            key = { it.id },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { index, season, focusModifier ->
                val isSelected = index == selectedSeasonIndex
                val targetColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                val targetContentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                val surfaceColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "seasonColor",
                )
                val contentColor by animateColorAsState(
                    targetValue = targetContentColor,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "seasonContentColor",
                )
                val seasonTabFocusState = rememberTvFocusState(focusedScale = 1.05f)
                FadingItem {
                    Surface(
                        modifier = focusModifier
                            .clip(ShapeCache.smooth16)
                            .then(seasonTabFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(seasonTabFocusState, ShapeCache.smooth16))
                            .clickable { selectedSeasonIndex = index },
                        color = surfaceColor,
                        contentColor = contentColor,
                    ) {
                        Text(
                            text = season.name ?: "Season ${season.indexNumber ?: index + 1}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
        }

        Spacer(Modifier.height(20.dp))

        val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
        val seasonEpisodes = selectedSeason?.let { episodes[it.id] }
        val isFetched = selectedSeason?.id?.let { fetchedSeasonIds.contains(it) } ?: false
        val isLoading = seasonEpisodes == null && selectedSeason != null && !isFetched

        val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = selectedSeasonIndex to (seasonEpisodes?.size ?: 0),
            transitionSpec = {
                val direction = if (targetState.first >= initialState.first) 1 else -1
                fadeIn(
                    animationSpec = tween(400),
                ) togetherWith fadeOut(
                    animationSpec = tween(300),
                )
            },
            label = "seasonEpisodes",
        ) { (seasonIdx, episodeCount) ->
            val currentEpisodes = seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
            val currentIsFetched = seasons.getOrNull(seasonIdx)?.id?.let { fetchedSeasonIds.contains(it) } ?: false
            val currentIsLoading = currentEpisodes == null && seasons.getOrNull(seasonIdx) != null && !currentIsFetched

            when {
                currentIsLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FadingItem {
                            JellyPlayLoadingIndicator()
                        }
                    }
                }
                currentEpisodes != null && currentEpisodes.isNotEmpty() -> {
                    TvFocusableItemRow(
                        items = currentEpisodes,
                        key = { "episode_${it.id}" },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) { _, episode, focusModifier ->
                            FadingItem {
                                EpisodeCard(
                                    episode = episode,
                                    getImageUrl = getImageUrl,
                                    isCurrentEpisode = episode.id == currentItemId,
                                    onPlayClick = { onEpisodePlayClick(episode) },
                                    onDetailClick = { onEpisodeDetailClick(episode) },
                                    modifier = focusModifier,
                                )
                            }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FadingItem {
                            Text("No episodes available", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EpisodeCardSkeleton() {
    JellyPlayLoadingIndicator()
}

@Composable
private fun EpisodeCard(
    episode: MediaItem,
    getImageUrl: (String) -> String,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "episodeCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "episodePlayScale",
    )

    val cardFocusState = rememberTvFocusState(focusedScale = 1.03f)

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val borderModifier = when {
        isSynthwave -> Modifier.border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            ),
            shape = ShapeCache.smooth16
        )
        isSoothing -> Modifier.border(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            shape = ShapeCache.smooth16
        )
        else -> Modifier
    }

    Column(
        modifier = modifier
            .width(280.dp)
            .then(borderModifier)
            .clip(ShapeCache.smooth16)
            .background(
                if (isCurrentEpisode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .then(
                if (isCurrentEpisode) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                else Modifier
            )
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .then(cardFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MediaImage(
                url = getImageUrl(episode.id),
                contentDescription = episode.name,
                blurHash = episode.blurHashes.primary,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))
            val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                    .then(epPlayFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(epPlayFocusState, CircleShape))
                    .clickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                        onClick = onPlayClick,
                    )
                    .padding(8.dp)
            )

            if (episode.playbackPositionTicks != null && episode.playbackPositionTicks!! > 0) {
                val progress = if (episode.runTimeTicks != null && episode.runTimeTicks!! > 0) {
                    (episode.playbackPositionTicks!!.toFloat() / episode.runTimeTicks!!).coerceIn(0f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (episode.isPlayed && (episode.playbackPositionTicks == null || episode.playbackPositionTicks!! <= 0)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(ShapeCache.smooth4)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = buildString {
                    episode.indexNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val hasWatchProgress = episode.playbackPositionTicks != null && episode.playbackPositionTicks!! > 0 && !episode.isPlayed
            val remainingTime = if (hasWatchProgress && episode.runTimeTicks != null) {
                formatRemainingTimeFromTicks(episode.runTimeTicks!!, episode.playbackPositionTicks!!)
            } else null
            val totalTime = if (episode.runTimeTicks != null) {
                formatDurationFromTicks(episode.runTimeTicks!!)
            } else null
            
            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "$remainingTime left",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            } else if (totalTime != null) {
                Text(
                    text = totalTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }
        }
    }
}

@Composable
private fun PersonItem(
    person: PersonInfo,
    imageUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "personScale",
    )

    val personFocusState = rememberTvFocusState(focusedScale = 1.08f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(personFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(personFocusState, CircleShape))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = person.name,
            blurHash = person.primaryBlurHash,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        person.role?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumTrackItem(
    track: MediaItem,
    index: Int,
    imageUrl: String,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "trackScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ShapeCache.smooth4),
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = track.name,
                blurHash = track.blurHashes.primary,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.albumArtist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onPlayClick) {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        track.runTimeTicks?.let { ticks ->
            val minutes = (ticks / 600_000_000)
            val seconds = ((ticks / 10_000_000) % 60)
            Text(
                text = String.format("%d:%02d", minutes, seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FadingItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "itemAlpha"
    )
    val blurRadius by animateFloatAsState(
        targetValue = if (visible) 0f else 8f,
        animationSpec = tween(400),
        label = "itemBlur"
    )
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .blur(blurRadius.dp)
    ) {
        content()
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
private fun VideosSection(
    videos: List<SeerrRelatedVideo>,
    onVideoClick: (SeerrRelatedVideo) -> Unit,
) {
    Column {
        FadingItem {
            Text(
                text = "Videos",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableItemRow(
            items = videos,
            key = { it.key ?: "" },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, video, focusModifier ->
                val thumbnailUrl = if (video.site?.lowercase() == "youtube") {
                    "https://img.youtube.com/vi/${video.key}/mqdefault.jpg"
                } else null

                val isTv = LocalTvMode.current
                val videoCardFocusState = rememberTvFocusState(focusedScale = 1.05f)
                val isSynthwave = LocalIsSynthwave.current
                val isSoothing = LocalIsSoothingTheme.current
                val videoCardBorder = when {
                    isSynthwave -> {
                        androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                    }
                    isSoothing -> {
                        androidx.compose.foundation.BorderStroke(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                    else -> null
                }

                FadingItem {
                    Card(
                        modifier = focusModifier
                            .width(240.dp)
                            .aspectRatio(16f / 9f)
                            .ifElse(isTv, videoCardFocusState.focusModifier)
                            .ifElse(isTv, Modifier.tvFocusIndicator(videoCardFocusState, ShapeCache.smooth8))
                            .clickable {
                                onVideoClick(video)
                            },
                        shape = ShapeCache.smooth8,
                        border = videoCardBorder,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (thumbnailUrl != null) {
                                MediaImage(
                                    url = thumbnailUrl,
                                    contentDescription = video.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Tabler.Outline.PlayerPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                            ),
                                            startY = 100f
                                        )
                                    )
                            )

                            Text(
                                text = video.name ?: "Video",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Icon(
                                Tabler.Outline.PlayerPlay,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
        }
    }
}


