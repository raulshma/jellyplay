package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
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
                    SeerrRequestDialog(
                        item = it,
                        isRequesting = requestResult?.isLoading == true,
                        requestSuccess = requestResult?.success,
                        requestError = requestResult?.error,
                        onConfirm = { seasons ->
                            viewModel.requestMedia(it, seasons)
                        },
                        onDismiss = {
                            showRequestDialog = false
                            viewModel.clearRequestResult()
                        }
                    )
                }
            }
        }
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
        animationSpec = tween(600),
        label = "backgroundColor",
    )

    val navBarColor = LocalNavigationBarColor.current
    navBarColor.value = backgroundColor

    val appBarColor by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "appBarColor",
    )

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // Backdrop
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
                        // Left column: poster + action buttons
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

                            SeerrActionButtons(
                                movieDetail = movieDetail,
                                tvDetail = tvDetail,
                                onRequestClick = onRequestClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right column: details
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
                    // Portrait/Compact Layout
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
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
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
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
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
            // Ratings and Overview
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RatingsRow(ratings)

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
                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified // Default
                    )
                }
            }

            // Details Table
            DetailsTable(movieDetail, tvDetail)

            // Recommendations
            if (recommendations.isNotEmpty()) {
                SeerrHorizontalSection(
                    title = "Recommendations",
                    items = recommendations,
                    getPosterUrl = getPosterUrl,
                    onNavigate = onNavigate
                )
            }

            // Similar
            if (similar.isNotEmpty()) {
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
                    onClick = {
                        onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(item.id, item.mediaType))
                    },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingsRow(ratings: SeerrRatings?) {
    if (ratings == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Rotten Tomatoes
        ratings.rt?.let { rt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🍅", modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "${rt.criticsScore}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "🍿", modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "${rt.audienceScore}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // IMDb
        ratings.imdb?.let { imdb ->
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
                    text = String.format(Locale.US, "%.1f", imdb.rating ?: 0f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // TMDb
        ratings.tmdb?.let { tmdb ->
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
                    text = "${(tmdb.rating?.times(10))?.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DetailsTable(movie: SeerrMovieDetails?, tv: SeerrTvDetails?) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        DetailRow("Status", movie?.status ?: tv?.status ?: "Unknown")
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

        val releaseDate = movie?.releaseDate ?: tv?.firstAirDate
        DetailRow(
            label = "Release Dates",
            value = releaseDate ?: "Unknown",
            icon = Icons.Default.Movie
        )
        
        movie?.digitalReleaseDate?.let {
            Spacer(Modifier.height(4.dp))
            DetailRow(
                label = "",
                value = it,
                icon = Icons.Default.Public
            )
        }

        if (movie != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            DetailRow("Revenue", movie.revenue?.takeIf { it > 0 }?.let { currencyFormatter.format(it) } ?: "—")
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            DetailRow("Budget", movie.budget?.takeIf { it > 0 }?.let { currencyFormatter.format(it) } ?: "—")
        }

        val language = movie?.originalLanguage ?: tv?.originalLanguage
        if (language != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            DetailRow("Original Language", Locale(language).displayLanguage)
        }

        val productionCountries = movie?.productionCountries ?: emptyList()
        if (productionCountries.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            val countryText = productionCountries.joinToString(", ") { country ->
                val flag = getFlagEmoji(country.iso31661)
                if (flag != null) "$flag ${country.name}" else country.name
            }
            DetailRow("Production Country", countryText)
        }

        val studios = movie?.productionCompanies?.map { it.name } ?: tv?.networks?.map { it.name } ?: emptyList()
        if (studios.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
            DetailRow("Studios", studios.joinToString("\n"))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.width(140.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.End
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
