package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsMonochromeTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.cardBorder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.ui.animation.fastEffectsSpec
import com.raulshma.jellyplay.core.ui.animation.pressScale
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.image.PhotoFolderPoster
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val dominantColorCache = android.util.LruCache<String, Color>(500)

@Composable
fun rememberDominantColor(
    imageUrl: String?,
    fallback: Color = MaterialTheme.colorScheme.surfaceContainer,
    itemId: String? = null
): Color {
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val isScrollIdle = LocalScrollIdle.current
    val cacheKey = itemId ?: imageUrl
    var color by remember(cacheKey) { mutableStateOf(cacheKey?.let { dominantColorCache.get(it) } ?: fallback) }
    val loader = coil3.SingletonImageLoader.get(context)

    // Skip Palette extraction entirely in performance mode: every unseen poster
    // otherwise launches a Coil request + Palette.generate() on Dispatchers.Default,
    // competing with the visible image decodes for CPU during scroll. Cached
    // values (from a prior non-perf-mode session) are still returned.
    //
    // Also defer until scroll settles (isScrollIdle): during fast scroll dozens
    // of newly-composed cards would each fire a 64px Coil execute + Palette
    // generate, starving the visible-card decodes. Reading LocalScrollIdle as a
    // LaunchedEffect key re-triggers the effect when scroll stops, so colors
    // populate ~150ms after the user stops flinging.
    LaunchedEffect(imageUrl, performanceMode, isScrollIdle()) {
        if (performanceMode || imageUrl.isNullOrBlank()) return@LaunchedEffect
        if (!isScrollIdle()) return@LaunchedEffect
        if (cacheKey != null) {
            dominantColorCache.get(cacheKey)?.let {
                color = it
                return@LaunchedEffect
            }
        }
        withContext(Dispatchers.Default) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(CoilSize(64, 64))
                    .allowHardware(false)
                    // Isolate the Palette decode in its own cache slot. Sharing
                    // the display URL's key made this 64px result evict the
                    // larger bitmap a live AsyncImage painter was still drawing;
                    // the BitmapPool then recycled it, crashing onDraw with
                    // "Canvas: trying to use a recycled bitmap". Same fix class
                    // as WidgetImageLoader (memoryCachePolicy DISABLED) but we
                    // keep Palette results cached for scroll speed.
                    .memoryCacheKey("${imageUrl}#palette-dominant")
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                        ?: return@withContext
                    val palette = Palette.from(bitmap).maximumColorCount(8).generate()
                    val extracted = palette.vibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                    if (extracted != null) {
                        val c = Color(extracted)
                        if (cacheKey != null) {
                            dominantColorCache.put(cacheKey, c)
                        }
                        color = c
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
    return color
}

@Composable
fun PlayButtonWithProgress(
    progressPercent: Float,
    dominantColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 36.dp,
) {
    val focusInteraction = rememberJellyFocusableInteraction(focusedScale = 1.15f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = fastEffectsSpec(),
        label = "playBtnScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * focusInteraction.scale,
        animationSpec = fastEffectsSpec(),
        label = "playBtnCombinedScale",
    )

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val progressColor = dominantColor

    Box(
        modifier = modifier
            .size(buttonSize)
            .then(focusInteraction.modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .jellyFocusIndicator(focusInteraction, ShapeCache.smooth10)
            .clip(ShapeCache.smooth10)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    ShapeCache.smooth10,
                )
        )

        val outlinePath = remember { androidx.compose.ui.graphics.Path() }
        val progressPath = remember { androidx.compose.ui.graphics.Path() }
        val pathMeasure = remember { androidx.compose.ui.graphics.PathMeasure() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.5.dp.toPx()
            val halfStroke = strokeWidth / 2f
            val cornerRadius = 10.dp.toPx()

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                style = Stroke(width = strokeWidth),
            )

            if (progressPercent > 0f) {
                val w = size.width - strokeWidth
                val h = size.height - strokeWidth
                val r = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
                val ox = halfStroke
                val oy = halfStroke

                outlinePath.rewind()
                outlinePath.moveTo(ox + w / 2f, oy)
                outlinePath.lineTo(ox + w - r, oy)
                outlinePath.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox + w - 2 * r, oy, ox + w, oy + 2 * r),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                outlinePath.lineTo(ox + w, oy + h - r)
                outlinePath.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox + w - 2 * r, oy + h - 2 * r, ox + w, oy + h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                outlinePath.lineTo(ox + r, oy + h)
                outlinePath.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox, oy + h - 2 * r, ox + 2 * r, oy + h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                outlinePath.lineTo(ox, oy + r)
                outlinePath.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox, oy, ox + 2 * r, oy + 2 * r),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                outlinePath.lineTo(ox + w / 2f, oy)

                pathMeasure.setPath(outlinePath, false)
                val totalLength = pathMeasure.length
                val progressLength = totalLength * progressPercent.coerceIn(0f, 1f)

                progressPath.rewind()
                pathMeasure.getSegment(0f, progressLength, progressPath, true)

                drawPath(
                    path = progressPath,
                    color = progressColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Icon(
            Tabler.Outline.PlayerPlay,
            contentDescription = stringResource(R.string.core_ui_play),
            modifier = Modifier.size(buttonSize * 0.55f),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun PosterCard(
    item: MediaItem,
    imageUrl: String,
    fallbackUrls: List<String> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    progressPercent: Float = 0f,
    blurHash: String? = null,
    onPlayClick: (() -> Unit)? = null,
    sharedElementKey: String? = null,
    photoFolderChildImageUrls: List<String> = emptyList(),
    clipToShape: Boolean = false,
    showEpisodeSeriesBadge: Boolean = false,
    gradientBrush: Brush? = null,
) {
    val isTv = LocalJellyPlayUi.current.isTv
    val cardPrefs = LocalCardDisplayPreferences.current
    val dominantColor = rememberDominantColor(imageUrl, itemId = item.id)
    val playButtonSize = if (isTv) 44.dp else 36.dp

    // For episode cards in Latest Media rows, show the series name as the title
    // (the episode title alone doesn't identify the show); the season/episode
    // chip on the image carries the S# E# context. Seasons use displayTitle so a
    // flat season row shows "S01 - Series" instead of an ambiguous "Season 1".
    val titleText = remember(item, showEpisodeSeriesBadge) {
        if (showEpisodeSeriesBadge && item.mediaType == MediaType.EPISODE) {
            item.seriesName?.takeIf { it.isNotBlank() } ?: item.displayTitle()
        } else {
            item.displayTitle()
        }
    }

    // Short-circuit the peek factory when no controller is wired: avoids the
    // per-card peek composition allocations in contexts that can't use it
    // (e.g. a library grid without the experimental preview enabled).
    val mediaPreviewController = com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController.current
    // When the host screen provides a quick-action controller,
    // long-press opens the action sheet instead of the peek preview — the
    // action sheet supersedes peek so both never fight over one gesture.
    val quickActionController = LocalMediaQuickActionController.current
    val previewFactory = if (quickActionController != null) null else if (mediaPreviewController != null) {
        remember(item, imageUrl, fallbackUrls, blurHash) {
            { sourceBounds: androidx.compose.ui.geometry.Rect? ->
                com.raulshma.jellyplay.core.ui.preview.MediaPreview(
                    item = item,
                    posterUrl = imageUrl,
                    backdropUrl = fallbackUrls.firstOrNull(),
                    blurHash = blurHash,
                    sourceBounds = sourceBounds,
                )
            }
        }
    } else null

    // Stable per-item lambda so combinedClickable's gesture detector isn't
    // restarted mid-press. Reads the controller from composition scope so the
    // sheet target is always this card's exact item.
    val onQuickActionsLongPress = quickActionController?.let { controller ->
        remember(item, controller) { { controller.show(item) } }
    }

    MediaCardScaffold(
        onClick = onClick,
        image = { imageModifier ->
            if (item.mediaType == MediaType.PHOTO_FOLDER && photoFolderChildImageUrls.isNotEmpty()) {
                PhotoFolderPoster(
                    imageUrls = photoFolderChildImageUrls,
                    modifier = imageModifier,
                    contentDescription = item.name,
                )
            } else {
                MediaImage(
                    url = imageUrl,
                    fallbackUrls = fallbackUrls,
                    contentDescription = item.name,
                    blurHash = blurHash,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                    // Poster cards fill a dynamic column width at a 2:3 aspect ratio.
                    // ~360×540 px covers ~2× density for typical grid card sizes
                    // (≤180 dp wide) without the over-decode of the 512×512 default.
                    size = CoilSize(360, 540),
                )
            }
        },
        title = titleText,
        modifier = modifier,
        aspectRatio = 2f / 3f,
        clipToShape = clipToShape,
        onPlayClick = onPlayClick,
        playButtonDominantColor = dominantColor,
        playButtonSize = playButtonSize,
        sharedElementKey = sharedElementKey,
        scrimBrush = gradientBrush,
        previewFactory = previewFactory,
        onLongPress = onQuickActionsLongPress,
        showProgress = showProgress,
        progressFraction = progressPercent,
        overlays = {
            if (item.isPlayed && cardPrefs.showWatchedCheckmark) {
                WatchedBadge(
                    accentTint = dominantColor,
                    iconColor = remember(dominantColor) {
                        if (isLightColor(dominantColor)) Color.Black else Color.White
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            } else if (!item.isPlayed && cardPrefs.showUnwatchedBadge) {
                val unplayedCount = item.unplayedItemCount
                // Unwatched-count badge for series/seasons/collections. Only
                // rendered when the user has enabled the unwatched badge
                // and the underlying MediaItem exposes a non-zero count.
                if (unplayedCount != null && unplayedCount > 0) {
                    UnwatchedCountBadge(
                        count = unplayedCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }

            if (item.communityRating != null) {
                RatingBadge(
                    rating = item.communityRating,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }

            // Bottom-left season/episode chip for episode cards surfaced in
            // Latest Media rows; the series name is shown as the card title.
            if (showEpisodeSeriesBadge && item.mediaType == MediaType.EPISODE) {
                EpisodeChip(
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp),
                )
            }
        },
        footer = {
            // Items with no year/runtime (e.g. freshly-added episodes) would
            // otherwise render an empty Row (0 height), making the card shorter
            // than its neighbours that show a meta line. Reserve one line of the
            // footer text style so every card's image+title+footer block is the
            // same total height.
            val footerStyle = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
            val footerLineHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                footerStyle.lineHeight.toDp()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(min = footerLineHeight),
            ) {
                // For episode cards the title shows the series name (see
                // showEpisodeSeriesBadge); carry the S# E# context into the
                // footer too, so the reserved line shows something useful
                // instead of an empty gap (matches WideMediaCard's subtitle).
                if (showEpisodeSeriesBadge && item.mediaType == MediaType.EPISODE) {
                    val episodeSubtitle = remember(item.seasonNumber, item.episodeNumber) {
                        when {
                            item.seasonNumber != null && item.episodeNumber != null ->
                                "S${item.seasonNumber} E${item.episodeNumber.toString().padStart(2, '0')}"
                            item.episodeNumber != null -> "E${item.episodeNumber.toString().padStart(2, '0')}"
                            item.seasonNumber != null -> "S${item.seasonNumber}"
                            else -> null
                        }
                    }
                    if (episodeSubtitle != null) {
                        Text(
                            text = episodeSubtitle,
                            style = footerStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                } else if (item.year != null) {
                    Text(
                        text = item.year.toString(),
                        style = footerStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val isSeries = item.mediaType == MediaType.SERIES
                val hasValidDuration = item.runTimeTicks != null && item.runTimeTicks!! > 0 && !isSeries
                val hasWatchProgress =
                    item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0 && !item.isPlayed
                val remainingTime =
                    remember(hasValidDuration, hasWatchProgress, item.runTimeTicks, item.playbackPositionTicks) {
                        if (hasWatchProgress && hasValidDuration) {
                            formatRemainingTimeFromTicks(item.runTimeTicks!!, item.playbackPositionTicks!!)
                        } else null
                    }
                val totalTime = remember(hasValidDuration, hasWatchProgress, item.runTimeTicks) {
                    if (hasValidDuration && !hasWatchProgress) {
                        formatDurationFromTicks(item.runTimeTicks!!)
                    } else null
                }

                val timeText = remainingTime ?: totalTime
                if (timeText != null) {
                    Text(
                        text = "•",
                        style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (remainingTime != null) "$timeText left" else timeText,
                        style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        color = if (remainingTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
