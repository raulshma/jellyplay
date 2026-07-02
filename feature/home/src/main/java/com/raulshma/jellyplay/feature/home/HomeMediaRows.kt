package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.PlayButtonWithProgress
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.components.rememberDominantColor
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
    itemContent: @Composable (index: Int) -> Unit,
) {
    if (clippingEnabled) {
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { itemCount },
            itemWidth = itemWidth,
            itemSpacing = spacing,
            contentPadding = PaddingValues(horizontal = contentPad),
            modifier = modifier,
        ) { index -> itemContent(index) }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier,
        ) {
            items(itemCount) { index -> itemContent(index) }
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
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = when {
        isTv -> 400.dp
        adaptiveInfo.windowSizeClass != WindowSizeClass.Compact -> 320.dp
        else -> 260.dp
    }
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (isTv) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
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
    val elevation by animateFloatAsState(
        targetValue = when {
            isPressed -> 12f
            tvFocusState.isFocused -> 16f
            else -> 4f
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardElevation",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardBrightness",
    )

    val dominantColor = rememberDominantColor(backdropUrl.ifBlank { imageUrl }, itemId = item.id)
    val progressPercent = item.progressFraction() ?: 0f
    val playButtonSize = if (isTv) 44.dp else 36.dp

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)

    Column(modifier = modifier.width(cardWidth)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFocusState.focusModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.dp.toPx()
                    clip = clipToShape
                    shape = ShapeCache.smooth12
                }
                .tvFocusIndicator(tvFocusState, ShapeCache.smooth12)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = ShapeCache.smooth12,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                MediaImage(
                    url = backdropUrl,
                    fallbackUrls = listOf(imageUrl).filter { it.isNotBlank() },
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

                val surfaceColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            remember(surfaceColor) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        surfaceColor.copy(alpha = 0.4f),
                                    ),
                                )
                            }
                        )
                )

                if (item.communityRating != null) {
                    val ratingText = remember(item.communityRating) { "%.1f".format(item.communityRating) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                ShapeCache.smooth4,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelSmall,
                                color = RatingColors.star,
                            )
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
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
            val hasWatchProgress =
                item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0 && !item.isPlayed
            val remainingTime = if (hasWatchProgress && hasValidDuration) {
                formatRemainingTimeFromTicks(item.runTimeTicks!!, item.playbackPositionTicks!!)
            } else null
            val totalTime = if (hasValidDuration && !hasWatchProgress) {
                formatDurationFromTicks(item.runTimeTicks!!)
            } else null

            val timeText = remainingTime ?: totalTime

            val subtitleText = remember(item) {
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

    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (isTv) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
        )
        if (effectiveItems.isEmpty()) return@Column
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
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = memoizedClick,
                    modifier = focusModifier.width(cardWidth),
                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                        (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                    } else 0f,
                    blurHash = item.blurHashes.primary,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                    clipToShape = clippingEnabled,
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
            ) { index ->
                val item = effectiveItems[index]
                val memoizedClick = remember(item) { { onItemClick(item) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } }
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = memoizedClick,
                    modifier = Modifier.width(cardWidth),
                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                        (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                    } else 0f,
                    blurHash = item.blurHashes.primary,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${item.id}",
                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                    clipToShape = clippingEnabled,
                )
            }
        }
    }
}
