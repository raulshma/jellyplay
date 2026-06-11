package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.cardBorder
import com.raulshma.jellyplay.core.designsystem.theme.cardElevation
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.animation.fastEffectsSpec
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val dominantColorCache = android.util.LruCache<String, Color>(500)

@Composable
fun rememberDominantColor(imageUrl: String?, fallback: Color = MaterialTheme.colorScheme.surfaceContainer, itemId: String? = null): Color {
    val context = LocalContext.current
    val cacheKey = itemId ?: imageUrl
    var color by remember(cacheKey) { mutableStateOf(cacheKey?.let { dominantColorCache.get(it) } ?: fallback) }
    val loader = coil3.SingletonImageLoader.get(context)

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
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
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.15f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = fastEffectsSpec(),
        label = "playBtnScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = fastEffectsSpec(),
        label = "playBtnCombinedScale",
    )

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val progressColor = dominantColor

    Box(
        modifier = modifier
            .size(buttonSize)
            .then(tvFocusState.focusModifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth10)
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
                val path = androidx.compose.ui.graphics.Path()
                val w = size.width - strokeWidth
                val h = size.height - strokeWidth
                val r = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
                val ox = halfStroke
                val oy = halfStroke

                path.moveTo(ox + w / 2f, oy)
                path.lineTo(ox + w - r, oy)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox + w - 2 * r, oy, ox + w, oy + 2 * r),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                path.lineTo(ox + w, oy + h - r)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox + w - 2 * r, oy + h - 2 * r, ox + w, oy + h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                path.lineTo(ox + r, oy + h)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox, oy + h - 2 * r, ox + 2 * r, oy + h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                path.lineTo(ox, oy + r)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox, oy, ox + 2 * r, oy + 2 * r),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                path.lineTo(ox + w / 2f, oy)

                val measure = androidx.compose.ui.graphics.PathMeasure()
                measure.setPath(path, false)
                val totalLength = measure.length
                val progressLength = totalLength * progressPercent.coerceIn(0f, 1f)

                val progressPath = androidx.compose.ui.graphics.Path()
                measure.getSegment(0f, progressLength, progressPath, true)

                drawPath(
                    path = progressPath,
                    color = progressColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Icon(
            Tabler.Outline.PlayerPlay,
            contentDescription = "Play",
            modifier = Modifier.size(buttonSize * 0.55f),
            tint = Color.White,
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
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardScale",
    )
    val scale = baseScale * tvFocusState.scale
    val themeVariant = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
    val elevation = themeVariant.cardElevation(
        isPressed = isPressed,
        isTvFocused = tvFocusState.isFocused,
        isTv = isTv,
    )
    val shape = ShapeCache.smooth12

    val dominantColor = rememberDominantColor(imageUrl, itemId = item.id)
    val playButtonSize = if (isTv) 44.dp else 36.dp

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    val sharedImageModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = sharedElementKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .then(sharedImageModifier)

    Column(modifier = modifier) {
        val border = themeVariant.cardBorder(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
            outline = MaterialTheme.colorScheme.outline,
        )

        val surfaceColor = MaterialTheme.colorScheme.surface
        val gradientBrush = remember(surfaceColor) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    surfaceColor.copy(alpha = 0.45f),
                ),
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFocusState.focusModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.toPx()
                    clip = false
                }
                .tvFocusIndicator(tvFocusState, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = shape,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                MediaImage(
                    url = imageUrl,
                    fallbackUrls = fallbackUrls,
                    contentDescription = item.name,
                    blurHash = blurHash,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(gradientBrush)
                )

                if (item.isPlayed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                dominantColor.copy(alpha = 0.85f),
                                ShapeCache.smooth4,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }

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
                                color = Color.White,
                            )
                        }
                    }
                }

                if (onPlayClick != null) {
                    PlayButtonWithProgress(
                        progressPercent = if (showProgress) progressPercent else 0f,
                        dominantColor = dominantColor,
                        onClick = onPlayClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        buttonSize = playButtonSize,
                    )
                }

                if (showProgress && progressPercent > 0f) {
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
                top = if (isTv) 8.dp else 6.dp,
            ),
        ) {
            Text(
                text = item.name,
                style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.year != null) {
                    Text(
                        text = item.year.toString(),
                        style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val isSeries = item.mediaType == MediaType.SERIES
                val hasValidDuration = item.runTimeTicks != null && item.runTimeTicks!! > 0 && !isSeries
                val hasWatchProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0 && !item.isPlayed
                val remainingTime = remember(hasValidDuration, hasWatchProgress, item.runTimeTicks, item.playbackPositionTicks) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = if (remainingTime != null) "$timeText left" else timeText,
                        style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        color = if (remainingTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String> = { emptyList() },
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    blurHashBuilder: (MediaItem) -> String? = { it.blurHashes.primary },
    onPlayClick: ((MediaItem) -> Unit)? = null,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val titleStyle = if (isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Text(
            text = title,
            style = titleStyle,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.tvFocusRestorer(),
        ) {
            items(items, key = { "${title}_${it.id}" }, contentType = { "mediaItem" }) { item ->
                val memoizedClick = remember(item) { { onItemClick(item) } }
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
                    blurHash = blurHashBuilder(item),
                    onPlayClick = onPlayClick?.let { click -> remember(item, click) { { click(item) } } },
                )
            }
        }
    }
}
