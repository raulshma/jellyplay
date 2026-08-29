package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer
import com.raulshma.jellyplay.core.ui.components.OfflineMediaCard
import com.raulshma.jellyplay.core.ui.components.PlayButtonWithProgress
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.WideMediaCard
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronRight

/**
 * Horizontal scroller for a home media row on touch devices. When [clippingEnabled]
 * is true (the experimental "Card Clipping" feature) it renders the
 * [HorizontalUncontainedCarousel], which caps/clips items at the row edges for the
 * carousel effect. When false (the default) it falls back to a plain [LazyRow]
 * with `clipToBounds = false` so items and their elevation bleed past the edges.
 */
/**
 * Home row for the offline-derived sections ([HomeSectionType.DOWNLOADED]):
 * the same chrome as [HomeMediaRow] — title, card width, spacing, TV/mobile
 * scroller, focus wiring — but cards render via [OfflineMediaCard] so artwork
 * resolves from local files and no server image URL is ever built (issue #147:
 * the offline home is the normal home, populated from downloads).
 */
@Composable
internal fun OfflineHomeMediaRow(
    title: String,
    items: List<OfflineMediaItem>,
    onItemClick: (OfflineMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    // TV-only: reports the D-pad-focused item so the screen's Menu key can
    // open its quick actions (the offline card's own long-press handles touch).
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Column(modifier = modifier) {
        HomeSectionTitle(
            title = title,
            contentPad = contentPad,
            onLongClick = null,
        )
        if (isTv) {
            TvFocusableItemRow(
                items = items,
                key = { it.id },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                focusRequester = focusRequester,
                onRowFocused = onRowFocused,
                clipToBounds = clippingEnabled,
                onFocusedIndexChange = { index ->
                    onFocusedItemChange?.let { change -> items.getOrNull(index)?.let { change(it.toMediaItem()) } }
                },
            ) { _, item, focusModifier ->
                // Every item here is downloaded by definition, so the
                // "Downloaded" status badge would be redundant.
                OfflineMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = focusModifier.width(cardWidth),
                    showStatusBadge = false,
                )
            }
        } else {
            HorizontalMediaScroller(
                itemCount = items.size,
                itemWidth = cardWidth,
                spacing = spacing,
                contentPad = contentPad,
                clippingEnabled = clippingEnabled,
                modifier = Modifier.tvFocusRestorer(),
                key = { index -> items[index].id },
            ) { index ->
                val item = items[index]
                OfflineMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(cardWidth),
                    showStatusBadge = false,
                )
            }
        }
    }
}

