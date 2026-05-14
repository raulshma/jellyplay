package com.raulshma.jellyplay.feature.details

import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.tvFocusable

@Composable
fun MediaDetailScreen(
    itemId: String,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long, subtitleStreamIndex: Int?, audioStreamIndex: Int?) -> Unit,
    onAudioClick: (itemId: String) -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onPersonClick: (personId: String) -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    val detail by viewModel.detail
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val currentItem = detail?.item
    val targetBackdropId = if (currentItem?.mediaType == MediaType.EPISODE && currentItem.seriesId != null) {
        currentItem.seriesId!!
    } else {
        currentItem?.id ?: itemId
    }
    val backdropUrl = viewModel.getBackdropUrl(targetBackdropId)

    ArtworkThemeWrapper(
        imageUrl = backdropUrl,
        dynamicTheming = preferences.dynamicTheming,
    ) {
        val activeDownload by viewModel.getDownloadFlow(itemId).collectAsStateWithLifecycle(initialValue = null)

        // Seerr integration state
        val seerrRecommendations by viewModel.seerrRecommendations.collectAsStateWithLifecycle()
        val seerrSimilar by viewModel.seerrSimilar.collectAsStateWithLifecycle()
        val isSeerrConnected by viewModel.isSeerrConnected.collectAsStateWithLifecycle()
        val isSeerrRecommendationsEnabled by viewModel.isSeerrRecommendationsEnabled.collectAsStateWithLifecycle()
        val seerrRequestResult by viewModel.seerrRequestResult.collectAsStateWithLifecycle()
        val seerrRadarrServers by viewModel.radarrServers.collectAsStateWithLifecycle()
        val seerrSonarrServers by viewModel.sonarrServers.collectAsStateWithLifecycle()
        val seerrIsLoadingServices by viewModel.isLoadingSeerrServices.collectAsStateWithLifecycle()
        var seerrRequestItem by remember { mutableStateOf<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem?>(null) }

        // Seerr card loading state for prefetch animation
        val seerrLoadingState = rememberSeerrCardLoadingState()
        val seerrPrefetchCallback: com.raulshma.jellyplay.core.ui.components.SeerrPrefetchCallback = { tmdbId, mediaType, onDone ->
            seerrLoadingState.startLoading(tmdbId)
            viewModel.prefetchSeerrDetails(tmdbId, mediaType) {
                seerrLoadingState.stopLoading(tmdbId)
                onDone()
            }
        }

        androidx.compose.runtime.CompositionLocalProvider(
            com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch provides seerrPrefetchCallback,
            com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState provides seerrLoadingState,
        ) {
        DetailContent(
            itemId = itemId,
            detail = detail,
            seasons = viewModel.seasons,
            episodes = viewModel.episodes,
            fetchedSeasonIds = viewModel.fetchedSeasonIds,
            smartPlayTarget = viewModel.smartPlayTarget,
            selectedSubtitleIndex = viewModel.selectedSubtitleIndex,
            selectedAudioIndex = viewModel.selectedAudioIndex,
            getImageUrl = { viewModel.getImageUrl(it) },
            getBackdropUrl = { viewModel.getBackdropUrl(it) },
            isDownloading = viewModel.isDownloading,
            activeDownload = activeDownload,
            isLoading = isLoading,
            error = error,
            onRetry = { viewModel.loadItem(itemId) },
            onPlayClick = { playItemId, sourceId, start ->
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
            onToggleFavorite = { viewModel.toggleFavorite() },
            onMarkPlayed = { viewModel.markPlayed() },
            onMarkUnplayed = { viewModel.markUnplayed() },
            onSubtitleSelect = { viewModel.selectSubtitle(it) },
            onAudioSelect = { viewModel.selectAudio(it) },
            onItemClick = onItemClick,
            onPersonClick = onPersonClick,
            onNavigateToSeries = onNavigateToSeries,
            onBack = onBack,
            seerrRecommendations = seerrRecommendations,
            seerrSimilar = seerrSimilar,
            isSeerrConnected = isSeerrConnected,
            isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
            getSeerrPosterUrl = { viewModel.getSeerrPosterUrl(it) },
            onSeerrRequest = { seerrRequestItem = it },
            onNavigate = onNavigate,
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
}

@OptIn(ExperimentalMaterial3Api::class)
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
    activeDownload: com.raulshma.jellyplay.core.model.DownloadItem?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
    onAudioSelect: (Int?) -> Unit,
    onItemClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeries: (String) -> Unit,
    onBack: () -> Unit,
    seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    seerrSimilar: List<SeerrSearchItem> = emptyList(),
    isSeerrConnected: Boolean = false,
    isSeerrRecommendationsEnabled: Boolean = false,
    getSeerrPosterUrl: (String?) -> String? = { null },
    onSeerrRequest: (SeerrSearchItem) -> Unit = {},
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
) {
    val item = detail?.item
    val scrollState = rememberScrollState()
    val isAudio = item?.mediaType == MediaType.AUDIO || item?.mediaType == MediaType.MUSIC
    var showDownloadDialog by remember { mutableStateOf(false) }
    val artworkColors = LocalArtworkColors.current

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = com.raulshma.jellyplay.core.ui.tv.isTvDevice()

    val density = LocalDensity.current
    val backdropHeight = when {
        isTv -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Tv
        adaptiveInfo.isLandscape && isExpanded -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.LandscapeExpanded
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Expanded
        else -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight.Portrait
    }
    val collapsedHeight = with(density) { backdropHeight.toPx() }
    val scrollOffset by remember {
        derivedStateOf { scrollState.value.toFloat() }
    }
    val scrollFraction by remember {
        derivedStateOf {
            (scrollOffset / collapsedHeight).coerceIn(0f, 1f)
        }
    }

    val baseOverlayColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: MaterialTheme.colorScheme.background

    val targetBackgroundColor = lerp(baseOverlayColor, Color.Black, 0.65f)
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(600),
        label = "backgroundColor",
    )

    val navBarColor = LocalNavigationBarColor.current
    navBarColor.value = backgroundColor

    val contentVisible = detail != null && item != null
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "contentAlpha",
    )
    val appBarColor by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "appBarColor",
    )

    val animatedContainerColor = lerp(
        Color.Transparent,
        backgroundColor.copy(alpha = 0.95f),
        appBarColor,
    )

    val animatedIconColor = lerp(
        Color.White,
        Color.White,
        appBarColor,
    )

    val animatedTitleAlpha by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "titleAlpha",
    )

    val targetBackdropId = if (item?.mediaType == MediaType.EPISODE && item.seriesId != null) {
        item.seriesId!!
    } else {
        itemId
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
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
                        animationSpec = tween(460, easing = FastOutSlowInEasing),
                    ) + scaleIn(
                        initialScale = 1.035f,
                        animationSpec = tween(620, easing = FastOutSlowInEasing),
                    ) togetherWith fadeOut(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ) + scaleOut(
                        targetScale = 0.99f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
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
                            startY = if (isLandscapeExpanded) 0f else with(density) { (backdropHeight - 200.dp).toPx() },
                            endY = with(density) { backdropHeight.toPx() }
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(backdropHeight - 150.dp))

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
                            enter = fadeIn(
                                animationSpec = tween(420, delayMillis = 80, easing = FastOutSlowInEasing),
                            ) + slideInVertically(
                                initialOffsetY = { it / 8 },
                                animationSpec = tween(420, delayMillis = 80, easing = FastOutSlowInEasing),
                            ) + scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(420, delayMillis = 80, easing = FastOutSlowInEasing),
                            ),
                            exit = fadeOut(tween(160)) + scaleOut(
                                targetScale = 0.98f,
                                animationSpec = tween(160, easing = FastOutSlowInEasing),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.width(220.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MediaImage(
                                    url = getImageUrl(itemId),
                                    contentDescription = null,
                                    blurHash = item?.blurHashes?.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .graphicsLayer { alpha = contentAlpha },
                                    contentScale = ContentScale.Crop,
                                )
                                if (detail != null && item != null) {
                                    DetailActionButtons(
                                        item = item,
                                        detail = detail,
                                        seasons = seasons,
                                        fetchedSeasonIds = fetchedSeasonIds,
                                        smartPlayTarget = smartPlayTarget,
                                        isAudio = isAudio,
                                        isDownloading = isDownloading,
                                        activeDownload = activeDownload,
                                        onPlayClick = onPlayClick,
                                        onAudioClick = onAudioClick,
                                        onDownloadClick = { showDownloadDialog = true },
                                        onToggleFavorite = onToggleFavorite,
                                        onMarkPlayed = onMarkPlayed,
                                        onMarkUnplayed = onMarkUnplayed,
                                        vertical = true,
                                    )
                                }
                            }
                        }

                        // Right column: metadata content (no action buttons)
                        if (detail != null && item != null) {
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = fadeIn(tween(400, delayMillis = 100)) +
                                        slideInVertically(
                                            initialOffsetY = { it / 12 },
                                            animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing),
                                        ),
                                exit = fadeOut(tween(180)) + slideOutVertically(
                                    targetOffsetY = { -it / 24 },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                ),
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
                                    isDownloading = isDownloading,
                                    activeDownload = activeDownload,
                                    onPlayClick = onPlayClick,
                                    onAudioClick = onAudioClick,
                                    onDownloadClick = { showDownloadDialog = true },
                                    onToggleFavorite = onToggleFavorite,
                                    onMarkPlayed = onMarkPlayed,
                                    onMarkUnplayed = onMarkUnplayed,
                                    onSubtitleSelect = onSubtitleSelect,
                                    onAudioSelect = onAudioSelect,
                                    onItemClick = onItemClick,
                                    onPersonClick = onPersonClick,
                                    onNavigateToSeries = onNavigateToSeries,
                                    modifier = Modifier,
                                    contentAlignment = Alignment.TopStart,
                                    showActionButtons = false,
                                    showMediaInfo = true,
                                    seerrRecommendations = seerrRecommendations,
                                    seerrSimilar = seerrSimilar,
                                    isSeerrConnected = isSeerrConnected,
                                    isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
                                    getSeerrPosterUrl = getSeerrPosterUrl,
                                    onSeerrRequest = onSeerrRequest,
                                    onNavigate = onNavigate,
                                )
                            }
                        } else if (isLoading) {
                            Box(modifier = Modifier.weight(1f)) {
                                SkeletonDetailBody()
                            }
                        } else if (error != null) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onRetry) { Text("Retry") }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(top = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = adaptiveInfo.contentPadding(isTv))
                                    .offset(y = (-40).dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                AnimatedVisibility(
                                    visible = contentVisible,
                                    enter = fadeIn(
                                        animationSpec = tween(420, delayMillis = 80, easing = FastOutSlowInEasing),
                                    ) + slideInVertically(
                                        initialOffsetY = { it / 8 },
                                        animationSpec = tween(420, delayMillis = 80, easing = FastOutSlowInEasing),
                                    ) + scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = tween(420, delayMillis = 80, easing = FastOutSlowInEasing),
                                    ),
                                    exit = fadeOut(tween(160)) + scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = tween(160, easing = FastOutSlowInEasing),
                                    ),
                                ) {
                                    val posterWidth = when {
                                        isTv -> 160.dp
                                        isExpanded -> 140.dp
                                        else -> 120.dp
                                    }
                                    MediaImage(
                                        url = getImageUrl(itemId),
                                        contentDescription = null,
                                        blurHash = item?.blurHashes?.primary,
                                        modifier = Modifier
                                            .width(posterWidth)
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .graphicsLayer { alpha = contentAlpha },
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (detail != null && item != null) {
                            AnimatedVisibility(
                                visible = contentVisible,
                                enter = fadeIn(tween(400, delayMillis = 100)) +
                                        slideInVertically(
                                            initialOffsetY = { it / 12 },
                                            animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing),
                                        ),
                                exit = fadeOut(tween(180)) + slideOutVertically(
                                    targetOffsetY = { -it / 24 },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                ),
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
                                    isDownloading = isDownloading,
                                    activeDownload = activeDownload,
                                    onPlayClick = onPlayClick,
                                    onAudioClick = onAudioClick,
                                    onDownloadClick = { showDownloadDialog = true },
                                    onToggleFavorite = onToggleFavorite,
                                    onMarkPlayed = onMarkPlayed,
                                    onMarkUnplayed = onMarkUnplayed,
                                    onSubtitleSelect = onSubtitleSelect,
                                    onAudioSelect = onAudioSelect,
                                    onItemClick = onItemClick,
                                    onPersonClick = onPersonClick,
                                    onNavigateToSeries = onNavigateToSeries,
                                    seerrRecommendations = seerrRecommendations,
                                    seerrSimilar = seerrSimilar,
                                    isSeerrConnected = isSeerrConnected,
                                    isSeerrRecommendationsEnabled = isSeerrRecommendationsEnabled,
                                    getSeerrPosterUrl = getSeerrPosterUrl,
                                    onSeerrRequest = onSeerrRequest,
                                    onNavigate = onNavigate,
                                )
                            }
                        } else if (isLoading) {
                            SkeletonDetailBody()
                        } else if (error != null) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onRetry) { Text("Retry") }
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
            TopAppBar(
                title = {
                    Text(
                        text = item?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = animatedTitleAlpha),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (appBarColor < 0.5f) Color.Black.copy(alpha = 0.3f) else Color.Transparent
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = animatedIconColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }

    if (showDownloadDialog) {
        val source = detail?.mediaSources?.firstOrNull()
        val fileSize = source?.size
        val context = LocalContext.current
        val availableBytes = remember {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
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
    var shouldShow by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        shouldShow = visible
    }

    AnimatedVisibility(
        visible = visible && shouldShow,
        enter = fadeIn(
            animationSpec = tween(340, delayMillis = delayIndex * 70, easing = FastOutSlowInEasing),
        ) + slideInVertically(
            initialOffsetY = { it / 14 },
            animationSpec = tween(340, delayMillis = delayIndex * 70, easing = FastOutSlowInEasing),
        ) + scaleIn(
            initialScale = 0.985f,
            animationSpec = tween(340, delayMillis = delayIndex * 70, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(160)) + slideOutVertically(
            targetOffsetY = { -it / 24 },
            animationSpec = tween(180, easing = FastOutSlowInEasing),
        ) + scaleOut(
            targetScale = 0.99f,
            animationSpec = tween(180, easing = FastOutSlowInEasing),
        ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaInfoSection(
    mediaStreams: List<MediaStream>,
    selectedAudioIndex: Int?,
    selectedSubtitleIndex: Int?,
    onAudioSelect: (Int?) -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
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
        val selectedAudio = audioStreams.firstOrNull { it.index == selectedAudioIndex } ?: defaultAudio
        val defaultSubtitle = subtitleStreams.firstOrNull { it.isDefault }
            ?: subtitleStreams.firstOrNull { it.index == selectedSubtitleIndex }
            ?: subtitleStreams.firstOrNull()
        val selectedSubtitle = subtitleStreams.firstOrNull { it.index == selectedSubtitleIndex } ?: defaultSubtitle

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

            QuickInfoPill(
                icon = Icons.Default.HighQuality,
                text = qualityLabel,
                modifier = Modifier.weight(1f),
            )

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

            QuickInfoPill(
                icon = Icons.Default.GraphicEq,
                text = audioLabel,
                showTrailingIndicator = true,
                onClick = { if (audioStreams.isNotEmpty()) picker = StreamPickerType.AUDIO },
                containerColor = chipBackgroundColor,
                modifier = Modifier.weight(1f),
            )

            val subtitleLabel = selectedSubtitle
                ?.displayTitle
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.length > 10) it.take(10) + "…" else it }
                ?: selectedSubtitle?.language?.uppercase()?.take(3)
                ?: "OFF"

            QuickInfoPill(
                icon = Icons.Default.ClosedCaption,
                text = subtitleLabel,
                showTrailingIndicator = subtitleStreams.isNotEmpty(),
                onClick = { picker = StreamPickerType.SUBTITLE },
                containerColor = chipBackgroundColor,
                modifier = Modifier.weight(1f),
            )
        }

        if (picker != null) {
            val activePicker = picker ?: return@Column
            val options = when (activePicker) {
                StreamPickerType.AUDIO -> {
                    listOf(StreamPickerOption(index = null, label = "Default", isDefault = true)) +
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
                }
                StreamPickerType.SUBTITLE -> {
                    listOf(StreamPickerOption(index = null, label = "Off", isDefault = true)) +
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
                }
            }

            val selectedIndex = when (activePicker) {
                StreamPickerType.AUDIO -> selectedAudioIndex
                StreamPickerType.SUBTITLE -> selectedSubtitleIndex
            }

            ModalBottomSheet(
                onDismissRequest = { picker = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = chipBackgroundColor,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (activePicker == StreamPickerType.AUDIO) "Select Audio" else "Select Subtitle",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(options, key = { "${activePicker}_${it.index}_${it.label}" }) { option ->
                            val isSelected = option.index == selectedIndex
                            val optionInteractionSource = remember { MutableInteractionSource() }
                            val isOptionPressed by optionInteractionSource.collectIsPressedAsState()
                            val optionScale by animateFloatAsState(
                                targetValue = if (isOptionPressed) 0.97f else 1f,
                                animationSpec = spring(stiffness = 400f),
                                label = "optionScale",
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .graphicsLayer {
                                        scaleX = optionScale
                                        scaleY = optionScale
                                    }
                                    .tvFocusable().clickable(
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
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                if (option.isDefault && option.index != null) {
                                    Text(
                                        text = "DEFAULT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .then(if (onClick != null) Modifier.tvFocusable().clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .tvFocusable(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (showTrailingIndicator) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
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
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.15f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) MaterialTheme.colorScheme.primary else Color.White,
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
        else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(200),
        label = "subtitleChipBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "subtitleChipContent",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .tvFocusable().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .tvFocusable(),
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
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailViewModel.SmartPlayTarget?,
    isAudio: Boolean,
    isDownloading: Boolean,
    activeDownload: com.raulshma.jellyplay.core.model.DownloadItem?,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val isSeriesOrEpisode = item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE
    val isSeries = item.mediaType == MediaType.SERIES
    val target = if (isSeriesOrEpisode) smartPlayTarget else null
    val hasProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0
    val isResolvingSeriesTarget = isSeries &&
        target == null &&
        (seasons.isEmpty() || fetchedSeasonIds.size < seasons.size)
    val canPlayPrimary = isAudio || !isSeries || target != null
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
        isSeries -> "No Episodes"
        hasProgress -> "Resume"
        else -> "Play"
    }

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "playButtonScale",
    )
    val markInteractionSource = remember { MutableInteractionSource() }
    val isMarkPressed by markInteractionSource.collectIsPressedAsState()
    val markScale by animateFloatAsState(
        targetValue = if (isMarkPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "markButtonScale",
    )
    val favoriteInteractionSource = remember { MutableInteractionSource() }
    val isFavoritePressed by favoriteInteractionSource.collectIsPressedAsState()
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavoritePressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "favoriteButtonScale",
    )
    val downloadInteractionSource = remember { MutableInteractionSource() }
    val isDownloadPressed by downloadInteractionSource.collectIsPressedAsState()
    val downloadScale by animateFloatAsState(
        targetValue = if (isDownloadPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "downloadButtonScale",
    )

    val downloadStatus = activeDownload?.status
    val isDownloadActive = downloadStatus == DownloadStatus.PENDING ||
            downloadStatus == DownloadStatus.DOWNLOADING ||
            downloadStatus == DownloadStatus.PAUSED
    val isDownloadCompleted = downloadStatus == DownloadStatus.COMPLETED
    val downloadProgress = if (activeDownload != null && activeDownload.totalSizeBytes > 0) {
        activeDownload.downloadedBytes.toFloat() / activeDownload.totalSizeBytes
    } else 0f

    // Play button — full width in vertical mode, fixed width in horizontal
    val playButton: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (canPlayPrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
                .graphicsLayer { scaleX = playScale; scaleY = playScale }
                .tvFocusable().clickable(
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
                }
                .tvFocusable(),
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .align(Alignment.CenterStart)
                        .background(Color.White.copy(alpha = 0.15f))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
                Spacer(Modifier.size(6.dp))
                Text(playLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
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
            playButton()
            Row(
                modifier = iconsModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { if (item.isPlayed) onMarkUnplayed() else onMarkPlayed() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = markScale; scaleY = markScale }
                        .tvFocusable().clickable(interactionSource = markInteractionSource, indication = null) {
                            if (item.isPlayed) onMarkUnplayed() else onMarkPlayed()
                        }
                        .tvFocusable()
                ) {
                    Icon(
                        if (item.isPlayed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (item.isPlayed) "Mark as Unwatched" else "Mark as Watched",
                        tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = favoriteScale; scaleY = favoriteScale }
                        .tvFocusable().clickable(interactionSource = favoriteInteractionSource, indication = null) { onToggleFavorite() }
                        .tvFocusable()
                ) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                if (!isAudio && detail.mediaSources.isNotEmpty()) {
                    IconButton(
                        onClick = onDownloadClick,
                        enabled = !isDownloading && !isDownloadActive && !isDownloadCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .graphicsLayer { scaleX = downloadScale; scaleY = downloadScale }
                            .tvFocusable().clickable(interactionSource = downloadInteractionSource, indication = null) { onDownloadClick() }
                            .tvFocusable()
                    ) {
                        if (isDownloading || isDownloadActive) {
                            if (downloadProgress > 0f && downloadStatus == DownloadStatus.DOWNLOADING) {
                                CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        } else if (isDownloadCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                        }
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
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canPlayPrimary) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    )
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .tvFocusable().clickable(
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
                    }
                    .tvFocusable(),
                contentAlignment = Alignment.Center,
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .align(Alignment.CenterStart)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text(playLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }

            IconButton(
                onClick = { if (item.isPlayed) onMarkUnplayed() else onMarkPlayed() },
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .graphicsLayer { scaleX = markScale; scaleY = markScale }
                    .tvFocusable().clickable(interactionSource = markInteractionSource, indication = null) {
                        if (item.isPlayed) onMarkUnplayed() else onMarkPlayed()
                    }
                    .tvFocusable()
            ) {
                Icon(
                    if (item.isPlayed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (item.isPlayed) "Mark as Unwatched" else "Mark as Watched",
                    tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else Color.White,
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .graphicsLayer { scaleX = favoriteScale; scaleY = favoriteScale }
                    .tvFocusable().clickable(interactionSource = favoriteInteractionSource, indication = null) { onToggleFavorite() }
                    .tvFocusable()
            ) {
                Icon(
                    if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                )
            }

            if (!isAudio && detail.mediaSources.isNotEmpty()) {
                val dlStatus = activeDownload?.status
                val dlActive = dlStatus == DownloadStatus.PENDING ||
                        dlStatus == DownloadStatus.DOWNLOADING ||
                        dlStatus == DownloadStatus.PAUSED
                val dlCompleted = dlStatus == DownloadStatus.COMPLETED
                val dlProgress = if (activeDownload != null && activeDownload.totalSizeBytes > 0) {
                    activeDownload.downloadedBytes.toFloat() / activeDownload.totalSizeBytes
                } else 0f

                IconButton(
                    onClick = onDownloadClick,
                    enabled = !isDownloading && !dlActive && !dlCompleted,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = downloadScale; scaleY = downloadScale }
                        .tvFocusable().clickable(interactionSource = downloadInteractionSource, indication = null) { onDownloadClick() }
                        .tvFocusable()
                ) {
                    if (isDownloading || dlActive) {
                        if (dlProgress > 0f && dlStatus == DownloadStatus.DOWNLOADING) {
                            CircularProgressIndicator(progress = { dlProgress }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (dlCompleted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    }
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
    isDownloading: Boolean,
    activeDownload: com.raulshma.jellyplay.core.model.DownloadItem?,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onSubtitleSelect: (Int?) -> Unit,
    onAudioSelect: (Int?) -> Unit,
    onItemClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeries: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    showActionButtons: Boolean = true,
    showMediaInfo: Boolean = true,
    seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    seerrSimilar: List<SeerrSearchItem> = emptyList(),
    isSeerrConnected: Boolean = false,
    isSeerrRecommendationsEnabled: Boolean = false,
    getSeerrPosterUrl: (String?) -> String? = { null },
    onSeerrRequest: (SeerrSearchItem) -> Unit = {},
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
) {
    val showContent = true

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = com.raulshma.jellyplay.core.ui.tv.isTvDevice()
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .tvFocusable().clickable { onNavigateToSeries(item.seriesId!!) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.seriesName ?: "Series",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.seasonName?.let { season ->
                            Text(
                                text = " › ",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                            Text(
                                text = season,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                        Text(
                            text = episodeContext,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )

                item.originalTitle
                    ?.takeIf { it.isNotBlank() && !it.equals(item.name, ignoreCase = true) }
                    ?.let { originalTitle ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = originalTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.year?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    item.runTimeTicks?.let { ticks ->
                        val minutes = ticks / 600_000_000
                        Text(
                            text = "${minutes}m",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    item.officialRating?.let {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                    item.communityRating?.let { rating ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                item.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                    Spacer(Modifier.height(14.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(genres, key = { it }, contentType = { "genre" }) { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .tvFocusable()
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White.copy(alpha = 0.95f),
                                )
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
                fetchedSeasonIds = fetchedSeasonIds,
                smartPlayTarget = smartPlayTarget,
                isAudio = isAudio,
                isDownloading = isDownloading,
                activeDownload = activeDownload,
                onPlayClick = onPlayClick,
                onAudioClick = onAudioClick,
                onDownloadClick = onDownloadClick,
                onToggleFavorite = onToggleFavorite,
                onMarkPlayed = onMarkPlayed,
                onMarkUnplayed = onMarkUnplayed,
                vertical = false,
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
                )
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 3) { }

        StaggeredDetailSection(visible = showContent, delayIndex = 4) {
            item.overview?.let { overview ->
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 5) {
            val showSeasons = (item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE) && seasons.isNotEmpty()
            if (showSeasons) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    SeasonsSection(
                        seriesItem = item,
                        seasons = seasons,
                        episodes = episodes,
                        fetchedSeasonIds = fetchedSeasonIds,
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
                        }
                    )
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 6) {
            if (detail.people.isNotEmpty()) {
                Column {
                    Text(
                        text = "Cast & Crew",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(detail.people, key = { "person_${it.id}" }, contentType = { "person" }) { person ->
                                PersonItem(
                                    person = person,
                                    imageUrl = getImageUrl(person.id),
                                    onClick = { onPersonClick(person.id) },
                                )
                }
            }

        }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 7) {
            if (detail.relatedItems.isNotEmpty()) {
                Column {
                    Text(
                        text = "More Like This",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(detail.relatedItems, key = { "related_${it.id}" }, contentType = { "mediaItem" }) { related ->
                            val adaptiveInfo = LocalAdaptiveInfo.current
                            PosterCard(
                                item = related,
                                imageUrl = getImageUrl(related.id),
                                onClick = { onItemClick(related.id) },
                                modifier = Modifier.width(
                                    if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                                ),
                            )
                        }
                    }
                }
            }
        }

        // ── Seerr Recommendations Section ──
        if (isSeerrConnected && isSeerrRecommendationsEnabled && seerrRecommendations.isNotEmpty()) {
            StaggeredDetailSection(visible = showContent, delayIndex = 8) {
                Column {
                    Text(
                        text = "Seerr Recommendations",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = seerrRecommendations.size,
                            key = { index -> "seerr_rec_${seerrRecommendations[index].id}" },
                            contentType = { "seerrRecItem" },
                        ) { index ->
                            val seerrItem = seerrRecommendations[index]
                            val posterUrl = getSeerrPosterUrl(seerrItem.posterPath)
                            val adaptiveInfo = LocalAdaptiveInfo.current
                            val loadingState = com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState.current
                            val prefetch = com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch.current
                            SeerrMediaCard(
                                item = seerrItem,
                                imageUrl = posterUrl,
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
                                modifier = Modifier.width(
                                    if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                                ),
                            )
                        }
                    }
                }
            }
        }

        // ── Seerr Similar Section ──
        if (isSeerrConnected && isSeerrRecommendationsEnabled && seerrSimilar.isNotEmpty()) {
            StaggeredDetailSection(visible = showContent, delayIndex = 9) {
                Column {
                    Text(
                        text = "Seerr Similar",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = seerrSimilar.size,
                            key = { index -> "seerr_sim_${seerrSimilar[index].id}" },
                            contentType = { "seerrSimItem" },
                        ) { index ->
                            val seerrItem = seerrSimilar[index]
                            val posterUrl = getSeerrPosterUrl(seerrItem.posterPath)
                            val adaptiveInfo = LocalAdaptiveInfo.current
                            val loadingState = com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState.current
                            val prefetch = com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch.current
                            SeerrMediaCard(
                                item = seerrItem,
                                imageUrl = posterUrl,
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
                                modifier = Modifier.width(
                                    if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SeasonsSection(
    seriesItem: MediaItem,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    getImageUrl: (String) -> String,
    currentItemId: String? = null,
    currentSeasonId: String? = null,
    onEpisodePlayClick: (MediaItem) -> Unit,
    onEpisodeDetailClick: (MediaItem) -> Unit,
) {
    val initialSeasonIndex = if (currentSeasonId != null) {
        seasons.indexOfFirst { it.id == currentSeasonId }.coerceAtLeast(0)
    } else 0
    var selectedSeasonIndex by remember { mutableStateOf(initialSeasonIndex) }

    Column {
        Text(
            text = "Seasons",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 24.dp),
            color = Color.White
        )

        Spacer(Modifier.height(16.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seasons, key = { it.id }, contentType = { "season" }) { season ->
                val index = seasons.indexOf(season)
                val isSelected = index == selectedSeasonIndex
                val targetColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f)
                val targetContentColor = if (isSelected) Color.Black else Color.White
                val surfaceColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(250),
                    label = "seasonColor",
                )
                val contentColor by animateColorAsState(
                    targetValue = targetContentColor,
                    animationSpec = tween(250),
                    label = "seasonContentColor",
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .tvFocusable().clickable { selectedSeasonIndex = index },
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

        AnimatedContent(
            targetState = selectedSeasonIndex to (seasonEpisodes?.size ?: 0),
            transitionSpec = {
                val direction = if (targetState.first >= initialState.first) 1 else -1
                fadeIn(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + slideInHorizontally(
                    initialOffsetX = { direction * it / 10 },
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                ) togetherWith fadeOut(
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                ) + slideOutHorizontally(
                    targetOffsetX = { -direction * it / 12 },
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                )
            },
            label = "seasonEpisodes",
        ) { (seasonIdx, episodeCount) ->
            val currentEpisodes = seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
            val currentIsFetched = seasons.getOrNull(seasonIdx)?.id?.let { fetchedSeasonIds.contains(it) } ?: false
            val currentIsLoading = currentEpisodes == null && seasons.getOrNull(seasonIdx) != null && !currentIsFetched

            when {
                currentIsLoading -> {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        userScrollEnabled = false,
                    ) {
                        items(3, contentType = { "shimmer" }) {
                            EpisodeCardSkeleton()
                        }
                    }
                }
                currentEpisodes != null && currentEpisodes.isNotEmpty() -> {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(currentEpisodes, key = { "episode_${it.id}" }, contentType = { "episode" }) { episode ->
                            EpisodeCard(
                                episode = episode,
                                getImageUrl = getImageUrl,
                                isCurrentEpisode = episode.id == currentItemId,
                                onPlayClick = { onEpisodePlayClick(episode) },
                                onDetailClick = { onEpisodeDetailClick(episode) },
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
                        Text("No episodes available", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    val shimmerColor = Color.White.copy(alpha = shimmerAlpha)

    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(shimmerColor)
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: MediaItem,
    getImageUrl: (String) -> String,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "episodeCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "episodePlayScale",
    )

    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrentEpisode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.05f)
            )
            .then(
                if (isCurrentEpisode) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                else Modifier
            )
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .tvFocusable().clickable(
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .tvFocusable().clickable(
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
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = buildString {
                    episode.indexNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.runTimeTicks != null) {
                Text(
                    text = "${episode.runTimeTicks!! / 600_000_000}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
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
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "personScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusable().clickable(
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
fun SkeletonDetailBody() {
    Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.height(40.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)))
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.height(20.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f)))
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.height(100.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)))
    }
}
