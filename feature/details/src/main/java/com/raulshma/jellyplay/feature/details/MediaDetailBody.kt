package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Star
import com.raulshma.jellyplay.feature.details.R
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.components.ExpandableText
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

/**
 * Shared entrance-reveal progress (0f → 1f) for the whole detail body.
 *
 * Previously each [FadingItem] and [StaggeredDetailSection] ran its own
 * `LaunchedEffect(Unit) + animateFloatAsState` pair — ~33 independent
 * coroutines and ~66 snapshot-state subscriptions fired on screen entry and
 * never fully released. This local is provided once (by [DetailContentBody])
 * from a single [Animatable], so every entrance-animated element reads the
 * same one snapshot/state and applies its stagger as pure arithmetic.
 *
 * `1f` is the default so elements outside a provider scope (e.g. tests) render
 * immediately instead of stuck at alpha 0.
 */
internal val LocalDetailEntrance = compositionLocalOf<Float> { 1f }

/**
 * Drives the single shared entrance animation. Returns the current progress as a
 * snapshot-read [Float] so consumers re-render only while the animation runs.
 *
 * The target is `1f + maxStaggerSpan` (not `1f`) because [StaggeredDetailSection]
 * subtracts a per-index offset from this value: animating only to `1f` would
 * leave every section with delayIndex > 0 permanently stuck below full alpha
 * (e.g. the lowest section at alpha ~0.46), making the lower half of the detail
 * body look dim once the animation finishes. Driving past `1f` lets the stagger
 * only delay each section's *start* — all of them clamp to alpha 1.0 at rest.
 */
@Composable
private fun rememberDetailEntranceProgress(): Float {
    val animatable = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val target = 1f + DETAIL_MAX_STAGGER_INDEX * DETAIL_STAGGER_STEP
    LaunchedEffect(spec) {
        animatable.animateTo(target, spec)
    }
    return animatable.asState().value
}

@Composable
internal fun FadingItem(
    modifier: Modifier = Modifier,
    delayIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val entrance = LocalDetailEntrance.current
    // Per-element stagger is a pure offset against the shared progress — no
    // coroutine, no extra snapshot subscription.
    val alpha = (entrance - delayIndex * 0.03f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
    ) {
        content()
    }
}

