package com.raulshma.jellyplay.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun VideoMiniPlayer(
    isVisible: Boolean,
    engine: MediaEngine?,
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible && engine != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val navBarColorState = LocalNavigationBarColor.current
        val isSynthwave = LocalIsSynthwave.current
        val isSoothing = LocalIsSoothingTheme.current

        val synthwaveTint = com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SYNTHWAVE_TINT
        val soothingTint = if (androidx.compose.foundation.isSystemInDarkTheme()) {
            com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SOOTHING_DARK_TINT
        } else {
            com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SOOTHING_LIGHT_TINT
        }
        val targetTint = when {
            isSynthwave -> synthwaveTint
            isSoothing -> soothingTint
            else -> navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainerHigh
        }

        val animatedColor by animateColorAsState(
            targetValue = targetTint,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "videoMiniPlayerColor",
        )

        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val synthwaveBorder = remember(primary, secondary) {
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(primary, secondary)
                )
            )
        }

        val border = when {
            isSynthwave -> {
                synthwaveBorder
            }
            isSoothing -> {
                androidx.compose.foundation.BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
            else -> null
        }

        // Wrap the shape when-chains in remember so the RoundedCornerShape
        // instances aren't rebuilt on every recomposition. The branches only
        // depend on isSynthwave / isSoothing.
        val shape = remember(isSynthwave, isSoothing) {
            when {
                isSynthwave -> RoundedCornerShape(0.dp)
                isSoothing -> ShapeCache.smooth16
                else -> ShapeCache.smooth12
            }
        }

        val videoShape = remember(isSynthwave, isSoothing) {
            when {
                isSynthwave -> RoundedCornerShape(0.dp)
                isSoothing -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                else -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            }
        }

        val bottomShape = remember(isSynthwave, isSoothing) {
            when {
                isSynthwave -> RoundedCornerShape(0.dp)
                isSoothing -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                else -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            }
        }

        // --- Draggable mini-player ---
        // dragOffsetX/Y is an additive translation on top of the caller-supplied
        // alignment/width. Kept as local UI state (out of VideoMiniPlayerState)
        // so view state doesn't leak into the data layer and stale px offsets
        // aren't persisted across items/rotations. Reads delegated state inside
        // the pointerInput lambdas, so the gesture detector (keyed on Unit) is
        // not restarted but always sees fresh layout/position values.
        val dragOffsetX = remember { Animatable(0f) }
        val dragOffsetY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val dragMarginPx = with(LocalDensity.current) { 8.dp.toPx() }
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        // Natural (caller-aligned) window position, captured only while no drag
        // is applied so snap anchors stay pinned to the caller's corner instead
        // of drifting mid-drag (the mount site already accounts for insets/nav).
        var naturalPos by remember { mutableStateOf(Offset.Zero) }

        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        dragOffsetX.value.roundToInt(),
                        dragOffsetY.value.roundToInt(),
                    )
                }
                .onGloballyPositioned { coords ->
                    cardSize = coords.size
                    if (dragOffsetX.value == 0f && dragOffsetY.value == 0f) {
                        naturalPos = coords.positionInWindow()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, delta ->
                            change.consume()
                            val dm = context.resources.displayMetrics
                            val screenWidthPx = dm.widthPixels.toFloat()
                            val screenHeightPx = dm.heightPixels.toFloat()
                            val w = cardSize.width.toFloat()
                            val h = cardSize.height.toFloat()
                            val naturalX = naturalPos.x
                            val naturalY = naturalPos.y
                            // Keep the card fully on-screen while dragging.
                            val minX = dragMarginPx - naturalX
                            val maxX = screenWidthPx - w - dragMarginPx - naturalX
                            val minY = dragMarginPx - naturalY
                            val maxY = screenHeightPx - h - dragMarginPx - naturalY
                            val clampedX = (dragOffsetX.value + delta.x).coerceIn(minX, maxX)
                            val clampedY = (dragOffsetY.value + delta.y).coerceIn(minY, maxY)
                            scope.launch {
                                dragOffsetX.snapTo(clampedX)
                                dragOffsetY.snapTo(clampedY)
                            }
                        },
                        onDragEnd = {
                            val dm = context.resources.displayMetrics
                            snapMiniPlayerToNearestCorner(
                                dragOffsetX = dragOffsetX,
                                dragOffsetY = dragOffsetY,
                                scope = scope,
                                naturalPos = naturalPos,
                                cardSize = cardSize,
                                screenWidthPx = dm.widthPixels.toFloat(),
                                screenHeightPx = dm.heightPixels.toFloat(),
                                marginPx = dragMarginPx,
                            )
                        },
                        onDragCancel = {
                            val dm = context.resources.displayMetrics
                            snapMiniPlayerToNearestCorner(
                                dragOffsetX = dragOffsetX,
                                dragOffsetY = dragOffsetY,
                                scope = scope,
                                naturalPos = naturalPos,
                                cardSize = cardSize,
                                screenWidthPx = dm.widthPixels.toFloat(),
                                screenHeightPx = dm.heightPixels.toFloat(),
                                marginPx = dragMarginPx,
                            )
                        },
                    )
                },
            shape = shape,
            color = animatedColor,
            border = border,
            shadowElevation = if (isSoothing) 4.dp else 12.dp,
            tonalElevation = if (isSoothing) 0.dp else 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(videoShape)
                        .background(Color.Black)
                        .focusIndicator(videoShape)
                        .clickable(onClick = onClick),
                ) {
                    if (engine != null) {
                        key(engine) {
                            AndroidView(
                                factory = { ctx ->
                                    engine.createSurfaceView(ctx)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    IconButtonWithPressAnimation(
                        onClick = onClose,
                        size = 32.dp,
                    ) {
                        Icon(
                            Tabler.Outline.X,
                            contentDescription = stringResource(R.string.media_close),
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bottomShape)
                        .background(animatedColor)
                        .focusIndicator(bottomShape)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    IconButtonWithPressAnimation(
                        onClick = onPlayPause,
                        size = 36.dp,
                    ) {
                        Icon(
                            if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Snaps the mini-player drag offset to the screen corner nearest its current
 * position. Targets are absolute window-space corners (with [marginPx]); the
 * required drag offset is derived by subtracting the caller-aligned
 * [naturalPos], so the math stays correct regardless of which mount site
 * (phone/tablet/full-screen) or scroll-coupled offset hosts the card.
 */
private fun snapMiniPlayerToNearestCorner(
    dragOffsetX: Animatable<Float, AnimationVector1D>,
    dragOffsetY: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    naturalPos: Offset,
    cardSize: IntSize,
    screenWidthPx: Float,
    screenHeightPx: Float,
    marginPx: Float,
) {
    val w = cardSize.width.toFloat()
    val h = cardSize.height.toFloat()
    val maxX = (screenWidthPx - w - marginPx).coerceAtLeast(marginPx)
    val maxY = (screenHeightPx - h - marginPx).coerceAtLeast(marginPx)
    val corners = listOf(
        IntOffset(marginPx.roundToInt(), marginPx.roundToInt()),   // top-start
        IntOffset(maxX.roundToInt(), marginPx.roundToInt()),        // top-end
        IntOffset(marginPx.roundToInt(), maxY.roundToInt()),        // bottom-start
        IntOffset(maxX.roundToInt(), maxY.roundToInt()),            // bottom-end
    )
    // Current window-space top-left of the card = natural + drag.
    val currentX = naturalPos.x + dragOffsetX.value
    val currentY = naturalPos.y + dragOffsetY.value
    val nearest = corners.minBy { corner ->
        val dx = corner.x - currentX
        val dy = corner.y - currentY
        dx * dx + dy * dy
    } ?: corners.last()
    // Animate both axes concurrently to the nearest corner.
    val targetX = nearest.x - naturalPos.x
    val targetY = nearest.y - naturalPos.y
    val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    scope.launch { dragOffsetX.animateTo(targetX, spec) }
    scope.launch { dragOffsetY.animateTo(targetY, spec) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconButtonWithPressAnimation(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "videoMiniButtonScale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .scale(scale)
            .focusIndicator(androidx.compose.foundation.shape.CircleShape),
        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
        interactionSource = interactionSource,
    ) {
        content()
    }
}
