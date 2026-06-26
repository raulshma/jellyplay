package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Star
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

@Composable
internal fun FadingItem(
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
internal fun DetailContentBody(
    item: MediaItem,
    detail: MediaDetail,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
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
                                .clickable { item.seriesId?.let(onNavigateToSeries) }
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
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Text(
                                    text = season,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        if (preferences.showExternalRatings) {
                            detail.criticRating?.let { criticRating ->
                                val criticText = remember(criticRating) { String.format("%.0f", criticRating) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Tabler.Outline.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "$criticText%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }
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

                detail.studios.takeIf { it.isNotEmpty() }?.let { studios ->
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .tvFocusRestorer(),
                    ) {
                        items(studios, key = { it.id }, contentType = { "studio" }) { studio ->
                            FadingItem {
                                val studioFocusState = rememberTvFocusState(focusedScale = 1.05f)
                                Box(
                                    modifier = Modifier
                                        .clip(ShapeCache.smooth16)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .then(studioFocusState.focusModifier)
                                        .then(Modifier.tvFocusIndicator(studioFocusState, ShapeCache.smooth16))
                                        .clickable { onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.StudioDetail(studio.id, studio.name)) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = studio.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f),
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
                    val filteredEpisodes = if (preferences.skipSpecials) {
                        episodes.mapValues { (_, eps) -> eps.filter { it.seasonNumber != 0 } }
                    } else {
                        episodes
                    }
                    SeasonsSection(
                        seriesItem = item,
                        seasons = seasons,
                        episodes = filteredEpisodes,
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
                        hideEpisodeThumbnails = preferences.hideEpisodeThumbnails,
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
                                val collectionProgress = collectionItem.progressFraction()
                                PosterCard(
                                    item = collectionItem,
                                    imageUrl = getImageUrl(collectionItem.id),
                                    onClick = collectionClick,
                                    showProgress = collectionProgress != null && collectionProgress > 0f,
                                    progressPercent = collectionProgress ?: 0f,
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
internal fun SeerrItemsRow(
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
                            .then(videoCardFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(videoCardFocusState, ShapeCache.smooth8))
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
