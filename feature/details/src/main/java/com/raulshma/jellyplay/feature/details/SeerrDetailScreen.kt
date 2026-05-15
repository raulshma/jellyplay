package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.*
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import java.text.NumberFormat
import java.util.*

@Composable
fun SeerrDetailScreen(
    tmdbId: Int,
    mediaType: String,
    onBack: () -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
    viewModel: SeerrDetailViewModel = hiltViewModel(),
) {
    val movieDetail by viewModel.movieDetails
    val tvDetail by viewModel.tvDetails
    val ratings by viewModel.ratings
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val seerrRecommendations by viewModel.seerrRecommendations.collectAsStateWithLifecycle()
    val seerrSimilar by viewModel.seerrSimilar.collectAsStateWithLifecycle()
    val requestResult by viewModel.requestResult.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val radarrServers by viewModel.radarrServers.collectAsStateWithLifecycle()
    val sonarrServers by viewModel.sonarrServers.collectAsStateWithLifecycle()
    val isLoadingServices by viewModel.isLoadingServices.collectAsStateWithLifecycle()

    LaunchedEffect(tmdbId, mediaType) {
        viewModel.loadDetails(tmdbId, mediaType)
    }

    val backdropPath = movieDetail?.backdropPath ?: tvDetail?.backdropPath
    val backdropUrl = viewModel.getSeerrBackdropUrl(backdropPath)

    ArtworkThemeWrapper(
        imageUrl = backdropUrl ?: "",
        dynamicTheming = preferences.dynamicTheming,
    ) {
        var showRequestDialog by remember { mutableStateOf(false) }
        val seerrLoadingState = rememberSeerrCardLoadingState()
        val prefetchCallback: com.raulshma.jellyplay.core.ui.components.SeerrPrefetchCallback = { tmdbId, mediaType, onDone ->
            seerrLoadingState.startLoading(tmdbId)
            viewModel.prefetchRelatedDetails(tmdbId, mediaType) {
                seerrLoadingState.stopLoading(tmdbId)
                onDone()
            }
        }

        androidx.compose.runtime.CompositionLocalProvider(
            com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch provides prefetchCallback,
            com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState provides seerrLoadingState,
        ) {

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && movieDetail == null && tvDetail == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null && movieDetail == null && tvDetail == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Error: $error", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.loadDetails(tmdbId, mediaType) }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    SeerrDetailContent(
                        movieDetail = movieDetail,
                        tvDetail = tvDetail,
                        ratings = ratings,
                        recommendations = seerrRecommendations,
                        similar = seerrSimilar,
                        getPosterUrl = { viewModel.getSeerrPosterUrl(it) },
                        getBackdropUrl = { viewModel.getSeerrBackdropUrl(it) },
                        onRequestClick = { showRequestDialog = true },
                        onNavigate = onNavigate,
                        onBack = onBack
                    )
                }
            }

            if (showRequestDialog) {
                val item = movieDetail?.let {
                    SeerrSearchItem(
                        id = it.id,
                        mediaType = "movie",
                        title = it.title,
                        overview = it.overview,
                        posterPath = it.posterPath,
                        releaseDate = it.releaseDate,
                        mediaInfo = it.mediaInfo
                    )
                } ?: tvDetail?.let {
                    SeerrSearchItem(
                        id = it.id,
                        mediaType = "tv",
                        name = it.name,
                        overview = it.overview,
                        posterPath = it.posterPath,
                        firstAirDate = it.firstAirDate,
                        mediaInfo = it.mediaInfo
                    )
                }

                item?.let {
                    // Fetch service details when dialog opens
                    LaunchedEffect(Unit) {
                        viewModel.loadServiceDetails(it.mediaType)
                    }

                    val tvSeasons = tvDetail?.seasons?.filter { season -> season.seasonNumber > 0 } ?: emptyList()

                    SeerrRequestDialog(
                        item = it,
                        radarrServers = radarrServers,
                        sonarrServers = sonarrServers,
                        seasons = if (it.mediaType.equals("tv", ignoreCase = true)) tvSeasons else emptyList(),
                        isLoadingServices = isLoadingServices,
                        isRequesting = requestResult?.isLoading == true,
                        requestSuccess = requestResult?.success,
                        requestError = requestResult?.error,
                        onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                            viewModel.requestMedia(it, seasons, serverId, profileId, rootFolder, tags)
                        },
                        onDismiss = {
                            showRequestDialog = false
                            viewModel.clearRequestResult()
                        }
                    )
                }
            }
        }
        } // CompositionLocalProvider
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeerrDetailContent(
    movieDetail: SeerrMovieDetails?,
    tvDetail: SeerrTvDetails?,
    ratings: SeerrRatings?,
    recommendations: List<SeerrSearchItem>,
    similar: List<SeerrSearchItem>,
    getPosterUrl: (String?) -> String?,
    getBackdropUrl: (String?) -> String?,
    onRequestClick: () -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = isTvDevice()
    val density = LocalDensity.current
    val artworkColors = LocalArtworkColors.current

    val backdropPath = movieDetail?.backdropPath ?: tvDetail?.backdropPath
    val title = movieDetail?.title ?: tvDetail?.name ?: ""
    val posterPath = movieDetail?.posterPath ?: tvDetail?.posterPath

    val backdropHeight = when {
        isTv -> AdaptiveBackdropHeight.Tv
        adaptiveInfo.isLandscape && isExpanded -> AdaptiveBackdropHeight.LandscapeExpanded
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> AdaptiveBackdropHeight.Expanded
        else -> AdaptiveBackdropHeight.Portrait
    }
    val collapsedHeight = with(density) { backdropHeight.toPx() }
    val scrollOffset by remember { derivedStateOf { scrollState.value.toFloat() } }
    val scrollFraction by remember { derivedStateOf { (scrollOffset / collapsedHeight).coerceIn(0f, 1f) } }

    val baseOverlayColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: MaterialTheme.colorScheme.background

    val targetBackgroundColor = lerp(baseOverlayColor, Color.Black, 0.65f)
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(600, easing = FancyTransitionEasing),
        label = "backgroundColor",
    )

    val navBarColor = LocalNavigationBarColor.current
    navBarColor.value = backgroundColor

    val appBarColor by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = AlphaEasing),
        label = "appBarColor",
    )

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
            if (backdropPath != null) {
                MediaImage(
                    url = getBackdropUrl(backdropPath) ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f + (scrollOffset * 0.001f).coerceAtLeast(0f)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentScale = ContentScale.Crop
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
                        Column(
                            modifier = Modifier.width(240.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                            ) {
                                MediaImage(
                                    url = getPosterUrl(posterPath) ?: "",
                                    contentDescription = title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            MediaInfoCondensed(
                                movieDetail = movieDetail,
                                tvDetail = tvDetail,
                                ratings = ratings,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(24.dp))

                            SeerrActionButtons(
                                movieDetail = movieDetail,
                                tvDetail = tvDetail,
                                onRequestClick = onRequestClick,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(24.dp))

                            ExternalLinksRow(
                                tmdbId = movieDetail?.id ?: tvDetail?.id ?: 0,
                                imdbId = movieDetail?.imdbId ?: tvDetail?.externalIds?.imdbId,
                                tvdbId = tvDetail?.externalIds?.tvdbId,
                                mediaType = if (movieDetail != null) "movie" else "tv"
                            )
                        }

                        SeerrDetailBody(
                            movieDetail = movieDetail,
                            tvDetail = tvDetail,
                            ratings = ratings,
                            recommendations = recommendations,
                            similar = similar,
                            getPosterUrl = getPosterUrl,
                            onNavigate = onNavigate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .offset(y = (-40).dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Card(
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(2f / 3f),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                MediaImage(
                                    url = getPosterUrl(posterPath) ?: "",
                                    contentDescription = title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    
                                    val contentRating = tvDetail?.contentRatings?.results?.find { it.iso31661 == "US" }?.rating
                                    if (contentRating != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = Color.Transparent,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = contentRating,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                val tagline = movieDetail?.tagline ?: tvDetail?.tagline
                                tagline?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        MediaInfoCondensed(
                            movieDetail = movieDetail,
                            tvDetail = tvDetail,
                            ratings = ratings,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(24.dp))

                        SeerrActionButtons(
                            movieDetail = movieDetail,
                            tvDetail = tvDetail,
                            onRequestClick = onRequestClick,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(32.dp))

                        SeerrDetailBody(
                            movieDetail = movieDetail,
                            tvDetail = tvDetail,
                            ratings = ratings,
                            recommendations = recommendations,
                            similar = similar,
                            getPosterUrl = getPosterUrl,
                            onNavigate = onNavigate,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Top Bar
        val animatedContainerColor = lerp(
            Color.Transparent,
            backgroundColor.copy(alpha = 0.95f),
            appBarColor,
        )

        TopAppBar(
            title = {
                AnimatedVisibility(
                    visible = scrollFraction > 0.7f,
                    enter = fadeIn(tween(300, easing = AlphaEasing)) + slideInVertically(tween(300, easing = FancyTransitionEasing)) { it / 2 },
                    exit = fadeOut(tween(300, easing = AlphaEasing)) + slideOutVertically(tween(300, easing = FancyTransitionEasing)) { it / 2 }
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = animatedContainerColor,
                navigationIconContentColor = Color.White,
                titleContentColor = Color.White
            )
        )
    }
}

@Composable
private fun SeerrActionButtons(
    movieDetail: SeerrMovieDetails?,
    tvDetail: SeerrTvDetails?,
    onRequestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaInfo = movieDetail?.mediaInfo ?: tvDetail?.mediaInfo
    val status = mediaInfo?.status ?: 0
    val isAvailable = status == 5 || status == 4 // Available or Partially Available

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isAvailable) {
            Button(
                onClick = onRequestClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Request", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { /* Could navigate to the item in library if we had the ID mapping */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 12.dp),
                enabled = false
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Available", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SeerrDetailBody(
    movieDetail: SeerrMovieDetails?,
    tvDetail: SeerrTvDetails?,
    ratings: SeerrRatings?,
    recommendations: List<SeerrSearchItem>,
    similar: List<SeerrSearchItem>,
    getPosterUrl: (String?) -> String?,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = isTvDevice()
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val maxWidth = adaptiveInfo.detailBodyMaxWidth(isTv)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Overview Section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val overview = movieDetail?.overview ?: tvDetail?.overview ?: ""
                if (overview.isNotBlank()) {
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 24.sp
                    )
                }
                
                val keywords = movieDetail?.keywords ?: tvDetail?.keywords ?: emptyList()
                if (keywords.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keywords.take(10).forEach { keyword ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(keyword.name, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    labelColor = Color.White.copy(alpha = 0.7f)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }

            // Watch Providers
            val watchProviders = movieDetail?.watchProviders ?: tvDetail?.watchProviders ?: emptyList()
            val usProviders = watchProviders.find { it.iso31661 == "US" }
            if (usProviders != null && (usProviders.flatrate.isNotEmpty() || usProviders.buy.isNotEmpty())) {
                WatchProvidersSection(usProviders, getPosterUrl)
            }

            if (isExpanded) {
                // Two column layout for Cast/Videos and Media Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        // Cast
                        val cast = tvDetail?.aggregateCredits?.cast ?: movieDetail?.credits?.cast ?: emptyList()
                        if (cast.isNotEmpty()) {
                            CastSection(cast, getPosterUrl)
                        }

                        // Seasons (TV only)
                        if (tvDetail != null && tvDetail.seasons.isNotEmpty()) {
                            SeasonsSection(tvDetail.seasons, getPosterUrl)
                        }

                        // Videos
                        val videos = movieDetail?.relatedVideos ?: tvDetail?.relatedVideos ?: emptyList()
                        if (videos.isNotEmpty()) {
                            VideosSection(videos)
                        }
                    }

                    Column(
                        modifier = Modifier.width(300.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        MediaInformationSection(movieDetail, tvDetail)
                    }
                }
            } else {
                // Stacked layout for compact screens
                val cast = tvDetail?.aggregateCredits?.cast ?: movieDetail?.credits?.cast ?: emptyList()
                if (cast.isNotEmpty()) {
                    CastSection(cast, getPosterUrl)
                }

                if (tvDetail != null && tvDetail.seasons.isNotEmpty()) {
                    SeasonsSection(tvDetail.seasons, getPosterUrl)
                }

                val videos = movieDetail?.relatedVideos ?: tvDetail?.relatedVideos ?: emptyList()
                if (videos.isNotEmpty()) {
                    VideosSection(videos)
                }

                MediaInformationSection(movieDetail, tvDetail)
            }

            // Recommendations
            AnimatedVisibility(
                visible = recommendations.isNotEmpty(),
                enter = fadeIn(tween(AnimationTokens.StandardDuration, easing = AlphaEasing)) +
                        slideInVertically(
                            initialOffsetY = { it / 16 },
                            animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing),
                        ),
                exit = fadeOut(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
            ) {
                SeerrHorizontalSection(
                    title = "Recommendations",
                    items = recommendations,
                    getPosterUrl = getPosterUrl,
                    onNavigate = onNavigate
                )
            }

            // Similar
            AnimatedVisibility(
                visible = similar.isNotEmpty(),
                enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 60, easing = AlphaEasing)) +
                        slideInVertically(
                            initialOffsetY = { it / 16 },
                            animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 60, easing = FancyTransitionEasing),
                        ),
                exit = fadeOut(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
            ) {
                SeerrHorizontalSection(
                    title = "Similar",
                    items = similar,
                    getPosterUrl = getPosterUrl,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

@Composable
private fun SeerrHorizontalSection(
    title: String,
    items: List<SeerrSearchItem>,
    getPosterUrl: (String?) -> String?,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
) {
    val loadingState = com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState.current
    val prefetch = com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(items) { item ->
                SeerrMediaCard(
                    item = item,
                    imageUrl = getPosterUrl(item.posterPath),
                    isLoading = loadingState?.isLoading(item.id) == true,
                    onClick = {
                        if (loadingState != null && prefetch != null) {
                            loadingState.startLoading(item.id)
                            prefetch(item.id, item.mediaType) {
                                loadingState.stopLoading(item.id)
                                onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(item.id, item.mediaType))
                            }
                        } else {
                            onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(item.id, item.mediaType))
                        }
                    },
                    modifier = Modifier.width(
                        LocalAdaptiveInfo.current.rowCardWidth(isTvDevice())
                    )
                )
            }
        }
    }
}

@Composable
private fun ExternalLinksRow(
    tmdbId: Int,
    imdbId: String?,
    tvdbId: Int?,
    mediaType: String,
) {
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TMDB
        SuggestionChip(
            onClick = { uriHandler.openUri("https://www.themoviedb.org/$mediaType/$tmdbId") },
            label = { Text("TMDB", fontWeight = FontWeight.Bold) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = Color(0xFF01B4E4).copy(alpha = 0.2f),
                labelColor = Color(0xFF01B4E4)
            ),
            border = null
        )

        if (imdbId != null) {
            SuggestionChip(
                onClick = { uriHandler.openUri("https://www.imdb.com/title/$imdbId") },
                label = { Text("IMDb", fontWeight = FontWeight.Bold) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFFF5C518).copy(alpha = 0.2f),
                    labelColor = Color(0xFFF5C518)
                ),
                border = null
            )
        }

        if (tvdbId != null) {
            SuggestionChip(
                onClick = { uriHandler.openUri("https://thetvdb.com/dereferrer/series/$tvdbId") },
                label = { Text("TVDB", fontWeight = FontWeight.Bold) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFF32A852).copy(alpha = 0.2f),
                    labelColor = Color(0xFF32A852)
                ),
                border = null
            )
        }
    }
}

@Composable
private fun WatchProvidersSection(
    providers: SeerrWatchProviderRegion,
    getLogoUrl: (String?) -> String?,
) {
    val allProviders = (providers.flatrate + providers.buy).distinctBy { it.providerId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Watch Now",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            allProviders.forEach { provider ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    MediaImage(
                        url = getLogoUrl(provider.logoPath) ?: "",
                        contentDescription = provider.providerName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun CastSection(
    cast: List<Any>, // Can be SeerrCast or SeerrAggregateCast
    getProfileUrl: (String?) -> String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(cast) { member ->
                val name: String
                val character: String
                val profilePath: String?

                if (member is SeerrAggregateCast) {
                    name = member.name
                    character = member.roles.firstOrNull()?.character ?: ""
                    profilePath = member.profilePath
                } else if (member is SeerrCast) {
                    name = member.name
                    character = member.character ?: ""
                    profilePath = member.profilePath
                } else return@items

                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        MediaImage(
                            url = getProfileUrl(profilePath) ?: "",
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = character,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonsSection(
    seasons: List<SeerrSeason>,
    getPosterUrl: (String?) -> String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Seasons",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(seasons.sortedByDescending { it.seasonNumber }) { season ->
                Column(modifier = Modifier.width(120.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        MediaImage(
                            url = getPosterUrl(season.posterPath) ?: "",
                            contentDescription = season.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${season.episodeCount} Episodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideosSection(
    videos: List<SeerrRelatedVideo>,
) {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Videos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(videos) { video ->
                val thumbnailUrl = if (video.site?.lowercase() == "youtube") {
                    "https://img.youtube.com/vi/${video.key}/mqdefault.jpg"
                } else null

                Card(
                    modifier = Modifier
                        .width(240.dp)
                        .aspectRatio(16f / 9f)
                        .clickable {
                            if (video.site?.lowercase() == "youtube") {
                                uriHandler.openUri("https://www.youtube.com/watch?v=${video.key}")
                            }
                        },
                    shape = RoundedCornerShape(8.dp)
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
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                        startY = 100f
                                    )
                                )
                        )
                        
                        Text(
                            text = video.name ?: "Video",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Icon(
                            Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingsRow(ratings: SeerrRatings?) {
    if (ratings == null) return

    // Build a list of only valid (non-null) rating items
    val ratingItems = mutableListOf<@Composable () -> Unit>()

    // Rotten Tomatoes Critics
    ratings.rt?.criticsScore?.let { score ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🍅", modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    // Rotten Tomatoes Audience
    ratings.rt?.audienceScore?.let { score ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🍿", modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    // IMDb
    val imdbRating = ratings.imdb
    if (imdbRating != null) {
        val imdbScore = imdbRating.criticsScore ?: imdbRating.rating
        if (imdbScore != null) {
            ratingItems.add {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "IMDb",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", imdbScore),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // TMDb
    ratings.tmdb?.rating?.let { rating ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF90CEA1), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TMDB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${(rating * 10).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    if (ratingItems.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        ratingItems.forEach { it() }
    }
}

@Composable
private fun MediaInfoCondensed(
    movieDetail: SeerrMovieDetails?,
    tvDetail: SeerrTvDetails?,
    ratings: SeerrRatings?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RatingsRow(ratings)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val releaseDate = movieDetail?.releaseDate ?: tvDetail?.firstAirDate
            releaseDate?.take(4)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }

            val runtime = movieDetail?.runtime ?: tvDetail?.episodeRunTime?.firstOrNull()
            if (runtime != null && runtime > 0) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${runtime}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val genres = movieDetail?.genres ?: tvDetail?.genres ?: emptyList()
            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(2).joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaInformationSection(movie: SeerrMovieDetails?, tv: SeerrTvDetails?) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaInfoRow("Status", movie?.status ?: tv?.status ?: "Unknown", Icons.Default.Info)
            
            val releaseDate = movie?.releaseDate ?: tv?.firstAirDate
            MediaInfoRow("Release Date", releaseDate ?: "Unknown", Icons.Default.Event)
            
            if (movie != null) {
                movie.revenue?.takeIf { it > 0 }?.let {
                    MediaInfoRow("Revenue", currencyFormatter.format(it), Icons.Default.Payments)
                }
                movie.budget?.takeIf { it > 0 }?.let {
                    MediaInfoRow("Budget", currencyFormatter.format(it), Icons.Default.AccountBalanceWallet)
                }
            }

            val language = movie?.originalLanguage ?: tv?.originalLanguage
            if (language != null) {
                MediaInfoRow("Language", Locale(language).displayLanguage, Icons.Default.Language)
            }

            val productionCountries = movie?.productionCountries ?: emptyList()
            if (productionCountries.isNotEmpty()) {
                val countryText = productionCountries.joinToString(", ") { country ->
                    val flag = getFlagEmoji(country.iso31661)
                    if (flag != null) "$flag ${country.name}" else country.name
                }
                MediaInfoRow("Country", countryText, Icons.Default.Public)
            }

            val studios = movie?.productionCompanies?.map { it.name } ?: tv?.networks?.map { it.name } ?: emptyList()
            if (studios.isNotEmpty()) {
                MediaInfoRow("Studios", studios.joinToString(", "), Icons.Default.Business)
            }
        }
    }
}

@Composable
private fun MediaInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color.White.copy(alpha = 0.4f)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )
        }
    }
}

private fun getFlagEmoji(countryCode: String): String? {
    if (countryCode.length != 2) return null
    val firstChar = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}
