package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Star
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.progressFraction
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag
import com.raulshma.jellyplay.core.ui.components.ExpandableText
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.formatRuntimeLabelFromTicks
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.OfflinePersonItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_finished_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_format_comic
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_page_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_percent_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_episode_play
import com.raulshma.jellyplay.feature.details.generated.resources.detail_episodes_count
import com.raulshma.jellyplay.feature.details.generated.resources.detail_more_like_this_on_device
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_cast_crew
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_chapters
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_items
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_more_like_this
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seerr_recommendations
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seerr_similar
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_special_features
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_tracks
import com.raulshma.jellyplay.feature.details.generated.resources.detail_see_all
import com.raulshma.jellyplay.feature.details.generated.resources.detail_time_left_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_up_next
import com.raulshma.jellyplay.feature.details.generated.resources.detail_watched_badge
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The detail body's per-family section renderers, decomposed out of the former
 * ~1,060-line inline [DetailContentBody] column.
 *
 * The trunk ([DetailContentBody]) walks the ordered list [DetailSectionAdmission]
 * produces and dispatches each [DetailSectionKind] to one renderer here. Every
 * renderer owns exactly one [StaggeredDetailSection] slot, receives its
 * `delayIndex` from the admission descriptor (never re-states it), and keeps
 * the former inline body verbatim — same content gates, same remember keys,
 * same focus choreography. Sections whose slot used to compose empty (seasons
 * on a movie, overview without a synopsis, ...) still render their empty slot:
 * membership and the inner content gate are deliberately separate layers, so
 * the body's vertical rhythm is byte-identical.
 */

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
internal fun TagChip(
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

/**
 * One horizontal chip row for the header's metadata facets (genres / tags /
 * studios). The three rows were hand-copied LazyRows differing only in items,
 * colours, navigability, contentType hint and the route their taps fire; the
 * studio row additionally re-open-coded the [TagChip] chain inline — every chip
 * now renders through [TagChip] (identical clip → background → focus → click →
 * padding chain and `focusedScale = 1.05f`).
 */
@Composable
private fun <T> DetailChipRow(
    chips: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    containerColor: Color,
    contentColor: Color,
    navigable: Boolean,
    onClick: (T) -> Unit,
    contentType: String? = null,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .tvFocusRestorer(),
    ) {
        items(chips, key = key, contentType = { contentType }) { chip ->
            FadingItem {
                TagChip(
                    label = label(chip),
                    containerColor = containerColor,
                    contentColor = contentColor,
                    enabled = navigable,
                    onClick = { onClick(chip) },
                )
            }
        }
    }
}

/**
 * The "prefer logos" detail title: the item's server clear-logo rendered at the
 * cinematic 2.39:1 ratio with the text title kept as the accessibility label
 * AND as the automatic fallback — the text shows while the logo loads and
 * permanently on a Coil error, so a missing/broken logo never blanks the title
 * block. The text title is never rendered alongside a successfully loaded logo.
 */
@Composable
private fun DetailLogoTitle(
    title: String,
    logoUrl: String,
    modifier: Modifier = Modifier,
) {
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth(LOGO_TITLE_WIDTH_FRACTION)
            .aspectRatio(LOGO_TITLE_ASPECT_RATIO)
            .semantics { heading() },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!logoLoaded) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!logoFailed) {
            AsyncImage(
                model = logoUrl,
                // The text title is the logo's accessibility label; suppressed
                // while the text is on screen so it is never announced twice.
                contentDescription = if (logoLoaded) title else null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> logoLoaded = true
                        is AsyncImagePainter.State.Error -> logoFailed = true
                        else -> Unit
                    }
                },
            )
        }
    }
}

/** Width of the logo title as a fraction of the detail body width. */
private const val LOGO_TITLE_WIDTH_FRACTION = 0.5f

