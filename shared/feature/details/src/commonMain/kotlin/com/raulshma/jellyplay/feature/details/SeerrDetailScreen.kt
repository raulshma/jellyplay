package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.rememberIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SeerrDetailPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCredits
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrReleases
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrWatchProvider
import com.raulshma.jellyplay.core.model.seerr.TmdbImageUrls
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberVideoClickHandler
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.BrandColors
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.rememberInitialFocus
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.composables.icons.tabler.outline.Building
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.CalendarEvent
import com.composables.icons.tabler.outline.Cash
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Circle
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.Hourglass
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Language
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Ticket
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Wallet
import com.composables.icons.tabler.outline.World
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_available
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_release_digital
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_release_physical
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_release_theatrical
import com.raulshma.jellyplay.feature.details.generated.resources.detail_link_imdb
import com.raulshma.jellyplay.feature.details.generated.resources.detail_link_tmdb
import com.raulshma.jellyplay.feature.details.generated.resources.detail_link_tvdb
import com.raulshma.jellyplay.feature.details.generated.resources.detail_request
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_cast
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_recommendations
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seasons
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_similar
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_videos
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_budget
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_country
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_currently_streaming_on
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_director_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_episodes_count
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_information
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_language
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_overview
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_pending
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_processing
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_release_date
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_requested
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_revenue
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_season_n
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_show_more
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_status
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_studios
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_unknown
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_unknown_error
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_video
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeerrDetailScreen(
    tmdbId: Int,
    mediaType: String,
    onBack: () -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
    viewModel: SeerrDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val movieDetail = uiState.movieDetails
    val tvDetail = uiState.tvDetails
    val ratings = uiState.ratings
    val isLoading = uiState.isLoading
    val error = uiState.error
    val seerrRecommendations = uiState.recommendations
    val seerrSimilar = uiState.similar
    val seerrSnapshot by viewModel.seerrSnapshot.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val seerrPrefs by viewModel.seerrPreferences.collectAsStateWithLifecycle()

    val selectedSeasonNumber = uiState.selectedSeasonNumber
    val episodesBySeason = uiState.episodesBySeason
    val isLoadingEpisodes = uiState.isLoadingEpisodes
    val jellyfinItemId = uiState.jellyfinItemId

    val uriHandler = LocalUriHandler.current

    LaunchedEffect(tmdbId, mediaType) {
        viewModel.loadDetails(tmdbId, mediaType)
    }

    val backdropUrl = movieDetail?.backdropUrl ?: tvDetail?.backdropUrl
    val outerIsLightTheme = rememberIsLightTheme()
    var activeTrailerKey by remember { mutableStateOf<String?>(null) }

    ArtworkThemeWrapper(
        imageUrl = backdropUrl ?: "",
        dynamicTheming = preferences.theme.dynamicTheming,
        darkTheme = !outerIsLightTheme,
        oledMode = preferences.theme.oledMode,
        colorStyle = preferences.theme.colorStyle,
        accentColorSwatch = preferences.theme.accentColorSwatch,
    ) {
        var showRequestDialog by remember { mutableStateOf(false) }
        val seerrLoadingState = rememberSeerrCardLoadingState()
        val prefetchCallback: com.raulshma.jellyplay.core.ui.components.SeerrPrefetchCallback =
            remember(seerrLoadingState, viewModel) {
                { tmdbId, mediaType, onDone ->
                    seerrLoadingState.startLoading(tmdbId)
                    viewModel.prefetchRelatedDetails(tmdbId, mediaType) {
                        seerrLoadingState.stopLoading(tmdbId)
                        onDone()
                    }
                }
            }

        androidx.compose.runtime.CompositionLocalProvider(
            com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch provides prefetchCallback,
            com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState provides seerrLoadingState,
        ) {

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && movieDetail == null && tvDetail == null -> {
                    JellyPlayLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null && movieDetail == null && tvDetail == null -> {
                    ErrorScreen(
                        message = error ?: stringResource(Res.string.detail_seerr_unknown_error),
                        onRetry = { viewModel.loadDetails(tmdbId, mediaType) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    SeerrDetailContent(
                        movieDetail = movieDetail,
                        tvDetail = tvDetail,
                        ratings = ratings,
                        recommendations = seerrRecommendations,
                        similar = seerrSimilar,
                        onRequestClick = { showRequestDialog = true },
                        onNavigate = onNavigate,
                        jellyfinItemId = jellyfinItemId,
                        onBack = onBack,
                        streamingRegion = seerrPrefs.streamingRegion,
                        discoverRegion = seerrPrefs.discoverRegion,
                        seerrServerUrl = seerrPrefs.serverUrl,
                        selectedSeasonNumber = selectedSeasonNumber,
                        episodesBySeason = episodesBySeason,
                        isLoadingEpisodes = isLoadingEpisodes,
                        onSeasonClick = { seasonNumber ->
                            tvDetail?.id?.let { tvId ->
                                viewModel.toggleSeason(tvId, seasonNumber)
                            }
                        },
                        onVideoClick = rememberVideoClickHandler(
                            uriHandler = uriHandler,
                            onPlayYouTube = { key -> activeTrailerKey = key },
                        ),
                        preferences = preferences,
                    )
                }
            }

            if (showRequestDialog) {
                val item = remember(movieDetail, tvDetail) {
                    movieDetail?.let {
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
                }

                item?.let {
                    // Fetch service details and TV seasons on-demand when the
                    // dialog opens — the snapshot's seasons/anime fields are
                    // populated by loadTvSeasons (same pattern as the search
                    // and media-detail request dialogs).
                    LaunchedEffect(Unit) {
                        viewModel.loadServiceDetails(it.mediaType)
                        if (it.mediaType.equals("tv", ignoreCase = true)) {
                            viewModel.loadTvSeasons(it.id)
                        }
                    }

                    SeerrRequestDialog(
                        item = it,
                        snapshot = seerrSnapshot,
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
                        InlineTrailerPlayerHost(
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
    onRequestClick: () -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
    jellyfinItemId: String?,
    onBack: () -> Unit,
    streamingRegion: String = "US",
    discoverRegion: String = "US",
    seerrServerUrl: String = "",
    selectedSeasonNumber: Int? = null,
    episodesBySeason: Map<Int, List<SeerrEpisode>> = emptyMap(),
    isLoadingEpisodes: Boolean = false,
    onSeasonClick: (Int) -> Unit = {},
    onVideoClick: (SeerrRelatedVideo) -> Unit,
    preferences: SeerrDetailPreferences,
) {
    val listState = rememberLazyListState()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val isTv = LocalTvMode.current
    val density = LocalDensity.current

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    // Rebuilt only when the theme flags / palette change so the consuming
    // Cards see a stable BorderStroke instance between scroll frames.
    val cardBorder = remember(isSynthwave, isSoothing, primary, secondary, outline) {
        when {
            isSynthwave -> {
                androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(primary, secondary)
                    )
                )
            }
            isSoothing -> {
                androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = outline.copy(alpha = 0.35f)
                )
            }
            else -> null
        }
    }

    val backdropUrl = movieDetail?.backdropUrl ?: tvDetail?.backdropUrl
    val title = movieDetail?.title ?: tvDetail?.name ?: ""
    val posterUrl = movieDetail?.posterUrl ?: tvDetail?.posterUrl

    val relatedVideos = movieDetail?.relatedVideos ?: tvDetail?.relatedVideos ?: emptyList()
    val trailerVideo = remember(relatedVideos) {
        relatedVideos.firstOrNull {
            it.site?.lowercase() == "youtube" &&
            (it.type?.lowercase() == "trailer" || it.type?.lowercase() == "teaser")
        } ?: relatedVideos.firstOrNull { it.site?.lowercase() == "youtube" }
    }
    var autoplayEmbedFailed by remember { mutableStateOf(false) }

    val contentFocusRequester = remember { FocusRequester() }
    val hasContent = movieDetail != null || tvDetail != null

    // Reuse the shared detail scroll-state controller instead of re-implementing
    // the backdrop-height/scroll-offset/fraction/color/nav-bar-sync chain here.
    // Behaviour is identical; consolidating removes ~40 lines of duplicated
    // scroll math and keeps the two detail surfaces from drifting apart.
    val detailScrollState = rememberDetailScrollState(listState, hasContent)
    val backdropHeight = detailScrollState.backdropHeight
    val baseBackdropHeight = detailScrollState.baseBackdropHeight
    val scrollOffset = detailScrollState.scrollOffset
    val scrollFraction = detailScrollState.scrollFraction
    val backgroundColor = detailScrollState.backgroundColor
    // Local alias for the media-detail "scrollCollapsed" value; SeerrDetail
    // historically named this appBarColor.
    val appBarColor = detailScrollState.scrollCollapsed

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    if (isTv) {
        LaunchedEffect(Unit) {
            var focused = false
            androidx.compose.runtime.snapshotFlow { hasContent }.collect {
                if (it && !focused) {
                    focused = true
                    kotlinx.coroutines.delay(50)
                    contentFocusRequester.tryRequestFocus()
                }
            }
        }
    }

    val backgroundModifier = if (isSynthwave) {
        Modifier.background(
            com.raulshma.jellyplay.core.designsystem.theme.synthwaveBackgroundBrush()
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(backdropHeight)
                .graphicsLayer {
                    translationY = -scrollOffset * 0.5f
                    alpha = 1f - (scrollFraction * 0.8f)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f + (scrollOffset * 0.001f).coerceAtLeast(0f)
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                if (backdropUrl != null) {
                    MediaImage(
                        url = backdropUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                val playAutoplayTrailer = preferences.trailerAutoplay && trailerVideo != null && !autoplayEmbedFailed
                val trailerKey = trailerVideo?.key
                if (playAutoplayTrailer && trailerKey != null) {
                    InlineTrailerPlayerHost(
                        videoKey = trailerKey,
                        modifier = Modifier.fillMaxSize(),
                        muted = true,
                        showControls = false,
                        autoplay = true,
                        focusable = false,
                        cropToFill = true,
                        onEmbedFailed = { autoplayEmbedFailed = true },
                    )
                }
            }

            val isLandscapeExpanded = isExpanded && adaptiveInfo.isLandscape
            // Rebuilt only when the theme colour / backdrop geometry change, not
            // on every scroll frame (mirrors DetailContent's fadeBrush).
            val scrimBrush = remember(backgroundColor, backdropHeight, baseBackdropHeight, isLandscapeExpanded, density) {
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
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimBrush)
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
                        Column(
                            modifier = Modifier.width(240.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                                shape = ShapeCache.smooth12,
                                border = cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSoothing) 1.5.dp else 12.dp)
                            ) {
                                MediaImage(
                                    url = posterUrl ?: "",
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
                                jellyfinItemId = jellyfinItemId,
                                onOpenInLibrary = { id ->
                                    onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.MediaDetail(id))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentFocusRequester = contentFocusRequester,
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
                            onNavigate = onNavigate,
                            modifier = Modifier.weight(1f),
                            streamingRegion = streamingRegion,
                            discoverRegion = discoverRegion,
                            seerrServerUrl = seerrServerUrl,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodesBySeason = episodesBySeason,
                            isLoadingEpisodes = isLoadingEpisodes,
                            onSeasonClick = onSeasonClick,
                            onVideoClick = onVideoClick,
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
                                shape = ShapeCache.smooth8,
                                border = cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSoothing) 1.5.dp else 8.dp)
                            ) {
                                MediaImage(
                                    url = posterUrl ?: "",
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    
                                    val contentRating = tvDetail?.contentRatings?.results?.find { it.iso31661 == "US" }?.rating
                                    if (contentRating != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = Color.Transparent,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                                            shape = ShapeCache.smooth4
                                        ) {
                                            Text(
                                                text = contentRating,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                            jellyfinItemId = jellyfinItemId,
                            onOpenInLibrary = { id ->
                                onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.MediaDetail(id))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentFocusRequester = contentFocusRequester,
                        )

                        Spacer(Modifier.height(32.dp))

                        SeerrDetailBody(
                            movieDetail = movieDetail,
                            tvDetail = tvDetail,
                            ratings = ratings,
                            recommendations = recommendations,
                            similar = similar,
                            onNavigate = onNavigate,
                            modifier = Modifier.fillMaxWidth(),
                            streamingRegion = streamingRegion,
                            discoverRegion = discoverRegion,
                            seerrServerUrl = seerrServerUrl,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodesBySeason = episodesBySeason,
                            isLoadingEpisodes = isLoadingEpisodes,
                            onSeasonClick = onSeasonClick,
                            onVideoClick = onVideoClick,
                        )
                    }
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

        MediumTopAppBar(
            title = {
                AnimatedVisibility(
                    visible = scrollFraction > 0.7f,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.defaultEffectsSpec()) { it / 2 },
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.defaultEffectsSpec()) { it / 2 }
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            navigationIcon = {
                CircleBgBackButton(
                    onClick = onBack,
                    scrollCollapsed = appBarColor,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = animatedContainerColor,
                scrolledContainerColor = animatedContainerColor,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun SeerrActionButtons(
    movieDetail: SeerrMovieDetails?,
    tvDetail: SeerrTvDetails?,
    onRequestClick: () -> Unit,
    jellyfinItemId: String?,
    onOpenInLibrary: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
) {
    val mediaInfo = movieDetail?.mediaInfo ?: tvDetail?.mediaInfo
    val status = mediaInfo?.status ?: 0
    val mediaStatus = remember(status) { SeerrMediaStatus.fromValue(status) }
    val isAvailable = mediaStatus == SeerrMediaStatus.AVAILABLE || mediaStatus == SeerrMediaStatus.PARTIALLY_AVAILABLE
    val isPending = mediaStatus == SeerrMediaStatus.PENDING
    val isProcessing = mediaStatus == SeerrMediaStatus.PROCESSING
    val hasRequest = mediaInfo?.requests?.isNotEmpty() == true
    val isRequested = isPending || isProcessing || hasRequest
    val buttonFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isAvailable) {
            // When the Jellyfin library item has been resolved, the button opens it
            // directly. Otherwise it stays disabled (still indicates availability).
            Button(
                onClick = { jellyfinItemId?.let(onOpenInLibrary) },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                    )
                    .then(buttonFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(buttonFocusState, ShapeCache.smooth12)),
                shape = ShapeCache.smooth12,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(vertical = 12.dp),
                enabled = jellyfinItemId != null
            ) {
                Icon(Tabler.Outline.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.detail_available), fontWeight = FontWeight.Bold)
            }
        } else if (isRequested) {
            val (labelRes, icon, color) = when {
                isProcessing -> Triple(Res.string.detail_seerr_processing, Tabler.Outline.Hourglass, StatusColors.info)
                isPending -> Triple(Res.string.detail_seerr_pending, Tabler.Outline.Clock, StatusColors.pending)
                else -> Triple(Res.string.detail_seerr_requested, Tabler.Outline.ArrowRight, StatusColors.requested)
            }
            Button(
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .then(
                        contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                    )
                    .then(buttonFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(buttonFocusState, ShapeCache.smooth12)),
                shape = ShapeCache.smooth12,
                colors = ButtonDefaults.buttonColors(
                    containerColor = color.copy(alpha = 0.15f),
                    contentColor = color,
                    disabledContainerColor = color.copy(alpha = 0.15f),
                    disabledContentColor = color
                ),
                contentPadding = PaddingValues(vertical = 12.dp),
                enabled = false
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes), fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRequestClick,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                    )
                    .then(buttonFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(buttonFocusState, ShapeCache.smooth12)),
                shape = ShapeCache.smooth12,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Tabler.Outline.Plus, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.detail_request), fontWeight = FontWeight.Bold)
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
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
    modifier: Modifier = Modifier,
    streamingRegion: String = "US",
    discoverRegion: String = "US",
    seerrServerUrl: String = "",
    selectedSeasonNumber: Int? = null,
    episodesBySeason: Map<Int, List<SeerrEpisode>> = emptyMap(),
    isLoadingEpisodes: Boolean = false,
    onSeasonClick: (Int) -> Unit = {},
    onVideoClick: (SeerrRelatedVideo) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
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
                        text = stringResource(Res.string.detail_seerr_overview),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
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
                                label = { Text(keyword.name, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                border = null
                            )
                        }
                    }
                }
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
                        val cast = tvDetail?.aggregateCredits?.cast?.toAggregateCastMembers()
                            ?: movieDetail?.credits?.cast?.toCastMembers()
                            ?: emptyList()
                        if (cast.isNotEmpty()) {
                            CastSection(cast)
                        }

                        // Seasons (TV only)
                        if (tvDetail != null && tvDetail.seasons.isNotEmpty()) {
                            SeasonsSection(
                                seasons = tvDetail.seasons,
                                selectedSeasonNumber = selectedSeasonNumber,
                                episodesBySeason = episodesBySeason,
                                isLoadingEpisodes = isLoadingEpisodes,
                                onSeasonClick = onSeasonClick,
                                showPosterUrl = tvDetail.posterUrl,
                            )
                        }

                        // Videos
                        val videos = movieDetail?.relatedVideos ?: tvDetail?.relatedVideos ?: emptyList()
                        if (videos.isNotEmpty()) {
                            VideosSection(videos, onVideoClick)
                        }
                    }

                    Column(
                        modifier = Modifier.width(300.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        MediaInformationSection(movieDetail, tvDetail, streamingRegion, discoverRegion, seerrServerUrl)
                    }
                }
            } else {
                // Stacked layout for compact screens
                val cast = tvDetail?.aggregateCredits?.cast?.toAggregateCastMembers()
                    ?: movieDetail?.credits?.cast?.toCastMembers()
                    ?: emptyList()
                if (cast.isNotEmpty()) {
                    CastSection(cast)
                }

                if (tvDetail != null && tvDetail.seasons.isNotEmpty()) {
                    SeasonsSection(
                        seasons = tvDetail.seasons,
                        selectedSeasonNumber = selectedSeasonNumber,
                        episodesBySeason = episodesBySeason,
                        isLoadingEpisodes = isLoadingEpisodes,
                        onSeasonClick = onSeasonClick,
                        showPosterUrl = tvDetail.posterUrl,
                    )
                }

                val videos = movieDetail?.relatedVideos ?: tvDetail?.relatedVideos ?: emptyList()
                if (videos.isNotEmpty()) {
                    VideosSection(videos, onVideoClick)
                }

                MediaInformationSection(movieDetail, tvDetail, streamingRegion, discoverRegion, seerrServerUrl)
            }

            // Recommendations
            AnimatedVisibility(
                visible = recommendations.isNotEmpty(),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        slideInVertically(
                            initialOffsetY = { it / 16 },
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                SeerrHorizontalSection(
                    title = stringResource(Res.string.detail_section_recommendations),
                    items = recommendations,
                    onNavigate = onNavigate
                )
            }

            // Similar
            AnimatedVisibility(
                visible = similar.isNotEmpty(),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        slideInVertically(
                            initialOffsetY = { it / 16 },
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                SeerrHorizontalSection(
                    title = stringResource(Res.string.detail_section_similar),
                    items = similar,
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
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
) {
    val loadingState = com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState.current
    val prefetch = com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch.current
    val uniqueItems = remember(items) { items.distinctBy { it.id } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            items(uniqueItems, key = { it.id }, contentType = { "seerrSearchItem" }) { item ->
                SeerrMediaCard(
                    item = item,
                    imageUrl = item.posterUrl,
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
                        LocalAdaptiveInfo.current.rowCardWidth(LocalTvMode.current)
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
            label = { Text(stringResource(Res.string.detail_link_tmdb), fontWeight = FontWeight.Bold) },
            modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = BrandColors.tmdb.copy(alpha = 0.2f),
                labelColor = BrandColors.tmdb
            ),
            border = null
        )

        if (imdbId != null) {
            SuggestionChip(
                onClick = { uriHandler.openUri("https://www.imdb.com/title/$imdbId") },
                label = { Text(stringResource(Res.string.detail_link_imdb), fontWeight = FontWeight.Bold) },
                modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = BrandColors.imdb.copy(alpha = 0.2f),
                labelColor = BrandColors.imdb
                ),
                border = null
            )
        }

        if (tvdbId != null) {
            SuggestionChip(
                onClick = { uriHandler.openUri("https://thetvdb.com/dereferrer/series/$tvdbId") },
                label = { Text(stringResource(Res.string.detail_link_tvdb), fontWeight = FontWeight.Bold) },
                modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = BrandColors.tvdb.copy(alpha = 0.2f),
                labelColor = BrandColors.tvdb
                ),
                border = null
            )
        }
    }
}

@Composable
private fun CastSection(
    cast: List<SeerrCastMember>,
) {
    val uniqueCast = remember(cast) { cast.distinctBy { it.id } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_section_cast),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            items(uniqueCast, key = { it.id }, contentType = { "castMember" }) { member ->
                val name = member.name
                val character = member.character
                val profileUrl = member.profileUrl

                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        MediaImage(
                            url = profileUrl ?: "",
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = character,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeasonsSection(
    seasons: List<SeerrSeason>,
    selectedSeasonNumber: Int? = null,
    episodesBySeason: Map<Int, List<SeerrEpisode>> = emptyMap(),
    isLoadingEpisodes: Boolean = false,
    onSeasonClick: (Int) -> Unit = {},
    showPosterUrl: String? = null,
) {
    val isTv = LocalTvMode.current
    val sortedSeasons = remember(seasons) {
        seasons.sortedByDescending { it.seasonNumber }.distinctBy { it.seasonNumber }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_section_seasons),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            items(sortedSeasons, key = { it.seasonNumber }, contentType = { "season" }) { season ->
                val isSelected = selectedSeasonNumber == season.seasonNumber
                val borderModifier = if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = ShapeCache.smooth8
                    )
                } else Modifier

                // Fall back to the show's main poster when this season has no
                // dedicated artwork — Overseerr returns a null posterPath for
                // some seasons (notably specials/season 0, or seasons TMDB has
                // no poster for). Without the fallback the card showed the
                // generic placeholder even though the show itself has artwork.
                val seasonCardUrl = remember(season.posterUrl, showPosterUrl) {
                    season.posterUrl ?: showPosterUrl
                }
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .then(borderModifier)
                        .clip(ShapeCache.smooth8)
                        .clickable { onSeasonClick(season.seasonNumber) }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        shape = ShapeCache.smooth8,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box {
                            MediaImage(
                                url = seasonCardUrl ?: "",
                                contentDescription = season.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        )
                                )
                                Icon(
                                    imageVector = com.composables.icons.tabler.Tabler.Outline.ChevronDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.detail_seerr_episodes_count, season.episodeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val selectedSeason = selectedSeasonNumber
        if (selectedSeason != null) {
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialHeight = { 0 }
                ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                val episodes = episodesBySeason[selectedSeason]
                if (isLoadingEpisodes && episodes == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        JellyPlayLoadingIndicator()
                    }
                } else if (episodes != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = sortedSeasons.find { it.seasonNumber == selectedSeason }?.name ?: stringResource(Res.string.detail_seerr_season_n, selectedSeason),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // Prefer the selected season's own poster as the
                        // episode-still fallback, then the show poster.
                        val selectedSeasonPoster = sortedSeasons
                            .find { it.seasonNumber == selectedSeason }?.posterUrl
                            ?: showPosterUrl
                        episodes.forEach { episode ->
                            EpisodeRow(episode = episode, fallbackImageUrl = selectedSeasonPoster)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: SeerrEpisode,
    fallbackImageUrl: String? = null,
) {
    // TMDB episode stills are frequently missing (unaired episodes, or episodes
    // TMDB has no still for). Fall back to the season/show poster so the row
    // isn't a bare text entry when stillPath is null.
    val stillUrl = episode.stillUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth8,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (stillUrl != null) {
                val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
                val scrimBrush = remember(surfaceContainerLow) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            surfaceContainerLow.copy(alpha = 0.9f),
                            surfaceContainerLow,
                        ),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    MediaImage(
                        url = stillUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scrimBrush)
                    )
                    Text(
                        text = "${episode.episodeNumber}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metaItems = mutableListOf<@Composable () -> Unit>()
                episode.airDate?.takeIf { it.isNotBlank() }?.let { date ->
                    metaItems.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = com.composables.icons.tabler.Tabler.Outline.Calendar,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatDate(date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                episode.runtime?.takeIf { it > 0 }?.let { mins ->
                    metaItems.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = com.composables.icons.tabler.Tabler.Outline.Clock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatRuntime(mins),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                episode.voteAverage?.takeIf { it > 0f }?.let { rating ->
                    metaItems.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = com.composables.icons.tabler.Tabler.Outline.Star,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = RatingColors.star
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatRatingOneDecimal(rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            episode.voteCount.takeIf { it > 0 }?.let { count ->
                                Text(
                                    text = " ($count)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (metaItems.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        metaItems.forEach { it() }
                    }
                }

                val directors = remember(episode.crew) {
                    episode.crew.filter { it.job.equals("Director", ignoreCase = true) }
                }
                if (directors.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = com.composables.icons.tabler.Tabler.Outline.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.detail_seerr_director_format, directors.joinToString(", ") { it.name }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val writers = remember(episode.crew) {
                    episode.crew.filter {
                        it.department.equals("Writing", ignoreCase = true)
                    }
                }
                if (writers.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = com.composables.icons.tabler.Tabler.Outline.Pencil,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = writers.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (episode.guestStars.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = com.composables.icons.tabler.Tabler.Outline.Users,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = episode.guestStars.joinToString(", ") {
                                it.character?.takeIf { c -> c.isNotBlank() }
                                    ?.let { c -> "${it.name} ($c)" }
                                    ?: it.name
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Spacer(Modifier.height(2.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                    if (!expanded && overview.length > 200) {
                        Text(
                            text = stringResource(Res.string.detail_seerr_show_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideosSection(
    videos: List<SeerrRelatedVideo>,
    onVideoClick: (SeerrRelatedVideo) -> Unit,
) {
    val uniqueVideos = remember(videos) {
        videos.distinctBy { it.key }.filter { !it.key.isNullOrBlank() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_section_videos),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            items(uniqueVideos, key = { it.key!! }, contentType = { "video" }) { video ->
                val thumbnailUrl = youTubeThumbnailUrl(video.site, video.key)

                val videoCardFocusState = rememberTvFocusState(focusedScale = 1.05f)

                Card(
                    modifier = Modifier
                        .width(240.dp)
                        .aspectRatio(16f / 9f)
                        .then(videoCardFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(videoCardFocusState, ShapeCache.smooth8))
                        .clickable {
                            onVideoClick(video)
                        },
                    shape = ShapeCache.smooth8
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
                            text = video.name ?: stringResource(Res.string.detail_seerr_video),
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
                    color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurface
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
                            .background(BrandColors.imdb, ShapeCache.smooth4)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "IMDb",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatRatingOneDecimal(imdbScore),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // TMDb
    ratings.tmdb?.rating?.let { rating ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The badge background is a fixed mint-green (#90CEA1), which is light in
                // every theme variant, so the onSurface token (light in dark theme) would be
                // low-contrast. Derive the text color from the badge's own luminance instead.
                val tmdbBadgeColor = BrandColors.tmdbBackground
                val tmdbBadgeText = remember(tmdbBadgeColor) {
                    if (isLightColor(tmdbBadgeColor)) Color.Black else Color.White
                }
                Box(
                    modifier = Modifier
                        .background(tmdbBadgeColor, ShapeCache.smooth4)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TMDB",
                        style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = tmdbBadgeText
                        )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${(rating * 10).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            val runtime = movieDetail?.runtime ?: tvDetail?.episodeRunTime?.firstOrNull()
            if (runtime != null && runtime > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    shape = ShapeCache.smooth4
                ) {
                    Text(
                        text = "${runtime}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val genres = movieDetail?.genres ?: tvDetail?.genres ?: emptyList()
            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(2).joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaInformationSection(
    movie: SeerrMovieDetails?,
    tv: SeerrTvDetails?,
    streamingRegion: String = "US",
    discoverRegion: String = "US",
    seerrServerUrl: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_seerr_information),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaInfoRow(stringResource(Res.string.detail_seerr_status), movie?.status ?: tv?.status ?: stringResource(Res.string.detail_seerr_unknown), Tabler.Outline.InfoCircle)

            val releaseDate = movie?.releaseDate ?: tv?.firstAirDate
            if (movie != null) {
                ReleaseDateRow(releaseDate, movie.releases, discoverRegion)
            } else {
                MediaInfoRow(stringResource(Res.string.detail_seerr_release_date), releaseDate ?: stringResource(Res.string.detail_seerr_unknown), Tabler.Outline.CalendarEvent)
            }

            if (movie != null) {
                movie.revenue?.takeIf { it > 0 }?.let {
                    MediaInfoRow(stringResource(Res.string.detail_seerr_revenue), formatUsCurrency(it), Tabler.Outline.Cash)
                }
                movie.budget?.takeIf { it > 0 }?.let {
                    MediaInfoRow(stringResource(Res.string.detail_seerr_budget), formatUsCurrency(it), Tabler.Outline.Wallet)
                }
            }

            val language = movie?.originalLanguage ?: tv?.originalLanguage
            if (language != null) {
                MediaInfoRow(stringResource(Res.string.detail_seerr_language), languageDisplayName(language) ?: language, Tabler.Outline.Language)
            }

            val productionCountries = movie?.productionCountries ?: emptyList()
            if (productionCountries.isNotEmpty()) {
                val countryText = productionCountries.joinToString(", ") { country ->
                    val flag = getFlagEmoji(country.iso31661)
                    if (flag != null) "$flag ${country.name}" else country.name
                }
                MediaInfoRow(stringResource(Res.string.detail_seerr_country), countryText, Tabler.Outline.World)
            }

            val studios = remember(movie, tv) {
                movie?.productionCompanies?.map { it.name } ?: tv?.networks?.map { it.name } ?: emptyList()
            }
            if (studios.isNotEmpty()) {
                MediaInfoRow(stringResource(Res.string.detail_seerr_studios), studios.joinToString(", "), Tabler.Outline.Building)
            }

            val watchProviders = movie?.watchProviders ?: tv?.watchProviders ?: emptyList()
            val regionProviders = watchProviders.find { it.iso31661 == streamingRegion }
            val streamingProviders = regionProviders?.flatrate.orEmpty()
            if (streamingProviders.isNotEmpty()) {
                StreamingProvidersRow(streamingProviders, streamingRegion, seerrServerUrl)
            }
        }
    }
}

@Composable
private fun ReleaseDateRow(
    releaseDate: String?,
    releases: SeerrReleases?,
    discoverRegion: String,
) {
    val filteredReleases = remember(releases, discoverRegion) {
        releases?.results
            ?.find { it.iso31661 == discoverRegion }
            ?.releaseDates
            .orEmpty()
            .filter { it.type in 3..5 }
            .distinctBy { it.type }
            .sortedBy { it.type }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Tabler.Outline.CalendarEvent,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = stringResource(Res.string.detail_seerr_release_date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = releaseDate ?: stringResource(Res.string.detail_seerr_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                if (filteredReleases.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    filteredReleases.forEach { release ->
                        ReleaseTypeIcon(release.type)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseTypeIcon(type: Int) {
    when (type) {
        3 -> Icon(
            imageVector = Tabler.Outline.Ticket,
            contentDescription = stringResource(Res.string.detail_cd_release_theatrical),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        4 -> Icon(
            imageVector = Tabler.Outline.CloudDownload,
            contentDescription = stringResource(Res.string.detail_cd_release_digital),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        5 -> Icon(
            imageVector = Tabler.Outline.Circle,
            contentDescription = stringResource(Res.string.detail_cd_release_physical),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StreamingProvidersRow(
    providers: List<SeerrWatchProvider>,
    region: String,
    seerrServerUrl: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Tabler.Outline.PlayerPlay,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = stringResource(Res.string.detail_seerr_currently_streaming_on),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                providers.forEach { provider ->
                    val logoUrl = provider.logoPath?.let { path ->
                        val cleanPath = path.trimStart('/')
                        if (seerrServerUrl.isNotBlank()) {
                            "${seerrServerUrl.trimEnd('/')}/imageproxy/tmdb/t/p/w45/$cleanPath"
                        } else {
                            "${TmdbImageUrls.LOGO_W45}/$cleanPath"
                        }
                    }
                    if (logoUrl != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(ShapeCache.smooth8)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ) {
                            MediaImage(
                                url = logoUrl,
                                contentDescription = provider.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )
        }
    }
}

// ── Wave 16C formatting seams (java.text purification) ───────────────────────
// java.text.NumberFormat/String.format have no wasmJs variant; these integer-
// math helpers replicate the Locale.US output shapes the two replaced call
// sites produced. Same body as core:ui's wasmJs formatOneDecimal actual (wave
// 11), which is `internal` to that module — hence the private copies here.

/**
 * "%.1f" formatting contract (the two former `String.format("%.1f", …)` /
 * `String.format(Locale.US, "%.1f", …)` sites): HALF_UP rounding at the first
 * decimal — computed as floor(|v|·10 + 0.5), i.e. ties round AWAY from zero,
 * the same rule java's Formatter applies to the same binary value — with the
 * decimal point always rendered and the sign applied symmetrically.
 *
 * Side benefit worth noting (review round): the episode-row site used
 * default-locale `String.format` at HEAD, so de/fr/ru/pt-BR JVM devices saw
 * "8,7" — this helper always renders the dot.
 *
 * Known-inert delta: -0.0f renders "0.0" (java emitted "-0.0"); unreachable
 * for Seerr ratings, kept documented for the next consumer.
 */
private fun formatRatingOneDecimal(value: Float): String {
    val magnitude = (kotlin.math.abs(value) * 10 + 0.5).toLong()
    val rendered = "${magnitude / 10}.${magnitude % 10}"
    return if (value < 0) "-$rendered" else rendered
}

/**
 * `NumberFormat.getCurrencyInstance(Locale.US)` output shape for whole-dollar
 * Longs ("$8.99" family: "$" prefix — "-" before the "$" for negatives —
 * comma-grouped thousands, exactly two decimals). Seerr budget/revenue are
 * whole dollar amounts, so the cents are always "00", exactly as
 * NumberFormat.format(Long) rendered them.
 */
private fun formatUsCurrency(amount: Long): String {
    val digits = if (amount < 0) amount.toString().removePrefix("-") else amount.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return (if (amount < 0) "-$" else "$") + grouped + ".00"
}