@Composable
private fun HorizontalMediaScroller(

    itemCount: Int,
    itemWidth: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    contentPad: androidx.compose.ui.unit.Dp,
    clippingEnabled: Boolean,
    modifier: Modifier = Modifier,
    key: ((index: Int) -> Any)? = null,
    itemContent: @Composable (index: Int) -> Unit,
) {
    // Hoist the carousel state out of the clipping branch so toggling
    // clippingEnabled (the experimental card-clipping flag) doesn't recreate
    // the CarouselState and reset scroll position. It's only consumed in the
    // carousel branch below.
    val carouselState = rememberCarouselState { itemCount }
    if (clippingEnabled) {
        // The carousel implements contentPadding by SHRINKING every item's mask
        // (Strategy.createShiftedKeylineListForContentPadding divides the padding
        // across all non-anchor keylines) instead of adding real space. At scroll
        // rest this masks the leading card(s) below itemWidth on BOTH sides, so
        // even with enough room the first/next cards look clipped. Move the
        // horizontal inset to the container Modifier so it's real padding, and
        // pass zero contentPadding — then no shrink step is generated and the
        // first card renders full at the start inset.
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = itemWidth,
            itemSpacing = spacing,
            contentPadding = PaddingValues(0.dp),
            modifier = modifier.padding(horizontal = contentPad),
        ) { index -> itemContent(index) }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) {
            // Stable keys + contentType let Compose preserve scroll position
            // across recompositions and reuse item slots efficiently.
            items(
                count = itemCount,
                key = key,
                contentType = { "mediaCard" },
            ) { index ->
                // animateItem (LazyItemScope) animates placement. Placement spec
                // routes through lazyItemPlacementSpec() so it snaps under
                // reduce-motion. Wrapping itemContent in a Box is required because
                // itemContent has no modifier param.
                val placementSpec = lazyItemPlacementSpec()
                Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                    itemContent(index)
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    backdropUrlBuilder: (MediaItem) -> String,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    onSectionLongClick: (() -> Unit)? = null,
    // TV-only: reports the D-pad-focused item so the screen's Menu key can open
    // its quick actions (the wide card's own long-press handles touch).
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    // Continue-watching cards are wide (landscape thumbnails + progress/metadata), so they
    // scale ~1.6× the portrait poster-card width rather than using [rowCardWidth] directly.
    val cardWidth = (adaptiveInfo.rowCardWidth(isTv) * 1.6f).coerceAtLeast(260.dp)
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    // The bottom scrim gradient is identical across every card in the same theme
    // state, so compute it once per row instead of allocating a Brush per card
    // as cards scroll in/out of view.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceScrimBrush = remember(surfaceColor) {
        Brush.verticalGradient(listOf(Color.Transparent, surfaceColor.copy(alpha = 0.4f)))
    }

    Column(modifier = modifier) {
        HomeSectionTitle(
            title = title,
            contentPad = contentPad,
            onLongClick = onSectionLongClick,
        )
        if (isTv) {
            TvFocusableItemRow(
                items = items,
                key = { it.id },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                focusRequester = focusRequester,
                onRowFocused = onRowFocused,
                clipToBounds = clippingEnabled,
                onFocusedIndexChange = { index ->
                    onFocusedItemChange?.let { change -> items.getOrNull(index)?.let(change) }
                },
            ) { _, item, focusModifier ->
                val memoizedClick = remember(item) { { onItemClick(item) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
                WideMediaCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    backdropUrl = backdropUrlBuilder(item),
                    onClick = memoizedClick,
                    onPlayClick = memoizedPlayClick,
                    cardWidth = cardWidth,
                    surfaceScrimBrush = surfaceScrimBrush,
                    modifier = focusModifier,
                    clipToShape = clippingEnabled,
                )
            }
        } else {
            HorizontalMediaScroller(
                itemCount = items.size,
                itemWidth = cardWidth,
                spacing = spacing,
                contentPad = contentPad,
                clippingEnabled = clippingEnabled,
                modifier = Modifier.tvFocusRestorer(),
                key = { index -> items[index].id },
            ) { index ->
                val item = items[index]
                val memoizedClick = remember(item) { { onItemClick(item) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
                WideMediaCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    backdropUrl = backdropUrlBuilder(item),
                    onClick = memoizedClick,
                    onPlayClick = memoizedPlayClick,
                    cardWidth = cardWidth,
                    surfaceScrimBrush = surfaceScrimBrush,
                    clipToShape = clippingEnabled,
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeMediaRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String>,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    photoFolderChildUrls: Map<String, List<String>> = emptyMap(),
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    showEpisodeSeriesBadge: Boolean = false,
    onSectionLongClick: (() -> Unit)? = null,
    onSeeAllClick: (() -> Unit)? = null,
    // TV-only: reports the D-pad-focused item so the screen's Menu key can open
    // its quick actions
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
    // Resolves a series' poster URL by id — used so episode cards render the
    // show's poster instead of the episode's own primary image (a landscape
    // scene grab). See [EpisodePosterResolver] usage below.
    seriesPosterResolver: (String) -> String = { "" },
    // Resolves a series' backdrop URL by id — middle fallback when a series has
    // no Primary image (common for freshly-added series whose library scan has
    // not yet generated a poster). See [EpisodePosterResolver].
    seriesBackdropResolver: (String) -> String = { "" },
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardPrefs = com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences.current
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    // Apply "hide watched items" filter at the row wrapper so the entire card
    // (and its slot in the scroller) disappears rather than leaving a gap.
    val effectiveItems = remember(items, cardPrefs.hideWatchedItems) {
        if (cardPrefs.hideWatchedItems) items.filterNot { it.isPlayed } else items
    }
    // When the filter empties a row, hide the whole row (header included) so
    // the user doesn't see section titles for fully-watched content.
    if (effectiveItems.isEmpty()) return
    // The poster bottom-scrim gradient is identical across every card in the
    // same theme state, so compute it once per row instead of per card.
    val posterSurfaceColor = MaterialTheme.colorScheme.surface
    val posterScrimBrush = remember(posterSurfaceColor) {
        Brush.verticalGradient(listOf(Color.Transparent, posterSurfaceColor.copy(alpha = 0.45f)))
    }

    // The See All pill hugs the header's trailing edge, so geometric focus search
    // from the cards below rarely ranks it. Redirect Up-exits from the row to the
    // pill explicitly (sections without one keep the default search).
    val seeAllFocusRequester = remember { FocusRequester() }
    val rowModifier = if (isTv && onSeeAllClick != null) {
        Modifier.focusProperties {
            exit = { direction ->
                if (direction == FocusDirection.Up) seeAllFocusRequester
                else FocusRequester.Default
            }
        }
    } else {
        Modifier
    }

    Column(modifier = modifier) {
        HomeSectionTitle(
            title = title,
            contentPad = contentPad,
            onLongClick = onSectionLongClick,
            onSeeAllClick = onSeeAllClick,
            seeAllFocusRequester = seeAllFocusRequester,
        )
        if (isTv) {
            TvFocusableItemRow(
                items = effectiveItems,
                key = { it.id },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = rowModifier,
                focusRequester = focusRequester,
                onRowFocused = onRowFocused,
                clipToBounds = clippingEnabled,
                onFocusedIndexChange = { index ->
                    onFocusedItemChange?.let { change -> effectiveItems.getOrNull(index)?.let(change) }
                },
            ) { _, item, focusModifier ->
                val memoizedClick = remember(item) { { onItemClick(item) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
                // Memoize progress so per-card arithmetic runs only when ticks change,
                // not on every scroll/animation-frame recompose (matches WideMediaCard).
                val progressPercent = remember(item.id, item.playbackPositionTicks, item.runTimeTicks) {
                    item.progressFraction() ?: 0f
                }
                val cardImage = rememberEpisodeCardImage(
                    item = item,
                    itemImageUrl = remember(item) { imageUrlBuilder(item) },
                    fallbackImageUrls = fallbackImageUrlBuilder(item),
                    seriesPosterResolver = seriesPosterResolver,
                    seriesBackdropResolver = seriesBackdropResolver,
                    showEpisodeSeriesBadge = showEpisodeSeriesBadge,
                )
                PosterCard(
                    item = item,
                    imageUrl = cardImage.imageUrl,
                    fallbackUrls = cardImage.fallbackUrls,
                    onClick = memoizedClick,
                    modifier = focusModifier.width(cardWidth),
                    showProgress = item.hasPlaybackPosition,
                    progressPercent = progressPercent,
                    blurHash = cardImage.blurHash,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                    clipToShape = clippingEnabled,
                    showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                    gradientBrush = posterScrimBrush,
                )
            }
        } else {
            HorizontalMediaScroller(
                itemCount = effectiveItems.size,
                itemWidth = cardWidth,
                spacing = spacing,
                contentPad = contentPad,
                clippingEnabled = clippingEnabled,
                modifier = Modifier.tvFocusRestorer(),
                key = { index -> effectiveItems[index].id },
            ) { index ->
                val item = effectiveItems[index]
                val memoizedClick = remember(item) { { onItemClick(item) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
                // Memoize progress so per-card arithmetic runs only when ticks change,
                // not on every scroll/animation-frame recompose (matches WideMediaCard).
                val progressPercent = remember(item.id, item.playbackPositionTicks, item.runTimeTicks) {
                    item.progressFraction() ?: 0f
                }
                val cardImage = rememberEpisodeCardImage(
                    item = item,
                    itemImageUrl = remember(item) { imageUrlBuilder(item) },
                    fallbackImageUrls = fallbackImageUrlBuilder(item),
                    seriesPosterResolver = seriesPosterResolver,
                    seriesBackdropResolver = seriesBackdropResolver,
                    showEpisodeSeriesBadge = showEpisodeSeriesBadge,
                )
                PosterCard(
                    item = item,
                    imageUrl = cardImage.imageUrl,
                    fallbackUrls = cardImage.fallbackUrls,
                    onClick = memoizedClick,
                    modifier = Modifier.width(cardWidth),
                    showProgress = item.hasPlaybackPosition,
                    progressPercent = progressPercent,
                    blurHash = cardImage.blurHash,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                    clipToShape = clippingEnabled,
                    showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                    gradientBrush = posterScrimBrush,
                )
            }
        }
    }
}

/**
 * The row title for a home section. Renders the heading typography and:
 * - an optional long-press affordance ([onLongClick]) to configure the section
 * (toggle visibility / reorder / open Home Layout settings), and
 * - an optional "See All" affordance ([onSeeAllClick]) that opens the full
 * library screen for that section with pre-applied filters.
 *
 * On touch, the title surface still only intercepts long-press so the row's own
 * horizontal scroll and item clicks are unaffected; "See All" is a separate
 * trailing pill so it can be tapped without intercepting the title. On TV the
 * "See All" pill is focusable (D-pad); the long-press is touch-only — section
 * configuration on TV goes through Settings → Home Layout. The title surface
 * must NOT be focusable on TV: a full-width indication-less clickable between
 * the rows is an invisible focus trap that also steals focus from the pill.
 */
@Composable
private fun HomeSectionTitle(
    title: String,
    contentPad: androidx.compose.ui.unit.Dp,
    onLongClick: (() -> Unit)?,
    onSeeAllClick: (() -> Unit)? = null,
    seeAllFocusRequester: FocusRequester? = null,
) {
    val isTv = LocalTvMode.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null && !isTv) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else Modifier,
            )
            .padding(horizontal = contentPad, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = if (isTv) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onSeeAllClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            SeeAllPill(
                onClick = onSeeAllClick,
                modifier = seeAllFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
    }
}

/**
 * The trailing "See All ›" affordance on a home section title. A compact glass
 * pill matching the [GlassPill] / GlassFilterChip idiom (shape morph + press
 * scale + TV focus, via [ExpressiveChipContainer]), with a trailing chevron so
 * it reads as navigational.
 */
@Composable
private fun SeeAllPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val bgColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    val contentColor = MaterialTheme.colorScheme.onSurface
    ExpressiveChipContainer(
        onClick = onClick,
        modifier = modifier,
        containerColor = bgColor,
        contentPadding = PaddingValues(6.dp),
    ) {
        Icon(
            imageVector = Tabler.Outline.ChevronRight,
            contentDescription = stringResource(com.raulshma.jellyplay.feature.home.R.string.home_see_all),
            modifier = Modifier.size(18.dp),
            tint = contentColor,
        )
    }
}

