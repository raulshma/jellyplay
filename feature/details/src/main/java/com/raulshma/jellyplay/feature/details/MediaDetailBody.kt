package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.EyeOff
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
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag
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

/**
 * Pill chip for a navigable metadata facet (genre / tag / studio). The genre and
 * tag rows shared the same clip → background → focus → clickable → padding → Text
 * shape, differing only in colour and the route argument; this collapses them so
 * the look stays consistent and the duplicated modifier chain lives once.
 *
 * [enabled] renders the chip as a non-clickable label (used for genres on a
 * LOCAL origin that can't fulfil a drill-in) — focus + click are attached only
 * when navigable, matching the previous per-branch gating.
 */
@Composable
private fun TagChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    Box(
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .background(containerColor)
            .then(if (enabled) focusState.focusModifier else Modifier)
            .then(if (enabled) Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
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
                                .clip(ShapeCache.smooth8)
                                .then(seriesNavFocusState.focusModifier)
                                .then(Modifier.tvFocusIndicator(seriesNavFocusState, ShapeCache.smooth8))
                                .clickable { item.seriesId?.let(callbacks.onNavigateToSeries) }
                                .padding(vertical = 2.dp),
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
                    }
                    if (episodeContext.isNotBlank() || item.isPlayed) {
                        FadingItem {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (episodeContext.isNotBlank()) {
                                        Text(
                                            text = episodeContext,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    if (item.isPlayed) {
                                        EpisodeWatchedTag(
                                            label = stringResource(R.string.detail_watched_badge),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
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
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
                                    modifier = Modifier.size(16.dp),
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
                                        modifier = Modifier.size(16.dp),
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
                        SegmentAvailabilityChip(
                            hasIntro = state.hasIntroSegment,
                            hasCredits = state.hasCreditSegment,
                        )
                    }
                }

                item.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                    Spacer(Modifier.height(14.dp))
                    // Genre chips: tappable → a filtered library section (REMOTE only).
                    // LOCAL origin renders as non-clickable labels — gated by
                    // capabilities.tagNavigation so a local origin never offers a
                    // drill-in it can't fulfill. Mirrors the studio chip pattern.
                    val genreNavEnabled = state.capabilities.tagNavigation
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .tvFocusRestorer(),
                    ) {
                        items(genres, key = { it }, contentType = { "genre" }) { genre ->
                            FadingItem {
                                TagChip(
                                    label = genre,
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                    enabled = genreNavEnabled,
                                    onClick = {
                                        callbacks.onNavigate(
                                            com.raulshma.jellyplay.core.ui.navigation.Route.LibrarySection(
                                                title = genre,
                                                genre = genre,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                detail.tagItems.takeIf { it.isNotEmpty() && state.capabilities.tagNavigation }?.let { tags ->
                    Spacer(Modifier.height(10.dp))
                    // Tag chips: tappable → a filtered library section (REMOTE only).
                    // Same tagNavigation gate as genres; tagItems carry a name + id
                    // but the library query filters tags by name, so name is all we pass.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .tvFocusRestorer(),
                    ) {
                        items(tags, key = { it.name }, contentType = { "tag" }) { tag ->
                            FadingItem {
                                TagChip(
                                    label = tag.name,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.95f),
                                    onClick = {
                                        callbacks.onNavigate(
                                            com.raulshma.jellyplay.core.ui.navigation.Route.LibrarySection(
                                                title = tag.name,
                                                tag = tag.name,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                detail.studios.takeIf { it.isNotEmpty() }?.let { studios ->
                    Spacer(Modifier.height(10.dp))
                    // Studio chips: REMOTE keeps click -> StudioDetail; LOCAL
                    // (detail.item.studios names only, no server id) renders as
                    // non-clickable labels. Gated by capabilities.studioNavigation
                    // so a local origin never offers a drill-in it can't fulfill.
                    val studioNavEnabled = state.capabilities.studioNavigation
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
                                        .then(if (studioNavEnabled) studioFocusState.focusModifier else Modifier)
                                        .then(if (studioNavEnabled) Modifier.tvFocusIndicator(studioFocusState, ShapeCache.smooth16) else Modifier)
                                        .then(
                                            if (studioNavEnabled) {
                                                Modifier.clickable {
                                                    callbacks.onNavigate(
                                                        com.raulshma.jellyplay.core.ui.navigation.Route.StudioDetail(studio.id, studio.name),
                                                    )
                                                }
                                            } else Modifier,
                                        )
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

                // ── Aggregate local-series header ("N episodes · size") ──
                // Mirrors OfflineSeriesScreen. Rendered only for a local series
                // (detailContext.seriesAggregate != null on a SERIES item). Placed
                // in the title metadata block so the count is visible alongside
                // genres/studios.
                if (item.mediaType == MediaType.SERIES) {
                    state.detailContext?.seriesAggregate?.let { aggregate ->
                    if (aggregate.downloadedEpisodeCount > 0 || aggregate.totalSizeBytes > 0L) {
                        Spacer(Modifier.height(10.dp))
                        FadingItem {
                            val parts = buildList {
                                if (aggregate.downloadedEpisodeCount > 0) {
                                    add(
                                        pluralStringResource(
                                            R.plurals.detail_episodes_count,
                                            aggregate.downloadedEpisodeCount,
                                            aggregate.downloadedEpisodeCount,
                                        ),
                                    )
                                }
                                if (aggregate.totalSizeBytes > 0L) {
                                    add(aggregate.totalSizeBytes.formatBytes())
                                }
                            }
                            Text(
                                text = parts.joinToString(" · "),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            // Stream selection is source-aware:
            //  - REMOTE (remoteStreamSelection): full MediaInfoSection with audio
            //    + subtitle inventories from the server MediaSource.
            //  - LOCAL (localStreamInfo): read-only quality/audio badges probed
            //    from the downloaded file, plus the manifest-backed
            //    LocalSubtitlePicker. Audio is switched in the player, not here.
            //  - LOCAL subtitles only (localSubtitleSelection): LocalSubtitlePicker
            //    alone — the file couldn't be probed (missing/corrupt/legacy).
            // Each branch is gated independently so a plain remote item with no
            // source still renders nothing.
            val source = detail.mediaSources.firstOrNull()
            when {
                state.capabilities.remoteStreamSelection && source != null -> {
                    MediaInfoSection(
                        mediaStreams = source.mediaStreams,
                        selectedAudioIndex = state.selectedAudioIndex,
                        selectedSubtitleIndex = state.selectedSubtitleIndex,
                        onAudioSelect = callbacks.onAudioSelect,
                        onSubtitleSelect = callbacks.onSubtitleSelect,
                        preferences = state.preferences,
                    )
                }
                state.capabilities.localStreamInfo && source != null -> {
                    // Quality + audio (read-only, probed) share a single badge row
                    // with the local subtitle pill, matching the remote section's
                    // 3-pill layout. The pill always renders (OFF when the manifest
                    // advertises no bundled subtitles); it is only interactive when
                    // a list is passed.
                    LocalMediaInfoSection(
                        mediaStreams = source.mediaStreams,
                        subtitles = if (state.capabilities.localSubtitleSelection) state.localSubtitles else emptyList(),
                        selectedSubtitleIndex = state.selectedLocalSubtitleIndex,
                        onSelectSubtitle = callbacks.onSelectLocalSubtitle,
                        horizontalPadding = bodyContentPad,
                    )
                }
                state.capabilities.localSubtitleSelection -> {
                    LocalSubtitlePicker(
                        subtitles = state.localSubtitles,
                        selectedIndex = state.selectedLocalSubtitleIndex,
                        onSelect = callbacks.onSelectLocalSubtitle,
                        modifier = Modifier.padding(horizontal = bodyContentPad),
                    )
                }
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

        // ── Chapters ──
        // MediaDetail.chapters is loaded for every item but was never surfaced
        // on the detail screen (only the player's ChapterPickerSheet used it).
        // Render it as a tappable thumbnail row that resumes the player at the
        // chapter's start position. Gated by capabilities.chapters (remote +
        // non-empty) so a local origin never offers a drill-in it can't fulfill.
        StaggeredDetailSection(visible = showContent && state.capabilities.chapters, delayIndex = 4) {
            val chapters = state.detail.chapters
            if (chapters.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = stringResource(R.string.detail_section_chapters),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier
                                .padding(horizontal = bodyContentPad)
                                .semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // ChapterInfo has no id and startPositionTicks isn't guaranteed
                    // unique — Jellyfin can emit duplicate positions (seen in the wild),
                    // which crashes LazyRow with a duplicate key. Pair each chapter with
                    // its list index so the key is collision-free. The index is already
                    // the stable identity used for /Images/Chapter/{index} lookups below.
                    TvFocusableItemRow(
                        items = chapters.mapIndexed { i, c -> i to c },
                        key = { (index, chapter) -> "chapter_${index}_${chapter.startPositionTicks}" },
                        contentPadding = PaddingValues(horizontal = bodyContentPad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { _, (index, chapter), focusModifier ->
                        val chapterImageUrl = remember(item.id, index, chapter.imageTag) {
                            callbacks.getChapterImageUrl(item.id, index, chapter.imageTag)
                        }
                        val chapterClick = remember(chapter.startPositionTicks) {
                            { callbacks.onPlayChapter(chapter.startPositionTicks) }
                        }
                        ChapterTile(
                            name = chapter.name,
                            imageUrl = chapterImageUrl,
                            timestamp = com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks(
                                chapter.startPositionTicks,
                            ),
                            onClick = chapterClick,
                            modifier = focusModifier,
                        )
                    }
                }
            }
        }

        // ── Download info card + freshness banner ──
        // Rendered for a snapshot with an attached download (remote-with-download
        // OR local origin). Gated so a plain remote-no-download item never shows
        // it. Placed after the overview to match the offline screen's ordering.
        // The seriesAggregate-only local series (no per-item download) is handled
        // by the header above; this card is per-item.
        val attachedDownload = state.detailContext?.download
        val isLocalOrigin = state.origin?.isLocal == true
        val showDownloadCard = showContent && (attachedDownload != null || isLocalOrigin)
        if (showDownloadCard) StaggeredDetailSection(visible = true, delayIndex = 4) {
            Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
                // Freshness banner: surfaces a server-detected update or a media-file
                // change that needs a full re-download. Tappable -> opens the resync
                // sheet. Hidden when there's nothing to act on.
                SyncUpdateBanner(
                    syncState = state.detailContext?.syncState,
                    resyncState = state.resyncState,
                    onClick = callbacks.onOpenResync,
                )
                DownloadInfoCard(
                    download = attachedDownload,
                    item = item,
                    onClick = callbacks.onOpenDownloadDetails,
                )
            }
        }

        StaggeredDetailSection(visible = showContent && isAudio && state.albumTracks.isNotEmpty(), delayIndex = 5) {
            Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
                FadingItem {
                    Text(
                        text = stringResource(R.string.detail_section_tracks),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
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

        if (showContent && item.mediaType == MediaType.SERIES && state.smartPlayTarget != null && state.preferences.showDetailUpNext) {
            StaggeredDetailSection(visible = showContent, delayIndex = 6) {
                Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
                    UpNextSection(
                        target = state.smartPlayTarget,
                        onPlayClick = {
                            val target = state.smartPlayTarget
                            val sourceId = null
                            callbacks.onPlayClick(target.episode.id, sourceId, target.startPositionTicks)
                        },
                        onHideClick = callbacks.onHideDetailUpNext,
                    )
                }
            }
        }

        StaggeredDetailSection(visible = showContent, delayIndex = 6) {
            val showSeasons = (item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE) && state.seasons.isNotEmpty()
            if (showSeasons) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    // Episode-card preferences: hideEpisodeThumbnails and skipSpecials
                    // are neutralized for a LOCAL origin (see MediaDetailSeasons
                    // "DEFERRED FOR LOCAL ORIGIN") — local cards always show art
                    // (hiding without guaranteed local artwork yields blank tiles)
                    // and render every season. Episode sort ([episodesDescending])
                    // IS honored for a local origin since offline episodes load in
                    // canonical ascending playback order, same as online.
                    val effectiveSkipSpecials = !isLocalOrigin && state.preferences.skipSpecials
                    val effectiveHideThumbnails = !isLocalOrigin && state.preferences.hideEpisodeThumbnails
                    val effectiveEpisodesDescending = state.preferences.episodesDescending
                    // Memoize the skip-specials filter so it is not recomputed (allocating
                    // a new Map + per-season Lists) on every recomposition of this
                    // detail item (scroll-driven FadingItem animations, sibling
                    // sections animating). Only re-runs when episodes or the
                    // skipSpecials flag actually change.
                    val filteredEpisodes = remember(state.episodes, effectiveSkipSpecials) {
                        if (effectiveSkipSpecials) {
                            state.episodes.mapValues { (_, eps) -> eps.filter { it.seasonNumber != 0 } }
                        } else {
                            state.episodes
                        }
                    }
                    val downloadedEpisodeIds = remember(isLocalOrigin, state.episodes, state.downloadedEpisodeIds) {
                        when {
                            isLocalOrigin -> state.episodes.values.flatten().map { it.id }.toSet()
                            state.downloadedEpisodeIds.isNotEmpty() -> state.downloadedEpisodeIds
                            else -> null
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
                        persistedSeasonId = state.persistedSeasonId,
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
                        onSeasonPinned = callbacks.onSeasonPinned,
                        hideEpisodeThumbnails = effectiveHideThumbnails,
                        episodesDescending = effectiveEpisodesDescending,
                        onEpisodesDescendingChange = callbacks.onEpisodesDescendingChange,
                        compactEpisodeList = state.preferences.compactEpisodeList,
                        onCompactEpisodeListChange = callbacks.onCompactEpisodeListChange,
                        onMarkSeasonPlayed = callbacks.onMarkSeasonPlayed,
                        onMarkSeasonUnplayed = callbacks.onMarkSeasonUnplayed,
                        // ── Episode parity: per-episode delete + local artwork ──
                        // Downloaded-episode set: for a LOCAL origin every episode is
                        // downloaded; for a REMOTE series we surface the loaded
                        // downloadedEpisodeIds (populated when the download sheet
                        // opened) so the trash badge matches the on-disk truth.
                        downloadedEpisodeIds = downloadedEpisodeIds,
                        onEpisodeDeleteClick = { episode -> callbacks.onDeleteEpisode(episode.id) },
                        // Resolve a downloaded episode thumbnail from DetailAssets before
                        // falling back to the server image url (which won't load offline).
                        getEpisodeLocalImagePath = { episode -> state.assets.episodeImages[episode.id] },
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
                            modifier = Modifier
                                .padding(horizontal = bodyContentPad)
                                .semantics { heading() },
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
                    // "See all" appears only when the cast is large enough to hide
                    // people behind the single row, and only for a navigable (remote)
                    // origin — the Cast & Crew screen re-fetches the full people list.
                    val showSeeAllCast = detail.people.size > 12 && state.capabilities.personNavigation
                    FadingItem {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = bodyContentPad),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.detail_section_cast_crew),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.semantics { heading() },
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (showSeeAllCast) {
                                val seeAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                                Text(
                                    text = stringResource(R.string.detail_see_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(ShapeCache.smooth16)
                                        .then(seeAllFocusState.focusModifier)
                                        .then(Modifier.tvFocusIndicator(seeAllFocusState, ShapeCache.smooth16))
                                        .clickable { callbacks.onSeeAllCast() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        // Cast rendering branches on capabilities.personNavigation:
                        //  - REMOTE (personNavigation true): existing PersonItem,
                        //    click -> onPersonClick, image via getImageUrl.
                        //  - LOCAL (personNavigation false): OfflinePersonItem with
                        //    the on-disk cast portrait (assets.castImages[id])
                        //    preferred over the server URL fallback, NO click.
                        val personNavEnabled = state.capabilities.personNavigation
                        TvFocusableItemRow(
                            items = detail.people,
                            key = { "person_${it.id}" },
                            contentPadding = PaddingValues(horizontal = bodyContentPad),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) { _, person, focusModifier ->
                            if (personNavEnabled) {
                                val personClick = remember(person.id) { { callbacks.onPersonClick(person.id) } }
                                val personImageUrl = remember(person.id) { callbacks.getImageUrl(person.id) }
                                PersonItem(
                                    person = person,
                                    imageUrl = personImageUrl,
                                    onClick = personClick,
                                    modifier = focusModifier,
                                )
                            } else {
                                // Local portrait preferred, then server URL fallback.
                                val localPortrait = state.assets.castImages[person.id]
                                val personImageUrl = remember(person.id, localPortrait) {
                                    localPortrait ?: callbacks.getImageUrl(person.id)
                                }
                                com.raulshma.jellyplay.core.ui.components.OfflinePersonItem(
                                    person = person.toOfflinePersonInfo(),
                                    imageUrl = personImageUrl,
                                    modifier = focusModifier,
                                    // No onClick — local persons have no server id to drill into.
                                )
                            }
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
            // A LOCAL origin has no server "similar" list, so it shows on-device
            // titles mined from the offline library (localRelatedItems) instead;
            // remote keeps the server-sourced relatedItems. Either way the row
            // renders identically — only the source + an "On-device" badge differ.
            val isLocalOrigin = state.origin?.isLocal == true
            val moreLikeThis = if (isLocalOrigin) state.localRelatedItems else state.relatedItems
            if (moreLikeThis.isNotEmpty()) {
                Column {
                    FadingItem {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = bodyContentPad),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.detail_section_more_like_this),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.semantics { heading() },
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (isLocalOrigin) {
                                Text(
                                    text = stringResource(R.string.detail_more_like_this_on_device),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(ShapeCache.smooth16)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Compute the card width once from the already-read adaptiveInfo
                    // instead of re-reading LocalAdaptiveInfo.current inside each
                    // visible item lambda (one CompositionLocal read per item).
                    val relatedCardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                    TvFocusableItemRow(
                        items = moreLikeThis,
                        key = { "related_${it.id}" },
                        contentPadding = PaddingValues(horizontal = bodyContentPad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        onFocusedIndexChange = { index ->
                            moreLikeThis.getOrNull(index)?.let(callbacks.onFocusedMediaItem)
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

        // ── Special Features / Extras ──
        // Featurettes, deleted scenes, interviews, etc. attached server-side via
        // Jellyfin's /Items/{id}/SpecialFeatures. Rendered as a tappable poster row
        // (matching "More like this") so an extra plays in the video player on tap.
        // Remote-only: the fetch is gated by capabilities.remoteDiscovery (see
        // triggerRemoteSideEffects), and an empty list hides the section entirely.
        StaggeredDetailSection(
            visible = showContent && state.capabilities.remoteDiscovery && state.specialFeatures.isNotEmpty(),
            delayIndex = 13,
        ) {
            val extras = state.specialFeatures
            Column {
                FadingItem {
                    Text(
                        text = stringResource(R.string.detail_section_special_features),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .padding(horizontal = bodyContentPad)
                            .semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Compute the card width once from the already-read adaptiveInfo
                // instead of re-reading LocalAdaptiveInfo.current inside each
                // visible item lambda (one CompositionLocal read per item).
                val extraCardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                TvFocusableItemRow(
                    items = extras,
                    key = { "extra_${it.id}" },
                    contentPadding = PaddingValues(horizontal = bodyContentPad),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    onFocusedIndexChange = { index ->
                        extras.getOrNull(index)?.let(callbacks.onFocusedMediaItem)
                    },
                ) { _, extra, focusModifier ->
                    val extraClick = remember(extra.id) { { callbacks.onPlayExtra(extra) } }
                    val extraImageUrl = remember(extra.id) { callbacks.getImageUrl(extra.id) }
                    PosterCard(
                        item = extra,
                        imageUrl = extraImageUrl,
                        onClick = extraClick,
                        modifier = focusModifier.width(extraCardWidth),
                    )
                }
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
                modifier = Modifier
                    .padding(horizontal = bodyContentPad)
                    .semantics { heading() },
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
private fun ChapterTile(
    name: String,
    imageUrl: String,
    timestamp: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.04f)
    Column(
        modifier = modifier
            .width(180.dp)
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .height(104.dp)
                .fillMaxWidth()
                .clip(ShapeCache.smooth12)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotEmpty()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val videoCardBorder = remember(isSynthwave, isSoothing, primaryColor, secondaryColor, outlineColor) {
        when {
            isSynthwave -> {
                androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor)
                    )
                )
            }
            isSoothing -> {
                androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = outlineColor.copy(alpha = 0.35f)
                )
            }
            else -> null
        }
    }
    // Same hoist for the per-card bottom scrim: identical for every video,
    // so build it once instead of per card per recomposition.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val videoScrimBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.85f)
            ),
            startY = 100f
        )
    }
    Column {
        FadingItem {
            Text(
                text = stringResource(R.string.detail_section_videos),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(horizontal = bodyContentPad)
                    .semantics { heading() },
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
                                    .background(videoScrimBrush)
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

@Composable
internal fun UpNextSection(
    target: DetailUiState.SmartPlayTarget,
    onPlayClick: () -> Unit,
    onHideClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "upNextCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "upNextPlayScale",
    )

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val borderModifier = remember(isSynthwave, isSoothing, primaryColor, secondaryColor, outlineColor) {
        when {
            isSynthwave -> Modifier.border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor, secondaryColor)
                ),
                shape = ShapeCache.smooth16,
            )
            isSoothing -> Modifier.border(
                width = 0.8.dp,
                color = outlineColor.copy(alpha = 0.35f),
                shape = ShapeCache.smooth16,
            )
            else -> Modifier
        }
    }

    val cardFocusState = rememberTvFocusState(focusedScale = 1.02f)
    val hideFocusState = rememberTvFocusState(focusedScale = 1.1f)
    val confirmHaptic = com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic()

    Column(modifier = modifier.fillMaxWidth()) {
        FadingItem {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_up_next),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .then(hideFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(hideFocusState, ShapeCache.smooth16))
                        .clickable {
                            confirmHaptic()
                            onHideClick()
                        },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = ShapeCache.smooth16,
                ) {
                    Icon(
                        imageVector = Tabler.Outline.EyeOff,
                        contentDescription = stringResource(R.string.detail_cd_hide_up_next),
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
            }
        }

        FadingItem {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(borderModifier)
                    .clip(ShapeCache.smooth16)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                    .then(cardFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
                    .clickable(
                        interactionSource = cardInteractionSource,
                        indication = null,
                        onClick = {
                            confirmHaptic()
                            onPlayClick()
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 90.dp)
                        .clip(ShapeCache.smooth16),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaImage(
                        url = target.primaryImageUrl ?: "",
                        contentDescription = target.episode.name,
                        blurHash = target.episode.blurHashes.primary,
                        size = coil3.size.Size(480, 270),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))
                    val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
                    Icon(
                        Tabler.Outline.PlayerPlay,
                        contentDescription = stringResource(R.string.detail_cd_episode_play),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer { scaleX = playScale; scaleY = playScale }
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                            .then(epPlayFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(epPlayFocusState, CircleShape))
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null,
                                onClick = {
                                    confirmHaptic()
                                    onPlayClick()
                                },
                            )
                            .padding(8.dp),
                    )
                    val t = target.startPositionTicks
                    val rt = target.episode.runTimeTicks
                    if (t > 0 && rt != null && rt > 0) {
                        val progress = (t.toFloat() / rt).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = target.episode.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val runtimeTicks = target.episode.runTimeTicks
                    val positionTicks = target.startPositionTicks
                    val hasWatchProgress = positionTicks > 0
                    val remainingTime = if (hasWatchProgress && runtimeTicks != null && runtimeTicks > 0) {
                        com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
                    } else null
                    val totalTime = if (runtimeTicks != null) {
                        com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks(runtimeTicks)
                    } else null

                    if (remainingTime != null && totalTime != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.detail_time_left_format, remainingTime),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Text(
                                text = totalTime,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (totalTime != null) {
                        Text(
                            text = totalTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    target.episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                        )
                    }
                }
            }
        }
    }
}
