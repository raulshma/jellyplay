package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.PlayButtonWithProgress
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.RatingBadge
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.components.rememberDominantColor
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

/**
 * Horizontal scroller for a home media row on touch devices. When [clippingEnabled]
 * is true (the experimental "Card Clipping" feature) it renders the
 * [HorizontalUncontainedCarousel], which caps/clips items at the row edges for the
 * carousel effect. When false (the default) it falls back to a plain [LazyRow]
 * with `clipToBounds = false` so items and their elevation bleed past the edges.
 */
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

@Composable
fun WideMediaCard(
    item: MediaItem,
    imageUrl: String,
    backdropUrl: String,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    cardWidth: Dp,
    surfaceScrimBrush: Brush,
    modifier: Modifier = Modifier,
    clipToShape: Boolean = false,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardCombinedScale",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardBrightness",
    )

    val dominantColor = rememberDominantColor(backdropUrl.ifBlank { imageUrl }, itemId = item.id)
    // Memoize the progress fraction so it is recomputed only when the item's
    // identity or its playback position/runtime ticks change (was recomputed
    // on every recomposition of the card).
    val progressPercent = remember(item.id, item.playbackPositionTicks, item.runTimeTicks) {
        item.progressFraction() ?: 0f
    }
    val playButtonSize = if (isTv) 44.dp else 36.dp

    // Press-and-hold "peek" preview; no-op on TV / when no controller is wired.
    val peek = rememberMediaPeek(
        item = item,
        posterUrl = imageUrl,
        backdropUrl = backdropUrl,
        blurHash = item.blurHashes.backdrop,
    )
    rememberReleaseDismiss(isPressed)

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)

    Column(modifier = modifier.width(cardWidth)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFocusState.focusModifier)
                .then(peek.boundsModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 0f
                    clip = clipToShape
                    shape = ShapeCache.smooth12
                }
                .tvFocusIndicator(tvFocusState, ShapeCache.smooth12)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = peek.onLongClick,
                ),
            shape = ShapeCache.smooth12,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                MediaImage(
                    url = backdropUrl,
                    fallbackUrls = remember(imageUrl) { if (imageUrl.isNotBlank()) listOf(imageUrl) else emptyList() },
                    contentDescription = item.name,
                    blurHash = item.blurHashes.backdrop,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                )

                if (brightnessOverlay > 0.01f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = brightnessOverlay))
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(surfaceScrimBrush)
                )

                if (item.communityRating != null) {
                    RatingBadge(
                        rating = item.communityRating,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }

                if (onPlayClick != null) {
                    PlayButtonWithProgress(
                        progressPercent = progressPercent,
                        dominantColor = dominantColor,
                        onClick = onPlayClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        buttonSize = playButtonSize,
                    )
                }

                if (progressPercent > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                start = 4.dp,
                end = 4.dp,
                top = 6.dp,
            ),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
            )
            val isSeries = item.mediaType == MediaType.SERIES
            val hasValidDuration = item.runTimeTicks != null && item.runTimeTicks!! > 0 && !isSeries
            val hasWatchProgress = item.hasWatchProgress
            val remainingTime = if (hasWatchProgress && hasValidDuration) {
                formatRemainingTimeFromTicks(item.runTimeTicks!!, item.playbackPositionTicks!!)
            } else null
            val totalTime = if (hasValidDuration && !hasWatchProgress) {
                formatDurationFromTicks(item.runTimeTicks!!)
            } else null

            val timeText = remainingTime ?: totalTime

            val subtitleText = remember(item.seriesName, item.seasonNumber, item.episodeNumber) {
                val parts = mutableListOf<String>()
                item.seriesName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                item.seasonNumber?.let { season ->
                    item.episodeNumber?.let { ep ->
                        parts.add("S${season}E${ep.toString().padStart(2, '0')}")
                    } ?: parts.add("S$season")
                }
                parts.joinToString(" · ")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else if (item.year != null) {
                    Text(
                        text = item.year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (timeText != null) {
                    if (subtitleText.isNotEmpty() || item.year != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Text(
                        text = if (remainingTime != null) "$timeText left" else timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remainingTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

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

    Column(modifier = modifier) {
        HomeSectionTitle(
            title = title,
            contentPad = contentPad,
            onLongClick = onSectionLongClick,
        )
        if (isTv) {
            TvFocusableItemRow(
                items = effectiveItems,
                key = { it.id },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                focusRequester = focusRequester,
                onRowFocused = onRowFocused,
                clipToBounds = clippingEnabled,
            ) { _, item, focusModifier ->
                val memoizedClick = remember(item) { { onItemClick(item) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
                // Memoize progress so per-card arithmetic runs only when ticks change,
                // not on every scroll/animation-frame recompose (matches WideMediaCard).
                val progressPercent = remember(item.id, item.playbackPositionTicks, item.runTimeTicks) {
                    item.progressFraction() ?: 0f
                }
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = memoizedClick,
                    modifier = focusModifier.width(cardWidth),
                    showProgress = item.hasPlaybackPosition,
                    progressPercent = progressPercent,
                    blurHash = item.blurHashes.primary,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                    clipToShape = clippingEnabled,
                    showEpisodeSeriesBadge = showEpisodeSeriesBadge,
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
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = memoizedClick,
                    modifier = Modifier.width(cardWidth),
                    showProgress = item.hasPlaybackPosition,
                    progressPercent = progressPercent,
                    blurHash = item.blurHashes.primary,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                    clipToShape = clippingEnabled,
                    showEpisodeSeriesBadge = showEpisodeSeriesBadge,
                    gradientBrush = posterScrimBrush,
                )
            }
        }
    }
}

/**
 * The row title for a home section. Renders the same heading typography the
 * rows always used, but adds an optional long-press affordance so the user can
 * configure the section (toggle visibility / reorder / open Home Layout
 * settings) directly from home — the same operations available under
 * Settings → Home Screen Layout.
 *
 * On touch, a trailing vertical-dots icon signals the affordance only when
 * [onLongClick] is non-null (i.e. for configurable section types). On TV the
 * hint is hidden (D-pad focus is the cue) and the long-press maps to the
 * select-and-hold key. The click handler is a no-op: the title is not a
 * navigation target, we only intercept long-press so the row's own horizontal
 * scroll and item clicks are unaffected.
 */
@Composable
private fun HomeSectionTitle(
    title: String,
    contentPad: androidx.compose.ui.unit.Dp,
    onLongClick: (() -> Unit)?,
) {
    val isTv = LocalTvMode.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
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
        )
    }
}

