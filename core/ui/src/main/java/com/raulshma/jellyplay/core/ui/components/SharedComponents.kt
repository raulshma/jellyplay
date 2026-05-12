package com.raulshma.jellyplay.core.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
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
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import androidx.palette.graphics.Palette
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─── Dominant color cache ────────────────────────────────────────────────────
private val dominantColorCache = mutableMapOf<String, Color>()

@Composable
fun rememberDominantColor(imageUrl: String?, fallback: Color = Color(0xFF2A2A3E)): Color {
    val context = LocalContext.current
    val cached = imageUrl?.let { dominantColorCache[it] }
    var color by remember { mutableStateOf(cached ?: fallback) }
    val loader = remember { ImageLoader(context) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        dominantColorCache[imageUrl]?.let {
            color = it
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(CoilSize(128, 128))
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
                        dominantColorCache[imageUrl] = c
                        color = c
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
    return color
}

// ─── Play Button with Rounded-Rect Progress Border ──────────────────────────

@Composable
fun PlayButtonWithProgress(
    progressPercent: Float,
    dominantColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 36.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "playBtnScale",
    )

    val trackColor = Color.White.copy(alpha = 0.25f)
    val progressColor = dominantColor

    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .tvFocusable().clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Background fill
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.65f),
                    RoundedCornerShape(10.dp),
                )
        )

        // Progress border that follows the rounded-rect shape
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.5.dp.toPx()
            val halfStroke = strokeWidth / 2f
            val cornerRadius = 10.dp.toPx()

            // Track: full rounded rect border
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                style = Stroke(width = strokeWidth),
            )

            // Progress: draw along rounded rect path
            if (progressPercent > 0f) {
                val path = androidx.compose.ui.graphics.Path()
                val w = size.width - strokeWidth
                val h = size.height - strokeWidth
                val r = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
                val ox = halfStroke
                val oy = halfStroke

                // Build rounded-rect path starting from top-center, going clockwise
                path.moveTo(ox + w / 2f, oy)
                // Top edge → top-right corner
                path.lineTo(ox + w - r, oy)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox + w - 2 * r, oy, ox + w, oy + 2 * r),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                // Right edge → bottom-right corner
                path.lineTo(ox + w, oy + h - r)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox + w - 2 * r, oy + h - 2 * r, ox + w, oy + h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                // Bottom edge → bottom-left corner
                path.lineTo(ox + r, oy + h)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox, oy + h - 2 * r, ox + 2 * r, oy + h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                // Left edge → top-left corner
                path.lineTo(ox, oy + r)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(ox, oy, ox + 2 * r, oy + 2 * r),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )
                // Close back to top-center
                path.lineTo(ox + w / 2f, oy)

                // Measure the path and extract the progress portion
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

        // Play icon
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(buttonSize * 0.55f),
            tint = Color.White,
        )
    }
}

// ─── Redesigned PosterCard ───────────────────────────────────────────────────

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
) {
    val isTv = isTvDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )

    val dominantColor = rememberDominantColor(imageUrl)
    val playButtonSize = if (isTv) 44.dp else 36.dp

    Column(modifier = modifier) {
        // ── Card (poster only, no text inside) ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .tvFocusable().clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .then(if (isTv) Modifier.tvFocusable() else Modifier)
                .then(if (isTv) Modifier.shadow(12.dp, RoundedCornerShape(12.dp)) else Modifier),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isTv) 12.dp else 4.dp),
        ) {
            Box {
                MediaImage(
                    url = imageUrl,
                    fallbackUrls = fallbackUrls,
                    contentDescription = item.name,
                    blurHash = blurHash,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop,
                )

                // Subtle gradient at bottom for depth
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.45f),
                                ),
                            )
                        )
                )

                // "Watched" badge in top-right
                if (item.isPlayed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                dominantColor.copy(alpha = 0.85f),
                                RoundedCornerShape(6.dp),
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

                // Play button in bottom-right
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
            }
        }

        // ── Info below card ──
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.9f),
            )
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
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
    val isTv = isTvDevice()
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
        ) {
            items(items, key = { "${title}_${it.id}" }, contentType = { "mediaItem" }) { item ->
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(cardWidth),
                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                        (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                    } else 0f,
                    blurHash = blurHashBuilder(item),
                    onPlayClick = onPlayClick?.let { { it(item) } },
                )
            }
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    AnimatedEntrance(visible = true) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedEntrance(visible = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun StaggeredSection(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    var shouldShow by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        shouldShow = visible
    }

    AnimatedVisibility(
        visible = visible && shouldShow,
        enter = fadeIn(
            animationSpec = tween(340, delayMillis = index * 55, easing = FastOutSlowInEasing),
        ) + slideInVertically(
            initialOffsetY = { it / 14 },
            animationSpec = tween(340, delayMillis = index * 55, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(160)) + slideOutVertically(
            targetOffsetY = { -it / 24 },
            animationSpec = tween(180, easing = FastOutSlowInEasing),
        ),
    ) {
        content()
    }
}

@Composable
fun AnimatedEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350, delayMillis = delayMillis)) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = tween(350, delayMillis = delayMillis, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(200)),
        content = content,
    )
}

@Composable
fun AnimatedScaleEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = delayMillis)) +
                androidx.compose.animation.scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(300, delayMillis = delayMillis, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut(tween(150)) +
                androidx.compose.animation.scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(150),
                ),
        content = content,
    )
}

@Composable
fun PressScaleBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.95f,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = tween(120),
        label = "pressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .tvFocusable().clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

@Composable
fun rememberAnimatedItemVisibility(index: Int): Boolean {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        visible = true
    }
    return visible
}

@Composable
fun AnimatedMediaItem(
    index: Int,
    delayPerItem: Int = 40,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(320, delayMillis = index * delayPerItem, easing = FastOutSlowInEasing),
        ) + slideInVertically(
            initialOffsetY = { it / 10 },
            animationSpec = tween(320, delayMillis = index * delayPerItem, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(140)),
    ) {
        content()
    }
}