@Composable
internal fun DetailContentBody(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    showActionButtons: Boolean = true,
    showMediaInfo: Boolean = true,
    contentFocusRequester: FocusRequester? = null,
) {
    val detail = state.detail ?: return
    val item = detail.item
    val isAudio = item.mediaType.isAudioType
    val isAlbum = item.mediaType == MediaType.ALBUM
    val showContent = true

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val maxWidth = adaptiveInfo.detailBodyMaxWidth(isTv)
    // Single source of truth for the body's horizontal inset so text, chips, and the
    // poster/action row all share the same left edge (previously the body hard-coded 24.dp
    // while the poster row used adaptiveInfo.contentPadding, causing misalignment on phones).
    val bodyContentPad = adaptiveInfo.contentPadding(isTv)

    // Single shared entrance animation drives every FadingItem / StaggeredDetailSection
    // in the body — replaces ~33 per-element LaunchedEffect + animateFloatAsState pairs.
    val entranceProgress = rememberDetailEntranceProgress()

    CompositionLocalProvider(LocalDetailEntrance provides entranceProgress) {
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
                    .padding(horizontal = bodyContentPad),
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
                                .clickable { item.seriesId?.let(callbacks.onNavigateToSeries) }
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
                        if (state.preferences.showExternalRatings) {
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
                                        .clickable { callbacks.onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.StudioDetail(studio.id, studio.name)) }
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
                state = state,
                callbacks = callbacks,
                vertical = false,
                contentFocusRequester = contentFocusRequester,
            )
        }

        if (showMediaInfo) StaggeredDetailSection(visible = showContent && !isAudio, delayIndex = 2) {
            val source = detail.mediaSources.firstOrNull()
            if (source != null) {
                MediaInfoSection(
                    mediaStreams = source.mediaStreams,
                    selectedAudioIndex = state.selectedAudioIndex,
                    selectedSubtitleIndex = state.selectedSubtitleIndex,
                    onAudioSelect = callbacks.onAudioSelect,
                    onSubtitleSelect = callbacks.onSubtitleSelect,
                    preferences = state.preferences,
                )
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 3) {
            item.overview?.let { overview ->
                FadingItem {
                    // F5: cap the overview so long synopses don't push everything
                    // below the fold, with a "Read more" toggle (collapsed=4 lines).
                    ExpandableText(
                        text = overview,
                        collapsedMaxLines = 4,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = bodyContentPad),
                    )
                }
            }
        }

        StaggeredDetailSection(visible = showContent && isAudio && state.albumTracks.isNotEmpty(), delayIndex = 5) {
            Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
                FadingItem {
                    Text(
                        text = stringResource(R.string.detail_section_tracks),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.albumTracks.forEachIndexed { index, track ->
                        val trackClick = remember(track.id) { { callbacks.onItemClick(track.id) } }
                        val trackPlayClick = remember(track.id, index) { { callbacks.onPlayAlbumTrack(index); callbacks.onItemClick(track.id) } }
                        val trackImageUrl = remember(track.id) { callbacks.getImageUrl(track.id) }
                        FadingItem {
                            AlbumTrackItem(
                                track = track,
                                index = index + 1,
                                imageUrl = trackImageUrl,
                                onClick = trackClick,
                                onPlayClick = trackPlayClick,
                            )
                        }
                    }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 6) {
            val showSeasons = (item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE) && state.seasons.isNotEmpty()
            if (showSeasons) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    // Memoize the skip-specials filter so it is not recomputed (allocating
                    // a new Map + per-season Lists) on every recomposition of this
                    // detail item (scroll-driven FadingItem animations, sibling
                    // sections animating). Only re-runs when episodes or the
                    // skipSpecials flag actually change.
                    val filteredEpisodes = remember(state.episodes, state.preferences.skipSpecials) {
                        if (state.preferences.skipSpecials) {
                            state.episodes.mapValues { (_, eps) -> eps.filter { it.seasonNumber != 0 } }
                        } else {
                            state.episodes
                        }
                    }
                    SeasonsSection(
                        seriesItem = item,
                        seasons = state.seasons,
                        episodes = filteredEpisodes,
                        fetchedSeasonIds = state.fetchedSeasonIds,
                        smartPlayTarget = state.smartPlayTarget,
                        getImageUrl = callbacks.getImageUrl,
                        currentItemId = if (item.mediaType == MediaType.EPISODE) item.id else null,
                        currentSeasonId = if (item.mediaType == MediaType.EPISODE) item.seasonId else null,
                        onEpisodePlayClick = { episode ->
                            val sourceId = null
                            val startPos = episode.playbackPositionTicks ?: 0L
                            callbacks.onPlayClick(episode.id, sourceId, startPos)
                        },
                        onEpisodeDetailClick = { episode ->
                            callbacks.onItemClick(episode.id)
                        },
                        onEpisodeLongPress = callbacks.onMediaQuickActions,
                        onFocusedEpisodeChange = callbacks.onFocusedMediaItem,
                        onSeasonSelected = callbacks.onSeasonSelected,
                        hideEpisodeThumbnails = state.preferences.hideEpisodeThumbnails,
                        episodesDescending = state.preferences.episodesDescending,
                        onEpisodesDescendingChange = callbacks.onEpisodesDescendingChange,
                        compactEpisodeList = state.preferences.compactEpisodeList,
                        onCompactEpisodeListChange = callbacks.onCompactEpisodeListChange,
                        onMarkSeasonPlayed = callbacks.onMarkSeasonPlayed,
                        onMarkSeasonUnplayed = callbacks.onMarkSeasonUnplayed,
                    )
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 7) {
            if (item.mediaType == MediaType.COLLECTION && state.collectionItems.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = stringResource(R.string.detail_section_items),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = bodyContentPad),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    TvFocusableItemRow(
                        items = state.collectionItems,
                        key = { "collection_${it.id}" },
                        contentPadding = PaddingValues(horizontal = bodyContentPad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        onFocusedIndexChange = { index ->
                            state.collectionItems.getOrNull(index)?.let(callbacks.onFocusedMediaItem)
                        },
                    ) { _, collectionItem, focusModifier ->
                            val collectionClick = remember(collectionItem.id) { { callbacks.onItemClick(collectionItem.id) } }
                            val collectionProgress = collectionItem.progressFraction()
                            val collectionImageUrl = remember(collectionItem.id) { callbacks.getImageUrl(collectionItem.id) }
                            PosterCard(
                                item = collectionItem,
                                imageUrl = collectionImageUrl,
                                onClick = collectionClick,
                                showProgress = collectionProgress != null && collectionProgress > 0f,
                                progressPercent = collectionProgress ?: 0f,
                                modifier = focusModifier.width(160.dp),
                            )
                    }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 8) {
            if (detail.people.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = stringResource(R.string.detail_section_cast_crew),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = bodyContentPad),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        TvFocusableItemRow(
                            items = detail.people,
                            key = { "person_${it.id}" },
                            contentPadding = PaddingValues(horizontal = bodyContentPad),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) { _, person, focusModifier ->
                                val personClick = remember(person.id) { { callbacks.onPersonClick(person.id) } }
                                val personImageUrl = remember(person.id) { callbacks.getImageUrl(person.id) }
                                PersonItem(
                                    person = person,
                                    imageUrl = personImageUrl,
                                    onClick = personClick,
                                    modifier = focusModifier,
                                )
                }

        }
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 9) {
            if (state.relatedVideos.isNotEmpty()) {
                VideosSection(videos = state.relatedVideos, onVideoClick = callbacks.onVideoClick)
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 10) {
            if (state.relatedItems.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = stringResource(R.string.detail_section_more_like_this),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = bodyContentPad),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // Compute the card width once from the already-read adaptiveInfo
                    // instead of re-reading LocalAdaptiveInfo.current inside each
                    // visible item lambda (one CompositionLocal read per item).
                    val relatedCardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                    TvFocusableItemRow(
                        items = state.relatedItems,
                        key = { "related_${it.id}" },
                        contentPadding = PaddingValues(horizontal = bodyContentPad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        onFocusedIndexChange = { index ->
                            state.relatedItems.getOrNull(index)?.let(callbacks.onFocusedMediaItem)
                        },
                    ) { _, related, focusModifier ->
                            val relatedClick = remember(related.id) { { callbacks.onItemClick(related.id) } }
                            val relatedImageUrl = remember(related.id) { callbacks.getImageUrl(related.id) }
                            PosterCard(
                                item = related,
                                imageUrl = relatedImageUrl,
                                onClick = relatedClick,
                                modifier = focusModifier.width(relatedCardWidth),
                            )
                    }
                }
            }
        }

        // ── Seerr Recommendations Section ──
        // The Seerr fetch is triggered from the VM (DetailViewModel.loadItem)
        // with a short delay so it doesn't contend with first-frame GPU work.
        // It is NOT triggered from a UI LaunchedEffect here — the former
        // LaunchedEffect over-keyed on isSeerrConnected / isSeerrRecommendationsEnabled
        // and ran on every flag tick (e.g. connection polling) even after the
        // first successful load. Late-connect (Seerr enabled while on the detail
        // screen) does not auto-fetch; revisit if that becomes a real need.

        if (state.isSeerrConnected && state.isSeerrRecommendationsEnabled && state.seerrRecommendations.isNotEmpty()) {
            StaggeredDetailSection(visible = showContent, delayIndex = 11) {
                SeerrItemsRow(
                    title = stringResource(R.string.detail_section_seerr_recommendations),
                    keyPrefix = "seerr_rec",
                    contentType = "seerrRecItem",
                    items = state.seerrRecommendations,
                    onSeerrRequest = callbacks.onSeerrRequest,
                    onNavigate = callbacks.onNavigate,
                )
            }
        }

        // ── Seerr Similar Section ──
        if (state.isSeerrConnected && state.isSeerrRecommendationsEnabled && state.seerrSimilar.isNotEmpty()) {
            StaggeredDetailSection(visible = showContent, delayIndex = 12) {
                SeerrItemsRow(
                    title = stringResource(R.string.detail_section_seerr_similar),
                    keyPrefix = "seerr_sim",
                    contentType = "seerrSimItem",
                    items = state.seerrSimilar,
                    onSeerrRequest = callbacks.onSeerrRequest,
                    onNavigate = callbacks.onNavigate,
                )
            }
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
    val bodyContentPad = adaptiveInfo.contentPadding(isTv = LocalTvMode.current)

    Column {
        FadingItem {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = bodyContentPad),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableItemRow(
            items = items,
            key = { "${keyPrefix}_${it.id}" },
            contentPadding = PaddingValues(horizontal = bodyContentPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, seerrItem, focusModifier ->
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

@Composable
private fun VideosSection(
    videos: List<SeerrRelatedVideo>,
    onVideoClick: (SeerrRelatedVideo) -> Unit,
) {
    val bodyContentPad = LocalAdaptiveInfo.current.contentPadding(isTv = LocalTvMode.current)
    // The video card border depends only on the active theme, not on the per-video
    // data, so compute it once here rather than rebuilding a BorderStroke + gradient
    // per video per recomposition.
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
    Column {
        FadingItem {
            Text(
                text = stringResource(R.string.detail_section_videos),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = bodyContentPad),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableItemRow(
            items = videos,
            // Fall back to the video name when its key is null so two null-key
            // videos don't collide and collapse their composition slots.
            key = { video -> video.key ?: video.name ?: "video" },
            contentType = { _, _ -> "video" },
            contentPadding = PaddingValues(horizontal = bodyContentPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, video, focusModifier ->
                val thumbnailUrl = remember(video.site, video.key) {
                    youTubeThumbnailUrl(video.site, video.key)
                }

                val videoCardFocusState = rememberTvFocusState(focusedScale = 1.05f)

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