/** Cinematic clear-logo aspect (the ratio Jellyfin documents for Logo art). */
private const val LOGO_TITLE_ASPECT_RATIO = 2.39f


// ── Header (delayIndex 0) ─────────────────────────────────────────────────

@Composable
internal fun DetailHeaderSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    detail: MediaDetail,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    // Book branch: the shared body flow stays, but the metadata row, the
    // reading card and the Contents section come from the book state (null
    // for every other media type), and the video-only sections hide.
    val isBook = item.mediaType == MediaType.BOOK
    val book = state.book

    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
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
                            .clickable { item.seriesId?.let(callbacks.navigation.onNavigateToSeries) }
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
                                        label = stringResource(Res.string.detail_watched_badge),
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }

            FadingItem {
                val logoTag = detail.logoImageTag
                val logoUrl = remember(item.id, logoTag) {
                    if (logoTag != null) callbacks.artwork.getLogoUrl(item.id) else ""
                }
                if (preferLogoTitleEnabled(state.preferences.preferLogos, logoTag, logoUrl)) {
                    DetailLogoTitle(
                        title = item.name,
                        logoUrl = logoUrl,
                    )
                } else {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
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

            // Book author line — the one credit a book carries. From
            // whichever field the server populated (person or artist item).
            if (isBook) {
                bookAuthorLabel(detail, item)?.let { author ->
                    FadingItem {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = author,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                    formatRuntimeLabelFromTicks(item.runTimeTicks)?.let { runtime ->
                        Text(
                            text = runtime,
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
                    // Book meta: file format + reading progress (the
                    // video-oriented year/runtime/rating set above renders
                    // mostly empty for books; these are the slots that count).
                    if (isBook && book != null) {
                        val comicLabel = stringResource(Res.string.detail_book_format_comic)
                        Text(
                            text = bookFormatLabel(book.format, comicLabel),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        when (
                            val progress = resolveBookReadingProgress(
                                isPlayed = item.isPlayed,
                                ticks = item.playbackPositionTicks ?: 0L,
                                format = book.format,
                                pageCount = book.pageCount,
                            )
                        ) {
                            BookReadingProgress.Finished -> {
                                Box(
                                    modifier = Modifier
                                        .clip(ShapeCache.smooth4)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.detail_book_finished_badge),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            is BookReadingProgress.Percent -> if (progress.percent > 0f) {
                                Text(
                                    text = stringResource(
                                        Res.string.detail_book_percent_progress,
                                        (progress.percent * 100).toInt().coerceIn(0, 100),
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            is BookReadingProgress.Pages -> {
                                Text(
                                    text = stringResource(
                                        Res.string.detail_book_page_progress,
                                        progress.page,
                                        progress.pageCount,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            BookReadingProgress.NotStarted, BookReadingProgress.Unknown -> Unit
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
                DetailChipRow(
                    chips = genres,
                    key = { it },
                    contentType = "genre",
                    navigable = genreNavEnabled,
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                    label = { it },
                    onClick = { genre ->
                        callbacks.navigation.onNavigate(
                            Route.LibrarySection(
                                title = genre,
                                genre = genre,
                            ),
                        )
                    },
                )
            }

            detail.tagItems.takeIf { it.isNotEmpty() && state.capabilities.tagNavigation }?.let { tags ->
                Spacer(Modifier.height(10.dp))
                // Tag chips: tappable → a filtered library section (REMOTE only).
                // Same tagNavigation gate as genres; tagItems carry a name + id
                // but the library query filters tags by name, so name is all we pass.
                DetailChipRow(
                    chips = tags,
                    key = { it.name },
                    contentType = "tag",
                    navigable = true,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.95f),
                    label = { it.name },
                    onClick = { tag ->
                        callbacks.navigation.onNavigate(
                            Route.LibrarySection(
                                title = tag.name,
                                tag = tag.name,
                            ),
                        )
                    },
                )
            }

            detail.studios.takeIf { it.isNotEmpty() && !isBook }?.let { studios ->
                Spacer(Modifier.height(10.dp))
                // Studio chips: REMOTE keeps click -> StudioDetail; LOCAL
                // (detail.item.studios names only, no server id) renders as
                // non-clickable labels. Gated by capabilities.studioNavigation
                // so a local origin never offers a drill-in it can't fulfill.
                val studioNavEnabled = state.capabilities.studioNavigation
                DetailChipRow(
                    chips = studios,
                    key = { it.id },
                    contentType = "studio",
                    navigable = studioNavEnabled,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f),
                    label = { it.name },
                    onClick = { studio ->
                        callbacks.navigation.onNavigate(
                            Route.StudioDetail(studio.id, studio.name),
                        )
                    },
                )
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
                                            Res.plurals.detail_episodes_count,
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
}

// ── Action row (delayIndex 1) ─────────────────────────────────────────────

@Composable
internal fun DetailActionRowSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    contentFocusRequester: FocusRequester?,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        DetailActionButtons(
            state = state,
            callbacks = callbacks,
            vertical = false,
            contentFocusRequester = contentFocusRequester,
        )
    }
}

// ── Book (delayIndex 2 reading card + TOC half of slot 4) ─────────────────

@Composable
internal fun DetailBookReadingCardSection(
    delayIndex: Int,
    state: DetailContentState,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    // ── Book reading card ──
    // Progress bar + percent/pages + local marks counts. Occupies the
    // media-info slot (delayIndex 2) which never renders for books.
    val book = state.book
    StaggeredDetailSection(visible = book != null, delayIndex = delayIndex) {
        book?.let { bookState ->
            val progress = resolveBookReadingProgress(
                isPlayed = item.isPlayed,
                ticks = item.playbackPositionTicks ?: 0L,
                format = bookState.format,
                pageCount = bookState.pageCount,
            )
            FadingItem {
                BookReadingCard(
                    format = bookState.format,
                    progress = progress,
                    bookmarkCount = bookState.bookmarkCount,
                    highlightCount = bookState.highlightCount,
                    modifier = Modifier.padding(horizontal = bodyContentPad),
                )
            }
        }
    }
}

// ── Media info (delayIndex 2) ─────────────────────────────────────────────

@Composable
internal fun DetailMediaInfoSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    detail: MediaDetail,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(visible = !item.mediaType.isAudioType, delayIndex = delayIndex) {
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
                    onAudioSelect = callbacks.playback.onAudioSelect,
                    onSubtitleSelect = callbacks.playback.onSubtitleSelect,
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
                    onSelectSubtitle = callbacks.playback.onSelectLocalSubtitle,
                    horizontalPadding = bodyContentPad,
                )
            }
            state.capabilities.localSubtitleSelection -> {
                LocalSubtitlePicker(
                    subtitles = state.localSubtitles,
                    selectedIndex = state.selectedLocalSubtitleIndex,
                    onSelect = callbacks.playback.onSelectLocalSubtitle,
                    modifier = Modifier.padding(horizontal = bodyContentPad),
                )
            }
        }
    }
}

// ── Overview (delayIndex 3) ───────────────────────────────────────────────

@Composable
internal fun DetailOverviewSection(
    delayIndex: Int,
    state: DetailContentState,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
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
}

// ── Chapters / Contents (delayIndex 4) ────────────────────────────────────

@Composable
internal fun DetailChaptersSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    detail: MediaDetail,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    // ── Chapters / Contents ──
    // Books render the local TOC cache as a vertical, expandable list that
    // deep-links into the reader (href for EPUB, page for PDF); video keeps
    // the server chapter thumbnail row that resumes the player at the
    // chapter's start position (gated by capabilities.chapters so an item
    // without chapter data never offers a drill-in it can't fulfill).
    val book = state.book
    val showBookToc = item.mediaType == MediaType.BOOK && !book?.toc.isNullOrEmpty()
    StaggeredDetailSection(
        visible = showBookToc || (state.capabilities.chapters && detail.chapters.isNotEmpty()),
        delayIndex = delayIndex,
    ) {
        if (showBookToc) {
            FadingItem {
                BookTocSection(
                    toc = book?.toc.orEmpty(),
                    contentPadding = bodyContentPad,
                    onJump = { entry ->
                        callbacks.playback.onReadClick(item.id, entry.href, entry.page)
                    },
                )
            }
        } else {
            val chapters = detail.chapters
            if (chapters.isNotEmpty()) {
                Column {
                    FadingItem {
                        Text(
                            text = stringResource(Res.string.detail_section_chapters),
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
                            callbacks.artwork.getChapterImageUrl(item.id, index, chapter.imageTag)
                        }
                        val chapterClick = remember(chapter.startPositionTicks) {
                            { callbacks.playback.onPlayChapter(chapter.startPositionTicks) }
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
    }
}

// ── Music (delayIndex 5) ──────────────────────────────────────────────────

@Composable
internal fun DetailAlbumTracksSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(
        visible = item.mediaType.isAudioType && state.albumTracks.isNotEmpty(),
        delayIndex = delayIndex,
    ) {
        Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
            FadingItem {
                Text(
                    text = stringResource(Res.string.detail_section_tracks),
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
                    val trackClick = remember(track.id) { { callbacks.navigation.onItemClick(track.id) } }
                    val trackPlayClick = remember(track.id, index) { { callbacks.playback.onPlayAlbumTrack(index); callbacks.navigation.onItemClick(track.id) } }
                    val trackImageUrl = remember(track.id) { callbacks.artwork.getImageUrl(track.id) }
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
}

// ── Up Next card (delayIndex 6, ahead of seasons) ─────────────────────────

@Composable
internal fun DetailUpNextCardSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    bodyContentPad: Dp,
) {
    // The admission fold guarantees SERIES + a resolved target + the preference
    // on. The card captures the target it was composed with; a preference flip
    // or a resolved target change recomposes the whole slot before any tap can
    // land on it.
    val target = state.smartPlayTarget ?: return
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
            UpNextSection(
                target = target,
                onPlayClick = {
                    val sourceId = null
                    callbacks.playback.onPlayClick(target.episode.id, sourceId, target.startPositionTicks)
                },
                onHideClick = callbacks.userData.onHideDetailUpNext,
            )
        }
    }
}

// ── Seasons (delayIndex 6, after up-next) ─────────────────────────────────

@Composable
internal fun DetailSeasonsSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    item: MediaItem,
    isLocalOrigin: Boolean,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
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
                    getImageUrl = callbacks.artwork.getImageUrl,
                    currentItemId = if (item.mediaType == MediaType.EPISODE) item.id else null,
                    currentSeasonId = if (item.mediaType == MediaType.EPISODE) item.seasonId else null,
                    persistedSeasonId = state.persistedSeasonId,
                    onEpisodePlayClick = { episode ->
                        val sourceId = null
                        val startPos = episode.playbackPositionTicks ?: 0L
                        callbacks.playback.onPlayClick(episode.id, sourceId, startPos)
                    },
                    onEpisodeDetailClick = { episode ->
                        callbacks.navigation.onItemClick(episode.id)
                    },
                    onEpisodeLongPress = callbacks.screen.onMediaQuickActions,
                    onFocusedEpisodeChange = callbacks.screen.onFocusedMediaItem,
                    onSeasonSelected = callbacks.seasons.onSeasonSelected,
                    onSeasonPinned = callbacks.seasons.onSeasonPinned,
                    hideEpisodeThumbnails = effectiveHideThumbnails,
                    episodesDescending = effectiveEpisodesDescending,
                    onEpisodesDescendingChange = callbacks.seasons.onEpisodesDescendingChange,
                    compactEpisodeList = state.preferences.compactEpisodeList,
                    onCompactEpisodeListChange = callbacks.seasons.onCompactEpisodeListChange,
                    onMarkSeasonPlayed = callbacks.seasons.onMarkSeasonPlayed,
                    onMarkSeasonUnplayed = callbacks.seasons.onMarkSeasonUnplayed,
                    // ── Episode parity: per-episode delete + local artwork ──
                    // Downloaded-episode set: for a LOCAL origin every episode is
                    // downloaded; for a REMOTE series we surface the loaded
                    // downloadedEpisodeIds (populated when the download sheet
                    // opened) so the trash badge matches the on-disk truth.
                    downloadedEpisodeIds = downloadedEpisodeIds,
                    onEpisodeDeleteClick = { episode -> callbacks.download.onDeleteEpisode(episode.id) },
                    // Resolve a downloaded episode thumbnail from DetailAssets before
                    // falling back to the server image url (which won't load offline).
                    getEpisodeLocalImagePath = { episode -> state.assets.episodeImages[episode.id] },
                )
            }
        }
    }
}

// ── Collection (delayIndex 7) ─────────────────────────────────────────────

@Composable
internal fun DetailCollectionItemsSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        if (item.mediaType == MediaType.COLLECTION && state.collectionItems.isNotEmpty()) {
            Column {
                FadingItem {
                    Text(
                        text = stringResource(Res.string.detail_section_items),
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
                        state.collectionItems.getOrNull(index)?.let(callbacks.screen.onFocusedMediaItem)
                    },
                ) { _, collectionItem, focusModifier ->
                    val collectionClick = remember(collectionItem.id) { { callbacks.navigation.onItemClick(collectionItem.id) } }
                    val collectionProgress = collectionItem.progressFraction()
                    val collectionImageUrl = remember(collectionItem.id) { callbacks.artwork.getImageUrl(collectionItem.id) }
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
}

// ── Cast & crew (delayIndex 8) ────────────────────────────────────────────

@Composable
internal fun DetailCastSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    detail: MediaDetail,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(visible = item.mediaType != MediaType.BOOK, delayIndex = delayIndex) {
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
                            text = stringResource(Res.string.detail_section_cast_crew),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (showSeeAllCast) {
                            val seeAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                            Text(
                                text = stringResource(Res.string.detail_see_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(ShapeCache.smooth16)
                                    .then(seeAllFocusState.focusModifier)
                                    .then(Modifier.tvFocusIndicator(seeAllFocusState, ShapeCache.smooth16))
                                    .clickable { callbacks.navigation.onSeeAllCast() }
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
                            val personClick = remember(person.id) { { callbacks.navigation.onPersonClick(person.id) } }
                            val personImageUrl = remember(person.id) { callbacks.artwork.getImageUrl(person.id) }
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
                                localPortrait ?: callbacks.artwork.getImageUrl(person.id)
                            }
                            OfflinePersonItem(
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
}

// ── More like this (delayIndex 10) ────────────────────────────────────────

@Composable
internal fun DetailMoreLikeThisSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    isLocalOrigin: Boolean,
    bodyContentPad: Dp,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        // A LOCAL origin has no server "similar" list, so it shows on-device
        // titles mined from the offline library (localRelatedItems) instead;
        // remote keeps the server-sourced relatedItems. Either way the row
        // renders identically — only the source + an "On-device" badge differ.
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
                            text = stringResource(Res.string.detail_section_more_like_this),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isLocalOrigin) {
                            Text(
                                text = stringResource(Res.string.detail_more_like_this_on_device),
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
                val adaptiveInfo = LocalAdaptiveInfo.current
                val relatedCardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
                TvFocusableItemRow(
                    items = moreLikeThis,
                    key = { "related_${it.id}" },
                    contentPadding = PaddingValues(horizontal = bodyContentPad),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    onFocusedIndexChange = { index ->
                        moreLikeThis.getOrNull(index)?.let(callbacks.screen.onFocusedMediaItem)
                    },
                ) { _, related, focusModifier ->
                    val relatedClick = remember(related.id) { { callbacks.navigation.onItemClick(related.id) } }
                    val relatedImageUrl = remember(related.id) { callbacks.artwork.getImageUrl(related.id) }
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
}

// ── Seerr rows (delayIndex 11 / 12) ───────────────────────────────────────

@Composable
internal fun DetailSeerrRowSection(
    delayIndex: Int,
    titleRes: StringResource,
    items: List<SeerrSearchItem>,
    keyPrefix: String,
    contentType: String,
    callbacks: DetailContentCallbacks,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        SeerrItemsRow(
            title = stringResource(titleRes),
            keyPrefix = keyPrefix,
            contentType = contentType,
            items = items,
            onSeerrRequest = callbacks.seerr.onSeerrRequest,
            onNavigate = callbacks.navigation.onNavigate,
        )
    }
}

// ── Special features / extras (delayIndex 13) ─────────────────────────────

@Composable
internal fun DetailSpecialFeaturesSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    bodyContentPad: Dp,
) {
    // ── Special Features / Extras ──
    // Featurettes, deleted scenes, interviews, etc. attached server-side via
    // Jellyfin's /Items/{id}/SpecialFeatures. Rendered as a tappable poster row
    // (matching "More like this") so an extra plays in the video player on tap.
    // Remote-only: the fetch is gated by capabilities.remoteDiscovery (see
    // triggerRemoteSideEffects), and an empty list hides the section entirely.
    StaggeredDetailSection(
        visible = state.capabilities.remoteDiscovery && state.specialFeatures.isNotEmpty(),
        delayIndex = delayIndex,
    ) {
        val extras = state.specialFeatures
        Column {
            FadingItem {
                Text(
                    text = stringResource(Res.string.detail_section_special_features),
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
            val adaptiveInfo = LocalAdaptiveInfo.current
            val extraCardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
            TvFocusableItemRow(
                items = extras,
                key = { "extra_${it.id}" },
                contentPadding = PaddingValues(horizontal = bodyContentPad),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                onFocusedIndexChange = { index ->
                    extras.getOrNull(index)?.let(callbacks.screen.onFocusedMediaItem)
                },
            ) { _, extra, focusModifier ->
                val extraClick = remember(extra.id) { { callbacks.playback.onPlayExtra(extra) } }
                val extraImageUrl = remember(extra.id) { callbacks.artwork.getImageUrl(extra.id) }
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

// ── Download footer (delayIndex 15) ───────────────────────────────────────

@Composable
internal fun DetailDownloadFooterSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    item: MediaItem,
    bodyContentPad: Dp,
) {
    // ── Download info card + freshness banner ──
    // Rendered for a snapshot with an attached download (remote-with-download
    // OR local origin). Gated so a plain remote-no-download item never shows
    // it. Placed at the end of the body so it reads as a footer below all
    // content sections (delayIndex 15 = one past the last content section,
    // so the footer is the final step of the entrance stagger). The
    // seriesAggregate-only local series (no per-item download) is handled
    // by the header above; this card is per-item.
    val attachedDownload = state.detailContext?.download
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
            // Freshness banner: surfaces a server-detected update or a media-file
            // change that needs a full re-download. Tappable -> opens the resync
            // sheet. Hidden when there's nothing to act on.
            SyncUpdateBanner(
                syncState = state.detailContext?.syncState,
                resyncState = state.resyncState,
                onClick = callbacks.download.onOpenResync,
            )
            DownloadInfoCard(
                download = attachedDownload,
                item = item,
                onClick = callbacks.download.onOpenDownloadDetails,
            )
        }
    }
}

/** Chapter thumbnail tile for the video chapter row (slot 4's video variant). */
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
